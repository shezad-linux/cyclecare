package com.cyclecare.feature.logging

import androidx.lifecycle.SavedStateHandle
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoggingSavedStateTest {
    @Test
    fun loggingEditorState_roundTripsThroughSavedStateHandle() {
        val handle = SavedStateHandle()
        val expected = LoggingEditorState(
            hydrationKey = "entry-1",
            existingEntryId = "entry-1",
            mood = Mood.Happy,
            painLevel = PainLevel.Moderate,
            energyLevel = EnergyLevel.High,
            dischargeType = DischargeType.EggWhite,
            medication = "Ibuprofen",
            notes = "Energy improved by afternoon",
            isDetailsExpanded = true,
        )

        handle.persistLoggingEditorState(expected)
        val restored = handle.toLoggingEditorState()

        assertThat(restored).isEqualTo(expected)
    }
}
