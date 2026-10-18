package com.cyclecare.feature.privacy

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardKeyValueRow
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
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

data class PrivacyUiState(
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val hideNotificationPreview: Boolean = true,
    val entryMode: PrivacyEntryMode = PrivacyEntryMode.Setup,
    val pinEntry: String = "",
    val secureAuthCapability: SecureAuthCapability = SecureAuthCapability.None,
    val message: String? = null,
)

class PrivacyViewModel(
    private val privacyRepository: PrivacyRepository,
) : ViewModel() {
    private val pinEntry = MutableStateFlow("")
    private val stagedPin = MutableStateFlow<String?>(null)
    private val entryMode = MutableStateFlow(PrivacyEntryMode.Setup)
    private val secureAuthCapability = MutableStateFlow(SecureAuthCapability.None)
    private val message = MutableStateFlow<String?>(null)

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
    ) { requestedMode: PrivacyEntryMode, capability: SecureAuthCapability, messageText: String? ->
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
                    message.value = "Biometric unlock is no longer available on this device."
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
                message.value = "Create and confirm a PIN first."
                return@launch
            }
            privacyRepository.setAppLockEnabled(enabled)
            message.value = if (enabled) {
                "App lock is on for sensitive screens."
            } else {
                "App lock is off."
            }
        }
    }

    fun requestPinChange() {
        pinEntry.value = ""
        stagedPin.value = null
        entryMode.value = PrivacyEntryMode.VerifyCurrentPin
        message.value = "Enter your current PIN to continue."
    }

    fun startPinSetup() {
        pinEntry.value = ""
        stagedPin.value = null
        entryMode.value = PrivacyEntryMode.Setup
        message.value = "Create a 4-digit PIN for sensitive screens."
    }

    fun onBiometricEnabled() {
        viewModelScope.launch {
            val consent = privacyRepository.observeConsent().first()
            val hasPin = privacyRepository.hasPinConfigured().first()
            if (!hasPin || !consent.appLockEnabled || !secureAuthCapability.value.supportsBiometric) {
                message.value = "Turn on app lock and keep a PIN before enabling biometric unlock."
                return@launch
            }
            privacyRepository.setBiometricEnabled(true)
            message.value = "Biometric unlock is ready."
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            privacyRepository.setBiometricEnabled(false)
            message.value = "Biometric unlock is off."
        }
    }

    fun onSecureRecoveryComplete() {
        viewModelScope.launch {
            privacyRepository.resetAppLock()
            stagedPin.value = null
            pinEntry.value = ""
            entryMode.value = PrivacyEntryMode.Setup
            message.value = "Access recovered. Create a new PIN."
        }
    }

    fun showMessage(messageText: String) {
        message.value = messageText
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
            message.value = "PIN must be exactly 4 digits."
            return
        }
        stagedPin.value = currentPin
        pinEntry.value = ""
        entryMode.value = nextMode
        message.value = "Enter the same PIN again to confirm it."
    }

    private fun verifyCurrentPin() {
        viewModelScope.launch {
            val valid = privacyRepository.validatePin(pinEntry.value)
            pinEntry.value = ""
            if (!valid) {
                message.value = "Current PIN didn't match. Try again."
                return@launch
            }
            entryMode.value = PrivacyEntryMode.CreateNewPin
            message.value = "Enter a new 4-digit PIN."
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
                message.value = "PINs didn't match. Start again."
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
                "PIN saved. App lock is on."
            } else {
                "PIN updated locally."
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

@Composable
private fun PrivacyScreen(
    uiState: PrivacyUiState,
    onToggleAppLock: (Boolean) -> Unit,
    onEnableBiometric: () -> Unit,
    onDisableBiometric: () -> Unit,
    onDigitPressed: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmitPrimary: () -> Unit,
    onStartPinSetup: () -> Unit,
    onStartPinChange: () -> Unit,
    onRecoverAccess: () -> Unit,
) {
    val entryContent = uiState.entryMode.toContent()

    LazyColumn(
        modifier = Modifier
            .padding(CycleCareSpace.space16)
            .testTag(PrivacyUiTags.SCREEN),
        contentPadding = PaddingValues(bottom = CycleCareSpace.space24),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            GradientHeaderCard(
                title = if (uiState.entryMode == PrivacyEntryMode.Dashboard) {
                    stringResource(R.string.privacy_lock)
                } else {
                    entryContent.title
                },
                subtitle = if (uiState.entryMode == PrivacyEntryMode.Dashboard) {
                    stringResource(R.string.a_local_pin_guards_sensitive_screens_while_secure_device_au)
                } else {
                    entryContent.subtitle
                },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
                        DashboardPill(
                            text = if (uiState.appLockEnabled) {
                                stringResource(R.string.app_lock_on)
                            } else {
                                stringResource(R.string.app_lock_off)
                            },
                        )
                        DashboardPill(
                            text = uiState.secureAuthCapability.summaryLabel(),
                            emphasized = uiState.secureAuthCapability != SecureAuthCapability.None,
                        )
                    }
                },
            )
        }

        when (uiState.entryMode) {
            PrivacyEntryMode.Dashboard -> {
                item {
                    DashboardSectionCard(
                        title = stringResource(R.string.protection),
                        subtitle = stringResource(R.string.use_a_4_digit_pin_for_local_lock_access_and_optionally_allo),
                        eyebrow = stringResource(R.string.security),
                    ) {
                        SettingSwitchRow(
                            title = stringResource(R.string.enable_app_lock),
                            detail = stringResource(R.string.sensitive_screens_require_your_pin_after_the_unlock_grace_p),
                            checked = uiState.appLockEnabled,
                            onCheckedChange = onToggleAppLock,
                            switchTag = PrivacyUiTags.APP_LOCK_SWITCH,
                        )
                        DashboardKeyValueRow(
                            label = stringResource(R.string.recovery),
                            value = uiState.secureAuthCapability.summaryLabel(),
                        )
                        if (uiState.secureAuthCapability.supportsBiometric) {
                            SettingSwitchRow(
                                title = stringResource(R.string.use_biometric_unlock),
                                detail = stringResource(R.string.authenticate_once_to_allow_fingerprint_or_face_unlock_when_),
                                checked = uiState.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        onEnableBiometric()
                                    } else {
                                        onDisableBiometric()
                                    }
                                },
                                enabled = uiState.hasPinConfigured && uiState.appLockEnabled,
                                switchTag = PrivacyUiTags.BIOMETRIC_SWITCH,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.biometric_unlock_is_unavailable_screen_lock_recovery_is_sti),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    DashboardSectionCard(
                        title = if (uiState.hasPinConfigured) {
                            stringResource(R.string.pin_ready)
                        } else {
                            stringResource(R.string.create_your_pin)
                        },
                        subtitle = if (uiState.hasPinConfigured) {
                            stringResource(R.string.update_your_pin_or_recover_access_using_secure_device_authe)
                        } else {
                            stringResource(R.string.create_and_confirm_a_pin_before_enabling_local_app_lock)
                        },
                        eyebrow = stringResource(R.string.pin_access),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                            DashboardKeyValueRow(
                                label = stringResource(R.string.unlock_method),
                                value = if (uiState.biometricEnabled) {
                                    stringResource(R.string.pin_and_biometric)
                                } else {
                                    stringResource(R.string.pin_only)
                                },
                            )
                            DashboardKeyValueRow(
                                label = stringResource(R.string.notification_preview),
                                value = if (uiState.hideNotificationPreview) {
                                    stringResource(R.string.private_value)
                                } else {
                                    stringResource(R.string.detailed)
                                },
                            )
                            FilledTonalButton(
                                onClick = if (uiState.hasPinConfigured) onStartPinChange else onStartPinSetup,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(PrivacyUiTags.UPDATE_PIN_BUTTON),
                            ) {
                                Text(
                                    if (uiState.hasPinConfigured) {
                                        stringResource(R.string.update_pin)
                                    } else {
                                        stringResource(R.string.create_pin)
                                    },
                                )
                            }
                            if (uiState.secureAuthCapability != SecureAuthCapability.None) {
                                OutlinedButton(
                                    onClick = onRecoverAccess,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(PrivacyUiTags.RECOVER_BUTTON),
                                ) {
                                    Text(stringResource(R.string.recover_access))
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.secure_recovery_isn_t_available_on_this_device_keep_your_pi),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            uiState.message?.let { text ->
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                item {
                    val secondaryLabel =
                        if (entryContent.secondaryAction == PrivacySecondaryAction.Recover &&
                            uiState.secureAuthCapability == SecureAuthCapability.None
                        ) {
                            null
                        } else {
                            entryContent.secondaryActionLabel
                        }
                    PrivacyPinEntryCard(
                        title = entryContent.title,
                        subtitle = entryContent.subtitle,
                        pinLength = uiState.pinEntry.length,
                        message = uiState.message,
                        primaryActionLabel = entryContent.primaryActionLabel,
                        primaryEnabled = uiState.pinEntry.length == 4,
                        onPrimaryAction = onSubmitPrimary,
                        onDigitPressed = onDigitPressed,
                        onDelete = onDelete,
                        secondaryActionLabel = secondaryLabel,
                        onSecondaryAction = if (secondaryLabel == null) {
                            null
                        } else {
                            entryContent.secondaryAction?.let {
                            {
                                when (it) {
                                    PrivacySecondaryAction.StartSetup -> onStartPinSetup()
                                    PrivacySecondaryAction.StartChange -> onStartPinChange()
                                    PrivacySecondaryAction.Recover -> onRecoverAccess()
                                }
                            }
                            }
                        },
                    )
                }
            }
        }
    }
}

private enum class PrivacySecondaryAction {
    StartSetup,
    StartChange,
    Recover,
}

private data class PrivacyEntryContent(
    val title: String,
    val subtitle: String,
    val primaryActionLabel: String,
    val secondaryActionLabel: String? = null,
    val secondaryAction: PrivacySecondaryAction? = null,
)

private fun PrivacyEntryMode.toContent(): PrivacyEntryContent = when (this) {
    PrivacyEntryMode.Dashboard -> PrivacyEntryContent(
        title = "PIN ready",
        subtitle = "Your app lock is configured.",
        primaryActionLabel = "Update PIN",
    )
    PrivacyEntryMode.Setup -> PrivacyEntryContent(
        title = "Create a PIN",
        subtitle = "Choose a 4-digit PIN before app lock can turn on.",
        primaryActionLabel = "Continue",
    )
    PrivacyEntryMode.ConfirmSetup -> PrivacyEntryContent(
        title = "Confirm your PIN",
        subtitle = "Enter the same 4 digits again to lock in local access.",
        primaryActionLabel = "Save PIN",
        secondaryActionLabel = "Start over",
        secondaryAction = PrivacySecondaryAction.StartSetup,
    )
    PrivacyEntryMode.VerifyCurrentPin -> PrivacyEntryContent(
        title = "Verify current PIN",
        subtitle = "Confirm your existing PIN before changing it.",
        primaryActionLabel = "Verify PIN",
        secondaryActionLabel = "Recover access",
        secondaryAction = PrivacySecondaryAction.Recover,
    )
    PrivacyEntryMode.CreateNewPin -> PrivacyEntryContent(
        title = "Create a new PIN",
        subtitle = "Choose a fresh 4-digit PIN for local app lock.",
        primaryActionLabel = "Continue",
    )
    PrivacyEntryMode.ConfirmNewPin -> PrivacyEntryContent(
        title = "Confirm new PIN",
        subtitle = "Re-enter the new PIN to finish the update.",
        primaryActionLabel = "Update PIN",
        secondaryActionLabel = "Start over",
        secondaryAction = PrivacySecondaryAction.StartChange,
    )
    PrivacyEntryMode.Unlock -> PrivacyEntryContent(
        title = "Unlock CycleCare",
        subtitle = "Enter your 4-digit PIN to reopen local cycle data.",
        primaryActionLabel = "Unlock",
        secondaryActionLabel = "Recover access",
        secondaryAction = PrivacySecondaryAction.Recover,
    )
}

private fun PrivacyEntryMode.resolveFor(hasPinConfigured: Boolean): PrivacyEntryMode = when {
    !hasPinConfigured && this == PrivacyEntryMode.ConfirmSetup -> PrivacyEntryMode.ConfirmSetup
    !hasPinConfigured -> PrivacyEntryMode.Setup
    hasPinConfigured && this in listOf(PrivacyEntryMode.Setup, PrivacyEntryMode.ConfirmSetup) -> PrivacyEntryMode.Dashboard
    else -> this
}

@Composable
private fun SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTag: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = CycleCareSpace.space16),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(switchTag),
        )
    }
}
