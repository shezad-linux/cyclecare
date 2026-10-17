@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.DashboardKeyValueRow
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.MetricPill
import com.cyclecare.core.designsystem.MetricPillTone
import com.cyclecare.core.designsystem.PeriodActiveGradientColors
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.cyclecare.core.designsystem.PeriodHeroScene
import com.cyclecare.core.designsystem.ScreenDebugPreviews
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
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.ScheduledReminder
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

data class HomeUiState(
    val daysUntilNextPeriod: Int? = null,
    val nextPeriodDateText: String = "Prediction unavailable",
    val cycleDay: Int? = null,
    val cycleLength: Int = 28,
    val averagePeriodLength: Int = 5,
    val variabilityText: String = "From your setup",
    val fertileWindowText: String = "Fertile window unavailable",
    val ovulationEstimateText: String = "Unavailable",
    val confidenceLabel: String = "",
    val hasCurrentCycle: Boolean = false,
    val cycleModeLabel: String = "Based on your history",
    val cyclePhaseLabel: String = "",
    val cycleSourceSummary: String = "Predictions use your saved baseline until more cycle history is logged.",
    val latestCycleSummary: String = "No logged period yet.",
    val reminderTeaser: HomeReminderTeaser = HomeReminderTeaser(),
    val insightTeaser: HomeInsightTeaser = HomeInsightTeaser(),
    val editorState: HomeEditorState = HomeEditorState(),
    val quickActions: List<HomeQuickAction> = defaultHomeQuickActions(),
    val message: String? = null,
    val cyclesLoggedCount: Int = 0,
    val cyclesNeededForInsights: Int = 3,
)

data class HomeReminderTeaser(
    val title: String = "No future reminder queued",
    val detail: String = "Enable reminder rules in Settings to surface the next alert here.",
    val supporting: String = "Delivered reminders still appear in your local reminder center.",
)

data class HomeInsightTeaser(
    val title: String = "Insights are building",
    val detail: String = "Keep logging cycles and symptoms to unlock stronger trends and pattern summaries.",
    val supporting: String = "Local insights never leave this device by default.",
    val trendPoints: List<HomeTrendPoint> = emptyList(),
)

data class HomeTrendPoint(
    val label: String,
    val value: Int,
)

