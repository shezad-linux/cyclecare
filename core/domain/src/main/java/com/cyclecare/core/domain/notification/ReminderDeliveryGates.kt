package com.cyclecare.core.domain.notification

import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import java.time.ZonedDateTime

/**
 * The delivery-time gates that wrap `decide()`.
 *
 * Scoring is the heart; these gates are its guards. The order is fixed
 * here in one place so the two delivery paths (the alarm receiver and the
 * WorkManager worker) cannot drift apart:
 *
 * 1. `decide` — the relevance, pinned-time or over-budget threshold
 *    suppresses the candidate outright. Silence is the default.
 * 2. The weekly budget — [`MAX_SENDS_PER_WEEK`] is a ceiling, not an
 *    influence on score, so it is checked separately.
 * 3. Auto-disable — a type the user has been heard on is not delivered
 *    again. Plumbing lives with the rule repository, so the caller checks
 *    it before the timing gates.
 * 4. Quiet hours — everything defers into one coalesced summary at the
 *    end of quiet hours, with `LatePeriod` as the exception the
 *    specification names.
 * 5. DND — a priority or stricter interruption filter defers everything
 *    except `LatePeriod`.
 * 6. Arbitration — more than `MAX_ARBITRATION_BATCH` candidates clustered
 *    around the same moment fold the rest into a summary.
 *
 * Deliberately pure: every input is handed in, nothing reads ambient
 * state, so the whole gate stack is testable on the JVM.
 */

/**
 * The interruption filters that still count as "do not disturb".
 *
 * Mirrors Android's `NotificationManager` values without importing the
 * Android framework into the domain layer: ALL (no filtering) is 1 and
 * every stricter filter is 2 or more. The delivery layer supplies the
 * integer from the real system.
 */
const val INTERRUPTION_FILTER_ALL = 1
const val INTERRUPTION_FILTER_PRIORITY = 2

/**
 * How wide the arbitration window is, in minutes.
 *
 * Spec `13` §10 names the batch size (`MAX_ARBITRATION_BATCH`) but not
 * the window the candidates are counted in. Thirty minutes is the length
 * the rest of the specification builds prompts around (the interstitial
 * recency gate in spec 15 §11.3 uses the same figure), so the same
 * window keeps the notification pressure story coherent.
 */
const val ARBITRATION_WINDOW_MINUTES = 30

/** How long a DND-deferred summary waits before rechecing. */
const val DND_RETRY_MINUTES = 30L

/**
 * Everything a gate needs about the candidate and the moment.
 *
 * Reservoirs of state — event history, prediction quality, goal, the
 * current interruption filter — are resolved by the delivery layer before
 * this is built. Nothing here is allowed to read ambient state.
 */
data class DeliveryGateInput(
    /** Carried from the alarm/worker payload. */
    val leadDays: Int,
    val windowHalfWidthDays: Int,
    val userPinnedTime: Boolean,
    val scheduledForEpochMillis: Long,
    val targetEpochDay: Long?,
    val deepLink: ReminderDeepLinkTarget,
    /** Inputs from the repositories. */
    val goal: TrackingGoal,
    /** How good the estimate behind this reminder is. */
    val quality: EstimateQuality,
    val hoursSinceLastSend: Long,
    val sendsLast24h: Int,
    val sendsLast7d: Int,
    /** Sends inside the arbitration window before this attempt. */
    val sendsInArbitrationWindow: Int,
    val dismissRateForType: Float,
    val openRateForType: Float,
    /** The interruption filter at the moment of the attempt. */
    val dndFilter: Int,
    /** The clock at the moment of the attempt. */
    val now: ZonedDateTime,
) {
    fun asCandidate(type: ReminderType, ruleId: String): ReminderCandidate = ReminderCandidate(
        type = type,
        ruleId = ruleId,
        leadDays = leadDays,
        hourOfDay = now.hour,
        userPinnedTime = userPinnedTime,
        scheduledForEpochMillis = scheduledForEpochMillis,
        targetEpochDay = targetEpochDay,
        windowHalfWidthDays = windowHalfWidthDays,
        deepLink = deepLink,
    )

    fun asRelevanceInput(type: ReminderType, ruleId: String): RelevanceInput = RelevanceInput(
        candidate = asCandidate(type, ruleId),
        goal = goal,
        quality = quality,
        hoursSinceLastSend = hoursSinceLastSend,
        sendsLast24h = sendsLast24h,
        sendsLast7d = sendsLast7d,
        dismissRateForType = dismissRateForType,
        openRateForType = openRateForType,
        sendsLearnedTime = false,
    )
}

