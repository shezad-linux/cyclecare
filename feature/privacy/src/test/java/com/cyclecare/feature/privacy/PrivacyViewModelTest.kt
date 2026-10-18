package com.cyclecare.feature.privacy

import com.cyclecare.core.designsystem.text.UiText
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * uiState is a stateIn(..., SharingStarted.WhileSubscribed) flow and
 * viewModelScope runs on Dispatchers.Main, which is the test dispatcher.
 * Two things follow: the test must hold an active subscriber, and the
 * dispatcher must be advanced before uiState reflects a mutation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakePrivacyRepository
    private lateinit var viewModel: PrivacyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePrivacyRepository()
        viewModel = PrivacyViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun appendDigit_stopsAtFourDigits() = runTest(dispatcher) {
        subscribe()

        repeat(6) { viewModel.appendDigit(it % 10) }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pinEntry).hasLength(4)
    }

    @Test
    fun removeLastDigit_dropsTrailingCharacter() = runTest(dispatcher) {
        subscribe()

        viewModel.appendDigit(1)
        viewModel.appendDigit(2)
        viewModel.removeLastDigit()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pinEntry).isEqualTo("1")
    }

    @Test
    fun submitWithFewerThanFourDigits_reportsErrorAndDoesNotStage() = runTest(dispatcher) {
        subscribe()

        type("12")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.pin_must_be_exactly_4_digits))
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.Setup)
        assertThat(repository.pin).isNull()
    }

    @Test
    fun setupWithMatchingConfirmation_storesPinEnablesAppLockAndLandsOnDashboard() = runTest(dispatcher) {
        subscribe()

        type("1234")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.ConfirmSetup)

        type("1234")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()

        assertThat(repository.pin).isEqualTo("1234")
        assertThat(repository.consent.value.appLockEnabled).isTrue()
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.Dashboard)
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.pin_saved_app_lock_is_on))
    }

    @Test
    fun setupWithMismatchedConfirmation_restartsSetupAndStoresNothing() = runTest(dispatcher) {
        subscribe()

        type("1234")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()

        type("9999")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()

        assertThat(repository.pin).isNull()
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.Setup)
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.pins_didnt_match_start_again))
    }

    @Test
    fun togglingAppLockOnWithoutPinIsRefused() = runTest(dispatcher) {
        subscribe()

        viewModel.toggleAppLock(true)
        advanceUntilIdle()

        assertThat(repository.consent.value.appLockEnabled).isFalse()
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.create_and_confirm_pin_first))
    }

    @Test
    fun verifyingCurrentPinWithWrongValue_staysInVerifyMode() = runTest(dispatcher) {
        repository.setPinForTest("1234")
        subscribe()

        viewModel.requestPinChange()
        advanceUntilIdle()
        type("0000")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.VerifyCurrentPin)
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.current_pin_didnt_match_try_again))
    }

    @Test
    fun verifyingCurrentPinThenChangingIt_replacesStoredPin() = runTest(dispatcher) {
        repository.setPinForTest("1234")
        subscribe()

        viewModel.requestPinChange()
        advanceUntilIdle()
        type("1234")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.CreateNewPin)

        type("5678")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.ConfirmNewPin)

        type("5678")
        viewModel.submitPrimaryAction()
        advanceUntilIdle()

        assertThat(repository.pin).isEqualTo("5678")
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.pin_updated_locally))
    }

    @Test
    fun enablingBiometric_requiresAppLockPinAndHardwareSupport() = runTest(dispatcher) {
        subscribe()

        viewModel.synchronizeSecureAuthCapability(SecureAuthCapability.Both)
        advanceUntilIdle()
        viewModel.onBiometricEnabled()
        advanceUntilIdle()

        assertThat(repository.consent.value.biometricEnabled).isFalse()
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.turn_on_app_lock_and_keep_a_pin_before_enabling_biometric_unlock))
    }

    @Test
    fun enablingBiometric_succeedsOnceLockPinAndCapabilityArePresent() = runTest(dispatcher) {
        repository.setPinForTest("1234")
        repository.consent.value = repository.consent.value.copy(appLockEnabled = true)
        subscribe()

        viewModel.synchronizeSecureAuthCapability(SecureAuthCapability.Both)
        advanceUntilIdle()
        viewModel.onBiometricEnabled()
        advanceUntilIdle()

        assertThat(repository.consent.value.biometricEnabled).isTrue()
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.biometric_unlock_is_ready))
    }

    @Test
    fun losingHardwareSupport_clearsBiometricAndExplains() = runTest(dispatcher) {
        repository.setPinForTest("1234")
        repository.consent.value = repository.consent.value.copy(appLockEnabled = true, biometricEnabled = true)
        subscribe()

        viewModel.synchronizeSecureAuthCapability(SecureAuthCapability.Both)
        advanceUntilIdle()
        viewModel.synchronizeSecureAuthCapability(SecureAuthCapability.None)
        advanceUntilIdle()

        assertThat(repository.consent.value.biometricEnabled).isFalse()
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.biometric_unlock_is_no_longer_available_on_this_device))
    }

    @Test
    fun secureRecovery_clearsPinAndLockAndReturnsToSetup() = runTest(dispatcher) {
        repository.setPinForTest("1234")
        repository.consent.value = repository.consent.value.copy(appLockEnabled = true)
        subscribe()

        viewModel.onSecureRecoveryComplete()
        advanceUntilIdle()

        assertThat(repository.pin).isNull()
        assertThat(repository.consent.value.appLockEnabled).isFalse()
        assertThat(repository.resetAppLockCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.entryMode).isEqualTo(PrivacyEntryMode.Setup)
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(UiText.Resource(R.string.access_recovered_create_a_new_pin))
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun type(pin: String) {
        pin.forEach { viewModel.appendDigit(it.digitToInt()) }
    }
}