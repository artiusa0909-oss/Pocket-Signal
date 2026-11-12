package com.pocketsignal.app

import android.app.*
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
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/** User-consented, local-only reader for visible Pocket Option chart information. */
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
    private val aggregator = OcrMinuteCandleAggregator()
    private val engine = SignalEngine(minimumConfidence = 70, minimumCandles = 12)
    @Volatile private var lastProcessedAt = 0L
    @Volatile private var lastAssetSeenAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        workerThread = HandlerThread("PocketSignalScreenReader").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (code == 0 || data == null) {
            ScreenCaptureState.status = "Нет разрешения на захват экрана"
            stopSelf()
            return START_NOT_STICKY
        }
        startCaptureForeground()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            if (projection == null) { ScreenCaptureState.status = "Android не выдал сессию захвата"; stopSelf(); return START_NOT_STICKY }
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { ScreenCaptureState.status = "Захват экрана остановлен"; stopSelf() }
            }, workerHandler)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    if (processing.compareAndSet(false, true)) {
                        val bitmap = try {
                            val plane = image.planes[0]
                            val stride = plane.pixelStride
                            val padding = plane.rowStride - stride * width
                            val padded = Bitmap.createBitmap(width + padding / stride, height, Bitmap.Config.ARGB_8888)
                            padded.copyPixelsFromBuffer(plane.buffer)
                            Bitmap.createBitmap(padded, 0, 0, width, height).also { if (it !== padded) padded.recycle() }
                        } catch (_: Exception) { null } finally { image.close() }
                        if (bitmap != null) processFrame(bitmap, width, height) else processing.set(false)
                    } else image.close()
                }
            }, workerHandler)
            virtualDisplay = projection?.createVirtualDisplay(
                "PocketSignalCapture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, workerHandler
            )
            ScreenCaptureState.status = "Экран подключён · анализ изображения графика"
            ScreenCaptureState.lastFrameAtMillis = System.currentTimeMillis()
        } catch (e: Exception) {
            ScreenCaptureState.status = "Ошибка захвата: " + e.javaClass.simpleName
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun processFrame(bitmap: Bitmap, width: Int, height: Int) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedAt < 350L) { bitmap.recycle(); processing.set(false); return }
        lastProcessedAt = now
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { b -> OcrTextLine(line.text, b.left, b.top, b.right, b.bottom) }
                }
                val parsed = PocketOptionOcrParser.parse(lines, width, height)
                val vision = ChartCandleVisionDetector.detect(bitmap, parsed.axisTicks, System.currentTimeMillis())
                ScreenCaptureState.recognizedTextLength = result.text.length
                ScreenCaptureState.lastFrameAtMillis = System.currentTimeMillis()
                ScreenCaptureState.visibleCandleCount = vision.visibleCandleCount
                ScreenCaptureState.chartMovement = vision.movement
                ScreenCaptureState.chartCalibration = when {
                    !vision.calibrated -> "Свечи видны · шкала цены не откалибрована"
                    parsed.isM1Chart -> "Шкала цены распознана · таймфрейм M1"
                    else -> "Шкала распознана · M1 строится по потоку котировок"
                }

                if (parsed.asset != null) {
                    if (parsed.asset != ScreenCaptureState.detectedAsset) {
                        aggregator.reset()
                        ScreenCaptureState.candleCount = 0
                        ScreenCaptureState.signalText = "НЕТ СИГНАЛА"
                    }
                    ScreenCaptureState.detectedAsset = parsed.asset
                    lastAssetSeenAt = System.currentTimeMillis()
                }
                val asset = ScreenCaptureState.detectedAsset
                val freshAsset = asset.isNotBlank() && System.currentTimeMillis() - lastAssetSeenAt < 10_000L
                val visualPrice = vision.candles.lastOrNull()?.close
                val price = parsed.priceText ?: visualPrice?.toString()
                if (freshAsset && price != null) {
                    val value = price.toDoubleOrNull()
                    if (value != null && value.isFinite() && value > 0.0) {
                        val quoteTime = System.currentTimeMillis()
                        val ocrCandles = aggregator.addTick(asset, value, quoteTime)
                        ScreenCaptureState.detectedPrice = price
                        ScreenCaptureState.lastQuoteAtMillis = quoteTime

                        // Vision-derived candles are used only when the visible chart scale can be
                        // calibrated from at least two OCR price-axis labels. This avoids fabricated OHLC.
                        val analysisCandles = if (vision.calibrated && parsed.isM1Chart) vision.candles else ocrCandles
                        ScreenCaptureState.candleCount = analysisCandles.size
                        val decision = engine.analyze(analysisCandles)
                        if (decision.ready && decision.direction != null) {
                            ScreenCaptureState.signalText = if (decision.direction == SignalDirection.CALL) "ВВЕРХ" else "ВНИЗ"
                            ScreenCaptureState.signalConfidence = decision.confidence
                            ScreenCaptureState.signalExpiryMinutes = decision.expirationMinutes.coerceAtLeast(1)
                            ScreenCaptureState.signalReasons = decision.reasons.joinToString(" · ")
                            ScreenCaptureState.status = "СИГНАЛ · ${decision.confidence}% · экспирация ${decision.expirationMinutes} мин"
                        } else {
                            ScreenCaptureState.signalText = "НЕТ СИГНАЛА"
                            ScreenCaptureState.signalConfidence = 0
                            ScreenCaptureState.signalExpiryMinutes = 1
                            ScreenCaptureState.signalReasons = if (!vision.calibrated)
                                "Визуально найдено свечей: ${vision.visibleCandleCount}. Для OHLC нужна точная шкала цены."
                            else decision.message
                            ScreenCaptureState.status = "Котировка ${asset}: ${price} · на графике свечей: ${vision.visibleCandleCount}"
                        }
                    }
                } else {
                    ScreenCaptureState.status = if (!freshAsset) "График захвачен · ожидание валютной пары" else "Актив ${asset} найден · цена не распознана однозначно"
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
        val stop = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 7414, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("POCKET SIGNAL AI")
            .setContentText("Чтение графика Pocket Option выполняется локально")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", pending).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pocket Signal screen capture", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
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
