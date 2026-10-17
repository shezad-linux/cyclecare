package com.cyclecare.core.domain.notification

import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.domain.prediction.EstimateQuality

/**
 * Notification relevance and delivery contract.
 *
 * The existing notification path sends a reminder whenever a date
 * arrives. This model asks, for every candidate: is this worth
 * interrupting this person right now? Most candidates should not win.
 *
 * Silence is the default and sending is the exception, because
 * over-notification is the fastest route to uninstall and to poor store
 * ratings.
 *
 * Spec: CycleCare-Redesign-Docs/13-notification-intelligence.md
 */

// ── Named thresholds ──────────────────────────────────────────────

/** Minimum score to deliver at all. */
const val SEND_THRESHOLD = 40

/** Applied once the daily budget is spent; only the strongest survive. */
const val SEND_THRESHOLD_OVER_BUDGET = 75

/** A user who pinned a time has expressed intent; honour it more readily. */
const val SEND_THRESHOLD_PINNED_TIME = 25

const val MAX_SENDS_PER_DAY = 3
const val MAX_SENDS_PER_WEEK = 12

const val DEFAULT_SEND_HOUR = 9
const val MIN_INTERACTIONS_FOR_LEARNING = 10

const val MIN_SESSIONS_BEFORE_INTERSTITIAL = 3
const val MIN_MINUTES_BETWEEN_INTERSTITIALS = 10
const val POST_LOG_GRACE_MINUTES = 30
const val MAX_ARBITRATION_BATCH = 2

const val AUTO_DISABLE_MIN_IMPRESSIONS = 10
const val AUTO_DISABLE_DISMISS_RATE = 0.60

/** Default quiet hours, applied until the user sets their own. */
const val DEFAULT_QUIET_HOURS_START_HOUR = 22
const val DEFAULT_QUIET_HOURS_END_HOUR = 8

// ── Inherent urgency ──────────────────────────────────────────────

/**
 * How time-sensitive a reminder type is, before any user context.
 *
 * DailyLog is a nudge, never urgent, and is the first thing suppressed
 * under budget pressure.
 */
fun urgencyBaseFor(type: ReminderType): Int = when (type) {
    ReminderType.PeriodPrep -> 50
    ReminderType.LatePeriod -> 45
    ReminderType.FertileWindowStart -> 35
    ReminderType.OvulationEstimate -> 30
    ReminderType.DailyLog -> 20
    ReminderType.Hydration -> 20
    ReminderType.Pill -> 20
}

/**
 * Channel a reminder type is delivered on.
 *
 * Hydration and Pill remain in the enum for compatibility with rows
 * already persisted, but are never generated. Hydration is a recurring
 * nag pattern that conflicts with the fatigue model; Pill is meaningless
 * without medication logging, which CycleCare does not have.
 */
