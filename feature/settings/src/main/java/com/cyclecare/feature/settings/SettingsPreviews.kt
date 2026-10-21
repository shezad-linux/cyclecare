@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.settings

import androidx.compose.runtime.Composable
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenWidthPreviews
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType

@ScreenWidthPreviews
@Composable
internal fun SettingsScreenPreview() {
    CycleCareTheme {
        SettingsScreen(
            uiState = previewSettingsState(),
            notificationsCanPost = true,
            notificationRecoveryActionLabel = null,
            onResolveNotificationAccess = {},
            onToggleRemindersEnabled = {},
            onToggleNotificationPreview = {},
            onToggleReminderRule = { _, _ -> },
            onUpdateChannelSettings = {},
            onOpenResetConfirmation = {},
            onOpenPrivacy = {},
            onOpenExport = {},
            onOpenReminderCenter = {},
            onOpenMigrationRecord = {},
            unreadReminderCount = 2,
            onPreviewReminderSound = {},
            showAdPrivacyOptions = false,
            onOpenAdPrivacyOptions = {},
            exactAlarmAccessGranted = true,
            canRequestExactAlarms = false,
            onRequestExactAlarms = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
internal fun SettingsNotificationsBlockedPreview() {
    CycleCareTheme {
        SettingsScreen(
            uiState = previewSettingsState(),
            notificationsCanPost = false,
            notificationRecoveryActionLabel = "Open notification settings",
            onResolveNotificationAccess = {},
            onToggleRemindersEnabled = {},
            onToggleNotificationPreview = {},
            onToggleReminderRule = { _, _ -> },
            onUpdateChannelSettings = {},
            onOpenResetConfirmation = {},
            onOpenPrivacy = {},
            onOpenExport = {},
            onOpenReminderCenter = {},
            onOpenMigrationRecord = {},
            unreadReminderCount = 0,
            onPreviewReminderSound = {},
            showAdPrivacyOptions = true,
            onOpenAdPrivacyOptions = {},
            exactAlarmAccessGranted = false,
            canRequestExactAlarms = false,
            onRequestExactAlarms = {},
        )
    }
}

/** The settings screen with one reminder rule and the three channels. */
internal fun previewSettingsState() = SettingsUiState(
    remindersEnabled = true,
    reminderItems = listOf(
        SettingsReminderItem(
            rule = ReminderRule(
                id = "daily-log",
                type = ReminderType.DailyLog,
                enabled = true,
                daysBefore = 0,
                hourOfDay = 20,
                minute = 0,
            ),
            title = uiText(R.string.reminder_type_daily_check_in),
            scheduleLabel = uiText(R.string.reminder_schedule_daily),
            detail = uiText(R.string.reminder_detail_daily_log),
        ),
    ),
    channelItems = listOf(
        SettingsChannelItem(
            settings = ReminderChannelSettings(
                channelKey = ReminderChannelKey.CycleAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.GentleBell,
                vibrationEnabled = true,
            ),
            title = uiText(R.string.channel_cycle_alerts),
            detail = uiText(R.string.channel_detail_cycle_alerts),
        ),
        SettingsChannelItem(
            settings = ReminderChannelSettings(
                channelKey = ReminderChannelKey.FertilityAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.WaterDrop,
                vibrationEnabled = true,
            ),
            title = uiText(R.string.channel_fertility_alerts),
            detail = uiText(R.string.channel_detail_fertility_alerts),
        ),
        SettingsChannelItem(
            settings = ReminderChannelSettings(
                channelKey = ReminderChannelKey.DailyCheckIn,
                enabled = false,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.SoftChime,
                vibrationEnabled = false,
            ),
            title = uiText(R.string.channel_daily_check_in),
            detail = uiText(R.string.channel_detail_daily_check_in),
        ),
    ),
)