data class HomeEditorState(
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
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

enum class HomeQuickActionType {
    LogSymptoms,
    OpenCalendar,
    ViewInsights,
}

data class HomeQuickAction(
    val type: HomeQuickActionType,
    val label: String,
    val description: String,
)

private data class HomeBaseState(
    val profile: UserProfile? = null,
    val cycles: List<CycleRecord> = emptyList(),
    val prediction: PredictionSnapshot? = null,
    val insights: InsightSnapshot? = null,
    val reminderRules: List<ReminderRule> = emptyList(),
)

private val dashboardDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
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

internal object HomeTags {
    const val DASHBOARD = "home_dashboard"
    const val HERO_CARD = "home_hero_card"
    const val EDIT_CYCLE_BUTTON = "home_edit_cycle"
    const val PROGRESS_BAR = "home_progress_bar"
    const val FERTILE_CARD = "home_fertile_card"
    const val REMINDER_CARD = "home_reminder_card"
    const val INSIGHTS_CARD = "home_insights_card"
    const val TODAY_ACTIONS = "home_today_actions"
    const val REMINDER_CENTER_BUTTON = "home_reminder_center"
    const val CYCLE_STATUS_CTA = "home_cycle_status_cta"
    const val FERTILITY_DETAIL_CTA = "home_fertility_detail_cta"
    const val REMINDER_DETAIL_CTA = "home_reminder_detail_cta"
    const val INSIGHTS_DETAIL_CTA = "home_insights_detail_cta"
    const val CYCLE_STATUS_SCREEN = "home_cycle_status_screen"
    const val CYCLE_STATUS_HERO = "home_cycle_status_hero"
    const val FERTILITY_DETAIL_SCREEN = "home_fertility_detail_screen"

    fun actionTag(type: HomeQuickActionType): String = "home_action_${type.name}"
}

internal fun defaultHomeQuickActions(): List<HomeQuickAction> = listOf(
    HomeQuickAction(
        type = HomeQuickActionType.LogSymptoms,
        label = "Track today",
        description = "Log mood, pain, energy, and more.",
    ),
    HomeQuickAction(
        type = HomeQuickActionType.OpenCalendar,
        label = "Calendar",
        description = "View history and upcoming dates.",
    ),
    HomeQuickAction(
        type = HomeQuickActionType.ViewInsights,
        label = "Insights",
        description = "See patterns from your logged data.",
    ),
)

internal fun formatNextPeriodTitle(daysUntilNextPeriod: Int?): String = when {
    daysUntilNextPeriod == null -> "Prediction will appear after setup"
    daysUntilNextPeriod > 1 -> "Next period in $daysUntilNextPeriod days"
    daysUntilNextPeriod == 1 -> "Next period in 1 day"
    daysUntilNextPeriod == 0 -> "Next period due today"
    else -> "Next period timing needs review"
}

internal fun homeHeroTitle(uiState: HomeUiState): String =
    if (uiState.hasCurrentCycle) "Period started" else formatNextPeriodTitle(uiState.daysUntilNextPeriod)

internal fun homeHeroSubtitle(uiState: HomeUiState): String =
    if (uiState.hasCurrentCycle) "Log how today feels" else "Expected ${uiState.nextPeriodDateText}"

internal fun formatCycleProgressLabel(
    cycleDay: Int?,
    cycleLength: Int,
    confidenceLabel: String,
): String {
    val dayLabel = if (cycleDay == null) "Day -- / $cycleLength" else "Day $cycleDay / $cycleLength"
    return if (confidenceLabel.isBlank()) dayLabel else "$dayLabel • $confidenceLabel"
}

internal fun formatFertileWindow(range: DateRange?): String =
    range?.let { "${it.start.format(dashboardDateFormatter)} - ${it.endInclusive.format(dashboardDateFormatter)}" }
        ?: "Fertile window unavailable"

internal fun formatDashboardDate(date: LocalDate?): String =
    date?.format(dashboardDateFormatter) ?: "Unavailable"

internal fun progressStateDescription(cycleDay: Int?, cycleLength: Int): String =
    if (cycleDay == null) {
        "Cycle progress unavailable"
    } else {
        "Day $cycleDay of $cycleLength"
    }

internal fun formatCycleRange(record: CycleRecord?): String = when {
    record == null -> "No logged period yet."
    // A null end means the period has not finished yet.
    record.endDate == null ->
        "${record.startDate.format(dashboardDateFormatter)} - ongoing"
    else ->
        "${record.startDate.format(dashboardDateFormatter)} - ${record.endDate!!.format(dashboardDateFormatter)}"
}

private fun buildCycleModeLabel(
    currentCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
): String = when {
    currentCycle != null -> "Currently logged"
    prediction != null -> "Based on your history"
    else -> "Waiting for prediction"
}

private fun buildCyclePhaseLabel(
    cycleDay: Int?,
    cycleLength: Int,
): String {
    if (cycleDay == null) return ""
    val cycleProgress = cycleDay.toFloat() / cycleLength.toFloat()
    return when {
        cycleProgress < 0.15f -> "Early cycle"
        cycleProgress < 0.40f -> "Mid cycle"
        cycleProgress < 0.60f -> "Fertile window"
        cycleProgress < 0.85f -> "Late cycle"
        else -> "Approaching next period"
    }
}

internal fun buildHomeCyclePhaseLabel(
    currentCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
    cycleLength: Int,
): String = if (currentCycle != null) {
    "Period days"
} else {
    buildCyclePhaseLabel(prediction?.cycleDay, cycleLength)
}

private fun buildCycleSourceSummary(
    profile: UserProfile?,
    currentCycle: CycleRecord?,
    latestCycle: CycleRecord?,
): String = when {
    currentCycle != null ->
        "Your current view is anchored by a logged period that runs ${formatCycleRange(currentCycle)}."
    latestCycle != null ->
        "Predictions combine your latest logged cycle with the saved baseline from setup."
    profile != null ->
        "Predictions are using your onboarding baseline until you log more local cycle history."
    else ->
        "Complete setup to start generating local cycle forecasts."
}

private fun buildLatestCycleSummary(
    currentCycle: CycleRecord?,
    latestCycle: CycleRecord?,
): String = when {
    currentCycle != null -> "Current logged period • ${formatCycleRange(currentCycle)}"
    latestCycle != null -> "Most recent logged period • ${formatCycleRange(latestCycle)}"
    else -> "No logged period yet. Predictions are based on your setup."
}

private fun buildVariabilityText(
    insights: InsightSnapshot?,
    confidenceLabel: String,
): String = when {
    insights == null || !insights.hasEnoughCycleData -> confidenceLabel.ifBlank { "From your setup" }
    insights.minCycleLength == insights.maxCycleLength -> "${insights.averageCycleLength}d steady"
    else -> "${insights.minCycleLength}-${insights.maxCycleLength}d range"
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
            title = "Reminders pending setup",
            detail = "Finish onboarding to activate local reminder scheduling.",
            supporting = "Reminder timing stays on this device.",
        )
    }
    if (!profile.remindersEnabled) {
        return HomeReminderTeaser(
            title = "Reminders paused",
            detail = "Turn reminders back on in Settings when you want period, fertility, or daily alerts again.",
            supporting = "Delivered reminders still remain in your local reminder center.",
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
            detail = nextReminder.body,
            supporting = "Next up ${nextReminder.scheduledAt.format(reminderDateTimeFormatter)}",
        )
    }
}

