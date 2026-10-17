package com.cyclecare.feature.privacy

import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.model.ConsentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePrivacyRepository(
    initialConsent: ConsentState = ConsentState(
        hideNotificationPreview = true,
        localDataAcknowledged = false,
        appLockEnabled = false,
        biometricEnabled = false,
    ),
) : PrivacyRepository {
    val consent = MutableStateFlow(initialConsent)

    // Stable across calls: returning a fresh flow here would let the
    // ViewModel's combine capture a one-time snapshot that never
    // reflects a later setPin or clearPin.
    private val pinState = MutableStateFlow<String?>(null)
    private val lastUnlock = MutableStateFlow<Long?>(null)

    val pin: String?
        get() = pinState.value

    fun setPinForTest(value: String?) {
        pinState.value = value
    }
    var resetAppLockCalls: Int = 0
    var setPinCalls: Int = 0

    override fun observeConsent(): Flow<ConsentState> = consent

    override fun hasPinConfigured(): Flow<Boolean> = pinState.map { it != null }

    override fun observeLastUnlockEpochMillis(): Flow<Long?> = lastUnlock

    override fun isBiometricEnabled(): Flow<Boolean> = consent.map { it.biometricEnabled }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        consent.value = consent.value.copy(appLockEnabled = enabled)
    }

    override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
        consent.value = consent.value.copy(hideNotificationPreview = hidden)
    }

    override suspend fun acknowledgeLocalData() {
        consent.value = consent.value.copy(localDataAcknowledged = true)
    }

    override suspend fun setPin(pin: String) {
        pinState.value = pin
        setPinCalls++
    }

    override suspend fun clearPin() {
        pinState.value = null
    }

    override suspend fun resetAppLock() {
        pinState.value = null
        resetAppLockCalls++
        consent.value = consent.value.copy(appLockEnabled = false, biometricEnabled = false)
    }

    override suspend fun validatePin(pin: String): Boolean = this.pin == pin

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        consent.value = consent.value.copy(biometricEnabled = enabled)
    }

    override suspend fun markUnlocked(epochMillis: Long) {
        lastUnlock.value = epochMillis
    }
}