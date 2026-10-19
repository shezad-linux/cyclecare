@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.token.CycleCareShapes
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
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
                    val isDark = isSystemInDarkTheme()
                    val headerTextColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                    Text(
                        text = uiState.cycleSourceSummary.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = headerTextColor.copy(alpha = if (isDark) 0.82f else 0.88f),
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
                        color = if (isDark) MaterialTheme.colorScheme.primary else Color.White,
                        trackColor = (if (isDark) MaterialTheme.colorScheme.primary else Color.White).copy(alpha = 0.24f),
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
                            containerColor = if (isDark) MaterialTheme.colorScheme.primary else Color.White,
                            contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.view_cycle_details))
                    }
                },
                trailing = {
                    val isDark = isSystemInDarkTheme()
                    OutlinedButton(
                        onClick = onOpenEditCycle,
                        modifier = Modifier
                            .defaultMinSize(minHeight = CycleCareSpace.minTouchTarget)
                            .testTag(HomeTags.EDIT_CYCLE_BUTTON),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            (if (isDark) MaterialTheme.colorScheme.outlineVariant else Color.White).copy(alpha = 0.45f),
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
internal fun EditCycleSheet(
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

internal fun HomeQuickAction.icon(): ImageVector = when (type) {
    HomeQuickActionType.LogSymptoms -> Icons.Rounded.Edit
    HomeQuickActionType.OpenCalendar -> Icons.Rounded.DateRange
    HomeQuickActionType.ViewInsights -> Icons.Rounded.LocalFlorist
}

