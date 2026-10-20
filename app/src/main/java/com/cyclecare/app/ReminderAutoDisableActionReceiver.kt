package com.cyclecare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyclecare.core.model.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The one-tap re-enable behind the auto-disable notice.
 *
 * Spec `13` §13: when a reminder type is self-disabled, the notice is
 * supposed to carry the fix in the same notification. Tapping it puts
 * every rule of the type back the way the user had them, clears the
 * auto-disable record, and lets the next refresh re-arm what was
 * cancelled.
 */
class ReminderAutoDisableActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val typeRaw = intent.getStringExtra(keyReminderType) ?: return
        val type = runCatching { enumValueOf<ReminderType>(typeRaw) }.getOrNull() ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reEnable(context, type)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reEnable(context: Context, type: ReminderType) {
        val application = context as CycleCareApplication
        val container = application.appContainer
        runCatching {
            reEnableReminderType(
                reminderRepository = container.reminderRepository,
                intelligence = container.reminderIntelligenceRepository,
                scheduler = container.reminderScheduler,
                type = type,
            )
        }.onSuccess {
            container.appLogger.info(
                tag = TAG,
                event = "reminder_type_re_enabled",
                attributes = ReminderDeliveryLog.reEnabled(type),
            )
        }.onFailure { error ->
            container.appLogger.warn(
                tag = TAG,
                event = "reminder_type_re_enable_failed",
                attributes = ReminderDeliveryLog.reEnabled(type),
                throwable = error,
            )
        }
    }

    companion object {
        const val keyReminderType = "reminder_reenable_type"
        private const val TAG = "ReminderReEnable"
    }
}
