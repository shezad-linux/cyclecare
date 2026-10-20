package com.cyclecare.core.domain.notification

import com.cyclecare.core.model.DeferredReminder
import com.cyclecare.core.model.ReminderAutoDisabled
import com.cyclecare.core.model.ReminderType

/**
 * The storage format for the notification intelligence's two small lists.
 *
 * The auto-disabled types and the deferred candidates have to survive
 * process death: a type that comes back after a restart was never
 * disabled at all, and a deferred candidate that is forgotten defers a
 * reminder twice. They live in a plain key/value store, so they need a
 * format.
 *
 * The format is hand-rolled rather than a JSON library on purpose. The
 * values are enums, small integers and one long; the format is written
 * and read only by the repository that owns it; and a hand-rolled format
 * cannot drift with a library upgrade.
 *
 * Pure and free of Android, so the round trip — the thing that actually
 * proves an auto-disable survives a restart — is testable on the JVM.
 */
object ReminderIntelligenceCodec {

    private const val ENTRY_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ","

    /**
     * Encodes the auto-disabled types.
     *
     * Each entry is `type,dismissed,opened,disabledAt`. The counts are
     * the explanation the user is shown, so they travel with the record
     * rather than being recomputed.
     */
    fun encodeAutoDisabled(list: List<ReminderAutoDisabled>): String =
        list.joinToString(ENTRY_SEPARATOR) { record ->
            listOf(
                record.type.name,
                record.dismissedCount.toString(),
                record.openedCount.toString(),
                record.disabledAtEpochMillis.toString(),
            ).joinToString(FIELD_SEPARATOR)
        }

    /**
     * Decodes the auto-disabled types.
     *
     * A corrupt entry is dropped rather than failing the whole list: one
     * unreadable record must not take the user's other settings with it.
     */
    fun decodeAutoDisabled(encoded: String?): List<ReminderAutoDisabled> =
        encoded?.split(ENTRY_SEPARATOR)?.mapNotNull { entry ->
            // The limit keeps a separator inside the last field from
            // splitting the record into more parts than it has.
            val fields = entry.split(FIELD_SEPARATOR, limit = 4)
            if (fields.size != 4) return@mapNotNull null
            val type = toReminderTypeOrNull(fields[0]) ?: return@mapNotNull null
            val dismissed = fields[1].toIntOrNull() ?: return@mapNotNull null
            val opened = fields[2].toIntOrNull() ?: return@mapNotNull null
            val at = fields[3].toLongOrNull() ?: return@mapNotNull null
            ReminderAutoDisabled(
                type = type,
                dismissedCount = dismissed,
                openedCount = opened,
                disabledAtEpochMillis = at,
            )
        }.orEmpty()

    /**
     * Encodes the deferred candidates.
     *
     * Each entry is `ruleId,type,scheduledAt,reason`.
     */
    fun encodeDeferred(list: List<DeferredReminder>): String =
        list.joinToString(ENTRY_SEPARATOR) { item ->
            listOf(
                item.ruleId,
                item.type.name,
                item.scheduledAtEpochMillis.toString(),
                item.reason,
            ).joinToString(FIELD_SEPARATOR)
        }

    /** Decodes the deferred candidates, dropping any corrupt entry. */
    fun decodeDeferred(encoded: String?): List<DeferredReminder> =
        encoded?.split(ENTRY_SEPARATOR)?.mapNotNull { entry ->
            // The reason is the last field, so it keeps any separator
            // inside it rather than splitting the record.
            val fields = entry.split(FIELD_SEPARATOR, limit = 4)
            if (fields.size != 4) return@mapNotNull null
            val ruleId = fields[0]
            val type = toReminderTypeOrNull(fields[1]) ?: return@mapNotNull null
            val at = fields[2].toLongOrNull() ?: return@mapNotNull null
            if (ruleId.isEmpty()) return@mapNotNull null
            DeferredReminder(
                ruleId = ruleId,
                type = type,
                scheduledAtEpochMillis = at,
                reason = fields[3],
            )
        }.orEmpty()

    private fun toReminderTypeOrNull(raw: String): ReminderType? =
        runCatching { enumValueOf<ReminderType>(raw) }.getOrNull()
}
