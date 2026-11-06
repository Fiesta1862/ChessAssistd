package com.chessassist

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var calibrateBtn: Button
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var projectionManager: MediaProjectionManager

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAndProceed() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { checkAndProceed() }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            OverlayService.projectionResultCode = result.resultCode
            OverlayService.projectionData = result.data
            startOverlayService()
        } else {
            statusText.text = getString(R.string.projection_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)
        calibrateBtn = findViewById(R.id.calibrateBtn)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)

        calibrateBtn.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        startBtn.setOnClickListener { checkAndProceed() }
        stopBtn.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            statusText.text = getString(R.string.service_stopped)
        }
    }

    private fun checkAndProceed() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = getString(R.string.need_overlay_perm)
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermLauncher.launch(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                statusText.text = getString(R.string.need_notif_perm)
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val prefs = getSharedPreferences("chessassist", MODE_PRIVATE)
        if (!prefs.contains("board_left")) {
            statusText.text = getString(R.string.need_calibration)
            startActivity(Intent(this, CalibrationActivity::class.java))
            return
        }
        statusText.text = getString(R.string.requesting_projection)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = getString(R.string.service_running)
    }
}
