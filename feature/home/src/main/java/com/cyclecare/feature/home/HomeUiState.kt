@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.text.pluralUiText
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.runtime.getValue
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

private val dashboardDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
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

internal fun buildHomeCyclePhaseLabel(
    currentCycle: CycleRecord?,
    prediction: PredictionSnapshot?,
    cycleLength: Int,
): UiText = if (currentCycle != null) {
    uiText(R.string.period_days)
} else {
    buildCyclePhaseLabel(prediction?.cycleDay, cycleLength)
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

