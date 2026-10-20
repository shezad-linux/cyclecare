package com.cyclecare.core.data

import com.cyclecare.core.database.AppSettingEntity
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.ReminderIntelligenceRepository
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
        database.appSettingDao().observe(AUTO_DISABLED_KEY).map { encoded -> parseAutoDisabled(encoded) }

    override suspend fun isAutoDisabled(type: ReminderType): Boolean =
        parseAutoDisabled(read(AUTO_DISABLED_KEY)).any { it.type == type }

    override suspend fun disable(
        type: ReminderType,
        dismissedCount: Int,
        openedCount: Int,
        disabledAtEpochMillis: Long,
    ) {
        val remaining = parseAutoDisabled(read(AUTO_DISABLED_KEY)).filterNot { it.type == type }
        write(
            AUTO_DISABLED_KEY,
            encodeAutoDisabled(
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
        val remaining = parseAutoDisabled(read(AUTO_DISABLED_KEY)).filterNot { it.type == type }
        if (remaining.isEmpty()) {
            database.appSettingDao().delete(AUTO_DISABLED_KEY)
        } else {
            write(AUTO_DISABLED_KEY, encodeAutoDisabled(remaining))
        }
    }

    override suspend fun defer(
        ruleId: String,
        type: ReminderType,
        scheduledAtEpochMillis: Long,
        reason: String,
    ): Int {
        val remaining = parseDeferred(read(DEFERRED_KEY)).filterNot { it.ruleId == ruleId }.toMutableList()
        remaining += DeferredReminder(
            ruleId = ruleId,
            type = type,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            reason = reason,
        )
        write(DEFERRED_KEY, encodeDeferred(remaining))
        return remaining.size
    }

    override suspend fun takeDeferred(): List<DeferredReminder> {
        val snapshot = parseDeferred(read(DEFERRED_KEY))
        database.appSettingDao().delete(DEFERRED_KEY)
        return snapshot
    }

    override suspend fun deferredCount(): Int? {
        val count = parseDeferred(read(DEFERRED_KEY)).size
        return count.takeIf { it > 0 }
    }

    private suspend fun read(key: String): String? = database.appSettingDao().get(key)

    private suspend fun write(key: String, value: String) {
        database.appSettingDao().put(com.cyclecare.core.database.AppSettingEntity(key = key, value = value))
    }

    private fun parseAutoDisabled(encoded: String?): List<ReminderAutoDisabled> =
        encoded?.split(ENTRY_SEPARATOR)?.mapNotNull { entry ->
            entry.split(FIELD_SEPARATOR).takeIf { it.size == 4 }?.let { fields ->
                val type = toReminderTypeOrNull(fields[0])
                val dismissed = fields[1].toIntOrNull()
                val opened = fields[2].toIntOrNull()
                val at = fields[3].toLongOrNull()
                if (type != null && dismissed != null && opened != null && at != null) {
                    ReminderAutoDisabled(
                        type = type,
                        dismissedCount = dismissed,
                        openedCount = opened,
                        disabledAtEpochMillis = at,
                    )
                } else {
                    logger.warn(
                        tag = STORE_TAG,
                        event = "auto_disabled_state_corrupt",
                        attributes = mapOf("entry" to entry),
                    )
                    null
                }
            }
        }.orEmpty()

    private fun encodeAutoDisabled(list: List<ReminderAutoDisabled>): String =
        list.joinToString(ENTRY_SEPARATOR) { record ->
            listOf(
                record.type.name,
                record.dismissedCount.toString(),
                record.openedCount.toString(),
                record.disabledAtEpochMillis.toString(),
            ).joinToString(FIELD_SEPARATOR)
        }

    private fun parseDeferred(encoded: String?): List<DeferredReminder> =
        encoded?.split(ENTRY_SEPARATOR)?.mapNotNull { entry ->
            entry.split(FIELD_SEPARATOR).takeIf { it.size == 4 }?.let { fields ->
                val ruleId = fields[0]
                val type = toReminderTypeOrNull(fields[1])
                val at = fields[2].toLongOrNull()
                if (ruleId.isNotEmpty() && type != null && at != null) {
                    DeferredReminder(
                        ruleId = ruleId,
                        type = type,
                        scheduledAtEpochMillis = at,
                        reason = fields[3],
                    )
                } else {
                    logger.warn(
                        tag = STORE_TAG,
                        event = "deferred_state_corrupt",
                        attributes = mapOf("entry" to entry),
                    )
                    null
                }
            }
        }.orEmpty()

    private fun encodeDeferred(list: List<DeferredReminder>): String =
        list.joinToString(ENTRY_SEPARATOR) { item ->
            listOf(
                item.ruleId,
                item.type.name,
                item.scheduledAtEpochMillis.toString(),
                item.reason,
            ).joinToString(FIELD_SEPARATOR)
        }

    private fun toReminderTypeOrNull(raw: String): ReminderType? =
        runCatching { enumValueOf<ReminderType>(raw) }.getOrNull()

    companion object {
        private const val STORE_TAG = "ReminderIntelligenceStore"
        private const val ENTRY_SEPARATOR = ";"
        private const val FIELD_SEPARATOR = ","

        /** app_settings row the auto-disabled types live under. */
        const val AUTO_DISABLED_KEY = "reminder_auto_disabled_types"

        /** app_settings row the deferred candidates live under. */
        const val DEFERRED_KEY = "reminder_deferred_candidates"
    }
}
