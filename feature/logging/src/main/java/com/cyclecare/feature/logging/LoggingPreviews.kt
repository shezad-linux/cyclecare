@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenWidthPreviews
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate

@ScreenWidthPreviews
@Composable
private fun LoggingScreenPreview() {
    CycleCareTheme {
        LoggingScreen(
            uiState = previewLoggingUiState(),
            onMoodSelected = {},
            onPainSelected = {},
            onEnergySelected = {},
            onDischargeSelected = {},
            onMedicationChanged = {},
            onNotesChanged = {},
            onToggleDetailsExpanded = {},
            onSave = {},
            onApplyYesterdayPattern = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
private fun LoggingScreenEditingPreview() {
    CycleCareTheme {
        LoggingScreen(
            uiState = previewLoggingUiState(hasExistingEntry = true),
            onMoodSelected = {},
            onPainSelected = {},
            onEnergySelected = {},
            onDischargeSelected = {},
            onMedicationChanged = {},
            onNotesChanged = {},
            onToggleDetailsExpanded = {},
            onSave = {},
            onApplyYesterdayPattern = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
private fun LoggingScreenSavingPreview() {
    CycleCareTheme {
        LoggingScreen(
            uiState = previewLoggingUiState().copy(
                isSaving = true,
                isSaveEnabled = false,
            ),
            onMoodSelected = {},
            onPainSelected = {},
            onEnergySelected = {},
            onDischargeSelected = {},
            onMedicationChanged = {},
            onNotesChanged = {},
            onToggleDetailsExpanded = {},
            onSave = {},
            onApplyYesterdayPattern = {},
        )
    }
}

internal fun previewLoggingUiState(
    hasExistingEntry: Boolean = false,
): LoggingUiState = buildLoggingUiState(
    today = LocalDate.of(2026, 3, 17),
    profile = UserProfile(
        ageBand = AgeBand.Twenties,
        averageCycleLength = 30,
        periodLength = 5,
        lastPeriodStart = LocalDate.of(2026, 3, 12),
        trackingGoal = TrackingGoal.SymptomAwareness,
        remindersEnabled = true,
        privacyModeEnabled = true,
    ),
    prediction = PredictionSnapshot(
        generatedAt = LocalDate.of(2026, 3, 17),
        cycleDay = 6,
        nextPeriodDate = LocalDate.of(2026, 4, 10),
        ovulationDate = LocalDate.of(2026, 3, 27),
        fertileWindow = DateRange(
            start = LocalDate.of(2026, 3, 23),
            endInclusive = LocalDate.of(2026, 3, 28),
        ),
        confidenceLabel = "Baseline estimate",
        daysUntilNextPeriod = 24,
    ),
    todayEntry = if (hasExistingEntry) {
        SymptomLog(
            id = "preview-log",
            date = LocalDate.of(2026, 3, 17),
            mood = Mood.Low,
            painLevel = PainLevel.Moderate,
            energyLevel = EnergyLevel.Low,
            dischargeType = DischargeType.Sticky,
            medication = "Ibuprofen",
            notes = "Cramps eased by afternoon.",
        )
    } else {
        null
    },
    editor = LoggingEditorState(
        existingEntryId = if (hasExistingEntry) "preview-log" else null,
        mood = Mood.Low,
        painLevel = PainLevel.Moderate,
        energyLevel = EnergyLevel.Low,
        dischargeType = DischargeType.Sticky,
        medication = if (hasExistingEntry) "Ibuprofen" else "",
        notes = if (hasExistingEntry) "Cramps eased by afternoon." else "",
        isDetailsExpanded = hasExistingEntry,
    ),
    saveMessage = if (hasExistingEntry) uiText(R.string.saved_to_local_timeline) else null,
    isSaving = false,
)
