@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

data class SettingsUiState(
    val consentState: ConsentState = ConsentState(
        hideNotificationPreview = true,
        localDataAcknowledged = false,
        appLockEnabled = false,
    ),
    val remindersEnabled: Boolean = true,
    val reminderItems: List<SettingsReminderItem> = emptyList(),
    val channelItems: List<SettingsChannelItem> = emptyList(),
)

data class SettingsReminderItem(
    val rule: ReminderRule,
    val title: UiText,
    val scheduleLabel: UiText,
    val detail: UiText,
)

data class SettingsChannelItem(
    val settings: ReminderChannelSettings,
    val title: UiText,
    val detail: UiText,
)

private val reminderTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal object SettingsTags {
    const val LIST = "settings_list"
    const val OPEN_RESET = "settings_open_reset"
    const val CONFIRM_RESET = "settings_confirm_reset"
    const val REMINDERS_ENABLED = "settings_reminders_enabled"
    const val NOTIFICATION_RECOVERY = "settings_notification_recovery"
    const val OPEN_REMINDER_CENTER = "settings_open_reminder_center"
    const val OPEN_AD_PRIVACY = "settings_open_ad_privacy"
    const val OPEN_EXACT_ALARMS = "settings_open_exact_alarms"
}

internal fun ReminderType.displayTitle(): UiText = when (this) {
    ReminderType.PeriodPrep -> uiText(R.string.reminder_type_period_preparation)
    ReminderType.FertileWindowStart -> uiText(R.string.reminder_type_fertile_window)
    ReminderType.OvulationEstimate -> uiText(R.string.reminder_type_ovulation_estimate)
    ReminderType.DailyLog -> uiText(R.string.reminder_type_daily_check_in)
    ReminderType.LatePeriod -> uiText(R.string.reminder_type_late_period_check)
    ReminderType.Hydration -> uiText(R.string.reminder_type_hydration)
    ReminderType.Pill -> uiText(R.string.reminder_type_pill)
}

internal fun buildReminderScheduleLabel(rule: ReminderRule): UiText {
    val timeLabel = LocalTime.of(rule.hourOfDay, rule.minute).format(reminderTimeFormatter)
    val label = when (rule.type) {
        ReminderType.PeriodPrep -> {
            val offset = rule.daysBefore.absoluteValue
            pluralUiText(R.plurals.days_before, offset, offset)
        }
        ReminderType.FertileWindowStart -> uiText(R.string.reminder_schedule_window_start)
        ReminderType.OvulationEstimate -> uiText(R.string.reminder_type_ovulation_estimate)
        ReminderType.DailyLog -> uiText(R.string.reminder_schedule_daily)
        ReminderType.LatePeriod -> {
            val offset = rule.daysBefore.absoluteValue
            pluralUiText(R.plurals.days_late, offset, offset)
        }
        else -> null
    }
    return if (label == null) {
        UiText.Raw(timeLabel)
    } else {
        uiText(R.string.reminder_schedule_separator, label, timeLabel)
    }
}

internal fun ReminderType.detailCopy(): UiText = when (this) {
    ReminderType.PeriodPrep -> uiText(R.string.reminder_detail_period_preparation)
    ReminderType.FertileWindowStart -> uiText(R.string.reminder_detail_fertile_window)
    ReminderType.OvulationEstimate -> uiText(R.string.reminder_detail_ovulation_estimate)
    ReminderType.DailyLog -> uiText(R.string.reminder_detail_daily_log)
    ReminderType.LatePeriod -> uiText(R.string.reminder_detail_late_period)
    ReminderType.Hydration -> uiText(R.string.reminder_detail_hydration)
    ReminderType.Pill -> uiText(R.string.reminder_detail_pill)
}

private val reminderDisplayOrder = listOf(
    ReminderType.PeriodPrep,
    ReminderType.FertileWindowStart,
    ReminderType.OvulationEstimate,
    ReminderType.DailyLog,
    ReminderType.LatePeriod,
)

internal fun buildReminderItems(rules: List<ReminderRule>): List<SettingsReminderItem> =
    rules
        .filter { it.type in reminderDisplayOrder }
        .sortedWith(compareBy({ reminderDisplayOrder.indexOf(it.type).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it.id }))
        .map { rule ->
            SettingsReminderItem(
                rule = rule,
                title = rule.type.displayTitle(),
                scheduleLabel = buildReminderScheduleLabel(rule),
                detail = rule.type.detailCopy(),
            )
        }

private val channelDisplayOrder = listOf(
    ReminderChannelKey.CycleAlerts,
    ReminderChannelKey.FertilityAlerts,
    ReminderChannelKey.DailyCheckIn,
)

internal fun ReminderChannelKey.displayTitle(): UiText = when (this) {
    ReminderChannelKey.CycleAlerts -> uiText(R.string.channel_cycle_alerts)
    ReminderChannelKey.FertilityAlerts -> uiText(R.string.channel_fertility_alerts)
    ReminderChannelKey.DailyCheckIn -> uiText(R.string.channel_daily_check_in)
}

internal fun ReminderChannelKey.detailCopy(): UiText = when (this) {
    ReminderChannelKey.CycleAlerts -> uiText(R.string.channel_detail_cycle_alerts)
    ReminderChannelKey.FertilityAlerts -> uiText(R.string.channel_detail_fertility_alerts)
    ReminderChannelKey.DailyCheckIn -> uiText(R.string.channel_detail_daily_check_in)
}

internal fun ReminderSoundPreset.displayTitle(): UiText = when (this) {
    ReminderSoundPreset.GentleBell -> uiText(R.string.sound_gentle_bell)
    ReminderSoundPreset.SoftChime -> uiText(R.string.sound_soft_chime)
    ReminderSoundPreset.WaterDrop -> uiText(R.string.sound_water_drop)
}

internal fun buildChannelItems(settings: List<ReminderChannelSettings>): List<SettingsChannelItem> =
    settings
        .sortedBy { channelDisplayOrder.indexOf(it.channelKey).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        .map { item ->
            SettingsChannelItem(
                settings = item,
                title = item.channelKey.displayTitle(),
                detail = item.channelKey.detailCopy(),
            )
        }

