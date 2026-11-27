package com.suecatracker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.suecatracker.app.databinding.OverlayTrackerBinding

class FloatingTrackerService : Service() {

    private var windowManager: WindowManager? = null
    private var binding: OverlayTrackerBinding? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val deck = SuecaDeck.createFreshDeck()
    private val cardViews = mutableMapOf<Pair<Suit, Rank>, TextView>()
    private var trumpSuit: Suit? = null
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        setupOverlay()
    }

    private fun startAsForeground() {
        val channelId = "sueca_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sueca Tracker Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sueca Tracker ativo")
            .setContentText("Toque nas cartas para marcar as que já saíram.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(101, notification)
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayBinding = OverlayTrackerBinding.inflate(LayoutInflater.from(this))
        binding = overlayBinding

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            typeFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 140
        }
        layoutParams = params

        setupDrag(overlayBinding.dragHandle, params)
        setupTrumpButtons(overlayBinding)
        setupControls(overlayBinding)
        buildCardGrid(overlayBinding.gridCards)
        refreshUi()

        windowManager?.addView(overlayBinding.root, params)
    }

    private fun setupDrag(handle: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(binding?.root, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupTrumpButtons(overlayBinding: OverlayTrackerBinding) {
        val map = mapOf(
            Suit.SPADES to overlayBinding.btnTrumpSpades,
            Suit.HEARTS to overlayBinding.btnTrumpHearts,
            Suit.DIAMONDS to overlayBinding.btnTrumpDiamonds,
            Suit.CLUBS to overlayBinding.btnTrumpClubs
        )

        map.forEach { (suit, button) ->
            button.setOnClickListener {
                trumpSuit = if (trumpSuit == suit) null else suit
                refreshUi()
            }
        }
    }

    private fun setupControls(overlayBinding: OverlayTrackerBinding) {
        overlayBinding.btnReset.setOnClickListener {
            for (i in deck.indices) {
                deck[i] = deck[i].copy(isPlayed = false)
            }
            trumpSuit = null
            refreshUi()
        }

        overlayBinding.btnMinimize.setOnClickListener {
            isMinimized = !isMinimized
            overlayBinding.bodyContainer.visibility = if (isMinimized) View.GONE else View.VISIBLE
            overlayBinding.btnMinimize.text = if (isMinimized) "+" else "—"
        }
    }

    private fun buildCardGrid(grid: GridLayout) {
        grid.removeAllViews()
        cardViews.clear()

        val density = resources.displayMetrics.density
        val cellWidth = (28 * density).toInt()
        val cellHeight = (26 * density).toInt()
        val margin = (1.5f * density).toInt()

        for (suit in SuecaDeck.suitsInOrder) {
            for (rank in SuecaDeck.ranksInOrder) {
                val cell = TextView(this).apply {
                    text = "${rank.label}${suit.symbol}"
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellWidth
                        height = cellHeight
                        setMargins(margin, margin, margin, margin)
                    }
                    setOnClickListener {
                        toggleCard(suit, rank)
                    }
                }
                cardViews[suit to rank] = cell
                grid.addView(cell)
            }
        }
    }

    private fun toggleCard(suit: Suit, rank: Rank) {
        val index = deck.indexOfFirst { it.suit == suit && it.rank == rank }
        if (index != -1) {
            val current = deck[index]
            deck[index] = current.copy(isPlayed = !current.isPlayed)
            refreshUi()
        }
    }

    private fun refreshUi() {
        val overlayBinding = binding ?: return

        val remainingCards = deck.count { !it.isPlayed }
        val remainingTrumps = trumpSuit?.let { activeTrump ->
            deck.count { it.suit == activeTrump && !it.isPlayed }
        } ?: 10

        overlayBinding.tvRemainingCards.text = "Faltam: $remainingCards/40"
        overlayBinding.tvRemainingTrumps.text = if (trumpSuit != null) {
            "Trunfo ${trumpSuit?.symbol}: $remainingTrumps"
        } else {
            "Trunfo: -"
        }

        val trumpButtons = mapOf(
            Suit.SPADES to overlayBinding.btnTrumpSpades,
            Suit.HEARTS to overlayBinding.btnTrumpHearts,
            Suit.DIAMONDS to overlayBinding.btnTrumpDiamonds,
            Suit.CLUBS to overlayBinding.btnTrumpClubs
        )

        trumpButtons.forEach { (suit, button) ->
            val isSelected = suit == trumpSuit
            button.background = GradientDrawable().apply {
                cornerRadius = 8f * resources.displayMetrics.density
                setColor(if (isSelected) Color.parseColor("#33FFD54F") else Color.TRANSPARENT)
                setStroke(
                    if (isSelected) 2 else 1,
                    if (isSelected) Color.parseColor("#FFD54F") else Color.parseColor("#44FFFFFF")
                )
            }
        }

        for (card in deck) {
            val view = cardViews[card.suit to card.rank] ?: continue
            val isTrump = trumpSuit != null && card.suit == trumpSuit
            val bgColor = if (card.isPlayed) {
                Color.parseColor("#C62828") // Vermelho = já saiu
            } else {
                Color.parseColor("#2E7D32") // Verde = ainda em jogo
            }
            val borderColor = when {
                isTrump && !card.isPlayed -> Color.parseColor("#FFD54F")
                card.rank.points > 0 -> Color.parseColor("#CCFFFFFF")
                else -> Color.parseColor("#33FFFFFF")
            }

            view.background = GradientDrawable().apply {
                cornerRadius = 5f * resources.displayMetrics.density
                setColor(bgColor)
                setStroke(if (isTrump) 2 else 1, borderColor)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding?.root?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        binding = null
    }
}
