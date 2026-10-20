package com.cyclecare.core.data

import com.cyclecare.core.database.AppSettingEntity
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.ReminderIntelligenceRepository
import com.cyclecare.core.domain.notification.ReminderIntelligenceCodec
import com.cyclecare.core.model.DeferredReminder
import com.cyclecare.core.model.ReminderAutoDisabled
import com.cyclecare.core.model.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Auto-disabled reminder types and deferred candidates, in Room.
 *
 * Spec `13` §9 and §10. Both kinds of state have to survive process
 * death: an auto-disabled type that came back the next time the app
 * restarted was never disabled at all, and a deferred candidate that was
 * forgotten by a reboot defers a reminder twice. The `app_settings`
 * table is a plain key/value store, which is the right shape for two
 * small lists.
 *
 * The encoding is deliberately hand-rolled rather than a JSON library:
 * the values are simple enums and longs, the format is written and read
 * only by this class, and a hand-rolled format cannot drift with a
 * library upgrade.
 *
 * Reset semantics come for free: resetting the app clears all tables
 * (including `app_settings`), so stale auto-disable records and deferred
 * candidates disappear with everything else.
 */
class RoomReminderIntelligenceRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderIntelligenceRepository {

    override fun observeAutoDisabled(): Flow<List<ReminderAutoDisabled>> =
        database.appSettingDao().observe(AUTO_DISABLED_KEY)
            .map { encoded -> decodeAutoDisabled(encoded) }

    override suspend fun isAutoDisabled(type: ReminderType): Boolean =
        decodeAutoDisabled(read(AUTO_DISABLED_KEY)).any { it.type == type }

    override suspend fun disable(
        type: ReminderType,
        dismissedCount: Int,
        openedCount: Int,
        disabledAtEpochMillis: Long,
    ) {
        val remaining = decodeAutoDisabled(read(AUTO_DISABLED_KEY))
            .filterNot { it.type == type }
        write(
            AUTO_DISABLED_KEY,
            ReminderIntelligenceCodec.encodeAutoDisabled(
                remaining + ReminderAutoDisabled(
                    type = type,
                    dismissedCount = dismissedCount,
                    openedCount = openedCount,
                    disabledAtEpochMillis = disabledAtEpochMillis,
                ),
            ),
        )
    }

    override suspend fun reEnable(type: ReminderType) {
        val remaining = decodeAutoDisabled(read(AUTO_DISABLED_KEY))
            .filterNot { it.type == type }
        if (remaining.isEmpty()) {
            database.appSettingDao().delete(AUTO_DISABLED_KEY)
        } else {
            write(AUTO_DISABLED_KEY, ReminderIntelligenceCodec.encodeAutoDisabled(remaining))
        }
    }

    override suspend fun defer(
        ruleId: String,
        type: ReminderType,
        scheduledAtEpochMillis: Long,
        reason: String,
    ): Int {
        val remaining = ReminderIntelligenceCodec.decodeDeferred(read(DEFERRED_KEY))
            .filterNot { it.ruleId == ruleId }
            .toMutableList()
        remaining += DeferredReminder(
            ruleId = ruleId,
            type = type,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            reason = reason,
        )
        write(DEFERRED_KEY, ReminderIntelligenceCodec.encodeDeferred(remaining))
        return remaining.size
    }

    override suspend fun takeDeferred(): List<DeferredReminder> {
        val snapshot = ReminderIntelligenceCodec.decodeDeferred(read(DEFERRED_KEY))
        database.appSettingDao().delete(DEFERRED_KEY)
        return snapshot
    }

    override suspend fun deferredCount(): Int? {
        val count = ReminderIntelligenceCodec.decodeDeferred(read(DEFERRED_KEY)).size
        return count.takeIf { it > 0 }
    }

    private suspend fun read(key: String): String? = database.appSettingDao().get(key)

    private suspend fun write(key: String, value: String) {
        database.appSettingDao().put(com.cyclecare.core.database.AppSettingEntity(key = key, value = value))
    }

    /**
     * Decodes the auto-disabled types, and says so when an entry was lost.
     *
     * A corrupt entry is dropped rather than taking the whole list with
     * it, but a silent drop would hide a storage problem, so the count is
     * reported.
     */
    private fun decodeAutoDisabled(encoded: String?): List<ReminderAutoDisabled> {
        val decoded = ReminderIntelligenceCodec.decodeAutoDisabled(encoded)
        val expected = encoded?.split(';')?.count { it.isNotEmpty() } ?: 0
        if (decoded.size != expected) {
            logger.warn(
                tag = STORE_TAG,
                event = "auto_disabled_state_corrupt",
                attributes = mapOf(
                    "expected" to expected.toString(),
                    "decoded" to decoded.size.toString(),
                ),
            )
        }
        return decoded
    }

    companion object {
        private const val STORE_TAG = "ReminderIntelligenceStore"

        /** app_settings row the auto-disabled types live under. */
        const val AUTO_DISABLED_KEY = "reminder_auto_disabled_types"

        /** app_settings row the deferred candidates live under. */
        const val DEFERRED_KEY = "reminder_deferred_candidates"
    }
}
