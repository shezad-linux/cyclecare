package com.cyclecare.core.domain

import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.model.DeferredReminder
import com.cyclecare.core.model.ReminderAutoDisabled
import com.cyclecare.core.model.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A store that never changes anything.
 *
 * Default for a container built without notification intelligence, so
 * every consumer has a valid object even when the feature is not wired
 * in.
 */
object NoOpReminderIntelligenceRepository : ReminderIntelligenceRepository {
    override fun observeAutoDisabled(): Flow<List<ReminderAutoDisabled>> =
        MutableStateFlow<List<ReminderAutoDisabled>>(emptyList()).asStateFlow()

    override suspend fun isAutoDisabled(type: ReminderType): Boolean = false

    override suspend fun disable(
        type: ReminderType,
        dismissedCount: Int,
        openedCount: Int,
        disabledAtEpochMillis: Long,
    ) = Unit

    override suspend fun reEnable(type: ReminderType) = Unit

    override suspend fun defer(
        ruleId: String,
        type: ReminderType,
        scheduledAtEpochMillis: Long,
        reason: String,
    ): Int = 0

    override suspend fun takeDeferred(): List<DeferredReminder> = emptyList()

    override suspend fun deferredCount(): Int? = null
}

/**
 * An in-process reminder of the same contract the Room store implements.
 *
 * Used by the instrumented harnesses, which replace the container with
 * fakes and need the deferral and auto-disable state to behave exactly
 * like production without a database.
 */
class InMemoryReminderIntelligenceRepository : ReminderIntelligenceRepository {
    private val autoDisabled = MutableStateFlow<List<ReminderAutoDisabled>>(emptyList())
    private val deferred = mutableListOf<DeferredReminder>()

    override fun observeAutoDisabled(): Flow<List<ReminderAutoDisabled>> = autoDisabled.asStateFlow()

    override suspend fun isAutoDisabled(type: ReminderType): Boolean =
        autoDisabled.value.any { it.type == type }

    override suspend fun disable(
        type: ReminderType,
        dismissedCount: Int,
        openedCount: Int,
        disabledAtEpochMillis: Long,
    ) {
        autoDisabled.value = autoDisabled.value
            .filterNot { it.type == type } + ReminderAutoDisabled(
            type = type,
            dismissedCount = dismissedCount,
            openedCount = openedCount,
            disabledAtEpochMillis = disabledAtEpochMillis,
        )
    }

    override suspend fun reEnable(type: ReminderType) {
        autoDisabled.value = autoDisabled.value.filterNot { it.type == type }
    }

    override suspend fun defer(
        ruleId: String,
        type: ReminderType,
        scheduledAtEpochMillis: Long,
        reason: String,
    ): Int {
        deferred.removeAll { it.ruleId == ruleId }
        deferred += DeferredReminder(
            ruleId = ruleId,
            type = type,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            reason = reason,
        )
        return deferred.size
    }

    override suspend fun takeDeferred(): List<DeferredReminder> {
        val snapshot = deferred.toList()
        deferred.clear()
        return snapshot
    }

    override suspend fun deferredCount(): Int? = deferred.size.takeIf { it > 0 }
}

/**
 * The pieces of a carried-through event history that scoring needs.
 *
 * Gathers the rates in one place because both the fatigue penalty and the
 * auto-disable decision ask for the same two numbers, and because a
 * reminder type with no impressions yet has no rates.
 */
data class ReminderTypeEngagement(
    val dismissRate: Float,
    val openRate: Float,
) {
    companion object {
        fun of(dismissedCount: Int, openedCount: Int, impressions: Int): ReminderTypeEngagement =
            if (impressions <= 0) {
                ReminderTypeEngagement(dismissRate = 0f, openRate = 0f)
            } else {
                ReminderTypeEngagement(
                    dismissRate = dismissedCount.toFloat() / impressions.toFloat(),
                    openRate = openedCount.toFloat() / impressions.toFloat(),
                )
            }
    }
}

/** Counts the recent sends the budgets ask for. */
data class SendBudgets(
    val last24h: Int,
    val last7d: Int,
    val lastArbitrationWindow: Int,
)

/** The quality mapping between the model enum and the scorer's enum. */
fun com.cyclecare.core.model.PredictionQuality.toEstimateQuality(): EstimateQuality = when (this) {
    com.cyclecare.core.model.PredictionQuality.INSUFFICIENT_HISTORY -> EstimateQuality.INSUFFICIENT_HISTORY
    com.cyclecare.core.model.PredictionQuality.LOW -> EstimateQuality.LOW
    com.cyclecare.core.model.PredictionQuality.MODERATE -> EstimateQuality.MODERATE
    com.cyclecare.core.model.PredictionQuality.HIGH -> EstimateQuality.HIGH
}
