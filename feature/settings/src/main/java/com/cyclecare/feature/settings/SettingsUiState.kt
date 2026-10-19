@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    val title: String,
    val scheduleLabel: String,
    val detail: String,
)

data class SettingsChannelItem(
    val settings: ReminderChannelSettings,
    val title: String,
    val detail: String,
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
}

internal fun ReminderType.displayTitle(): String = when (this) {
    ReminderType.PeriodPrep -> "Period preparation"
    ReminderType.FertileWindowStart -> "Fertile window"
    ReminderType.OvulationEstimate -> "Ovulation estimate"
    ReminderType.DailyLog -> "Daily check-in"
    ReminderType.LatePeriod -> "Late-period check"
    ReminderType.Hydration -> "Hydration reminder"
    ReminderType.Pill -> "Pill reminder"
}

internal fun buildReminderScheduleLabel(rule: ReminderRule): String {
    val timeLabel = LocalTime.of(rule.hourOfDay, rule.minute).format(reminderTimeFormatter)
    return when (rule.type) {
        ReminderType.PeriodPrep -> {
            val offset = rule.daysBefore.absoluteValue
            val dayLabel = if (offset == 1) "1 day before" else "$offset days before"
            "$dayLabel • $timeLabel"
        }
        ReminderType.FertileWindowStart -> "Window start • $timeLabel"
        ReminderType.OvulationEstimate -> "Ovulation estimate • $timeLabel"
        ReminderType.DailyLog -> "Daily • $timeLabel"
        ReminderType.LatePeriod -> {
            val offset = rule.daysBefore.absoluteValue
            val dayLabel = if (offset == 1) "1 day late" else "$offset days late"
            "$dayLabel • $timeLabel"
        }
        else -> timeLabel
    }
}

internal fun ReminderType.detailCopy(): String = when (this) {
    ReminderType.PeriodPrep -> "Heads-up before your predicted start date."
    ReminderType.FertileWindowStart -> "Heads-up when your fertile window estimate begins."
    ReminderType.OvulationEstimate -> "Heads-up on your ovulation estimate day."
    ReminderType.DailyLog -> "Prompt to capture mood, pain, energy, and notes."
    ReminderType.LatePeriod -> "Optional check-in if your predicted start date has already passed."
    ReminderType.Hydration -> "Gentle nudge to stay hydrated."
    ReminderType.Pill -> "Daily birth control reminder."
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

internal fun ReminderChannelKey.displayTitle(): String = when (this) {
    ReminderChannelKey.CycleAlerts -> "Cycle alerts"
    ReminderChannelKey.FertilityAlerts -> "Fertility alerts"
    ReminderChannelKey.DailyCheckIn -> "Daily check-in"
}

internal fun ReminderChannelKey.detailCopy(): String = when (this) {
    ReminderChannelKey.CycleAlerts -> "Period preparation and optional late-period reminders."
    ReminderChannelKey.FertilityAlerts -> "Fertile-window start and ovulation reminders."
    ReminderChannelKey.DailyCheckIn -> "Daily symptom logging prompts."
}

internal fun ReminderSoundPreset.displayTitle(): String = when (this) {
    ReminderSoundPreset.GentleBell -> "Gentle bell"
    ReminderSoundPreset.SoftChime -> "Soft chime"
    ReminderSoundPreset.WaterDrop -> "Water drop"
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

