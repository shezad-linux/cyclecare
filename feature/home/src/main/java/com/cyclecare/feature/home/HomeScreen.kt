@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import kotlin.math.roundToInt
import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.token.CycleCareTypography
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
    val nextPeriodDateText: UiText = uiText(R.string.prediction_unavailable),
    val cycleDay: Int? = null,
    val cycleLength: Int = 28,
    val averagePeriodLength: Int = 5,
    val variabilityText: UiText = uiText(R.string.from_your_setup),
    val fertileWindowText: UiText = uiText(R.string.fertile_window_unavailable),
    val ovulationEstimateText: UiText = uiText(R.string.unavailable),
    val confidenceLabel: String = "",
    /**
     * Human-readable spread, or null when the engine declined to emit a
     * range. Null must never be rendered as if it were zero.
     */
    val nextPeriodRangeText: UiText? = null,
    /** False when history is too thin for any range to be meaningful. */
    val supportsRange: Boolean = false,
    val overdueDays: Int = 0,
    val hasCurrentCycle: Boolean = false,
    val cycleModeLabel: UiText = uiText(R.string.based_on_your_history),
    val cyclePhaseLabel: UiText = uiText(R.string.empty_label),
    val cycleSourceSummary: UiText =
        uiText(R.string.predictions_use_saved_baseline_until_more_history),
    val latestCycleSummary: UiText = uiText(R.string.no_logged_period_yet),
    val reminderTeaser: HomeReminderTeaser = HomeReminderTeaser(),
    val insightTeaser: HomeInsightTeaser = HomeInsightTeaser(),
    val editorState: HomeEditorState = HomeEditorState(),
    val quickActions: List<HomeQuickAction> = defaultHomeQuickActions(),
    val message: UiText? = null,
    val cyclesLoggedCount: Int = 0,
    val cyclesNeededForInsights: Int = 3,
)

data class HomeReminderTeaser(
    val title: UiText = uiText(R.string.no_future_reminder_queued),
    val detail: UiText = uiText(R.string.enable_reminder_rules_in_settings_hint),
    val supporting: UiText = uiText(R.string.delivered_reminders_stay_in_center),
    /**
     * Whether this reminder can be actioned from the card, rather than
     * only inspected.
     *
     * This used to be decided by `teaser.title.contains("check-in")`,
     * which silently breaks the moment the title is translated: the
     * card would fall back to "View details" and offer no way to check
     * in. The decision belongs to the producer, which knows the reminder
     * type, not to a substring match on rendered copy.
     */
    val isActionable: Boolean = false,
)

data class HomeInsightTeaser(
    val title: UiText = uiText(R.string.insights_are_building),
    val detail: UiText = uiText(R.string.keep_logging_for_stronger_trends),
    val supporting: UiText = uiText(R.string.local_insights_stay_on_device),
    val trendPoints: List<HomeTrendPoint> = emptyList(),
)

data class HomeTrendPoint(
    val label: UiText,
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
    val startDateError: UiText? = null,
    val endDateError: UiText? = null,
    val cycleLengthError: UiText? = null,
    val isSaveEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiText? = null,
)

enum class HomeQuickActionType {
    LogSymptoms,
    OpenCalendar,
    ViewInsights,
}

data class HomeQuickAction(
    val type: HomeQuickActionType,
    val label: UiText,
    val description: UiText,
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
    const val NEXT_PERIOD_RANGE = "home_next_period_range"
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
        label = uiText(R.string.track_today),
        description = uiText(R.string.log_mood_pain_energy_hint),
    ),
    HomeQuickAction(
        type = HomeQuickActionType.OpenCalendar,
        label = uiText(R.string.calendar),
        description = uiText(R.string.view_history_and_upcoming_dates),
    ),
    HomeQuickAction(
        type = HomeQuickActionType.ViewInsights,
        label = uiText(R.string.insights),
        description = uiText(R.string.see_patterns_from_logged_data),
    ),
)

