package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service that monitors app launches in real time.
 * When a focus session is active and the user opens a non-allowed app,
 * this service intercepts the action and launches InterruptionActivity.
 *
 * IMPORTANT: The user must enable this service manually in:
 *   Settings → Accessibility → Installed Services → Focus Guard
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        /** Reference used by other components to check if service is running. */
        @Volatile
        var instance: AppBlockerAccessibilityService? = null

        /**
         * Apps that are always allowed during a focus session.
         * Includes phone/call apps across major OEMs.
         */
        private val ALLOWED_PACKAGES = setOf(
            // Our own app
            "com.focusguard.app",
            // AOSP phone / dialer
            "com.android.phone",
            "com.android.dialer",
            "com.android.incallui",
            // Google Dialer
            "com.google.android.dialer",
            // Samsung
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            // Xiaomi / MIUI
            "com.miui.incallui",
            // OnePlus
            "com.oneplus.dialer",
            // Motorola
            "com.motorola.incallui"
        )

        /**
         * System UI package prefixes that must never be blocked
         * (launcher, system UI overlay, lock screen, etc.).
         */
        private val SYSTEM_ALLOWED_PREFIXES = listOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",   // Samsung One UI launcher
            "com.miui.home",                   // MIUI launcher
            "com.oneplus.launcher",
            "com.motorola.launcher3",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher"
        )
    }

    private lateinit var focusStateManager: FocusStateManager
    private var lastBlockedPackage: String = ""
    private var lastBlockedTime: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        focusStateManager = FocusStateManager.getInstance(applicationContext)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Do nothing if no active focus session
        if (!focusStateManager.isActive()) return

        // Do nothing if the user has temporarily unlocked
        if (focusStateManager.isUnlocked()) return

        // Always allow system UI and launchers
        if (SYSTEM_ALLOWED_PREFIXES.any { packageName.startsWith(it) }) return

        // Always allow whitelisted packages (phone / calls)
        if (packageName in ALLOWED_PACKAGES) return

        // Debounce: avoid spamming the interruption screen for the same package
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockedTime) < 2000L) return

        lastBlockedPackage = packageName
        lastBlockedTime = now

        launchInterruptionScreen()
    }

    private fun launchInterruptionScreen() {
        val intent = Intent(this, InterruptionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Required override — no-op
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
