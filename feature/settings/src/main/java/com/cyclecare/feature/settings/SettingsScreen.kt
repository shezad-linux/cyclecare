@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset



@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    notificationsCanPost: Boolean,
    notificationRecoveryActionLabel: String?,
    onResolveNotificationAccess: () -> Unit,
    onToggleRemindersEnabled: (Boolean) -> Unit,
    onToggleNotificationPreview: (Boolean) -> Unit,
    onToggleReminderRule: (ReminderRule, Boolean) -> Unit,
    onUpdateChannelSettings: (ReminderChannelSettings) -> Unit,
    onOpenResetConfirmation: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenReminderCenter: () -> Unit,
    unreadReminderCount: Int,
    onPreviewReminderSound: (ReminderSoundPreset) -> Unit,
    showAdPrivacyOptions: Boolean,
    onOpenAdPrivacyOptions: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(CycleCareSpace.space16)
            .testTag(SettingsTags.LIST),
        contentPadding = PaddingValues(bottom = CycleCareSpace.space16),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            GradientHeaderCard(
                title = stringResource(R.string.settings),
                subtitle = stringResource(R.string.local_first_controls_for_reminders_privacy_and_export),
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
                        SettingsBadge(text = stringResource(R.string.stored_locally))
                        SettingsBadge(
                            text = stringResource(
                                if (notificationsCanPost) {
                                    R.string.notifications_on
                                } else {
                                    R.string.notifications_blocked
                                },
                            ),
                            emphasized = !notificationsCanPost,
                        )
                    }
                },
            )
        }

        item {
            SettingsMetricGrid(
                remindersEnabled = uiState.remindersEnabled,
                unreadReminderCount = unreadReminderCount,
                previewMode = stringResource(
                    if (uiState.consentState.hideNotificationPreview) {
                        R.string.preview_mode_private
                    } else {
                        R.string.detailed
                    },
                ),
            )
        }

        item {
            ActionCard(
                title = stringResource(R.string.reminder_center),
                body = if (unreadReminderCount == 0) {
                    stringResource(R.string.reopen_recent_reminders_and_in_app_alerts_even_if_android_bl)
                } else {
                    pluralStringResource(
                        R.plurals.unread_reminders_waiting_in_your_local_reminder_center,
                        unreadReminderCount,
                        unreadReminderCount,
                    )
                },
                actionLabel = stringResource(R.string.open_reminder_center),
                onAction = onOpenReminderCenter,
                actionModifier = Modifier.testTag(SettingsTags.OPEN_REMINDER_CENTER),
            )
        }

        if (!notificationsCanPost) {
            item {
                SectionCard(
                    title = stringResource(R.string.notifications_are_blocked),
                    body = stringResource(
                        R.string.period_fertile_window_and_daily_check_in_reminders_stay_loca,
                    ),
                ) {
                    notificationRecoveryActionLabel?.let { actionLabel ->
                        OutlinedButton(
                            onClick = onResolveNotificationAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTags.NOTIFICATION_RECOVERY),
                        ) {
                            Text(actionLabel)
                        }
                    }
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(
                            R.string.keep_local_reminder_timing_active_across_baseline_edits_rebo,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitchRow(
                        modifier = Modifier.testTag(SettingsTags.REMINDERS_ENABLED),
                        title = stringResource(R.string.reminders_enabled),
                        detail = stringResource(R.string.cancel_or_restore_all_future_reminder_work_at_once),
                        checked = uiState.remindersEnabled,
                        onCheckedChange = onToggleRemindersEnabled,
                    )
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    Text(stringResource(R.string.alert_channels), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(
                            R.string.choose_calm_bundled_sounds_and_vibration_behavior_for_cycle,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.channelItems.forEachIndexed { index, item ->
                        ReminderChannelCard(
                            item = item,
                            remindersEnabled = uiState.remindersEnabled,
                            onToggleChannelEnabled = { enabled ->
                                onUpdateChannelSettings(item.settings.copy(enabled = enabled))
                            },
                            onToggleSoundEnabled = { enabled ->
                                onUpdateChannelSettings(item.settings.copy(soundEnabled = enabled))
                            },
                            onToggleVibrationEnabled = { enabled ->
                                onUpdateChannelSettings(item.settings.copy(vibrationEnabled = enabled))
                            },
                            onSelectSoundPreset = { preset ->
                                onUpdateChannelSettings(item.settings.copy(soundPreset = preset))
                            },
                            onPreviewSound = { onPreviewReminderSound(item.settings.soundPreset) },
                        )
                        if (index != uiState.channelItems.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    Text(
                        stringResource(R.string.notification_privacy),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.keep_sensitive_details_out_of_visible_lock_screen_reminders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.hide_notification_previews),
                        detail = stringResource(
                            R.string.show_a_generic_reminder_body_until_the_app_is_unlocked,
                        ),
                        checked = uiState.consentState.hideNotificationPreview,
                        onCheckedChange = onToggleNotificationPreview,
                    )
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    Text(stringResource(R.string.reminder_rules), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.control_which_local_reminders_stay_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.reminderItems.isEmpty()) {
                        Text(
                            stringResource(R.string.no_reminder_rules_are_available_yet),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        uiState.reminderItems.forEachIndexed { index, item ->
                            ReminderRuleCard(
                                item = item,
                                enabled = uiState.remindersEnabled,
                                onCheckedChange = { enabled -> onToggleReminderRule(item.rule, enabled) },
                            )
                            if (index != uiState.reminderItems.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }

        item {
            ActionCard(
                title = stringResource(R.string.privacy_lock),
                body = stringResource(R.string.set_or_update_your_pin_before_sensitive_screens_open),
                actionLabel = stringResource(R.string.open_privacy_lock),
                onAction = onOpenPrivacy,
            )
        }

        if (showAdPrivacyOptions) {
            item {
                ActionCard(
                    title = stringResource(R.string.ad_privacy_choices),
                    body = stringResource(
                        R.string.review_or_update_the_ad_consent_options_required_for_your_re,
                    ),
                    actionLabel = stringResource(R.string.manage_ad_privacy),
                    onAction = onOpenAdPrivacyOptions,
                    actionModifier = Modifier.testTag(SettingsTags.OPEN_AD_PRIVACY),
                )
            }
        }

        item {
            ActionCard(
                title = stringResource(R.string.export_and_share),
                body = stringResource(
                    R.string.create_local_markdown_or_pdf_reports_only_when_you_choose_to,
                ),
                actionLabel = stringResource(R.string.open_export),
                onAction = onOpenExport,
            )
        }

        item {
            ActionCard(
                title = stringResource(R.string.reset_local_data),
                body = stringResource(R.string.clear_local_health_data_reminder_settings_privacy_state_and_),
                actionLabel = stringResource(R.string.reset_local_data),
                onAction = onOpenResetConfirmation,
                actionModifier = Modifier.testTag(SettingsTags.OPEN_RESET),
            )
        }
    }
}

