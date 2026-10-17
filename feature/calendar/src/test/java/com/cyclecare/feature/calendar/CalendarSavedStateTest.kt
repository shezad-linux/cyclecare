package com.cyclecare.feature.calendar

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalendarSavedStateTest {
    @Test
    fun calendarEditorState_roundTripsThroughSavedStateHandle() {
        val handle = SavedStateHandle()
        val expected = CycleEditorState(
            isVisible = true,
            editingId = "cycle-2",
            startDateText = "2026-03-11",
            endDateText = "2026-03-15",
            cycleLengthText = "28",
            notes = "History edit",
        )

        handle.persistCycleEditorState(expected)
        val restored = handle.toCycleEditorState()

        assertThat(restored).isEqualTo(expected)
        handle.clearCycleEditorState()
        assertThat(handle.toCycleEditorState()).isEqualTo(CycleEditorState())
    }
}
