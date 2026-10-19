package com.cyclecare.feature.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.PrivacyRules
import com.cyclecare.core.model.ConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PrivacyViewModel(
    private val privacyRepository: PrivacyRepository,
) : ViewModel() {
    private val pinEntry = MutableStateFlow("")
    private val stagedPin = MutableStateFlow<String?>(null)
    private val entryMode = MutableStateFlow(PrivacyEntryMode.Setup)
    private val secureAuthCapability = MutableStateFlow(SecureAuthCapability.None)
    private val message = MutableStateFlow<UiText?>(null)

    private val privacyState = combine(
        privacyRepository.observeConsent(),
        privacyRepository.hasPinConfigured(),
        pinEntry,
    ) { consent: ConsentState, hasPin: Boolean, currentPinEntry: String ->
        Triple(consent, hasPin, currentPinEntry)
    }

    private val flowState = combine(
        entryMode,
        secureAuthCapability,
        message,
    ) { requestedMode: PrivacyEntryMode, capability: SecureAuthCapability, messageText: UiText? ->
        Triple(requestedMode, capability, messageText)
    }

    val uiState: StateFlow<PrivacyUiState> = combine(
        privacyState,
        flowState,
    ) { (consent, hasPin, currentPinEntry), (requestedMode, capability, messageText) ->
        PrivacyUiState(
            appLockEnabled = consent.appLockEnabled,
            biometricEnabled = consent.biometricEnabled,
            hasPinConfigured = hasPin,
            hideNotificationPreview = consent.hideNotificationPreview,
            entryMode = requestedMode.resolveFor(hasPinConfigured = hasPin),
            pinEntry = currentPinEntry,
            secureAuthCapability = capability,
            message = messageText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyUiState())

    fun synchronizeSecureAuthCapability(capability: SecureAuthCapability) {
        secureAuthCapability.value = capability
        viewModelScope.launch {
            val consent = privacyRepository.observeConsent().first()
            val hasPin = privacyRepository.hasPinConfigured().first()
            if ((!capability.supportsBiometric || !hasPin || !consent.appLockEnabled) && consent.biometricEnabled) {
                privacyRepository.setBiometricEnabled(false)
                if (!capability.supportsBiometric) {
                    message.value = uiText(R.string.biometric_unlock_is_no_longer_available_on_this_device)
                }
            }
        }
    }

    fun appendDigit(digit: Int) {
        pinEntry.update { current ->
            if (current.length >= 4) current else current + digit.toString()
        }
    }

    fun removeLastDigit() {
        pinEntry.update { current -> current.dropLast(1) }
    }

    fun toggleAppLock(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !privacyRepository.hasPinConfigured().first()) {
                entryMode.value = PrivacyEntryMode.Setup
                message.value = uiText(R.string.create_and_confirm_pin_first)
                return@launch
            }
            privacyRepository.setAppLockEnabled(enabled)
            message.value = if (enabled) {
                uiText(R.string.app_lock_is_on_for_sensitive_screens)
            } else {
                uiText(R.string.app_lock_is_off)
            }
        }
    }

    fun requestPinChange() {
        pinEntry.value = ""
        stagedPin.value = null
        entryMode.value = PrivacyEntryMode.VerifyCurrentPin
        message.value = uiText(R.string.enter_your_current_pin_to_continue)
    }

    fun startPinSetup() {
        pinEntry.value = ""
        stagedPin.value = null
        entryMode.value = PrivacyEntryMode.Setup
        message.value = uiText(R.string.create_a_4_digit_pin_for_sensitive_screens)
    }

    fun onBiometricEnabled() {
        viewModelScope.launch {
            val consent = privacyRepository.observeConsent().first()
            val hasPin = privacyRepository.hasPinConfigured().first()
            if (!hasPin || !consent.appLockEnabled || !secureAuthCapability.value.supportsBiometric) {
                message.value = uiText(R.string.turn_on_app_lock_and_keep_a_pin_before_enabling_biometric_unlock)
                return@launch
            }
            privacyRepository.setBiometricEnabled(true)
            message.value = uiText(R.string.biometric_unlock_is_ready)
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            privacyRepository.setBiometricEnabled(false)
            message.value = uiText(R.string.biometric_unlock_is_off)
        }
    }

    fun onSecureRecoveryComplete() {
        viewModelScope.launch {
            privacyRepository.resetAppLock()
            stagedPin.value = null
            pinEntry.value = ""
            entryMode.value = PrivacyEntryMode.Setup
            message.value = uiText(R.string.access_recovered_create_a_new_pin)
        }
    }

    fun showMessage(messageText: String) {
        // Already-localized text from the non-composable platform auth
        // controller: either the biometric prompt's own string
        // (platform-owned) or a message the controller resolved from its
        // resources against its Context.
        message.value = UiText.Raw(messageText)
    }

    fun submitPrimaryAction() {
        when (entryMode.value.resolveFor(hasPinConfigured = uiState.value.hasPinConfigured)) {
            PrivacyEntryMode.Setup -> stagePinForConfirmation(PrivacyEntryMode.ConfirmSetup)
            PrivacyEntryMode.ConfirmSetup -> commitNewPin(initialSetup = true)
            PrivacyEntryMode.VerifyCurrentPin -> verifyCurrentPin()
            PrivacyEntryMode.CreateNewPin -> stagePinForConfirmation(PrivacyEntryMode.ConfirmNewPin)
            PrivacyEntryMode.ConfirmNewPin -> commitNewPin(initialSetup = false)
            PrivacyEntryMode.Dashboard -> requestPinChange()
            PrivacyEntryMode.Unlock -> Unit
        }
    }

    private fun stagePinForConfirmation(nextMode: PrivacyEntryMode) {
        val currentPin = pinEntry.value
        if (!PrivacyRules.isPinValid(currentPin)) {
            message.value = uiText(R.string.pin_must_be_exactly_4_digits)
            return
        }
        stagedPin.value = currentPin
        pinEntry.value = ""
        entryMode.value = nextMode
        message.value = uiText(R.string.enter_the_same_pin_again_to_confirm_it)
    }

    private fun verifyCurrentPin() {
        viewModelScope.launch {
            val valid = privacyRepository.validatePin(pinEntry.value)
            pinEntry.value = ""
            if (!valid) {
                message.value = uiText(R.string.current_pin_didnt_match_try_again)
                return@launch
            }
            entryMode.value = PrivacyEntryMode.CreateNewPin
            message.value = uiText(R.string.enter_a_new_4_digit_pin)
        }
    }

    private fun commitNewPin(initialSetup: Boolean) {
        viewModelScope.launch {
            val candidate = pinEntry.value
            val staged = stagedPin.value
            if (candidate != staged) {
                stagedPin.value = null
                pinEntry.value = ""
                entryMode.value = if (initialSetup) PrivacyEntryMode.Setup else PrivacyEntryMode.CreateNewPin
                message.value = uiText(R.string.pins_didnt_match_start_again)
                return@launch
            }

            privacyRepository.setPin(candidate)
            if (initialSetup) {
                privacyRepository.setAppLockEnabled(true)
            }
            privacyRepository.markUnlocked()
            stagedPin.value = null
            pinEntry.value = ""
            entryMode.value = PrivacyEntryMode.Dashboard
            message.value = if (initialSetup) {
                uiText(R.string.pin_saved_app_lock_is_on)
            } else {
                uiText(R.string.pin_updated_locally)
            }
        }
    }
}

