package com.cyclecare.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
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

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // An isolated store, so the test starts from a known state instead of
        // inheriting counters written by another test.
        repository = DataStoreInterstitialStatsRepository.isolated(
            context = context,
            name = "interstitial_stats_test_${System.nanoTime()}",
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
}
