package com.cyclecare.app

import android.content.Intent
import com.cyclecare.core.model.ReminderDeepLinkTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReminderNavigationRequest(
    val target: ReminderDeepLinkTarget,
    val eventId: String? = null,
    val targetEpochDay: Long? = null,
)

interface ReminderNavigationCoordinator {
    val requests: StateFlow<ReminderNavigationRequest?>
    fun dispatch(request: ReminderNavigationRequest)
    fun clear()
}

class InMemoryReminderNavigationCoordinator : ReminderNavigationCoordinator {
    private val requestState = MutableStateFlow<ReminderNavigationRequest?>(null)

    override val requests: StateFlow<ReminderNavigationRequest?> = requestState.asStateFlow()

    override fun dispatch(request: ReminderNavigationRequest) {
        requestState.value = request
    }

    override fun clear() {
        requestState.value = null
    }
}

internal object ReminderIntentKeys {
    const val Target = "reminder_target"
    const val EventId = "reminder_event_id"
    const val TargetEpochDay = "reminder_target_epoch_day"
}

internal fun Intent.toReminderNavigationRequest(): ReminderNavigationRequest? {
    val rawTarget = getStringExtra(ReminderIntentKeys.Target) ?: return null
    val target = runCatching { enumValueOf<ReminderDeepLinkTarget>(rawTarget) }.getOrNull() ?: return null
    val eventId = getStringExtra(ReminderIntentKeys.EventId)
    val targetEpochDay = if (hasExtra(ReminderIntentKeys.TargetEpochDay)) {
        getLongExtra(ReminderIntentKeys.TargetEpochDay, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    } else {
        null
    }
    return ReminderNavigationRequest(
        target = target,
        eventId = eventId,
        targetEpochDay = targetEpochDay,
    )
}
