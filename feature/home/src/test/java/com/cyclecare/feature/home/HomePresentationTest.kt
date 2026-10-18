package com.cyclecare.feature.home

import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class HomePresentationTest {
    @Test
    fun fertileWindow_usesNaturalDashboardDateFormatting() {
        val range = DateRange(
            start = LocalDate.of(2026, 3, 21),
            endInclusive = LocalDate.of(2026, 3, 26),
        )

        assertThat(formatFertileWindow(range)).isEqualTo("Mar 21 - Mar 26")
    }

    @Test
    fun cycleProgressLabel_keepsDayContextAndConfidence() {
        assertThat(
            formatCycleProgressLabel(
                cycleDay = 6,
                cycleLength = 28,
                confidenceLabel = "Baseline estimate",
            ),
        ).isEqualTo("Day 6 / 28 • Baseline estimate")
    }

    @Test
    fun heroCopy_usesPeriodStartedCopyDuringCurrentCycle() {
        val state = HomeUiState(
            hasCurrentCycle = true,
            daysUntilNextPeriod = 21,
            nextPeriodDateText = "Apr 6",
        )

        assertThat(homeHeroTitle(state)).isEqualTo("Period started")
        assertThat(homeHeroSubtitle(state)).isEqualTo("Log how today feels")
    }

    @Test
    fun heroCopy_keepsPredictionCopyOutsideCurrentCycle() {
        val state = HomeUiState(
            hasCurrentCycle = false,
            daysUntilNextPeriod = 3,
            nextPeriodDateText = "Mar 19",
            // Declared explicitly because supportsRange defaults to
            // false: a caller that omits it gets the honest "no range"
            // copy rather than a bare date presented as expected.
            supportsRange = true,
        )

        assertThat(homeHeroTitle(state)).isEqualTo("Next period in 3 days")
        assertThat(homeHeroSubtitle(state)).isEqualTo("Expected Mar 19")
    }

    @Test
    fun cyclePhaseLabel_usesPeriodDaysDuringCurrentCycle() {
        val today = LocalDate.of(2026, 3, 16)
        val prediction = PredictionSnapshot(
            generatedAt = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateRange(today.plusDays(7), today.plusDays(12)),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 22,
        )
        val currentCycle = CycleRecord(
            id = "active-cycle",
            startDate = today.minusDays(5),
            endDate = today,
            cycleLength = 28,
            source = CycleSource.User,
        )

        assertThat(
            buildHomeCyclePhaseLabel(
                currentCycle = currentCycle,
                prediction = prediction,
                cycleLength = 28,
            ),
        ).isEqualTo("Period days")
    }

    @Test
    fun progressStateDescription_isUserFacing() {
        assertThat(progressStateDescription(cycleDay = 6, cycleLength = 28)).isEqualTo("Day 6 of 28")
        assertThat(progressStateDescription(cycleDay = null, cycleLength = 28)).isEqualTo("Cycle progress unavailable")
    }

    @Test
    fun defaultEditorState_usesSavedProfileBaseline() {
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = LocalDate.of(2026, 3, 11),
            trackingGoal = TrackingGoal.PeriodPrep,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )

        val editor = defaultEditorState(profile, LocalDate.of(2026, 3, 16))

        assertThat(editor.isVisible).isTrue()
        assertThat(editor.isExpanded).isFalse()
        assertThat(editor.startDateText).isEqualTo("2026-03-16")
        assertThat(editor.endDateText).isEqualTo("2026-03-20")
        assertThat(editor.cycleLengthText).isEqualTo("28")
    }

    @Test
    fun validateHomeEditorState_rejectsFutureStartDate() {
        val validated = validateHomeEditorState(
            editor = HomeEditorState(
                isVisible = true,
                isExpanded = true,
                startDateText = LocalDate.now().plusDays(1).toString(),
                endDateText = LocalDate.now().toString(),
                cycleLengthText = "28",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        assertThat(validated.startDateError).isEqualTo("Start date cannot be in the future.")
        assertThat(validated.isSaveEnabled).isFalse()
    }

    @Test
    fun validateHomeEditorState_rejectsBlankCycleLength() {
        val validated = validateHomeEditorState(
            editor = HomeEditorState(
                isVisible = true,
                isExpanded = true,
                startDateText = "2026-03-10",
                endDateText = "2026-03-14",
                cycleLengthText = "",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        assertThat(validated.cycleLengthError).isEqualTo("Cycle length is required.")
        assertThat(validated.isSaveEnabled).isFalse()
    }

    @Test
    fun nextPeriodRange_rendersBothEndsAndTheHalfWidth() {
        val text = formatNextPeriodRange(
            range = DateRange(
                start = LocalDate.of(2026, 4, 7),
                endInclusive = LocalDate.of(2026, 4, 11),
            ),
            supportsRange = true,
            halfWidthDays = 2,
        )

        assertThat(text).isNotNull()
        assertThat(text).contains("Apr 7")
        assertThat(text).contains("Apr 11")
        assertThat(text).contains("2 days")
    }

    @Test
    fun nextPeriodRange_isNullWhenTheEngineSupportsNoRange() {
        // Distinguishing "no range" from "empty range" is the whole
        // point: a caller that conflates them prints a bare date and the
        // overstatement returns.
        val text = formatNextPeriodRange(range = null, supportsRange = false, halfWidthDays = null)

        assertThat(text).isNotNull()
        assertThat(text).contains("Not enough history")
    }

    @Test
    fun heroSubtitle_doesNotPresentAPointEstimateAsExpectedWithoutARange() {
        val state = HomeUiState(
            nextPeriodDateText = "Apr 9",
            supportsRange = false,
            hasCurrentCycle = false,
        )

        assertThat(homeHeroSubtitle(state)).doesNotContain("Expected Apr 9 ")
        assertThat(homeHeroSubtitle(state)).contains("not enough history")
    }

    @Test
    fun heroSubtitle_surfacesOverdueDays() {
        val state = HomeUiState(
            nextPeriodDateText = "Apr 9",
            supportsRange = true,
            overdueDays = 3,
            hasCurrentCycle = false,
        )

        assertThat(homeHeroSubtitle(state)).contains("3 days past")
    }

    @Test
    fun overdueFormatting_isSingularAtOneDay() {
        assertThat(formatOverdue(1)).isEqualTo("1 day past the expected date")
        assertThat(formatOverdue(0)).isEmpty()
        assertThat(formatOverdue(5)).contains("5 days past")
    }
}
