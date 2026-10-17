package com.cyclecare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) return
        ReminderRefreshWorker.enqueueImmediate(context)
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
