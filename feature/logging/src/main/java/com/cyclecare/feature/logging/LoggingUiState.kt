@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LoggingUiState(
    val dateLabel: String = buildLogDateLabel(LocalDate.now()),
    val cycleProgressLabel: UiText? = null,
    /**
     * The phase as a value, not as a display string. Controls which
     * copy the hero insight shows; see [CyclePhase].
     */
    val cyclePhase: CyclePhase? = null,
    /**
     * Cycle day as a number. Previously recovered by parsing
     * `cycleProgressLabel`, which broke under any translation.
     */
    val cycleDay: Int? = null,
    val heroMode: PeriodHeroMode = PeriodHeroMode.Predicted,
    val heroProgress: Float = 0f,
    val privacyNote: UiText = uiText(R.string.privacy_note_saved_locally),
    val existingEntryId: String? = null,
    val hasExistingEntry: Boolean = false,
    val entryStatusLabel: UiText? = null,
    val saveCtaLabel: UiText = uiText(R.string.save_cta_save),
    val saveMessage: UiText? = null,
    val completedSections: Int = 0,
    val totalSections: Int = 4,
    // Null means not tracked, which is a first-class answer and not a
    // missing value. It must never be coerced into a real selection.
    val mood: Mood? = null,
    val painLevel: PainLevel? = null,
    val energyLevel: EnergyLevel? = null,
    val dischargeType: DischargeType? = null,
    val medication: String = "",
    val notes: String = "",
    val isDetailsExpanded: Boolean = false,
    val notesRemaining: Int = MAX_SYMPTOM_NOTE_LENGTH,
    val notesError: UiText? = null,
    val isSaveEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val streakLabel: UiText = pluralUiText(R.plurals.streak_days, 0, 0),
    val cycleScore: Int = 80,
    val hasYesterdayEntry: Boolean = false,
)

internal data class LoggingEditorState(
    val hydrationKey: String = "",
    val existingEntryId: String? = null,
    // Null means not tracked, which is a first-class answer and not a
    // missing value. It must never be coerced into a real selection.
    val mood: Mood? = null,
    val painLevel: PainLevel? = null,
    val energyLevel: EnergyLevel? = null,
    val dischargeType: DischargeType? = null,
    val medication: String = "",
    val notes: String = "",
    val isDetailsExpanded: Boolean = false,
)

internal fun buildLogDateLabel(
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): String = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale))

internal fun buildCycleProgressLabel(
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
): UiText? = if (profile != null && prediction != null) {
    uiText(R.string.cycle_progress_day, prediction.cycleDay, profile.averageCycleLength)
} else {
    null
}

internal object LoggingTags {
    const val LIST = "logging_list"
    const val HeroCard = "logging_hero_card"
    const val SaveBar = "logging_save_bar"
    fun mood(mood: Mood): String = "logging_mood_${mood.name}"
    fun notTrackedChip(): String = "logging_not_tracked"
    const val NotesField = "logging_notes_field"
    const val NotesError = "logging_notes_error"
}

internal fun buildCycleProgressRatio(
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
): Float = if (profile != null && prediction != null) {
    (prediction.cycleDay.toFloat() / profile.averageCycleLength.toFloat()).coerceIn(0f, 1f)
} else {
    0f
}

internal fun buildLogHeroMode(
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
    cycles: List<CycleRecord> = emptyList(),
    today: LocalDate = LocalDate.now(),
): PeriodHeroMode {
    val hasCurrentUserCycle = cycles.any { cycle ->
        cycle.source == CycleSource.User &&
            !today.isBefore(cycle.startDate) &&
            !today.isAfter(cycle.endDate)
    }
    return if (
        hasCurrentUserCycle ||
        (
            profile != null &&
                prediction != null &&
                prediction.cycleDay <= profile.periodLength
            )
    ) {
        PeriodHeroMode.Active
    } else {
        PeriodHeroMode.Predicted
    }
}

internal fun loggingHeroEyebrow(uiState: LoggingUiState): UiText =
    if (uiState.heroMode == PeriodHeroMode.Active) {
        uiText(R.string.hero_eyebrow_period_check_in)
    } else {
        uiText(R.string.hero_eyebrow_daily_check_in)
    }

