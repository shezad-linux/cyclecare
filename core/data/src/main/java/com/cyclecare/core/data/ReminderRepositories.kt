package com.cyclecare.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReminderRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderRepository {
    override fun observeRules(): Flow<List<ReminderRule>> =
        database.reminderRuleDao().observeAll().map { rules -> rules.map { it.toModel(logger) } }

    override suspend fun upsertRule(rule: ReminderRule) {
        database.reminderRuleDao().upsert(rule.toEntity())
    }
}

class RoomReminderEventsRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderEventsRepository {
    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> =
        database.reminderEventDao().observeRecent(limit).map { events -> events.map { it.toModel(logger) } }

    override fun observeUnreadCount(): Flow<Int> =
        database.reminderEventDao().observeUnreadCount()

    override fun observeNextUnread(): Flow<ReminderEvent?> =
        database.reminderEventDao().observeNextUnread().map { it?.toModel(logger) }

    override suspend fun recordEvent(event: ReminderEvent) {
        database.reminderEventDao().upsert(event.toEntity())
    }

    override suspend fun markRead(id: String) {
        database.reminderEventDao().markRead(id, System.currentTimeMillis())
    }

    override suspend fun markAllRead() {
        database.reminderEventDao().markAllRead(System.currentTimeMillis())
    }

    override suspend fun dismiss(id: String) {
        database.reminderEventDao().dismiss(id, System.currentTimeMillis())
    }

    override suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long? =
        database.reminderEventDao().latestDeliveryAtSince(sinceEpochMillis)
}

class LocalReminderChannelSettingsRepository(
    private val context: Context,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderChannelSettingsRepository {
    override fun observeChannelSettings(): Flow<List<ReminderChannelSettings>> =
        context.dataStore.data.map { preferences ->
            ReminderChannelKey.entries.map { channelKey ->
                val defaults = defaultReminderChannelSettings(channelKey)
                ReminderChannelSettings(
                    channelKey = channelKey,
                    enabled = preferences[PreferenceKeys.channelEnabled(channelKey)] ?: defaults.enabled,
                    soundEnabled = preferences[PreferenceKeys.channelSoundEnabled(channelKey)] ?: defaults.soundEnabled,
                    soundPreset = preferences[PreferenceKeys.channelSoundPreset(channelKey)]
                        ?.toReminderSoundPreset(logger)
                        ?: defaults.soundPreset,
                    vibrationEnabled = preferences[PreferenceKeys.channelVibrationEnabled(channelKey)] ?: defaults.vibrationEnabled,
                )
            }
        }

    override suspend fun upsertChannelSettings(settings: ReminderChannelSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.channelEnabled(settings.channelKey)] = settings.enabled
            preferences[PreferenceKeys.channelSoundEnabled(settings.channelKey)] = settings.soundEnabled
            preferences[PreferenceKeys.channelSoundPreset(settings.channelKey)] = settings.soundPreset.name
            preferences[PreferenceKeys.channelVibrationEnabled(settings.channelKey)] = settings.vibrationEnabled
        }
    }
}

internal fun defaultReminderChannelSettings(
    channelKey: ReminderChannelKey,
) = when (channelKey) {
    ReminderChannelKey.CycleAlerts -> ReminderChannelSettings(
        channelKey = channelKey,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.GentleBell,
        vibrationEnabled = true,
    )
    ReminderChannelKey.FertilityAlerts -> ReminderChannelSettings(
        channelKey = channelKey,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.WaterDrop,
        vibrationEnabled = true,
    )
    ReminderChannelKey.DailyCheckIn -> ReminderChannelSettings(
        channelKey = channelKey,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.SoftChime,
        vibrationEnabled = false,
    )
}

