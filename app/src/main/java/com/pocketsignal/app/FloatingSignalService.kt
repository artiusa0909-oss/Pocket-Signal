package com.pocketsignal.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Draggable companion panel showing locally read, unverified quote and chart data. */
class FloatingSignalService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private var expanded = true
    private val handler = Handler(Looper.getMainLooper())
    private var statusView: TextView? = null
    private var quoteView: TextView? = null
    private var candlesView: TextView? = null
    private var movementView: TextView? = null
    private var signalView: TextView? = null
    private var reasonView: TextView? = null
    private val refresh = object : Runnable {
        override fun run() {
            statusView?.text = ScreenCaptureState.status
            quoteView?.text = ScreenCaptureState.detectedAsset.ifBlank { "Актив не определён" } + "  ·  " +
                ScreenCaptureState.detectedPrice.ifBlank { "Цена не определена" }
            candlesView?.text = "Свечей на экране: ${ScreenCaptureState.visibleCandleCount}  ·  OHLC: ${ScreenCaptureState.candleCount}/30"
            movementView?.text = ScreenCaptureState.chartMovement + "  ·  " + ScreenCaptureState.chartCalibration
            signalView?.text = ScreenCaptureState.signalText + "  ·  " + ScreenCaptureState.signalConfidence + "%  ·  " +
                ScreenCaptureState.signalExpiryMinutes + " мин"
            reasonView?.text = ScreenCaptureState.signalReasons
            handler.postDelayed(this, 500L)
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager; showPanel(); handler.post(refresh) }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun showPanel() {
        if (panel != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10))
            background = GradientDrawable().apply { setColor(Color.rgb(17, 27, 39)); cornerRadius = dp(15).toFloat(); setStroke(dp(1), Color.rgb(32, 214, 154)) }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = label("POCKET SIGNAL AI", 13f, Color.WHITE, true)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusView = label("Ожидание подключения", 10f, Color.rgb(32, 214, 154), true)
        quoteView = label("Актив и цена не определены", 12f, Color.WHITE, true)
        candlesView = label("Свечей на экране: 0  ·  OHLC: 0/30", 10f, Color.LTGRAY)
        movementView = label("ОЖИДАНИЕ ГРАФИКА", 10f, Color.rgb(32, 214, 154), true)
        signalView = label("НЕТ СИГНАЛА", 13f, Color.WHITE, true)
        reasonView = label("Ожидание анализа графика", 9f, Color.rgb(255, 194, 102))
        body.addView(statusView); body.addView(quoteView); body.addView(candlesView); body.addView(movementView); body.addView(signalView); body.addView(reasonView)
        val connect = Button(this).apply {
            text = "ПОДКЛЮЧИТЬ POCKET OPTION"
            setOnClickListener { startActivity(Intent(this@FloatingSignalService, ScreenCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        val close = Button(this).apply { text = "ЗАКРЫТЬ"; setOnClickListener { stopSelf() } }
        val toggle = Button(this).apply { text = "−"; setOnClickListener { setExpanded(root, body, close, this) } }
        header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f)); header.addView(toggle, LinearLayout.LayoutParams(dp(48), dp(40)))
        root.addView(header); root.addView(body); root.addView(connect); root.addView(close)
        val drag = object : View.OnTouchListener {
            private var ix = 0; private var iy = 0; private var tx = 0f; private var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { params.x = ix + (e.rawX - tx).roundToInt(); params.y = iy + (e.rawY - ty).roundToInt(); wm.updateViewLayout(root, params); return true }
                }
                return false
            }
        }
        header.setOnTouchListener(drag); title.setOnTouchListener(drag)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(dp(310), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(110) }
        panel = root; wm.addView(root, params)
    }
    private fun setExpanded(root: LinearLayout, body: View, close: View, toggle: Button) {
        expanded = !expanded; body.visibility = if (expanded) View.VISIBLE else View.GONE; close.visibility = if (expanded) View.VISIBLE else View.GONE
        toggle.text = if (expanded) "−" else "+"; params.width = if (expanded) dp(310) else dp(190); wm.updateViewLayout(root, params)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (panel == null) showPanel(); return START_STICKY }
    override fun onDestroy() {
        panel?.let { wm.removeView(it) }; panel = null; handler.removeCallbacks(refresh)
        statusView = null; quoteView = null; candlesView = null; movementView = null; signalView = null; reasonView = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
