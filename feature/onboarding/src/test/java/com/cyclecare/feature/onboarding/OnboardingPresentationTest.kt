package com.cyclecare.feature.onboarding

import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import org.junit.Test

class OnboardingPresentationTest {
    @Test
    fun ageBandDisplayLabels_areUserFriendly() {
        // Asserted on resource identity rather than on English, so the
        // wording can change and the copy still cannot drift from the
        // resource it claims to come from.
        assertThat(AgeBand.Teen.displayLabel()).isEqualTo(uiText(R.string.age_band_teen))
        assertThat(AgeBand.Twenties.displayLabel()).isEqualTo(uiText(R.string.age_band_twenties))
        assertThat(AgeBand.Thirties.displayLabel()).isEqualTo(uiText(R.string.age_band_thirties))
        assertThat(AgeBand.FortiesPlus.displayLabel())
            .isEqualTo(uiText(R.string.age_band_forties_plus))
    }

    @Test
    fun trackingGoalDisplayLabels_areUserFriendly() {
        assertThat(TrackingGoal.PeriodPrep.displayLabel())
            .isEqualTo(uiText(R.string.goal_period_preparation))
        assertThat(TrackingGoal.SymptomAwareness.displayLabel())
            .isEqualTo(uiText(R.string.goal_symptom_tracking))
        assertThat(TrackingGoal.ConceptionAwareness.displayLabel())
            .isEqualTo(uiText(R.string.goal_pregnancy_planning))
    }

    @Test
    fun submitEnabled_isTrueForDefaultValidState() {
        assertThat(OnboardingUiState().isSubmitEnabled).isTrue()
    }

    @Test
    fun submitEnabled_isFalseForFutureDate() {
        val state = OnboardingUiState(lastPeriodStart = LocalDate.now().plusDays(1))

        assertThat(state.isSubmitEnabled).isFalse()
        assertThat(state.lastPeriodStartError)
            .isEqualTo(uiText(R.string.last_period_start_in_future))
    }

    @Test
    fun submitEnabled_isFalseForInvalidCycleLength() {
        val state = OnboardingUiState(cycleLength = 19)

        assertThat(state.isSubmitEnabled).isFalse()
        assertThat(state.cycleLengthError)
            .isEqualTo(uiText(R.string.cycle_length_out_of_range))
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
