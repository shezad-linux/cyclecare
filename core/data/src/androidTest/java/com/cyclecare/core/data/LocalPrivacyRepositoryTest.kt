package com.cyclecare.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPrivacyRepositoryTest {
    private lateinit var repository: LocalPrivacyRepository

    @Before
    fun setUp() = runBlocking {
        repository = LocalPrivacyRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        repository.resetAppLock()
    }

    @After
    fun tearDown() = runBlocking {
        repository.resetAppLock()
    }

    @Test
    fun setPin_thenValidatePin_acceptsSavedPinOnly() = runBlocking {
        repository.setPin("1234")

        assertThat(repository.hasPinConfigured().first()).isTrue()
        assertThat(repository.validatePin("1234")).isTrue()
        assertThat(repository.validatePin("9999")).isFalse()
    }

    @Test
    fun resetAppLock_clearsPinUnlockTimestampAndBiometricState() = runBlocking {
        repository.setPin("1234")
        repository.setAppLockEnabled(true)
        repository.setBiometricEnabled(true)
        repository.markUnlocked(epochMillis = 42L)

        repository.resetAppLock()

        val consent = repository.observeConsent().first()
        assertThat(repository.hasPinConfigured().first()).isFalse()
        assertThat(repository.validatePin("1234")).isFalse()
        assertThat(repository.observeLastUnlockEpochMillis().first()).isNull()
        assertThat(consent.appLockEnabled).isFalse()
        assertThat(consent.biometricEnabled).isFalse()
    }
}
