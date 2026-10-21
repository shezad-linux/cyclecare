@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import com.cyclecare.core.designsystem.token.CycleCareSpace
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
import androidx.compose.ui.res.stringResource
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
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ads.DraftTracker
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
import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.domain.prediction.DateWindow
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

internal const val MAX_SYMPTOM_NOTE_LENGTH = 500

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
                saveCtaLabel = uiState.saveCtaLabel.resolve(),
                saveMessage = uiState.saveMessage?.resolve(),
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
            contentPadding = PaddingValues(horizontal = CycleCareSpace.space16, vertical = CycleCareSpace.space14),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
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
                    title = stringResource(R.string.mood),
                    helperText = stringResource(R.string.pick_the_closest_overall_feeling),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
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
                                text = mood.displayLabel().resolve(),
                            )
                        }
                    }
                }
            }
            item {
                SelectionSectionCard(
                    title = stringResource(R.string.pain),
                    helperText = uiState.painLevel?.helperText()?.resolve()
                        ?: stringResource(R.string.not_tracked),
                    accent = MaterialTheme.colorScheme.surface,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                    ) {
                        NotTrackedChip(
                            selected = uiState.painLevel == null,
                            onClick = { onPainSelected(null) },
                        )
                        PainLevel.entries.forEach { painLevel ->
                            SelectionChip(
                                selected = uiState.painLevel == painLevel,
                                onClick = { onPainSelected(painLevel) },
                                text = painLevel.displayLabel().resolve(),
                            )
                        }
                    }
                }
            }
            item {
                SelectionSectionCard(
                    title = stringResource(R.string.energy),
                    helperText = stringResource(R.string.how_steady_does_your_energy_feel_right_now),
                    accent = MaterialTheme.colorScheme.surface,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                    ) {
                        NotTrackedChip(
                            selected = uiState.energyLevel == null,
                            onClick = { onEnergySelected(null) },
                        )
                        EnergyLevel.entries.forEach { energyLevel ->
                            SelectionChip(
                                selected = uiState.energyLevel == energyLevel,
                                onClick = { onEnergySelected(energyLevel) },
                                text = energyLevel.displayLabel().resolve(),
                            )
                        }
                    }
                }
            }
            item {
                SelectionSectionCard(
                    title = stringResource(R.string.body_cues),
                    helperText = stringResource(R.string.optional_cervical_fluid_observations_helps_estimate_fertilit),
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                    ) {
                        NotTrackedChip(
                            selected = uiState.dischargeType == null,
                            onClick = { onDischargeSelected(null) },
                        )
                        DischargeType.entries.forEach { dischargeType ->
                            SelectionChip(
                                selected = uiState.dischargeType == dischargeType,
                                onClick = { onDischargeSelected(dischargeType) },
                                text = dischargeType.displayLabel().resolve(),
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

