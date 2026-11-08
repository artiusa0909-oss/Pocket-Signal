package com.pocketsignal.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.min

/**
 * Independent floating companion. It never injects code into, reads, or modifies
 * the Pocket Option application. GPT requests go only to a user-configured HTTPS
 * gateway; API credentials belong on that server, never in this APK.
 */
class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var chatScroll: ScrollView? = null
    private var chatMessages: LinearLayout? = null
    private var input: EditText? = null
    private var sendButton: Button? = null
    private var statusText: TextView? = null
    private val history = mutableListOf<Pair<String, String>>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (bubble == null && panel == null) showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlayViews()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun overlayParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun showBubble() {
        removePanel()
        val view = TextView(this).apply {
            text = "PS"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(20, 190, 139))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
            contentDescription = "Открыть Pocket Signal"
        }
        val params = overlayParams(dp(58), dp(58), false).apply {
            x = dp(14)
            y = dp(180)
        }
        attachDrag(view, params) { showPanel() }
        windowManager.addView(view, params)
        bubble = view
    }

    private fun showPanel() {
        removeBubble()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val width = min(dp(350), screenWidth - dp(24)).coerceAtLeast(dp(260))
        val height = min(dp(520), (screenHeight * 0.72f).toInt()).coerceAtLeast(dp(340))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(13, 20, 29), dp(18), Color.rgb(44, 62, 78))
            elevation = dp(14).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "POCKET SIGNAL  ·  GPT"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f))
        val minimize = smallButton("—")
        minimize.setOnClickListener { showBubble() }
        header.addView(minimize, LinearLayout.LayoutParams(dp(38), dp(36)))
        val close = smallButton("×")
        close.setOnClickListener { stopSelf() }
        header.addView(close, LinearLayout.LayoutParams(dp(38), dp(36)))
        root.addView(header)

        val status = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(32, 214, 154))
            text = if (gatewayUrl().isBlank()) {
                "GPT: требуется адрес защищённого шлюза"
            } else {
                "GPT: шлюз настроен · источник котировок не подключён"
            }
            setPadding(0, dp(3), 0, dp(8))
        }
        statusText = status
        root.addView(status)

        val divider = View(this).apply { setBackgroundColor(Color.rgb(42, 55, 69)) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(8))
        }
        val messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(messages)
        chatScroll = scroll
        chatMessages = messages
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (history.isEmpty()) {
            appendMessage(
                "assistant",
                "Я Pocket Signal. Здесь будет диалог с GPT. Оригинальный Pocket Option остаётся отдельным приложением и не изменяется. Для ответов GPT нужен HTTPS-шлюз; для рыночного анализа дополнительно нужен проверенный источник котировок."
            )
        } else {
            history.forEach { appendMessage(it.first, it.second, remember = false) }
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val edit = EditText(this).apply {
            hint = "Спросить GPT…"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(137, 153, 169))
            singleLine = false
            maxLines = 3
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = rounded(Color.rgb(27, 39, 52), dp(12), Color.rgb(57, 75, 91))
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        input = edit
        val send = Button(this).apply {
            text = "➤"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20, 160, 121)))
        }
        sendButton = send
        send.setOnClickListener { sendChatMessage() }
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChatMessage()
                true
            } else false
        }
        inputRow.addView(edit, LinearLayout.LayoutParams(0, dp(52), 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(54), dp(48)).apply {
            leftMargin = dp(6)
        })
        root.addView(inputRow)

        val params = overlayParams(width, height, true).apply {
            x = dp(12)
            y = dp(100)
        }
        attachDrag(header, params) { }
        windowManager.addView(root, params)
        panel = root
        panelParams = params
    }

    private fun sendChatMessage() {
        val message = input?.text?.toString()?.trim().orEmpty()
        if (message.isBlank()) return
        val endpoint = gatewayUrl()
        if (!endpoint.startsWith("https://")) {
            appendMessage("assistant", "Сначала открой Pocket Signal и сохрани HTTPS-адрес своего GPT-шлюза. Ключ OpenAI должен находиться только на сервере.")
            return
        }

        appendMessage("user", message)
        input?.setText("")
        sendButton?.isEnabled = false
        statusText?.text = "GPT: отправляем запрос…"
        thread(name = "PocketSignalChat") {
            try {
                val response = requestGateway(endpoint, message)
                history.add("user" to message)
                history.add("assistant" to response)
                mainHandler.post {
                    appendMessage("assistant", response)
                    statusText?.text = "GPT: ответ получен · котировки не подключены"
                    sendButton?.isEnabled = true
                }
            } catch (error: Exception) {
                mainHandler.post {
                    appendMessage("assistant", "Не удалось получить ответ: ${error.message ?: "ошибка соединения"}. Проверь HTTPS-шлюз.")
                    statusText?.text = "GPT: ошибка соединения"
                    sendButton?.isEnabled = true
                }
            }
        }
    }

    private fun requestGateway(baseUrl: String, message: String): String {
        val url = URL(baseUrl.trimEnd('/') + "/v1/chat")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val messages = JSONArray()
            (history + listOf("user" to message)).takeLast(20).forEach { (role, text) ->
                messages.put(JSONObject().put("role", role).put("content", text))
            }
            val payload = JSONObject().put("messages", messages)
                .put("client", "pocket-signal-android")
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            val json = JSONObject(body)
            return json.optString("reply").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("В ответе шлюза нет поля reply")
        } finally {
            connection.disconnect()
        }
    }

    private fun gatewayUrl(): String =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_GATEWAY, "").orEmpty().trim()

    private fun appendMessage(role: String, text: String, remember: Boolean = true) {
        val container = chatMessages ?: return
        val view = TextView(this).apply {
            this.text = (if (role == "user") "Вы" else "GPT") + "\n" + text
            textSize = 13f
            setTextColor(if (role == "user") Color.WHITE else Color.rgb(213, 224, 233))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(
                if (role == "user") Color.rgb(24, 72, 72) else Color.rgb(29, 42, 56),
                dp(12),
                Color.TRANSPARENT
            )
        }
        val params = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(7)
        }
        container.addView(view, params)
        if (remember && history.size < 40) history.add(role to text)
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun smallButton(label: String) = TextView(this).apply {
        text = label
        textSize = 21f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(34, 48, 62), dp(9), Color.TRANSPARENT)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams, onTap: () -> Unit) {
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) moved = true
                    if (moved) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager.updateViewLayout(view.rootView, params) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> true
            }
        }
    }

    private fun removeBubble() {
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        bubble = null
    }

    private fun removePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        panel = null
        panelParams = null
        chatScroll = null
        chatMessages = null
        input = null
        sendButton = null
        statusText = null
    }

    private fun removeOverlayViews() {
        removeBubble()
        removePanel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pocket Signal floating assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingOverlayService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 10, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = launchIntent?.let {
            PendingIntent.getActivity(
                this, 11, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pocket Signal работает")
            .setContentText("Плавающее окно доступно поверх Pocket Option")
            .setOngoing(true)
            .setContentIntent(contentPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPending)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.pocketsignal.app.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "pocket_signal_overlay"
        private const val NOTIFICATION_ID = 2401
        const val PREFS = "pocket_signal_prefs"
        const val KEY_GATEWAY = "gpt_gateway_url"
    }
}
