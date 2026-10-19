package com.cyclecare.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 15 §11.3 conditions 2, 3 and 8 depend on counters that have to
 * survive the process. The old implementation kept the last-shown timestamp
 * in a field on the ad manager, so it was lost whenever the app was killed
 * and the cooldown silently reset.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreInterstitialStatsRepositoryTest {

    private lateinit var repository: DataStoreInterstitialStatsRepository

    private lateinit var storeName: String
    private lateinit var storeScope: CoroutineScope

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // An isolated store, so the test starts from a known state instead of
        // inheriting counters written by another test. The name is kept so a
        // second instance can be pointed at the same file.
        storeName = "interstitial_stats_test_${System.nanoTime()}"
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = DataStoreInterstitialStatsRepository.isolated(
            context = context,
            name = storeName,
            scope = storeScope,
        )
    }

    @Test
    fun sessions_startAtZeroAndAccumulate() = runBlocking {
        assertThat(repository.completedSessions()).isEqualTo(0)

        repository.recordSession()
        repository.recordSession()

        assertThat(repository.completedSessions()).isEqualTo(2)
    }

    @Test
    fun lastShownAt_isNullUntilAnInterstitialIsShown() = runBlocking {
        assertThat(repository.lastInterstitialShownAtEpochMillis()).isNull()
    }

    @Test
    fun recordInterstitialShown_storesTheTimestamp() = runBlocking {
        repository.recordInterstitialShown(1_700_000_000_000L, "2026-10-19")

        assertThat(repository.lastInterstitialShownAtEpochMillis())
            .isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun recordInterstitialShown_countsAgainstTheDayItHappenedOn() = runBlocking {
        repository.recordInterstitialShown(1_700_000_000_000L, "2026-10-19")
        repository.recordInterstitialShown(1_700_000_060_000L, "2026-10-19")

        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(2)
    }

    @Test
    fun theDailyCountResetsOnANewDay() = runBlocking {
        repository.recordInterstitialShown(1_700_000_000_000L, "2026-10-19")
        repository.recordInterstitialShown(1_700_000_060_000L, "2026-10-19")
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(2)

        // The next day starts from zero. A count belonging to yesterday must
        // not be carried into today, or the cap would be spent before the
        // user had done anything.
        assertThat(repository.interstitialsShownOn("2026-10-20")).isEqualTo(0)

        repository.recordInterstitialShown(1_700_086_400_000L, "2026-10-20")
        assertThat(repository.interstitialsShownOn("2026-10-20")).isEqualTo(1)
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(0)
    }

    @Test
    fun recordingAnInterstitialUpdatesBothTheTimestampAndTheCount() = runBlocking {
        repository.recordInterstitialShown(1_700_000_000_000L, "2026-10-19")

        assertThat(repository.lastInterstitialShownAtEpochMillis())
            .isEqualTo(1_700_000_000_000L)
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(1)
    }

    // ── the daily cap at the persistence level ────────────────────────
    //
    // The cap itself is a product decision of three. The gate decides; the
    // store is what makes the count survive a process death, which is what
    // stops the cap being reset by killing the app.

    @Test
    fun theStoreCanHoldZeroThroughThreeImpressions() = runBlocking {
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(0)

        repository.recordInterstitialShown(1_700_000_000_000L, "2026-10-19")
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(1)

        repository.recordInterstitialShown(1_700_000_060_000L, "2026-10-19")
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(2)

        repository.recordInterstitialShown(1_700_000_120_000L, "2026-10-19")
        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(3)
    }

    @Test
    fun theCountSurvivesARestart() = runBlocking {
        // Cancelling the scope releases the store file, which is what a
        // process death does. The counters have to come back, or the cap
        // would be reset by killing the app.
        repository.recordInterstitialShown(1_700_000_000_000L, "2026-10-19")
        repository.recordInterstitialShown(1_700_000_060_000L, "2026-10-19")
        repository.recordSession()
        storeScope.cancel()
        // Let the file handle be released before it is opened again.
        delay(200)

        val afterRestart = DataStoreInterstitialStatsRepository.isolated(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            name = storeName,
        )

        assertThat(afterRestart.interstitialsShownOn("2026-10-19")).isEqualTo(2)
        assertThat(afterRestart.lastInterstitialShownAtEpochMillis())
            .isEqualTo(1_700_000_060_000L)
        assertThat(afterRestart.completedSessions()).isEqualTo(1)
    }

    @Test
    fun theSessionCountSurvivesARestart() = runBlocking {
        repeat(3) { repository.recordSession() }
        storeScope.cancel()
        delay(200)

        val afterRestart = DataStoreInterstitialStatsRepository.isolated(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            name = storeName,
        )

        assertThat(afterRestart.completedSessions()).isEqualTo(3)
    }

    @Test
    fun repeatedEventsAreCountedOnceEach() = runBlocking {
        // Three separate impressions are three records. The store does not
        // deduplicate, because each record is a distinct ad that was shown.
        repeat(3) { index ->
            repository.recordInterstitialShown(1_700_000_000_000L + index * 60_000L, "2026-10-19")
        }

        assertThat(repository.interstitialsShownOn("2026-10-19")).isEqualTo(3)
    }
}
