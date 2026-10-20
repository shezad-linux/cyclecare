package com.cyclecare.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Arms the alarm that posts the coalesced summary of deferred reminders.
 *
 * Spec `13` §10: candidates that fall into quiet hours, DND or an
 * arbitration fold are not posted individually — they wait in the
 * intelligence store and this alarm wakes [ReminderDeferredSummaryReceiver]
 * once, at the open end of whatever deferred them.
 *
 * Re-scheduling with the same pending intent replaces the previous alarm,
 * so a second deferral later in the night simply moves the single summary
 * later. Nobody receives a burst at the window's end.
 *
 * Tier 2 (`setAndAllowWhileIdle`): a small set of people are waiting for
 * a batch summary, not a time-critical alert that justifies requesting
 * the exact-alarm permission.
 */
internal object ReminderDeferralScheduler {

    /** The fixed address of the one repeating summary alarm. */
    const val uniqueAlarmName = "cyclecare-reminder-deferred-summary"

    fun schedule(context: Context, triggerAtEpochMillis: Long) {
        val intent = Intent(context, ReminderDeferredSummaryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueAlarmName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val target = maxOf(triggerAtEpochMillis, System.currentTimeMillis() + 1)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pendingIntent)
    }

    /**
     * Drops the summary alarm. Called when a summary found nothing left
     * in the store, so no patrol runs for a state that no longer exists.
     */
    fun cancel(context: Context) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueAlarmName.hashCode(),
            Intent(context, ReminderDeferredSummaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
