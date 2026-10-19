@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import java.time.LocalDate
import java.time.YearMonth

@ScreenDebugPreviews
@Composable
private fun CalendarScreenPreview() {
    val sampleCycle = CycleRecord(
        id = "cycle-1",
        startDate = LocalDate.of(2026, 3, 11),
        endDate = LocalDate.of(2026, 3, 15),
        cycleLength = 28,
        source = CycleSource.User,
        notes = "Cycle felt regular and symptoms stayed light.",
    )
    val samplePrediction = PredictionSnapshot(
        generatedAt = LocalDate.of(2026, 3, 16),
        cycleDay = 6,
        nextPeriodDate = LocalDate.of(2026, 4, 8),
        ovulationDate = LocalDate.of(2026, 3, 23),
        fertileWindow = com.cyclecare.core.model.DateRange(
            start = LocalDate.of(2026, 3, 19),
            endInclusive = LocalDate.of(2026, 3, 24),
        ),
        confidenceLabel = "Baseline estimate",
        daysUntilNextPeriod = 23,
    )
    val previewMonth = YearMonth.of(2026, 3)

    CycleCareTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                month = previewMonth,
                selectedDate = sampleCycle.startDate,
                selectedDaySummary = buildSelectedDaySummary(sampleCycle.startDate, sampleCycle, samplePrediction),
                selectedCycle = sampleCycle,
                cycles = listOf(sampleCycle),
                prediction = samplePrediction,
                historyItems = buildHistoryItems(listOf(sampleCycle)),
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onSelectDate = {},
            onOpenCreate = {},
            onOpenEdit = {},
            onOpenHistoryEdit = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onCycleLengthChanged = {},
            onNotesChanged = {},
            onSaveCycle = {},
            onDismissEditor = {},
            onShowDeleteConfirmation = {},
            onDismissDeleteConfirmation = {},
            onDeleteCycle = {},
        )
    }
}
