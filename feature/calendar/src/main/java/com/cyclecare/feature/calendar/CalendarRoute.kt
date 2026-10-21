@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import com.cyclecare.core.domain.prediction.derivedIntervalsByRecordId
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class CalendarBaseState(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val cycles: List<CycleRecord>,
    val prediction: CyclePrediction?,
)

/**
 * Month labels are formatted as a localized month name plus year. There is
 * no [DateTimeFormatter.ofLocalizedDate] equivalent for a bare YearMonth
 * (formatting a YearMonth with a LONG localized formatter throws
 * UnsupportedTemporalTypeException for the missing day-of-month), so the
 * pattern stays in code with the resolved locale driving the month names.
 */
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
    private val messageFlow = MutableStateFlow<UiText?>(null)

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
            selectedDate = base.selectedDate,
            selectedDaySummary = buildSelectedDaySummary(
                selectedDate = base.selectedDate,
                selectedCycle = selectedCycle,
                prediction = base.prediction,
                derivedCycleLength = selectedCycle?.let {
                    derivedIntervalsByRecordId(base.cycles)[it.id]
                },
            ),
            selectedCycle = selectedCycle,
            cycles = base.cycles,
            prediction = base.prediction,
            historyItems = historyItems,
            historyEmptyMessage = if (historyItems.isEmpty()) {
                uiText(R.string.history_empty_message)
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
        // The cycle length the editor starts from is the derived
        // interval, not the stored column.
        //
        // Spec `02` §9.4.5: application code never reads `cycleLength`.
        // The stored value is written at logging time from whatever the
        // user last reported, so it can disagree with the real gap. The
        // most recent cycle has no derived interval yet, so the editor
        // starts from the default the user can change.
        val derivedInterval = uiState.value.selectedCycle?.let { selected ->
            derivedIntervalsByRecordId(uiState.value.cycles)[selected.id]
        }
        editorFlow.update {
            CycleEditorState(
                isVisible = true,
                startDateText = editorStartDate.toString(),
                endDateText = editorEndDate.toString(),
                cycleLengthText = derivedInterval?.toString() ?: DEFAULT_CYCLE_LENGTH_DAYS,
            ).also(savedStateHandle::persistCycleEditorState)
        }
    }

    fun openEditSelectedCycle() {
        val cycle = uiState.value.selectedCycle ?: return
        val derivedInterval = derivedIntervalsByRecordId(uiState.value.cycles)[cycle.id]
        editorFlow.update {
            cycle.toEditorState(derivedInterval).also(savedStateHandle::persistCycleEditorState)
        }
    }

    fun openHistoryEditor(id: String) {
        val cycle = uiState.value.cycles.firstOrNull { it.id == id } ?: return
        monthFlow.update { YearMonth.from(cycle.startDate) }
        selectedDateFlow.update { cycle.startDate }
        anchorDayOfMonthFlow.update { cycle.startDate.dayOfMonth }
        persistCalendarPosition(YearMonth.from(cycle.startDate), cycle.startDate, cycle.startDate.dayOfMonth)
        val derivedInterval = derivedIntervalsByRecordId(uiState.value.cycles)[cycle.id]
        editorFlow.update {
            cycle.toEditorState(derivedInterval).also(savedStateHandle::persistCycleEditorState)
        }
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
            messageFlow.update { uiText(R.string.cycle_history_updated_locally) }
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
            messageFlow.update { uiText(R.string.cycle_entry_removed_locally) }
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

/**
 * The editor state for editing a cycle.
 *
 * The cycle length comes from the derived interval. The stored
 * `cycleLength` column is not read: spec `02` §9.4.5 requires
 * application code to take intervals from the shared extractor, and the
 * stored value can disagree with the real gap. A cycle with no derived
 * interval yet starts from the default, which the user can change.
 */
private fun CycleRecord.toEditorState(derivedIntervalDays: Int?): CycleEditorState =
    CycleEditorState(
        isVisible = true,
        editingId = id,
        startDateText = startDate.toString(),
        endDateText = endDate.toString(),
        cycleLengthText = derivedIntervalDays?.toString() ?: DEFAULT_CYCLE_LENGTH_DAYS,
        notes = notes.orEmpty(),
    )

/** The cycle length the editor starts from when nothing is derived yet. */
private const val DEFAULT_CYCLE_LENGTH_DAYS = "28"

internal fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

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

