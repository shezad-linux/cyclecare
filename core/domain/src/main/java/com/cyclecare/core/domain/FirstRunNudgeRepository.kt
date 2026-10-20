package com.cyclecare.core.domain

/**
 * Whether the one-time nudge towards Settings still has to be shown.
 *
 * Spec `15` §1.3.4: Settings moving out of the thumb zone is only
 * acceptable with a one-time, dismissible nudge after onboarding, never
 * repeated and never a badge.
 *
 * The flag is durable, so the nudge does not come back after the process
 * restarts, and it is set as soon as the nudge has been shown, so it is
 * shown exactly once rather than once per dismissal.
 */
interface FirstRunNudgeRepository {
    /**
     * Whether the nudge should still be shown.
     *
     * True only before the nudge has been shown for the first time.
     */
    suspend fun shouldShowFirstRunNudge(): Boolean

    /**
     * Records that the nudge has been shown.
     *
     * Called as the nudge appears rather than when it is dismissed, so that
     * closing the app without dismissing it does not bring the nudge back.
     */
    suspend fun markFirstRunNudgeShown()
}
