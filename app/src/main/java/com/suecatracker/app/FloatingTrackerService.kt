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
import android.graphics.drawable.ColorDrawable
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

    private val ranks = listOf("A", "7", "K", "J", "Q", "6", "5", "4", "3", "2")
    private val suits = listOf("♠", "♥", "♦", "♣")
    private val suitColors = listOf(
        Color.WHITE,
        Color.RED,
        Color.RED,
        Color.WHITE
    )

    private val cardStates = mutableMapOf<String, Boolean>()
    private val moveHistory = mutableListOf<String>()
    private val cardViews = mutableMapOf<String, TextView>()
    private val suitSummaryViews = mutableListOf<TextView>()

    private var headerCountView: TextView? = null
    private var bodyView: LinearLayout? = null
    private var minButtonView: TextView? = null
    private var minimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundNotification()
            buildOverlay()
            updateCounters()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "sueca_tracker_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sueca Tracker",
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

    private fun buildOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 20
        lp.y = 100
        params = lp

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#EE141923"))
        root.setPadding(14, 12, 14, 12)

        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL
        topRow.setPadding(0, 0, 0, 10)

        val titleView = TextView(this)
        titleView.text = "Sueca • 40/40"
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 13f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.setPadding(0, 0, 16, 0)
        headerCountView = titleView
        topRow.addView(titleView)

        val minView = TextView(this)
        minView.text = "–"
        minView.setTextColor(Color.WHITE)
        minView.textSize = 13f
        minView.setTypeface(null, Typeface.BOLD)
        minView.gravity = Gravity.CENTER
        minView.setBackgroundColor(Color.parseColor("#2A364B"))
        minView.setPadding(18, 6, 18, 6)
        minView.setOnClickListener {
            minimized = !minimized
            bodyView?.visibility = if (minimized) View.GONE else View.VISIBLE
            minButtonView?.text = if (minimized) "+" else "–"
        }
        minButtonView = minView
        topRow.addView(minView)

        val gap = View(this)
        gap.layoutParams = LinearLayout.LayoutParams(10, 1)
        topRow.addView(gap)

        val closeView = TextView(this)
        closeView.text = "✕"
        closeView.setTextColor(Color.WHITE)
        closeView.textSize = 13f
        closeView.setTypeface(null, Typeface.BOLD)
        closeView.gravity = Gravity.CENTER
        closeView.setBackgroundColor(Color.parseColor("#B91C1C"))
        closeView.setPadding(18, 6, 18, 6)
        closeView.setOnClickListener {
            stopSelf()
        }
        topRow.addView(closeView)

        enableDrag(topRow)
        root.addView(topRow)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        bodyView = body

        val suitsRow = LinearLayout(this)
        suitsRow.orientation = LinearLayout.HORIZONTAL
        suitsRow.setPadding(0, 0, 0, 8)
        suitSummaryViews.clear()

        for (s in suits.indices) {
            val sView = TextView(this)
            sView.text = "${suits[s]} 10"
            sView.setTextColor(suitColors[s])
            sView.textSize = 12f
            sView.setTypeface(null, Typeface.BOLD)
            sView.gravity = Gravity.CENTER
            sView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            suitSummaryViews.add(sView)
            suitsRow.addView(sView)
        }
        body.addView(suitsRow)

        cardViews.clear()
        cardStates.clear()

        for (rank in ranks) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 3, 0, 3)

            for (s in suits.indices) {
                val suit = suits[s]
                val key = "$rank$suit"
                cardStates[key] = false

                val cell = TextView(this)
                cell.text = key
                cell.setTextColor(Color.WHITE)
                cell.textSize = 12f
                cell.setTypeface(null, Typeface.BOLD)
                cell.gravity = Gravity.CENTER
                cell.background = ColorDrawable(Color.parseColor("#1E7E34"))
                val cellLp = LinearLayout.LayoutParams(112, 58)
                if (s < suits.lastIndex) {
                    cellLp.rightMargin = 6
                }
                cell.layoutParams = cellLp

                cell.setOnClickListener {
                    toggleCard(key)
                }

                cardViews[key] = cell
                row.addView(cell)
            }

            body.addView(row)
        }

        val bottomRow = LinearLayout(this)
        bottomRow.orientation = LinearLayout.HORIZONTAL
        bottomRow.setPadding(0, 10, 0, 0)

        val undoButton = Button(this)
        undoButton.text = "DESFAZER"
        undoButton.textSize = 11f
        undoButton.setTextColor(Color.WHITE)
        undoButton.setTypeface(null, Typeface.BOLD)
        undoButton.minWidth = 0
        undoButton.minimumWidth = 0
        undoButton.minHeight = 0
        undoButton.minimumHeight = 0
        undoButton.setPadding(12, 10, 12, 10)
        undoButton.setBackgroundColor(Color.parseColor("#263042"))
        val undoLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        undoLp.rightMargin = 6
        undoButton.layoutParams = undoLp
        undoButton.setOnClickListener {
            undoLastCard()
        }
        bottomRow.addView(undoButton)

        val clearButton = Button(this)
        clearButton.text = "LIMPAR (40)"
        clearButton.textSize = 11f
        clearButton.setTextColor(Color.WHITE)
        clearButton.setTypeface(null, Typeface.BOLD)
        clearButton.minWidth = 0
        clearButton.minimumWidth = 0
        clearButton.minHeight = 0
        clearButton.minimumHeight = 0
        clearButton.setPadding(12, 10, 12, 10)
        clearButton.setBackgroundColor(Color.parseColor("#1E8E3E"))
        clearButton.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        clearButton.setOnClickListener {
            clearBoard()
        }
        bottomRow.addView(clearButton)

        body.addView(bottomRow)
        root.addView(body)

        floatingView = root
        wm.addView(root, lp)
    }

    private fun toggleCard(key: String) {
        val current = cardStates[key] ?: false
        val next = !current
        cardStates[key] = next

        if (next) {
            moveHistory.add(key)
        } else {
            moveHistory.remove(key)
        }

        paintCard(key, next)
        updateCounters()
    }

    private fun undoLastCard() {
        if (moveHistory.isEmpty()) return
        val lastKey = moveHistory.removeAt(moveHistory.lastIndex)
        cardStates[lastKey] = false
        paintCard(lastKey, false)
        updateCounters()
    }

    private fun clearBoard() {
        for (key in cardStates.keys.toList()) {
            cardStates[key] = false
            paintCard(key, false)
        }
        moveHistory.clear()
        updateCounters()
    }

    private fun paintCard(key: String, isPlayed: Boolean) {
        val colorHex = if (isPlayed) "#B91C1C" else "#1E7E34"
        cardViews[key]?.background = ColorDrawable(Color.parseColor(colorHex))
    }

    private fun updateCounters() {
        var totalRemaining = 0

        for (s in suits.indices) {
            val suit = suits[s]
            var suitRemaining = 10
            for (rank in ranks) {
                val key = "$rank$suit"
                if (cardStates[key] == true) {
                    suitRemaining--
                }
            }
            totalRemaining += suitRemaining
            suitSummaryViews.getOrNull(s)?.text = "${suits[s]} $suitRemaining"
        }

        headerCountView?.text = "Sueca • $totalRemaining/40"
    }

    private fun enableDrag(dragHandle: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            val currentParams = params ?: return@setOnTouchListener false
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = currentParams.x
                    startY = currentParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentParams.x = startX + (event.rawX - touchX).toInt()
                    currentParams.y = startY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(floatingView, currentParams)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        try {
            floatingView?.let {
                windowManager?.removeViewImmediate(it)
            }
        } catch (_: Exception) {
        }
        floatingView = null
        super.onDestroy()
    }
}
