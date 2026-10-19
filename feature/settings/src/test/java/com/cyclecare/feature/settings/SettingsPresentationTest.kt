package com.cyclecare.feature.settings

import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun `build reminder items formats user-facing copy`() {
        val items = buildReminderItems(
            listOf(
                ReminderRule(
                    id = "fertile",
                    type = ReminderType.FertileWindowStart,
                    enabled = true,
                    daysBefore = 0,
                    hourOfDay = 9,
                    minute = 0,
                ),
                ReminderRule(
                    id = "daily",
                    type = ReminderType.DailyLog,
                    enabled = true,
                    daysBefore = 0,
                    hourOfDay = 20,
                    minute = 0,
                ),
                ReminderRule(
                    id = "period",
                    type = ReminderType.PeriodPrep,
                    enabled = true,
                    daysBefore = -2,
                    hourOfDay = 9,
                    minute = 0,
                ),
            ),
        )

        // Asserted on resource identity rather than on English, so the
        // copy can be reworded or translated without breaking the test and
        // cannot drift away from the resource it claims to come from.
        assertThat(items[0].title).isEqualTo(uiText(R.string.reminder_type_period_preparation))
        assertThat(items[0].scheduleLabel)
            .isEqualTo(
                uiText(
                    R.string.reminder_schedule_separator,
                    pluralUiText(R.plurals.days_before, 2, 2),
                    "9:00 AM",
                ),
            )
        assertThat(items[1].title).isEqualTo(uiText(R.string.reminder_type_fertile_window))
        assertThat(items[1].scheduleLabel)
            .isEqualTo(
                uiText(
                    R.string.reminder_schedule_separator,
                    uiText(R.string.reminder_schedule_window_start),
                    "9:00 AM",
                ),
            )
        assertThat(items[2].title).isEqualTo(uiText(R.string.reminder_type_daily_check_in))
        assertThat(items[2].scheduleLabel)
            .isEqualTo(
                uiText(
                    R.string.reminder_schedule_separator,
                    uiText(R.string.reminder_schedule_daily),
                    "8:00 PM",
                ),
            )
    }

    @Test
    fun `reminder type detail copy stays user facing`() {
        assertThat(ReminderType.PeriodPrep.detailCopy())
            .isEqualTo(uiText(R.string.reminder_detail_period_preparation))
        assertThat(ReminderType.FertileWindowStart.detailCopy())
            .isEqualTo(uiText(R.string.reminder_detail_fertile_window))
        assertThat(ReminderType.OvulationEstimate.detailCopy())
            .isEqualTo(uiText(R.string.reminder_detail_ovulation_estimate))
        assertThat(ReminderType.DailyLog.detailCopy())
            .isEqualTo(uiText(R.string.reminder_detail_daily_log))
    }

    @Test
    fun `ovulation reminders use estimate wording consistently`() {
        val rule = ReminderRule(
            id = "ovulation",
            type = ReminderType.OvulationEstimate,
            enabled = true,
            daysBefore = 0,
            hourOfDay = 7,
            minute = 30,
        )

        assertThat(ReminderType.OvulationEstimate.displayTitle())
            .isEqualTo(uiText(R.string.reminder_type_ovulation_estimate))
        assertThat(buildReminderScheduleLabel(rule))
            .isEqualTo(
                uiText(
                    R.string.reminder_schedule_separator,
                    uiText(R.string.reminder_type_ovulation_estimate),
                    "7:30 AM",
                ),
            )
    }

    @Test
    fun `channel items stay in user-facing order`() {
        val items = buildChannelItems(
            listOf(
                ReminderChannelSettings(
                    channelKey = ReminderChannelKey.DailyCheckIn,
                    enabled = true,
                    soundEnabled = true,
                    soundPreset = ReminderSoundPreset.SoftChime,
                    vibrationEnabled = false,
                ),
                ReminderChannelSettings(
                    channelKey = ReminderChannelKey.CycleAlerts,
                    enabled = true,
                    soundEnabled = true,
                    soundPreset = ReminderSoundPreset.GentleBell,
                    vibrationEnabled = true,
                ),
            ),
        )

        assertThat(items.map { it.title })
            .containsExactly(
                uiText(R.string.channel_cycle_alerts),
                uiText(R.string.channel_daily_check_in),
            )
            .inOrder()
    }
}
