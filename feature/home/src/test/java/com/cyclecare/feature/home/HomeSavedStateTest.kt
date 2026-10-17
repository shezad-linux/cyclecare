package com.cyclecare.feature.home

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeSavedStateTest {
    @Test
    fun homeEditorState_roundTripsThroughSavedStateHandle() {
        val handle = SavedStateHandle()
        val expected = HomeEditorState(
            isVisible = true,
            isExpanded = true,
            editingId = "cycle-1",
            startDateText = "2026-03-10",
            endDateText = "2026-03-14",
            cycleLengthText = "29",
            notes = "Updated from home sheet",
        )

        handle.persistHomeEditorState(expected)
        val restored = handle.toHomeEditorState()

        assertThat(restored).isEqualTo(expected)
        handle.clearHomeEditorState()
        assertThat(handle.toHomeEditorState()).isEqualTo(HomeEditorState())
    }
}
