package com.cyclecare.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cyclecare.core.domain.PrivacyRules
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

class ReminderDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as CycleCareApplication
        val container = application.appContainer
        val logger = container.appLogger
        val permissionStatus = container.notificationPermissionController.status()
        val consent = container.privacyRepository.observeConsent().first()
        val channelSettings = container.reminderChannelSettingsRepository.observeChannelSettings().first()
        val channelKey = inputData.getString(keyChannelKey)
            ?.let { raw -> runCatching { enumValueOf<ReminderChannelKey>(raw) }.getOrNull() }
            ?: ReminderChannelKey.DailyCheckIn
        val settings = channelSettings.firstOrNull { it.channelKey == channelKey }
        val deepLinkTarget = inputData.getString(keyDeepLinkTarget)
            ?.let { raw -> runCatching { enumValueOf<ReminderDeepLinkTarget>(raw) }.getOrNull() }
            ?: ReminderDeepLinkTarget.Home
        val targetEpochDay = inputData.getLong(keyTargetEpochDay, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        val scheduledAtEpochMillis = inputData.getLong(keyScheduledAtEpochMillis, -1L)
        val deliveredAtEpochMillis = System.currentTimeMillis()

        val event = ReminderEvent(
            id = "${inputData.getString(keyRuleId).orEmpty()}-$scheduledAtEpochMillis",
            ruleId = inputData.getString(keyRuleId).orEmpty(),
            type = inputData.getString(keyReminderType)
                ?.let { raw -> runCatching { enumValueOf<ReminderType>(raw) }.getOrNull() }
                ?: ReminderType.DailyLog,
            channelKey = channelKey,
            scheduledAt = Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
            deliveredAt = Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
            title = inputData.getString(keyTitle).orEmpty(),
            body = inputData.getString(keyBody).orEmpty(),
            privateBody = inputData.getString(keyPrivateBody).orEmpty(),
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
            return Result.success()
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
            return Result.success()
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
            return Result.success()
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
            return Result.success()
        }
        if (container.reminderNotificationClient.syncChannels(channelSettings).isFailure) {
            return Result.retry()
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderIntentKeys.Target, deepLinkTarget.name)
            putExtra(ReminderIntentKeys.EventId, event.id)
            targetEpochDay?.let { putExtra(ReminderIntentKeys.TargetEpochDay, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            inputData.getInt(keyNotificationId, 0),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (title, body) = if (consent.hideNotificationPreview) {
            "Cycle reminder" to event.privateBody
        } else {
            event.title to event.body
        }

        val notification = NotificationCompat.Builder(applicationContext, ReminderChannels.channelId(effectiveSettings))
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
            notificationId = inputData.getInt(keyNotificationId, 0),
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
                Result.success()
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
                Result.success()
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
                Result.retry()
            }
        }
    }

    companion object {
        const val keyNotificationId = "notification_id"
        const val keyTitle = "title"
        const val keyBody = "body"
        const val keyPrivateBody = "private_body"
        const val keyRuleId = "rule_id"
        const val keyReminderType = "reminder_type"
        const val keyChannelKey = "channel_key"
        const val keyDeepLinkTarget = "deep_link_target"
        const val keyTargetEpochDay = "target_epoch_day"
        const val keyScheduledAtEpochMillis = "scheduled_at_epoch_millis"
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
