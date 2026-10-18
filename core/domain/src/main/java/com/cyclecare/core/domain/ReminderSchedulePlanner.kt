package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.ScheduledReminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.absoluteValue

class ReminderSchedulePlanner(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun buildSchedules(
        prediction: PredictionSnapshot?,
        rules: List<ReminderRule>,
        cycles: List<CycleRecord> = emptyList(),
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
    ): List<ScheduledReminder> = rules
        .filter { it.enabled }
        .distinctBy { it.id }
        .mapNotNull { rule ->
            when (rule.type) {
                ReminderType.PeriodPrep -> buildPeriodPrep(rule, prediction, now)
                ReminderType.FertileWindowStart -> buildFertileWindow(rule, prediction, now)
                ReminderType.OvulationEstimate -> buildOvulationReminder(rule, prediction, now)
                ReminderType.DailyLog -> buildDailyLog(rule, now)
                ReminderType.LatePeriod -> buildLatePeriod(rule, prediction, cycles, now)
                ReminderType.Hydration,
                ReminderType.Pill,
                -> null
            }
        }
        .sortedBy { it.scheduledAt.toInstant() }

    /**
     * Earliest date the next period could plausibly start.
     *
     * Reminders used to be anchored to the centre of the estimate. That
     * asserts a precision the app does not have: if the engine says the
     * next period falls between the 24th and the 29th, "your period may
     * start in 2 days" sent on the 22nd may be four days early.
     * Anchoring to the earliest plausible start means the reminder
     * arrives while the answer is still open rather than after it has
     * already resolved.
     *
     * Spec: 13-notification-intelligence.md section 6.
     */
    private fun earliestPlausibleStart(prediction: PredictionSnapshot): LocalDate =
        prediction.nextPeriodDate.minusDays(
            (prediction.nextPeriodHalfWidthDays ?: 0).toLong(),
        )

    /**
     * True when the engine had too little history to support an estimate
     * worth interrupting someone about.
     *
     * Below three intervals there is nothing to say, so prediction-derived
     * reminders are withheld rather than sent from a default assumption.
     */
    private fun PredictionSnapshot.hasSupportableEstimate(): Boolean =
        supportsRange && nextPeriodHalfWidthDays != null

    private fun buildPeriodPrep(
        rule: ReminderRule,
        prediction: PredictionSnapshot?,
        now: ZonedDateTime,
    ): ScheduledReminder? {
        val predictionSnapshot = prediction ?: return null
        // With too little history the estimate rests on an assumption, so
        // nothing is scheduled. A DailyLog nudge covers the gap without
        // asserting a date the app cannot support.
        if (!predictionSnapshot.hasSupportableEstimate()) return null

        val targetDate = earliestPlausibleStart(predictionSnapshot).plusDays(rule.daysBefore.toLong())
        val scheduledAt = scheduleDateTime(targetDate, rule, now)
        if (!scheduledAt.isAfter(now)) return null

        val halfWidth = predictionSnapshot.nextPeriodHalfWidthDays ?: 0
        val leadDays = rule.daysBefore.absoluteValue
        val body = when {
            halfWidth <= 1 && leadDays == 0 ->
                "Your period may start tomorrow."
            halfWidth <= 1 ->
                "Your period may start in $leadDays days. Prepare if helpful."
            else ->
                "Your period is likely between ${earliestPlausibleStart(predictionSnapshot)} " +
                    "and ${predictionSnapshot.nextPeriodDate}."
        }
        return ScheduledReminder(
            uniqueWorkName = reminderWorkName(rule.id),
            ruleId = rule.id,
            type = rule.type,
            channelKey = reminderTypeChannelKey(rule.type),
            scheduledAt = scheduledAt,
            title = "Period reminder",
            body = body,
            privateBody = "Cycle reminder available. Open the app to review it.",
            deepLinkTarget = ReminderDeepLinkTarget.Home,
        )
    }

    private fun buildFertileWindow(
        rule: ReminderRule,
        prediction: PredictionSnapshot?,
        now: ZonedDateTime,
    ): ScheduledReminder? {
        val predictionSnapshot = prediction ?: return null
        if (!predictionSnapshot.hasSupportableEstimate()) return null
        val targetDate = predictionSnapshot.fertileWindow.start.plusDays(rule.daysBefore.toLong())
        val scheduledAt = scheduleDateTime(targetDate, rule, now)
        if (!scheduledAt.isAfter(now)) return null

        return ScheduledReminder(
            uniqueWorkName = reminderWorkName(rule.id),
            ruleId = rule.id,
            type = rule.type,
            channelKey = reminderTypeChannelKey(rule.type),
            scheduledAt = scheduledAt,
            title = "Fertile window reminder",
            body = "Your fertile window estimate begins today. Open CycleCare to review the calendar.",
            privateBody = "Cycle reminder available. Open the app to review it.",
            deepLinkTarget = ReminderDeepLinkTarget.Calendar,
            targetEpochDay = targetDate.toEpochDay(),
        )
    }

    private fun buildOvulationReminder(
        rule: ReminderRule,
        prediction: PredictionSnapshot?,
        now: ZonedDateTime,
    ): ScheduledReminder? {
        val predictionSnapshot = prediction ?: return null
        val targetDate = predictionSnapshot.ovulationDate.plusDays(rule.daysBefore.toLong())
        val scheduledAt = scheduleDateTime(targetDate, rule, now)
        if (!scheduledAt.isAfter(now)) return null

        return ScheduledReminder(
            uniqueWorkName = reminderWorkName(rule.id),
            ruleId = rule.id,
            type = rule.type,
            channelKey = reminderTypeChannelKey(rule.type),
            scheduledAt = scheduledAt,
            title = "Ovulation reminder",
            body = "Today matches your current ovulation estimate. Open CycleCare to review the calendar.",
            privateBody = "Cycle reminder available. Open the app to review it.",
            deepLinkTarget = ReminderDeepLinkTarget.Calendar,
            targetEpochDay = targetDate.toEpochDay(),
        )
    }

    private fun buildDailyLog(
        rule: ReminderRule,
        now: ZonedDateTime,
    ): ScheduledReminder {
        val candidateToday = now.withHour(rule.hourOfDay).withMinute(rule.minute).withSecond(0).withNano(0)
        val scheduledAt = if (candidateToday.isAfter(now)) candidateToday else candidateToday.plusDays(1)
        return ScheduledReminder(
            uniqueWorkName = reminderWorkName(rule.id),
            ruleId = rule.id,
            type = rule.type,
            channelKey = reminderTypeChannelKey(rule.type),
            scheduledAt = scheduledAt,
            title = "Daily check-in",
            body = "Take a moment to log today's symptoms.",
            privateBody = "Cycle reminder available. Open the app to review it.",
            deepLinkTarget = ReminderDeepLinkTarget.Log,
        )
    }

    private fun buildLatePeriod(
        rule: ReminderRule,
        prediction: PredictionSnapshot?,
        cycles: List<CycleRecord>,
        now: ZonedDateTime,
    ): ScheduledReminder? {
        val predictionSnapshot = prediction ?: return null
        if (!predictionSnapshot.hasSupportableEstimate()) return null
        // Deliberately NOT anchored to the earliest plausible start, the
        // way period-prep is. "Late" means past the end of the plausible
        // range, not the start of it; anchoring early would tell someone
        // their period was late while it could still legitimately have
        // not arrived. The centre estimate plus the rule's own grace days
        // is what this detector is for.
        val predictedStart = predictionSnapshot.nextPeriodDate
        val hasUserCycleSincePrediction = cycles.any { cycle ->
            cycle.source == CycleSource.User && !cycle.startDate.isBefore(predictedStart)
        }
        if (hasUserCycleSincePrediction) return null

        val targetDate = predictedStart.plusDays(rule.daysBefore.toLong())
        val scheduledAt = scheduleDateTime(targetDate, rule, now)
        if (!scheduledAt.isAfter(now)) return null

        return ScheduledReminder(
            uniqueWorkName = reminderWorkName(rule.id),
            ruleId = rule.id,
            type = rule.type,
            channelKey = reminderTypeChannelKey(rule.type),
            scheduledAt = scheduledAt,
            title = "Cycle timing reminder",
            body = "Your period looks later than expected. Open CycleCare to update your cycle if it started.",
            privateBody = "Cycle reminder available. Open the app to review it.",
            deepLinkTarget = ReminderDeepLinkTarget.Home,
        )
    }

    private fun scheduleDateTime(
        date: LocalDate,
        rule: ReminderRule,
        now: ZonedDateTime,
    ): ZonedDateTime = ZonedDateTime.of(date, LocalTime.of(rule.hourOfDay, rule.minute), now.zone)
}

internal fun reminderWorkName(ruleId: String): String = "cyclecare-reminder-rule-${ruleId.lowercase()}"

fun reminderTypeChannelKey(type: ReminderType): ReminderChannelKey = when (type) {
    ReminderType.PeriodPrep,
    ReminderType.LatePeriod,
    ReminderType.Hydration,
    ReminderType.Pill,
    -> ReminderChannelKey.CycleAlerts
    ReminderType.FertileWindowStart,
    ReminderType.OvulationEstimate,
    -> ReminderChannelKey.FertilityAlerts
    ReminderType.DailyLog -> ReminderChannelKey.DailyCheckIn
}
