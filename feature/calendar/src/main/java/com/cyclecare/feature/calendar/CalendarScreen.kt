@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cyclecare.core.designsystem.token.extendedColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val monthLabel: String = formatMonthLabel(YearMonth.now()),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDaySummary: SelectedDaySummary = buildSelectedDaySummary(LocalDate.now(), null, null),
    val selectedCycle: CycleRecord? = null,
    val cycles: List<CycleRecord> = emptyList(),
    val prediction: PredictionSnapshot? = null,
    val historyItems: List<CycleHistoryItem> = emptyList(),
    val historyEmptyMessage: String? = null,
    val legendItems: List<CalendarLegendItem> = defaultCalendarLegendItems(),
    val editor: CycleEditorState = CycleEditorState(),
    val showDeleteConfirmation: Boolean = false,
    val message: String? = null,
)

data class SelectedDaySummary(
    val dateLabel: String,
    val statusLabel: String,
    val detail: String,
    val primaryActionLabel: String?,
    val type: CalendarLegendType,
)

data class CycleHistoryItem(
    val id: String,
    val startDate: LocalDate,
    val dateRangeLabel: String,
    val cycleLengthLabel: String,
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
    val label: String,
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
    val startDateError: String? = null,
    val endDateError: String? = null,
    val cycleLengthError: String? = null,
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

private data class CalendarBaseState(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val cycles: List<CycleRecord>,
    val prediction: PredictionSnapshot?,
)

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val selectedDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
private val dayRangeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val crossYearRangeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val calendarFirstDayOfWeek = DayOfWeek.MONDAY

private object CalendarStateKeys {
    const val Month = "month"
    const val SelectedDate = "selected_date"
    const val AnchorDayOfMonth = "anchor_day_of_month"
    const val EditorVisible = "editor_visible"
    const val EditorEditingId = "editor_editing_id"
    const val EditorStartDateText = "editor_start_date_text"
    const val EditorEndDateText = "editor_end_date_text"
    const val EditorCycleLengthText = "editor_cycle_length_text"
    const val EditorNotes = "editor_notes"
}

internal object CalendarTags {
    const val MONTH_LABEL = "calendar_month_label"
    const val PREVIOUS_MONTH = "calendar_previous_month"
    const val NEXT_MONTH = "calendar_next_month"
    const val SELECTED_DAY_PANEL = "calendar_selected_day_panel"
    const val SCROLL = "calendar_scroll"

    fun day(date: LocalDate): String = "calendar_day_$date"
    fun history(id: String): String = "calendar_history_$id"
}

internal fun formatMonthLabel(month: YearMonth): String = month.format(monthFormatter)

internal fun formatCalendarDate(date: LocalDate): String = date.format(selectedDateFormatter)

/**
 * A null end date means the period is still in progress, which is shown
 * as an open range rather than being hidden or guessed at.
 */
internal fun formatCycleRangeLabel(startDate: LocalDate, endDate: LocalDate?): String {
    if (endDate == null) {
        return "${startDate.format(dayRangeFormatter)} - ongoing"
    }
    return when {
        startDate.year != endDate.year -> {
            "${startDate.format(crossYearRangeFormatter)} - ${endDate.format(crossYearRangeFormatter)}"
        }
        else -> {
            "${startDate.format(dayRangeFormatter)} - ${endDate.format(dayRangeFormatter)}"
        }
    }
}

internal fun buildHistoryItems(cycles: List<CycleRecord>): List<CycleHistoryItem> =
    cycles
        .filter { it.source != CycleSource.Predicted }
        .sortedByDescending { it.startDate }
        .take(6)
        .map { cycle ->
            CycleHistoryItem(
                id = cycle.id,
                startDate = cycle.startDate,
                dateRangeLabel = formatCycleRangeLabel(cycle.startDate, cycle.endDate),
                cycleLengthLabel = "${cycle.cycleLength} day cycle",
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
        editor.startDateText.isBlank() -> "Start date is required."
        startDate == null -> "Use YYYY-MM-DD for the start date."
        startDate.isAfter(today) -> "Start date cannot be in the future."
        else -> null
    }
    val endDateError = when {
        editor.endDateText.isBlank() -> "End date is required."
        endDate == null -> "Use YYYY-MM-DD for the end date."
        endDate.isAfter(today) -> "End date cannot be in the future."
        else -> null
    }
    val cycleLengthError = when {
        editor.cycleLengthText.isBlank() -> "Cycle length is required."
        cycleLength == null -> "Cycle length must be a whole number."
        cycleLength !in 20..45 -> "Cycle length must stay between 20 and 45 days."
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
    CalendarLegendItem(CalendarLegendType.Period, "Period"),
    CalendarLegendItem(CalendarLegendType.Fertile, "Fertile window"),
    CalendarLegendItem(CalendarLegendType.Ovulation, "Ovulation"),
    CalendarLegendItem(CalendarLegendType.TodaySelected, "Today / selected"),
)

internal fun calendarDayStateDescription(day: CalendarDay): String = buildList {
    if (day.isSelected) add("selected")
    if (day.isToday) add("today")
    if (day.isPeriodDay) add("period")
    if (day.isFertileDay) add("fertile")
    if (day.isOvulationDay) add("ovulation")
    add(if (day.isCurrentMonth) "current month" else "outside month")
}.joinToString(", ")

internal fun calendarDayFillType(day: CalendarDay): CalendarLegendType? = when {
    day.isPeriodDay -> CalendarLegendType.Period
    day.isOvulationDay -> CalendarLegendType.Ovulation
    day.isFertileDay -> CalendarLegendType.Fertile
    else -> null
}

internal fun orderedWeekdays(
    firstDayOfWeek: DayOfWeek = calendarFirstDayOfWeek,
): List<DayOfWeek> {
    val weekdays = DayOfWeek.entries
    val startIndex = weekdays.indexOf(firstDayOfWeek)
    return weekdays.drop(startIndex) + weekdays.take(startIndex)
}

internal fun calendarGridStart(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek = calendarFirstDayOfWeek,
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
): SelectedDaySummary {
    val isOvulationDay = prediction?.ovulationDate == selectedDate
    val isFertileDay = prediction?.fertileWindow?.let { selectedDate >= it.start && selectedDate <= it.endInclusive } == true
    val isToday = selectedDate == today
    val isFuture = selectedDate.isAfter(today)
    val daysUntilPeriod = prediction?.daysUntilNextPeriod

    return when {
        selectedCycle != null -> {
            val periodLength = java.time.temporal.ChronoUnit.DAYS.between(selectedCycle.startDate, selectedCycle.endDate).toInt() + 1
            val cycleNotes = selectedCycle.notes
            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate),
                statusLabel = "Period day",
                detail = buildString {
                    append("${periodLength}-day period • ${selectedCycle.cycleLength}d cycle")
                    if (selectedDate == selectedCycle.startDate) {
                        append(" • First day of period")
                    } else if (selectedDate == selectedCycle.endDate) {
                        append(" • Last day of period")
                    }
                    if (!cycleNotes.isNullOrBlank()) {
                        append(". ${cycleNotes.take(80)}")
                    }
                },
                primaryActionLabel = "Update entry",
                type = CalendarLegendType.Period,
            )
        }

        isOvulationDay -> SelectedDaySummary(
            dateLabel = formatCalendarDate(selectedDate),
            statusLabel = "Ovulation estimate",
            detail = buildString {
                append("Peak fertility • ")
                if (isFuture) {
                    append("This is a future estimate based on your local history.")
                } else if (isToday) {
                    append("Tracking symptoms today may help with future insights.")
                } else {
                    append("Consider logging how you feel on this date.")
                }
            },
            primaryActionLabel = if (isFuture) null else "Log period on this date",
            type = CalendarLegendType.Ovulation,
        )

        isFertileDay -> {
            val daysInFertileWindow = prediction?.fertileWindow?.let {
                java.time.temporal.ChronoUnit.DAYS.between(it.start, selectedDate).toInt() + 1
            } ?: 1
            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate),
                statusLabel = "Fertile window",
                detail = buildString {
                    append("Day $daysInFertileWindow of fertile window • ")
                    if (isFuture) {
                        append("This is a future estimate based on your local history.")
                    } else if (isToday) {
                        append("Track symptoms for better insights.")
                    } else {
                        append("Log mood and energy for future reference.")
                    }
                },
                primaryActionLabel = if (isFuture) null else "Log period on this date",
                type = CalendarLegendType.Fertile,
            )
        }

        selectedDate.isAfter(today) -> SelectedDaySummary(
            dateLabel = formatCalendarDate(selectedDate),
            statusLabel = "Future date",
            detail = buildString {
                append("This date is in the future. ")
                daysUntilPeriod?.let { days ->
                    if (days > 0) {
                        val daysUntilSelected = java.time.temporal.ChronoUnit.DAYS.between(today, selectedDate).toInt()
                        if (daysUntilSelected <= days + 7 && daysUntilSelected > 0) {
                            append("Your period is expected around ${prediction?.nextPeriodDate?.format(dayRangeFormatter) ?: "soon"}.")
                        }
                    }
                }
            },
            primaryActionLabel = null,
            type = CalendarLegendType.Neutral,
        )

        else -> {
            val insightText = when {
                daysUntilPeriod != null && daysUntilPeriod > 0 -> {
                    val daysUntilSelected = java.time.temporal.ChronoUnit.DAYS.between(today, selectedDate).toInt()
                    val daysFromPeriod = daysUntilPeriod - daysUntilSelected
                    when {
                        daysFromPeriod in -14..-1 -> "Late cycle • Consider checking in"
                        daysFromPeriod == 0 -> "Period expected today"
                        daysFromPeriod in 1..7 -> "Period expected ${if (daysFromPeriod == 1) "tomorrow" else "in $daysFromPeriod days"}"
                        daysFromPeriod < 0 -> "Cycle day ${daysUntilPeriod + 1}"
                        else -> null
                    }
                }
                isToday -> "Track how you're feeling today"
                else -> null
            }

            SelectedDaySummary(
                dateLabel = formatCalendarDate(selectedDate),
                statusLabel = if (isToday) "Today" else "No cycle event",
                detail = insightText ?: "No logged entry or prediction for this date yet. Track your symptoms or period when ready.",
                primaryActionLabel = "Log period on this date",
                type = CalendarLegendType.Neutral,
            )
        }
    }
}

