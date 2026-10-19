@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.cyclecare.core.designsystem.BottomActionRail
import com.cyclecare.core.designsystem.FormSectionCard
import com.cyclecare.core.designsystem.MetricPill
import com.cyclecare.core.designsystem.MetricPillTone
import com.cyclecare.core.designsystem.text.resolve

@Composable
internal fun DetailsCard(
    uiState: LoggingUiState,
    onMedicationChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    FormSectionCard(
        title = stringResource(R.string.add_details),
        helperText = stringResource(R.string.medication_and_notes_are_optional_for_a_quick_check_in),
        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill(
                    text = if (uiState.isDetailsExpanded) {
                        stringResource(R.string.expanded)
                    } else {
                        stringResource(R.string.optional)
                    },
                    tone = if (uiState.isDetailsExpanded) MetricPillTone.Highlight else MetricPillTone.Neutral,
                )
                TextButton(onClick = onToggleExpanded) {
                    Text(
                        if (uiState.isDetailsExpanded) {
                            stringResource(R.string.hide_details)
                        } else {
                            stringResource(R.string.expand)
                        },
                    )
                }
            }
            if (uiState.isDetailsExpanded) {
                val notesUsed = MAX_SYMPTOM_NOTE_LENGTH - uiState.notesRemaining
                val notesErrorText = uiState.notesError?.resolve()
                val notesCounterText = stringResource(
                    R.string.count_ratio,
                    notesUsed,
                    MAX_SYMPTOM_NOTE_LENGTH,
                )
                OutlinedTextField(
                    value = uiState.medication,
                    onValueChange = onMedicationChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.medication)) },
                )
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = notesErrorText ?: notesCounterText
                        }
                        .testTag(LoggingTags.NotesField),
                    minLines = 3,
                    label = { Text(stringResource(R.string.notes)) },
                    isError = uiState.notesError != null,
                    supportingText = {
                        Text(
                            notesErrorText ?: notesCounterText,
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
                    Text(stringResource(R.string.add_medication_or_notes))
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
internal fun SaveBar(
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

