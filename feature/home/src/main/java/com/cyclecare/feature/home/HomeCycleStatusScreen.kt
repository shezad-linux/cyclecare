@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.designsystem.text.resolve
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardKeyValueRow
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.MetricPill
import com.cyclecare.core.designsystem.MetricPillTone
import com.cyclecare.core.designsystem.PeriodActiveGradientColors
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.cyclecare.core.designsystem.PeriodHeroScene

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
internal fun HomeDetailShell(
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
internal fun CycleStatusHeroCard(
    uiState: HomeUiState,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useStackedLayout = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        val heroMode = if (uiState.hasCurrentCycle) PeriodHeroMode.Active else PeriodHeroMode.Predicted
        val isDark = isSystemInDarkTheme()
        val heroGradientColors = if (uiState.hasCurrentCycle) {
            PeriodActiveGradientColors()
        } else {
            if (isDark) {
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                )
            } else {
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                )
            }
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
internal fun CycleStatusHeroText(
    uiState: HomeUiState,
    progress: Float,
) {
    val isDark = isSystemInDarkTheme()
    val heroTextColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
    val progressStateDescriptionText = progressStateDescription(uiState.cycleDay, uiState.cycleLength).resolve()
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
        Text(
            stringResource(R.string.cycle_detail),
            style = MaterialTheme.typography.labelMedium,
            color = heroTextColor.copy(alpha = 0.84f),
        )
        Text(
            text = homeHeroTitle(uiState).resolve(),
            style = MaterialTheme.typography.titleLarge,
            color = heroTextColor,
        )
        Text(
            text = homeHeroSubtitle(uiState).resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = heroTextColor.copy(alpha = 0.9f),
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
                color = heroTextColor.copy(alpha = 0.82f),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics {
                        stateDescription = progressStateDescriptionText
                    },
                color = if (isDark) MaterialTheme.colorScheme.primary else Color.White,
                trackColor = (if (isDark) MaterialTheme.colorScheme.primary else Color.White).copy(alpha = 0.24f),
            )
        }
    }
}

