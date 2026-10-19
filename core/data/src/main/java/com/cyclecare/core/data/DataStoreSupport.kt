package com.cyclecare.core.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cyclecare.core.model.ReminderChannelKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal object PreferenceKeys {
    val hideNotificationPreview = booleanPreferencesKey("hide_notification_preview")
    val localDataAcknowledged = booleanPreferencesKey("local_data_acknowledged")
    val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
    val pinHash = stringPreferencesKey("pin_hash")
    val pinSalt = stringPreferencesKey("pin_salt")
    val lastUnlockEpochMillis = longPreferencesKey("last_unlock_epoch_millis")
    val biometricEnabled = booleanPreferencesKey("biometric_enabled")

    fun channelEnabled(key: ReminderChannelKey) = booleanPreferencesKey("reminder_channel_${key.name.lowercase()}_enabled")
    fun channelSoundEnabled(key: ReminderChannelKey) = booleanPreferencesKey("reminder_channel_${key.name.lowercase()}_sound_enabled")
    fun channelSoundPreset(key: ReminderChannelKey) = stringPreferencesKey("reminder_channel_${key.name.lowercase()}_sound_preset")
    fun channelVibrationEnabled(key: ReminderChannelKey) = booleanPreferencesKey("reminder_channel_${key.name.lowercase()}_vibration_enabled")
}

internal suspend fun androidx.datastore.core.DataStore<Preferences>.dataMap(): Preferences = data.first()

internal suspend fun <T> Flow<T>.firstValue(): T = first()

