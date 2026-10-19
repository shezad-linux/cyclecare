package com.cyclecare.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import java.security.SecureRandom
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.model.ConsentState
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalPrivacyRepository(
    private val context: Context,
) : PrivacyRepository {
    override fun observeConsent(): Flow<ConsentState> =
        context.dataStore.data.map { preferences ->
            ConsentState(
                hideNotificationPreview = preferences[PreferenceKeys.hideNotificationPreview] ?: true,
                localDataAcknowledged = preferences[PreferenceKeys.localDataAcknowledged] ?: false,
                appLockEnabled = preferences[PreferenceKeys.appLockEnabled] ?: false,
                biometricEnabled = preferences[PreferenceKeys.biometricEnabled] ?: false,
            )
        }

    override fun hasPinConfigured(): Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            !preferences[PreferenceKeys.pinHash].isNullOrBlank()
        }

    override fun observeLastUnlockEpochMillis(): Flow<Long?> =
        context.dataStore.data.map { preferences -> preferences[PreferenceKeys.lastUnlockEpochMillis] }

    override fun isBiometricEnabled(): Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferenceKeys.biometricEnabled] ?: false
        }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.appLockEnabled] = enabled
            if (!enabled) {
                preferences[PreferenceKeys.biometricEnabled] = false
            }
        }
    }

    override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.hideNotificationPreview] = hidden
        }
    }

    override suspend fun acknowledgeLocalData() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.localDataAcknowledged] = true
        }
    }

    override suspend fun setPin(pin: String) {
        val salt = SecureRandom().let { random ->
            ByteArray(16).also { random.nextBytes(it) }
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.pinSalt] = salt
            preferences[PreferenceKeys.pinHash] = (salt + pin).sha256()
        }
    }

    override suspend fun clearPin() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.pinHash)
            preferences.remove(PreferenceKeys.pinSalt)
            preferences.remove(PreferenceKeys.lastUnlockEpochMillis)
            preferences[PreferenceKeys.appLockEnabled] = false
            preferences[PreferenceKeys.biometricEnabled] = false
        }
    }

    override suspend fun resetAppLock() {
        clearPin()
    }

    override suspend fun validatePin(pin: String): Boolean {
        val preferences = context.dataStore.dataMap()
        val salt = preferences[PreferenceKeys.pinSalt] ?: return false
        return preferences[PreferenceKeys.pinHash] == (salt + pin).sha256()
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val hasPin = !preferences[PreferenceKeys.pinHash].isNullOrBlank()
            val appLockEnabled = preferences[PreferenceKeys.appLockEnabled] ?: false
            preferences[PreferenceKeys.biometricEnabled] = enabled && hasPin && appLockEnabled
        }
    }

    override suspend fun markUnlocked(epochMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.lastUnlockEpochMillis] = epochMillis
        }
    }
}

internal fun String.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

