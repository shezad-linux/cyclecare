package com.cyclecare.feature.settings

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

        assertThat(items[0].title).isEqualTo("Period preparation")
        assertThat(items[0].scheduleLabel).isEqualTo("2 days before • 9:00 AM")
        assertThat(items[1].title).isEqualTo("Fertile window")
        assertThat(items[1].scheduleLabel).isEqualTo("Window start • 9:00 AM")
        assertThat(items[2].title).isEqualTo("Daily check-in")
        assertThat(items[2].scheduleLabel).isEqualTo("Daily • 8:00 PM")
    }

    @Test
    fun `reminder type detail copy stays user facing`() {
        assertThat(ReminderType.PeriodPrep.detailCopy()).isEqualTo("Heads-up before your predicted start date.")
        assertThat(ReminderType.FertileWindowStart.detailCopy()).isEqualTo("Heads-up when your fertile window estimate begins.")
        assertThat(ReminderType.OvulationEstimate.detailCopy()).isEqualTo("Heads-up on your ovulation estimate day.")
        assertThat(ReminderType.DailyLog.detailCopy()).isEqualTo("Prompt to capture mood, pain, energy, and notes.")
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

        assertThat(ReminderType.OvulationEstimate.displayTitle()).isEqualTo("Ovulation estimate")
        assertThat(buildReminderScheduleLabel(rule)).isEqualTo("Ovulation estimate • 7:30 AM")
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

        assertThat(items.map { it.title }).containsExactly("Cycle alerts", "Daily check-in").inOrder()
    }
}
