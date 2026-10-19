package com.cyclecare.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ads.DraftTracker
import com.cyclecare.core.domain.ads.InMemoryDraftTracker
import com.cyclecare.core.domain.ads.InterstitialDropReason
import com.cyclecare.core.domain.ads.InterstitialGate
import com.cyclecare.core.domain.ads.InterstitialGateDecision
import com.cyclecare.core.domain.ads.InterstitialGateState
import com.cyclecare.core.domain.ads.InterstitialStatsRepository
import com.cyclecare.core.domain.time.TimeProvider
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Spec 15 §11.3, at the level where the conditions are gathered from real
 * repositories and the consequence of a drop is observed.
 *
 * [com.cyclecare.core.domain.ads.InterstitialGateTest] covers the decision
 * itself. This covers what happens around it: that a dropped request still
 * completes the navigation the user asked for, that a shown ad records
 * exactly one impression, and that a dropped request records none.
 */
class InterstitialCoordinatorTest {

    // ── fakes ──────────────────────────────────────────────────

    private class FakeStatsRepository : InterstitialStatsRepository {
        var sessions: Int = 0
        var lastShownAt: Long? = null
        var shownDayKey: String? = null
        var shownOnDay: Int = 0
        var recordCalls: Int = 0

        override suspend fun completedSessions(): Int = sessions
        override suspend fun recordSession() { sessions += 1 }
        override suspend fun lastInterstitialShownAtEpochMillis(): Long? = lastShownAt
        override suspend fun interstitialsShownOn(dayKey: String): Int =
            if (shownDayKey == dayKey) shownOnDay else 0

        override suspend fun recordInterstitialShown(epochMillis: Long, dayKey: String) {
            recordCalls += 1
            lastShownAt = epochMillis
            if (shownDayKey == dayKey) {
                shownOnDay += 1
            } else {
                shownDayKey = dayKey
                shownOnDay = 1
            }
        }
    }

    private class FakeReminderEventsRepository(
        private val deliveredAtEpochMillis: Long? = null,
    ) : ReminderEventsRepository {
        override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> = flowOf(emptyList())
        override fun observeUnreadCount(): Flow<Int> = flowOf(0)
        override fun observeNextUnread(): Flow<ReminderEvent?> = flowOf(null)
        override suspend fun recordEvent(event: ReminderEvent) = Unit
        override suspend fun markRead(id: String) = Unit
        override suspend fun markAllRead() = Unit
        override suspend fun dismiss(id: String) = Unit
        override suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long? =
            deliveredAtEpochMillis?.takeIf { it >= sinceEpochMillis }
    }

    private class FakeProfileRepository(
        private val onboarded: Boolean = true,
    ) : ProfileRepository {
        override fun observeProfile(): Flow<UserProfile?> = flowOf(
            if (onboarded) {
                UserProfile(
                    ageBand = AgeBand.Twenties,
                    averageCycleLength = 28,
                    periodLength = 5,
                    lastPeriodStart = LocalDate.of(2026, 10, 1),
                    trackingGoal = TrackingGoal.PeriodPrep,
                    remindersEnabled = false,
                    privacyModeEnabled = false,
                )
            } else {
                null
            },
        )

        override suspend fun saveProfile(profile: UserProfile) = Unit
        override suspend fun updateRemindersEnabled(enabled: Boolean) = Unit
    }

    private class FixedClock(private val instant: ZonedDateTime) : TimeProvider {
        override fun today(): LocalDate = instant.toLocalDate()
        override fun now(): ZonedDateTime = instant
        override fun nowEpochMillis(): Long = instant.toInstant().toEpochMilli()
    }

