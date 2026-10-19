@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import kotlin.math.roundToInt

@Composable
internal fun OnboardingBaselineStep(
    uiState: OnboardingUiState,
    onAgeBandSelected: (AgeBand) -> Unit,
    onCycleLengthIncrement: (Int) -> Unit,
    onPeriodLengthChanged: (Int) -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    OnboardingPanel(
        title = stringResource(R.string.baseline_setup),
        subtitle = stringResource(R.string.baseline_setup_subtitle),
    ) {
        OnboardingInputBlock(
            icon = Icons.Rounded.PersonOutline,
            title = stringResource(R.string.age_band),
            description = stringResource(R.string.used_to_tailor_cycle_predictions_and_insights),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
            ) {
                AgeBand.entries.forEach { ageBand ->
                    FilterChip(
                        selected = uiState.ageBand == ageBand,
                        onClick = { onAgeBandSelected(ageBand) },
                        modifier = Modifier.testTag("onboarding_age_${ageBand.name}"),
                        label = { Text(ageBand.displayLabel().resolve()) },
                        colors = onboardingChipColors(),
                    )
                }
            }
        }

        SoftDivider()

        OnboardingInputBlock(
            icon = Icons.Rounded.Sync,
            title = stringResource(R.string.typical_cycle_length),
        ) {
            StepperField(
                valueLabel = uiState.cycleLengthLabel,
                onDecrement = { onCycleLengthIncrement(-1) },
                onIncrement = { onCycleLengthIncrement(1) },
                decrementEnabled = uiState.cycleLength > 20,
                incrementEnabled = uiState.cycleLength < 45,
                contentDescriptionBase = stringResource(R.string.cycle_length),
                valueTestTag = OnboardingTags.CYCLE_LENGTH_VALUE,
                modifier = Modifier
                    .testTag(OnboardingTags.CYCLE_LENGTH_SLIDER)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = uiState.cycleLength.toFloat(),
                            range = 20f..45f,
                            steps = 24,
                        )
                        setProgress { target ->
                            val targetLength = target.roundToInt().coerceIn(20, 45)
                            onCycleLengthIncrement(targetLength - uiState.cycleLength)
                            true
                        }
                    },
            )
            uiState.cycleLengthError?.let { error ->
                InlineError(text = error)
            }
        }

        SoftDivider()

        OnboardingInputBlock(
            icon = Icons.Rounded.FavoriteBorder,
            title = stringResource(R.string.typical_period_length),
        ) {
            StepperField(
                valueLabel = uiState.periodLengthLabel,
                onDecrement = { onPeriodLengthChanged(-1) },
                onIncrement = { onPeriodLengthChanged(1) },
                decrementEnabled = uiState.periodLength > 2,
                incrementEnabled = uiState.periodLength < 10,
                contentDescriptionBase = stringResource(R.string.period_length),
            )
            uiState.periodLengthError?.let { error ->
                InlineError(text = error)
            }
        }

        SoftDivider()

        OnboardingInputBlock(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.last_period_start),
            description = stringResource(R.string.this_helps_predict_your_next_cycle_accurately),
        ) {
            DateSelectionField(
                label = uiState.lastPeriodStartLabel,
                onClick = onOpenDatePicker,
            )
            uiState.lastPeriodStartError?.let { error ->
                InlineError(text = error)
            }
        }
    }
}

@Composable
internal fun OnboardingPreferencesStep(
    uiState: OnboardingUiState,
    onGoalSelected: (TrackingGoal) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onPrivacyChanged: (Boolean) -> Unit,
) {
    OnboardingPanel(
        title = stringResource(R.string.preferences),
        subtitle = stringResource(R.string.preferences_subtitle),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        OnboardingInputBlock(
            icon = Icons.Rounded.FavoriteBorder,
            title = stringResource(R.string.wellness_goal),
            description = stringResource(R.string.choose_your_primary_goal),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
            ) {
                TrackingGoal.entries.forEach { goal ->
                    FilterChip(
                        selected = uiState.goal == goal,
                        onClick = { onGoalSelected(goal) },
                        modifier = Modifier.testTag("onboarding_goal_${goal.name}"),
                        label = { Text(goal.displayLabel().resolve()) },
                        colors = onboardingChipColors(),
                    )
                }
            }
            GoalSummary(text = uiState.goal.description())
        }

        SoftDivider()

        ToggleSettingRow(
            icon = Icons.Rounded.NotificationsNone,
            title = stringResource(R.string.smart_reminders),
            description = stringResource(R.string.smart_reminders_description),
            checked = uiState.remindersEnabled,
            onCheckedChange = onRemindersChanged,
        )
        ToggleSettingRow(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.hide_sensitive_details_in_notifications),
            description = stringResource(R.string.notifications_wont_show_words_like_period_or_fertile),
            checked = uiState.privacyModeEnabled,
            onCheckedChange = onPrivacyChanged,
        )
    }
}

@Composable
internal fun OnboardingProgressHeader(
    currentStep: Int,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.step_x_of_2, currentStep),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(if (currentStep == 1) R.string.baseline else R.string.preferences),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        )
    }
}

