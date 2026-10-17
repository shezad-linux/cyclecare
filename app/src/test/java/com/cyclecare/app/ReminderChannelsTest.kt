package com.cyclecare.app

import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderSoundPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReminderChannelsTest {
    @Test
    fun channelId_usesVersionedSchema() {
        val channelId = ReminderChannels.channelId(
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.CycleAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.GentleBell,
                vibrationEnabled = true,
            ),
        )

        assertThat(channelId).startsWith("cyclecare_v2_")
    }
}