    private class FakeInterstitialAd(
        private val unitId: String,
        private val onShow: () -> Unit,
    ) : InterstitialAd() {
        private var callback: FullScreenContentCallback? = null

        override fun getAdUnitId(): String = unitId
        @Suppress("UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")
        override fun getResponseInfo(): ResponseInfo = null as ResponseInfo
        override fun setFullScreenContentCallback(callback: FullScreenContentCallback?) {
            this.callback = callback
        }
        override fun getFullScreenContentCallback(): FullScreenContentCallback? = callback
        override fun setImmersiveMode(enabled: Boolean) {}
        override fun setOnPaidEventListener(listener: OnPaidEventListener?) {}
        override fun getOnPaidEventListener(): OnPaidEventListener? = null
        override fun getPlacementId(): Long = 0L
        override fun setPlacementId(p0: Long) {}

        override fun show(activity: Activity) {
            onShow()
            callback?.onAdDismissedFullScreenContent()
        }
    }

    private class FakeLoader : InterstitialAdLoader {
        var ad: FakeInterstitialAd? = null
        var loadCount: Int = 0
        /**
         * Total ads displayed. Tracked here rather than on the ad instance
         * because dismissing an ad preloads the next one, replacing it.
         */
        var showCount: Int = 0

        override fun load(
            context: Context,
            adUnitId: String,
            adRequest: AdRequest,
            callback: InterstitialAdLoadCallback,
        ) {
            loadCount += 1
            val created = FakeInterstitialAd(adUnitId) { showCount += 1 }
            ad = created
            callback.onAdLoaded(created)
        }
    }

    private class FakeContext : ContextWrapper(null)
    private class FakeActivity : Activity()

    /** Everything a test needs to drive one request. */
    private class Fixture(
        val coordinator: InterstitialCoordinator,
        val stats: FakeStatsRepository,
        val manager: InterstitialAdManager,
        val loader: FakeLoader,
        val dropped: MutableList<InterstitialDropReason>,
    )

    // ── fixtures ───────────────────────────────────────────────

    private val now: ZonedDateTime =
        ZonedDateTime.of(2026, 10, 19, 12, 0, 0, 0, ZoneId.of("UTC"))
    private val minute = 60_000L

    private fun fixture(
        stats: FakeStatsRepository = FakeStatsRepository(),
        sessions: Int = 3,
        reminders: FakeReminderEventsRepository = FakeReminderEventsRepository(),
        draftTracker: DraftTracker = InMemoryDraftTracker(),
        onboarded: Boolean = true,
        scope: CoroutineScope,
    ): Fixture {
        stats.sessions = sessions
        val loader = FakeLoader()
        val manager = InterstitialAdManager(
            context = FakeContext(),
            adLoader = loader,
            adUnitIdProvider = { "test_unit" },
        )
        // Preload, the way the app does when consent is granted.
        manager.loadAd()
        val dropped = mutableListOf<InterstitialDropReason>()
        val coordinator = InterstitialCoordinator(
            adManager = manager,
            statsRepository = stats,
            reminderEventsRepository = reminders,
            profileRepository = FakeProfileRepository(onboarded),
            draftTracker = draftTracker,
            timeProvider = FixedClock(now),
            scope = scope,
            onDropped = { dropped.add(it) },
        )
        return Fixture(coordinator, stats, manager, loader, dropped)
    }

    // ── tests ──────────────────────────────────────────────────

    @Test
    fun showsAndRecordsOneImpression_whenEveryConditionHolds() = runTest {
        val f = fixture(scope = this)

        var finished = false
        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { finished = true }
        // The impression is written from a launched coroutine.
        runCurrent()

        assertThat(finished).isTrue()
        assertThat(f.dropped).isEmpty()
        assertThat(f.loader.showCount).isEqualTo(1)
        assertThat(f.stats.recordCalls).isEqualTo(1)
        assertThat(f.stats.lastShownAt).isEqualTo(now.toInstant().toEpochMilli())
    }

