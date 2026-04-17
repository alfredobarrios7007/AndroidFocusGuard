package com.focusguard.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.focusguard.app.databinding.ActivityInterruptionBinding

/**
 * Full-screen blocking activity shown when the user tries to open a non-allowed app
 * during a focus session.
 *
 * Flow:
 *   1. Main screen: "Do you really want to break your goal?" → Yes / No
 *   2. If No  → "Restart the clock" or "Stay with current time"
 *   3. If Yes → Unlock temporarily and let the user proceed
 */
class InterruptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInterruptionBinding
    private lateinit var focusStateManager: FocusStateManager

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInterruptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        focusStateManager = FocusStateManager.getInstance(this)

        // Intercept back-press — user cannot escape via the back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* blocked */ }
        })

        showMainQuestion()
    }

    // -------------------------------------------------------------------------
    // UI phases
    // -------------------------------------------------------------------------

    /** Phase 1 — ask the user if they really want to break focus. */
    private fun showMainQuestion() {
        binding.tvEmoji.text = "⛔"
        binding.tvQuestion.text = getString(R.string.question_break_goal)

        binding.layoutMainButtons.visibility = View.VISIBLE
        binding.layoutSecondaryButtons.visibility = View.GONE

        binding.btnNo.setOnClickListener { showSecondaryOptions() }
        binding.btnYes.setOnClickListener { unlockAndExit() }
    }

    /** Phase 2 — user said No; ask whether to restart or keep the clock. */
    private fun showSecondaryOptions() {
        binding.tvEmoji.text = "💪"
        binding.tvQuestion.text = getString(R.string.question_great_choice)

        binding.layoutMainButtons.visibility = View.GONE
        binding.layoutSecondaryButtons.visibility = View.VISIBLE

        binding.btnRestartClock.setOnClickListener { restartAndExit() }
        binding.btnStayClock.setOnClickListener { finish() }  // keep timer as-is, close screen
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /** User said "Yes" — temporarily unlock and close the blocker screen. */
    private fun unlockAndExit() {
        focusStateManager.setUnlocked(true)
        finish()
    }

    /**
     * User chose "Restart the clock" — restart the timer service from 0
     * using the same original duration.
     */
    private fun restartAndExit() {
        // Tell the running service to restart the countdown
        val intent = Intent(this, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_RESTART
        }
        startService(intent)
        finish()
    }
}
