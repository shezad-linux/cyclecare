package com.cyclecare.app
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.res.stringResource
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
    val context = LocalContext.current
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

    // Hoisted so the local functions and the callbacks below can use them.
    // A string resource cannot be read inside a callback, only during
    // composition.
    val unlockTitle = stringResource(R.string.unlock_cyclecare)
    val unlockSubtitle = stringResource(R.string.unlock_cyclecare_subtitle)
    val tooManyFailedAttempts = stringResource(R.string.too_many_failed_attempts)
    val recoverAccessTitle = stringResource(R.string.recover_access)
    val recoverAccessSubtitle = stringResource(R.string.recover_access_subtitle)
    val useBiometricInstead = stringResource(R.string.use_biometric_instead)

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
            title = unlockTitle,
            subtitle = unlockSubtitle,
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
                    Text(stringResource(R.string.reset_local_data))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.reset_local_data_title)) },
            text = {
                Text(stringResource(R.string.reset_local_data_message))
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
            // The scrim stays full-bleed; the content column caps at the
            // same width as every other screen so a tablet does not render
            // a phone-width PIN form across 800+ dp.
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = CenterConstrainedMaxWidth)
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(CycleCareSpace.space16),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
            contentPadding = PaddingValues(bottom = CycleCareSpace.space24),
        ) {
            item {
                GradientHeaderCard(
                    title = stringResource(R.string.cyclecare_locked),
                    subtitle = stringResource(R.string.cyclecare_locked_subtitle),
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
                            DashboardPill(
                                text = if (canUseBiometric) {
                                    stringResource(R.string.biometric_ready)
                                } else {
                                    stringResource(R.string.pin_required)
                                },
                            )
                            DashboardPill(
                                text = capability.summaryLabel().resolve(),
                                emphasized = capability != SecureAuthCapability.None,
                            )
                        }
                    },
                )
            }

            if (!canRecover) {
                item {
                    DashboardSectionCard(
                        title = stringResource(R.string.no_secure_recovery),
                        subtitle = stringResource(R.string.no_secure_recovery_subtitle),
                        eyebrow = stringResource(R.string.recovery_eyebrow),
                    ) {
                        OutlinedButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PrivacyUiTags.RESET_LOCAL_DATA),
                        ) {
                            Text(stringResource(R.string.reset_local_data))
                        }
                    }
                }
            }

            item {
                val subtitle = if (isLockedOut) {
                    stringResource(R.string.lockout_message, cooldownSecondsLeft)
                } else if (canUseBiometric) {
                    stringResource(R.string.biometric_unlock_prompt)
                } else {
                    stringResource(R.string.pin_unlock_prompt)
                }
                PrivacyPinEntryCard(
                    title = stringResource(R.string.unlock_cyclecare),
                    subtitle = subtitle,
                    pinLength = pinEntry.length,
                    message = message,
                    primaryActionLabel = if (isLockedOut) {
                        stringResource(R.string.wait_cooldown, cooldownSecondsLeft)
                    } else {
                        stringResource(R.string.unlock)
                    },
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
                                        message = tooManyFailedAttempts
                                    } else {
                                        message = context.resources.getQuantityString(
                                            R.plurals.attempts_remaining,
                                            5 - failedAttempts,
                                            5 - failedAttempts,
                                        )
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
                    secondaryActionLabel = if (canRecover) recoverAccessTitle else null,
                    onSecondaryAction = if (canRecover) {
                        {
                            secureAuthController.authenticate(
                                preference = SecureAuthPreference.PreferBiometric,
                                title = recoverAccessTitle,
                                subtitle = recoverAccessSubtitle,
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
                    tertiaryActionLabel = if (canUseBiometric) useBiometricInstead else null,
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
