package com.cyclecare.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class OnboardingSavedStateTest {
    @Test
    fun persistedDraft_restoresFromSavedStateHandle() {
        val handle = SavedStateHandle()
        val expected = OnboardingUiState(
            ageBand = AgeBand.Thirties,
            cycleLength = 31,
            periodLength = 6,
            lastPeriodStart = LocalDate.of(2026, 3, 9),
            lastPeriodStartLabel = "March 9, 2026",
            goal = TrackingGoal.SymptomAwareness,
            remindersEnabled = false,
            privacyModeEnabled = false,
        )

        handle.persistDraft(expected)
        val restored = handle.toOnboardingUiState()

        assertThat(restored.ageBand).isEqualTo(expected.ageBand)
        assertThat(restored.cycleLength).isEqualTo(expected.cycleLength)
        assertThat(restored.periodLength).isEqualTo(expected.periodLength)
        assertThat(restored.lastPeriodStart).isEqualTo(expected.lastPeriodStart)
        assertThat(restored.goal).isEqualTo(expected.goal)
        assertThat(restored.remindersEnabled).isEqualTo(expected.remindersEnabled)
        assertThat(restored.privacyModeEnabled).isEqualTo(expected.privacyModeEnabled)
    }
}
