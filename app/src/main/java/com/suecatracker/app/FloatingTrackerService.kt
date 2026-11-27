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
                "Erro no serviço: ${e.javaClass.simpleName}:${e.message}",
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

        val layoutParams = WindowManager
