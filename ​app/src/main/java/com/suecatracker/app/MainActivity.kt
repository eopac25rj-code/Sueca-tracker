package com.suecatracker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.suecatracker.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isOverlayRunning = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startOverlayService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPermission.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnToggleOverlay.setOnClickListener {
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
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
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
        val intent = Intent(this, FloatingTrackerService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isOverlayRunning = true
        updateToggleButtonUi()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, FloatingTrackerService::class.java)
        stopService(intent)
        isOverlayRunning = false
        updateToggleButtonUi()
    }

    private fun updatePermissionUi() {
        val hasPermission = Settings.canDrawOverlays(this)
        binding.btnPermission.text = if (hasPermission) {
            "Sobreposição permitida ✓"
        } else {
            "Permitir sobreposição na tela"
        }
        updateToggleButtonUi()
    }

    private fun updateToggleButtonUi() {
        binding.btnToggleOverlay.text = if (isOverlayRunning) {
            "Parar contador flutuante"
        } else {
            "Iniciar contador flutuante"
        }
    }
}