    @Test
    fun dropsAndDoesNotRecordAnImpression_whenTheGateRejects() = runTest {
        val f = fixture(scope = this)

        // The logging screen is forbidden.
        var finished = false
        f.coordinator.requestInterstitial(FakeActivity(), "log", true) { finished = true }

        assertThat(finished).isTrue()
        assertThat(f.dropped).containsExactly(InterstitialDropReason.FORBIDDEN_SCREEN)
        // A dropped request must not consume an impression, or the daily
        // cap would be spent on ads the user never saw.
        assertThat(f.stats.recordCalls).isEqualTo(0)
        assertThat(f.stats.lastShownAt).isNull()
        assertThat(f.loader.showCount).isEqualTo(0)
    }

    @Test
    fun theRequestStillFinishes_whenTheGateRejects() = runTest {
        val f = fixture(scope = this)
        var finished = false
        f.coordinator.requestInterstitial(FakeActivity(), "log", true) { finished = true }
        // The user asked to change tab. A dropped ad must not swallow it.
        assertThat(finished).isTrue()
    }

    @Test
    fun drops_whenAnUnsavedDraftExists() = runTest {
        val draftTracker = InMemoryDraftTracker()
        val f = fixture(draftTracker = draftTracker, scope = this)

        draftTracker.setUnsavedDraft(true)
        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }

