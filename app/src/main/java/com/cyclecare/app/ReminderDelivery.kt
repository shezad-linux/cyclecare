package com.cyclecare.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cyclecare.core.domain.PrivacyRules
import com.cyclecare.core.domain.notification.DeliveryGateOutcome
import com.cyclecare.core.domain.notification.QuietHours
import com.cyclecare.core.model.ReminderChannelKey
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
 * delivered at most once. The decision itself lives in
 * [ReminderDeliveryPolicy], which is pure, and the attributes that record
 * it live in [ReminderDeliveryLog].
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
        // The lateness is classified before it is logged. The log line
        // is written at the delivery instant, so a delay in minutes
        // could be subtracted from that time to recover the scheduled
        // instant — which for a period reminder is the derived cycle
        // date. A class cannot be subtracted.
        val deliveryLatency = deliveryLatencyOf(
            maxOf(0L, deliveredAtEpochMillis - scheduledAtEpochMillis),
        )

        val event = ReminderEvent(
            id = "${payload.ruleId}-$scheduledAtEpochMillis",
            ruleId = payload.ruleId,
            type = payload.reminderType,
            channelKey = channelKey,
            scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(ZoneId.systemDefault()),
            deliveredAt = Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(ZoneId.systemDefault()),
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
                tag = ReminderDeliveryLog.TAG,
                event = "delivery_routed_in_app",
                attributes = ReminderDeliveryLog.routedInApp(event.ruleId, event.type),
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
                tag = ReminderDeliveryLog.TAG,
                event = "delivery_skipped_channel_disabled",
                attributes = ReminderDeliveryLog.skippedChannelDisabled(
                    event.ruleId,
                    event.type,
                    channelKey,
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
                tag = ReminderDeliveryLog.TAG,
                event = "delivery_skipped_notifications_unavailable",
                attributes = ReminderDeliveryLog.skippedNotificationsUnavailable(
                    event.ruleId,
                    event.type,
                    channelKey,
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
                tag = ReminderDeliveryLog.TAG,
                event = "delivery_skipped_missing_channel_settings",
                attributes = ReminderDeliveryLog.skippedMissingChannelSettings(
                    event.ruleId,
                    event.type,
                    channelKey,
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
        // The decision is pure and its inputs are all data, so the gates
        // can be exercised without a device.
        val intelligence = container.reminderIntelligenceRepository
        val gateInput = ReminderDeliveryPolicy.gateInput(
            events = container.reminderEventsRepository,
            profile = container.profileRepository,
            prediction = container.predictionRepository,
            type = payload.reminderType,
            payload = payload,
            deliveredAtEpochMillis = deliveredAtEpochMillis,
            interruptionFilter = AndroidInterruptionFilterSource(context).currentInterruptionFilter(),
            quietHours = QuietHours.Default,
        )
        val gateOutcome = ReminderDeliveryPolicy.decide(
            intelligence = intelligence,
            type = payload.reminderType,
            input = gateInput,
            quietHours = QuietHours.Default,
        )
        var deliveredScore = 0
        var deliveredThreshold = 0
        when (gateOutcome) {
            is DeliveryGateOutcome.Suppress -> {
                logger.info(
                    tag = ReminderDeliveryLog.TAG,
                    event = "delivery_suppressed",
                    attributes = ReminderDeliveryLog.suppressed(
                        payload.ruleId,
                        payload.reminderType,
                        gateOutcome.reason,
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
                // The hour is worked out here rather than inside the log
                // call, so that no instant is ever an argument to a log.
                val deferUntilHour = hourOfDay(gateOutcome.deferredUntilEpochMillis)
                logger.info(
                    tag = ReminderDeliveryLog.TAG,
                    event = "delivery_deferred",
                    attributes = ReminderDeliveryLog.deferred(
                        ruleId = payload.ruleId,
                        type = payload.reminderType,
                        reason = gateOutcome.reason,
                        deferredCount = deferredCount,
                        deferUntilHour = deferUntilHour,
                    ),
                )
                return ReminderDeliveryOutcome.Delivered
            }
            is DeliveryGateOutcome.Deliver -> {
                deliveredScore = gateOutcome.score
                deliveredThreshold = gateOutcome.threshold
            }
        }

        // Idempotence: the insert is the lock. A retry that lost its post
        // still finds the row and stops here instead of posting again.
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
            val publishOutcome = publishOnce(
                events = container.reminderEventsRepository,
                event = event,
            ) {
                container.reminderNotificationClient.post(
                    notificationId = payload.notificationId,
                    notification = notification,
                )
            }
        ) {
            DeliveryPublishOutcome.Posted -> {
                // The group needs exactly one summary beside its members,
                // or Android renders an orphan group.
                ReminderGroupSummary.post(
                    context = context,
                    container = container,
                    channelId = ReminderChannels.channelId(cycleAlertsSettings),
                )
                evaluateAutoDisable(
                    context = context,
                    container = container,
                    channelId = ReminderChannels.channelId(cycleAlertsSettings),
                    type = payload.reminderType,
                )
                logger.info(
                    tag = ReminderDeliveryLog.TAG,
                    event = "delivery_success",
                    attributes = ReminderDeliveryLog.success(
                        ruleId = event.ruleId,
                        type = event.type,
                        channelKey = channelKey,
                        leadDays = payload.leadDays,
                        deliveryLatency = deliveryLatency,
                        score = deliveredScore,
                        threshold = deliveredThreshold,
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
            DeliveryPublishOutcome.Deduplicated -> {
                logger.info(
                    tag = ReminderDeliveryLog.TAG,
                    event = "delivery_deduplicated",
                    attributes = ReminderDeliveryLog.deduplicated(event.ruleId, event.type),
                )
                ReminderDeliveryOutcome.Delivered
            }
            DeliveryPublishOutcome.PermissionUnavailable -> {
                container.reminderEventsRepository.recordEvent(event)
                logger.info(
                    tag = ReminderDeliveryLog.TAG,
                    event = "delivery_skipped_notifications_unavailable",
                    attributes = ReminderDeliveryLog.skippedNotificationsUnavailable(
                        event.ruleId,
                        event.type,
                        channelKey,
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
            is DeliveryPublishOutcome.Failed -> {
                logger.error(
                    tag = ReminderDeliveryLog.TAG,
                    event = "delivery_failed",
                    attributes = ReminderDeliveryLog.failed(event.ruleId, event.type, channelKey),
                    throwable = publishOutcome.throwable,
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
        val dismissed = container.reminderEventsRepository.countDismissedForType(type)
        val opened = container.reminderEventsRepository.countOpenedForType(type)
        val decision = evaluateAutoDisableDecision(
            impressions = impressions,
            dismissed = dismissed,
            opened = opened,
        )
        if (!decision.shouldDisable) return

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
                tag = ReminderDeliveryLog.TAG,
                event = "auto_disable_failed",
                attributes = ReminderDeliveryLog.autoDisableFailed(type),
                throwable = error,
            )
            return
        }
        postAutoDisableNotice(context, container, channelId, type)
        container.appLogger.info(
            tag = ReminderDeliveryLog.TAG,
            event = "reminder_type_auto_disabled",
            attributes = ReminderDeliveryLog.autoDisabled(type, impressions, dismissed, opened),
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
                tag = ReminderDeliveryLog.TAG,
                event = "refresh_after_delivery_failed",
                attributes = ReminderDeliveryLog.refreshFailed(ruleId, outcome),
                throwable = error,
            )
        }
    }

    private fun deliveryTimestamp(deliveredAtEpochMillis: Long): ZonedDateTime =
        Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(ZoneId.systemDefault())

    /**
     * The hour of the day an instant falls in.
     *
     * A bucket, not a date: the diagnostic records when a deferral ends
     * without saying which day it is.
     */
    private fun hourOfDay(epochMillis: Long): Int =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).hour
}
