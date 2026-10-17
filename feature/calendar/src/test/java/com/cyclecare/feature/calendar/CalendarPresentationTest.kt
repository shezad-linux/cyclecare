package com.cyclecare.feature.calendar

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.domain.CycleRecordValidator
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Test

class CalendarPresentationTest {
    @Test
    fun monthLabel_usesNaturalFormatting() {
        assertThat(formatMonthLabel(YearMonth.of(2026, 3))).isEqualTo("March 2026")
    }

    @Test
    fun selectedDaySummary_marksOvulationAndKeepsFriendlyAction() {
        val prediction = PredictionSnapshot(
            generatedAt = LocalDate.of(2026, 3, 16),
            cycleDay = 6,
            nextPeriodDate = LocalDate.of(2026, 4, 8),
            ovulationDate = LocalDate.of(2026, 3, 23),
            fertileWindow = DateRange(
                start = LocalDate.of(2026, 3, 19),
                endInclusive = LocalDate.of(2026, 3, 24),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 23,
        )

        val summary = buildSelectedDaySummary(
            selectedDate = LocalDate.of(2026, 3, 23),
            selectedCycle = null,
            prediction = prediction,
        )

        assertThat(summary.statusLabel).isEqualTo("Ovulation estimate")
        assertThat(summary.primaryActionLabel).isEqualTo("Log period on this date")
    }

    @Test
    fun selectedDaySummary_futurePredictionHidesPeriodEntryAction() {
        val prediction = PredictionSnapshot(
            generatedAt = LocalDate.of(2026, 3, 16),
            cycleDay = 6,
            nextPeriodDate = LocalDate.of(2026, 4, 8),
            ovulationDate = LocalDate.of(2026, 3, 23),
            fertileWindow = DateRange(
                start = LocalDate.of(2026, 3, 19),
                endInclusive = LocalDate.of(2026, 3, 24),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 23,
        )

        val summary = buildSelectedDaySummary(
            selectedDate = LocalDate.of(2026, 3, 23),
            selectedCycle = null,
            prediction = prediction,
            today = LocalDate.of(2026, 3, 16),
        )

        assertThat(summary.statusLabel).isEqualTo("Ovulation estimate")
        assertThat(summary.primaryActionLabel).isNull()
        assertThat(summary.detail).contains("future estimate")
    }

    @Test
    fun historyItems_areSortedNewestFirstAndNaturalRange() {
        val items = buildHistoryItems(
            listOf(
                CycleRecord(
                    id = "older",
                    startDate = LocalDate.of(2026, 2, 11),
                    endDate = LocalDate.of(2026, 2, 15),
                    cycleLength = 28,
                    source = CycleSource.User,
                    notes = null,
                ),
                CycleRecord(
                    id = "newer",
                    startDate = LocalDate.of(2026, 3, 11),
                    endDate = LocalDate.of(2026, 3, 15),
                    cycleLength = 28,
                    source = CycleSource.User,
                    notes = "Cycle felt regular.",
                ),
            ),
        )

        assertThat(items.first().id).isEqualTo("newer")
        assertThat(items.first().dateRangeLabel).isEqualTo("Mar 11 - Mar 15")
        assertThat(items.first().cycleLengthLabel).isEqualTo("28 day cycle")
    }

    @Test
    fun buildCalendarDays_alwaysReturnsFortyTwoUniqueDates() {
        val days = buildCalendarDays(
            CalendarUiState(
                month = YearMonth.of(2026, 3),
                monthLabel = formatMonthLabel(YearMonth.of(2026, 3)),
                selectedDate = LocalDate.of(2026, 3, 16),
            ),
            today = LocalDate.of(2026, 3, 16),
        )

        assertThat(days).hasSize(42)
        assertThat(days.map { it.date }.distinct()).hasSize(42)
    }

    @Test
    fun orderedWeekdays_areMondayFirst() {
        assertThat(orderedWeekdays()).containsExactly(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        ).inOrder()
    }

    @Test
    fun calendarGridStart_monthStartingMonday_alignsWithoutLeadingDays() {
        val start = calendarGridStart(YearMonth.of(2021, 3))

        assertThat(start).isEqualTo(LocalDate.of(2021, 3, 1))
    }

    @Test
    fun calendarGridStart_monthStartingSunday_alignsWithPreviousMonday() {
        val start = calendarGridStart(YearMonth.of(2021, 8))

        assertThat(start).isEqualTo(LocalDate.of(2021, 7, 26))
    }

    @Test
    fun buildCalendarDays_handlesLeapYearFebruary() {
        val month = YearMonth.of(2028, 2)
        val days = buildCalendarDays(
            CalendarUiState(
                month = month,
                monthLabel = formatMonthLabel(month),
                selectedDate = LocalDate.of(2028, 2, 29),
            ),
            today = LocalDate.of(2028, 2, 29),
        )

        val leapDay = days.first { it.date == LocalDate.of(2028, 2, 29) }
        val marchFirst = days.first { it.date == LocalDate.of(2028, 3, 1) }

        assertThat(leapDay.isCurrentMonth).isTrue()
        assertThat(leapDay.isToday).isTrue()
        assertThat(leapDay.isSelected).isTrue()
        assertThat(days.count { YearMonth.from(it.date) == month }).isEqualTo(29)
        assertThat(marchFirst.isCurrentMonth).isFalse()
    }

    @Test
    fun buildCalendarDays_handlesNonLeapYearFebruary() {
        val month = YearMonth.of(2027, 2)
        val days = buildCalendarDays(
            CalendarUiState(
                month = month,
                monthLabel = formatMonthLabel(month),
                selectedDate = LocalDate.of(2027, 2, 28),
            ),
            today = LocalDate.of(2027, 2, 28),
        )

        assertThat(days.count { YearMonth.from(it.date) == month }).isEqualTo(28)
        assertThat(days.any { it.date == LocalDate.of(2027, 3, 1) && !it.isCurrentMonth }).isTrue()
    }

    @Test
    fun buildCalendarDays_crossingYearBoundary_marksAdjacentMonthsCorrectly() {
        val month = YearMonth.of(2027, 1)
        val days = buildCalendarDays(
            CalendarUiState(
                month = month,
                monthLabel = formatMonthLabel(month),
                selectedDate = LocalDate.of(2027, 1, 1),
            ),
            today = LocalDate.of(2027, 1, 1),
        )

        val decemberDay = days.first { it.date == LocalDate.of(2026, 12, 31) }
        val januaryDay = days.first { it.date == LocalDate.of(2027, 1, 1) }

        assertThat(decemberDay.isCurrentMonth).isFalse()
        assertThat(januaryDay.isCurrentMonth).isTrue()
        assertThat(januaryDay.isToday).isTrue()
        assertThat(januaryDay.isSelected).isTrue()
    }

    @Test
    fun calendarDayFillType_prioritizesPeriodThenOvulationThenFertile() {
        assertThat(
            calendarDayFillType(
                CalendarDay(
                    date = LocalDate.of(2026, 3, 23),
                    isCurrentMonth = true,
                    isPeriodDay = true,
                    isFertileDay = true,
                    isOvulationDay = true,
                    isToday = false,
                    isSelected = false,
                ),
            ),
        ).isEqualTo(CalendarLegendType.Period)

        assertThat(
            calendarDayFillType(
                CalendarDay(
                    date = LocalDate.of(2026, 3, 23),
                    isCurrentMonth = true,
                    isPeriodDay = false,
                    isFertileDay = true,
                    isOvulationDay = true,
                    isToday = false,
                    isSelected = false,
                ),
            ),
        ).isEqualTo(CalendarLegendType.Ovulation)

        assertThat(
            calendarDayFillType(
                CalendarDay(
                    date = LocalDate.of(2026, 3, 19),
                    isCurrentMonth = true,
                    isPeriodDay = false,
                    isFertileDay = true,
                    isOvulationDay = false,
                    isToday = false,
                    isSelected = false,
                ),
            ),
        ).isEqualTo(CalendarLegendType.Fertile)
    }

    @Test
    fun anchoredDay_restoresThirtyFirstWhenReturningToLongMonth() {
        val march = YearMonth.of(2026, 3)
        val february = YearMonth.of(2026, 2)

        val febDate = february.withAnchoredDay(31)
        val marchDate = march.withAnchoredDay(31)

        assertThat(febDate).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(marchDate).isEqualTo(LocalDate.of(2026, 3, 31))
    }

    @Test
    fun validateCycleEditorState_rejectsMalformedAndFutureInputs() {
        val validated = validateCycleEditorState(
            editor = CycleEditorState(
                isVisible = true,
                startDateText = LocalDate.now().plusDays(1).toString(),
                endDateText = "invalid",
                cycleLengthText = "",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        assertThat(validated.startDateError).isEqualTo("Start date cannot be in the future.")
        assertThat(validated.endDateError).isEqualTo("Use YYYY-MM-DD for the end date.")
        assertThat(validated.cycleLengthError).isEqualTo("Cycle length is required.")
        assertThat(validated.isSaveEnabled).isFalse()
    }
}
