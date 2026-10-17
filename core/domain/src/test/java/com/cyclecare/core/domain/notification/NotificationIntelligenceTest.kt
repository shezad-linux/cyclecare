package com.cyclecare.core.domain.notification

import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationIntelligenceTest {
    private fun candidate(
        type: ReminderType = ReminderType.PeriodPrep,
        leadDays: Int = 2,
        userPinnedTime: Boolean = false,
    ) = ReminderCandidate(
        type = type,
        ruleId = "rule-${type.name}",
        leadDays = leadDays,
        hourOfDay = 9,
        userPinnedTime = userPinnedTime,
        scheduledForEpochMillis = 0L,
        targetEpochDay = null,
        windowHalfWidthDays = 2,
        deepLink = ReminderDeepLinkTarget.Home,
    )

    private fun input(
        candidate: ReminderCandidate = candidate(),
        goal: TrackingGoal = TrackingGoal.PeriodPrep,
        quality: EstimateQuality = EstimateQuality.MODERATE,
        hoursSinceLastSend: Long = 24,
        sendsLast24h: Int = 0,
        dismissRate: Float = 0f,
        openRate: Float = 1f,
        learnedTime: Boolean = false,
    ) = RelevanceInput(
        candidate = candidate,
        goal = goal,
        quality = quality,
        hoursSinceLastSend = hoursSinceLastSend,
        sendsLast24h = sendsLast24h,
        sendsLast7d = 0,
        dismissRateForType = dismissRate,
        openRateForType = openRate,
        sendsLearnedTime = learnedTime,
    )

    // ── urgency ─────────────────────────────────────────────────────

    @Test
    fun periodPrepOutranksEveryOtherType() {
        val scores = ReminderType.entries
            .filter { it != ReminderType.Hydration && it != ReminderType.Pill }
            .map { urgencyBaseFor(it) }

        assertThat(urgencyBaseFor(ReminderType.PeriodPrep)).isGreaterThan(urgencyBaseFor(ReminderType.LatePeriod))
        assertThat(urgencyBaseFor(ReminderType.LatePeriod))
            .isGreaterThan(urgencyBaseFor(ReminderType.FertileWindowStart))
        assertThat(scores).containsNoDuplicates()
    }

    @Test
    fun dailyLogIsTheLeastUrgentRealType() {
        assertThat(urgencyBaseFor(ReminderType.DailyLog))
            .isLessThan(urgencyBaseFor(ReminderType.PeriodPrep))
    }

    // ── channels ────────────────────────────────────────────────────

    @Test
    fun channelMappingMatchesTheExistingThreeChannels() {
        assertThat(channelFor(ReminderType.PeriodPrep).name).isEqualTo("CycleAlerts")
        assertThat(channelFor(ReminderType.LatePeriod).name).isEqualTo("CycleAlerts")
        assertThat(channelFor(ReminderType.FertileWindowStart).name).isEqualTo("FertilityAlerts")
        assertThat(channelFor(ReminderType.OvulationEstimate).name).isEqualTo("FertilityAlerts")
        assertThat(channelFor(ReminderType.DailyLog).name).isEqualTo("DailyCheckIn")
    }

    // ── quality gate ────────────────────────────────────────────────

    @Test
    fun insufficientHistorySuppressesEstimateDerivedReminders() {
        val score = relevanceScore(input(quality = EstimateQuality.INSUFFICIENT_HISTORY))

        assertThat(score).isLessThan(SEND_THRESHOLD)
        assertThat(decide(input(quality = EstimateQuality.INSUFFICIENT_HISTORY)).deliver).isFalse()
    }

    @Test
    fun aStrongCandidateIsDelivered() {
        val decision = decide(input())

        assertThat(decision.deliver).isTrue()
        assertThat(decision.suppressedBecause).isNull()
    }

    // ── goal alignment ──────────────────────────────────────────────

    @Test
    fun fertilityReminderIsPenalisedForANonConceptionGoal() {
        val aligned = relevanceScore(
            input(
                candidate = candidate(type = ReminderType.FertileWindowStart, leadDays = 1),
                goal = TrackingGoal.ConceptionAwareness,
            ),
        )
        val mismatched = relevanceScore(
            input(
                candidate = candidate(type = ReminderType.FertileWindowStart, leadDays = 1),
                goal = TrackingGoal.PeriodPrep,
            ),
        )

        assertThat(mismatched).isLessThan(aligned)
    }

    @Test
    fun fertilityReminderIsSuppressedEntirelyForANonConceptionGoal() {
        val decision = decide(
            input(
                candidate = candidate(type = ReminderType.FertileWindowStart, leadDays = 1),
                goal = TrackingGoal.PeriodPrep,
            ),
        )

        assertThat(decision.deliver).isFalse()
    }

    // ── lead fit ────────────────────────────────────────────────────

    @Test
    fun anOverlyEarlyPeriodReminderScoresWorseThanATimelyOne() {
        val timely = relevanceScore(input(candidate = candidate(leadDays = 2)))
        val tooEarly = relevanceScore(input(candidate = candidate(leadDays = 9)))

        assertThat(tooEarly).isLessThan(timely)
    }

    // ── recency and fatigue ─────────────────────────────────────────

    @Test
    fun aRecentSendPenalisesTheNextCandidate() {
        val fresh = relevanceScore(input(hoursSinceLastSend = 24))
        val justSent = relevanceScore(input(hoursSinceLastSend = 1))

        assertThat(justSent).isLessThan(fresh)
    }

    @Test
    fun consistentlyDismissedTypeIsProgressivelySuppressed() {
        val engaged = relevanceScore(input(dismissRate = 0f, openRate = 1f))
        val ignored = relevanceScore(input(dismissRate = 0.9f, openRate = 0.1f))

        assertThat(ignored).isLessThan(engaged)
    }

    // ── budget ──────────────────────────────────────────────────────

    @Test
    fun theDailyBudgetLowersTheBarForDelivery() {
        val underBudget = relevanceScore(input(sendsLast24h = 0))
        val atBudget = relevanceScore(input(sendsLast24h = MAX_SENDS_PER_DAY))

        assertThat(atBudget).isLessThan(underBudget)
    }

    @Test
    fun aWeakCandidateIsDroppedOnceTheBudgetIsSpent() {
        val weak = input(
            candidate = candidate(type = ReminderType.DailyLog),
            sendsLast24h = MAX_SENDS_PER_DAY,
        )

        assertThat(decide(weak).deliver).isFalse()
        assertThat(decide(weak).suppressedBecause).isEqualTo("daily budget exhausted")
    }

    // ── pinned time ─────────────────────────────────────────────────

    @Test
    fun aPinnedTimeLowersTheThresholdSoUserIntentIsHonoured() {
        val pinned = input(candidate = candidate(userPinnedTime = true))
        val learned = input(candidate = candidate(userPinnedTime = false))

        assertThat(thresholdFor(pinned)).isEqualTo(SEND_THRESHOLD_PINNED_TIME)
        assertThat(thresholdFor(learned)).isEqualTo(SEND_THRESHOLD)
    }

    @Test
    fun learnedTimeBonusNeverAppliesToAPinnedTime() {
        assertThat(learnedTimeBonus(sendsLearnedTime = true, userPinnedTime = true)).isEqualTo(0)
        assertThat(learnedTimeBonus(sendsLearnedTime = true, userPinnedTime = false)).isEqualTo(8)
        assertThat(learnedTimeBonus(sendsLearnedTime = false, userPinnedTime = false)).isEqualTo(0)
    }

    // ── quiet hours ─────────────────────────────────────────────────

    @Test
    fun quietHoursWrapAcrossMidnight() {
        val quiet = QuietHours(startHour = 22, endHour = 8)

        assertThat(quiet.contains(23)).isTrue()
        assertThat(quiet.contains(2)).isTrue()
        assertThat(quiet.contains(7)).isTrue()
        assertThat(quiet.contains(12)).isFalse()
    }

    @Test
    fun quietHoursHandleAnOvernightWrapInBothDirections() {
        val morningToEvening = QuietHours(startHour = 8, endHour = 22)

        assertThat(morningToEvening.contains(9)).isTrue()
        assertThat(morningToEvening.contains(21)).isTrue()
        assertThat(morningToEvening.contains(23)).isFalse()
        assertThat(morningToEvening.contains(3)).isFalse()
    }

    // ── engagement ──────────────────────────────────────────────────

    @Test
    fun noLearnedHourBelowTheInteractionFloor() {
        val sparse = EngagementProfile(List(24) { 0 }, interactionCount = MIN_INTERACTIONS_FOR_LEARNING - 1)

        assertThat(sparse.peakHour(QuietHours.Default)).isNull()
    }

    @Test
    fun theEngagementPeakIsChosen() {
        val histogram = MutableList(24) { 0 }.also {
            it[7] = 3
            it[13] = 30
            it[21] = 20
        }

        val profile = EngagementProfile(histogram, interactionCount = 32)

        assertThat(profile.peakHour(QuietHours.Default)).isEqualTo(13)
    }

    @Test
    fun thePeakNeverFallsInsideQuietHours() {
        val histogram = MutableList(24) { 0 }.also {
            it[23] = 50
            it[9] = 10
        }

        val profile = EngagementProfile(histogram, interactionCount = 60)

        assertThat(profile.peakHour(QuietHours.Default)).isEqualTo(9)
    }
}