package com.cyclecare.feature.privacy

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

    LaunchedEffect(capability) {
        viewModel.synchronizeSecureAuthCapability(capability)
    }

    PrivacyScreen(
        uiState = uiState,
        onToggleAppLock = viewModel::toggleAppLock,
        onEnableBiometric = {
            authController.authenticate(
                preference = SecureAuthPreference.PreferBiometric,
                title = "Enable biometric unlock",
                subtitle = "Authenticate once to enable biometric unlock for CycleCare.",
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
                title = "Recover access",
                subtitle = "Authenticate with biometrics or your screen lock to reset the app PIN.",
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
            .padding(16.dp)
            .testTag(PrivacyUiTags.SCREEN),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeaderCard(
                title = if (uiState.entryMode == PrivacyEntryMode.Dashboard) "Privacy lock" else entryContent.title,
                subtitle = if (uiState.entryMode == PrivacyEntryMode.Dashboard) {
                    "A local PIN guards sensitive screens, while secure device auth handles recovery."
                } else {
                    entryContent.subtitle
                },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardPill(text = if (uiState.appLockEnabled) "App lock on" else "App lock off")
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
                        title = "Protection",
                        subtitle = "Use a 4-digit PIN for local lock access and optionally allow biometric unlock.",
                        eyebrow = "Security",
                    ) {
                        SettingSwitchRow(
                            title = "Enable app lock",
                            detail = "Sensitive screens require your PIN after the unlock grace period expires.",
                            checked = uiState.appLockEnabled,
                            onCheckedChange = onToggleAppLock,
                            switchTag = PrivacyUiTags.APP_LOCK_SWITCH,
                        )
                        DashboardKeyValueRow(
                            label = "Recovery",
                            value = uiState.secureAuthCapability.summaryLabel(),
                        )
                        if (uiState.secureAuthCapability.supportsBiometric) {
                            SettingSwitchRow(
                                title = "Use biometric unlock",
                                detail = "Authenticate once to allow fingerprint or face unlock when the device supports it.",
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
                                text = "Biometric unlock is unavailable. Screen-lock recovery is still supported when your device has a secure lock screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    DashboardSectionCard(
                        title = if (uiState.hasPinConfigured) "PIN ready" else "Create your PIN",
                        subtitle = if (uiState.hasPinConfigured) {
                            "Update your PIN or recover access using secure device authentication."
                        } else {
                            "Create and confirm a PIN before enabling local app lock."
                        },
                        eyebrow = "PIN access",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardKeyValueRow(
                                label = "Unlock method",
                                value = if (uiState.biometricEnabled) "PIN and biometric" else "PIN only",
                            )
                            DashboardKeyValueRow(
                                label = "Notification preview",
                                value = if (uiState.hideNotificationPreview) "Private" else "Detailed",
                            )
                            FilledTonalButton(
                                onClick = if (uiState.hasPinConfigured) onStartPinChange else onStartPinSetup,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(PrivacyUiTags.UPDATE_PIN_BUTTON),
                            ) {
                                Text(if (uiState.hasPinConfigured) "Update PIN" else "Create PIN")
                            }
                            if (uiState.secureAuthCapability != SecureAuthCapability.None) {
                                OutlinedButton(
                                    onClick = onRecoverAccess,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(PrivacyUiTags.RECOVER_BUTTON),
                                ) {
                                    Text("Recover access")
                                }
                            } else {
                                Text(
                                    text = "Secure recovery isn't available on this device. Keep your PIN safe or use Reset local data from Settings if you lose access.",
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
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
