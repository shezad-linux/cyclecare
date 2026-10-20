package com.cyclecare.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cyclecare.core.domain.PrivacyRules
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
    /** The reminder was posted, or was deliberately skipped. */
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
 */
internal object ReminderDelivery {

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
    val deliveredAtEpochMillis = System.currentTimeMillis()

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
    container.reminderEventsRepository.recordEvent(event)

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
    if (container.reminderNotificationClient.syncChannels(channelSettings).isFailure) {
        return ReminderDeliveryOutcome.Retry
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

    val notification = NotificationCompat.Builder(context, ReminderChannels.channelId(effectiveSettings))
        .setSmallIcon(R.drawable.ic_notification_small)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
            logger.info(
                tag = "CycleCareReminder",
                event = "delivery_success",
                attributes = mapOf(
                    "ruleId" to event.ruleId,
                    "type" to event.type.name,
                    "channelKey" to channelKey.name,
                    "scheduledAtEpochMillis" to scheduledAtEpochMillis.toString(),
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
}