private fun buildInsightTeaser(
    insights: InsightSnapshot?,
    cyclesLoggedCount: Int = 0,
    cyclesNeeded: Int = 3,
): HomeInsightTeaser = when {
    insights == null || (!insights.hasEnoughCycleData && !insights.hasEnoughSymptomData) -> HomeInsightTeaser(
        title = "Insights are building",
        detail = "Keep logging to unlock patterns and trends.",
        supporting = "$cyclesLoggedCount/$cyclesNeeded cycles logged",
    )
    else -> HomeInsightTeaser(
        title = if (insights.irregularCycles) "Cycle variability picked up" else "Recent rhythm snapshot",
        detail = insights.narrativeSummary,
        supporting = insights.cycleWindowLabel,
        trendPoints = insights.recentCycleLengths.mapIndexed { index, value ->
            HomeTrendPoint(label = "C${index + 1}", value = value)
        },
    )
}

internal fun defaultEditorState(
    profile: UserProfile?,
    today: LocalDate = LocalDate.now(),
): HomeEditorState {
    val periodLength = profile?.periodLength ?: 5
    val cycleLength = profile?.averageCycleLength ?: 28
    return HomeEditorState(
        isVisible = true,
        isExpanded = false,
        startDateText = today.toString(),
        endDateText = today.plusDays((periodLength - 1).toLong()).toString(),
        cycleLengthText = cycleLength.toString(),
    )
}

