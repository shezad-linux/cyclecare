package com.cyclecare.core.domain.notification

import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * The gate stack around `decide`, in delivery order.
 *
 * A candidate that survives one gate must still survive the next, so a
 * fix to a scoring term cannot silently undo budget, quiet-hours or
 * arbitration behaviour — and vice versa. Every test pins the clock to
 * noon rather than reading ambient time.
 */
class ReminderDeliveryGatesTest {

    private val zone = ZoneId.of("UTC")

    private fun input(
        leadDays: Int = 1,
        userPinnedTime: Boolean = false,
        goal: TrackingGoal = TrackingGoal.PeriodPrep,
        quality: EstimateQuality = EstimateQuality.HIGH,
        hoursSinceLastSend: Long = 24,
        sendsLast24h: Int = 0,
        sendsLast7d: Int = 0,
        sendsInArbitrationWindow: Int = 0,
        dismissRate: Float = 0f,
        // Perfect engagement is the clean-slate baseline: the fatigue
        // term only subtracts, so a zero open rate would drag every
        // score down by twenty and make the intent of each test opaque.
        openRate: Float = 1f,
        dndFilter: Int = INTERRUPTION_FILTER_ALL,
        hour: Int = 12,
    ) = DeliveryGateInput(
        leadDays = leadDays,
        windowHalfWidthDays = 1,
        userPinnedTime = userPinnedTime,
        scheduledForEpochMillis = 1_773_900_000_000,
        targetEpochDay = 20_513,
        deepLink = ReminderDeepLinkTarget.Calendar,
        goal = goal,
        quality = quality,
        hoursSinceLastSend = hoursSinceLastSend,
        sendsLast24h = sendsLast24h,
        sendsLast7d = sendsLast7d,
        sendsInArbitrationWindow = sendsInArbitrationWindow,
        dismissRateForType = dismissRate,
        openRateForType = openRate,
        dndFilter = dndFilter,
        now = ZonedDateTime.of(2026, 3, 17, hour, 0, 0, 0, zone),
    )

    @Test
    fun `a clearly relevant candidate delivers`() {
        val outcome = evaluateDeliveryGates(ReminderType.PeriodPrep, input())

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `a candidate below the threshold is suppressed outright`() {
        // 50 urgency - 15 lead fit + 0 quality = 35, under the default 40.
        val outcome =
            evaluateDeliveryGates(ReminderType.PeriodPrep, input(leadDays = 6, quality = EstimateQuality.LOW))

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("below the delivery threshold")
    }

    @Test
    fun `insufficient history suppresses an estimate-anchored type`() {
        val outcome =
            evaluateDeliveryGates(ReminderType.PeriodPrep, input(quality = EstimateQuality.INSUFFICIENT_HISTORY))

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
    }

    @Test
    fun `the daily budget raises the threshold above a good score`() {
        // 50 + 10 + 10 - 25 fatigue/budget = 45, still under the 75 over-budget bar.
        val outcome = evaluateDeliveryGates(ReminderType.PeriodPrep, input(sendsLast24h = 3))

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("daily budget exhausted")
    }

    @Test
    fun `a pinned time clears a lower threshold`() {
        // The same week-out candidate that failed the default threshold
        // clears the pinned bar: 35 >= 25.
        val outcome = evaluateDeliveryGates(
            ReminderType.PeriodPrep,
            input(leadDays = 6, quality = EstimateQuality.LOW, userPinnedTime = true),
        )

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `the weekly budget suppresses even a high scorer`() {
        val outcome = evaluateDeliveryGates(ReminderType.PeriodPrep, input(sendsLast7d = MAX_SENDS_PER_WEEK))

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("weekly budget exhausted")
    }

    @Test
    fun `quiet hours defer a standard send and exempt LatePeriod`() {
        val deferred = evaluateDeliveryGates(ReminderType.PeriodPrep, input(hour = 23))
        val late = evaluateDeliveryGates(ReminderType.LatePeriod, input(hour = 23))

        assertThat(deferred).isInstanceOf(DeliveryGateOutcome.Defer::class.java)
        assertThat((deferred as DeliveryGateOutcome.Defer).reason).isEqualTo("quiet hours")
        assertThat(late).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `do not disturb defers a standard send and exempts LatePeriod`() {
        val deferred = evaluateDeliveryGates(ReminderType.PeriodPrep, input(dndFilter = INTERRUPTION_FILTER_PRIORITY))
        val late = evaluateDeliveryGates(ReminderType.LatePeriod, input(dndFilter = INTERRUPTION_FILTER_PRIORITY))

        assertThat(deferred).isInstanceOf(DeliveryGateOutcome.Defer::class.java)
        assertThat((deferred as DeliveryGateOutcome.Defer).reason).isEqualTo("do not disturb")
        assertThat(late).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `a third candidate inside the arbitration window defers`() {
        val third = evaluateDeliveryGates(ReminderType.PeriodPrep, input(sendsInArbitrationWindow = MAX_ARBITRATION_BATCH))
        val second = evaluateDeliveryGates(ReminderType.PeriodPrep, input(sendsInArbitrationWindow = MAX_ARBITRATION_BATCH - 1))

        assertThat(third).isInstanceOf(DeliveryGateOutcome.Defer::class.java)
        assertThat((third as DeliveryGateOutcome.Defer).reason).isEqualTo("arbitration")
        assertThat(second).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `quiet hours end early in the window the same day and late in it the next day`() {
        val quietHours = QuietHours.Default
        val early = ZonedDateTime.of(2026, 3, 17, 7, 30, 0, 0, zone)
        val late = ZonedDateTime.of(2026, 3, 17, 23, 5, 0, 0, zone)

        val earlyEnd = quietHoursEndEpochMillis(early)
        val lateEnd = quietHoursEndEpochMillis(late)

        val endHour = 8
        assertThat(quietHours.endHour).isEqualTo(endHour)
        assertThat(quietHours.startHour).isEqualTo(DEFAULT_QUIET_HOURS_START_HOUR)
        assertThat(earlyEnd)
            .isEqualTo(early.withHour(endHour).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant().toEpochMilli())
        assertThat(lateEnd)
            .isEqualTo(late.plusDays(1).withHour(endHour).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant().toEpochMilli())
    }
}
