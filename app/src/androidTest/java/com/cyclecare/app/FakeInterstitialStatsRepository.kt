package com.cyclecare.app

import com.cyclecare.core.domain.ads.InterstitialStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory stand-in for the persisted interstitial counters.
 *
 * The real implementation is DataStore-backed, which is not available to a
 * plain JVM instrumented test. The counters only need to behave correctly
 * within one test run, and the gate's behaviour for a given counter value is
 * covered directly in InterstitialGateTest.
 *
 * The reads deliberately resume on a background dispatcher, the way a
 * DataStore read does. The gate suspends on that read, so the coroutine that
 * asked for an interstitial resumes on the storage thread rather than the
 * main one, and the completion callback arrives there. The real
 * DataStore-backed repository behaves the same way, so a fake that returned
 * inline would hide a genuine threading defect: navigating a tab from that
 * callback crashed with "setCurrentState must be called on the main thread".
 */
class FakeInterstitialStatsRepository : InterstitialStatsRepository {

    private var sessions: Int = 0
    private var lastShownAt: Long? = null
    private var shownDayKey: String? = null
    private var shownOnDay: Int = 0

    override suspend fun completedSessions(): Int =
        withContext(Dispatchers.IO) { sessions }

    override suspend fun recordSession() {
        sessions += 1
    }

    override suspend fun lastInterstitialShownAtEpochMillis(): Long? =
        withContext(Dispatchers.IO) { lastShownAt }

    override suspend fun interstitialsShownOn(dayKey: String): Int =
        withContext(Dispatchers.IO) { if (shownDayKey == dayKey) shownOnDay else 0 }

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