internal fun formatNextPeriodTitle(daysUntilNextPeriod: Int?): UiText = when {
    daysUntilNextPeriod == null -> uiText(R.string.prediction_will_appear_after_setup)
    daysUntilNextPeriod > 1 -> pluralUiText(R.plurals.next_period_in_n_days, daysUntilNextPeriod, daysUntilNextPeriod)
    daysUntilNextPeriod == 1 -> uiText(R.string.next_period_in_one_day)
    daysUntilNextPeriod == 0 -> uiText(R.string.next_period_due_today)
    else -> uiText(R.string.next_period_timing_needs_review)
}

internal fun homeHeroTitle(uiState: HomeUiState): UiText =
    if (uiState.hasCurrentCycle) uiText(R.string.period_started) else formatNextPeriodTitle(uiState.daysUntilNextPeriod)

internal fun homeHeroSubtitle(uiState: HomeUiState): UiText = when {
    uiState.hasCurrentCycle -> uiText(R.string.log_how_today_feels)
    uiState.overdueDays > 0 ->
        uiText(R.string.expected_date_with_overdue, uiState.nextPeriodDateText, formatOverdue(uiState.overdueDays))
    uiState.supportsRange -> uiText(R.string.expected_date, uiState.nextPeriodDateText)
    // No range is supportable, so the point estimate is not presented as
    // the expected date at all.
    else -> uiText(R.string.date_not_enough_history_for_a_range, uiState.nextPeriodDateText)
}

internal fun formatCycleProgressLabel(
    cycleDay: Int?,
    cycleLength: Int,
    confidenceLabel: String,
): UiText {
    val dayText = cycleDay?.toString() ?: "--"
    return if (confidenceLabel.isBlank()) {
        uiText(R.string.day_slash_total, dayText, cycleLength)
    } else {
        // Sentence shape in one resource: the day label and the
        // confidence suffix are parts of a single statement, not
        // separately translatable fragments.
        uiText(R.string.day_slash_total_with_confidence, dayText, cycleLength, confidenceLabel)
    }
}

/**
 * Renders the engine's window, or an explicit statement that there isn't
 * one.
 *
 * The v1 UI had a single date and no way to express doubt, so it printed
 * that date as though it were exact. An absent window is reported as
 * absent rather than silently collapsed to a point estimate.
 */
internal fun formatNextPeriodRange(
    range: DateRange?,
    supportsRange: Boolean,
    halfWidthDays: Int?,
): UiText? = when {
    range != null -> {
        val start = range.start.format(dashboardDateFormatter)
        val end = range.endInclusive.format(dashboardDateFormatter)
        if (halfWidthDays != null && halfWidthDays > 0) {
            uiText(R.string.date_range_with_halfwidth, start, end, halfWidthDays)
        } else {
            uiText(R.string.date_range, start, end)
        }
    }

    supportsRange -> null
    else -> uiText(R.string.not_enough_history_yet_for_a_range)
}

internal fun formatOverdue(days: Int): UiText = when {
    days <= 0 -> UiText.Raw("")
    days == 1 -> uiText(R.string.one_day_past_the_expected_date)
    else -> pluralUiText(R.plurals.n_days_past_the_expected_date, days, days)
}

internal fun formatFertileWindow(range: DateRange?): UiText =
    range?.let { uiText(R.string.date_range, it.start.format(dashboardDateFormatter), it.endInclusive.format(dashboardDateFormatter)) }
        ?: uiText(R.string.fertile_window_unavailable)

internal fun formatDashboardDate(date: LocalDate?): UiText =
    date?.let { UiText.Raw(it.format(dashboardDateFormatter)) } ?: uiText(R.string.unavailable)

internal fun progressStateDescription(cycleDay: Int?, cycleLength: Int): UiText =
    if (cycleDay == null) {
        uiText(R.string.cycle_progress_unavailable)
    } else {
        uiText(R.string.day_x_of_y, cycleDay, cycleLength)
    }

internal fun formatCycleRange(record: CycleRecord?): UiText = when {
    record == null -> uiText(R.string.no_logged_period_yet)
    // A null end means the period has not finished yet.
    record.endDate == null ->
        uiText(R.string.date_ongoing, record.startDate.format(dashboardDateFormatter))
    else ->
        uiText(
            R.string.date_range,
            record.startDate.format(dashboardDateFormatter),
            record.endDate!!.format(dashboardDateFormatter),
        )
}