/**
 * Which part of the cycle the user is in.
 *
 * Previously this existed only as the English display string, and
 * `buildSmartLoggingInsight` compared that string for equality to pick
 * its copy. Translating it would have made every branch miss silently:
 * no compile error, no failing test, just a permanently generic hero
 * insight. The decision now lives in the value rather than in the copy.
 */
enum class CyclePhase {
    PERIOD_DAYS,
    EARLY,
    MID,
    FERTILE_WINDOW,
    LATE,
    APPROACHING_PERIOD,
}

private fun resolveCyclePhase(
    cycleDay: Int?,
    cycleLength: Int,
    heroMode: PeriodHeroMode,
): CyclePhase? {
    if (heroMode == PeriodHeroMode.Active) return CyclePhase.PERIOD_DAYS
    if (cycleDay == null) return null
    val cycleProgress = cycleDay.toFloat() / cycleLength.toFloat()
    return when {
        cycleProgress < 0.15f -> CyclePhase.EARLY
        cycleProgress < 0.40f -> CyclePhase.MID
        cycleProgress < 0.60f -> CyclePhase.FERTILE_WINDOW
        cycleProgress < 0.85f -> CyclePhase.LATE
        else -> CyclePhase.APPROACHING_PERIOD
    }
}

internal fun CyclePhase.displayKey(): UiText = when (this) {
    CyclePhase.PERIOD_DAYS -> uiText(R.string.cycle_phase_period_days)
    CyclePhase.EARLY -> uiText(R.string.cycle_phase_early_cycle)
    CyclePhase.MID -> uiText(R.string.cycle_phase_mid_cycle)
    CyclePhase.FERTILE_WINDOW -> uiText(R.string.cycle_phase_fertile_window)
    CyclePhase.LATE -> uiText(R.string.cycle_phase_late_cycle)
    CyclePhase.APPROACHING_PERIOD -> uiText(R.string.cycle_phase_approaching_period)
}

internal fun buildEditorState(
    entry: SymptomLog?,
    hydrationKey: String,
): LoggingEditorState = if (entry == null) {
    LoggingEditorState(hydrationKey = hydrationKey)
} else {
    LoggingEditorState(
        hydrationKey = hydrationKey,
        existingEntryId = entry.id,
        mood = entry.mood,
        painLevel = entry.painLevel,
        energyLevel = entry.energyLevel,
        dischargeType = entry.dischargeType,
        medication = entry.medication.orEmpty(),
        notes = entry.notes.orEmpty(),
        isDetailsExpanded = !entry.medication.isNullOrBlank() || !entry.notes.isNullOrBlank(),
    )
}

/**
 * Whether the editor holds content that differs from what is saved.
 *
 * The comparison is against the stored entry rather than against a snapshot
 * taken when the editor was first opened. A snapshot would be wrong across
 * a ViewModel recreation, because the restored editor would be compared
 * against itself and no draft would ever be detected. The stored entry is
 * the source of truth for what has been saved, so comparing against it
 * gives the same answer every time.
 *
 * `isDetailsExpanded` is deliberately excluded: it is a presentation flag,
 * not content, and expanding the details section is not a draft.
 */
internal fun LoggingEditorState.hasUnsavedContentComparedTo(entry: SymptomLog?): Boolean {
    if (entry == null) {
        return mood != null ||
            painLevel != null ||
            energyLevel != null ||
            dischargeType != null ||
            medication.isNotBlank() ||
            notes.isNotBlank()
    }
    return mood != entry.mood ||
        painLevel != entry.painLevel ||
        energyLevel != entry.energyLevel ||
        dischargeType != entry.dischargeType ||
        medication != entry.medication.orEmpty() ||
        notes != entry.notes.orEmpty()
}

internal fun validateSymptomNotes(notes: String): UiText? =
    if (notes.length <= MAX_SYMPTOM_NOTE_LENGTH) {
        null
    } else {
        pluralUiText(
            R.plurals.notes_max_characters,
            MAX_SYMPTOM_NOTE_LENGTH,
            MAX_SYMPTOM_NOTE_LENGTH,
        )
    }