        assertThat(f.dropped).containsExactly(InterstitialDropReason.UNSAVED_DRAFT)
    }

    @Test
    fun shows_whenTheDraftIsSaved() = runTest {
        val draftTracker = InMemoryDraftTracker()
        val f = fixture(draftTracker = draftTracker, scope = this)

        draftTracker.setUnsavedDraft(true)
        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        draftTracker.setUnsavedDraft(false)
        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        runCurrent()

        // Only the first request was dropped.
        assertThat(f.dropped).containsExactly(InterstitialDropReason.UNSAVED_DRAFT)
        assertThat(f.stats.recordCalls).isEqualTo(1)
    }

    @Test
    fun drops_whenAReminderWasDeliveredWithinTheWindow() = runTest {
        val reminders = FakeReminderEventsRepository(
            deliveredAtEpochMillis = now.toInstant().toEpochMilli() - 5 * minute,
        )
        val f = fixture(reminders = reminders, scope = this)

        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }

        assertThat(f.dropped).containsExactly(InterstitialDropReason.RECENT_REMINDER_DELIVERY)
    }

    @Test
    fun shows_whenTheReminderWasDeliveredLongEnoughAgo() = runTest {
        val reminders = FakeReminderEventsRepository(
            deliveredAtEpochMillis = now.toInstant().toEpochMilli() - 45 * minute,
        )
        val f = fixture(reminders = reminders, scope = this)

        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }

        assertThat(f.dropped).isEmpty()
    }

    @Test
    fun drops_whenFewerThanThreeSessionsHaveCompleted() = runTest {
        val f = fixture(sessions = 2, scope = this)

        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }

        assertThat(f.dropped).containsExactly(InterstitialDropReason.TOO_FEW_SESSIONS)
    }

    @Test
    fun drops_whenOnboardingIsNotComplete() = runTest {
        val f = fixture(onboarded = false, scope = this)

        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }

        assertThat(f.dropped).containsExactly(InterstitialDropReason.ONBOARDING_INCOMPLETE)
    }

    @Test
    fun drops_whenConsentIsNotGranted() = runTest {
        val f = fixture(scope = this)

        f.coordinator.requestInterstitial(FakeActivity(), "home", false) { }

        assertThat(f.dropped).containsExactly(InterstitialDropReason.CONSENT_NOT_GRANTED)
    }

    @Test
    fun drops_whenTheDailyCapIsReached() = runTest {
        val stats = FakeStatsRepository()
        val f = fixture(stats = stats, scope = this)
        // Three interstitials have already been shown today, but not
        // recently, so the ten minute window is not what stops this one.
        repeat(3) {
            stats.recordInterstitialShown(
                now.toInstant().toEpochMilli() - 20 * minute,
                "2026-10-19",
            )
        }

        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }

        assertThat(f.dropped).containsExactly(InterstitialDropReason.DAILY_CAP_REACHED)
    }

    @Test
    fun aSecondRequestInsideTheWindowIsDropped() = runTest {
        val f = fixture(scope = this)

        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        runCurrent()

        // The first was shown. The second is inside the ten minute window
        // and must be dropped rather than shown again.
        assertThat(f.dropped).containsExactly(InterstitialDropReason.TOO_SOON_SINCE_LAST)
        assertThat(f.stats.recordCalls).isEqualTo(1)
    }

    @Test
    fun repeatedDroppedRequestsDoNotRecordImpressions() = runTest {
        val f = fixture(scope = this)

        repeat(5) {
            f.coordinator.requestInterstitial(FakeActivity(), "log", true) { }
        }

        assertThat(f.dropped).hasSize(5)
        assertThat(f.stats.recordCalls).isEqualTo(0)
        assertThat(f.stats.shownOnDay).isEqualTo(0)
    }

    @Test
    fun repeatedShownRequestsAreCappedByTheDailyLimit() = runTest {
        val f = fixture(scope = this)

        // The first is shown. The rest fall inside the ten minute window
        // and are dropped, so the day never exceeds its cap through this
        // path.
        repeat(5) {
            f.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        }
        runCurrent()

        assertThat(f.stats.recordCalls).isEqualTo(1)
        assertThat(f.stats.shownOnDay).isEqualTo(1)
    }

    // ── the daily cap ──────────────────────────────────────────────
    //
    // The cap is a product decision of three per day. The gate decides;
    // these tests check the whole path, so that the count the gate reads
    // is the count that was actually recorded.

    @Test
    fun theFourthInterstitialOfTheDayIsSuppressed() = runTest {
        val stats = FakeStatsRepository()
        val subject = fixture(stats = stats, scope = this)

        // Three are shown, each with enough time between them for the
        // spacing rule to be satisfied.
        repeat(3) { index ->
            stats.recordInterstitialShown(
                now.toInstant().toEpochMilli() - (3L - index) * 20 * minute,
                "2026-10-19",
            )
        }
        subject.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        runCurrent()

        assertThat(subject.dropped).containsExactly(InterstitialDropReason.DAILY_CAP_REACHED)
        assertThat(stats.recordCalls).isEqualTo(3)
    }

    @Test
    fun threeInterstitialsInADayAreAllowed() = runTest {
        val stats = FakeStatsRepository()
        val subject = fixture(stats = stats, scope = this)

        // Two already shown today, so this is the third.
        repeat(2) { index ->
            stats.recordInterstitialShown(
                now.toInstant().toEpochMilli() - (2L - index) * 20 * minute,
                "2026-10-19",
            )
        }
        subject.coordinator.requestInterstitial(FakeActivity(), "home", true) { }
        runCurrent()

        assertThat(subject.dropped).isEmpty()
        assertThat(stats.recordCalls).isEqualTo(3)
    }

    // ── the spacing rule ───────────────────────────────────────────
    //
    // This is what the old 60 second cooldown was for, and it is now the
    // ten minute rule in spec 15 §11.3 condition 3, which supersedes it.

    @Test
    fun twoInterstitialsAreNeverShownInsideTheSpacingRule() {
        // The in-memory mirror means the second request in the same
        // instant is refused even before the first impression has been
        // written to disk.
        val state = InterstitialGateState(
            onboardingComplete = true,
            completedSessions = 3,
            millisSinceLastInterstitial = 0L,
            hasUnsavedDraft = false,
            millisSinceLastReminderDelivery = 60 * minute,
            currentRoute = "home",
            adsConsentGranted = true,
            interstitialsShownToday = 0,
        )

        assertThat(InterstitialGate.evaluate(state))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.TOO_SOON_SINCE_LAST))
    }
}