class PrivacyViewModelFactory(
    private val privacyRepository: PrivacyRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PrivacyViewModel(privacyRepository) as T
}

@Composable
fun PrivacyRoute(
    factory: PrivacyViewModelFactory,
    secureAuthController: SecureAuthController? = null,
) {
    val authController = secureAuthController ?: rememberPlatformSecureAuthController()
    val viewModel: PrivacyViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val capability by authController.capability.collectAsStateWithLifecycle(
        initialValue = authController.capability.value,
    )

    // The onEnableBiometric/onRecoverAccess lambdas below are plain
    // () -> Unit, not composable scopes, so the prompt copy has to be
    // resolved here and captured.
    val enableBiometricTitle = stringResource(R.string.enable_biometric_unlock)
    val enableBiometricSubtitle =
        stringResource(R.string.authenticate_once_to_enable_biometric_unlock_for_cyclecare)
    val recoverAccessTitle = stringResource(R.string.recover_access)
    val recoverAccessSubtitle =
        stringResource(R.string.authenticate_with_biometrics_or_your_screen_lock_to_reset_t)

    LaunchedEffect(capability) {
        viewModel.synchronizeSecureAuthCapability(capability)
    }

    PrivacyScreen(
        uiState = uiState,
        onToggleAppLock = viewModel::toggleAppLock,
        onEnableBiometric = {
            authController.authenticate(
                preference = SecureAuthPreference.PreferBiometric,
                title = enableBiometricTitle,
                subtitle = enableBiometricSubtitle,
                onSuccess = viewModel::onBiometricEnabled,
                onFailure = viewModel::showMessage,
            )
        },
        onDisableBiometric = viewModel::disableBiometric,
        onDigitPressed = viewModel::appendDigit,
        onDelete = viewModel::removeLastDigit,
        onSubmitPrimary = viewModel::submitPrimaryAction,
        onStartPinSetup = viewModel::startPinSetup,
        onStartPinChange = viewModel::requestPinChange,
        onRecoverAccess = {
            authController.authenticate(
                preference = SecureAuthPreference.PreferBiometric,
                title = recoverAccessTitle,
                subtitle = recoverAccessSubtitle,
                onSuccess = viewModel::onSecureRecoveryComplete,
                onFailure = viewModel::showMessage,
            )
        },
    )
}

