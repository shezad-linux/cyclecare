package com.cyclecare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyclecare.core.domain.notification.DND_RETRY_MINUTES
import com.cyclecare.core.domain.notification.INTERRUPTION_FILTER_ALL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Posts the coalesced summary of everything that was deferred.
 *
 * Spec `13` §10: quiet-hours deferrals are not delivered individually —
 * they are folded into one summary so the user does not receive a burst
 * at the end of the window. The summary is what appears; the deferred
 * detail stays in the reminder center inside the app.
 *
 * The store is only drained once DND has lifted too: the summary posted
 * through a priority filter defeats the point of having deferred. If DND
 * is still on, nothing is taken and the summary is re-armed for later.
 */
class ReminderDeferredSummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                deliverSummary(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliverSummary(context: Context) {
        val application = context as CycleCareApplication
        val container = application.appContainer
        val intelligence = container.reminderIntelligenceRepository
        if (intelligence.deferredCount() == null) {
            // Nothing waiting. The deferrers arm the alarm every time a
            // candidate lands in the store, so an empty store with the
            // alarm still armed is the tail of a batch that already ran.
            ReminderDeferralScheduler.cancel(context)
            return
        }
        if (AndroidInterruptionFilterSource(context).currentInterruptionFilter() > INTERRUPTION_FILTER_ALL) {
            ReminderDeferralScheduler.schedule(
                context,
                container.timeProvider.nowEpochMillis() + DND_RETRY_MINUTES,
            )
            container.appLogger.info(
                tag = TAG,
                event = "deferred_summary_held_for_dnd",
                attributes = mapOf(
                    "count" to (intelligence.deferredCount() ?: 0).toString(),
                ),
            )
            return
        }
        val deferred = intelligence.takeDeferred()
        if (deferred.isEmpty()) return

        val channelId = ReminderChannels.summaryChannelId(
            types = deferred.map { it.type },
            settings = container.reminderChannelSettingsRepository.observeChannelSettings().first(),
        )
        // The deferred summary is the coalesced one, arriving without
        // members beside it. A flagged group summary with an empty group
        // is hidden by the platform, so this is the standalone grouped
        // variant: visible on its own, and bundled with whatever posts
        // after it.
        val notification = ReminderNotificationTemplates.redactedStandalone(
            context = context,
            channelId = channelId,
            title = context.getString(R.string.reminder_deferred_summary_title),
            body = context.resources.getQuantityString(
                R.plurals.reminder_deferred_summary_body,
                deferred.size,
                deferred.size,
            ),
        )
        container.reminderNotificationClient.post(
            notificationId = ReminderChannels.deferredSummaryNotificationId,
            notification = notification,
        )
        // The group now has a member, so the group's own summary can be
        // posted beside it instead of leaving an orphan group.
        ReminderGroupSummary.post(
            context = context,
            container = container,
            channelId = channelId,
        )
        container.appLogger.info(
            tag = TAG,
            event = "deferred_summary_posted",
            attributes = mapOf(
                "count" to deferred.size.toString(),
                "types" to deferred.map { it.type.name }.distinct().joinToString(","),
            ),
        )
    }

    private companion object {
        const val TAG = "ReminderDeferredSummary"
    }
}