internal fun validateHomeEditorState(
    editor: HomeEditorState,
    cycles: List<CycleRecord>,
    validator: CycleRecordValidator,
    today: LocalDate = LocalDate.now(),
): HomeEditorState {
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
    private val messageFlow = MutableStateFlow<String?>(null)

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
        val cycleLength = base.profile?.averageCycleLength ?: base.cycles.lastOrNull()?.cycleLength ?: 28
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
            fertileWindowText = formatFertileWindow(base.prediction?.fertileWindow),
            ovulationEstimateText = formatDashboardDate(base.prediction?.ovulationDate),
            confidenceLabel = base.prediction?.confidenceLabel.orEmpty(),
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

    fun openEditor() {
        messageFlow.update { null }
        val currentCycle = baseState.value.currentUserCycle()
        editorFlow.update {
            when {
                currentCycle != null -> currentCycle.toEditorState()
                else -> defaultEditorState(baseState.value.profile)
            }.also(savedStateHandle::persistHomeEditorState)
        }
    }

    fun expandEditor() {
        editorFlow.update { current ->
            val fallback = defaultEditorState(baseState.value.profile)
            (current.takeIf { it.isVisible } ?: fallback).copy(
                isVisible = true,
                isExpanded = true,
                error = null,
            ).also(savedStateHandle::persistHomeEditorState)
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
            successMessage = "Period started locally.",
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
            successMessage = if (editor.editingId == null) "Cycle saved locally." else "Cycle updated locally.",
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
        successMessage: String,
        onValidationError: (String) -> Unit,
        onBeforeSave: () -> Unit,
    ) {
        val validation = validator.validate(candidate, cycles)
        if (!validation.isValid) {
            onValidationError(validation.message.orEmpty())
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
            transform(current).also(savedStateHandle::persistHomeEditorState)
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

@Composable
fun HomeCycleStatusRoute(
    factory: HomeViewModelFactory,
    onBackToDashboard: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeCycleStatusScreen(
        uiState = uiState,
        onBack = onBackToDashboard,
        onOpenEditCycle = viewModel::openEditor,
        onOpenCalendar = onOpenCalendar,
    )
    if (uiState.editorState.isVisible) {
        EditCycleSheet(
            editor = uiState.editorState,
            hasCurrentCycle = uiState.hasCurrentCycle,
            onDismiss = viewModel::dismissEditor,
            onExpand = viewModel::expandEditor,
            onQuickStartPeriod = viewModel::quickStartPeriodToday,
            onStartDateChanged = viewModel::updateEditorStartDate,
            onEndDateChanged = viewModel::updateEditorEndDate,
            onCycleLengthChanged = viewModel::updateEditorCycleLength,
            onNotesChanged = viewModel::updateEditorNotes,
            onSaveCycle = viewModel::saveCycle,
        )
    }
}

@Composable
fun HomeFertilityDetailRoute(
    factory: HomeViewModelFactory,
    onBackToDashboard: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeFertilityDetailScreen(
        uiState = uiState,
        onBack = onBackToDashboard,
        onOpenCalendar = onOpenCalendar,
        onOpenInsights = onOpenInsights,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onOpenLog: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenCycleStatus: () -> Unit,
    onOpenFertilityDetail: () -> Unit,
    reminderUnreadCount: Int,
    onOpenReminderCenter: () -> Unit,
    onOpenEditCycle: () -> Unit,
    onExpandEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onQuickStartPeriod: () -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onCycleLengthChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveCycle: () -> Unit,
    inlineAd: (@Composable () -> Unit)? = null,
) {
    val progress = remember(uiState.cycleDay, uiState.cycleLength) {
        uiState.cycleDay
            ?.let { cycleDay -> (cycleDay.toFloat() / uiState.cycleLength.toFloat()).coerceIn(0f, 1f) }
            ?: 0f
    }
    val heroTitle = remember(uiState.hasCurrentCycle, uiState.daysUntilNextPeriod) {
        homeHeroTitle(uiState)
    }
    val heroSubtitle = remember(uiState.hasCurrentCycle, uiState.nextPeriodDateText) {
        homeHeroSubtitle(uiState)
    }
    val heroGradientColors = if (uiState.hasCurrentCycle) PeriodActiveGradientColors() else null
    val cycleProgressLabel = remember(uiState.cycleDay, uiState.cycleLength, uiState.confidenceLabel) {
        formatCycleProgressLabel(
            cycleDay = uiState.cycleDay,
            cycleLength = uiState.cycleLength,
            confidenceLabel = uiState.confidenceLabel,
        )
    }

    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .testTag(HomeTags.DASHBOARD),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
    ) {
        item {
            HomeDashboardHeader(
                reminderUnreadCount = reminderUnreadCount,
                onOpenReminderCenter = onOpenReminderCenter,
            )
        }
        item {
            GradientHeaderCard(
                title = heroTitle,
                subtitle = heroSubtitle,
                modifier = Modifier.testTag(HomeTags.HERO_CARD),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                gradientColors = heroGradientColors,
                stackTrailingOnCompact = true,
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            text = "Day ${uiState.cycleDay ?: "--"} of ${uiState.cycleLength}",
                            tone = MetricPillTone.Inverse,
                        )
                        MetricPill(
                            text = uiState.cycleModeLabel,
                            tone = MetricPillTone.Inverse,
                        )
                        if (uiState.cyclePhaseLabel.isNotBlank()) {
                            MetricPill(
                                text = uiState.cyclePhaseLabel,
                                tone = MetricPillTone.Inverse,
                            )
                        }
                    }
                    Text(
                        text = uiState.cycleSourceSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .testTag(HomeTags.PROGRESS_BAR)
                            .semantics {
                                stateDescription = "Day ${uiState.cycleDay ?: 0} of ${uiState.cycleLength}"
                            },
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                    )
                    Button(
                        onClick = onOpenCycleStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HomeTags.CYCLE_STATUS_CTA),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("View cycle details")
                    }
                },
                trailing = {
                    OutlinedButton(
                        onClick = onOpenEditCycle,
                        modifier = Modifier.testTag(HomeTags.EDIT_CYCLE_BUTTON),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit")
                    }
                },
            )
        }
        item {
            HomeMetricGrid(
                uiState = uiState,
                reminderUnreadCount = reminderUnreadCount,
            )
        }
        item {
            FertilityTeaserCard(
                fertileWindowText = uiState.fertileWindowText,
                ovulationEstimateText = uiState.ovulationEstimateText,
                confidenceLabel = uiState.confidenceLabel,
                onOpenFertilityDetail = onOpenFertilityDetail,
            )
        }
        item {
            ReminderTeaserCard(
                teaser = uiState.reminderTeaser,
                unreadCount = reminderUnreadCount,
                onOpenReminderCenter = onOpenReminderCenter,
                onTrackToday = onOpenLog,
            )
        }
        item {
            InsightTeaserCard(
                teaser = uiState.insightTeaser,
                onOpenInsights = onOpenInsights,
            )
        }
        if (inlineAd != null) {
            item {
                inlineAd()
            }
        }
        item {
            DashboardSectionCard(
                title = "Quick actions",
                modifier = Modifier.testTag(HomeTags.TODAY_ACTIONS),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.quickActions.forEach { action ->
                        QuickActionButton(
                            modifier = Modifier.testTag(HomeTags.actionTag(action.type)),
                            action = action,
                            onClick = when (action.type) {
                                HomeQuickActionType.LogSymptoms -> onOpenLog
                                HomeQuickActionType.OpenCalendar -> onOpenCalendar
                                HomeQuickActionType.ViewInsights -> onOpenInsights
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        "Your data stays private on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (uiState.editorState.isVisible) {
        EditCycleSheet(
            editor = uiState.editorState,
            hasCurrentCycle = uiState.hasCurrentCycle,
            onDismiss = onDismissEditor,
            onExpand = onExpandEditor,
            onQuickStartPeriod = onQuickStartPeriod,
            onStartDateChanged = onStartDateChanged,
            onEndDateChanged = onEndDateChanged,
            onCycleLengthChanged = onCycleLengthChanged,
            onNotesChanged = onNotesChanged,
            onSaveCycle = onSaveCycle,
        )
    }
}

@Composable
private fun HomeDashboardHeader(
    reminderUnreadCount: Int,
    onOpenReminderCenter: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStackedHeader = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f

        if (useStackedHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Dashboard",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HomeReminderButton(
                        reminderUnreadCount = reminderUnreadCount,
                        onOpenReminderCenter = onOpenReminderCenter,
                    )
                }
                Text(
                    text = "Cycle, reminders, and insights in one local view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Dashboard",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Cycle, reminders, and insights in one local view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HomeReminderButton(
                    reminderUnreadCount = reminderUnreadCount,
                    onOpenReminderCenter = onOpenReminderCenter,
                )
            }
        }
    }
}

@Composable
private fun HomeReminderButton(
    reminderUnreadCount: Int,
    onOpenReminderCenter: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onOpenReminderCenter,
        modifier = Modifier.testTag(HomeTags.REMINDER_CENTER_BUTTON),
    ) {
        BadgedBox(
            badge = {
                if (reminderUnreadCount > 0) {
                    Badge {
                        Text(reminderUnreadCount.coerceAtMost(99).toString())
                    }
                }
            },
        ) {
            Icon(Icons.Rounded.Notifications, contentDescription = "Open reminder center")
        }
    }
}

@Composable
private fun HomeMetricGrid(
    uiState: HomeUiState,
    reminderUnreadCount: Int,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStackedMetrics = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.2f

        if (useStackedMetrics) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "Avg cycle",
                    value = "${uiState.cycleLength} days",
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                )
                MetricCard(
                    label = "Avg period",
                    value = "${uiState.averagePeriodLength} days",
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                )
                MetricCard(
                    label = "Variability",
                    value = uiState.variabilityText,
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                )
                MetricCard(
                    label = "Unread reminders",
                    value = reminderUnreadCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "Avg cycle",
                        value = "${uiState.cycleLength} days",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                    )
                    MetricCard(
                        label = "Avg period",
                        value = "${uiState.averagePeriodLength} days",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "Variability",
                        value = uiState.variabilityText,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    )
                    MetricCard(
                        label = "Unread reminders",
                        value = reminderUnreadCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FertilityTeaserCard(
    fertileWindowText: String,
    ovulationEstimateText: String,
    confidenceLabel: String,
    onOpenFertilityDetail: () -> Unit,
) {
    DashboardSectionCard(
        title = "Fertile window",
        subtitle = fertileWindowText,
        modifier = Modifier.testTag(HomeTags.FERTILE_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Ovulation estimate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ovulationEstimateText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (confidenceLabel.isNotBlank()) {
                    DashboardPill(text = confidenceLabel)
                }
            }
            FilledTonalButton(
                onClick = onOpenFertilityDetail,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTags.FERTILITY_DETAIL_CTA),
            ) {
                Text("View fertility details")
            }
        }
    }
}

