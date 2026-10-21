package com.cyclecare.app

import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderType

/**
 * The delivery path's diagnostic attributes, in one place.
 *
 * Spec `04` §6: diagnostics must never carry period dates, symptoms,
 * notes, medication or free text. A scheduled reminder's instant is
 * derived from the predicted period date, so it is never logged — not as
 * an epoch value, not as a formatted date, and not as a measurement that
 * would reconstruct one.
 *
 * What is logged instead is a bucket: the lead in days, the hour of the
 * day, a class of lateness, a count, a reason from a closed set. None of
 * them can be reversed back into a date on the device.
 *
 * Keeping the attributes here rather than inline at each call site means
 * the rule is checkable: a test can ask every builder for its map and
 * assert that nothing in it is a timestamp or a date.
 */
internal object ReminderDeliveryLog {

    /** The tag every delivery diagnostic is logged under. */
    const val TAG = "CycleCareReminder"

    /** The reminder was shown in the app instead of as a notification. */
    fun routedInApp(ruleId: String, type: ReminderType): Map<String, String> =
        mapOf(
            "ruleId" to ruleId,
            "type" to type.name,
        )

    /** The channel the reminder would have used is switched off. */
    fun skippedChannelDisabled(
        ruleId: String,
        type: ReminderType,
        channelKey: ReminderChannelKey,
    ): Map<String, String> = mapOf(
        "ruleId" to ruleId,
        "type" to type.name,
        "channelKey" to channelKey.name,
    )

    /** The system will not accept a notification right now. */
    fun skippedNotificationsUnavailable(
        ruleId: String,
        type: ReminderType,
        channelKey: ReminderChannelKey?,
    ): Map<String, String> = mapOf(
        "ruleId" to ruleId,
        "type" to type.name,
        "channelKey" to (channelKey?.name ?: "none"),
    )

    /** There is no channel settings row for this reminder's channel. */
    fun skippedMissingChannelSettings(
        ruleId: String,
        type: ReminderType,
        channelKey: ReminderChannelKey,
    ): Map<String, String> = mapOf(
        "ruleId" to ruleId,
        "type" to type.name,
        "channelKey" to channelKey.name,
    )

    /**
     * The candidate did not pass the gates.
     *
     * The reason comes from a closed set the domain owns, so it names a
     * rule and never a date.
     */
    fun suppressed(ruleId: String, type: ReminderType, reason: String): Map<String, String> =
        mapOf(
            "ruleId" to ruleId,
            "type" to type.name,
            "reason" to reason,
        )

    /**
     * The candidate was deferred into quiet hours, do-not-disturb or an
     * arbitration fold.
     *
     * `deferUntilHour` is the hour of the day the summary will post, not
     * the instant it will post: an hour is a bucket, an instant is a
     * date.
     */
    fun deferred(
        ruleId: String,
        type: ReminderType,
        reason: String,
        deferredCount: Int,
        deferUntilHour: Int,
    ): Map<String, String> = mapOf(
        "ruleId" to ruleId,
        "type" to type.name,
        "reason" to reason,
        "deferredCount" to deferredCount.toString(),
        "deferUntilHour" to deferUntilHour.toString(),
    )

    /** The delivery had already been recorded; nothing was posted again. */
    fun deduplicated(ruleId: String, type: ReminderType): Map<String, String> =
        mapOf(
            "ruleId" to ruleId,
            "type" to type.name,
        )

    /**
     * The notification was posted.
     *
     * `score` and `threshold` are the intelligence module's own numbers,
     * so the log records the decision and the bar it cleared. `leadDays`
     * is how far ahead the reminder was planned: a length between the
     * reminder and the predicted date, so it names no day and anchors
     * nothing.
     *
     * `deliveryLatency` is a class rather than a measurement. The log
     * line is emitted at the delivery instant, so a delay in minutes is
     * a subtraction: take it off the time the line carries and the
     * scheduled instant comes back — and for a `PeriodPrep` reminder
     * that instant is the predicted period date minus the lead time. A
     * class cannot be subtracted, so the log says *that* a delivery was
     * late without saying by how much.
     */
    fun success(
        ruleId: String,
        type: ReminderType,
        channelKey: ReminderChannelKey,
        leadDays: Int,
        deliveryLatency: DeliveryLatency,
        score: Int,
        threshold: Int,
    ): Map<String, String> = mapOf(
        "ruleId" to ruleId,
        "type" to type.name,
        "channelKey" to channelKey.name,
        "leadDays" to leadDays.toString(),
        "deliveryLatency" to deliveryLatency.name,
        "score" to score.toString(),
        "threshold" to threshold.toString(),
    )

    /** The post itself failed. */
    fun failed(
        ruleId: String,
        type: ReminderType,
        channelKey: ReminderChannelKey,
    ): Map<String, String> = mapOf(
        "ruleId" to ruleId,
        "type" to type.name,
        "channelKey" to channelKey.name,
    )

    /** A type was paused because the user was not opening it. */
    fun autoDisabled(
        type: ReminderType,
        impressions: Int,
        dismissed: Int,
        opened: Int,
    ): Map<String, String> = mapOf(
        "type" to type.name,
        "impressions" to impressions.toString(),
        "dismissed" to dismissed.toString(),
        "opened" to opened.toString(),
    )

    /** The pause could not be recorded. */
    fun autoDisableFailed(type: ReminderType): Map<String, String> =
        mapOf("type" to type.name)

    /** The user asked for a paused type back. */
    fun reEnabled(type: ReminderType): Map<String, String> =
        mapOf("type" to type.name)

    /** The refresh after a delivery could not run. */
    fun refreshFailed(ruleId: String, outcome: String): Map<String, String> =
        mapOf(
            "ruleId" to ruleId,
            "outcome" to outcome,
        )
}

/**
 * How late a delivery was.
 *
 * A class, not a measurement. The delivery diagnostic is written at the
 * moment of delivery, so a delay in minutes is a subtraction: subtract
 * it from the time the log line carries and the scheduled instant comes
 * back. For a `PeriodPrep` or `LatePeriod` reminder that instant is the
 * predicted period date minus the lead time, so the figure would be the
 * derived cycle date wearing a duration's clothes.
 *
 * A class cannot be subtracted. It says that the delivery was late
 * without saying by how much, which is the part of the answer a reader
 * of the diagnostics actually needs: on time, or not.
 */
internal enum class DeliveryLatency {
    /** Within the tolerance of the scheduled instant. */
    OnTime,

    /** Later than the tolerance. */
    Late,
}

/**
 * The lateness class for a delivery that was [delayMillis] late.
 *
 * The tolerance is a tolerance and not a measurement. A delivery inside
 * it has told the reader nothing the log line's own time does not
 * already say, and a delivery outside it is reported as late without the
 * figure that would place it.
 */
internal fun deliveryLatencyOf(delayMillis: Long): DeliveryLatency =
    if (delayMillis <= ON_TIME_TOLERANCE_MILLIS) DeliveryLatency.OnTime else DeliveryLatency.Late

/**
 * How late a delivery may be and still count as on time.
 *
 * Five minutes: an exact alarm is expected within a minute or two, and
 * the retry path already waits that long, so a delivery later than this
 * was deferred — a downgraded alarm, a dozing device, or a post that
 * failed once.
 */
private const val ON_TIME_TOLERANCE_MILLIS = 5 * 60_000L
