package com.cyclecare.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cyclecare.core.domain.PrivacyRules
import com.cyclecare.core.domain.ReminderTypeEngagement
import com.cyclecare.core.domain.notification.AUTO_DISABLE_DISMISS_RATE
import com.cyclecare.core.domain.notification.AUTO_DISABLE_MIN_IMPRESSIONS
import com.cyclecare.core.domain.notification.ARBITRATION_WINDOW_MINUTES
import com.cyclecare.core.domain.notification.INTERRUPTION_FILTER_ALL
import com.cyclecare.core.domain.notification.DeliveryGateInput
import com.cyclecare.core.domain.notification.DeliveryGateOutcome
import com.cyclecare.core.domain.notification.evaluateDeliveryGates
import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.domain.toEstimateQuality
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * What happened to a delivery attempt.
 */
internal enum class ReminderDeliveryOutcome {
    /** The reminder was posted, or was deliberately skipped or deferred. */
    Delivered,

    /** The attempt should be tried again. */
    Retry,
}

/**
 * The delivery of a reminder.
 *
 * Shared by the two ways a reminder reaches the app: an alarm, which is
 * how a time-sensitive reminder is delivered, and the WorkManager worker,
 * which remains the fallback for a retry and for the periodic refresh.
 *
 * Keeping one implementation means the two paths cannot drift, which
 * matters because the alarm path is the one the specification's Doze
 * demonstration depends on.
 *
 * This is where the notification intelligence from spec `13` is wired in.
 * A candidate is no longer delivered merely because its date arrived: it
 * is scored against the relevance thresholds, the budgets and the
 * engagement history; it defers into quiet hours or DND; it loses to
 * arbitration when too many candidates arrive together; and it is
 * delivered at most once. What used to be a fire-and-forget post is now
 * the full gate stack, evaluated here so both paths share it.
 */
internal object ReminderDelivery {

