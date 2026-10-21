@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderSchedulePlanner
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import java.time.ZonedDateTime
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

private data class HomeBaseState(
    val profile: UserProfile? = null,
    val cycles: List<CycleRecord> = emptyList(),
    val prediction: PredictionSnapshot? = null,
    val insights: InsightSnapshot? = null,
    val reminderRules: List<ReminderRule> = emptyList(),
)

private val reminderDateTimeFormatter = DateTimeFormatter.ofPattern("MMM d • h:mm a", Locale.US)

private object HomeStateKeys {
    const val EditorVisible = "editor_visible"
    const val EditorExpanded = "editor_expanded"
    const val EditorEditingId = "editor_editing_id"
    const val EditorStartDateText = "editor_start_date_text"
    const val EditorEndDateText = "editor_end_date_text"
    const val EditorCycleLengthText = "editor_cycle_length_text"
    const val EditorNotes = "editor_notes"
}

private fun buildCycleModeLabel(
    currentCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
): UiText = when {
    currentCycle != null -> uiText(R.string.currently_logged)
    prediction != null -> uiText(R.string.based_on_your_history)
    else -> uiText(R.string.waiting_for_prediction)
}

private fun buildCycleSourceSummary(
    profile: UserProfile?,
    currentCycle: CycleRecord?,
    latestCycle: CycleRecord?,
): UiText = when {
    currentCycle != null ->
        uiText(R.string.view_anchored_by_logged_period, formatCycleRange(currentCycle))
    latestCycle != null ->
        uiText(R.string.predictions_combine_latest_cycle_with_baseline)
    profile != null ->
        uiText(R.string.predictions_use_onboarding_baseline)
    else ->
        uiText(R.string.complete_setup_for_forecasts)
}

private fun buildLatestCycleSummary(
    currentCycle: CycleRecord?,
    latestCycle: CycleRecord?,
): UiText = when {
    currentCycle != null -> uiText(R.string.current_logged_period, formatCycleRange(currentCycle))
    latestCycle != null -> uiText(R.string.most_recent_logged_period, formatCycleRange(latestCycle))
    else -> uiText(R.string.no_logged_period_predictions_from_setup)
}

private fun buildVariabilityText(
    insights: InsightSnapshot?,
    confidenceLabel: String,
): UiText = when {
    insights == null || !insights.hasEnoughCycleData ->
        // The confidence label comes from the domain as English prose, which
        // is a known boundary gap. The fallback is ours, so it is a
        // resource rather than a literal.
        confidenceLabel.ifBlank { null }?.let { UiText.Raw(it) }
            ?: uiText(R.string.from_your_setup)
    insights.minCycleLength == insights.maxCycleLength ->
        uiText(R.string.n_d_steady, insights.averageCycleLength)
    else ->
        uiText(R.string.min_max_d_range, insights.minCycleLength, insights.maxCycleLength)
}

private fun buildReminderTeaser(
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
    rules: List<ReminderRule>,
    cycles: List<CycleRecord>,
    planner: ReminderSchedulePlanner,
    now: ZonedDateTime = ZonedDateTime.now(),
): HomeReminderTeaser {
    if (profile == null) {
        return HomeReminderTeaser(
            title = uiText(R.string.reminders_pending_setup),
            detail = uiText(R.string.finish_onboarding_for_reminders),
            supporting = uiText(R.string.reminder_timing_stays_on_device),
        )
    }
    if (!profile.remindersEnabled) {
        return HomeReminderTeaser(
            title = uiText(R.string.reminders_paused),
            detail = uiText(R.string.turn_reminders_back_on_hint),
            supporting = uiText(R.string.delivered_reminders_remain_in_center),
        )
    }
    val nextReminder = planner.buildSchedules(
        prediction = prediction,
        rules = rules,
        cycles = cycles,
        now = now,
    ).firstOrNull()
    return if (nextReminder == null) {
        HomeReminderTeaser()
    } else {
        HomeReminderTeaser(
            title = nextReminder.type.dashboardLabel(),
            // Planner copy lives in core:domain as plain English; carried
            // as Raw pending the domain localization follow-up.
            detail = UiText.Raw(nextReminder.body),
            supporting = uiText(
                R.string.next_up_at_time,
                nextReminder.scheduledAt.format(reminderDateTimeFormatter),
            ),
            // The producer knows the type; the renderer must not infer it
            // from translated copy.
            isActionable = nextReminder.type == ReminderType.DailyLog,
        )
    }
}