sealed class DeliveryGateOutcome {
    /** Post the notification. */
    data object Deliver : DeliveryGateOutcome()

    /**
     * Post nothing, and schedule no summary on this candidate's behalf —
     * the candidate is out of the delivery path for good, either because
     * it lost the score or because the type has been auto-disabled.
     */
    data class Suppress(val reason: String) : DeliveryGateOutcome()

    /**
     * Post nothing now. The caller records the candidate as deferred and
     * schedules the coalesced summary for [deferredUntilEpochMillis].
     */
    data class Defer(
        val reason: String,
        val deferredUntilEpochMillis: Long,
    ) : DeliveryGateOutcome()
}

/** Which of the three channels a posted or summarised candidate uses. */
fun channelForDelivery(type: ReminderType): ReminderChannelKey = channelFor(type)

/**
 * Runs the gate stack in order.
 *
 * `LatePeriod` is the documented exception to quiet hours and DND, in
 * both cases because it is the one reminder the user explicitly opted
 * into. It still loses to the scores, the weekly budget and arbitration,
 * which are about sending too much, not about the time of day.
 */
fun evaluateDeliveryGates(
    type: ReminderType,
    input: DeliveryGateInput,
    quietHours: QuietHours = QuietHours.Default,
): DeliveryGateOutcome {
    val decision = decide(input.asRelevanceInput(type, "candidate"))
    if (!decision.deliver) {
        return DeliveryGateOutcome.Suppress(decision.suppressedBecause ?: "below threshold")
    }
    if (input.sendsLast7d >= MAX_SENDS_PER_WEEK) {
        return DeliveryGateOutcome.Suppress("weekly budget exhausted")
    }
    val latePeriodExempt = type == ReminderType.LatePeriod
    return when {
        !latePeriodExempt && quietHours.contains(input.now.hour) -> {
            DeliveryGateOutcome.Defer("quiet hours", quietHoursEndEpochMillis(input.now, quietHours))
        }
        !latePeriodExempt && input.dndFilter > INTERRUPTION_FILTER_ALL -> {
            val resumeAt = input.now.plusMinutes(DND_RETRY_MINUTES)
            DeliveryGateOutcome.Defer("do not disturb", resumeAt.toInstant().toEpochMilli())
        }
        input.sendsInArbitrationWindow >= MAX_ARBITRATION_BATCH -> {
            val resumeAt = input.now.plusMinutes(ARBITRATION_WINDOW_MINUTES.toLong())
            DeliveryGateOutcome.Defer("arbitration", resumeAt.toInstant().toEpochMilli())
        }
        else -> DeliveryGateOutcome.Deliver
    }
}

/**
 * The instant the current quiet-hours window ends.
 *
 * Quiet hours wrap midnight: with the default 22 → 8, an event deferred
 * at 23:05 waits until 08:00 the next day, and one deferred at 07:30
 * waits until 08:00 the same day.
 */
fun quietHoursEndEpochMillis(
    now: ZonedDateTime,
    quietHours: QuietHours = QuietHours.Default,
): Long {
    val atEndHour = now.withHour(quietHours.endHour)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
    val target = if (atEndHour.isAfter(now)) atEndHour else atEndHour.plusDays(1)
    return target.toInstant().toEpochMilli()
}
