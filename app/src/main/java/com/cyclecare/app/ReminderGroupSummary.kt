package com.cyclecare.app

import android.app.NotificationManager
import android.content.Context

/**
 * The one summary that belongs to the reminders group.
 *
 * Spec `13` §10: "the current code sets a group key with no summary,
 * which Android renders as an orphan group". Every member posts with the
 * group key, and the group needs exactly one summary beside it. That
 * summary is the redacted variant, so a batch never leaks a cycle detail
 * on a lock screen.
 *
 * The summary is posted after the member, never instead of it: a flagged
 * group summary whose group has no children is hidden by the platform,
 * which would lose the coalesced quiet-hours summary altogether. So the
 * coalesced summary posts as a member of the group, and this is the
 * group's header.
 */
internal object ReminderGroupSummary {

    /**
     * Posts the group summary.
     *
     * The count is the number of notifications the system currently holds
     * in the group. It is best-effort: a device that will not report its
     * active notifications gets the summary without a count rather than
     * no summary at all.
     */
    fun post(
        context: Context,
        container: AppContainer,
        channelId: String,
    ) {
        val memberCount = activeMemberCount(context)
        val body = if (memberCount > 0) {
            context.resources.getQuantityString(
                R.plurals.reminder_group_summary_body,
                memberCount,
                memberCount,
            )
        } else {
            context.getString(R.string.reminder_redacted_generic)
        }
        val notification = ReminderNotificationTemplates.redactedGroupSummary(
            context = context,
            channelId = channelId,
            title = context.getString(R.string.reminder_group_summary_title),
            body = body,
        )
        container.reminderNotificationClient.post(
            notificationId = ReminderChannels.groupedSummaryNotificationId,
            notification = notification,
        )
    }

    /**
     * How many notifications the group currently holds.
     *
     * The app can read its own notifications without a listener, and the
     * summary itself is excluded so the count is the members alone.
     */
    private fun activeMemberCount(context: Context): Int {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return 0
        return runCatching {
            manager.activeNotifications.count { statusBarNotification ->
                statusBarNotification.notification.group == ReminderChannels.remindersGroupKey &&
                    statusBarNotification.id != ReminderChannels.groupedSummaryNotificationId
            }
        }.getOrDefault(0)
    }
}
