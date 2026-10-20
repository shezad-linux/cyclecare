package com.cyclecare.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Puts a failed reminder delivery back on the queue.
 *
 * An alarm fires once. The WorkManager path could retry by returning
 * `Result.retry()`, but an alarm has no such mechanism, so a delivery that
 * fails has to be scheduled again explicitly.
 *
 * The retry is put back a few minutes out rather than immediately, so a
 * failure that will not fix itself, such as the notification permission
 * being revoked, does not become a loop.
 */
internal object ReminderRetryScheduler {

    private const val RETRY_DELAY_MILLIS = 5 * 60 * 1000L

    /**
     * [nowMillis] exists so tests can pin the clock; every real caller
     * uses the default.
     */
    fun reschedule(
        context: Context,
        payload: ReminderAlarmPayload,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val intent = payload.writeTo(Intent(context, ReminderAlarmReceiver::class.java))
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            payload.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Tier 2, because a retry is not worth a permission prompt and the
        // delivery only has to happen reasonably soon.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nowMillis + RETRY_DELAY_MILLIS,
            pendingIntent,
        )
    }
}
