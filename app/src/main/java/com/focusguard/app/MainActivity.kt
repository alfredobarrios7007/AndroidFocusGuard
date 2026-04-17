package com.focusguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var focusStateManager: FocusStateManager

    // Receives live timer ticks from FocusTimerService
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val remaining = intent?.getLongExtra(FocusTimerService.EXTRA_REMAINING_MILLIS, 0L) ?: 0L
            val finished = intent?.getBooleanExtra(FocusTimerService.EXTRA_TIMER_FINISHED, false) ?: false
            if (finished) onSessionEnded() else updateTimerDisplay(remaining)
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        focusStateManager = FocusStateManager.getInstance(this)
        setupPicker()
        setupButtons()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            timerReceiver,
            IntentFilter(FocusTimerService.BROADCAST_TIMER_TICK),
            RECEIVER_NOT_EXPORTED
        )
        refreshUI()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(timerReceiver) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupPicker() {
        binding.numberPickerMinutes.apply {
            minValue = 1
            maxValue = 240
            value = 25
            wrapSelectorWheel = false
        }
    }

    private fun setupButtons() {
        binding.btnStartStop.setOnClickListener {
            if (focusStateManager.isActive()) stopSession() else startSession()
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, R.string.toast_find_accessibility, Toast.LENGTH_LONG).show()
        }
    }

    // -------------------------------------------------------------------------
    // Session control
    // -------------------------------------------------------------------------

    private fun startSession() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, R.string.toast_enable_accessibility_first, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val minutes = binding.numberPickerMinutes.value
        val intent = Intent(this, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_START
            putExtra(FocusTimerService.EXTRA_DURATION_MINUTES, minutes)
        }
        startForegroundService(intent)
        refreshUI()
        Toast.makeText(this, R.string.toast_session_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopSession() {
        startService(Intent(this, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_STOP
        })
        onSessionEnded()
    }

    // -------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------

    private fun refreshUI() {
        val active = focusStateManager.isActive()
        val accessibilityOk = isAccessibilityServiceEnabled()

        // Accessibility warning row
        binding.layoutAccessibilityWarning.visibility =
            if (!accessibilityOk) View.VISIBLE else View.GONE

        // Accessibility status button label
        binding.btnEnableAccessibility.text =
            if (accessibilityOk) getString(R.string.btn_accessibility_enabled)
            else getString(R.string.btn_enable_accessibility)
        binding.btnEnableAccessibility.isEnabled = !accessibilityOk

        if (active) {
            binding.btnStartStop.text = getString(R.string.btn_stop_session)
            binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            binding.numberPickerMinutes.isEnabled = false
            binding.tvStatus.text = getString(R.string.status_active)
            updateTimerDisplay(focusStateManager.getRemainingMillis())
        } else {
            onSessionEnded()
        }
    }

    private fun onSessionEnded() {
        binding.btnStartStop.text = getString(R.string.btn_start_focus)
        binding.btnStartStop.setBackgroundColor(getColor(R.color.green_start))
        binding.numberPickerMinutes.isEnabled = true
        binding.tvTimer.text = "00:00"
        binding.tvStatus.text = getString(R.string.status_idle)
    }

    private fun updateTimerDisplay(remainingMillis: Long) {
        val totalSeconds = remainingMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        binding.tvTimer.text = if (hours > 0)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else
            String.format("%02d:%02d", minutes, seconds)
    }

    // -------------------------------------------------------------------------
    // Permission helpers
    // -------------------------------------------------------------------------

    private fun isAccessibilityServiceEnabled(): Boolean {
        val target = packageName + "/" + AppBlockerAccessibilityService::class.java.canonicalName
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(target)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}
