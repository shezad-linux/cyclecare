package com.cyclecare.app

import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderIntelligenceRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.ReminderTypeEngagement
import com.cyclecare.core.domain.notification.ARBITRATION_WINDOW_MINUTES
import com.cyclecare.core.domain.notification.AUTO_DISABLE_DISMISS_RATE
import com.cyclecare.core.domain.notification.AUTO_DISABLE_MIN_IMPRESSIONS
import com.cyclecare.core.domain.notification.DeliveryGateInput
import com.cyclecare.core.domain.notification.DeliveryGateOutcome
import com.cyclecare.core.domain.notification.QuietHours
import com.cyclecare.core.domain.notification.evaluateDeliveryGates
import com.cyclecare.core.domain.notification.sendsAtLearnedTime
import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.domain.toEstimateQuality
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * The delivery decision, in one place, with nothing ambient in it.
 *
 * The intelligence module in `core:domain` is pure, so it cannot read the
 * event history, the channel settings or the system's interruption
 * filter. Those are resolved here, in the app layer, and handed over as
 * data. That is what makes the whole gate stack testable on the JVM: a
 * test builds a [DeliveryGateInput] from fake repositories and asserts
 * what the decision is, without a device and without an emulator.
 *
 * The decision is deliberately one function. The alarm path and the
 * WorkManager path both call it, so a fix to one of the gates cannot
 * leave the other behind.
 */
internal object ReminderDeliveryPolicy {

    /**
     * Assembles the input the gate stack needs.
     *
     * Everything here is a read of a repository or of the payload the
     * alarm carried. The interruption filter is passed in rather than
     * read here, because reading it needs the Android framework.
     */
    suspend fun gateInput(
        events: ReminderEventsRepository,
        profile: ProfileRepository,
        prediction: PredictionRepository,
        type: ReminderType,
        payload: ReminderAlarmPayload,
        deliveredAtEpochMillis: Long,
        interruptionFilter: Int,
        quietHours: QuietHours = QuietHours.Default,
        learnedSendHour: Int? = null,
    ): DeliveryGateInput {
        val engagement = engagementForType(events, type)
        return DeliveryGateInput(
            leadDays = payload.leadDays,
            windowHalfWidthDays = payload.windowHalfWidthDays,
            userPinnedTime = payload.userPinnedTime,
            scheduledForEpochMillis = payload.scheduledAtEpochMillis,
            targetEpochDay = payload.targetEpochDay,
            deepLink = payload.deepLinkTarget,
            goal = profile.observeProfile().first()?.trackingGoal ?: TrackingGoal.PeriodPrep,
            quality = qualityForDelivery(type, prediction),
            hoursSinceLastSend = hoursSinceLastSend(events, deliveredAtEpochMillis),
            sendsLast24h = events.countDeliveredBetween(
                fromEpochMillis = deliveredAtEpochMillis - MILLIS_PER_DAY,
                toEpochMillis = deliveredAtEpochMillis,
            ),
            sendsLast7d = events.countDeliveredBetween(
                fromEpochMillis = deliveredAtEpochMillis - MILLIS_PER_WEEK,
                toEpochMillis = deliveredAtEpochMillis,
            ),
            sendsInArbitrationWindow = events.countDeliveredBetween(
                fromEpochMillis = deliveredAtEpochMillis - ARBITRATION_WINDOW_MINUTES * MILLIS_PER_MINUTE,
                toEpochMillis = deliveredAtEpochMillis,
            ),
            dismissRateForType = engagement.dismissRate,
            openRateForType = engagement.openRate,
            // The learned-time bonus never applies to a time the user
            // pinned. Spec `13` §8.
            sendsLearnedTime = sendsAtLearnedTime(
                userPinnedTime = payload.userPinnedTime,
                learnedHourOfDay = learnedSendHour,
                scheduledHourOfDay = Instant.ofEpochMilli(payload.scheduledAtEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .hour,
            ),
            dndFilter = interruptionFilter,
            now = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(deliveredAtEpochMillis),
                ZoneId.systemDefault(),
            ),
        )
    }

    /**
     * The whole decision.
     *
     * An auto-disabled type is out before anything else runs: it was
     * paused because the user was not opening it, and asking again would
     * be exactly the nagging the pause exists to stop.
     */
    suspend fun decide(
        intelligence: ReminderIntelligenceRepository,
        type: ReminderType,
        input: DeliveryGateInput,
        quietHours: QuietHours = QuietHours.Default,
    ): DeliveryGateOutcome {
        if (intelligence.isAutoDisabled(type)) {
            return DeliveryGateOutcome.Suppress("type auto-disabled")
        }
        return evaluateDeliveryGates(type = type, input = input, quietHours = quietHours)
    }

    /**
     * Estimate quality as the scorer wants it.
     *
     * A `DailyLog` nudge is not anchored to an estimate at all — the
     * planner schedules it whether or not any prediction exists — so it
     * carries a neutral quality rather than inheriting the engine's
     * verdict about unrelated history. Every other type carries the
     * prediction's own quality, because the estimate is their whole
     * point.
     */
    private suspend fun qualityForDelivery(
        type: ReminderType,
        prediction: PredictionRepository,
    ): EstimateQuality {
        val snapshot = prediction.observePrediction().first() ?: return EstimateQuality.INSUFFICIENT_HISTORY
        if (type == ReminderType.DailyLog) {
            return EstimateQuality.MODERATE
        }
        return snapshot.quality.toEstimateQuality()
    }

