package com.pocketsignal.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Draggable, non-focusable companion panel.
 * No CALL/PUT advice is displayed until verified live market data is connected.
 */
class FloatingSignalService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private var expanded = true

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun showPanel() {
        if (panel != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(17, 27, 39))
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(32, 214, 154))
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = text("POCKET SIGNAL AI", 13f, Color.WHITE, true)
        val toggle = Button(this).apply {
            this.text = "−"
            setOnClickListener { setExpanded(root, body, close, this) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(text("СТАТУС: ОЖИДАНИЕ ДАННЫХ", 11f, Color.rgb(32, 214, 154), true))
        body.addView(text("Таймфрейм: M1", 10f, Color.LTGRAY))
        body.addView(text("CALL / PUT: нет сигнала", 12f, Color.WHITE, true))
        body.addView(text("Нет проверенного потока котировок", 9f, Color.rgb(255, 194, 102)))
        val close = Button(this).apply {
            this.text = "ЗАКРЫТЬ"
            setOnClickListener { stopSelf() }
        }

        header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f))
        header.addView(toggle, LinearLayout.LayoutParams(dp(48), dp(40)))
        root.addView(header)
        root.addView(body)
        root.addView(close)

        val drag = object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                        params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                        windowManager.updateViewLayout(root, params)
                        return true
                    }
                }
                return false
            }
        }
        header.setOnTouchListener(drag)
        title.setOnTouchListener(drag)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            dp(270),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(110)
        }

        panel = root
        windowManager.addView(root, params)
    }

    private fun setExpanded(
        root: LinearLayout,
        body: View,
        close: View,
        toggle: Button
    ) {
        expanded = !expanded
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        close.visibility = if (expanded) View.VISIBLE else View.GONE
        toggle.text = if (expanded) "−" else "+"
        params.width = if (expanded) dp(270) else dp(190)
        windowManager.updateViewLayout(root, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel == null) showPanel()
        return START_STICKY
    }

    override fun onDestroy() {
        panel?.let { windowManager.removeView(it) }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