private fun buildInsightTeaser(
    insights: InsightSnapshot?,
    cyclesLoggedCount: Int = 0,
    cyclesNeeded: Int = 3,
): HomeInsightTeaser = when {
    insights == null || (!insights.hasEnoughCycleData && !insights.hasEnoughSymptomData) -> HomeInsightTeaser(
        title = uiText(R.string.insights_are_building),
        detail = uiText(R.string.keep_logging_to_unlock_patterns),
        supporting = uiText(R.string.n_of_m_cycles_logged, cyclesLoggedCount, cyclesNeeded),
    )
    else -> HomeInsightTeaser(
        title = if (insights.irregularCycles) {
            uiText(R.string.cycle_variability_picked_up)
        } else {
            uiText(R.string.recent_rhythm_snapshot)
        },
        // Domain prose, not yet localized in core:domain — Raw pending
        // the follow-up.
        detail = UiText.Raw(insights.narrativeSummary),
        supporting = UiText.Raw(insights.cycleWindowLabel),
        trendPoints = insights.recentCycleLengths.mapIndexed { index, value ->
            // Chart tick label; raw is the justified carrier.
            HomeTrendPoint(label = UiText.Raw("C${index + 1}"), value = value)
        },
    )
}

class HomeViewModel(
    profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    predictionRepository: PredictionRepository,
    insightRepository: InsightRepository,
    reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val validator: CycleRecordValidator,
    private val savedStateHandle: SavedStateHandle,
    private val reminderSchedulePlanner: ReminderSchedulePlanner = ReminderSchedulePlanner(),
) : ViewModel() {
    private val editorFlow = MutableStateFlow(savedStateHandle.toHomeEditorState())
    private val messageFlow = MutableStateFlow<UiText?>(null)

    private val baseState: StateFlow<HomeBaseState> = combine(
        profileRepository.observeProfile(),
        cycleRepository.observeCycles(),
        predictionRepository.observePrediction(),
        insightRepository.observeInsights(),
        reminderRepository.observeRules(),
    ) { profile, cycles, prediction, insights, reminderRules ->
        HomeBaseState(
            profile = profile,
            cycles = cycles,
            prediction = prediction,
            insights = insights,
            reminderRules = reminderRules,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeBaseState())

    val uiState: StateFlow<HomeUiState> = combine(
        baseState,
        editorFlow,
        messageFlow,
    ) { base, editor, message ->
        val currentCycle = base.currentUserCycle()
        val userCycles = base.cycles.filter { it.source == CycleSource.User || it.source == CycleSource.Imported }
        val latestCycle = userCycles.lastOrNull()
        // The observed average is the canonical figure. Insights renders
        // the same number from the same snapshot, so the two screens
        // cannot disagree about the user's cycle length on the same day,
        // which they could when Today showed the onboarding baseline
        // and Insights showed what the logged history actually says.
        val cycleLength = resolveAverageCycleLength(
            insights = base.insights,
            profile = base.profile,
        )
        val validatedEditor = validateHomeEditorState(
            editor = editor,
            cycles = base.cycles,
            validator = validator,
        )
        HomeUiState(
            daysUntilNextPeriod = base.prediction?.daysUntilNextPeriod,
            nextPeriodDateText = formatDashboardDate(base.prediction?.nextPeriodDate),
            cycleDay = base.prediction?.cycleDay,
            cycleLength = cycleLength,
            averagePeriodLength = base.profile?.periodLength ?: latestCycle?.let { cycle ->
                java.time.temporal.ChronoUnit.DAYS.between(cycle.startDate, cycle.endDate).toInt() + 1
            } ?: 5,
            variabilityText = buildVariabilityText(
                insights = base.insights,
                confidenceLabel = base.prediction?.confidenceLabel.orEmpty(),
            ),
            fertileWindowText = formatFertileWindow(
                range = base.prediction?.fertileWindow,
                supportsRange = base.prediction?.supportsRange ?: false,
            ),
            ovulationEstimateText = formatDashboardDate(base.prediction?.ovulationDate),
            confidenceLabel = base.prediction?.confidenceLabel.orEmpty(),
            nextPeriodRangeText = formatNextPeriodRange(
                range = base.prediction?.nextPeriodWindow,
                supportsRange = base.prediction?.supportsRange ?: false,
                halfWidthDays = base.prediction?.nextPeriodHalfWidthDays,
            ),
            supportsRange = base.prediction?.supportsRange ?: false,
            overdueDays = base.prediction?.overdueDays ?: 0,
            hasCurrentCycle = currentCycle != null,
            cycleModeLabel = buildCycleModeLabel(currentCycle, base.prediction),
            cyclePhaseLabel = buildHomeCyclePhaseLabel(currentCycle, base.prediction, cycleLength),
            cycleSourceSummary = buildCycleSourceSummary(
                profile = base.profile,
                currentCycle = currentCycle,
                latestCycle = latestCycle,
            ),
            latestCycleSummary = buildLatestCycleSummary(
                currentCycle = currentCycle,
                latestCycle = latestCycle,
            ),
            reminderTeaser = buildReminderTeaser(
                profile = base.profile,
                prediction = base.prediction,
                rules = base.reminderRules,
                cycles = base.cycles,
                planner = reminderSchedulePlanner,
            ),
            insightTeaser = buildInsightTeaser(
                insights = base.insights,
                cyclesLoggedCount = userCycles.size,
                cyclesNeeded = 3,
            ),
            editorState = validatedEditor,
            quickActions = defaultHomeQuickActions(),
            message = message,
            cyclesLoggedCount = userCycles.size,
            cyclesNeededForInsights = 3,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Recomputes the editor's validation errors and [HomeEditorState.isSaveEnabled] so the save
     * button reflects the current input rather than a stale value.
     */
    private fun validateEditor(editor: HomeEditorState): HomeEditorState =
        validateHomeEditorState(editor = editor, cycles = baseState.value.cycles, validator = validator)
            .also(savedStateHandle::persistHomeEditorState)

    fun openEditor() {
        messageFlow.update { null }
        val currentCycle = baseState.value.currentUserCycle()
        editorFlow.update {
            validateEditor(
                when {
                    currentCycle != null -> currentCycle.toEditorState()
                    else -> defaultEditorState(baseState.value.profile)
                }
            )
        }
    }

    fun expandEditor() {
        editorFlow.update { current ->
            val fallback = defaultEditorState(baseState.value.profile)
            validateEditor(
                (current.takeIf { it.isVisible } ?: fallback).copy(
                    isVisible = true,
                    isExpanded = true,
                    error = null,
                )
            )
        }
    }

    fun dismissEditor() {
        editorFlow.update { HomeEditorState() }
        savedStateHandle.clearHomeEditorState()
    }

    fun updateEditorStartDate(value: String) = updateEditorState { it.copy(startDateText = value, error = null) }
    fun updateEditorEndDate(value: String) = updateEditorState { it.copy(endDateText = value, error = null) }
    fun updateEditorCycleLength(value: String) = updateEditorState { it.copy(cycleLengthText = value, error = null) }
    fun updateEditorNotes(value: String) = updateEditorState { it.copy(notes = value, error = null) }

    fun quickStartPeriodToday() {
        if (editorFlow.value.isSaving) return
        val base = baseState.value
        val currentCycle = base.currentUserCycle()
        if (currentCycle != null) {
            editorFlow.update { currentCycle.toEditorState().also(savedStateHandle::persistHomeEditorState) }
            return
        }

        val profile = base.profile ?: return
        val today = LocalDate.now()
        val candidate = CycleRecord(
            id = UUID.randomUUID().toString(),
            startDate = today,
            endDate = today.plusDays((profile.periodLength - 1).toLong()),
            cycleLength = profile.averageCycleLength,
            source = CycleSource.User,
        )
        saveCandidate(
            candidate = candidate,
            cycles = base.cycles,
            successMessage = uiText(R.string.period_started_locally),
            onValidationError = { error ->
                editorFlow.update {
                    defaultEditorState(profile, today).copy(
                        isExpanded = true,
                        isSaving = false,
                        error = error,
                    ).also(savedStateHandle::persistHomeEditorState)
                }
            },
            onBeforeSave = {
                editorFlow.update {
                    defaultEditorState(profile, today).copy(
                        isVisible = true,
                        isExpanded = false,
                        isSaving = true,
                    ).also(savedStateHandle::persistHomeEditorState)
                }
            },
        )
    }

    fun saveCycle() {
        val editor = editorFlow.value
        val validatedEditor = validateHomeEditorState(
            editor = editor,
            cycles = baseState.value.cycles,
            validator = validator,
        )
        if (!validatedEditor.isSaveEnabled) {
            editorFlow.update { validatedEditor.also(savedStateHandle::persistHomeEditorState) }
            return
        }

        val candidate = CycleRecord(
            id = editor.editingId ?: UUID.randomUUID().toString(),
            startDate = validatedEditor.parsedStartDate!!,
            endDate = validatedEditor.parsedEndDate!!,
            cycleLength = validatedEditor.parsedCycleLength!!,
            source = CycleSource.User,
            notes = editor.notes.ifBlank { null },
        )
        saveCandidate(
            candidate = candidate,
            cycles = baseState.value.cycles,
            successMessage = if (editor.editingId == null) {
                uiText(R.string.cycle_saved_locally)
            } else {
                uiText(R.string.cycle_updated_locally)
            },
            onValidationError = { error ->
                editorFlow.update {
                    it.copy(isSaving = false, error = error).also(savedStateHandle::persistHomeEditorState)
                }
            },
            onBeforeSave = {
                editorFlow.update {
                    it.copy(isSaving = true).also(savedStateHandle::persistHomeEditorState)
                }
            },
        )
    }

    private fun saveCandidate(
        candidate: CycleRecord,
        cycles: List<CycleRecord>,
        successMessage: UiText,
        // Core:domain validator messages are plain English until the
        // deferred domain localization follow-up; carried as Raw.
        onValidationError: (UiText) -> Unit,
        onBeforeSave: () -> Unit,
    ) {
        val validation = validator.validate(candidate, cycles)
        if (!validation.isValid) {
            onValidationError(UiText.Raw(validation.message.orEmpty()))
            return
        }

        onBeforeSave()
        viewModelScope.launch {
            cycleRepository.upsertCycle(candidate)
            reminderScheduler.refreshSchedules()
            editorFlow.update { HomeEditorState() }
            savedStateHandle.clearHomeEditorState()
            messageFlow.update { successMessage }
        }
    }

    private fun updateEditorState(transform: (HomeEditorState) -> HomeEditorState) {
        editorFlow.update { current ->
            // The editor's errors and its save-enabled flag are recomputed on
            // every change, so the button reflects the current input rather
            // than a stale value.
            validateEditor(transform(current))
        }
    }
}

class HomeViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val predictionRepository: PredictionRepository,
    private val insightRepository: InsightRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val validator: CycleRecordValidator = CycleRecordValidator(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        HomeViewModel(
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            predictionRepository = predictionRepository,
            insightRepository = insightRepository,
            reminderRepository = reminderRepository,
            reminderScheduler = reminderScheduler,
            validator = validator,
            savedStateHandle = extras.createSavedStateHandle(),
        ) as T
}

@Composable
fun HomeRoute(
    factory: HomeViewModelFactory,
    onOpenLog: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenCycleStatus: () -> Unit,
    onOpenFertilityDetail: () -> Unit,
    reminderUnreadCount: Int,
    onOpenReminderCenter: () -> Unit,
    inlineAd: (@Composable () -> Unit)? = null,
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onOpenLog = onOpenLog,
        onOpenCalendar = onOpenCalendar,
        onOpenInsights = onOpenInsights,
        onOpenCycleStatus = onOpenCycleStatus,
        onOpenFertilityDetail = onOpenFertilityDetail,
        reminderUnreadCount = reminderUnreadCount,
        onOpenReminderCenter = onOpenReminderCenter,
        onOpenEditCycle = viewModel::openEditor,
        onExpandEditor = viewModel::expandEditor,
        onDismissEditor = viewModel::dismissEditor,
        onQuickStartPeriod = viewModel::quickStartPeriodToday,
        onStartDateChanged = viewModel::updateEditorStartDate,
        onEndDateChanged = viewModel::updateEditorEndDate,
        onCycleLengthChanged = viewModel::updateEditorCycleLength,
        onNotesChanged = viewModel::updateEditorNotes,
        onSaveCycle = viewModel::saveCycle,
        inlineAd = inlineAd,
    )
}

private fun ReminderType.dashboardLabel(): UiText = when (this) {
    ReminderType.PeriodPrep -> uiText(R.string.period_reminder)
    ReminderType.FertileWindowStart -> uiText(R.string.fertile_window_reminder)
    ReminderType.OvulationEstimate -> uiText(R.string.ovulation_reminder)
    ReminderType.DailyLog -> uiText(R.string.daily_check_in)
    ReminderType.LatePeriod -> uiText(R.string.late_period_check)
    ReminderType.Hydration -> uiText(R.string.hydration_reminder)
    ReminderType.Pill -> uiText(R.string.pill_reminder)
}

private fun HomeBaseState.currentUserCycle(today: LocalDate = LocalDate.now()): CycleRecord? =
    cycles.lastOrNull { cycle ->
        cycle.source == CycleSource.User &&
            !today.isBefore(cycle.startDate) &&
            !today.isAfter(cycle.endDate)
    }

private fun CycleRecord.toEditorState(): HomeEditorState =
    HomeEditorState(
        isVisible = true,
        isExpanded = true,
        editingId = id,
        startDateText = startDate.toString(),
        endDateText = endDate.toString(),
        cycleLengthText = cycleLength.toString(),
        notes = notes.orEmpty(),
    )

internal fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

internal fun SavedStateHandle.toHomeEditorState(): HomeEditorState {
    val isVisible = get<Boolean>(HomeStateKeys.EditorVisible) ?: false
    return if (!isVisible) {
        HomeEditorState()
    } else {
        HomeEditorState(
            isVisible = true,
            isExpanded = get<Boolean>(HomeStateKeys.EditorExpanded) ?: false,
            editingId = get<String>(HomeStateKeys.EditorEditingId),
            startDateText = get<String>(HomeStateKeys.EditorStartDateText).orEmpty(),
            endDateText = get<String>(HomeStateKeys.EditorEndDateText).orEmpty(),
            cycleLengthText = get<String>(HomeStateKeys.EditorCycleLengthText) ?: "28",
            notes = get<String>(HomeStateKeys.EditorNotes).orEmpty(),
        )
    }
}

internal fun SavedStateHandle.persistHomeEditorState(state: HomeEditorState) {
    this[HomeStateKeys.EditorVisible] = state.isVisible
    this[HomeStateKeys.EditorExpanded] = state.isExpanded
    this[HomeStateKeys.EditorEditingId] = state.editingId
    this[HomeStateKeys.EditorStartDateText] = state.startDateText
    this[HomeStateKeys.EditorEndDateText] = state.endDateText
    this[HomeStateKeys.EditorCycleLengthText] = state.cycleLengthText
    this[HomeStateKeys.EditorNotes] = state.notes
}

internal fun SavedStateHandle.clearHomeEditorState() {
    this[HomeStateKeys.EditorVisible] = false
    this[HomeStateKeys.EditorExpanded] = false
    remove<String>(HomeStateKeys.EditorEditingId)
    remove<String>(HomeStateKeys.EditorStartDateText)
    remove<String>(HomeStateKeys.EditorEndDateText)
    remove<String>(HomeStateKeys.EditorCycleLengthText)
    remove<String>(HomeStateKeys.EditorNotes)
}
