package com.cyclecare.app

import com.cyclecare.core.domain.ads.InterstitialStatsRepository

/**
 * In-memory stand-in for the persisted interstitial counters.
 *
 * The real implementation is DataStore-backed, which is not available to a
 * plain JVM instrumented test. The counters only need to behave correctly
 * within one test run, and the gate's behaviour for a given counter value is
 * covered directly in InterstitialGateTest.
 */
class FakeInterstitialStatsRepository : InterstitialStatsRepository {

    private var sessions: Int = 0
    private var lastShownAt: Long? = null
    private var shownDayKey: String? = null
    private var shownOnDay: Int = 0

    override suspend fun completedSessions(): Int = sessions

    override suspend fun recordSession() {
        sessions += 1
    }

    override suspend fun lastInterstitialShownAtEpochMillis(): Long? = lastShownAt

    override suspend fun interstitialsShownOn(dayKey: String): Int =
        if (shownDayKey == dayKey) shownOnDay else 0

    override suspend fun recordInterstitialShown(epochMillis: Long, dayKey: String) {
        lastShownAt = epochMillis
        if (shownDayKey == dayKey) {
            shownOnDay += 1
        } else {
            shownDayKey = dayKey
            shownOnDay = 1
        }
    }
}
