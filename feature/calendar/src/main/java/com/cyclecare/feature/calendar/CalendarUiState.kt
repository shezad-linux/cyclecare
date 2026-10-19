@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import androidx.compose.runtime.getValue
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDaySummary: SelectedDaySummary = buildSelectedDaySummary(LocalDate.now(), null, null),
    val selectedCycle: CycleRecord? = null,
    val cycles: List<CycleRecord> = emptyList(),
    val prediction: PredictionSnapshot? = null,
    val historyItems: List<CycleHistoryItem> = emptyList(),
    val historyEmptyMessage: UiText? = null,
    val legendItems: List<CalendarLegendItem> = defaultCalendarLegendItems(),
    val editor: CycleEditorState = CycleEditorState(),
    val showDeleteConfirmation: Boolean = false,
    val message: UiText? = null,
)

data class SelectedDaySummary(
    val dateLabel: String,
    val statusLabel: UiText,
    val detail: UiText,
    val primaryActionLabel: UiText?,
    val type: CalendarLegendType,
)

data class CycleHistoryItem(
    val id: String,
    val startDate: LocalDate,
    val dateRangeLabel: UiText,
    val cycleLengthLabel: UiText,
    val notesPreview: String? = null,
)

enum class CalendarLegendType {
    Period,
    Fertile,
    Ovulation,
    TodaySelected,
    Neutral,
}

data class CalendarLegendItem(
    val type: CalendarLegendType,
    val label: UiText,
)

data class CycleEditorState(
    val isVisible: Boolean = false,
    val editingId: String? = null,
    val startDateText: String = "",
    val endDateText: String = "",
    val cycleLengthText: String = "28",
    val notes: String = "",
    val parsedStartDate: LocalDate? = null,
    val parsedEndDate: LocalDate? = null,
    val parsedCycleLength: Int? = null,
    val startDateError: UiText? = null,
    val endDateError: UiText? = null,
    val cycleLengthError: UiText? = null,
    val isSaveEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isPeriodDay: Boolean,
    val isFertileDay: Boolean,
    val isOvulationDay: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
)

private fun monthFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", locale)

private fun selectedDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

private fun rangeDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

/** The editor's machine-readable date hint; must survive translation verbatim. */
internal object CalendarTags {
    const val MONTH_LABEL = "calendar_month_label"
    const val PREVIOUS_MONTH = "calendar_previous_month"
    const val NEXT_MONTH = "calendar_next_month"
    const val SELECTED_DAY_PANEL = "calendar_selected_day_panel"
    const val SCROLL = "calendar_scroll"

    fun day(date: LocalDate): String = "calendar_day_$date"
    fun history(id: String): String = "calendar_history_$id"
}

internal fun formatMonthLabel(
    month: YearMonth,
    locale: Locale = Locale.getDefault(),
): String = month.format(monthFormatter(locale))

internal fun formatCalendarDate(
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): String = date.format(selectedDateFormatter(locale))

/**
 * A null end date means the period is still in progress, which is shown
 * as an open range rather than being hidden or guessed at.
 */
internal fun formatCycleRangeLabel(
    startDate: LocalDate,
    endDate: LocalDate?,
    locale: Locale = Locale.getDefault(),
): UiText {
    val formattedStart = startDate.format(rangeDateFormatter(locale))
    return if (endDate == null) {
        uiText(R.string.cycle_range_ongoing, formattedStart)
    } else {
        uiText(R.string.cycle_range, formattedStart, endDate.format(rangeDateFormatter(locale)))
    }
}

internal fun buildHistoryItems(
    cycles: List<CycleRecord>,
    locale: Locale = Locale.getDefault(),
): List<CycleHistoryItem> =
    cycles
        .filter { it.source != CycleSource.Predicted }
        .sortedByDescending { it.startDate }
        .take(6)
        .map { cycle ->
            CycleHistoryItem(
                id = cycle.id,
                startDate = cycle.startDate,
                dateRangeLabel = formatCycleRangeLabel(cycle.startDate, cycle.endDate, locale),
                cycleLengthLabel = pluralUiText(
                    R.plurals.history_cycle_length,
                    cycle.cycleLength,
                    cycle.cycleLength,
                ),
                notesPreview = cycle.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    if (notes.length > 90) "${notes.take(87)}..." else notes
                },
            )
        }

