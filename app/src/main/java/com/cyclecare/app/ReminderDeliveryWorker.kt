package com.cyclecare.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType

/**
 * Delivers a reminder through WorkManager.
 *
 * A reminder is delivered by an alarm, which is what reaches the device
 * through Doze. This worker remains as the path a failed alarm delivery
 * is retried through, and for any delivery queued before the app moved to
 * alarms.
 *
 * The delivery itself is in [ReminderDelivery], so the two paths cannot
 * drift apart.
 */
class ReminderDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val payload = payloadFrom(inputData) ?: return Result.failure()
        return when (ReminderDelivery.deliver(applicationContext, payload)) {
            ReminderDeliveryOutcome.Delivered -> Result.success()
            ReminderDeliveryOutcome.Retry -> Result.retry()
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

        /** The same payload an alarm carries, read out of worker data. */
        internal fun payloadFrom(data: Data): ReminderAlarmPayload? {
            val ruleId = data.getString(keyRuleId) ?: return null
            val type = data.getString(keyReminderType)
                ?.let { raw -> runCatching { enumValueOf<ReminderType>(raw) }.getOrNull() }
                ?: return null
            val channelKey = data.getString(keyChannelKey)
                ?.let { raw -> runCatching { enumValueOf<ReminderChannelKey>(raw) }.getOrNull() }
                ?: return null
            val deepLinkTarget = data.getString(keyDeepLinkTarget)
                ?.let { raw -> runCatching { enumValueOf<ReminderDeepLinkTarget>(raw) }.getOrNull() }
                ?: return null
            val scheduledAt = data.getLong(keyScheduledAtEpochMillis, -1L)
            if (scheduledAt < 0L) return null
            val epochDay = data.getLong(keyTargetEpochDay, Long.MIN_VALUE)
            return ReminderAlarmPayload(
                notificationId = data.getInt(keyNotificationId, 0),
                title = data.getString(keyTitle).orEmpty(),
                body = data.getString(keyBody).orEmpty(),
                privateBody = data.getString(keyPrivateBody).orEmpty(),
                ruleId = ruleId,
                reminderType = type,
                channelKey = channelKey,
                deepLinkTarget = deepLinkTarget,
                targetEpochDay = epochDay.takeIf { it != Long.MIN_VALUE },
                scheduledAtEpochMillis = scheduledAt,
            )
        }
    }
}
