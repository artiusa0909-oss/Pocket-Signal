package com.pocketsignal.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-consented, on-device screen reader for the Pocket Option app/window.
 * It performs OCR locally; no screenshot or recognized text is sent to a server.
 */
class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "screen_capture_result_code"
        const val EXTRA_RESULT_DATA = "screen_capture_result_data"
        private const val CHANNEL_ID = "pocket_signal_screen_capture"
        private const val NOTIFICATION_ID = 7413
        private const val ACTION_STOP = "com.pocketsignal.app.STOP_SCREEN_CAPTURE"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val processing = AtomicBoolean(false)
    @Volatile private var lastOcrAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        workerThread = HandlerThread("PocketSignalScreenReader").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            ScreenCaptureState.status = "Нет разрешения на захват экрана"
            stopSelf()
            return START_NOT_STICKY
        }

        startCaptureForeground()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                ScreenCaptureState.status = "Android не выдал сессию захвата"
                stopSelf()
                return START_NOT_STICKY
            }
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ScreenCaptureState.status = "Захват экрана остановлен"
                    stopSelf()
                }
            }, workerHandler)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val density = metrics.densityDpi
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    if (processing.compareAndSet(false, true)) {
                        val bitmap = try {
                            val plane = image.planes[0]
                            val pixelStride = plane.pixelStride
                            val rowPadding = plane.rowStride - pixelStride * width
                            val padded = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            padded.copyPixelsFromBuffer(plane.buffer)
                            Bitmap.createBitmap(padded, 0, 0, width, height).also {
                                if (it !== padded) padded.recycle()
                            }
                        } catch (_: Exception) {
                            null
                        } finally {
                            image.close()
                        }
                        if (bitmap != null) runOcr(bitmap) else processing.set(false)
                    } else {
                        image.close()
                    }
                }
            }, workerHandler)

            virtualDisplay = projection?.createVirtualDisplay(
                "PocketSignalCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                workerHandler
            )
            ScreenCaptureState.status = "Экран подключён · распознавание котировок"
            ScreenCaptureState.lastFrameAtMillis = System.currentTimeMillis()
        } catch (e: Exception) {
            ScreenCaptureState.status = "Ошибка захвата: ${e.javaClass.simpleName}"
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun runOcr(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastOcrAt < 900L) {
            bitmap.recycle()
            processing.set(false)
            return
        }
        lastOcrAt = now
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                // Keep only the OCR length, not personal/account text, in shared state.
                ScreenCaptureState.recognizedTextLength = result.text.length
                ScreenCaptureState.lastFrameAtMillis = System.currentTimeMillis()
                ScreenCaptureState.status = if (result.text.isNotBlank()) {
                    "Экран читается · OCR: ${result.text.length} знаков"
                } else {
                    "Экран захвачен · текст пока не распознан"
                }
            }
            .addOnFailureListener {
                ScreenCaptureState.status = "Захват работает · ошибка распознавания текста"
            }
            .addOnCompleteListener {
                bitmap.recycle()
                processing.set(false)
            }
    }

    private fun startCaptureForeground() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pendingStop = PendingIntent.getService(
            this, 7414, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("POCKET SIGNAL AI")
            .setContentText("Чтение экрана Pocket Option выполняется на устройстве")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", pendingStop)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pocket Signal screen capture",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        recognizer.close()
        workerThread?.quitSafely()
        ScreenCaptureState.status = "Захват экрана остановлен"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
