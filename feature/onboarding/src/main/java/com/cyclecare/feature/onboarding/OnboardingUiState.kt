@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import java.time.LocalDate

data class OnboardingUiState(
    val ageBand: AgeBand = AgeBand.Twenties,
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lastPeriodStart: LocalDate = LocalDate.now().minusDays(5),
    val lastPeriodStartLabel: String = LocalDate.now().minusDays(5).formatOnboardingDate(),
    val goal: TrackingGoal = TrackingGoal.PeriodPrep,
    val remindersEnabled: Boolean = false,
    val privacyModeEnabled: Boolean = true,
    val showDatePicker: Boolean = false,
    val saving: Boolean = false,
) {
    val cycleLengthLabel: UiText get() = pluralUiText(R.plurals.n_days, cycleLength, cycleLength)
    val periodLengthLabel: UiText get() = pluralUiText(R.plurals.n_days, periodLength, periodLength)
    val cycleLengthError: UiText?
        get() = if (cycleLength in 20..45) null else uiText(R.string.cycle_length_out_of_range)
    val periodLengthError: UiText?
        get() = if (periodLength in 2..10) null else uiText(R.string.period_length_out_of_range)
    val lastPeriodStartError: UiText?
        get() = if (!lastPeriodStart.isAfter(LocalDate.now())) null else uiText(R.string.last_period_start_in_future)
    val isSubmitEnabled: Boolean
        get() = !saving &&
            cycleLengthError == null &&
            periodLengthError == null &&
            lastPeriodStartError == null
}

internal object OnboardingTags {
    const val CYCLE_LENGTH_VALUE = "onboarding_cycle_length_value"
    const val CYCLE_LENGTH_SLIDER = "onboarding_cycle_length_slider"
    const val LAST_PERIOD_ROW = "onboarding_last_period_row"
    const val DATE_PICKER = "onboarding_date_picker"
    const val DATE_PICKER_CONFIRM = "onboarding_date_picker_confirm"
    const val START_TRACKING_BUTTON = "onboarding_start_tracking"
}

internal fun AgeBand.displayLabel(): UiText = when (this) {
    AgeBand.Teen -> uiText(R.string.age_band_teen)
    AgeBand.Twenties -> uiText(R.string.age_band_twenties)
    AgeBand.Thirties -> uiText(R.string.age_band_thirties)
    AgeBand.FortiesPlus -> uiText(R.string.age_band_forties_plus)
}

internal fun TrackingGoal.displayLabel(): UiText = when (this) {
    TrackingGoal.PeriodPrep -> uiText(R.string.goal_period_preparation)
    TrackingGoal.SymptomAwareness -> uiText(R.string.goal_symptom_tracking)
    TrackingGoal.ConceptionAwareness -> uiText(R.string.goal_pregnancy_planning)
}

internal fun TrackingGoal.description(): UiText = when (this) {
    TrackingGoal.PeriodPrep -> uiText(R.string.goal_period_preparation_description)
    TrackingGoal.SymptomAwareness -> uiText(R.string.goal_symptom_tracking_description)
    TrackingGoal.ConceptionAwareness -> uiText(R.string.goal_pregnancy_planning_description)
}

internal fun LocalDate.formatOnboardingDate(): String = onboardingDateFormatter.format(this)

