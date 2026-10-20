package com.cyclecare.app

import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P9-08: no delivery log attribute is a timestamp or a date.
 *
 * A reminder's scheduled instant is derived from the predicted period
 * date, so logging it — as an epoch value, as a formatted date, or as
 * something that reconstructs one — would put a cycle date into the
 * diagnostics. `04` §6 forbids that: diagnostics must never carry period
 * dates, symptoms, notes, medication or free text.
 *
 * The attributes are built in one place, so the rule is checkable: every
 * builder is asked for its map, and nothing in any of them may be a
 * timestamp or a date.
 */
class ReminderDeliveryLogTest {

    private val type = ReminderType.PeriodPrep
    private val channelKey = ReminderChannelKey.CycleAlerts

    @Test
    fun `no delivery log attribute carries a timestamp`() {
        for ((event, attributes) in allAttributeMaps()) {
            for ((key, value) in attributes) {
                assertThat(EPOCH_MILLIS.containsMatchIn(value)).isFalse()
                assertThat(ISO_TIMESTAMP.containsMatchIn(value)).isFalse()
            }
        }
    }

    @Test
    fun `no delivery log attribute carries a date`() {
        for ((event, attributes) in allAttributeMaps()) {
            for ((key, value) in attributes) {
                assertThat(ISO_DATE.containsMatchIn(value)).isFalse()
                assertThat(SLASHED_DATE.containsMatchIn(value)).isFalse()
                assertThat(MONTH_NAME.containsMatchIn(value)).isFalse()
                assertThat(value).doesNotContain("/")
            }
        }
    }

    @Test
    fun `the buckets that are logged are the ones that carry no date`() {
        val attributes = ReminderDeliveryLog.success(
            ruleId = "period",
            type = type,
            channelKey = channelKey,
            leadDays = 2,
            deliveryDelayMinutes = 3,
            score = 70,
            threshold = 40,
        )

        // A distance, a duration and the scorer's own numbers: not an
        // instant.
        assertThat(attributes["leadDays"]).isEqualTo("2")
        assertThat(attributes["deliveryDelayMinutes"]).isEqualTo("3")
        assertThat(attributes["score"]).isEqualTo("70")
        assertThat(attributes["threshold"]).isEqualTo("40")
        assertThat(attributes.values).doesNotContain("2026-03-17")
    }

    @Test
    fun `a deferral logs the hour it will end, not the instant`() {
        val attributes = ReminderDeliveryLog.deferred(
            ruleId = "period",
            type = type,
            reason = "quiet hours",
            deferredCount = 2,
            deferUntilHour = 8,
        )

        assertThat(attributes["deferUntilHour"]).isEqualTo("8")
        assertThat(attributes["deferredCount"]).isEqualTo("2")
        assertThat(attributes.values).doesNotContain("2026-03-17")
    }

    /**
     * Every attribute map the delivery path can log.
     *
     * The list is explicit on purpose: a new log line that is not added
     * here is not covered, so the list has to grow with the code.
     */
    private fun allAttributeMaps(): List<Pair<String, Map<String, String>>> = listOf(
        "delivery_routed_in_app" to ReminderDeliveryLog.routedInApp("period", type),
        "delivery_skipped_channel_disabled" to
            ReminderDeliveryLog.skippedChannelDisabled("period", type, channelKey),
        "delivery_skipped_notifications_unavailable" to
            ReminderDeliveryLog.skippedNotificationsUnavailable("period", type, channelKey),
        "delivery_skipped_missing_channel_settings" to
            ReminderDeliveryLog.skippedMissingChannelSettings("period", type, channelKey),
        "delivery_suppressed" to ReminderDeliveryLog.suppressed("period", type, "below the delivery threshold"),
        "delivery_deferred" to ReminderDeliveryLog.deferred("period", type, "quiet hours", 1, 8),
        "delivery_deduplicated" to ReminderDeliveryLog.deduplicated("period", type),
        "delivery_success" to
            ReminderDeliveryLog.success("period", type, channelKey, 2, 3, 70, 40),
        "delivery_failed" to ReminderDeliveryLog.failed("period", type, channelKey),
        "reminder_type_auto_disabled" to ReminderDeliveryLog.autoDisabled(type, 10, 7, 1),
        "auto_disable_failed" to ReminderDeliveryLog.autoDisableFailed(type),
        "reminder_type_re_enabled" to ReminderDeliveryLog.reEnabled(type),
        "refresh_after_delivery_failed" to ReminderDeliveryLog.refreshFailed("period", "posted"),
    )

    private companion object {
        /** A raw epoch value: thirteen digits. */
        val EPOCH_MILLIS = Regex("""\b\d{13}\b""")

        /** An ISO-8601 instant, with or without a zone. */
        val ISO_TIMESTAMP = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}""")

        /** An ISO-8601 date. */
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        /** A date with slashes. */
        val SLASHED_DATE = Regex("""\d{1,2}/\d{1,2}/\d{2,4}""")

        /** A month written out, the way a formatter would write it. */
        val MONTH_NAME = Regex(
            """(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b""",
        )
    }
}