    /** The notification id of the one-time auto-disable explanation. */
    internal val autoDisableNoticeNotificationId =
        "cyclecare-reminder-auto-disable-notice".hashCode()

suspend fun deliver(context: Context, payload: ReminderAlarmPayload): ReminderDeliveryOutcome {
    val application = context as CycleCareApplication
    val container = application.appContainer
    val logger = container.appLogger
    val permissionStatus = container.notificationPermissionController.status()
    val consent = container.privacyRepository.observeConsent().first()
    val channelSettings = container.reminderChannelSettingsRepository.observeChannelSettings().first()
    val channelKey = payload.channelKey
    val settings = channelSettings.firstOrNull { it.channelKey == channelKey }
    val deepLinkTarget = payload.deepLinkTarget
    val targetEpochDay = payload.targetEpochDay
    val scheduledAtEpochMillis = payload.scheduledAtEpochMillis
    val deliveredAtEpochMillis = container.timeProvider.nowEpochMillis()

    val event = ReminderEvent(
        id = "${payload.ruleId}-$scheduledAtEpochMillis",
        ruleId = payload.ruleId,
        type = payload.reminderType,
        channelKey = channelKey,
        scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
        deliveredAt = Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
        title = payload.title,
        body = payload.body,
        privateBody = payload.privateBody,
        deepLinkTarget = deepLinkTarget,
        targetEpochDay = targetEpochDay,
    )

    val hasPinConfigured = container.privacyRepository.hasPinConfigured().first()
    val lastUnlockEpochMillis = container.privacyRepository.observeLastUnlockEpochMillis().first()
    val hasUnlockedInCurrentProcess =
        lastUnlockEpochMillis != null &&
            lastUnlockEpochMillis >= container.processStartEpochMillis
    val isLocked = consent.appLockEnabled &&
        hasPinConfigured &&
        (
            !hasUnlockedInCurrentProcess ||
                !PrivacyRules.isUnlockFresh(lastUnlockEpochMillis, deliveredAtEpochMillis)
        )

    if (container.appVisibilityObserver.isForeground.value && !isLocked) {
        container.reminderEventsRepository.recordEvent(event)
        logger.info(
            tag = "CycleCareReminder",
            event = "delivery_routed_in_app",
            attributes = mapOf(
                "ruleId" to event.ruleId,
                "type" to event.type.name,
            ),
        )
        refreshFutureSchedules(
            container = container,
            logger = logger,
            deliveredAt = deliveryTimestamp(deliveredAtEpochMillis),
            ruleId = event.ruleId,
            outcome = "in_app",
        )
        return ReminderDeliveryOutcome.Delivered
    }

    if (settings?.enabled == false) {
        container.reminderEventsRepository.recordEvent(event)
        logger.info(
            tag = "CycleCareReminder",
            event = "delivery_skipped_channel_disabled",
            attributes = mapOf(
                "ruleId" to event.ruleId,
                "type" to event.type.name,
                "channelKey" to channelKey.name,
            ),
        )
        refreshFutureSchedules(
            container = container,
            logger = logger,
            deliveredAt = deliveryTimestamp(deliveredAtEpochMillis),
            ruleId = event.ruleId,
            outcome = "channel_disabled",
        )
        return ReminderDeliveryOutcome.Delivered
    }

    if (!permissionStatus.canPostNotifications) {
        container.reminderEventsRepository.recordEvent(event)
        logger.info(
            tag = "CycleCareReminder",
            event = "delivery_skipped_notifications_unavailable",
            attributes = mapOf(
                "ruleId" to event.ruleId,
                "type" to event.type.name,
            ),
        )
        refreshFutureSchedules(
            container = container,
            logger = logger,
            deliveredAt = deliveryTimestamp(deliveredAtEpochMillis),
            ruleId = event.ruleId,
            outcome = "notifications_blocked",
        )
        return ReminderDeliveryOutcome.Delivered
    }
    val effectiveSettings = settings ?: run {
        container.reminderEventsRepository.recordEvent(event)
        logger.warn(
            tag = "CycleCareReminder",
            event = "delivery_skipped_missing_channel_settings",
            attributes = mapOf(
                "ruleId" to event.ruleId,
                "type" to event.type.name,
                "channelKey" to channelKey.name,
            ),
        )
        refreshFutureSchedules(
            container = container,
            logger = logger,
            deliveredAt = deliveryTimestamp(deliveredAtEpochMillis),
            ruleId = event.ruleId,
            outcome = "missing_channel_settings",
        )
        return ReminderDeliveryOutcome.Delivered
    }
    // The channel sync is infrastructure, not intelligence: sync first, so
    // a broken channel cannot silently eat a candidate that would have
    // scored well.
    if (container.reminderNotificationClient.syncChannels(channelSettings).isFailure) {
        container.reminderEventsRepository.recordEvent(event)
        return ReminderDeliveryOutcome.Retry
    }

    // ── The intelligence gates ─────────────────────────────────────
    // An auto-disabled type is out before anything else runs. No event
    // row is written, because no impression would have happened.
    val intelligence = container.reminderIntelligenceRepository
    if (intelligence.isAutoDisabled(payload.reminderType)) {
        logger.info(
            tag = "CycleCareReminder",
            event = "delivery_suppressed",
            attributes = mapOf(
                "ruleId" to payload.ruleId,
                "type" to payload.reminderType.name,
                "reason" to "type auto-disabled",
            ),
        )
        return ReminderDeliveryOutcome.Delivered
    }

    val gateOutcome = evaluateDeliveryGates(
        type = payload.reminderType,
        input = DeliveryGateInput(
            leadDays = payload.leadDays,
            windowHalfWidthDays = payload.windowHalfWidthDays,
            userPinnedTime = payload.userPinnedTime,
            scheduledForEpochMillis = scheduledAtEpochMillis,
            targetEpochDay = targetEpochDay,
            deepLink = deepLinkTarget,
            goal = container.profileRepository.observeProfile().first()?.trackingGoal
                ?: com.cyclecare.core.model.TrackingGoal.PeriodPrep,
            quality = qualityForDelivery(payload.reminderType, container),
            hoursSinceLastSend = hoursSinceLastSend(container, deliveredAtEpochMillis),
            sendsLast24h = container.reminderEventsRepository.countDeliveredBetween(
                fromEpochMillis = deliveredAtEpochMillis - MILLIS_PER_DAY,
                toEpochMillis = deliveredAtEpochMillis,
            ),
            sendsLast7d = container.reminderEventsRepository.countDeliveredBetween(
                fromEpochMillis = deliveredAtEpochMillis - MILLIS_PER_WEEK,
                toEpochMillis = deliveredAtEpochMillis,
            ),
            sendsInArbitrationWindow = container.reminderEventsRepository.countDeliveredBetween(
                fromEpochMillis = deliveredAtEpochMillis - ARBITRATION_WINDOW_MINUTES * MILLIS_PER_MINUTE,
                toEpochMillis = deliveredAtEpochMillis,
            ),
            dismissRateForType = engagementForType(container, payload.reminderType).dismissRate,
            openRateForType = engagementForType(container, payload.reminderType).openRate,
            dndFilter = interruptionFilterFor(context),
            now = deliveryTimestamp(deliveredAtEpochMillis),
        ),
    )
    when (gateOutcome) {
        is DeliveryGateOutcome.Suppress -> {
            logger.info(
                tag = "CycleCareReminder",
                event = "delivery_suppressed",
                attributes = mapOf(
                    "ruleId" to payload.ruleId,
                    "type" to payload.reminderType.name,
                    "reason" to gateOutcome.reason,
                ),
            )
            return ReminderDeliveryOutcome.Delivered
        }
        is DeliveryGateOutcome.Defer -> {
            val deferredCount = intelligence.defer(
                ruleId = payload.ruleId,
                type = payload.reminderType,
                scheduledAtEpochMillis = scheduledAtEpochMillis,
                reason = gateOutcome.reason,
            )
            ReminderDeferralScheduler.schedule(context, gateOutcome.deferredUntilEpochMillis)
            logger.info(
                tag = "CycleCareReminder",
                event = "delivery_deferred",
                attributes = mapOf(
                    "ruleId" to payload.ruleId,
                    "type" to payload.reminderType.name,
                    "reason" to gateOutcome.reason,
                    "deferredUntilEpochMillis" to gateOutcome.deferredUntilEpochMillis.toString(),
                    "deferredCount" to deferredCount.toString(),
                ),
            )
            return ReminderDeliveryOutcome.Delivered
        }
        DeliveryGateOutcome.Deliver -> Unit
    }

    // Idempotence: the insert is the lock. A retry that lost its post
    // still finds the row and stops here instead of posting again.
    val isNewDelivery = container.reminderEventsRepository.insertIfAbsent(event)
    if (!isNewDelivery) {
        logger.info(
            tag = "CycleCareReminder",
            event = "delivery_deduplicated",
            attributes = mapOf(
                "ruleId" to event.ruleId,
                "type" to event.type.name,
            ),
        )
        return ReminderDeliveryOutcome.Delivered
    }

    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(ReminderIntentKeys.Target, deepLinkTarget.name)
        putExtra(ReminderIntentKeys.EventId, event.id)
        targetEpochDay?.let { putExtra(ReminderIntentKeys.TargetEpochDay, it) }
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        payload.notificationId,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val (title, body) = if (consent.hideNotificationPreview) {
        context.getString(R.string.reminder_notification_title) to event.privateBody
    } else {
        event.title to event.body
    }

    // Channel ids for the extras that travel with a member post: the
    // redacted public variant and the group summary share whatever
    // channel the finding settings actually created, so the system
    // never looks up a channel that does not exist.
    val cycleAlertsSettings = channelSettings.firstOrNull { it.channelKey == ReminderChannelKey.CycleAlerts }
        ?: effectiveSettings

    val notification = NotificationCompat.Builder(context, ReminderChannels.channelId(effectiveSettings))
        .setSmallIcon(R.drawable.ic_notification_small)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            redactedNotification(
                context = context,
                payload = payload,
                channelId = ReminderChannels.channelId(cycleAlertsSettings),
            ),
        )
        .setGroup(ReminderChannels.remindersGroupKey)
        .setPriority(
            when (channelKey) {
                ReminderChannelKey.CycleAlerts -> NotificationCompat.PRIORITY_HIGH
                ReminderChannelKey.FertilityAlerts -> NotificationCompat.PRIORITY_DEFAULT
                ReminderChannelKey.DailyCheckIn -> NotificationCompat.PRIORITY_LOW
            },
        )
        .build()

