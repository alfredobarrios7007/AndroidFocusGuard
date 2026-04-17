package com.focusguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground Service that runs the countdown timer for a focus session.
 * Keeps a persistent notification showing the remaining time.
 * Broadcasts timer ticks so MainActivity can update its UI live.
 */
class FocusTimerService : Service() {

    companion object {
        const val ACTION_START = "com.focusguard.app.ACTION_START"
        const val ACTION_STOP = "com.focusguard.app.ACTION_STOP"
        const val ACTION_RESTART = "com.focusguard.app.ACTION_RESTART"

        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"

        const val CHANNEL_ID = "focus_guard_timer_channel"
        const val NOTIFICATION_ID = 1001

        // Broadcast sent every second with timer updates
        const val BROADCAST_TIMER_TICK = "com.focusguard.app.TIMER_TICK"
        const val EXTRA_REMAINING_MILLIS = "extra_remaining_millis"
        const val EXTRA_TIMER_FINISHED = "extra_timer_finished"
    }

    private var countDownTimer: CountDownTimer? = null
    private lateinit var focusStateManager: FocusStateManager

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        focusStateManager = FocusStateManager.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 25)
                startTimer(durationMinutes)
            }
            ACTION_RESTART -> restartTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Timer control
    // -------------------------------------------------------------------------

    private fun startTimer(durationMinutes: Int) {
        focusStateManager.startFocus(durationMinutes)
        val durationMillis = durationMinutes * 60 * 1000L

        startForeground(NOTIFICATION_ID, buildNotification(durationMillis))
        scheduleCountDown(durationMillis)
    }

    private fun restartTimer() {
        countDownTimer?.cancel()
        focusStateManager.restartFocus()
        val durationMillis = focusStateManager.getDurationMillis()
        updateNotification(durationMillis)
        scheduleCountDown(durationMillis)
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        focusStateManager.stopFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleCountDown(durationMillis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                updateNotification(millisUntilFinished)
                broadcastTick(millisUntilFinished, finished = false)
            }

            override fun onFinish() {
                focusStateManager.stopFocus()
                broadcastTick(0L, finished = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun buildNotification(remainingMillis: Long): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FocusTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, formatTime(remainingMillis)))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.notification_action_stop), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(remainingMillis: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // -------------------------------------------------------------------------
    // Broadcast helper
    // -------------------------------------------------------------------------

    private fun broadcastTick(remainingMillis: Long, finished: Boolean) {
        sendBroadcast(Intent(BROADCAST_TIMER_TICK).apply {
            putExtra(EXTRA_REMAINING_MILLIS, remainingMillis)
            putExtra(EXTRA_TIMER_FINISHED, finished)
        })
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else
            String.format("%02d:%02d", minutes, seconds)
    }
}
