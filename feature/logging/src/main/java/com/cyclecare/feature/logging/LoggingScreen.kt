@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.BottomActionRail
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.FormSectionCard
import com.cyclecare.core.designsystem.MetricPill
import com.cyclecare.core.designsystem.MetricPillTone
import com.cyclecare.core.designsystem.PeriodActiveAccentColor
import com.cyclecare.core.designsystem.PeriodActiveContainerColor
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
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

data class LoggingUiState(
    val dateLabel: String = buildLogDateLabel(LocalDate.now()),
    val cycleProgressLabel: String? = null,
    val cyclePhaseLabel: String = "",
    val heroMode: PeriodHeroMode = PeriodHeroMode.Predicted,
    val heroProgress: Float = 0f,
    val privacyNote: String = "Saved locally on this device by default.",
    val existingEntryId: String? = null,
    val hasExistingEntry: Boolean = false,
    val entryStatusLabel: String? = null,
    val saveCtaLabel: String = "Save today's entry",
    val saveMessage: String? = null,
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
    val notesError: String? = null,
    val isSaveEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val streakLabel: String = "0 day streak!",
    val cycleScore: Int = 80,
    val hasYesterdayEntry: Boolean = false,
)

private data class LoggingContext(
    val profile: UserProfile? = null,
    val cycles: List<CycleRecord> = emptyList(),
    val prediction: PredictionSnapshot? = null,
    val todayEntry: SymptomLog? = null,
    val symptoms: List<SymptomLog> = emptyList(),
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

private val logDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

private object LoggingStateKeys {
    const val HydrationKey = "hydration_key"
    const val ExistingEntryId = "existing_entry_id"
    const val Mood = "mood"
    const val PainLevel = "pain_level"
    const val EnergyLevel = "energy_level"
    const val DischargeType = "discharge_type"
    const val Medication = "medication"
    const val Notes = "notes"
    const val DetailsExpanded = "details_expanded"
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

internal const val MAX_SYMPTOM_NOTE_LENGTH = 500

internal fun buildLogDateLabel(date: LocalDate): String = date.format(logDateFormatter)

internal fun buildCycleProgressLabel(
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
): String? = if (profile != null && prediction != null) {
    "Day ${prediction.cycleDay} / ${profile.averageCycleLength}"
} else {
    null
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

internal fun loggingHeroEyebrow(uiState: LoggingUiState): String =
    if (uiState.heroMode == PeriodHeroMode.Active) "Period check-in" else "Daily check-in"

private fun buildCyclePhaseLabel(
    cycleDay: Int?,
    cycleLength: Int,
    heroMode: PeriodHeroMode,
): String {
    if (heroMode == PeriodHeroMode.Active) return "Period days"
    if (cycleDay == null) return ""
    val cycleProgress = cycleDay.toFloat() / cycleLength.toFloat()
    return when {
        cycleProgress < 0.15f -> "Early cycle"
        cycleProgress < 0.40f -> "Mid cycle"
        cycleProgress < 0.60f -> "Fertile window"
        cycleProgress < 0.85f -> "Late cycle"
        else -> "Approaching period"
    }
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

internal fun validateSymptomNotes(notes: String): String? =
    if (notes.length <= MAX_SYMPTOM_NOTE_LENGTH) {
        null
    } else {
        "Notes can be up to $MAX_SYMPTOM_NOTE_LENGTH characters."
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

internal fun buildEnergyStreakInsight(logs: List<SymptomLog>, today: LocalDate): String? {
    val targetDays = listOf(today, today.minusDays(1), today.minusDays(2))
    val energyLevels = targetDays.map { date ->
        logs.find { it.date == date }?.energyLevel
    }
    return if (energyLevels.all { it == EnergyLevel.Steady }) {
        "Energy steady for 3 days in a row!"
    } else if (energyLevels.all { it == EnergyLevel.High }) {
        "High energy level sustained for 3 days!"
    } else if (energyLevels.all { it == EnergyLevel.Low }) {
        "Energy has been low for 3 days. Get some restful sleep."
    } else {
        null
    }
}

internal fun buildCompletionInsight(logs: List<SymptomLog>, today: LocalDate): String {
    val energyInsight = buildEnergyStreakInsight(logs, today)
    if (energyInsight != null) return energyInsight

    val moodDays = listOf(today, today.minusDays(1))
    val moods = moodDays.map { date -> logs.find { it.date == date }?.mood }
    if (moods.all { it == Mood.Happy }) {
        return "Great to see! You've been happy for 2 days in a row."
    } else if (moods.all { it == Mood.Stable }) {
        return "Feeling balanced: stable mood logged for 2 days."
    }

    val painDays = listOf(today, today.minusDays(1), today.minusDays(2))
    val pains = painDays.map { date -> logs.find { it.date == date }?.painLevel }
    if (pains.all { it == PainLevel.None }) {
        return "Pain-free for 3 days. Your cycle is going smoothly!"
    }

    return "Saved! Check-in complete. Keep up the great habit."
}

internal fun buildLoggingUiState(
    today: LocalDate,
    profile: UserProfile?,
    prediction: PredictionSnapshot?,
    todayEntry: SymptomLog?,
    editor: LoggingEditorState,
    saveMessage: String?,
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
    val streakLabel = if (streak > 0) "$streak day streak!" else "No streak yet"

    val finalSaveMessage = if (isSaved) {
        buildCompletionInsight(activeSymptoms, today)
    } else {
        saveMessage
    }

    return LoggingUiState(
        dateLabel = buildLogDateLabel(today),
        cycleProgressLabel = buildCycleProgressLabel(profile, prediction),
        cyclePhaseLabel = buildCyclePhaseLabel(cycleDay, cycleLength, heroMode),
        heroMode = heroMode,
        heroProgress = buildCycleProgressRatio(profile, prediction),
        existingEntryId = editor.existingEntryId,
        hasExistingEntry = editor.existingEntryId != null,
        entryStatusLabel = if (todayEntry != null) "Editing today's saved check-in" else null,
        saveCtaLabel = when {
            isSaved -> "Edit today's entry"
            editor.existingEntryId != null -> "Update today's entry"
            else -> "Save today's entry"
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

internal fun Mood.displayLabel(): String = when (this) {
    Mood.Stable -> "Stable"
    Mood.Happy -> "Happy"
    Mood.Low -> "Low"
    Mood.Irritable -> "Irritable"
    Mood.Anxious -> "Anxious"
}

internal fun PainLevel.displayLabel(): String = when (this) {
    PainLevel.None -> "None"
    PainLevel.Light -> "Light"
    PainLevel.Moderate -> "Moderate"
    PainLevel.Strong -> "Strong"
}

internal fun PainLevel.helperText(): String = when (this) {
    PainLevel.None -> "No pain today"
    PainLevel.Light -> "Mild discomfort"
    PainLevel.Moderate -> "Noticeable pain"
    PainLevel.Strong -> "Hard to ignore"
}

internal fun EnergyLevel.displayLabel(): String = when (this) {
    EnergyLevel.Low -> "Low"
    EnergyLevel.Steady -> "Steady"
    EnergyLevel.High -> "High"
}

internal fun DischargeType.displayLabel(): String = when (this) {
    DischargeType.None -> "None"
    DischargeType.Dry -> "Dry"
    DischargeType.Creamy -> "Creamy"
    DischargeType.Sticky -> "Sticky"
    DischargeType.EggWhite -> "Egg white"
}

class LoggingViewModel(
    private val symptomRepository: SymptomRepository,
    profileRepository: ProfileRepository,
    cycleRepository: CycleRepository,
    predictionRepository: PredictionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val today = LocalDate.now()
    private val editorFlow = MutableStateFlow(savedStateHandle.toLoggingEditorState())
    private val saveMessageFlow = MutableStateFlow<String?>(null)
    private val isSavingFlow = MutableStateFlow(false)
    private val isSavedFlow = MutableStateFlow(false)

    private val contextFlow: StateFlow<LoggingContext> = combine(
        profileRepository.observeProfile(),
        cycleRepository.observeCycles(),
        predictionRepository.observePrediction(),
        symptomRepository.observeSymptoms(),
    ) { profile, cycles, prediction, symptoms ->
        val todayEntry = symptoms.find { it.date == today }
        LoggingContext(
            profile = profile,
            cycles = cycles,
            prediction = prediction,
            todayEntry = todayEntry,
            symptoms = symptoms,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoggingContext())

    val uiState: StateFlow<LoggingUiState> = combine(
        contextFlow,
        editorFlow,
        saveMessageFlow,
        isSavingFlow,
        isSavedFlow,
    ) { context, editor, saveMessage, isSaving, isSaved ->
        buildLoggingUiState(
            today = today,
            profile = context.profile,
            prediction = context.prediction,
            todayEntry = context.todayEntry,
            editor = editor,
            saveMessage = saveMessage,
            isSaving = isSaving,
            isSaved = isSaved,
            symptoms = context.symptoms,
            cycles = context.cycles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoggingUiState())

    init {
        viewModelScope.launch {
            contextFlow.collect { context ->
                val hydrationKey = context.todayEntry?.id ?: "new:$today"
                if (editorFlow.value.hydrationKey != hydrationKey) {
                    editorFlow.value = buildEditorState(context.todayEntry, hydrationKey).also(
                        savedStateHandle::persistLoggingEditorState,
                    )
                }
            }
        }
    }

    fun applyYesterdayPattern() {
        val yesterday = contextFlow.value.symptoms.find { it.date == today.minusDays(1) }
        if (yesterday != null) {
            updateEditor { current ->
                current.copy(
                    mood = yesterday.mood,
                    painLevel = yesterday.painLevel,
                    energyLevel = yesterday.energyLevel,
                    dischargeType = yesterday.dischargeType,
                )
            }
        } else {
            updateEditor { current ->
                current.copy(
                    mood = null,
                    painLevel = PainLevel.None,
                    energyLevel = EnergyLevel.Steady,
                    dischargeType = DischargeType.None,
                )
            }
        }
    }

    private fun updateEditor(transform: (LoggingEditorState) -> LoggingEditorState) {
        editorFlow.update { current ->
            transform(current).also(savedStateHandle::persistLoggingEditorState)
        }
        saveMessageFlow.value = null
    }

    fun selectMood(mood: Mood?) = updateEditor { it.copy(mood = if (it.mood == mood) null else mood) }

    fun selectPainLevel(painLevel: PainLevel?) = updateEditor { it.copy(painLevel = if (it.painLevel == painLevel) null else painLevel) }

    fun selectEnergy(energyLevel: EnergyLevel?) = updateEditor { it.copy(energyLevel = if (it.energyLevel == energyLevel) null else energyLevel) }

    fun selectDischarge(dischargeType: DischargeType?) = updateEditor { it.copy(dischargeType = if (it.dischargeType == dischargeType) null else dischargeType) }

    fun updateMedication(medication: String) = updateEditor {
        it.copy(
            medication = medication,
            isDetailsExpanded = it.isDetailsExpanded || medication.isNotBlank(),
        )
    }

    fun updateNotes(notes: String) = updateEditor {
        it.copy(
            notes = notes,
            isDetailsExpanded = it.isDetailsExpanded || notes.isNotBlank(),
        )
    }

    fun toggleDetailsExpanded() = updateEditor { it.copy(isDetailsExpanded = !it.isDetailsExpanded) }

    fun save() {
        val state = uiState.value
        if (!state.isSaveEnabled || isSavingFlow.value) return

        isSavingFlow.value = true
        viewModelScope.launch {
            try {
                symptomRepository.upsertSymptom(
                    SymptomLog(
                        id = state.existingEntryId ?: UUID.randomUUID().toString(),
                        date = today,
                        mood = state.mood,
                        painLevel = state.painLevel,
                        energyLevel = state.energyLevel,
                        dischargeType = state.dischargeType,
                        medication = state.medication.ifBlank { null },
                        notes = state.notes.ifBlank { null },
                    ),
                )
                saveMessageFlow.value = "Saved to your local timeline."
                isSavedFlow.value = true
            } finally {
                isSavingFlow.value = false
            }
        }
    }
}

class LoggingViewModelFactory(
    private val symptomRepository: SymptomRepository,
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val predictionRepository: PredictionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        LoggingViewModel(
            symptomRepository = symptomRepository,
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            predictionRepository = predictionRepository,
            savedStateHandle = extras.createSavedStateHandle(),
        ) as T
}

@Composable
fun LoggingRoute(factory: LoggingViewModelFactory) {
    val viewModel: LoggingViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoggingScreen(
        uiState = uiState,
        onMoodSelected = viewModel::selectMood,
        onPainSelected = viewModel::selectPainLevel,
        onEnergySelected = viewModel::selectEnergy,
        onDischargeSelected = viewModel::selectDischarge,
        onMedicationChanged = viewModel::updateMedication,
        onNotesChanged = viewModel::updateNotes,
        onToggleDetailsExpanded = viewModel::toggleDetailsExpanded,
        onSave = viewModel::save,
        onApplyYesterdayPattern = viewModel::applyYesterdayPattern,
    )
}

@Composable
internal fun LoggingScreen(
    uiState: LoggingUiState,
    onMoodSelected: (Mood?) -> Unit,
    onPainSelected: (PainLevel?) -> Unit,
    onEnergySelected: (EnergyLevel?) -> Unit,
    onDischargeSelected: (DischargeType?) -> Unit,
    onMedicationChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onToggleDetailsExpanded: () -> Unit,
    onSave: () -> Unit,
    onApplyYesterdayPattern: () -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            SaveBar(
                saveCtaLabel = uiState.saveCtaLabel,
                saveMessage = uiState.saveMessage,
                enabled = uiState.isSaveEnabled,
                isSaving = uiState.isSaving,
                onSave = onSave,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(LoggingTags.LIST),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LoggingHeroCard(
                    uiState = uiState,
                    onMoodSelected = onMoodSelected,
                    onPainSelected = onPainSelected,
                    onEnergySelected = onEnergySelected,
                    onDischargeSelected = onDischargeSelected,
                    onApplyYesterdayPattern = onApplyYesterdayPattern,
                )
            }
            item {
                SelectionSectionCard(
                    title = "Mood",
                    helperText = "Pick the closest overall feeling.",
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NotTrackedChip(
                            selected = uiState.mood == null,
                            onClick = { onMoodSelected(null) },
                        )
                        Mood.entries.forEach { mood ->
                            SelectionChip(
                                selected = uiState.mood == mood,
                                onClick = { onMoodSelected(mood) },
                                modifier = Modifier.testTag(LoggingTags.mood(mood)),
                                text = mood.displayLabel(),
                            )
                        }
                    }
                }
            }
            item {
                SelectionSectionCard(
                    title = "Pain",
                    helperText = uiState.painLevel?.helperText() ?: "Not tracked",
                    accent = MaterialTheme.colorScheme.surface,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NotTrackedChip(
                            selected = uiState.painLevel == null,
                            onClick = { onPainSelected(null) },
                        )
                        PainLevel.entries.forEach { painLevel ->
                            SelectionChip(
                                selected = uiState.painLevel == painLevel,
                                onClick = { onPainSelected(painLevel) },
                                text = painLevel.displayLabel(),
                            )
                        }
                    }
                }
            }
            item {
                SelectionSectionCard(
                    title = "Energy",
                    helperText = "How steady does your energy feel right now?",
                    accent = MaterialTheme.colorScheme.surface,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NotTrackedChip(
                            selected = uiState.energyLevel == null,
                            onClick = { onEnergySelected(null) },
                        )
                        EnergyLevel.entries.forEach { energyLevel ->
                            SelectionChip(
                                selected = uiState.energyLevel == energyLevel,
                                onClick = { onEnergySelected(energyLevel) },
                                text = energyLevel.displayLabel(),
                            )
                        }
                    }
                }
            }
            item {
                SelectionSectionCard(
                    title = "Body cues",
                    helperText = "Optional cervical fluid observations. Helps estimate fertility timing.",
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NotTrackedChip(
                            selected = uiState.dischargeType == null,
                            onClick = { onDischargeSelected(null) },
                        )
                        DischargeType.entries.forEach { dischargeType ->
                            SelectionChip(
                                selected = uiState.dischargeType == dischargeType,
                                onClick = { onDischargeSelected(dischargeType) },
                                text = dischargeType.displayLabel(),
                            )
                        }
                    }
                }
            }
            item {
                DetailsCard(
                    uiState = uiState,
                    onMedicationChanged = onMedicationChanged,
                    onNotesChanged = onNotesChanged,
                    onToggleExpanded = onToggleDetailsExpanded,
                )
            }
        }
    }
}

@Composable
private fun LoggingHeroCard(
    uiState: LoggingUiState,
    onMoodSelected: (Mood?) -> Unit,
    onPainSelected: (PainLevel?) -> Unit,
    onEnergySelected: (EnergyLevel?) -> Unit,
    onDischargeSelected: (DischargeType?) -> Unit,
    onApplyYesterdayPattern: () -> Unit,
) {
    val isActivePeriod = uiState.heroMode == PeriodHeroMode.Active
    CycleCarePanel(
        modifier = Modifier
            .testTag(LoggingTags.HeroCard),
        containerColor = if (isActivePeriod) {
            PeriodActiveContainerColor()
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoggingHeroText(uiState = uiState)
            if (uiState.cyclePhaseLabel.isNotBlank()) {
                val smartInsight = buildSmartLoggingInsight(uiState)
                if (smartInsight.isNotBlank()) {
                    Text(
                        text = smartInsight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActivePeriod) {
                            PeriodActiveAccentColor()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (uiState.hasYesterdayEntry) {
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Repeat yesterday's pattern?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onApplyYesterdayPattern,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Same as yesterday ✓",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isActivePeriod) PeriodActiveAccentColor() else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

internal fun buildSmartLoggingInsight(uiState: LoggingUiState): String {
    val cyclePhase = uiState.cyclePhaseLabel
    val daysUntilFertile = uiState.cycleProgressLabel?.let { label ->
        label.substringAfter("Day ").substringBefore(" /").toIntOrNull()?.let { cycleDay ->
            val fertileStart = 12
            if (cycleDay < fertileStart) {
                fertileStart - cycleDay
            } else null
        }
    }

    return when {
        uiState.heroMode == PeriodHeroMode.Active -> "Take a moment to log today's symptoms."
        cyclePhase == "Fertile window" -> "You're in your fertile window. Logging symptoms helps track patterns."
        cyclePhase == "Early cycle" && daysUntilFertile != null -> "Fertile window starts in $daysUntilFertile days."
        cyclePhase == "Late cycle" -> "Period expected soon. Logging symptoms helps track regularity."
        cyclePhase == "Mid cycle" -> "Mid cycle phase. Fertile window approaching in a few days."
        cyclePhase == "Approaching period" -> "Period expected soon. Note any symptoms you notice."
        uiState.hasExistingEntry -> "Your entry for today is saved. Update anytime."
        else -> "Logging symptoms helps improve your predictions over time."
    }
}

@Composable
private fun LoggingHeroText(
    uiState: LoggingUiState,
) {
    val completionProgress = (
        uiState.completedSections.toFloat() / uiState.totalSections.coerceAtLeast(1).toFloat()
    ).coerceIn(0f, 1f)
    val isActivePeriod = uiState.heroMode == PeriodHeroMode.Active
    val accentColor = if (isActivePeriod) PeriodActiveAccentColor() else MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = loggingHeroEyebrow(uiState),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Log today",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = uiState.dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MetricPill(
                    text = "${uiState.streakLabel} • 8s check-in",
                    tone = MetricPillTone.Neutral,
                )
            }
            if (uiState.completedSections > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${uiState.completedSections}/${uiState.totalSections}",
                            style = MaterialTheme.typography.titleLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "done",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Entry progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${uiState.completedSections}/${uiState.totalSections}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                )
            }
            LinearProgressIndicator(
                progress = { completionProgress },
                modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.cycleProgressLabel?.let { label ->
                MetricPill(text = label)
            }
            if (uiState.cyclePhaseLabel.isNotBlank()) {
                MetricPill(
                    text = uiState.cyclePhaseLabel,
                    tone = MetricPillTone.Highlight,
                )
            }
            MetricPill(
                text = "Score: ${uiState.cycleScore}",
                tone = MetricPillTone.Highlight,
            )
            uiState.entryStatusLabel?.let { label ->
                MetricPill(
                    text = label,
                    tone = MetricPillTone.Highlight,
                )
            }
        }
        Text(
            text = uiState.privacyNote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionSectionCard(
    title: String,
    helperText: String,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    FormSectionCard(
        title = title,
        helperText = helperText,
        accent = accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailsCard(
    uiState: LoggingUiState,
    onMedicationChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    FormSectionCard(
        title = "Add details",
        helperText = "Medication and notes are optional for a quick check-in.",
        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill(
                    text = if (uiState.isDetailsExpanded) "Expanded" else "Optional",
                    tone = if (uiState.isDetailsExpanded) MetricPillTone.Highlight else MetricPillTone.Neutral,
                )
                TextButton(onClick = onToggleExpanded) {
                    Text(if (uiState.isDetailsExpanded) "Hide details" else "Expand")
                }
            }
            if (uiState.isDetailsExpanded) {
                OutlinedTextField(
                    value = uiState.medication,
                    onValueChange = onMedicationChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Medication") },
                )
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = uiState.notesError
                                ?: "${MAX_SYMPTOM_NOTE_LENGTH - uiState.notesRemaining} / $MAX_SYMPTOM_NOTE_LENGTH"
                        }
                        .testTag(LoggingTags.NotesField),
                    minLines = 3,
                    label = { Text("Notes") },
                    isError = uiState.notesError != null,
                    supportingText = {
                        Text(
                            uiState.notesError ?: "${MAX_SYMPTOM_NOTE_LENGTH - uiState.notesRemaining} / $MAX_SYMPTOM_NOTE_LENGTH",
                            modifier = Modifier.testTag(LoggingTags.NotesError),
                            color = uiState.notesError?.let { MaterialTheme.colorScheme.error }
                                ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            } else {
                OutlinedButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add medication or notes")
                }
            }
        }
    }
}

/**
 * Explicit "not tracked" option, offered as a real choice alongside the
 * tracked values so a skipped field is visibly a decision rather than an
 * accidental omission.
 */
@Composable
private fun NotTrackedChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionChip(
        text = "Not tracked",
        selected = selected,
        onClick = onClick,
        modifier = modifier.testTag(LoggingTags.notTrackedChip()),
    )
}

@Composable
private fun SelectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.01f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "ChipScale"
    )

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onClick,
        selected = selected,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(99.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (selected) 0.dp else 1.dp,
            color = borderColor
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SaveBar(
    saveCtaLabel: String,
    saveMessage: String?,
    enabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    BottomActionRail(
        modifier = Modifier.testTag(LoggingTags.SaveBar),
        message = saveMessage,
        actionLabel = saveCtaLabel,
        enabled = enabled,
        isLoading = isSaving,
        onAction = onSave,
    )
}

internal fun SavedStateHandle.toLoggingEditorState(): LoggingEditorState =
    LoggingEditorState(
        hydrationKey = get<String>(LoggingStateKeys.HydrationKey).orEmpty(),
        existingEntryId = get<String>(LoggingStateKeys.ExistingEntryId),
        mood = get<String>(LoggingStateKeys.Mood)?.let(Mood::valueOf) ?: Mood.Stable,
        painLevel = get<String>(LoggingStateKeys.PainLevel)?.let(PainLevel::valueOf) ?: PainLevel.None,
        energyLevel = get<String>(LoggingStateKeys.EnergyLevel)?.let(EnergyLevel::valueOf) ?: EnergyLevel.Steady,
        dischargeType = get<String>(LoggingStateKeys.DischargeType)?.let(DischargeType::valueOf) ?: DischargeType.None,
        medication = get<String>(LoggingStateKeys.Medication).orEmpty(),
        notes = get<String>(LoggingStateKeys.Notes).orEmpty(),
        isDetailsExpanded = get<Boolean>(LoggingStateKeys.DetailsExpanded) ?: false,
    )

internal fun SavedStateHandle.persistLoggingEditorState(state: LoggingEditorState) {
    this[LoggingStateKeys.HydrationKey] = state.hydrationKey
    this[LoggingStateKeys.ExistingEntryId] = state.existingEntryId
    // Persisted as the constant name, or absent when not tracked.
    // Round-tripping null is what preserves an untracked field across
    // process death instead of restoring a fabricated default.
    this[LoggingStateKeys.Mood] = state.mood?.name
    this[LoggingStateKeys.PainLevel] = state.painLevel?.name
    this[LoggingStateKeys.EnergyLevel] = state.energyLevel?.name
    this[LoggingStateKeys.DischargeType] = state.dischargeType?.name
    this[LoggingStateKeys.Medication] = state.medication
    this[LoggingStateKeys.Notes] = state.notes
    this[LoggingStateKeys.DetailsExpanded] = state.isDetailsExpanded
}

@ScreenDebugPreviews
@Composable
private fun LoggingScreenPreview() {
    CycleCareTheme {
        LoggingScreen(
            uiState = previewLoggingUiState(),
            onMoodSelected = {},
            onPainSelected = {},
            onEnergySelected = {},
            onDischargeSelected = {},
            onMedicationChanged = {},
            onNotesChanged = {},
            onToggleDetailsExpanded = {},
            onSave = {},
            onApplyYesterdayPattern = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun LoggingScreenEditingPreview() {
    CycleCareTheme {
        LoggingScreen(
            uiState = previewLoggingUiState(hasExistingEntry = true),
            onMoodSelected = {},
            onPainSelected = {},
            onEnergySelected = {},
            onDischargeSelected = {},
            onMedicationChanged = {},
            onNotesChanged = {},
            onToggleDetailsExpanded = {},
            onSave = {},
            onApplyYesterdayPattern = {},
        )
    }
}

internal fun previewLoggingUiState(
    hasExistingEntry: Boolean = false,
): LoggingUiState = buildLoggingUiState(
    today = LocalDate.of(2026, 3, 17),
    profile = UserProfile(
        ageBand = AgeBand.Twenties,
        averageCycleLength = 30,
        periodLength = 5,
        lastPeriodStart = LocalDate.of(2026, 3, 12),
        trackingGoal = TrackingGoal.SymptomAwareness,
        remindersEnabled = true,
        privacyModeEnabled = true,
    ),
    prediction = PredictionSnapshot(
        generatedAt = LocalDate.of(2026, 3, 17),
        cycleDay = 6,
        nextPeriodDate = LocalDate.of(2026, 4, 10),
        ovulationDate = LocalDate.of(2026, 3, 27),
        fertileWindow = DateRange(
            start = LocalDate.of(2026, 3, 23),
            endInclusive = LocalDate.of(2026, 3, 28),
        ),
        confidenceLabel = "Baseline estimate",
        daysUntilNextPeriod = 24,
    ),
    todayEntry = if (hasExistingEntry) {
        SymptomLog(
            id = "preview-log",
            date = LocalDate.of(2026, 3, 17),
            mood = Mood.Low,
            painLevel = PainLevel.Moderate,
            energyLevel = EnergyLevel.Low,
            dischargeType = DischargeType.Sticky,
            medication = "Ibuprofen",
            notes = "Cramps eased by afternoon.",
        )
    } else {
        null
    },
    editor = LoggingEditorState(
        existingEntryId = if (hasExistingEntry) "preview-log" else null,
        mood = Mood.Low,
        painLevel = PainLevel.Moderate,
        energyLevel = EnergyLevel.Low,
        dischargeType = DischargeType.Sticky,
        medication = if (hasExistingEntry) "Ibuprofen" else "",
        notes = if (hasExistingEntry) "Cramps eased by afternoon." else "",
        isDetailsExpanded = hasExistingEntry,
    ),
    saveMessage = if (hasExistingEntry) "Saved to your local timeline." else null,
    isSaving = false,
)
