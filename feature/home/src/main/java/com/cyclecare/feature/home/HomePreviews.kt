@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.ScreenWidthPreviews
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.cyclecare.core.designsystem.CycleCareTheme

@ScreenWidthPreviews
@Composable
private fun HomeScreenPreview() {
    CycleCareTheme {
        HomeScreen(
            uiState = previewHomeState(),
            onOpenLog = {},
            onOpenCalendar = {},
            onOpenInsights = {},
            onOpenCycleStatus = {},
            onOpenFertilityDetail = {},
            reminderUnreadCount = 2,
            onOpenReminderCenter = {},
            onOpenEditCycle = {},
            onExpandEditor = {},
            onDismissEditor = {},
            onQuickStartPeriod = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onCycleLengthChanged = {},
            onNotesChanged = {},
            onSaveCycle = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
private fun HomeCycleStatusPreview() {
    CycleCareTheme {
        HomeCycleStatusScreen(
            uiState = previewHomeState(),
            onBack = {},
            onOpenEditCycle = {},
            onOpenCalendar = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
private fun HomeFertilityDetailPreview() {
    CycleCareTheme {
        HomeFertilityDetailScreen(
            uiState = previewHomeState(),
            onBack = {},
            onOpenCalendar = {},
            onOpenInsights = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
private fun EditCycleSheetPreview() {
    CycleCareTheme {
        EditCycleSheet(
            editor = HomeEditorState(
                isVisible = true,
                isExpanded = true,
                startDateText = "2026-03-11",
                endDateText = "2026-03-15",
                cycleLengthText = "28",
                notes = "Cycle felt regular and symptoms stayed light.",
            ),
            hasCurrentCycle = false,
            onDismiss = {},
            onExpand = {},
            onQuickStartPeriod = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onCycleLengthChanged = {},
            onNotesChanged = {},
            onSaveCycle = {},
        )
    }
}

internal fun previewHomeState() = HomeUiState(
    daysUntilNextPeriod = 23,
    nextPeriodDateText = UiText.Raw("Apr 9"),
    cycleDay = 6,
    cycleLengthDays = 28,
    averagePeriodLength = 5,
    variabilityText = UiText.Raw("26-29d range"),
    fertileWindowText = UiText.Raw("Mar 21 - Mar 26"),
    ovulationEstimateText = UiText.Raw("Mar 25"),
    confidenceLabelRes = null,
    hasCurrentCycle = false,
    cycleModeLabel = uiText(R.string.based_on_your_history),
    cyclePhaseLabel = uiText(R.string.early_cycle),
    cycleSourceSummary = uiText(R.string.predictions_combine_latest_cycle_with_baseline),
    latestCycleSummary = uiText(R.string.most_recent_logged_period, "Feb 12 - Feb 16"),
    reminderTeaser = HomeReminderTeaser(
        title = uiText(R.string.daily_check_in),
        detail = uiText(R.string.take_a_moment_to_log_mood_energy_medication),
        supporting = uiText(R.string.next_up_at_time, "Mar 18 • 8:00 PM"),
    ),
    insightTeaser = HomeInsightTeaser(
        title = uiText(R.string.recent_rhythm_snapshot),
        detail = uiText(R.string.recent_cycle_lengths_stay_close),
        supporting = uiText(R.string.based_on_your_four_logged_cycles),
        trendPoints = listOf(
            HomeTrendPoint(uiText(R.string.cycle_point_label, 1), 27),
            HomeTrendPoint(uiText(R.string.cycle_point_label, 2), 28),
            HomeTrendPoint(uiText(R.string.cycle_point_label, 3), 29),
            HomeTrendPoint(uiText(R.string.cycle_point_label, 4), 28),
        ),
    ),
    cyclesLoggedCount = 4,
    cyclesNeededForInsights = 3,
)