fun channelFor(type: ReminderType): ReminderChannelKey = when (type) {
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

// ── Contracts ─────────────────────────────────────────────────────

data class ReminderCandidate(
    val type: ReminderType,
    val ruleId: String,
    val leadDays: Int,
    val hourOfDay: Int,
    val userPinnedTime: Boolean,
    val scheduledForEpochMillis: Long,
    val targetEpochDay: Long?,
    /** From the prediction, so timing can respect uncertainty. */
    val windowHalfWidthDays: Int,
    val deepLink: ReminderDeepLinkTarget,
) {
    val channelKey: ReminderChannelKey get() = channelFor(type)
}

/** Everything the scorer needs, with no ambient state. */
data class RelevanceInput(
    val candidate: ReminderCandidate,
    val goal: TrackingGoal,
    val quality: EstimateQuality,
    val hoursSinceLastSend: Long,
    val sendsLast24h: Int,
    val sendsLast7d: Int,
    /** Lifetime share of impressions dismissed, 0..1. */
    val dismissRateForType: Float,
    /** Lifetime share of impressions opened, 0..1. */
    val openRateForType: Float,
    val sendsLearnedTime: Boolean,
)

data class DeliveryDecision(
    val candidate: ReminderCandidate,
    val score: Int,
    val deliver: Boolean,
    val suppressedBecause: String?,
)

data class QuietHours(val startHour: Int, val endHour: Int) {
    fun contains(hourOfDay: Int): Boolean = when {
        startHour == endHour -> true
        startHour < endHour -> hourOfDay in startHour until endHour
        else -> hourOfDay >= startHour || hourOfDay < endHour
    }

    companion object {
        val Default = QuietHours(DEFAULT_QUIET_HOURS_START_HOUR, DEFAULT_QUIET_HOURS_END_HOUR)
    }
}

data class EngagementProfile(
    /** 24 counters, one per hour of the day. */
    val hourHistogram: List<Int>,
    val interactionCount: Int,
) {
    fun peakHour(excluding: QuietHours): Int? {
        if (interactionCount < MIN_INTERACTIONS_FOR_LEARNING) return null
        if (hourHistogram.size != 24) return null
        return hourHistogram.indices
            .filterNot { excluding.contains(it) }
            .maxByOrNull { hourHistogram[it] }
    }
}

// ── Scoring ───────────────────────────────────────────────────────

/**
 * Rewards lead times suited to the type.
 *
 * A PeriodPrep reminder two days out is actionable; one a week out is
 * noise. Keeps the user's lead time meaningful without forbidding it.
 */
fun leadFitBonus(type: ReminderType, leadDays: Int): Int = when (type) {
    ReminderType.PeriodPrep -> when (leadDays) {
        in 1..2 -> 10
        in 3..4 -> 0
        else -> -15
    }
    ReminderType.LatePeriod -> 0
    ReminderType.FertileWindowStart -> if (leadDays in 0..2) 10 else -10
    ReminderType.OvulationEstimate -> if (leadDays in 0..1) 10 else -10
    else -> 0
}

/**
 * Fertility reminders are noise for a non-conception goal, and a
 * period reminder is still wanted by a conception goal.
 *
 * Replaces the v1 behaviour where fertility rules were seeded once and
 * never re-evaluated if the tracking goal changed after onboarding.
 */
fun goalAlignmentBonus(type: ReminderType, goal: TrackingGoal): Int {
    val fertility = type == ReminderType.FertileWindowStart || type == ReminderType.OvulationEstimate
    val conception = goal == TrackingGoal.ConceptionAwareness
    return when {
        fertility && conception -> 15
        fertility && !conception -> -25
        else -> 0
    }
}

/**
 * Cannot support an estimate-derived reminder without an estimate.
 *
 * With fewer than three intervals there is nothing worth interrupting
 * anyone about, so fertility and period-prep reminders are suppressed
 * rather than sent with a fabricated date.
 */
fun qualityBonus(quality: EstimateQuality): Int = when (quality) {
    EstimateQuality.HIGH -> 10
    EstimateQuality.MODERATE -> 5
    EstimateQuality.LOW -> 0
    EstimateQuality.INSUFFICIENT_HISTORY -> -100
}

fun learnedTimeBonus(sendsLearnedTime: Boolean, userPinnedTime: Boolean): Int =
    if (sendsLearnedTime && !userPinnedTime) 8 else 0

fun recencyPenalty(hoursSinceLastSend: Long): Int = when {
    hoursSinceLastSend < 2 -> -30
    hoursSinceLastSend < 6 -> -15
    else -> 0
}

/**
 * Suppresses a type the user consistently dismisses without opening.
 *
 * Returns a negative contribution, consistent with the other penalty
 * terms, so a dismissed type loses score rather than gaining it.
 * Progressive rather than binary, so a single bad day does not silence
 * a reminder type outright.
 */
fun fatiguePenalty(dismissRate: Float, openRate: Float): Int =
    -((1f - openRate) * 40f * (0.5f + dismissRate)).toInt()

fun budgetPressurePenalty(sendsLast24h: Int): Int = when {
    sendsLast24h <= 1 -> 0
    sendsLast24h == 2 -> -10
    sendsLast24h == MAX_SENDS_PER_DAY -> -25
    else -> -60
}

/** The complete relevance score. Every term is named and reviewable. */
fun relevanceScore(input: RelevanceInput): Int =
    urgencyBaseFor(input.candidate.type) +
        leadFitBonus(input.candidate.type, input.candidate.leadDays) +
        goalAlignmentBonus(input.candidate.type, input.goal) +
        qualityBonus(input.quality) +
        learnedTimeBonus(input.sendsLearnedTime, input.candidate.userPinnedTime) +
        recencyPenalty(input.hoursSinceLastSend) +
        fatiguePenalty(input.dismissRateForType, input.openRateForType) +
        budgetPressurePenalty(input.sendsLast24h)

/**
 * The threshold that applies, given budget and user intent.
 *
 * A pinned send time is the user telling the system when they want to
 * hear from it, so the bar is lower. That is the agency guarantee in
 * 13 section 8: learned time is a suggestion, never an override.
 */
fun thresholdFor(input: RelevanceInput): Int = when {
    input.candidate.userPinnedTime -> SEND_THRESHOLD_PINNED_TIME
    input.sendsLast24h >= MAX_SENDS_PER_DAY -> SEND_THRESHOLD_OVER_BUDGET
    else -> SEND_THRESHOLD
}

fun decide(input: RelevanceInput): DeliveryDecision {
    val score = relevanceScore(input)
    val threshold = thresholdFor(input)
    val reason = when {
        score >= threshold -> null
        input.sendsLast24h >= MAX_SENDS_PER_DAY -> "daily budget exhausted"
        input.candidate.userPinnedTime -> "below the pinned-time threshold"
        else -> "below the delivery threshold"
    }
    return DeliveryDecision(
        candidate = input.candidate,
        score = score,
        deliver = score >= threshold,
        suppressedBecause = reason,
    )
}

// ── Timing ────────────────────────────────────────────────────────

/**
 * Schedules against the earliest plausible start, not the centre.
 *
 * If the prediction says the next period falls between Oct 24 and
 * Oct 29, then "starts in 2 days" sent on Oct 22 may be four days
 * early. Anchoring to the centre asserts a precision the app does not
 * have.
 */
fun earliestPlausibleStart(prediction: CyclePrediction): java.time.LocalDate =
    prediction.nextPeriodDate.minusDays(
        (prediction.nextPeriodWindow?.halfWidthDays ?: 0).toLong(),
    )

/**
 * Days before the earliest plausible start that a reminder should fire.
 *
 * Returns null when the prediction supports no range, which suppresses
 * the reminder entirely rather than inventing a date.
 */
fun leadDaysFor(prediction: CyclePrediction, requestedLeadDays: Int): Int? {
    if (prediction.nextPeriodWindow == null) return null
    val anchor = earliestPlausibleStart(prediction)
    val target = anchor.minusDays(requestedLeadDays.toLong())
    return java.time.temporal.ChronoUnit.DAYS
        .between(prediction.generatedForDate, target)
        .toInt()
}

/**
 * True when the expected window has passed with no logged start.
 *
 * The v1 implementation fired LatePeriod at a fixed offset from the
 * prediction regardless of whether the user had since logged. The grace
 * period also scales with the estimate's own uncertainty, so a wide
 * range escalates later rather than accusing a user of being late.
 */
fun isLatePeriodCandidate(
    prediction: CyclePrediction,
    loggedStartAfterAnchor: Boolean,
    graceDays: Int = com.cyclecare.core.domain.prediction.LATE_PERIOD_GRACE_DAYS,
): Boolean {
    if (prediction.nextPeriodWindow == null) return false
    if (loggedStartAfterAnchor) return false
    val grace = maxOf(graceDays, prediction.nextPeriodWindow.halfWidthDays)
    return prediction.generatedForDate.isAfter(prediction.nextPeriodDate.plusDays(grace.toLong()))
}