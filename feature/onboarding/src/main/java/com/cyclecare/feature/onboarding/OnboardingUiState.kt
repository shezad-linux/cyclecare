@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    val cycleLengthLabel: String get() = "$cycleLength days"
    val periodLengthLabel: String get() = "$periodLength days"
    val cycleLengthError: String?
        get() = if (cycleLength in 20..45) null else "Cycle length must stay between 20 and 45 days."
    val periodLengthError: String?
        get() = if (periodLength in 2..10) null else "Period length must stay between 2 and 10 days."
    val lastPeriodStartError: String?
        get() = if (!lastPeriodStart.isAfter(LocalDate.now())) null else "Last period start cannot be in the future."
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

internal fun AgeBand.displayLabel(): String = when (this) {
    AgeBand.Teen -> "Teen (13-19)"
    AgeBand.Twenties -> "20s"
    AgeBand.Thirties -> "30s"
    AgeBand.FortiesPlus -> "40+"
}

internal fun TrackingGoal.displayLabel(): String = when (this) {
    TrackingGoal.PeriodPrep -> "Period Preparation"
    TrackingGoal.SymptomAwareness -> "Symptom Tracking"
    TrackingGoal.ConceptionAwareness -> "Pregnancy Planning"
}

internal fun TrackingGoal.description(): String = when (this) {
    TrackingGoal.PeriodPrep -> "Get reminders before your period"
    TrackingGoal.SymptomAwareness -> "Track mood, pain, and trends"
    TrackingGoal.ConceptionAwareness -> "Focus on fertile window insights"
}

internal fun LocalDate.formatOnboardingDate(): String = onboardingDateFormatter.format(this)

