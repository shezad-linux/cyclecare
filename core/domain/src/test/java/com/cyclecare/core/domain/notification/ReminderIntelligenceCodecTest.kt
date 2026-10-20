package com.cyclecare.core.domain.notification

import com.cyclecare.core.model.DeferredReminder
import com.cyclecare.core.model.ReminderAutoDisabled
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The persisted form of the notification intelligence.
 *
 * Spec `13` §9: a type the app paused has to stay paused after a
 * restart, or it was never paused at all. The record lives in a
 * key/value store, so it has to survive a round trip through the
 * encoding — and the counts that explain the pause have to survive it
 * too, because they are what the user is shown.
 *
 * The round trip is pure, so it is testable without a database.
 */
class ReminderIntelligenceCodecTest {

    @Test
    fun `an auto-disabled type survives the round trip with its counts`() {
        val records = listOf(
            ReminderAutoDisabled(
                type = ReminderType.PeriodPrep,
                dismissedCount = 7,
                openedCount = 1,
                disabledAtEpochMillis = 1_773_900_000_000,
            ),
        )

        val encoded = ReminderIntelligenceCodec.encodeAutoDisabled(records)
        val decoded = ReminderIntelligenceCodec.decodeAutoDisabled(encoded)

        assertThat(decoded).isEqualTo(records)
    }

    @Test
    fun `several auto-disabled types survive the round trip`() {
        val records = listOf(
            ReminderAutoDisabled(ReminderType.PeriodPrep, 7, 1, 1_773_900_000_000),
            ReminderAutoDisabled(ReminderType.DailyLog, 6, 0, 1_773_900_001_000),
            ReminderAutoDisabled(ReminderType.LatePeriod, 9, 2, 1_773_900_002_000),
        )

        val decoded = ReminderIntelligenceCodec.decodeAutoDisabled(
            ReminderIntelligenceCodec.encodeAutoDisabled(records),
        )

        assertThat(decoded).isEqualTo(records)
    }

    @Test
    fun `an empty record list round trips to nothing`() {
        val encoded = ReminderIntelligenceCodec.encodeAutoDisabled(emptyList())

        assertThat(encoded).isEmpty()
        assertThat(ReminderIntelligenceCodec.decodeAutoDisabled(encoded)).isEmpty()
        assertThat(ReminderIntelligenceCodec.decodeAutoDisabled(null)).isEmpty()
    }

    @Test
    fun `a corrupt entry is dropped rather than taking the list with it`() {
        val encoded = "NotAType,1,2,3;PeriodPrep,7,1,1773900000000"

        val decoded = ReminderIntelligenceCodec.decodeAutoDisabled(encoded)

        assertThat(decoded).hasSize(1)
        assertThat(decoded.single().type).isEqualTo(ReminderType.PeriodPrep)
    }

    @Test
    fun `a deferred candidate survives the round trip`() {
        val deferred = listOf(
            DeferredReminder(
                ruleId = "period",
                type = ReminderType.PeriodPrep,
                scheduledAtEpochMillis = 1_773_900_000_000,
                reason = "quiet hours",
            ),
        )

        val decoded = ReminderIntelligenceCodec.decodeDeferred(
            ReminderIntelligenceCodec.encodeDeferred(deferred),
        )

        assertThat(decoded).isEqualTo(deferred)
    }

    @Test
    fun `a deferred candidate whose reason holds a separator still round trips`() {
        val deferred = listOf(
            DeferredReminder(
                ruleId = "period",
                type = ReminderType.PeriodPrep,
                scheduledAtEpochMillis = 1_773_900_000_000,
                reason = "quiet hours, do not disturb",
            ),
        )

        val encoded = ReminderIntelligenceCodec.encodeDeferred(deferred)

        assertThat(ReminderIntelligenceCodec.decodeDeferred(encoded)).isEqualTo(deferred)
    }
}
