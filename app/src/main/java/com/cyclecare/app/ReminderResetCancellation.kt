package com.cyclecare.app

import android.content.Context

/**
 * Cancels every reminder the app has armed.
 *
 * Spec: a reset deletes the user's data, and a reminder that is still
 * armed would fire afterwards — with nothing to show, because the rules
 * it belongs to are gone. The alarm has to be cancelled before the
 * tables are cleared, while the rule ids that address them still exist.
 *
 * The rule ids are addressed by the same stable name the scheduler used,
 * so the cancellation finds the alarm that was actually armed.
 *
 * The app layer owns this because only it knows which receiver class an
 * alarm is addressed to; `core:data` supplies the ids.
 */
internal object ReminderResetCancellation {

    /**
     * Cancels the alarms for [ruleIds], the deferred summary alarm, and
     * the notifications the app has posted.
     */
    fun cancel(context: Context, ruleIds: List<String>) {
        val alarmFacade = AndroidReminderAlarmFacade(context)
        ruleIds.forEach { uniqueName -> alarmFacade.cancel(uniqueName) }
        // The coalesced summary has its own alarm, and it is not
        // addressed by a rule.
        ReminderDeferralScheduler.cancel(context)
        val notificationManager = notificationManagerCompat(context)
        notificationManager.cancel(ReminderChannels.groupedSummaryNotificationId)
        notificationManager.cancel(ReminderChannels.deferredSummaryNotificationId)
        notificationManager.cancel(ReminderDelivery.autoDisableNoticeNotificationId)
    }
}
