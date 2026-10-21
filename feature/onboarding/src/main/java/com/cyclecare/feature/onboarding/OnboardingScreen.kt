@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun SavedStateHandle.toOnboardingUiState(): OnboardingUiState {
    val defaultDate = LocalDate.now().minusDays(5)
    val lastPeriodStart = get<Long>(OnboardingStateKeys.LastPeriodStartEpochDay)
        ?.let(LocalDate::ofEpochDay)
        ?: defaultDate
    return OnboardingUiState(
        ageBand = get<String>(OnboardingStateKeys.AgeBand)
            ?.let { value -> runCatching { AgeBand.valueOf(value) }.getOrNull() }
            ?: AgeBand.Twenties,
        cycleLengthDays = get<Int>(OnboardingStateKeys.CycleLength) ?: 28,
        periodLength = get<Int>(OnboardingStateKeys.PeriodLength) ?: 5,
        lastPeriodStart = lastPeriodStart,
        lastPeriodStartLabel = lastPeriodStart.formatOnboardingDate(),
        goal = get<String>(OnboardingStateKeys.Goal)
            ?.let { value -> runCatching { TrackingGoal.valueOf(value) }.getOrNull() }
            ?: TrackingGoal.PeriodPrep,
        remindersEnabled = get<Boolean>(OnboardingStateKeys.RemindersEnabled) ?: true,
        privacyModeEnabled = get<Boolean>(OnboardingStateKeys.PrivacyModeEnabled) ?: true,
    )
}

internal fun SavedStateHandle.persistDraft(state: OnboardingUiState) {
    this[OnboardingStateKeys.AgeBand] = state.ageBand.name
    this[OnboardingStateKeys.CycleLength] = state.cycleLengthDays
    this[OnboardingStateKeys.PeriodLength] = state.periodLength
    this[OnboardingStateKeys.LastPeriodStartEpochDay] = state.lastPeriodStart.toEpochDay()
    this[OnboardingStateKeys.Goal] = state.goal.name
    this[OnboardingStateKeys.RemindersEnabled] = state.remindersEnabled
    this[OnboardingStateKeys.PrivacyModeEnabled] = state.privacyModeEnabled
}

@ScreenDebugPreviews
@Composable
private fun OnboardingScreenPreview() {
    CycleCareTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(),
            onAgeBandSelected = {},
            onCycleLengthIncrement = {},
            onPeriodLengthChanged = {},
            onGoalSelected = {},
            onRemindersChanged = {},
            onPrivacyChanged = {},
            onOpenDatePicker = {},
            onDismissDatePicker = {},
            onDateSelected = {},
            onContinue = {},
        )
    }
}

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onAgeBandSelected: (AgeBand) -> Unit,
    onCycleLengthIncrement: (Int) -> Unit,
    onPeriodLengthChanged: (Int) -> Unit,
    onGoalSelected: (TrackingGoal) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onPrivacyChanged: (Boolean) -> Unit,
    onOpenDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onContinue: () -> Unit,
) {
    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val progress by animateFloatAsState(targetValue = if (currentStep == 1) 0.5f else 1f, label = "progress")

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentStep = currentStep,
                saving = uiState.saving,
                enabled = uiState.isSubmitEnabled,
                onStep1Continue = { currentStep = 2 },
                onBack = { currentStep = 1 },
                onContinue = onContinue,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = CycleCareSpace.space16, vertical = CycleCareSpace.space20),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
        ) {
            OnboardingProgressHeader(currentStep = currentStep, progress = progress)

            GradientHeaderCard(
                title = stringResource(R.string.private_cycle_tracking),
                subtitle = stringResource(R.string.private_tracking_subtitle),
                contentPadding = PaddingValues(horizontal = CycleCareSpace.space20, vertical = CycleCareSpace.space18),
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        OnboardingPill(text = stringResource(R.string.local_profile))
                        OnboardingPill(text = stringResource(R.string.editable_later))
                    }
                },
            )

            if (currentStep == 1) {
                OnboardingBaselineStep(
                    uiState = uiState,
                    onAgeBandSelected = onAgeBandSelected,
                    onCycleLengthIncrement = onCycleLengthIncrement,
                    onPeriodLengthChanged = onPeriodLengthChanged,
                    onOpenDatePicker = onOpenDatePicker,
                )
            } else {
                OnboardingPreferencesStep(
                    uiState = uiState,
                    onGoalSelected = onGoalSelected,
                    onRemindersChanged = onRemindersChanged,
                    onPrivacyChanged = onPrivacyChanged,
                )
            }
        }
    }

    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.lastPeriodStart.toEpochMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !utcTimeMillis.toLocalDate().isAfter(LocalDate.now())
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                Button(
                    modifier = Modifier.testTag(OnboardingTags.DATE_PICKER_CONFIRM),
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            onDateSelected(selectedMillis.toLocalDate())
                        } ?: onDismissDatePicker()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                modifier = Modifier.testTag(OnboardingTags.DATE_PICKER),
            )
        }
    }
}