@Composable
private fun ReminderTeaserCard(
    teaser: HomeReminderTeaser,
    unreadCount: Int,
    onOpenReminderCenter: () -> Unit,
    onTrackToday: () -> Unit,
) {
    val isDailyCheckIn = teaser.title.contains("check-in", ignoreCase = true)
    DashboardSectionCard(
        title = teaser.title,
        subtitle = teaser.detail,
        modifier = Modifier.testTag(HomeTags.REMINDER_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (unreadCount > 0) {
                    DashboardPill(
                        text = "$unreadCount unread",
                        emphasized = true,
                    )
                }
                DashboardPill(text = teaser.supporting)
            }
            if (isDailyCheckIn) {
                Button(
                    onClick = onTrackToday,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Check in now")
                }
            } else {
                FilledTonalButton(
                    onClick = onOpenReminderCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HomeTags.REMINDER_DETAIL_CTA),
                ) {
                    Text("View details")
                }
            }
        }
    }
}

@Composable
private fun InsightTeaserCard(
    teaser: HomeInsightTeaser,
    onOpenInsights: () -> Unit,
) {
    DashboardSectionCard(
        title = teaser.title,
        subtitle = teaser.detail,
        modifier = Modifier.testTag(HomeTags.INSIGHTS_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (teaser.trendPoints.isNotEmpty()) {
                HomeMiniTrendStrip(points = teaser.trendPoints)
            }
            DashboardPill(
                text = teaser.supporting,
                emphasized = teaser.trendPoints.isEmpty(),
            )
            OutlinedButton(
                onClick = onOpenInsights,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("View insights")
            }
        }
    }
}

