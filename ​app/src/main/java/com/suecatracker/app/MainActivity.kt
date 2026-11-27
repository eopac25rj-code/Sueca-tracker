package com.suecatracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.suecatracker.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private var isOverlayRunning = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startOverlayService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val viewBinding = ActivityMainBinding.inflate(layoutInflater)
            binding = viewBinding
            setContentView(viewBinding.root)

            viewBinding.btnPermission.setOnClickListener {
                requestOverlayPermission()
            }

            viewBinding.btnToggleOverlay.setOnClickListener {
                if (isOverlayRunning) {
                    stopOverlayService()
                } else {
                    if (!Settings.canDrawOverlays(this)) {
                        Toast.makeText(
                            this,
                            "Ative primeiro a permissão de sobreposição na tela.",
                            Toast.LENGTH_SHORT
                        ).show()
                        requestOverlayPermission()
                        return@setOnClickListener
                    }
                    ensureNotificationPermissionAndStart()
                }
            }
        } catch (e: Throwable) {
            showCrashDetails(e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updatePermissionUi()
        } catch (e: Throwable) {
            showCrashDetails(e)
        }
    }

    private fun showCrashDetails(e: Throwable) {
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            setPadding(32, 64, 32, 32)
            textSize = 13f
            text = "Erro ao abrir:\n\n${e.stackTraceToString()}"
        }
        scroll.addView(tv)
        setContentView(scroll)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun ensureNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startOverlayService()
    }

    private fun startOverlayService() {
        try {
            val intent = Intent(this, FloatingTrackerService::class.java)
            ContextCompat.startForegroundService(this, intent)
            isOverlayRunning = true
            updateToggleButtonUi()
        } catch (e: Throwable) {
            showCrashDetails(e)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, FloatingTrackerService::class.java)
        stopService(intent)
        isOverlayRunning = false
        updateToggleButtonUi()
    }

    private fun updatePermissionUi() {
        val b = binding ?: return
        val hasPermission = Settings.canDrawOverlays(this)
        b.btnPermission.text = if (hasPermission) {
            "Sobreposição permitida ✓"
        } else {
            "Permitir sobreposição na tela"
        }
        updateToggleButtonUi()
    }

    private fun updateToggleButtonUi() {
        val b = binding ?: return
        b.btnToggleOverlay.text = if (isOverlayRunning) {
            "Parar contador flutuante"
        } else {
            "Iniciar contador flutuante"
        }
    }
}