internal fun validateCycleEditorState(
    editor: CycleEditorState,
    cycles: List<CycleRecord>,
    validator: CycleRecordValidator,
    today: LocalDate = LocalDate.now(),
): CycleEditorState {
    val startDate = editor.startDateText.toLocalDateOrNull()
    val endDate = editor.endDateText.toLocalDateOrNull()
    val cycleLength = editor.cycleLengthText.toIntOrNull()

    val startDateError = when {
        editor.startDateText.isBlank() -> uiText(R.string.editor_start_date_required)
        startDate == null -> uiText(R.string.editor_start_date_format, IsoDateFormatHint)
        startDate.isAfter(today) -> uiText(R.string.editor_start_date_future)
        else -> null
    }
    val endDateError = when {
        editor.endDateText.isBlank() -> uiText(R.string.editor_end_date_required)
        endDate == null -> uiText(R.string.editor_end_date_format, IsoDateFormatHint)
        endDate.isAfter(today) -> uiText(R.string.editor_end_date_future)
        else -> null
    }
    val cycleLengthError = when {
        editor.cycleLengthText.isBlank() -> uiText(R.string.editor_cycle_length_required)
        cycleLength == null -> uiText(R.string.editor_cycle_length_whole_number)
        cycleLength !in 20..45 -> uiText(R.string.editor_cycle_length_range, 20, 45)
        else -> null
    }

    val summaryError = if (startDateError == null && endDateError == null && cycleLengthError == null) {
        validator.validate(
            candidate = CycleRecord(
                id = editor.editingId ?: "validation",
                startDate = startDate!!,
                endDate = endDate!!,
                cycleLength = cycleLength!!,
                source = CycleSource.User,
                notes = editor.notes.ifBlank { null },
            ),
            existingRecords = cycles,
        ).message
    } else {
        null
    }

    return editor.copy(
        parsedStartDate = startDate,
        parsedEndDate = endDate,
        parsedCycleLength = cycleLength,
        startDateError = startDateError,
        endDateError = endDateError,
        cycleLengthError = cycleLengthError,
        error = summaryError,
        isSaveEnabled = !editor.isSaving &&
            startDateError == null &&
            endDateError == null &&
            cycleLengthError == null &&
            summaryError == null,
    )
}

internal fun defaultCalendarLegendItems(): List<CalendarLegendItem> = listOf(
    CalendarLegendItem(CalendarLegendType.Period, uiText(R.string.legend_period)),
    CalendarLegendItem(CalendarLegendType.Fertile, uiText(R.string.legend_fertile_window)),
    CalendarLegendItem(CalendarLegendType.Ovulation, uiText(R.string.ovulation)),
    CalendarLegendItem(CalendarLegendType.TodaySelected, uiText(R.string.legend_today_selected)),
)

/**
 * Structured TalkBack state for a day cell, in display order. The
 * localized sentence is assembled in the composable from
 * [CalendarDayState] rather than produced here, because the tokens must
 * resolve against the current locale's resources.
 */
internal enum class CalendarDayState {
    Selected,
    Today,
    Period,
    Fertile,
    Ovulation,
    CurrentMonth,
    OutsideMonth,
}

internal fun calendarDayStateDescription(day: CalendarDay): List<CalendarDayState> = buildList {
    if (day.isSelected) add(CalendarDayState.Selected)
    if (day.isToday) add(CalendarDayState.Today)
    if (day.isPeriodDay) add(CalendarDayState.Period)
    if (day.isFertileDay) add(CalendarDayState.Fertile)
    if (day.isOvulationDay) add(CalendarDayState.Ovulation)
    add(if (day.isCurrentMonth) CalendarDayState.CurrentMonth else CalendarDayState.OutsideMonth)
}