private fun buildCycleModeLabel(
    currentCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
): UiText = when {
    currentCycle != null -> uiText(R.string.currently_logged)
    prediction != null -> uiText(R.string.based_on_your_history)
    else -> uiText(R.string.waiting_for_prediction)
}

private fun buildCyclePhaseLabel(
    cycleDay: Int?,
    cycleLength: Int,
): UiText {
    if (cycleDay == null) return uiText(R.string.empty_label)
    val cycleProgress = cycleDay.toFloat() / cycleLength.toFloat()
    return when {
        cycleProgress < 0.15f -> uiText(R.string.early_cycle)
        cycleProgress < 0.40f -> uiText(R.string.mid_cycle)
        cycleProgress < 0.60f -> uiText(R.string.fertile_window)
        cycleProgress < 0.85f -> uiText(R.string.late_cycle)
        else -> uiText(R.string.approaching_next_period)
    }
}

internal fun buildHomeCyclePhaseLabel(
    currentCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
    cycleLength: Int,
): UiText = if (currentCycle != null) {
    uiText(R.string.period_days)
} else {
    buildCyclePhaseLabel(prediction?.cycleDay, cycleLength)
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
        confidenceLabel.ifBlank { "From your setup" }.let { UiText.Raw(it) }
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
        editor.startDateText.isBlank() -> uiText(R.string.start_date_required)
        startDate == null -> uiText(R.string.use_iso_for_start_date)
        startDate.isAfter(today) -> uiText(R.string.start_date_cannot_be_future)
        else -> null
    }
    val endDateError = when {
        editor.endDateText.isBlank() -> uiText(R.string.end_date_required)
        endDate == null -> uiText(R.string.use_iso_for_end_date)
        endDate.isAfter(today) -> uiText(R.string.end_date_cannot_be_future)
        else -> null
    }
    val cycleLengthError = when {
        editor.cycleLengthText.isBlank() -> uiText(R.string.cycle_length_required)
        cycleLength == null -> uiText(R.string.cycle_length_whole_number)
        cycleLength !in 20..45 -> uiText(R.string.cycle_length_range_error)
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
        error = summaryError?.let { UiText.Raw(it) },
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
    // Resolved once at the composable boundary so the progress bar's
    // accessibility state description stays locale-correct without
    // calling stringResource inside the modifier lambda.
    val progressStateDescriptionText = progressStateDescription(uiState.cycleDay, uiState.cycleLength).resolve()
    val heroTitle = homeHeroTitle(uiState).resolve()
    val heroSubtitle = homeHeroSubtitle(uiState).resolve()
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
            .padding(horizontal = CycleCareSpace.space16)
            .testTag(HomeTags.DASHBOARD),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
        contentPadding = PaddingValues(top = CycleCareSpace.space16, bottom = CycleCareSpace.space28),
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
                contentPadding = PaddingValues(horizontal = CycleCareSpace.space20, vertical = CycleCareSpace.space18),
                gradientColors = heroGradientColors,
                stackTrailingOnCompact = true,
                supportingContent = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        MetricPill(
                            text = "Day ${uiState.cycleDay ?: "--"} of ${uiState.cycleLength}",
                            tone = MetricPillTone.Inverse,
                        )
                        MetricPill(
                            text = uiState.cycleModeLabel.resolve(),
                            tone = MetricPillTone.Inverse,
                        )
                        if (uiState.cyclePhaseLabel.resolve().isNotBlank()) {
                            MetricPill(
                                text = uiState.cyclePhaseLabel.resolve(),
                                tone = MetricPillTone.Inverse,
                            )
                        }
                        // The spread belongs beside the estimate. Without
                        // it the hero reads as an exact date, which is the
                        // specific overstatement this engine was built to
                        // remove.
                        if (!uiState.hasCurrentCycle &&
                            uiState.nextPeriodRangeText != null
                        ) {
                            MetricPill(
                                text = uiState.nextPeriodRangeText.resolve(),
                                tone = MetricPillTone.Inverse,
                                modifier = Modifier.testTag(HomeTags.NEXT_PERIOD_RANGE),
                            )
                        }
                    }
                    Text(
                        text = uiState.cycleSourceSummary.resolve(),
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
                            // Material's default button height is 40dp,
                            // below the 48dp minimum touch target.
                            .heightIn(min = CycleCareSpace.minTouchTarget)
                            .testTag(HomeTags.CYCLE_STATUS_CTA),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.view_cycle_details))
                    }
                },
                trailing = {
                    OutlinedButton(
                        onClick = onOpenEditCycle,
                        modifier = Modifier
                            .defaultMinSize(minHeight = CycleCareSpace.minTouchTarget)
                            .testTag(HomeTags.EDIT_CYCLE_BUTTON),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.edit))
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
                fertileWindowText = uiState.fertileWindowText.resolve(),
                ovulationEstimateText = uiState.ovulationEstimateText.resolve(),
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
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
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
                        stringResource(R.string.your_data_stays_private_on_this_device),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.message?.let { message ->
                        Text(
                            text = message.resolve(),
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
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard),
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
                    text = stringResource(R.string.cycle_reminders_and_insights_in_one_local_view),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.cycle_reminders_and_insights_in_one_local_view),
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
        modifier = Modifier
            .sizeIn(minWidth = CycleCareSpace.minTouchTarget, minHeight = CycleCareSpace.minTouchTarget)
            .testTag(HomeTags.REMINDER_CENTER_BUTTON),
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
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
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
                    value = uiState.variabilityText.resolve(),
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
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
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
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    MetricCard(
                        label = "Variability",
                        value = uiState.variabilityText.resolve(),
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
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4)) {
                    Text(
                        stringResource(R.string.ovulation_estimate),
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
                Text(stringResource(R.string.view_fertility_details))
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
    val isDailyCheckIn = teaser.isActionable
    DashboardSectionCard(
        title = teaser.title.resolve(),
        subtitle = teaser.detail.resolve(),
        modifier = Modifier.testTag(HomeTags.REMINDER_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
            ) {
                if (unreadCount > 0) {
                    DashboardPill(
                        text = pluralStringResource(R.plurals.n_unread, unreadCount, unreadCount),
                        emphasized = true,
                    )
                }
                DashboardPill(text = teaser.supporting.resolve())
            }
            if (isDailyCheckIn) {
                Button(
                    onClick = onTrackToday,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.check_in_now))
                }
            } else {
                FilledTonalButton(
                    onClick = onOpenReminderCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HomeTags.REMINDER_DETAIL_CTA),
                ) {
                    Text(stringResource(R.string.view_details))
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
        title = teaser.title.resolve(),
        subtitle = teaser.detail.resolve(),
        modifier = Modifier.testTag(HomeTags.INSIGHTS_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            if (teaser.trendPoints.isNotEmpty()) {
                HomeMiniTrendStrip(points = teaser.trendPoints)
            }
            DashboardPill(
                text = teaser.supporting.resolve(),
                emphasized = teaser.trendPoints.isEmpty(),
            )
            OutlinedButton(
                onClick = onOpenInsights,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.view_insights))
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
        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
    ) {
        points.takeLast(6).forEach { point ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6),
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
                            .padding(CycleCareSpace.space4)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
                    )
                }
                Text(
                    point.label.resolve(),
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
        modifier = modifier.padding(CycleCareSpace.space16),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
        contentPadding = PaddingValues(bottom = CycleCareSpace.space24),
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.back_to_dashboard))
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
    // Resolved once at the composable boundary so the progress bar's
    // accessibility state description stays locale-correct without
    // calling stringResource inside the modifier lambda.
    val progressStateDescriptionText = progressStateDescription(uiState.cycleDay, uiState.cycleLength).resolve()
    HomeDetailShell(
        title = formatNextPeriodTitle(uiState.daysUntilNextPeriod).resolve(),
        subtitle = stringResource(
            R.string.expected_date_with_overdue,
            uiState.nextPeriodDateText.resolve(),
            formatCycleProgressLabel(uiState.cycleDay, uiState.cycleLength, uiState.confidenceLabel).resolve(),
        ),
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
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                Button(
                    onClick = onOpenEditCycle,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.edit_cycle))
                }
                OutlinedButton(
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.open_calendar))
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    MetricCard(
                        label = stringResource(R.string.avg_cycle),
                        value = stringResource(R.string.n_days, uiState.cycleLength),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = stringResource(R.string.avg_period),
                        value = stringResource(R.string.n_days, uiState.averagePeriodLength),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    MetricCard(
                        label = stringResource(R.string.variability),
                        value = uiState.variabilityText.resolve(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    )
                    MetricCard(
                        label = stringResource(R.string.next_period),
                        value = uiState.nextPeriodDateText.resolve(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                    )
                }
            }
            DashboardSectionCard(
                title = stringResource(R.string.current_status),
                subtitle = uiState.latestCycleSummary.resolve(),
                eyebrow = uiState.cycleModeLabel.resolve(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .semantics {
                                stateDescription = progressStateDescriptionText
                            },
                    )
                    Text(
                        uiState.cycleSourceSummary.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DashboardSectionCard(
                title = stringResource(R.string.logged_vs_predicted),
                subtitle = stringResource(R.string.logged_vs_predicted_subtitle),
                eyebrow = stringResource(R.string.interpretation),
                accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10)) {
                    DashboardKeyValueRow(
                        label = stringResource(R.string.fertile_window),
                        value = uiState.fertileWindowText.resolve(),
                    )
                    DashboardKeyValueRow(
                        label = stringResource(R.string.ovulation_estimate),
                        value = uiState.ovulationEstimateText.resolve(),
                    )
                }
            }
            Text(
                text = uiState.cycleSourceSummary.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            val containerModifier = Modifier
                .background(
                    Brush.linearGradient(
                        heroGradientColors,
                    ),
                )
                .padding(CycleCareSpace.space16)

            if (useStackedLayout) {
                Column(
                    modifier = containerModifier,
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    CycleStatusHeroText(
                        uiState = uiState,
                        progress = progress,
                    )
                    PeriodHeroScene(
                        mode = heroMode,
                        progress = progress,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                    )
                }
            } else {
                Row(
                    modifier = containerModifier,
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                    ) {
                        CycleStatusHeroText(
                            uiState = uiState,
                            progress = progress,
                        )
                    }
                    PeriodHeroScene(
                        mode = heroMode,
                        progress = progress,
                        compact = true,
                        modifier = Modifier.size(136.dp),
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
    val progressStateDescriptionText = progressStateDescription(uiState.cycleDay, uiState.cycleLength).resolve()
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
        Text(
            stringResource(R.string.cycle_detail),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
        )
        Text(
            text = homeHeroTitle(uiState).resolve(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = homeHeroSubtitle(uiState).resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space6),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6),
        ) {
            MetricPill(
                text = uiState.cycleModeLabel.resolve(),
                tone = MetricPillTone.Inverse,
            )
            if (uiState.confidenceLabel.isNotBlank()) {
                MetricPill(
                    text = uiState.confidenceLabel,
                    tone = MetricPillTone.Inverse,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6)) {
            Text(
                stringResource(R.string.cycle_progress),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics {
                        stateDescription = progressStateDescriptionText
                    },
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
            )
        }
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
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                MetricCard(
                    label = "Fertile window",
                    value = uiState.fertileWindowText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                )
                MetricCard(
                    label = stringResource(R.string.ovulation),
                    value = uiState.ovulationEstimateText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                )
            }
            DashboardSectionCard(
                title = stringResource(R.string.how_this_estimate_is_built),
                subtitle = uiState.cycleSourceSummary.resolve(),
                eyebrow = stringResource(R.string.from_your_cycle_history),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10)) {
                    Text(
                        stringResource(R.string.fertility_timing_is_estimated_from_your_saved_baseline_and_r),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DashboardKeyValueRow(
                        label = stringResource(R.string.current_window),
                        value = uiState.fertileWindowText.resolve(),
                    )
                    DashboardKeyValueRow(
                        label = stringResource(R.string.ovulation_date),
                        value = uiState.ovulationEstimateText.resolve(),
                    )
                }
            }
            Button(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_calendar))
            }
            OutlinedButton(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_insights))
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
        contentPadding = PaddingValues(horizontal = CycleCareSpace.space16, vertical = CycleCareSpace.space18),
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space2),
        ) {
            Text(action.label.resolve(), style = MaterialTheme.typography.titleMedium)
            Text(action.description.resolve(), style = MaterialTheme.typography.bodySmall)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CycleCareSpace.space24, vertical = CycleCareSpace.space8),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
        ) {
            Text(
                text = if (editor.editingId == null) {
                    stringResource(R.string.edit_cycle)
                } else {
                    stringResource(R.string.edit_current_cycle)
                },
                style = MaterialTheme.typography.headlineLarge,
            )

            if (!editor.isExpanded && editor.editingId == null) {
                Text(
                    stringResource(R.string.start_a_new_period_today_or_expand_the_details_if_you_need_t),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(CycleCareSpace.space16),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        Text(
                            stringResource(R.string.period_started_today),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.use_your_saved_baseline_and_period_length_for_a_quick_local_),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onQuickStartPeriod,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.period_started_today))
                }
                OutlinedButton(
                    onClick = onExpand,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.edit_details))
                }
            } else {
                Text(
                    text = if (hasCurrentCycle) {
                        stringResource(R.string.update_the_cycle_entry_covering_today_changes_stay_local_and)
                    } else {
                        stringResource(R.string.adjust_the_period_dates_cycle_length_and_notes_before_saving)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = editor.startDateText,
                    onValueChange = onStartDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.start_date_label)) },
                    singleLine = true,
                    isError = editor.startDateError != null,
                    supportingText = {
                        editor.startDateError?.let { error ->
                            Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = editor.endDateText,
                    onValueChange = onEndDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_date_label)) },
                    singleLine = true,
                    isError = editor.endDateError != null,
                    supportingText = {
                        editor.endDateError?.let { error ->
                            Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = editor.cycleLengthText,
                    onValueChange = onCycleLengthChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cycle_length_label)) },
                    singleLine = true,
                    isError = editor.cycleLengthError != null,
                    supportingText = {
                        editor.cycleLengthError?.let { error ->
                            Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = editor.notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text(stringResource(R.string.notes_label)) },
                )
                editor.error?.let { error ->
                    Text(
                        text = error.resolve(),
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
                            stringResource(R.string.saving)
                        } else if (editor.editingId == null) {
                            stringResource(R.string.save_cycle)
                        } else {
                            stringResource(R.string.save_changes)
                        },
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

private fun HomeQuickAction.icon(): ImageVector = when (type) {
    HomeQuickActionType.LogSymptoms -> Icons.Rounded.Edit
    HomeQuickActionType.OpenCalendar -> Icons.Rounded.DateRange
    HomeQuickActionType.ViewInsights -> Icons.Rounded.LocalFlorist
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
    nextPeriodDateText = UiText.Raw("Apr 9"),
    cycleDay = 6,
    cycleLength = 28,
    averagePeriodLength = 5,
    variabilityText = UiText.Raw("26-29d range"),
    fertileWindowText = UiText.Raw("Mar 21 - Mar 26"),
    ovulationEstimateText = UiText.Raw("Mar 25"),
    confidenceLabel = "",
    hasCurrentCycle = false,
    cycleModeLabel = uiText(R.string.based_on_your_history),
    cyclePhaseLabel = uiText(R.string.early_cycle),
    cycleSourceSummary = uiText(R.string.predictions_combine_latest_cycle_with_baseline),
    latestCycleSummary = uiText(R.string.most_recent_logged_period, "Feb 12 - Feb 16"),
    reminderTeaser = HomeReminderTeaser(
        title = uiText(R.string.daily_check_in),
        detail = uiText(R.string.take_a_moment_to_log_mood_energy_medication),
        supporting = uiText(R.string.next_up_at_time, "Mar 18 • 8:00 PM"),
    ),
    insightTeaser = HomeInsightTeaser(
        title = uiText(R.string.recent_rhythm_snapshot),
        detail = uiText(R.string.recent_cycle_lengths_stay_close),
        supporting = uiText(R.string.based_on_your_four_logged_cycles),
        trendPoints = listOf(
            HomeTrendPoint(uiText(R.string.cycle_point_label, 1), 27),
            HomeTrendPoint(uiText(R.string.cycle_point_label, 2), 28),
            HomeTrendPoint(uiText(R.string.cycle_point_label, 3), 29),
            HomeTrendPoint(uiText(R.string.cycle_point_label, 4), 28),
        ),
    ),
    cyclesLoggedCount = 4,
    cyclesNeededForInsights = 3,
)
