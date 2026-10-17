package com.cyclecare.feature.onboarding

import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import org.junit.Test

class OnboardingPresentationTest {
    @Test
    fun ageBandDisplayLabels_areUserFriendly() {
        assertThat(AgeBand.Teen.displayLabel()).isEqualTo("Teen (13-19)")
        assertThat(AgeBand.Twenties.displayLabel()).isEqualTo("20s")
        assertThat(AgeBand.Thirties.displayLabel()).isEqualTo("30s")
        assertThat(AgeBand.FortiesPlus.displayLabel()).isEqualTo("40+")
    }

    @Test
    fun trackingGoalDisplayLabels_areUserFriendly() {
        assertThat(TrackingGoal.PeriodPrep.displayLabel()).isEqualTo("Period Preparation")
        assertThat(TrackingGoal.SymptomAwareness.displayLabel()).isEqualTo("Symptom Tracking")
        assertThat(TrackingGoal.ConceptionAwareness.displayLabel()).isEqualTo("Pregnancy Planning")
    }

    @Test
    fun submitEnabled_isTrueForDefaultValidState() {
        assertThat(OnboardingUiState().isSubmitEnabled).isTrue()
    }

    @Test
    fun submitEnabled_isFalseForFutureDate() {
        val state = OnboardingUiState(lastPeriodStart = LocalDate.now().plusDays(1))

        assertThat(state.isSubmitEnabled).isFalse()
        assertThat(state.lastPeriodStartError).isEqualTo("Last period start cannot be in the future.")
    }

    @Test
    fun submitEnabled_isFalseForInvalidCycleLength() {
        val state = OnboardingUiState(cycleLength = 19)

        assertThat(state.isSubmitEnabled).isFalse()
        assertThat(state.cycleLengthError).isEqualTo("Cycle length must stay between 20 and 45 days.")
    }

    @Test
    fun invalidSavedStateEnums_fallBackToSafeDefaults() {
        val restored = SavedStateHandle(
            mapOf(
                "age_band" to "UnknownBand",
                "goal" to "UnknownGoal",
            ),
        ).toOnboardingUiState()

        assertThat(restored.ageBand).isEqualTo(AgeBand.Twenties)
        assertThat(restored.goal).isEqualTo(TrackingGoal.PeriodPrep)
    }
}
