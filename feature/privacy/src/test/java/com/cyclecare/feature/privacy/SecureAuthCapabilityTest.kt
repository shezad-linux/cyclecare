package com.cyclecare.feature.privacy

import com.cyclecare.core.designsystem.text.UiText
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecureAuthCapabilityTest {
    @Test
    fun none_supportsNeitherBiometricNorDeviceCredential() {
        assertThat(SecureAuthCapability.None.supportsBiometric).isFalse()
        assertThat(SecureAuthCapability.None.supportsDeviceCredential).isFalse()
    }

    @Test
    fun biometric_supportsOnlyBiometric() {
        assertThat(SecureAuthCapability.Biometric.supportsBiometric).isTrue()
        assertThat(SecureAuthCapability.Biometric.supportsDeviceCredential).isFalse()
    }

    @Test
    fun deviceCredential_supportsOnlyDeviceCredential() {
        assertThat(SecureAuthCapability.DeviceCredential.supportsBiometric).isFalse()
        assertThat(SecureAuthCapability.DeviceCredential.supportsDeviceCredential).isTrue()
    }

    @Test
    fun both_supportsBoth() {
        assertThat(SecureAuthCapability.Both.supportsBiometric).isTrue()
        assertThat(SecureAuthCapability.Both.supportsDeviceCredential).isTrue()
    }

    @Test
    fun summaryLabel_isDistinctForEveryCapability() {
        val labels = SecureAuthCapability.entries.map { it.summaryLabel() }

        assertThat(labels).hasSize(SecureAuthCapability.entries.size)
        assertThat(labels).containsNoDuplicates()
    }

    @Test
    fun summaryLabel_neverPromisesAnUnavailableMethod() {
        assertThat(SecureAuthCapability.None.summaryLabel())
            .isEqualTo(UiText.Resource(R.string.no_secure_recovery))
        assertThat(SecureAuthCapability.Biometric.summaryLabel())
            .isEqualTo(UiText.Resource(R.string.biometric_recovery))
        assertThat(SecureAuthCapability.DeviceCredential.summaryLabel())
            .isEqualTo(UiText.Resource(R.string.screen_lock_recovery))
        assertThat(SecureAuthCapability.Both.summaryLabel())
            .isEqualTo(UiText.Resource(R.string.biometric_and_screen_lock_recovery))
    }
}