internal fun calculateStreak(logs: List<SymptomLog>, today: LocalDate): Int {
    val loggedDates = logs.map { it.date }.toSet()
    var streak = 0
    var currentDate = today
    if (loggedDates.contains(today)) {
        while (loggedDates.contains(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }
    } else if (loggedDates.contains(today.minusDays(1))) {
        currentDate = today.minusDays(1)
        while (loggedDates.contains(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }
    }
    return streak
}

internal fun calculateCycleScore(completedSections: Int, streak: Int): Int {
    val base = 75
    val sectionsBonus = completedSections * 4
    val streakBonus = (streak * 2).coerceAtMost(10)
    return (base + sectionsBonus + streakBonus).coerceAtMost(100)
}

internal fun buildEnergyStreakInsight(logs: List<SymptomLog>, today: LocalDate): UiText? {
    val targetDays = listOf(today, today.minusDays(1), today.minusDays(2))
    val energyLevels = targetDays.map { date ->
        logs.find { it.date == date }?.energyLevel
    }
    return when {
        energyLevels.all { it == EnergyLevel.Steady } ->
            pluralUiText(R.plurals.energy_steady_days, targetDays.size, targetDays.size)

        energyLevels.all { it == EnergyLevel.High } ->
            pluralUiText(R.plurals.energy_high_days, targetDays.size, targetDays.size)

        energyLevels.all { it == EnergyLevel.Low } ->
            pluralUiText(R.plurals.energy_low_days, targetDays.size, targetDays.size)

        else -> null
    }
}

internal fun buildCompletionInsight(logs: List<SymptomLog>, today: LocalDate): UiText {
    val energyInsight = buildEnergyStreakInsight(logs, today)
    if (energyInsight != null) return energyInsight

    val moodDays = listOf(today, today.minusDays(1))
    val moods = moodDays.map { date -> logs.find { it.date == date }?.mood }
    if (moods.all { it == Mood.Happy }) {
        return pluralUiText(R.plurals.mood_happy_days, moodDays.size, moodDays.size)
    } else if (moods.all { it == Mood.Stable }) {
        return pluralUiText(R.plurals.mood_stable_days, moodDays.size, moodDays.size)
    }

    val painDays = listOf(today, today.minusDays(1), today.minusDays(2))
    val pains = painDays.map { date -> logs.find { it.date == date }?.painLevel }
    if (pains.all { it == PainLevel.None }) {
        return pluralUiText(R.plurals.pain_free_days, painDays.size, painDays.size)
    }

    return uiText(R.string.saved_checkin_complete)
}

internal fun buildLoggingUiState(
    today: LocalDate,
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
    todayEntry: SymptomLog?,
    editor: LoggingEditorState,
    saveMessage: UiText?,
    isSaving: Boolean,
    isSaved: Boolean = false,
    symptoms: List<SymptomLog> = emptyList(),
    cycles: List<CycleRecord> = emptyList(),
): LoggingUiState {
    val cycleLength = profile?.averageCycleLength ?: 28
    val cycleDay = prediction?.cycleDay
    val heroMode = buildLogHeroMode(profile, prediction, cycles, today)

    val completedSections = listOfNotNull(
        editor.mood.takeIf { it != Mood.Stable },
        editor.painLevel.takeIf { it != PainLevel.None },
        editor.energyLevel.takeIf { it != EnergyLevel.Steady },
        editor.dischargeType.takeIf { it != DischargeType.None },
    ).size

    val activeSymptoms = if (symptoms.isEmpty() && todayEntry != null) {
        listOf(todayEntry)
    } else {
        symptoms
    }

    val yesterdayEntry = activeSymptoms.find { it.date == today.minusDays(1) }
    val streak = calculateStreak(activeSymptoms, today)
    val cycleScore = calculateCycleScore(completedSections, streak)
    val streakLabel = if (streak > 0) {
        pluralUiText(R.plurals.streak_days, streak, streak)
    } else {
        uiText(R.string.no_streak_yet)
    }

    val finalSaveMessage = if (isSaved) {
        buildCompletionInsight(activeSymptoms, today)
    } else {
        saveMessage
    }

    return LoggingUiState(
        dateLabel = buildLogDateLabel(today),
        cycleProgressLabel = buildCycleProgressLabel(profile, prediction),
        cyclePhase = resolveCyclePhase(cycleDay, cycleLength, heroMode),
        cycleDay = cycleDay,
        heroMode = heroMode,
        heroProgress = buildCycleProgressRatio(profile, prediction),
        existingEntryId = editor.existingEntryId,
        hasExistingEntry = editor.existingEntryId != null,
        entryStatusLabel = if (todayEntry != null) uiText(R.string.editing_todays_saved_check_in) else null,
        saveCtaLabel = when {
            isSaved -> uiText(R.string.save_cta_edit)
            editor.existingEntryId != null -> uiText(R.string.save_cta_update)
            else -> uiText(R.string.save_cta_save)
        },
        saveMessage = finalSaveMessage,
        completedSections = completedSections,
        totalSections = 4,
        mood = editor.mood,
        painLevel = editor.painLevel,
        energyLevel = editor.energyLevel,
        dischargeType = editor.dischargeType,
        medication = editor.medication,
        notes = editor.notes,
        isDetailsExpanded = editor.isDetailsExpanded,
        notesRemaining = MAX_SYMPTOM_NOTE_LENGTH - editor.notes.length,
        notesError = validateSymptomNotes(editor.notes),
        isSaveEnabled = !isSaving && validateSymptomNotes(editor.notes) == null,
        isSaving = isSaving,
        isSaved = isSaved,
        streakLabel = streakLabel,
        cycleScore = cycleScore,
        hasYesterdayEntry = yesterdayEntry != null,
    )
}

internal fun Mood.displayLabel(): UiText = when (this) {
    Mood.Stable -> uiText(R.string.mood_stable)
    Mood.Happy -> uiText(R.string.mood_happy)
    Mood.Low -> uiText(R.string.mood_low)
    Mood.Irritable -> uiText(R.string.mood_irritable)
    Mood.Anxious -> uiText(R.string.mood_anxious)
}

internal fun PainLevel.displayLabel(): UiText = when (this) {
    PainLevel.None -> uiText(R.string.pain_none)
    PainLevel.Light -> uiText(R.string.pain_light)
    PainLevel.Moderate -> uiText(R.string.pain_moderate)
    PainLevel.Strong -> uiText(R.string.pain_strong)
}

internal fun PainLevel.helperText(): UiText = when (this) {
    PainLevel.None -> uiText(R.string.pain_helper_none)
    PainLevel.Light -> uiText(R.string.pain_helper_light)
    PainLevel.Moderate -> uiText(R.string.pain_helper_moderate)
    PainLevel.Strong -> uiText(R.string.pain_helper_strong)
}

internal fun EnergyLevel.displayLabel(): UiText = when (this) {
    EnergyLevel.Low -> uiText(R.string.energy_low)
    EnergyLevel.Steady -> uiText(R.string.energy_steady)
    EnergyLevel.High -> uiText(R.string.energy_high)
}

internal fun DischargeType.displayLabel(): UiText = when (this) {
    DischargeType.None -> uiText(R.string.discharge_none)
    DischargeType.Dry -> uiText(R.string.discharge_dry)
    DischargeType.Creamy -> uiText(R.string.discharge_creamy)
    DischargeType.Sticky -> uiText(R.string.discharge_sticky)
    DischargeType.EggWhite -> uiText(R.string.discharge_egg_white)
}

internal fun buildSmartLoggingInsight(
    uiState: LoggingUiState,
    // Defaults read the structured values off the state, never the
    // formatted label, so existing call sites stay correct.
    cycleDay: Int? = uiState.cycleDay,
    cyclePhase: CyclePhase? = uiState.cyclePhase,
): UiText {
    // The cycle day used to be parsed back out of the formatted
    // "Day N / M" label. Reordering or translating that label would make
    // toIntOrNull() return null and silently disable the countdown, so
    // it is now carried as data.
    val daysUntilFertile = cycleDay?.let { day ->
        val fertileStart = 12
        if (day < fertileStart) fertileStart - day else null
    }

    return when {
        uiState.heroMode == PeriodHeroMode.Active -> uiText(R.string.hero_insight_active_period)
        cyclePhase == CyclePhase.FERTILE_WINDOW -> uiText(R.string.hero_insight_fertile_window)
        cyclePhase == CyclePhase.EARLY && daysUntilFertile != null ->
            pluralUiText(R.plurals.fertile_window_starts_in_days, daysUntilFertile, daysUntilFertile)
        cyclePhase == CyclePhase.LATE -> uiText(R.string.hero_insight_late_cycle)
        cyclePhase == CyclePhase.MID -> uiText(R.string.hero_insight_mid_cycle)
        cyclePhase == CyclePhase.APPROACHING_PERIOD -> uiText(R.string.hero_insight_approaching_period)
        uiState.hasExistingEntry -> uiText(R.string.hero_insight_existing_entry)
        else -> uiText(R.string.hero_insight_default)
    }
}

