@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import com.cyclecare.core.designsystem.token.extendedColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val IsoDateFormatHint = "YYYY-MM-DD"

@Composable
internal fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenHistoryEdit: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onCycleLengthChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveCycle: () -> Unit,
    onDismissEditor: () -> Unit,
    onShowDeleteConfirmation: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onDeleteCycle: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val monthLabel = remember(uiState.month, locale) { formatMonthLabel(uiState.month, locale) }
    val days = remember(uiState.month, uiState.selectedDate, uiState.cycles, uiState.prediction, firstDayOfWeek) {
        buildCalendarDays(uiState, firstDayOfWeek = firstDayOfWeek)
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(CycleCareSpace.space16)
            // Tagged so tests can reach the selected-day panel, which
            // sits below the grid and is off-screen at rest.
            .testTag(CalendarTags.SCROLL),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        GradientHeaderCard(
            title = stringResource(R.string.calendar),
            subtitle = stringResource(R.string.review_logged_periods_fertile_windows_and_upcoming_estimates),
            supportingContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                ) {
                    uiState.legendItems.forEach { item ->
                        LegendChip(item = item)
                    }
                }
            },
        )

        CycleCarePanel(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onPreviousMonth,
                        modifier = Modifier.testTag(CalendarTags.PREVIOUS_MONTH),
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.previous_month))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.cycle_calendar),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            monthLabel,
                            modifier = Modifier.testTag(CalendarTags.MONTH_LABEL),
                            // The month is this screen's display element,
                            // so it sits on the display ramp rather than
                            // the headline step.
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onNextMonth,
                        modifier = Modifier.testTag(CalendarTags.NEXT_MONTH),
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.next_month))
                    }
                }

                val daySpacing = 8.dp

                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(daySpacing),
                    ) {
                        // The short weekday name does not fit a column of
                        // a seventh of a narrow screen at a large font
                        // scale, and it broke mid-word into "Su n" and
                        // "M on". The narrow form is a single letter,
                        // which is what a seven column grid wants and
                        // which cannot break.
                        val weekdayStyle =
                            if (LocalDensity.current.fontScale > 1.2f) TextStyle.NARROW else TextStyle.SHORT
                        orderedWeekdays(firstDayOfWeek).forEach { dayOfWeek ->
                            Text(
                                dayOfWeek.getDisplayName(weekdayStyle, locale),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(daySpacing),
                    ) {
                        days.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(daySpacing),
                            ) {
                                week.forEach { day ->
                                    CalendarDayCell(
                                        day = day,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                        onClick = { onSelectDate(day.date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        CycleCarePanel(
            modifier = Modifier.testTag(CalendarTags.SELECTED_DAY_PANEL),
            containerColor = selectedDayPanelColor(uiState.selectedDaySummary.type),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                Text(
                    stringResource(R.string.selected_day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                ) {
                    Text(uiState.selectedDaySummary.dateLabel, style = MaterialTheme.typography.titleLarge)
                    StatusPill(summary = uiState.selectedDaySummary)
                }
                Text(uiState.selectedDaySummary.detail.resolve(), style = MaterialTheme.typography.bodyLarge)
                if (!uiState.selectedCycle?.notes.isNullOrBlank()) {
                    Text(
                        uiState.selectedCycle?.notes.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (uiState.selectedCycle == null && uiState.selectedDaySummary.primaryActionLabel != null) {
                    Button(onClick = onOpenCreate, modifier = Modifier.fillMaxWidth()) {
                        Text(uiState.selectedDaySummary.primaryActionLabel.resolve())
                    }
                } else {
                    if (uiState.selectedCycle != null) {
                        Button(onClick = onOpenEdit, modifier = Modifier.fillMaxWidth()) {
                            Text(uiState.selectedDaySummary.primaryActionLabel?.resolve() ?: stringResource(R.string.update_entry))
                        }
                        TextButton(onClick = onShowDeleteConfirmation, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.delete_cycle_entry))
                        }
                    }
                }

                uiState.message?.let { message ->
                    Text(
                        message.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            Text(stringResource(R.string.recent_cycle_history), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.edit_previous_entries_without_leaving_the_calendar),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.historyItems.isEmpty()) {
                CycleCarePanel(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
                ) {
                    Text(
                        text = uiState.historyEmptyMessage?.resolve().orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                uiState.historyItems.forEach { item ->
                    HistoryCard(
                        item = item,
                        onEdit = { onOpenHistoryEdit(item.id) },
                    )
                }
            }
        }
    }

    if (uiState.editor.isVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissEditor,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CycleCareSpace.space24, vertical = CycleCareSpace.space8),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
            ) {
                Text(
                    if (uiState.editor.editingId == null) {
                        stringResource(R.string.log_period)
                    } else {
                        stringResource(R.string.edit_period)
                    },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    stringResource(R.string.use_simple_date_entry_for_now_then_adjust_cycle_length_and_n),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.editor.startDateText,
                    onValueChange = onStartDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.start_date_yyyy_mm_dd)) },
                    singleLine = true,
                    isError = uiState.editor.startDateError != null,
                    supportingText = {
                        uiState.editor.startDateError?.let { error ->
                            Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.editor.endDateText,
                    onValueChange = onEndDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.end_date_yyyy_mm_dd)) },
                    singleLine = true,
                    isError = uiState.editor.endDateError != null,
                    supportingText = {
                        uiState.editor.endDateError?.let { error ->
                            Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.editor.cycleLengthText,
                    onValueChange = onCycleLengthChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cycle_length)) },
                    singleLine = true,
                    isError = uiState.editor.cycleLengthError != null,
                    supportingText = {
                        uiState.editor.cycleLengthError?.let { error ->
                            Text(error.resolve(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                )
                OutlinedTextField(
                    value = uiState.editor.notes,
                    onValueChange = onNotesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 3,
                )
                uiState.editor.error?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(
                    onClick = onSaveCycle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.editor.isSaveEnabled,
                ) {
                    Text(
                        if (uiState.editor.isSaving) {
                            stringResource(R.string.saving)
                        } else {
                            stringResource(R.string.save_locally)
                        },
                    )
                }
                TextButton(onClick = onDismissEditor, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            confirmButton = {
                Button(onClick = onDeleteCycle) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteConfirmation) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.delete_cycle_entry_2)) },
            text = { Text(stringResource(R.string.this_removes_the_selected_period_entry_and_recalculates_loca)) },
        )
    }
}

