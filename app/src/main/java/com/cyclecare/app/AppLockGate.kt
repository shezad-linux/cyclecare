package com.cyclecare.app
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.feature.privacy.PrivacyPinEntryCard
import com.cyclecare.feature.privacy.PrivacyUiTags
import com.cyclecare.feature.privacy.SecureAuthCapability
import com.cyclecare.feature.privacy.SecureAuthController
import com.cyclecare.feature.privacy.SecureAuthPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object AppLockTags {
    const val OVERLAY = "app_lock_overlay"
    const val BIOMETRIC_UNLOCK = "app_lock_biometric_unlock"
    const val RESET_DIALOG_CONFIRM = "app_lock_reset_confirm"
}

@Composable
internal fun AppLockGate(
    privacyRepository: PrivacyRepository,
    appResetRepository: AppResetRepository,
    secureAuthController: SecureAuthController,
    onOpenPrivacyAfterRecovery: () -> Unit,
) {
    var pinEntry by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var autoBiometricAttempted by rememberSaveable { mutableStateOf(false) }
    var failedAttempts by rememberSaveable { mutableIntStateOf(0) }
    var lockedUntilMillis by rememberSaveable { mutableLongStateOf(0L) }
    var cooldownSecondsLeft by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val capability by secureAuthController.capability.collectAsStateWithLifecycle(
        initialValue = secureAuthController.capability.value,
    )
    val biometricEnabled by privacyRepository.isBiometricEnabled().collectAsStateWithLifecycle(initialValue = false)
    val now = System.currentTimeMillis()
    val isLockedOut = lockedUntilMillis > now
    val canRecover = capability != SecureAuthCapability.None
    val canUseBiometric = biometricEnabled && capability.supportsBiometric

    LaunchedEffect(lockedUntilMillis) {
        while (lockedUntilMillis > System.currentTimeMillis()) {
            cooldownSecondsLeft = ((lockedUntilMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1)
            delay(1_000L)
        }
        cooldownSecondsLeft = 0
    }

    LaunchedEffect(capability, biometricEnabled) {
        if (!capability.supportsBiometric && biometricEnabled) {
            privacyRepository.setBiometricEnabled(false)
        }
    }

    fun requestBiometricUnlock() {
        secureAuthController.authenticate(
            preference = SecureAuthPreference.PreferBiometric,
            title = "Unlock CycleCare",
            subtitle = "Use biometrics to unlock local cycle data.",
            onSuccess = {
                scope.launch {
                    failedAttempts = 0
                    privacyRepository.markUnlocked()
                    pinEntry = ""
                    message = null
                }
            },
            onFailure = { error -> message = error },
        )
    }

    LaunchedEffect(canUseBiometric, isLockedOut) {
        if (!canUseBiometric || isLockedOut) {
            autoBiometricAttempted = false
            return@LaunchedEffect
        }
        if (!autoBiometricAttempted) {
            autoBiometricAttempted = true
            requestBiometricUnlock()
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        scope.launch { appResetRepository.resetAllLocalData() }
                    },
                    modifier = Modifier.testTag(AppLockTags.RESET_DIALOG_CONFIRM),
                ) {
                    Text("Reset local data")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Reset local data?") },
            text = {
                Text("This clears your local profile, cycle data, reminders, exports, and privacy settings so you can start over.")
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            .testTag(AppLockTags.OVERLAY),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(CycleCareSpace.space16),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
            contentPadding = PaddingValues(bottom = CycleCareSpace.space24),
        ) {
            item {
                GradientHeaderCard(
                    title = "CycleCare locked",
                    subtitle = "Reopen local cycle data with your PIN or biometrics. Secure device auth is also available for PIN recovery.",
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
                            DashboardPill(text = if (canUseBiometric) "Biometric ready" else "PIN required")
                            DashboardPill(
                                text = capability.summaryLabel(),
                                emphasized = capability != SecureAuthCapability.None,
                            )
                        }
                    },
                )
            }

            if (!canRecover) {
                item {
                    DashboardSectionCard(
                        title = "No secure recovery",
                        subtitle = "This device has no biometric or screen-lock recovery set up. If you lose your PIN, the only way forward is to reset local data.",
                        eyebrow = "Recovery",
                    ) {
                        OutlinedButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PrivacyUiTags.RESET_LOCAL_DATA),
                        ) {
                            Text("Reset local data")
                        }
                    }
                }
            }

            item {
                val subtitle = if (isLockedOut) {
                    "Too many failed attempts. Wait ${cooldownSecondsLeft}s before trying again."
                } else if (canUseBiometric) {
                    "Use biometrics now or enter your 4-digit PIN to reopen local cycle data."
                } else {
                    "Enter your 4-digit PIN to reopen local cycle data."
                }
                PrivacyPinEntryCard(
                    title = "Unlock CycleCare",
                    subtitle = subtitle,
                    pinLength = pinEntry.length,
                    message = message,
                    primaryActionLabel = if (isLockedOut) "Wait ${cooldownSecondsLeft}s" else "Unlock",
                    primaryEnabled = !isLockedOut && pinEntry.length == 4,
                    onPrimaryAction = {
                        if (!isLockedOut) {
                            scope.launch {
                                val valid = privacyRepository.validatePin(pinEntry)
                                if (valid) {
                                    failedAttempts = 0
                                    privacyRepository.markUnlocked()
                                    pinEntry = ""
                                    message = null
                                } else {
                                    pinEntry = ""
                                    failedAttempts += 1
                                    if (failedAttempts >= 5) {
                                        lockedUntilMillis = System.currentTimeMillis() + 30_000L
                                        message = "Too many failed attempts. Try again in 30 seconds."
                                    } else {
                                        message = "Incorrect PIN. ${5 - failedAttempts} attempts remaining."
                                    }
                                }
                            }
                        }
                    },
                    onDigitPressed = { digit ->
                        if (!isLockedOut && pinEntry.length < 4) {
                            pinEntry += digit.toString()
                        }
                    },
                    onDelete = {
                        if (!isLockedOut) {
                            pinEntry = pinEntry.dropLast(1)
                        }
                    },
                    secondaryActionLabel = if (canRecover) "Recover access" else null,
                    onSecondaryAction = if (canRecover) {
                        {
                            secureAuthController.authenticate(
                                preference = SecureAuthPreference.PreferBiometric,
                                title = "Recover access",
                                subtitle = "Authenticate with biometrics or your screen lock to reset the app PIN.",
                                onSuccess = {
                                    scope.launch {
                                        privacyRepository.resetAppLock()
                                        pinEntry = ""
                                        message = null
                                        failedAttempts = 0
                                        onOpenPrivacyAfterRecovery()
                                    }
                                },
                                onFailure = { error -> message = error },
                            )
                        }
                    } else {
                        null
                    },
                    tertiaryActionLabel = if (canUseBiometric) "Use biometric instead" else null,
                    onTertiaryAction = if (canUseBiometric) {
                        ::requestBiometricUnlock
                    } else {
                        null
                    },
                )
            }
        }
    }
}