internal fun calendarDayFillType(day: CalendarDay): CalendarLegendType? = when {
    day.isPeriodDay -> CalendarLegendType.Period
    day.isOvulationDay -> CalendarLegendType.Ovulation
    day.isFertileDay -> CalendarLegendType.Fertile
    else -> null
}

internal fun orderedWeekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> {
    val weekdays = DayOfWeek.entries
    val startIndex = weekdays.indexOf(firstDayOfWeek)
    return weekdays.drop(startIndex) + weekdays.take(startIndex)
}

internal fun calendarGridStart(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
): LocalDate {
    val firstDay = month.atDay(1)
    val offset = (7 + (firstDay.dayOfWeek.value - firstDayOfWeek.value)) % 7
    return firstDay.minusDays(offset.toLong())
}

internal fun buildSelectedDaySummary(
    selectedDate: LocalDate,
    selectedCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault(),
): SelectedDaySummary {
    val isOvulationDay = prediction?.ovulationDate == selectedDate
    val isFertileDay = prediction?.fertileWindow?.let { selectedDate >= it.start && selectedDate <= it.endInclusive } == true
    val isToday = selectedDate == today
    val isFuture = selectedDate.isAfter(today)
    val daysUntilPeriod = prediction?.daysUntilNextPeriod

    return when {
        selectedCycle != null -> {
            val periodLength = java.time.temporal.ChronoUnit.DAYS.between(selectedCycle.startDate, selectedCycle.endDate).toInt() + 1
            val cycleNotes = selectedCycle.notes?.takeIf { it.isNotBlank() }?.take(80)
            val isFirstDay = selectedDate == selectedCycle.startDate
            val isLastDay = selectedDate == selectedCycle.endDate
            val detail = when {
                isFirstDay && cycleNotes != null ->
                    uiText(R.string.summary_period_detail_first_day_notes, periodLength, selectedCycle.cycleLength, cycleNotes)
                isFirstDay ->
                    uiText(R.string.summary_period_detail_first_day, periodLength, selectedCycle.cycleLength)
                isLastDay && cycleNotes != null ->
                    uiText(R.string.summary_period_detail_last_day_notes, periodLength, selectedCycle.cycleLength, cycleNotes)
                isLastDay ->
                    uiText(R.string.summary_period_detail_last_day, periodLength, selectedCycle.cycleLength)
                cycleNotes != null ->
                    uiText(R.string.summary_period_detail_notes, periodLength, selectedCycle.cycleLength, cycleNotes)
                else ->
                    uiText(R.string.summary_period_detail, periodLength, selectedCycle.cycleLength)
            }
            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate, locale),
                statusLabel = uiText(R.string.summary_status_period_day),
                detail = detail,
                primaryActionLabel = uiText(R.string.update_entry),
                type = CalendarLegendType.Period,
            )
        }

        isOvulationDay -> SelectedDaySummary(
            dateLabel = formatCalendarDate(selectedDate, locale),
            statusLabel = uiText(R.string.summary_status_ovulation_estimate),
            detail = when {
                isFuture -> uiText(R.string.summary_ovulation_detail_future)
                isToday -> uiText(R.string.summary_ovulation_detail_today)
                else -> uiText(R.string.summary_ovulation_detail_past)
            },
            primaryActionLabel = if (isFuture) null else uiText(R.string.summary_action_log_period),
            type = CalendarLegendType.Ovulation,
        )

        isFertileDay -> {
            val daysInFertileWindow = prediction?.fertileWindow?.let {
                java.time.temporal.ChronoUnit.DAYS.between(it.start, selectedDate).toInt() + 1
            } ?: 1
            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate, locale),
                statusLabel = uiText(R.string.summary_status_fertile_window),
                detail = when {
                    isFuture -> uiText(R.string.summary_fertile_detail_future, daysInFertileWindow)
                    isToday -> uiText(R.string.summary_fertile_detail_today, daysInFertileWindow)
                    else -> uiText(R.string.summary_fertile_detail_past, daysInFertileWindow)
                },
                primaryActionLabel = if (isFuture) null else uiText(R.string.summary_action_log_period),
                type = CalendarLegendType.Fertile,
            )
        }

        selectedDate.isAfter(today) -> {
            val detail = if (daysUntilPeriod != null && daysUntilPeriod > 0) {
                val daysUntilSelected = java.time.temporal.ChronoUnit.DAYS.between(today, selectedDate).toInt()
                if (daysUntilSelected <= daysUntilPeriod + 7 && daysUntilSelected > 0) {
                    val expectedDate = prediction?.nextPeriodDate
                    if (expectedDate != null) {
                        uiText(R.string.summary_future_detail_expected, expectedDate.format(rangeDateFormatter(locale)))
                    } else {
                        uiText(R.string.summary_future_detail_expected_soon)
                    }
                } else {
                    uiText(R.string.summary_future_detail)
                }
            } else {
                uiText(R.string.summary_future_detail)
            }
            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate, locale),
                statusLabel = uiText(R.string.summary_status_future_date),
                detail = detail,
                primaryActionLabel = null,
                type = CalendarLegendType.Neutral,
            )
        }

        else -> {
            val insight = when {
                daysUntilPeriod != null && daysUntilPeriod > 0 -> {
                    val daysUntilSelected = java.time.temporal.ChronoUnit.DAYS.between(today, selectedDate).toInt()
                    val daysFromPeriod = daysUntilPeriod - daysUntilSelected
                    when {
                        daysFromPeriod in -14..-1 -> uiText(R.string.summary_insight_late_cycle)
                        daysFromPeriod == 0 -> uiText(R.string.summary_insight_period_today)
                        daysFromPeriod in 1..7 -> pluralUiText(
                            R.plurals.summary_insight_period_in_days,
                            daysFromPeriod,
                            daysFromPeriod,
                        )
                        daysFromPeriod < 0 -> uiText(R.string.summary_insight_cycle_day, daysUntilPeriod + 1)
                        else -> null
                    }
                }
                isToday -> uiText(R.string.summary_insight_track_today)
                else -> null
            }

            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate, locale),
                statusLabel = if (isToday) uiText(R.string.summary_status_today) else uiText(R.string.summary_status_no_event),
                detail = insight ?: uiText(R.string.summary_neutral_detail_empty),
                primaryActionLabel = uiText(R.string.summary_action_log_period),
                type = CalendarLegendType.Neutral,
            )
        }
    }
}

/** Month, selected date and anchor day restored together as one unit. */
internal fun buildCalendarDays(
    uiState: CalendarUiState,
    today: LocalDate = LocalDate.now(),
    firstDayOfWeek: DayOfWeek,
): List<CalendarDay> {
    val month = uiState.month
    val start = calendarGridStart(month, firstDayOfWeek)

    return (0 until 42).map { index ->
        val date = start.plusDays(index.toLong())
        val isPeriodDay = uiState.cycles.any { date >= it.startDate && date <= it.endDate }
        val isFertileDay = uiState.prediction?.fertileWindow?.let { date >= it.start && date <= it.endInclusive } == true
        val isOvulationDay = uiState.prediction?.ovulationDate == date
        CalendarDay(
            date = date,
            isCurrentMonth = YearMonth.from(date) == month,
            isPeriodDay = isPeriodDay,
            isFertileDay = isFertileDay,
            isOvulationDay = isOvulationDay,
            isToday = date == today,
            isSelected = date == uiState.selectedDate,
        )
    }
}

internal fun YearMonth.withAnchoredDay(anchorDay: Int): LocalDate =
    atDay(anchorDay.coerceAtMost(lengthOfMonth()))

