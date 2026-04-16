package com.focusguard.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Singleton that manages the focus session state via SharedPreferences.
 * Shared between MainActivity, FocusTimerService, and AppBlockerAccessibilityService.
 */
class FocusStateManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "focus_guard_prefs"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_DURATION_MILLIS = "duration_millis"
        private const val KEY_IS_UNLOCKED = "is_unlocked"

        @Volatile
        private var instance: FocusStateManager? = null

        fun getInstance(context: Context): FocusStateManager {
            return instance ?: synchronized(this) {
                instance ?: FocusStateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Start a new focus session with the given duration in minutes. */
    fun startFocus(durationMinutes: Int) {
        val durationMillis = durationMinutes * 60 * 1000L
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis

        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putLong(KEY_START_TIME, startTime)
            .putLong(KEY_END_TIME, endTime)
            .putLong(KEY_DURATION_MILLIS, durationMillis)
            .putBoolean(KEY_IS_UNLOCKED, false)
            .apply()
    }

    /** Stop and clear the current focus session. */
    fun stopFocus() {
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, false)
            .putBoolean(KEY_IS_UNLOCKED, false)
            .apply()
    }

    /**
     * Restart the clock: keep the same total duration but reset start/end times.
     * Used when user chooses "Restart the clock" on the interruption screen.
     */
    fun restartFocus() {
        val durationMillis = prefs.getLong(KEY_DURATION_MILLIS, 25 * 60 * 1000L)
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis

        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putLong(KEY_START_TIME, startTime)
            .putLong(KEY_END_TIME, endTime)
            .putBoolean(KEY_IS_UNLOCKED, false)
            .apply()
    }

    /**
     * Temporarily unlock app access (user chose "Yes" on interruption screen).
     * The timer keeps running but the blocker stops intercepting app launches.
     */
    fun setUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_IS_UNLOCKED, unlocked).apply()
    }

    /** Returns true if a focus session is active and not yet expired. */
    fun isActive(): Boolean {
        if (!prefs.getBoolean(KEY_IS_ACTIVE, false)) return false
        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        if (System.currentTimeMillis() >= endTime) {
            stopFocus()
            return false
        }
        return true
    }

    /** Returns true if the user has temporarily unlocked app access. */
    fun isUnlocked(): Boolean = prefs.getBoolean(KEY_IS_UNLOCKED, false)

    /** Remaining milliseconds in the current session. Returns 0 if not active. */
    fun getRemainingMillis(): Long {
        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        return maxOf(0L, endTime - System.currentTimeMillis())
    }

    fun getEndTime(): Long = prefs.getLong(KEY_END_TIME, 0L)

    /** Original duration of the current (or last) session in milliseconds. */
    fun getDurationMillis(): Long = prefs.getLong(KEY_DURATION_MILLIS, 0L)
}