@Composable
private fun HomeMiniTrendStrip(
    points: List<HomeTrendPoint>,
) {
    val maxValue = points.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        points.takeLast(6).forEach { point ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(42.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height((28 + (point.value * 72 / maxValue)).dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
                    )
                }
                Text(
                    point.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeDetailShell(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to dashboard")
            }
        }
        item {
            headerContent?.invoke() ?: GradientHeaderCard(
                title = title,
                subtitle = subtitle,
            )
        }
        item { content() }
    }
}

@Composable
internal fun HomeCycleStatusScreen(
    uiState: HomeUiState,
    onBack: () -> Unit,
    onOpenEditCycle: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val progress = remember(uiState.cycleDay, uiState.cycleLength) {
        uiState.cycleDay
            ?.let { cycleDay -> (cycleDay.toFloat() / uiState.cycleLength.toFloat()).coerceIn(0f, 1f) }
            ?: 0f
    }
    HomeDetailShell(
        title = formatNextPeriodTitle(uiState.daysUntilNextPeriod),
        subtitle = "Expected ${uiState.nextPeriodDateText} • ${formatCycleProgressLabel(uiState.cycleDay, uiState.cycleLength, uiState.confidenceLabel)}",
        modifier = Modifier.testTag(HomeTags.CYCLE_STATUS_SCREEN),
        onBack = onBack,
        headerContent = {
            CycleStatusHeroCard(
                uiState = uiState,
                progress = progress,
                modifier = Modifier.testTag(HomeTags.CYCLE_STATUS_HERO),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "Avg cycle",
                        value = "${uiState.cycleLength} days",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Avg period",
                        value = "${uiState.averagePeriodLength} days",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "Variability",
                        value = uiState.variabilityText,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    )
                    MetricCard(
                        label = "Next period",
                        value = uiState.nextPeriodDateText,
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                    )
                }
            }
            DashboardSectionCard(
                title = "Current status",
                subtitle = uiState.latestCycleSummary,
                eyebrow = uiState.cycleModeLabel,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .semantics {
                                stateDescription = progressStateDescription(uiState.cycleDay, uiState.cycleLength)
                            },
                    )
                    Text(
                        uiState.cycleSourceSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DashboardSectionCard(
                title = "Logged vs predicted",
                subtitle = "Logged cycle records refresh the dashboard immediately. Future timing still remains an estimate until you log the next start date.",
                eyebrow = "Interpretation",
                accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardKeyValueRow(
                        label = "Fertile window",
                        value = uiState.fertileWindowText,
                    )
                    DashboardKeyValueRow(
                        label = "Ovulation estimate",
                        value = uiState.ovulationEstimateText,
                    )
                }
            }
            Button(onClick = onOpenEditCycle, modifier = Modifier.fillMaxWidth()) {
                Text("Edit cycle")
            }
            OutlinedButton(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
                Text("Open calendar")
            }
        }
    }
}

