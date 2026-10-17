package com.cyclecare.feature.logging

import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class LoggingPresentationTest {
    private val today = LocalDate.of(2026, 3, 16)

    @Test
    fun `build logging ui state keeps blank entry compact`() {
        val editor = buildEditorState(entry = null, hydrationKey = "new:$today")

        val uiState = buildLoggingUiState(
            today = today,
            profile = null,
            prediction = null,
            todayEntry = null,
            editor = editor,
            saveMessage = null,
            isSaving = false,
        )

        assertThat(uiState.hasExistingEntry).isFalse()
        assertThat(uiState.saveCtaLabel).isEqualTo("Save today's entry")
        assertThat(uiState.isDetailsExpanded).isFalse()
        assertThat(uiState.dateLabel).isEqualTo("Monday, Mar 16")
    }

    @Test
    fun `active period uses period check in copy`() {
        val uiState = LoggingUiState(
            heroMode = PeriodHeroMode.Active,
            cyclePhaseLabel = "Early cycle",
        )

        assertThat(loggingHeroEyebrow(uiState)).isEqualTo("Period check-in")
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo("Take a moment to log today's symptoms.")
    }

    @Test
    fun `predicted period keeps daily check in copy`() {
        val uiState = LoggingUiState(
            heroMode = PeriodHeroMode.Predicted,
            cyclePhaseLabel = "Late cycle",
        )

        assertThat(loggingHeroEyebrow(uiState)).isEqualTo("Daily check-in")
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo("Period expected soon. Logging symptoms helps track regularity.")
    }

    @Test
    fun `current logged cycle drives active period check in copy`() {
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = today.minusDays(24),
            trackingGoal = TrackingGoal.SymptomAwareness,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )
        val prediction = PredictionSnapshot(
            generatedAt = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateRange(today.plusDays(7), today.plusDays(12)),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 22,
        )

        val uiState = buildLoggingUiState(
            today = today,
            profile = profile,
            prediction = prediction,
            todayEntry = null,
            editor = buildEditorState(entry = null, hydrationKey = "new:$today"),
            saveMessage = null,
            isSaving = false,
            cycles = listOf(
                CycleRecord(
                    id = "active-cycle",
                    startDate = today.minusDays(5),
                    endDate = today,
                    cycleLength = 28,
                    source = CycleSource.User,
                ),
            ),
        )

        assertThat(uiState.heroMode).isEqualTo(PeriodHeroMode.Active)
        assertThat(uiState.cyclePhaseLabel).isEqualTo("Period days")
        assertThat(loggingHeroEyebrow(uiState)).isEqualTo("Period check-in")
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo("Take a moment to log today's symptoms.")
    }

    @Test
    fun `existing entry reuses update wording and expands details`() {
        val entry = SymptomLog(
            id = "today-entry",
            date = today,
            mood = Mood.Happy,
            painLevel = PainLevel.Light,
            energyLevel = EnergyLevel.High,
            dischargeType = DischargeType.EggWhite,
            medication = "Ibuprofen",
            notes = "Symptoms eased by evening.",
        )
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = today.minusDays(5),
            trackingGoal = TrackingGoal.SymptomAwareness,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )
        val prediction = PredictionSnapshot(
            generatedAt = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateRange(today.plusDays(7), today.plusDays(12)),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 22,
        )

        val uiState = buildLoggingUiState(
            today = today,
            profile = profile,
            prediction = prediction,
            todayEntry = entry,
            editor = buildEditorState(entry = entry, hydrationKey = entry.id),
            saveMessage = "Saved to your local timeline.",
            isSaving = false,
        )

        assertThat(uiState.hasExistingEntry).isTrue()
        assertThat(uiState.saveCtaLabel).isEqualTo("Update today's entry")
        assertThat(uiState.entryStatusLabel).isEqualTo("Editing today's saved check-in")
        assertThat(uiState.cycleProgressLabel).isEqualTo("Day 6 / 28")
        assertThat(uiState.isDetailsExpanded).isTrue()
        assertThat(uiState.dischargeType?.displayLabel()).isEqualTo("Egg white")
    }

    @Test
    fun `build cycle progress label degrades safely without complete context`() {
        assertThat(buildCycleProgressLabel(profile = null, prediction = null)).isNull()
        assertThat(
            buildCycleProgressLabel(
                profile = UserProfile(
                    ageBand = AgeBand.Twenties,
                    averageCycleLength = 28,
                    periodLength = 5,
                    lastPeriodStart = today.minusDays(5),
                    trackingGoal = TrackingGoal.PeriodPrep,
                    remindersEnabled = true,
                    privacyModeEnabled = true,
                ),
                prediction = null,
            ),
        ).isNull()
    }
}