/** Month, selected date and anchor day restored together as one unit. */
private data class RestoredCalendarPosition(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val anchorDayOfMonth: Int,
)

class CalendarViewModel(
    private val cycleRepository: CycleRepository,
    predictionRepository: PredictionRepository,
    private val reminderScheduler: ReminderScheduler,
    private val validator: CycleRecordValidator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * Restores the calendar position as one consistent unit.
     *
     * These three values used to be read independently. When only some
     * of the saved keys came back, the result was internally
     * contradictory: the displayed month restored to March while the
     * selected day silently fell back to today's day-of-month, so the
     * day panel showed a date nobody had selected. Deriving all three
     * from a single decision removes that class of inconsistency.
     */
    private val restoredPosition: RestoredCalendarPosition = run {
        val savedDate = savedStateHandle.get<String>(CalendarStateKeys.SelectedDate)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        if (savedDate != null) {
            RestoredCalendarPosition(
                month = YearMonth.from(savedDate),
                selectedDate = savedDate,
                anchorDayOfMonth = savedDate.dayOfMonth,
            )
        } else {
            val month = savedStateHandle.get<String>(CalendarStateKeys.Month)
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: YearMonth.now()
            val day = savedStateHandle.get<Int>(CalendarStateKeys.AnchorDayOfMonth)
                ?: LocalDate.now().dayOfMonth
            // Clamp: a 31st anchor restored into a 30-day month would
            // otherwise throw.
            val date = month.atDay(minOf(day, month.lengthOfMonth()))
            RestoredCalendarPosition(month, date, date.dayOfMonth)
        }
    }

    private val restoredSelectedDate = restoredPosition.selectedDate
    private val restoredMonth = restoredPosition.month

    private val monthFlow = MutableStateFlow(restoredMonth)
    private val selectedDateFlow = MutableStateFlow(restoredSelectedDate)
    private val anchorDayOfMonthFlow = MutableStateFlow(restoredPosition.anchorDayOfMonth)
    private val editorFlow = MutableStateFlow(savedStateHandle.toCycleEditorState())
    private val showDeleteConfirmationFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)

    private val baseState = combine(
        monthFlow,
        selectedDateFlow,
        cycleRepository.observeCycles(),
        predictionRepository.observePrediction(),
    ) { month, selectedDate, cycles, prediction ->
        CalendarBaseState(
            month = month,
            selectedDate = selectedDate,
            cycles = cycles,
            prediction = prediction,
        )
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        baseState,
        editorFlow,
        showDeleteConfirmationFlow,
        messageFlow,
    ) { base, editor, showDeleteConfirmation, message ->
        val selectedCycle = base.cycles.find { base.selectedDate >= it.startDate && base.selectedDate <= it.endDate }
        val historyItems = buildHistoryItems(base.cycles)
        val validatedEditor = validateCycleEditorState(
            editor = editor,
            cycles = base.cycles,
            validator = validator,
        )
        CalendarUiState(
            month = base.month,
            monthLabel = formatMonthLabel(base.month),
            selectedDate = base.selectedDate,
            selectedDaySummary = buildSelectedDaySummary(
                selectedDate = base.selectedDate,
                selectedCycle = selectedCycle,
                prediction = base.prediction,
            ),
            selectedCycle = selectedCycle,
            cycles = base.cycles,
            prediction = base.prediction,
            historyItems = historyItems,
            historyEmptyMessage = if (historyItems.isEmpty()) {
                "Your logged cycles will appear here once you save them. History makes predictions and reminder timing more reliable."
            } else {
                null
            },
            legendItems = defaultCalendarLegendItems(),
            editor = validatedEditor,
            showDeleteConfirmation = showDeleteConfirmation,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun previousMonth() {
        val targetMonth = monthFlow.value.minusMonths(1)
        monthFlow.update { targetMonth }
        val targetDate = targetMonth.withAnchoredDay(anchorDayOfMonthFlow.value)
        selectedDateFlow.update { targetDate }
        persistCalendarPosition(targetMonth, targetDate, anchorDayOfMonthFlow.value)
        messageFlow.update { null }
    }

    fun nextMonth() {
        val targetMonth = monthFlow.value.plusMonths(1)
        monthFlow.update { targetMonth }
        val targetDate = targetMonth.withAnchoredDay(anchorDayOfMonthFlow.value)
        selectedDateFlow.update { targetDate }
        persistCalendarPosition(targetMonth, targetDate, anchorDayOfMonthFlow.value)
        messageFlow.update { null }
    }

    fun selectDate(date: LocalDate) {
        selectedDateFlow.update { date }
        monthFlow.update { YearMonth.from(date) }
        anchorDayOfMonthFlow.update { date.dayOfMonth }
        persistCalendarPosition(YearMonth.from(date), date, date.dayOfMonth)
        messageFlow.update { null }
    }

    fun applyExternalSelectedDate(date: LocalDate) {
        selectDate(date)
    }

    fun openCreateEditor() {
        val selectedDate = uiState.value.selectedDate
        val today = LocalDate.now()
        val editorStartDate = selectedDate.coerceAtMost(today)
        val editorEndDate = editorStartDate.plusDays(4).coerceAtMost(today)
        editorFlow.update {
            CycleEditorState(
                isVisible = true,
                startDateText = editorStartDate.toString(),
                endDateText = editorEndDate.toString(),
                cycleLengthText = uiState.value.selectedCycle?.cycleLength?.toString() ?: "28",
            ).also(savedStateHandle::persistCycleEditorState)
        }
    }

    fun openEditSelectedCycle() {
        val cycle = uiState.value.selectedCycle ?: return
        editorFlow.update { cycle.toEditorState().also(savedStateHandle::persistCycleEditorState) }
    }

    fun openHistoryEditor(id: String) {
        val cycle = uiState.value.cycles.firstOrNull { it.id == id } ?: return
        monthFlow.update { YearMonth.from(cycle.startDate) }
        selectedDateFlow.update { cycle.startDate }
        anchorDayOfMonthFlow.update { cycle.startDate.dayOfMonth }
        persistCalendarPosition(YearMonth.from(cycle.startDate), cycle.startDate, cycle.startDate.dayOfMonth)
        editorFlow.update { cycle.toEditorState().also(savedStateHandle::persistCycleEditorState) }
        messageFlow.update { null }
    }

    fun updateEditorStartDate(value: String) = updateEditorState { it.copy(startDateText = value, error = null) }
    fun updateEditorEndDate(value: String) = updateEditorState { it.copy(endDateText = value, error = null) }
    fun updateEditorCycleLength(value: String) = updateEditorState { it.copy(cycleLengthText = value, error = null) }
    fun updateEditorNotes(value: String) = updateEditorState { it.copy(notes = value, error = null) }
    fun dismissEditor() {
        editorFlow.update { CycleEditorState() }
        savedStateHandle.clearCycleEditorState()
    }

    fun showDeleteConfirmation() {
        showDeleteConfirmationFlow.update { true }
    }

    fun dismissDeleteConfirmation() {
        showDeleteConfirmationFlow.update { false }
    }

    fun saveCycle() {
        val editor = validateCycleEditorState(
            editor = editorFlow.value,
            cycles = uiState.value.cycles,
            validator = validator,
        )
        if (!editor.isSaveEnabled) {
            editorFlow.update { editor.also(savedStateHandle::persistCycleEditorState) }
            return
        }

        val candidate = CycleRecord(
            id = editor.editingId ?: UUID.randomUUID().toString(),
            startDate = editor.parsedStartDate!!,
            endDate = editor.parsedEndDate!!,
            cycleLength = editor.parsedCycleLength!!,
            source = CycleSource.User,
            notes = editor.notes.ifBlank { null },
        )
        val validation = validator.validate(candidate, uiState.value.cycles)
        if (!validation.isValid) {
            editorFlow.update {
                it.copy(isSaving = false, error = validation.message).also(savedStateHandle::persistCycleEditorState)
            }
            return
        }

        editorFlow.update {
            it.copy(isSaving = true).also(savedStateHandle::persistCycleEditorState)
        }
        viewModelScope.launch {
            cycleRepository.upsertCycle(candidate)
            reminderScheduler.refreshSchedules()
            monthFlow.update { YearMonth.from(candidate.startDate) }
            selectedDateFlow.update { candidate.startDate }
            anchorDayOfMonthFlow.update { candidate.startDate.dayOfMonth }
            persistCalendarPosition(YearMonth.from(candidate.startDate), candidate.startDate, candidate.startDate.dayOfMonth)
            messageFlow.update { "Cycle history updated locally." }
            editorFlow.update { CycleEditorState() }
            savedStateHandle.clearCycleEditorState()
        }
    }

    fun deleteSelectedCycle() {
        val selectedCycle = uiState.value.selectedCycle ?: return
        viewModelScope.launch {
            cycleRepository.deleteCycle(selectedCycle.id)
            reminderScheduler.refreshSchedules()
            showDeleteConfirmationFlow.update { false }
            messageFlow.update { "Cycle entry removed locally." }
        }
    }

    private fun updateEditorState(transform: (CycleEditorState) -> CycleEditorState) {
        editorFlow.update { current ->
            transform(current).also(savedStateHandle::persistCycleEditorState)
        }
    }

    private fun persistCalendarPosition(month: YearMonth, selectedDate: LocalDate, anchorDayOfMonth: Int) {
        savedStateHandle[CalendarStateKeys.Month] = month.toString()
        savedStateHandle[CalendarStateKeys.SelectedDate] = selectedDate.toString()
        savedStateHandle[CalendarStateKeys.AnchorDayOfMonth] = anchorDayOfMonth
    }
}

class CalendarViewModelFactory(
    private val cycleRepository: CycleRepository,
    private val predictionRepository: PredictionRepository,
    private val reminderScheduler: ReminderScheduler,
    private val validator: CycleRecordValidator = CycleRecordValidator(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        CalendarViewModel(
            cycleRepository = cycleRepository,
            predictionRepository = predictionRepository,
            reminderScheduler = reminderScheduler,
            validator = validator,
            savedStateHandle = extras.createSavedStateHandle(),
        ) as T
}

@Composable
fun CalendarRoute(
    factory: CalendarViewModelFactory,
    externalSelectedDate: LocalDate? = null,
    onExternalSelectedDateConsumed: () -> Unit = {},
) {
    val viewModel: CalendarViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(externalSelectedDate) {
        externalSelectedDate ?: return@LaunchedEffect
        viewModel.applyExternalSelectedDate(externalSelectedDate)
        onExternalSelectedDateConsumed()
    }
    CalendarScreen(
        uiState = uiState,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectDate = viewModel::selectDate,
        onOpenCreate = viewModel::openCreateEditor,
        onOpenEdit = viewModel::openEditSelectedCycle,
        onOpenHistoryEdit = viewModel::openHistoryEditor,
        onStartDateChanged = viewModel::updateEditorStartDate,
        onEndDateChanged = viewModel::updateEditorEndDate,
        onCycleLengthChanged = viewModel::updateEditorCycleLength,
        onNotesChanged = viewModel::updateEditorNotes,
        onSaveCycle = viewModel::saveCycle,
        onDismissEditor = viewModel::dismissEditor,
        onShowDeleteConfirmation = viewModel::showDeleteConfirmation,
        onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmation,
        onDeleteCycle = viewModel::deleteSelectedCycle,
    )
}

@Composable
internal fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenHistoryEdit: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onCycleLengthChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveCycle: () -> Unit,
    onDismissEditor: () -> Unit,
    onShowDeleteConfirmation: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onDeleteCycle: () -> Unit,
) {
    val days = remember(uiState.month, uiState.selectedDate, uiState.cycles, uiState.prediction) {
        buildCalendarDays(uiState)
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            // Tagged so tests can reach the selected-day panel, which
            // sits below the grid and is off-screen at rest.
            .testTag(CalendarTags.SCROLL),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientHeaderCard(
            title = "Calendar",
            subtitle = "Review logged periods, fertile windows, and upcoming estimates.",
            supportingContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.legendItems.forEach { item ->
                        LegendChip(item = item)
                    }
                }
            },
        )

        CycleCarePanel(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier.testTag(CalendarTags.PREVIOUS_MONTH),
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Cycle calendar",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            uiState.monthLabel,
                            modifier = Modifier.testTag(CalendarTags.MONTH_LABEL),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onNextMonth,
                        modifier = Modifier.testTag(CalendarTags.NEXT_MONTH),
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val daySpacing = 8.dp
                    val dayCellSize = (maxWidth - (daySpacing * 6)) / 7

                    // A lazy grid inside this scrolling column has to be
                    // given a fixed height. Deriving that height from the
                    // row count actually present, rather than always
                    // assuming six rows, is what makes every day
                    // reachable: days in rows the grid had no room for
                    // were laid out with zero bounds, so tapping them
                    // silently did nothing.
                    val gridRowCount = ((days.size + 6) / 7).coerceIn(1, 6)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(daySpacing),
                        ) {
                            orderedWeekdays().forEach { dayOfWeek ->
                                Text(
                                    dayOfWeek.name.take(3),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((dayCellSize * gridRowCount) + (daySpacing * (gridRowCount - 1))),
                            userScrollEnabled = false,
                            verticalArrangement = Arrangement.spacedBy(daySpacing),
                            horizontalArrangement = Arrangement.spacedBy(daySpacing),
                        ) {
                            items(days, key = { it.date.toEpochDay() }) { day ->
                                CalendarDayCell(
                                    day = day,
                                    modifier = Modifier.aspectRatio(1f),
                                    onClick = { onSelectDate(day.date) },
                                )
                            }
                        }
                    }
                }
            }
        }

        CycleCarePanel(
            modifier = Modifier.testTag(CalendarTags.SELECTED_DAY_PANEL),
            containerColor = selectedDayPanelColor(uiState.selectedDaySummary.type),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Selected day",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(uiState.selectedDaySummary.dateLabel, style = MaterialTheme.typography.titleLarge)
                    StatusPill(summary = uiState.selectedDaySummary)
                }
                Text(uiState.selectedDaySummary.detail, style = MaterialTheme.typography.bodyLarge)
                if (!uiState.selectedCycle?.notes.isNullOrBlank()) {
                    Text(
                        uiState.selectedCycle?.notes.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (uiState.selectedCycle == null && uiState.selectedDaySummary.primaryActionLabel != null) {
                    Button(onClick = onOpenCreate, modifier = Modifier.fillMaxWidth()) {
                        Text(uiState.selectedDaySummary.primaryActionLabel)
                    }
                } else {
                    if (uiState.selectedCycle != null) {
                        Button(onClick = onOpenEdit, modifier = Modifier.fillMaxWidth()) {
                            Text(uiState.selectedDaySummary.primaryActionLabel ?: "Update entry")
                        }
                        TextButton(onClick = onShowDeleteConfirmation, modifier = Modifier.fillMaxWidth()) {
                            Text("Delete cycle entry")
                        }
                    }
                }

                uiState.message?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent cycle history", style = MaterialTheme.typography.titleLarge)
            Text(
                "Edit previous entries without leaving the calendar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.historyItems.isEmpty()) {
                CycleCarePanel(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
                ) {
                    Text(
                        text = uiState.historyEmptyMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                uiState.historyItems.forEach { item ->
                    HistoryCard(
                        item = item,
                        onEdit = { onOpenHistoryEdit(item.id) },
                    )
                }
            }
        }
    }

    if (uiState.editor.isVisible) {
        ModalBottomSheet(onDismissRequest = onDismissEditor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (uiState.editor.editingId == null) "Log period" else "Edit period",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Use simple date entry for now, then adjust cycle length and notes before saving locally.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.editor.startDateText,
                    onValueChange = onStartDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Start date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = uiState.editor.startDateError != null,
                    supportingText = {
                        uiState.editor.startDateError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.editor.endDateText,
                    onValueChange = onEndDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("End date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = uiState.editor.endDateError != null,
                    supportingText = {
                        uiState.editor.endDateError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.editor.cycleLengthText,
                    onValueChange = onCycleLengthChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cycle length") },
                    singleLine = true,
                    isError = uiState.editor.cycleLengthError != null,
                    supportingText = {
                        uiState.editor.cycleLengthError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.editor.notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    minLines = 3,
                )
                uiState.editor.error?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onSaveCycle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.editor.isSaveEnabled,
                ) {
                    Text(if (uiState.editor.isSaving) "Saving..." else "Save locally")
                }
                TextButton(onClick = onDismissEditor, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            confirmButton = {
                Button(onClick = onDeleteCycle) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirmation) { Text("Cancel") }
            },
            title = { Text("Delete cycle entry?") },
            text = { Text("This removes the selected period entry and recalculates local predictions.") },
        )
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val extended = extendedColors
    val fillType = calendarDayFillType(day)
    val hasCustomFill = fillType != null

    val containerColor = when (fillType) {
        CalendarLegendType.Period -> extended.calendarPeriodFill
        CalendarLegendType.Ovulation -> extended.calendarOvulationFill
        CalendarLegendType.Fertile -> extended.calendarFertileFill
        else -> if (day.isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (day.isCurrentMonth) 0.74f else 0.4f)
        }
    }
    val borderColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    Surface(
        modifier = modifier
            .testTag(CalendarTags.day(day.date))
            .semantics { stateDescription = calendarDayStateDescription(day) }
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (day.isSelected) 2.5.dp else if (borderColor == Color.Transparent) 0.dp else 2.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    color = when {
                        hasCustomFill -> if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> if (day.isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (day.isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                if (day.isOvulationDay) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Ovulation",
                        modifier = Modifier.size(10.dp),
                        tint = if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (day.isToday && !day.isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun LegendChip(item: CalendarLegendItem) {
    val accent = legendColor(item.type)
    Surface(
        color = accent.copy(alpha = 0.28f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.75.dp, accent.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent.copy(alpha = 0.9f), CircleShape),
            )
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(summary: SelectedDaySummary) {
    val accent = legendColor(summary.type)
    Surface(
        color = accent.copy(alpha = 0.25f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = summary.type.icon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(summary.statusLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun selectedDayPanelColor(type: CalendarLegendType): Color {
    val extended = extendedColors
    return when (type) {
        CalendarLegendType.Period -> extended.calendarPeriodSelected
        CalendarLegendType.Fertile -> extended.calendarFertileSelected
        CalendarLegendType.Ovulation -> extended.calendarOvulationSelected
        CalendarLegendType.TodaySelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        CalendarLegendType.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
}

@Composable
private fun HistoryCard(
    item: CycleHistoryItem,
    onEdit: () -> Unit,
) {
    CycleCarePanel(
        modifier = Modifier.testTag(CalendarTags.history(item.id)),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.dateRangeLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                item.cycleLengthLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.notesPreview?.let { notes ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Edit entry")
            }
        }
    }
}

internal fun buildCalendarDays(
    uiState: CalendarUiState,
    today: LocalDate = LocalDate.now(),
    firstDayOfWeek: DayOfWeek = calendarFirstDayOfWeek,
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

@Composable
private fun legendColor(type: CalendarLegendType): Color {
    val extended = extendedColors
    return when (type) {
        CalendarLegendType.Period -> extended.calendarPeriodSwatch
        CalendarLegendType.Fertile -> extended.calendarFertileSwatch
        CalendarLegendType.Ovulation -> extended.calendarOvulationSwatch
        CalendarLegendType.TodaySelected -> MaterialTheme.colorScheme.primary
        CalendarLegendType.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun CalendarLegendType.icon(): ImageVector = when (this) {
    CalendarLegendType.Period -> Icons.Rounded.WaterDrop
    CalendarLegendType.Fertile -> Icons.Rounded.EventRepeat
    CalendarLegendType.Ovulation -> Icons.Rounded.Star
    CalendarLegendType.TodaySelected -> Icons.Rounded.EventRepeat
    CalendarLegendType.Neutral -> Icons.Rounded.Star
}

internal fun YearMonth.withAnchoredDay(anchorDay: Int): LocalDate =
    atDay(anchorDay.coerceAtMost(lengthOfMonth()))

private fun CycleRecord.toEditorState(): CycleEditorState =
    CycleEditorState(
        isVisible = true,
        editingId = id,
        startDateText = startDate.toString(),
        endDateText = endDate.toString(),
        cycleLengthText = cycleLength.toString(),
        notes = notes.orEmpty(),
    )

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

internal fun SavedStateHandle.toCycleEditorState(): CycleEditorState {
    val isVisible = get<Boolean>(CalendarStateKeys.EditorVisible) ?: false
    return if (!isVisible) {
        CycleEditorState()
    } else {
        CycleEditorState(
            isVisible = true,
            editingId = get<String>(CalendarStateKeys.EditorEditingId),
            startDateText = get<String>(CalendarStateKeys.EditorStartDateText).orEmpty(),
            endDateText = get<String>(CalendarStateKeys.EditorEndDateText).orEmpty(),
            cycleLengthText = get<String>(CalendarStateKeys.EditorCycleLengthText) ?: "28",
            notes = get<String>(CalendarStateKeys.EditorNotes).orEmpty(),
        )
    }
}

internal fun SavedStateHandle.persistCycleEditorState(state: CycleEditorState) {
    this[CalendarStateKeys.EditorVisible] = state.isVisible
    this[CalendarStateKeys.EditorEditingId] = state.editingId
    this[CalendarStateKeys.EditorStartDateText] = state.startDateText
    this[CalendarStateKeys.EditorEndDateText] = state.endDateText
    this[CalendarStateKeys.EditorCycleLengthText] = state.cycleLengthText
    this[CalendarStateKeys.EditorNotes] = state.notes
}

internal fun SavedStateHandle.clearCycleEditorState() {
    this[CalendarStateKeys.EditorVisible] = false
    remove<String>(CalendarStateKeys.EditorEditingId)
    remove<String>(CalendarStateKeys.EditorStartDateText)
    remove<String>(CalendarStateKeys.EditorEndDateText)
    remove<String>(CalendarStateKeys.EditorCycleLengthText)
    remove<String>(CalendarStateKeys.EditorNotes)
}

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
                monthLabel = formatMonthLabel(previewMonth),
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
