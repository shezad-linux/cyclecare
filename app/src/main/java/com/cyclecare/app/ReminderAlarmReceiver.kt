package com.cyclecare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives a reminder alarm and delivers the reminder.
 *
 * Spec `13` §11 puts time-sensitive reminders on alarms rather than on
 * deferred work, because a `OneTimeWorkRequest` with a delay is deferred
 * without bound while the device is idle. Both alarm APIs the scheduler
 * uses are delivered through Doze.
 *
 * The receiver hands off to [ReminderDelivery], which is the same delivery
 * the WorkManager path uses, so the two cannot drift.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = ReminderAlarmPayload.from(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (ReminderDelivery.deliver(context, payload)) {
                    ReminderDeliveryOutcome.Delivered -> Unit
                    ReminderDeliveryOutcome.Retry -> {
                        // The alarm is one shot, so a failed delivery has to
                        // be put back rather than dropped.
                        ReminderRetryScheduler.reschedule(context, payload)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