    /**
     * Hours since the last send of any reminder, or a no-penalty
     * sentinel when nothing was ever sent.
     *
     * The scorer penalises sends within two hours; the clean-slate caller
     * has nothing to be penalised for, and a zero would read as "sent
     * just now". Twenty-four hours matches a first impression that is
     * neither fresh nor stale.
     */
    private suspend fun hoursSinceLastSend(
        events: ReminderEventsRepository,
        nowEpochMillis: Long,
    ): Long {
        val lastDelivery = events.latestDeliveryAtSince(0L)
        return if (lastDelivery == null) {
            NO_HISTORY_SENTINEL_HOURS
        } else {
            (nowEpochMillis - lastDelivery).coerceAtLeast(0L) / MILLIS_PER_HOUR
        }
    }

    private suspend fun engagementForType(
        events: ReminderEventsRepository,
        type: ReminderType,
    ): ReminderTypeEngagement {
        val impressions = events.countDeliveredForType(type)
        val dismissed = events.countDismissedForType(type)
        val opened = events.countOpenedForType(type)
        return ReminderTypeEngagement.of(dismissed, opened, impressions)
    }

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
    private const val MILLIS_PER_WEEK = 7L * MILLIS_PER_DAY

    /** What "never sent anything" looks like to the recency penalty. */
    const val NO_HISTORY_SENTINEL_HOURS = 24L
}

/**
 * Whether this type just crossed the auto-disable threshold.
 *
 * Spec `13` §9: a type whose dismiss rate is at or above 60% across a
 * lifetime of at least ten impressions is paused by the app. Below either
 * bound nothing happens — a handful of impressions is not a judgement.
 */
internal data class AutoDisableDecision(
    val shouldDisable: Boolean,
    val reason: String,
)

internal fun evaluateAutoDisableDecision(
    impressions: Int,
    dismissed: Int,
    opened: Int,
): AutoDisableDecision {
    if (impressions < AUTO_DISABLE_MIN_IMPRESSIONS) {
        return AutoDisableDecision(
            shouldDisable = false,
            reason = "only $impressions impressions, below the $AUTO_DISABLE_MIN_IMPRESSIONS minimum",
        )
    }
    val dismissRate = dismissed.toFloat() / impressions.toFloat()
    if (dismissRate < AUTO_DISABLE_DISMISS_RATE) {
        return AutoDisableDecision(
            shouldDisable = false,
            reason = "dismiss rate below the $AUTO_DISABLE_DISMISS_RATE threshold",
        )
    }
    return AutoDisableDecision(
        shouldDisable = true,
        reason = "$dismissed of $impressions dismissed, at or above the $AUTO_DISABLE_DISMISS_RATE threshold",
    )
}

/**
 * Puts a paused reminder type back the way the user had it.
 *
 * The one-tap re-enable behind the notification action and the re-enable
 * button in the reminder centre are the same operation, so they are the
 * same function: switch the rules back on, clear the pause record, and
 * re-arm what the pause cancelled.
 */
internal suspend fun reEnableReminderType(
    reminderRepository: ReminderRepository,
    intelligence: ReminderIntelligenceRepository,
    scheduler: ReminderScheduler,
    type: ReminderType,
) {
    reminderRepository.setRuleTypeEnabled(type, enabled = true)
    intelligence.reEnable(type)
    scheduler.refreshSchedules()
}

/** What happened when a delivery was published. */
internal sealed interface DeliveryPublishOutcome {
    /** The notification was posted. */
    data object Posted : DeliveryPublishOutcome

    /** The delivery had already been recorded, so nothing was posted. */
    data object Deduplicated : DeliveryPublishOutcome

    /** The system would not accept a notification right now. */
    data object PermissionUnavailable : DeliveryPublishOutcome

    /** The post itself failed. */
    data class Failed(val throwable: Throwable) : DeliveryPublishOutcome
}

/**
 * Records the event and posts the notification — at most once.
 *
 * Spec `13` §12: the insert is the lock. A retry that lost its post
 * finds the row and stops, so a retried delivery never produces a second
 * notification. The order matters: the row goes in first, so a crash
 * between the insert and the post loses the notification rather than
 * duplicating it.
 *
 * The post is a closure so the ordering can be tested without building an
 * Android notification.
 */
internal suspend fun publishOnce(
    events: ReminderEventsRepository,
    event: ReminderEvent,
    post: suspend () -> ReminderNotificationPostResult,
): DeliveryPublishOutcome {
    val isNewDelivery = events.insertIfAbsent(event)
    if (!isNewDelivery) {
        return DeliveryPublishOutcome.Deduplicated
    }
    return when (val result = post()) {
        ReminderNotificationPostResult.Posted -> DeliveryPublishOutcome.Posted
        ReminderNotificationPostResult.PermissionUnavailable -> DeliveryPublishOutcome.PermissionUnavailable
        is ReminderNotificationPostResult.Failed -> DeliveryPublishOutcome.Failed(result.throwable)
    }
}
