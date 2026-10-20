package com.cyclecare.core.domain.notification

import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spec `13` §8: a pinned send time is never overridden.
 *
 * Two things can try to move a reminder's hour: the learned send time
 * the engagement profile suggests, and the scorer's learned-time bonus.
 * Neither may touch a time the user chose, so both rules live in one
 * place and are asserted here rather than being an assumption spread
 * across the scheduling code.
 */
class ReminderSendTimeTest {

    private fun rule(
        hourOfDay: Int = 9,
        userPinnedTime: Boolean = false,
    ) = ReminderRule(
        id = ReminderType.PeriodPrep.name,
        type = ReminderType.PeriodPrep,
        enabled = true,
        daysBefore = -2,
        hourOfDay = hourOfDay,
        minute = 0,
        userPinnedTime = userPinnedTime,
    )

    @Test
    fun `a pinned time is never overridden by a learned hour`() {
        val hour = resolveSendHour(
            userPinnedTime = true,
            ruleHourOfDay = 9,
            learnedHourOfDay = 21,
        )

        assertThat(hour).isEqualTo(9)
    }

    @Test
    fun `an unpinned time takes the learned hour`() {
        val hour = resolveSendHour(
            userPinnedTime = false,
            ruleHourOfDay = 9,
            learnedHourOfDay = 21,
        )

        assertThat(hour).isEqualTo(21)
    }

    @Test
    fun `an unpinned time with no learned hour keeps its own`() {
        val hour = resolveSendHour(
            userPinnedTime = false,
            ruleHourOfDay = 9,
            learnedHourOfDay = null,
        )

        assertThat(hour).isEqualTo(9)
    }

    @Test
    fun `the rule for a rule object reads the pinned flag off the rule`() {
        val pinned = resolveSendHour(rule(hourOfDay = 7, userPinnedTime = true), learnedHourOfDay = 21)
        val unpinned = resolveSendHour(rule(hourOfDay = 7, userPinnedTime = false), learnedHourOfDay = 21)

        assertThat(pinned).isEqualTo(7)
        assertThat(unpinned).isEqualTo(21)
    }

    @Test
    fun `the learned-time bonus never applies to a pinned time`() {
        val pinned = sendsAtLearnedTime(
            userPinnedTime = true,
            learnedHourOfDay = 21,
            scheduledHourOfDay = 21,
        )
        val unpinned = sendsAtLearnedTime(
            userPinnedTime = false,
            learnedHourOfDay = 21,
            scheduledHourOfDay = 21,
        )
        val differentHour = sendsAtLearnedTime(
            userPinnedTime = false,
            learnedHourOfDay = 21,
            scheduledHourOfDay = 9,
        )

        assertThat(pinned).isFalse()
        assertThat(unpinned).isTrue()
        assertThat(differentHour).isFalse()
    }

    @Test
    fun `a pinned candidate is scored against the lower pinned threshold`() {
        val pinned = ReminderCandidate(
            type = ReminderType.PeriodPrep,
            ruleId = "period",
            leadDays = 2,
            hourOfDay = 9,
            userPinnedTime = true,
            scheduledForEpochMillis = 1_773_900_000_000,
            targetEpochDay = 20_513,
            windowHalfWidthDays = 1,
            deepLink = com.cyclecare.core.model.ReminderDeepLinkTarget.Calendar,
        )
        val input = RelevanceInput(
            candidate = pinned,
            goal = com.cyclecare.core.model.TrackingGoal.PeriodPrep,
            quality = com.cyclecare.core.domain.prediction.EstimateQuality.HIGH,
            hoursSinceLastSend = 24,
            sendsLast24h = 0,
            sendsLast7d = 0,
            dismissRateForType = 0f,
            openRateForType = 1f,
            sendsLearnedTime = false,
        )

        assertThat(thresholdFor(input)).isEqualTo(SEND_THRESHOLD_PINNED_TIME)
    }
}