@Composable
private fun CycleStatusHeroCard(
    uiState: HomeUiState,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useStackedLayout = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        val heroMode = if (uiState.hasCurrentCycle) PeriodHeroMode.Active else PeriodHeroMode.Predicted
        val heroGradientColors = if (uiState.hasCurrentCycle) {
            PeriodActiveGradientColors()
        } else {
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            )
        }
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            val containerModifier = Modifier
                .background(
                    Brush.linearGradient(
                        heroGradientColors,
                    ),
                )
                .padding(20.dp)

            if (useStackedLayout) {
                Column(
                    modifier = containerModifier,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CycleStatusHeroText(
                        uiState = uiState,
                        progress = progress,
                    )
                    PeriodHeroScene(
                        mode = heroMode,
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(188.dp),
                    )
                }
            } else {
                Row(
                    modifier = containerModifier,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CycleStatusHeroText(
                            uiState = uiState,
                            progress = progress,
                        )
                    }
                    PeriodHeroScene(
                        mode = heroMode,
                        progress = progress,
                        modifier = Modifier.size(176.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleStatusHeroText(
    uiState: HomeUiState,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Cycle detail",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
        )
        Text(
            text = homeHeroTitle(uiState),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = homeHeroSubtitle(uiState),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricPill(
                text = uiState.cycleModeLabel,
                tone = MetricPillTone.Inverse,
            )
            if (uiState.confidenceLabel.isNotBlank()) {
                MetricPill(
                    text = uiState.confidenceLabel,
                    tone = MetricPillTone.Inverse,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Cycle progress",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .semantics {
                        stateDescription = progressStateDescription(uiState.cycleDay, uiState.cycleLength)
                    },
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
            )
        }
        Text(
            uiState.cycleSourceSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
        )
    }
}

@Composable
internal fun HomeFertilityDetailScreen(
    uiState: HomeUiState,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    HomeDetailShell(
        title = "Fertility detail",
        subtitle = "${uiState.fertileWindowText} • Ovulation ${uiState.ovulationEstimateText}",
        modifier = Modifier.testTag(HomeTags.FERTILITY_DETAIL_SCREEN),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    label = "Fertile window",
                    value = uiState.fertileWindowText,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                )
                MetricCard(
                    label = "Ovulation",
                    value = uiState.ovulationEstimateText,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                )
            }
            DashboardSectionCard(
                title = "How this estimate is built",
                subtitle = uiState.cycleSourceSummary,
                eyebrow = "From your cycle history",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Fertility timing is estimated from your saved baseline and recent logged cycle history. It should be read as guidance, not a medical measurement.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DashboardKeyValueRow(
                        label = "Current window",
                        value = uiState.fertileWindowText,
                    )
                    DashboardKeyValueRow(
                        label = "Ovulation date",
                        value = uiState.ovulationEstimateText,
                    )
                }
            }
            Button(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
                Text("Open calendar")
            }
            OutlinedButton(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text("Open insights")
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    action: HomeQuickAction,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(action.label, style = MaterialTheme.typography.titleMedium)
            Text(action.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EditCycleSheet(
    editor: HomeEditorState,
    hasCurrentCycle: Boolean,
    onDismiss: () -> Unit,
    onExpand: () -> Unit,
    onQuickStartPeriod: () -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onCycleLengthChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveCycle: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (editor.editingId == null) "Edit cycle" else "Edit current cycle",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (!editor.isExpanded && editor.editingId == null) {
                Text(
                    "Start a new period today or expand the details if you need to correct dates before saving.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Period started today", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Use your saved baseline and period length for a quick local entry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onQuickStartPeriod,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Period started today")
                }
                OutlinedButton(
                    onClick = onExpand,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit details")
                }
            } else {
                Text(
                    text = if (hasCurrentCycle) {
                        "Update the cycle entry covering today. Changes stay local and refresh the timeline immediately."
                    } else {
                        "Adjust the period dates, cycle length, and notes before saving them locally."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = editor.startDateText,
                    onValueChange = onStartDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Start date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = editor.startDateError != null,
                    supportingText = {
                        editor.startDateError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = editor.endDateText,
                    onValueChange = onEndDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("End date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = editor.endDateError != null,
                    supportingText = {
                        editor.endDateError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = editor.cycleLengthText,
                    onValueChange = onCycleLengthChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cycle length") },
                    singleLine = true,
                    isError = editor.cycleLengthError != null,
                    supportingText = {
                        editor.cycleLengthError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = editor.notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Notes") },
                )
                editor.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onSaveCycle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editor.isSaveEnabled,
                ) {
                    Text(
                        if (editor.isSaving) {
                            "Saving..."
                        } else if (editor.editingId == null) {
                            "Save cycle"
                        } else {
                            "Save changes"
                        },
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

private fun HomeQuickAction.icon(): ImageVector = when (type) {
    HomeQuickActionType.LogSymptoms -> Icons.Rounded.Edit
    HomeQuickActionType.OpenCalendar -> Icons.Rounded.DateRange
    HomeQuickActionType.ViewInsights -> Icons.Rounded.LocalFlorist
}

private fun ReminderType.dashboardLabel(): String = when (this) {
    ReminderType.PeriodPrep -> "Period reminder"
    ReminderType.FertileWindowStart -> "Fertile window reminder"
    ReminderType.OvulationEstimate -> "Ovulation reminder"
    ReminderType.DailyLog -> "Daily check-in"
    ReminderType.LatePeriod -> "Late-period check"
    ReminderType.Hydration -> "Hydration reminder"
    ReminderType.Pill -> "Pill reminder"
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

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

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

@ScreenDebugPreviews
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

@ScreenDebugPreviews
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

@ScreenDebugPreviews
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

internal fun previewHomeState() = HomeUiState(
    daysUntilNextPeriod = 23,
    nextPeriodDateText = "Apr 9",
    cycleDay = 6,
    cycleLength = 28,
    averagePeriodLength = 5,
    variabilityText = "26-29d range",
    fertileWindowText = "Mar 21 - Mar 26",
    ovulationEstimateText = "Mar 25",
    confidenceLabel = "",
    hasCurrentCycle = false,
    cycleModeLabel = "Based on your history",
    cyclePhaseLabel = "Early cycle",
    cycleSourceSummary = "Predictions combine your saved baseline and recent local history.",
    latestCycleSummary = "Most recent logged period • Feb 12 - Feb 16",
    reminderTeaser = HomeReminderTeaser(
        title = "Daily check-in",
        detail = "Take a moment to log mood, pain, energy, or medication for today.",
        supporting = "Next up Mar 18 • 8:00 PM",
    ),
    insightTeaser = HomeInsightTeaser(
        title = "Recent rhythm snapshot",
        detail = "Recent cycle lengths are staying close to your saved baseline while stronger symptom clustering forms around period days.",
        supporting = "Based on your four logged cycles.",
        trendPoints = listOf(
            HomeTrendPoint("C1", 27),
            HomeTrendPoint("C2", 28),
            HomeTrendPoint("C3", 29),
            HomeTrendPoint("C4", 28),
        ),
    ),
    cyclesLoggedCount = 4,
    cyclesNeededForInsights = 3,
)
