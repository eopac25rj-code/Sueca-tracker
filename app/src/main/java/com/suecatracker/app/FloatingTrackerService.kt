package com.suecatracker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
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
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingTrackerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private val suitCounts = intArrayOf(10, 10, 10, 10)
    private val suitLabels = arrayOf("♠", "♥", "♦", "♣")
    private val suitColors = intArrayOf(
        Color.WHITE,
        Color.parseColor("#FF5252"),
        Color.parseColor("#FF5252"),
        Color.WHITE
    )

    private var totalTextView: TextView? = null
    private val suitCountViews = arrayOfNulls<TextView>(4)
    private var bodyContainer: LinearLayout? = null
    private var minimizeBtn: Button? = null
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startAsForegroundSafe()
            createOverlayView()
        } catch (e: Throwable) {
            Toast.makeText(
                this,
                "Erro no serviço: ${e.javaClass.simpleName}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    private fun startAsForegroundSafe() {
        val channelId = "sueca_tracker_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sueca Tracker Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sueca Tracker ativo")
            .setContentText("Contador flutuante em execução")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun createOverlayView() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }
        params = layoutParams

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg("#E616181D", 24f, "#3A3F4B")
            setPadding(20, 16, 20, 16)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val totalTv = TextView(this).apply {
            text = "Restam: 40"
            setTextColor(Color.parseColor("#4ADE80"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(4, 0, 16, 0)
        }
        totalTextView = totalTv
        header.addView(totalTv)

        val minBtn = createMiniButton("–") {
            toggleMinimize()
        }
        minimizeBtn = minBtn
        header.addView(minBtn)

        val closeBtn = createMiniButton("✕") {
            stopSelf()
        }
        closeBtn.setTextColor(Color.parseColor("#FF6B6B"))
        header.addView(closeBtn)

        attachDragListener(header)
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        bodyContainer = body

        for (i in 0 until 4) {
            val idx = i
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val suitTv = TextView(this).apply {
                text = suitLabels[idx]
                setTextColor(suitColors[idx])
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setWidth(48)
            }

            val minusBtn = createMiniButton("-") {
                if (suitCounts[idx] > 0) {
                    suitCounts[idx]--
                    refreshUi()
                }
            }

            val countTv = TextView(this).apply {
                text = "10"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setWidth(56)
            }
            suitCountViews[idx] = countTv

            val plusBtn = createMiniButton("+") {
                if (suitCounts[idx] < 10) {
                    suitCounts[idx]++
                    refreshUi()
                }
            }

            row.addView(suitTv)
            row.addView(minusBtn)
            row.addView(countTv)
            row.addView(plusBtn)
            body.addView(row)
        }

        val resetBtn = Button(this).apply {
            text = "Nova rodada (40)"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#2B303B", 14f, null)
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                for (i in 0 until 4) {
                    suitCounts[i] = 10
                }
                refreshUi()
            }
        }
        body.addView(resetBtn)

        root.addView(body)
        floatingView = root
        wm.addView(root, layoutParams)
    }

    private fun makeRoundedBg(fillHex: String, radius: Float, strokeHex: String?): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = radius
            if (strokeHex != null) {
                setStroke(2, Color.parseColor(strokeHex))
            }
        }
    }

    private fun createMiniButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setMinWidth(0)
            setMinimumWidth(0)
            setMinHeight(0)
            setMinimumHeight(0)
            setPadding(18, 6, 18, 6)
            background = makeRoundedBg("#262A34", 12f, null)
            setOnClickListener { onClick() }
        }
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        bodyContainer?.visibility = if (isMinimized) View.GONE else View.VISIBLE
        minimizeBtn?.text = if (isMinimized) "+" else "–"
    }

    private fun refreshUi() {
        var total = 0
        for (i in 0 until 4) {
            suitCountViews[i]?.text = suitCounts[i].toString()
            total += suitCounts[i]
        }
        totalTextView?.text = "Restam: $total"
    }

    private fun attachDragListener(target: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        target.setOnTouchListener(View.OnTouchListener { _, event ->
            val p = params ?: return@OnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, p)
                    true
                }
                else -> false
            }
        })
    }

    override fun onDestroy() {
        try {
            floatingView?.let { view ->
                windowManager?.removeViewImmediate(view)
            }
        } catch (_: Throwable) {
        }
        floatingView = null
        super.onDestroy()
    }
}