    return when (
        val postResult = container.reminderNotificationClient.post(
        notificationId = payload.notificationId,
        notification = notification,
        )
    ) {
        ReminderNotificationPostResult.Posted -> {
            evaluateAutoDisable(
                context = context,
                container = container,
                channelId = ReminderChannels.channelId(cycleAlertsSettings),
                type = payload.reminderType,
            )
            logger.info(
                tag = "CycleCareReminder",
                event = "delivery_success",
                attributes = mapOf(
                    "ruleId" to event.ruleId,
                    "type" to event.type.name,
                    "channelKey" to channelKey.name,
                    // No absolute scheduled time here: from the local
                    // clock it reconstructs the derived period date.
                    // The latency is the diagnostic that matters.
                    "deliveryDelayMinutes" to maxOf(0L, (deliveredAtEpochMillis - scheduledAtEpochMillis) / MILLIS_PER_MINUTE).toString(),
                ),
            )
            refreshFutureSchedules(
                container = container,
                logger = logger,
                deliveredAt = deliveryTimestamp(deliveredAtEpochMillis),
                ruleId = event.ruleId,
                outcome = "posted",
            )
            ReminderDeliveryOutcome.Delivered
        }
        ReminderNotificationPostResult.PermissionUnavailable -> {
            container.reminderEventsRepository.recordEvent(event)
            logger.info(
                tag = "CycleCareReminder",
                event = "delivery_skipped_notifications_unavailable",
                attributes = mapOf(
                    "ruleId" to event.ruleId,
                    "type" to event.type.name,
                    "channelKey" to channelKey.name,
                ),
            )
            refreshFutureSchedules(
                container = container,
                logger = logger,
                deliveredAt = deliveryTimestamp(deliveredAtEpochMillis),
                ruleId = event.ruleId,
                outcome = "notifications_unavailable_at_post",
            )
            ReminderDeliveryOutcome.Delivered
        }
        is ReminderNotificationPostResult.Failed -> {
            logger.error(
                tag = "CycleCareReminder",
                event = "delivery_failed",
                attributes = mapOf(
                    "ruleId" to event.ruleId,
                    "type" to event.type.name,
                    "channelKey" to channelKey.name,
                ),
                throwable = postResult.throwable,
            )
            ReminderDeliveryOutcome.Retry
        }
    }
}

    /**
     * The redacted notification the lock screen shows instead of content.
     *
     * Spec `13` §13: per-type copy, no cycle dates, no symptom values.
     */
    private fun redactedNotification(
        context: Context,
        payload: ReminderAlarmPayload,
        channelId: String,
    ): Notification = ReminderNotificationTemplates.redactedContent(
        context = context,
        channelId = channelId,
        type = payload.reminderType,
    )

    /**
     * Checks whether this type just crossed the auto-disable threshold.
     *
     * Called once per posted impression, so the counts always include it.
     * Disabling is recorded before the next refresh runs, which is also
     * what cancels the reminders the disabled rules had armed.
     */
    private suspend fun evaluateAutoDisable(
        context: Context,
        container: AppContainer,
        channelId: String,
        type: ReminderType,
    ) {
        val impressions = container.reminderEventsRepository.countDeliveredForType(type)
        if (impressions < AUTO_DISABLE_MIN_IMPRESSIONS) return
        val dismissed = container.reminderEventsRepository.countDismissedForType(type)
        val opened = container.reminderEventsRepository.countOpenedForType(type)
        val dismissRate = dismissed.toFloat() / impressions.toFloat()
        if (dismissRate < AUTO_DISABLE_DISMISS_RATE) return

        val alreadyDisabled = container.reminderIntelligenceRepository.isAutoDisabled(type)
        if (alreadyDisabled) return

        runCatching {
            container.reminderRepository.setRuleTypeEnabled(type, enabled = false)
            container.reminderIntelligenceRepository.disable(
                type = type,
                dismissedCount = dismissed,
                openedCount = opened,
                disabledAtEpochMillis = container.timeProvider.nowEpochMillis(),
            )
        }.onFailure { error ->
            container.appLogger.warn(
                tag = "CycleCareReminder",
                event = "auto_disable_failed",
                attributes = mapOf("type" to type.name),
                throwable = error,
            )
            return
        }
        postAutoDisableNotice(context, container, channelId, type)
        container.appLogger.info(
            tag = "CycleCareReminder",
            event = "reminder_type_auto_disabled",
            attributes = mapOf(
                "type" to type.name,
                "impressions" to impressions.toString(),
                "dismissed" to dismissed.toString(),
                "opened" to opened.toString(),
            ),
        )
    }

    /**
     * The one-tap explanation with the re-enable action.
     *
     * Spec `13` §13: "a one-time notice that it can be re-enabled in
     * Settings, with a re-enable button in the notification itself."
     */
    private fun postAutoDisableNotice(
        context: Context,
        container: AppContainer,
        channelId: String,
        type: ReminderType,
    ) {
        val reEnableIntent = Intent(context, ReminderAutoDisableActionReceiver::class.java).apply {
            putExtra(ReminderAutoDisableActionReceiver.keyReminderType, type.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            type.name.hashCode(),
            reEnableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.reminder_auto_disabled_title))
            .setContentText(context.getString(R.string.reminder_auto_disabled_body))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_action_re_enable), pendingIntent)
            .build()
        container.reminderNotificationClient.post(
            notificationId = autoDisableNoticeNotificationId,
            notification = notice,
        )
    }

    /**
     * Estimate quality as the scorer wants it.
     *
     * A `DailyLog` nudge is not anchored to an estimate at all — the
     * planner schedules it whether or not any prediction exists — so it
     * carries a neutral quality rather than inheriting the engine's
     * verdict about unrelated history. All other types carry the
     * prediction's own quality, because their whole point is the
     * estimate.
     */
    private suspend fun qualityForDelivery(type: ReminderType, container: AppContainer): EstimateQuality {
        val prediction = container.predictionRepository.observePrediction().first() ?: return EstimateQuality.INSUFFICIENT_HISTORY
        if (type == ReminderType.DailyLog) {
            return EstimateQuality.MODERATE
        }
        return prediction.quality.toEstimateQuality()
    }

    /**
     * Hours since the last send of any reminder, or a no-penalty sentinel
     * when nothing was ever sent.
     *
     * The scorer penalises sends within two hours; the clean-slate caller
     * has nothing to be penalised for, and a zero would read as "sent
     * just now". Twenty-four hours matches a first impression that is
     * neither fresh nor stale.
     */
    private suspend fun hoursSinceLastSend(container: AppContainer, nowEpochMillis: Long): Long {
        val lastDelivery = container.reminderEventsRepository.latestDeliveryAtSince(0L)
        return if (lastDelivery == null) {
            24L
        } else {
            (nowEpochMillis - lastDelivery).coerceAtLeast(0L) / MILLIS_PER_HOUR
        }
    }

    private suspend fun engagementForType(
        container: AppContainer,
        type: ReminderType,
    ): ReminderTypeEngagement {
        val impressions = container.reminderEventsRepository.countDeliveredForType(type)
        val dismissed = container.reminderEventsRepository.countDismissedForType(type)
        val opened = container.reminderEventsRepository.countOpenedForType(type)
        return ReminderTypeEngagement.of(dismissed, opened, impressions)
    }

    private suspend fun refreshFutureSchedules(
        container: AppContainer,
        logger: com.cyclecare.core.domain.AppLogger,
        deliveredAt: ZonedDateTime,
        ruleId: String,
        outcome: String,
    ) {
        runCatching {
            container.reminderScheduler.refreshSchedules(now = deliveredAt)
        }.onFailure { error ->
            logger.warn(
                tag = "CycleCareReminder",
                event = "refresh_after_delivery_failed",
                attributes = mapOf(
                    "ruleId" to ruleId,
                    "outcome" to outcome,
                ),
                throwable = error,
            )
        }
    }

    private fun deliveryTimestamp(deliveredAtEpochMillis: Long): ZonedDateTime =
        Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(ZoneId.systemDefault())

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
    private const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
    private const val MILLIS_PER_WEEK = 7L * MILLIS_PER_DAY
}

/**
 * The interruption filter, translated outside the domain layer.
 */
internal fun interruptionFilterFor(context: Context): Int =
    context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        ?: INTERRUPTION_FILTER_ALL
