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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingTrackerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private val ranks = arrayOf("A", "7", "K", "J", "Q", "6", "5", "4", "3", "2")
    private val suitSymbols = arrayOf("♠", "♥", "♦", "♣")
    private val suitIconColors = intArrayOf(
        Color.WHITE,
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#FF6B6B"),
        Color.WHITE
    )

    private val played = Array(ranks.size) { BooleanArray(4) }
    private val history = mutableListOf<Pair<Int, Int>>()

    private val suitHeaderViews = arrayOfNulls<TextView>(4)
    private val cardButtons = Array(ranks.size) { arrayOfNulls<TextView>(4) }
    private var titleTextView: TextView? = null
    private var bodyScrollView: ScrollView? = null
    private var minimizeBtn: TextView? = null
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startAsForegroundSafe()
            createOverlayView()
            refreshAllUi()
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

        val maxHeightPx = (resources.displayMetrics.heightPixels * 0.72f).toInt()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12f)
            y = dp(60f)
        }
        params = layoutParams

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedRect("#F2131822", 18f, "#2F3A4E")
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }

        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2f), 0, dp(2f), dp(8f))
        }

        val titleTv = TextView(this).apply {
            text = "Sueca • 40/40"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(2f), 0, dp(14f), 0)
        }
        titleTextView = titleTv
        headerBar.addView(titleTv)

        val minTv = createTopActionButton("–", "#283548", Color.WHITE) {
            toggleMinimize()
        }
        minimizeBtn = minTv
        headerBar.addView(minTv)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8f), dp(1f))
        }
        headerBar.addView(spacer)

        val closeTv = createTopActionButton("✕", "#C93B2B", Color.WHITE) {
            stopSelf()
        }
        headerBar.addView(closeTv)

        attachDragListener(headerBar)
        root.addView(headerBar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                maxHeightPx
            )
            isVerticalScrollBarEnabled = false
        }
        bodyScrollView = scroll

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val suitSummaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f), 0, dp(8f))
        }

        for (s in 0 until 4) {
            val sTv = TextView(this).apply {
                text = "${suitSymbols[s]} 10"
                setTextColor(suitIconColors[s])
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            suitHeaderViews[s] = sTv
            suitSummaryRow.addView(sTv)
        }
        content.addView(suitSummaryRow)

        for (r in ranks.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3f), 0, dp(3f))
            }

            for (s in 0 until 4) {
                val rankIdx = r
                val suitIdx = s

                val cell = TextView(this).apply {
                    text = "${ranks[rankIdx]}${suitSymbols[suitIdx]}"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = makeRoundedRect("#1E7E34", 8f, "#38A169")
                    layoutParams = LinearLayout.LayoutParams(dp(54f), dp(30f)).apply {
                        if (suitIdx < 3) rightMargin = dp(5f)
                    }
                    setOnClickListener {
                        toggleCard(rankIdx, suitIdx)
                    }
                }
                cardButtons[rankIdx][suitIdx] = cell
                row.addView(cell)
            }

            content.addView(row)
        }

        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, 0)
        }

        val undoBtn = Button(this).apply {
            text = "DESFAZER"
            textSize = 11f
            setTextColor(Color.parseColor("#CBD5E1"))
            setTypeface(null, Typeface.BOLD)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = makeRoundedRect("#232B3B", 8f, "#3A465C")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6f)
            }
            setOnClickListener {
                undoLastMove()
            }
        }
        footerRow.addView(undoBtn)

        val clearBtn = Button(this).apply {
            text = "LIMPAR (40)"
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = makeRoundedRect("#1E8E3E", 8f, "#34D399")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                resetDeck()
            }
        }
        footerRow.addView(clearBtn)

        content.addView(footerRow)
        scroll.addView(content)
        root.addView(scroll)

        floatingView = root
        wm.addView(root, layoutParams)
    }

    private fun createTopActionButton(
        label: String,
        bgHex: String,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(textColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedRect(bgHex, 6f, null)
            layoutParams = LinearLayout.LayoutParams(dp(26f), dp(24f))
            setOnClickListener { onClick() }
        }
    }

    private fun toggleCard(rankIdx: Int, suitIdx: Int) {
        val nextState = !played[rankIdx][suitIdx]
        played[rankIdx][suitIdx] = nextState
        val item = Pair(rankIdx, suitIdx)
        if (nextState) {
            history.add(item)
        } else {
            history.remove(item)
        }
        refreshAllUi()
    }

    private fun undoLastMove() {
        if (history.isEmpty()) return
        val last = history.removeAt(history.lastIndex)
        played[last.first][last.second] = false
        refreshAllUi()
    }

    private fun resetDeck() {
        for (r in ranks.indices) {
            for (s in 0 until 4) {
                played[r][s] = false
            }
        }
        history.clear()
        refreshAllUi()
    }

    private fun refreshAllUi() {
        var remainingTotal = 0
        val suitRemaining = IntArray(4) { 10 }

        for (r in ranks.indices) {
            for (s in 0 until 4) {
                val isPlayed = played[r][s]
                val cell = cardButtons[r][s]
                if (isPlayed) {
                    suitRemaining[s]--
                    cell?.background = makeRoundedRect("#B91C1C", 8f, "#F87171")
                    cell?.alpha = 0.92f
                } else {
                    remainingTotal++
                    cell?.background = makeRoundedRect("#1E7E34", 8f, "#38A169")
                    cell?.alpha = 1.0f
                }
            }
        }

        for (s in 0 until 4) {
            suitHeaderViews[s]?.text = "${suitSymbols[s]} ${suitRemaining[s]}"
        }

        titleTextView?.text = "Sueca • $remainingTotal/40"
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        bodyScrollView?.visibility = if (isMinimized) View.GONE else View.VISIBLE
        minimizeBtn?.text = if (isMinimized) "+" else "–"
    }

    private fun makeRoundedRect(fillHex: String, radiusDp: Float, strokeHex: String?): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeHex != null) {
                setStroke(dp(1f), Color.parseColor(strokeHex))
            }
        }
    }

    private fun dp(value: Float): Int {
        return (value * resources.displayMetrics.density).toInt()
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
