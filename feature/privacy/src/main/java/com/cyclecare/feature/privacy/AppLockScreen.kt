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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.cyclecare.core.designsystem.DashboardKeyValueRow
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.text.resolve

@Composable
internal fun PrivacyScreen(
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
                            text = uiState.secureAuthCapability.summaryLabel().resolve(),
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
                            value = uiState.secureAuthCapability.summaryLabel().resolve(),
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
                                    text = text.resolve(),
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
                        message = uiState.message?.resolve(),
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

@Composable
private fun PrivacyEntryMode.toContent(): PrivacyEntryContent = when (this) {
    PrivacyEntryMode.Dashboard -> PrivacyEntryContent(
        title = stringResource(R.string.pin_ready),
        subtitle = stringResource(R.string.your_app_lock_is_configured),
        primaryActionLabel = stringResource(R.string.update_pin),
    )
    PrivacyEntryMode.Setup -> PrivacyEntryContent(
        title = stringResource(R.string.create_a_pin),
        subtitle = stringResource(R.string.choose_a_4_digit_pin_before_app_lock_can_turn_on),
        primaryActionLabel = stringResource(R.string.continue_label),
    )
    PrivacyEntryMode.ConfirmSetup -> PrivacyEntryContent(
        title = stringResource(R.string.confirm_your_pin),
        subtitle = stringResource(R.string.enter_the_same_4_digits_again_to_lock_in_local_access),
        primaryActionLabel = stringResource(R.string.save_pin),
        secondaryActionLabel = stringResource(R.string.start_over),
        secondaryAction = PrivacySecondaryAction.StartSetup,
    )
    PrivacyEntryMode.VerifyCurrentPin -> PrivacyEntryContent(
        title = stringResource(R.string.verify_current_pin),
        subtitle = stringResource(R.string.confirm_your_existing_pin_before_changing_it),
        primaryActionLabel = stringResource(R.string.verify_pin),
        secondaryActionLabel = stringResource(R.string.recover_access),
        secondaryAction = PrivacySecondaryAction.Recover,
    )
    PrivacyEntryMode.CreateNewPin -> PrivacyEntryContent(
        title = stringResource(R.string.create_a_new_pin),
        subtitle = stringResource(R.string.choose_a_fresh_4_digit_pin_for_local_app_lock),
        primaryActionLabel = stringResource(R.string.continue_label),
    )
    PrivacyEntryMode.ConfirmNewPin -> PrivacyEntryContent(
        title = stringResource(R.string.confirm_new_pin),
        subtitle = stringResource(R.string.re_enter_the_new_pin_to_finish_the_update),
        primaryActionLabel = stringResource(R.string.update_pin),
        secondaryActionLabel = stringResource(R.string.start_over),
        secondaryAction = PrivacySecondaryAction.StartChange,
    )
    PrivacyEntryMode.Unlock -> PrivacyEntryContent(
        title = stringResource(R.string.unlock_cyclecare),
        subtitle = stringResource(R.string.enter_your_4_digit_pin_to_reopen_local_cycle_data),
        primaryActionLabel = stringResource(R.string.unlock),
        secondaryActionLabel = stringResource(R.string.recover_access),
        secondaryAction = PrivacySecondaryAction.Recover,
    )
}

internal fun PrivacyEntryMode.resolveFor(hasPinConfigured: Boolean): PrivacyEntryMode = when {
    !hasPinConfigured && this == PrivacyEntryMode.ConfirmSetup -> PrivacyEntryMode.ConfirmSetup
    !hasPinConfigured -> PrivacyEntryMode.Setup
    hasPinConfigured && this in listOf(PrivacyEntryMode.Setup, PrivacyEntryMode.ConfirmSetup) -> PrivacyEntryMode.Dashboard
    else -> this
}

@Composable
internal fun SettingSwitchRow(
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
