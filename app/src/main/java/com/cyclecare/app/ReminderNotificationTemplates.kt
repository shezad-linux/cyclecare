package com.cyclecare.app

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.cyclecare.core.model.ReminderType

/**
 * The redacted notification variants.
 *
 * Spec `13` §13: every reminder carries `setPublicVersion`, built from
 * per-type copy that never names a cycle date, a symptom or a note. One
 * place builds them so the lock-screen story cannot drift from the
 * in-shade story.
 */
internal object ReminderNotificationTemplates {

    /**
     * The lock-screen variant attached to a full reminder.
     */
    fun redactedContent(context: Context, channelId: String, type: ReminderType): Notification {
        val redactedText = when (type) {
            ReminderType.DailyLog -> context.getString(R.string.reminder_redacted_daily_log)
            else -> context.getString(R.string.reminder_redacted_generic)
        }
        return redactedBase(context, channelId)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(redactedText)
            .build()
    }

    /**
     * The group summary itself is the redacted variant, so a batch never
     * leaks detail on a lock screen.
     */
    fun redactedGroupSummary(
        context: Context,
        channelId: String,
        title: String,
        body: String,
    ): Notification = redactedBase(context, channelId)
        .setContentTitle(title)
        .setContentText(body)
        .setGroup(ReminderChannels.remindersGroupKey)
        .setGroupSummary(true)
        .setPublicVersion(redactedBase(context, channelId).build())
        .build()

    /**
     * The coalesced quiet-hours summary, posted when no member is beside
     * it. The platform hides a summary whose group has no children, so
     * the deferred summary is a standalone grouped member instead: it
     * bundles with its peers in the shade and posts a public copy of
     * what it shows without naming any cycle values.
     */
    fun redactedStandalone(
        context: Context,
        channelId: String,
        title: String,
        body: String,
    ): Notification = redactedBase(context, channelId)
        .setContentTitle(title)
        .setContentText(body)
        .setGroup(ReminderChannels.remindersGroupKey)
        .setPublicVersion(redactedBase(context, channelId).build())
        .build()

    private fun redactedBase(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setAutoCancel(true)
}
