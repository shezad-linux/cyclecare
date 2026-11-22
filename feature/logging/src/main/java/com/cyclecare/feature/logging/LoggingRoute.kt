@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

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
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ads.DraftTracker
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LoggingContext(
    val profile: UserProfile? = null,
    val cycles: List<CycleRecord> = emptyList(),
    val prediction: CyclePrediction? = null,
    val todayEntry: SymptomLog? = null,
    val symptoms: List<SymptomLog> = emptyList(),
)

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

class LoggingViewModel(
    private val symptomRepository: SymptomRepository,
    profileRepository: ProfileRepository,
    cycleRepository: CycleRepository,
    predictionRepository: PredictionRepository,
    private val savedStateHandle: SavedStateHandle,
    /**
     * Reports whether this editor holds unsaved changes, so the interstitial
     * gate can honour spec 15 §11.3 condition 4.
     */
    private val draftTracker: DraftTracker,
) : ViewModel() {
    private val today = LocalDate.now()
    private val editorFlow = MutableStateFlow(savedStateHandle.toLoggingEditorState())
    private val saveMessageFlow = MutableStateFlow<UiText?>(null)
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
        // Report the draft state whenever either the editor or the stored
        // entry changes, because both decide whether a draft exists: the
        // editor holds what the user typed, the entry holds what was saved.
        viewModelScope.launch {
            combine(editorFlow, contextFlow) { editor, context -> editor to context.todayEntry }
                .collect { (editor, savedEntry) ->
                    draftTracker.setUnsavedDraft(editor.hasUnsavedContentComparedTo(savedEntry))
                }
        }
    }

    override fun onCleared() {
        // The draft surface is gone, so it is no longer holding unsaved
        // changes. Without this the tracker would keep suppressing
        // interstitials for a draft the user can no longer reach.
        draftTracker.setUnsavedDraft(false)
        super.onCleared()
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
                saveMessageFlow.value = uiText(R.string.saved_to_local_timeline)
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
    private val draftTracker: DraftTracker,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        LoggingViewModel(
            symptomRepository = symptomRepository,
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            predictionRepository = predictionRepository,
            savedStateHandle = extras.createSavedStateHandle(),
            draftTracker = draftTracker,
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

internal fun SavedStateHandle.toLoggingEditorState(): LoggingEditorState =
    LoggingEditorState(
        hydrationKey = get<String>(LoggingStateKeys.HydrationKey).orEmpty(),
        existingEntryId = get<String>(LoggingStateKeys.ExistingEntryId),
        // Persisted values are read back with valueOf(); a state file that
        // predates an enum rename must not crash the screen on restore.
        // A corrupt value falls back to the same default a missing value
        // would — the editor is a draft surface, so the loss is silent.
        mood = parseSavedEnum(get<String>(LoggingStateKeys.Mood), Mood.Stable),
        painLevel = parseSavedEnum(get<String>(LoggingStateKeys.PainLevel), PainLevel.None),
        energyLevel = parseSavedEnum(get<String>(LoggingStateKeys.EnergyLevel), EnergyLevel.Steady),
        dischargeType = parseSavedEnum(get<String>(LoggingStateKeys.DischargeType), DischargeType.None),
        medication = get<String>(LoggingStateKeys.Medication).orEmpty(),
        notes = get<String>(LoggingStateKeys.Notes).orEmpty(),
        isDetailsExpanded = get<Boolean>(LoggingStateKeys.DetailsExpanded) ?: false,
    )

private inline fun <reified T : Enum<T>> parseSavedEnum(
    raw: String?,
    fallback: T,
): T = raw?.let { rawValue -> enumValues<T>().firstOrNull { it.name == rawValue } } ?: fallback

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

