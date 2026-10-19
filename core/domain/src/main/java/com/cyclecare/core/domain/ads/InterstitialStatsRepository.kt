package com.cyclecare.core.domain.ads

/**
 * Persisted counters the interstitial gate needs.
 *
 * Spec 15 §11.3 conditions 2, 3 and 8 all depend on state that must outlive
 * the process. The current implementation keeps `lastShownTimeMillis` in a
 * field on `InterstitialAdManager`, so it is lost whenever the app is
 * killed, which resets the 60 second cooldown to "never shown" on the next
 * launch.
 */
interface InterstitialStatsRepository {

    /**
     * Number of sessions the user has completed.
     *
     * A session is one foreground period: the app moving into the
     * foreground. Spec 15 §11.3 condition 2 requires at least
     * `MIN_SESSIONS_BEFORE_INTERSTITIAL` of them.
     */
    suspend fun completedSessions(): Int

    /** Records that the user has entered a new session. */
    suspend fun recordSession()

    /** Epoch millis of the last interstitial actually shown, or null. */
    suspend fun lastInterstitialShownAtEpochMillis(): Long?

    /** Number of interstitials shown so far on [dayKey]. */
    suspend fun interstitialsShownOn(dayKey: String): Int

    /**
     * Records that an interstitial was shown.
     *
     * Updates both the last-shown timestamp used by condition 3 and the
     * daily counter used by condition 8, so the two cannot disagree.
     */
    suspend fun recordInterstitialShown(epochMillis: Long, dayKey: String)
}
