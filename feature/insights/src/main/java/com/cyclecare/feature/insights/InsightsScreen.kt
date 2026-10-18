@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.InsightPhase
import com.cyclecare.core.model.InsightPhaseHighlight
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal object InsightsTags {
    const val OverviewScreen = "insights_overview"
    const val CycleTrendsScreen = "insights_cycle_trends"
    const val SymptomPatternsScreen = "insights_symptom_patterns"
    const val CycleTrendsCta = "insights_cycle_trends_cta"
    const val SymptomPatternsCta = "insights_symptom_patterns_cta"
    const val BackToOverview = "insights_back_to_overview"
}

private val TrendChartsBuildingSummary = uiText(R.string.trend_charts_building_summary)
private val SymptomPatternsLockedSummary = uiText(R.string.symptom_patterns_locked_summary)
private val BuildStrongerPatternsSummary = uiText(R.string.build_stronger_patterns_summary)

data class InsightsUiState(
    val averageCycleText: UiText = pluralUiText(R.plurals.n_days, 28, 28),
    val averagePeriodText: UiText = pluralUiText(R.plurals.n_days, 5, 5),
    val variabilityLabel: UiText = uiText(R.string.variability_steady_lately),
    val variabilityText: UiText = pluralUiText(R.plurals.n_day_rhythm, 28, 28),
    val variabilityDetail: UiText = uiText(R.string.variability_consistent),
    val variabilityInterpretation: UiText = uiText(R.string.variability_close_to_average),
    val cycleWindowLabel: UiText = uiText(R.string.cycle_window_recent_cycles),
    val hasEnoughCycleData: Boolean = false,
    val hasEnoughSymptomData: Boolean = false,
    val cyclesLoggedCount: Int = 0,
    val cyclesNeededForInsights: Int = 2,
    val daysLoggedThisCycle: Int = 0,
    val daysNeededForSymptomInsights: Int = 5,
    val cycleTrendBars: List<InsightBarPointUi> = emptyList(),
    val periodTrendItems: List<InsightStripItemUi> = emptyList(),
    val cycleTrendSummary: UiText = TrendChartsBuildingSummary,
    val symptomSummary: UiText = SymptomPatternsLockedSummary,
    val symptomSections: List<InsightBreakdownSectionUi> = emptyList(),
    val phaseHighlights: List<InsightPhaseHighlightUi> = emptyList(),
    val narrativeTitle: UiText = uiText(R.string.narrative_build_stronger_patterns),
    val narrativeSummary: UiText = UiText.Raw(""),
    val cycleProgressLabel: UiText = pluralUiText(R.plurals.n_of_m_cycles_logged, 1, 1, 2),
    val symptomProgressLabel: UiText = uiText(R.string.keep_logging_to_unlock_patterns),
)

data class InsightBarPointUi(
    val label: UiText,
    val value: Int,
    val valueLabel: UiText,
)

data class InsightStripItemUi(
    val label: UiText,
    val valueLabel: UiText,
)

data class InsightBreakdownSectionUi(
    val title: UiText,
    val topLabel: UiText?,
    val items: List<InsightBreakdownItemUi>,
)

data class InsightBreakdownItemUi(
    val label: UiText,
    val count: Int,
    val fraction: Float,
    val valueLabel: UiText,
)

data class InsightPhaseHighlightUi(
    val badgeLabel: UiText,
    val title: UiText,
    val detail: UiText,
)

internal fun buildInsightsUiState(insights: InsightSnapshot): InsightsUiState {
    val cycleTrendBars = insights.recentCycleLengths.mapIndexed { index, value ->
        InsightBarPointUi(
            label = uiText(R.string.cycle_point_label, index + 1),
            value = value,
            valueLabel = pluralUiText(R.plurals.n_days, value, value),
        )
    }
    val periodTrendItems = insights.recentPeriodLengths.mapIndexed { index, value ->
        InsightStripItemUi(
            label = uiText(R.string.period_point_label, index + 1),
            valueLabel = uiText(R.string.day_compact, value),
        )
    }
    val symptomSections = listOf(
        breakdownSection(
            title = uiText(R.string.section_mood),
            counts = insights.moodCounts.mapKeys { (mood, _) -> mood.label() },
        ),
        breakdownSection(
            title = uiText(R.string.section_energy),
            counts = insights.energyCounts.mapKeys { (energy, _) -> energy.label() },
        ),
        breakdownSection(
            title = uiText(R.string.section_pain),
            counts = insights.painCounts.mapKeys { (pain, _) -> pain.label() },
        ),
        breakdownSection(
            title = uiText(R.string.section_body_cues),
            counts = insights.dischargeCounts.mapKeys { (discharge, _) -> discharge.label() },
        ),
    ).filter { section -> section.items.isNotEmpty() }

    val cyclesLogged = insights.recentCycleLengths.size
    val cyclesNeeded = 2
    val daysLoggedThisCycle = insights.moodCounts.values.sum()
    val daysNeeded = 5

    return InsightsUiState(
        averageCycleText = pluralUiText(R.plurals.n_days, insights.averageCycleLength, insights.averageCycleLength),
        averagePeriodText = pluralUiText(R.plurals.n_days, insights.averagePeriodLength, insights.averagePeriodLength),
        variabilityLabel = if (insights.irregularCycles) {
            uiText(R.string.variability_more_variation)
        } else {
            uiText(R.string.variability_steady_lately)
        },
        variabilityText = if (insights.minCycleLength == insights.maxCycleLength) {
            pluralUiText(R.plurals.n_day_rhythm, insights.averageCycleLength, insights.averageCycleLength)
        } else {
            pluralUiText(
                R.plurals.min_max_day_range,
                insights.maxCycleLength,
                insights.minCycleLength,
                insights.maxCycleLength,
            )
        },
        variabilityDetail = when {
            !insights.hasEnoughCycleData -> uiText(R.string.variability_consistent)
            insights.irregularCycles -> uiText(R.string.variability_more_variation_than_usual)
            else -> uiText(R.string.variability_consistent)
        },
        variabilityInterpretation = when {
            !insights.hasEnoughCycleData -> uiText(R.string.variability_close_to_average)
            insights.irregularCycles -> uiText(R.string.variability_above_baseline)
            else -> uiText(R.string.variability_close_to_average)
        },
        cycleWindowLabel = UiText.Raw(insights.cycleWindowLabel),
        hasEnoughCycleData = insights.hasEnoughCycleData,
        hasEnoughSymptomData = insights.hasEnoughSymptomData,
        cyclesLoggedCount = cyclesLogged,
        cyclesNeededForInsights = cyclesNeeded,
        daysLoggedThisCycle = daysLoggedThisCycle,
        daysNeededForSymptomInsights = daysNeeded,
        cycleTrendBars = cycleTrendBars,
        periodTrendItems = periodTrendItems,
        cycleTrendSummary = when {
            !insights.hasEnoughCycleData -> TrendChartsBuildingSummary
            else -> pluralUiText(
                R.plurals.your_last_cycle_was_n_days_within_typical_range,
                insights.averageCycleLength,
                insights.averageCycleLength,
            )
        },
        symptomSummary = when {
            !insights.hasEnoughSymptomData -> SymptomPatternsLockedSummary
            else -> uiText(R.string.symptoms_show_patterns_related_to_cycle_phases)
        },
        symptomSections = symptomSections,
        phaseHighlights = insights.phaseHighlights.map { highlight ->
            InsightPhaseHighlightUi(
                badgeLabel = when (highlight.phase) {
                    InsightPhase.Period -> uiText(R.string.phase_period)
                    InsightPhase.FertileWindow -> uiText(R.string.phase_fertile_window)
                },
                title = UiText.Raw(highlight.title),
                detail = UiText.Raw(highlight.description),
            )
        },
        narrativeTitle = if (insights.hasEnoughCycleData || insights.hasEnoughSymptomData) {
            uiText(R.string.narrative_what_we_know)
        } else {
            uiText(R.string.narrative_build_stronger_patterns)
        },
        narrativeSummary = when {
            !insights.hasEnoughCycleData && !insights.hasEnoughSymptomData -> BuildStrongerPatternsSummary
            insights.narrativeSummary.isBlank() && cyclesLogged > 0 -> {
                pluralUiText(
                    R.plurals.your_last_cycle_was_n_days_keep_logging_to_refine_your_insights,
                    insights.averageCycleLength,
                    insights.averageCycleLength,
                )
            }
            insights.narrativeSummary.isBlank() -> {
                uiText(R.string.start_logging_to_build_insights)
            }
            else -> UiText.Raw(insights.narrativeSummary)
        },
        cycleProgressLabel = pluralUiText(R.plurals.n_of_m_cycles_logged, cyclesLogged, cyclesLogged, cyclesNeeded),
        symptomProgressLabel = if (daysLoggedThisCycle > 0) {
            pluralUiText(R.plurals.n_of_m_days_logged, daysLoggedThisCycle, daysLoggedThisCycle, daysNeeded)
        } else {
            uiText(R.string.log_a_few_days_to_see_patterns)
        },
    )
}

class InsightsViewModel(
    insightRepository: InsightRepository,
) : ViewModel() {
    val uiState: StateFlow<InsightsUiState> = insightRepository.observeInsights()
        .map(::buildInsightsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())
}

class InsightsViewModelFactory(
    private val insightRepository: InsightRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InsightsViewModel(insightRepository) as T
}

@Composable
fun InsightsRoute(
    factory: InsightsViewModelFactory,
    onOpenCycleTrends: () -> Unit,
    onOpenSymptomPatterns: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsOverviewScreen(
        uiState = uiState,
        onOpenCycleTrends = onOpenCycleTrends,
        onOpenSymptomPatterns = onOpenSymptomPatterns,
    )
}

@Composable
fun InsightsCycleTrendsRoute(
    factory: InsightsViewModelFactory,
    onBackToOverview: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CycleTrendsScreen(
        uiState = uiState,
        onBackToOverview = onBackToOverview,
    )
}

@Composable
fun InsightsSymptomPatternsRoute(
    factory: InsightsViewModelFactory,
    onBackToOverview: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SymptomPatternsScreen(
        uiState = uiState,
        onBackToOverview = onBackToOverview,
    )
}

@Composable
internal fun InsightsOverviewScreen(
    uiState: InsightsUiState,
    onOpenCycleTrends: () -> Unit,
    onOpenSymptomPatterns: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(CycleCareSpace.space16)
            .testTag(InsightsTags.OverviewScreen),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            GradientHeaderCard(
                title = stringResource(R.string.insights),
                subtitle = uiState.variabilityDetail.resolve(),
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        InsightBadge(text = uiState.variabilityLabel.resolve(), emphasized = true)
                        if (uiState.cyclesLoggedCount > 0) {
                            InsightBadge(
                                text = pluralStringResource(
                                    R.plurals.n_cycles_logged,
                                    uiState.cyclesLoggedCount,
                                    uiState.cyclesLoggedCount,
                                ),
                            )
                        }
                    }
                },
            )
        }
        item {
            InsightsMetricGrid(uiState = uiState)
        }
        item {
            ElevatedInsightCard {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    Text(stringResource(R.string.cycle_variability), style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        InsightBadge(
                            text = uiState.variabilityText.resolve(),
                            emphasized = uiState.hasEnoughCycleData,
                        )
                        if (uiState.cycleWindowLabel.resolve().isNotBlank()) {
                            InsightBadge(text = uiState.cycleWindowLabel.resolve())
                        }
                    }
                    Text(
                        uiState.variabilityInterpretation.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (uiState.hasEnoughCycleData) {
            item {
                ElevatedInsightCard {
                    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space14)) {
                        Text(stringResource(R.string.recent_cycle_trends), style = MaterialTheme.typography.titleLarge)
                        MiniBarChart(
                            points = uiState.cycleTrendBars,
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        PeriodLengthStrip(items = uiState.periodTrendItems)
                        Text(
                            uiState.variabilityInterpretation.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onOpenCycleTrends,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(InsightsTags.CycleTrendsCta),
                        ) {
                            Text(stringResource(R.string.view_cycle_trends))
                        }
                    }
                }
            }
        } else if (uiState.cyclesLoggedCount > 0) {
            item {
                ElevatedInsightCard {
                    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                        Text(stringResource(R.string.trend_charts_are_still_building), style = MaterialTheme.typography.titleLarge)
                        InsightProgressBadge(
                            current = uiState.cyclesLoggedCount,
                            total = uiState.cyclesNeededForInsights,
                            label = stringResource(R.string.cycles_logged),
                        )
                        Text(
                            uiState.cycleProgressLabel.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            uiState.cycleTrendSummary.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (uiState.hasEnoughSymptomData) {
            item {
                ElevatedInsightCard {
                    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space14)) {
                        Text(stringResource(R.string.symptom_patterns), style = MaterialTheme.typography.titleLarge)
                        uiState.phaseHighlights.take(2).forEach { item ->
                            PhaseHighlightCard(item = item)
                        }
                        if (uiState.phaseHighlights.isEmpty()) {
                            uiState.symptomSections.take(2).forEach { section ->
                                PatternHighlightLine(
                                    title = section.title,
                                    detail = sectionHighlight(section),
                                )
                            }
                        }
                        Text(
                            uiState.symptomSummary.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onOpenSymptomPatterns,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(InsightsTags.SymptomPatternsCta),
                        ) {
                            Text(stringResource(R.string.view_symptom_patterns))
                        }
                    }
                }
            }
        } else {
            item {
                ElevatedInsightCard {
                    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                        Text(stringResource(R.string.symptom_clustering_needs_more_check_ins), style = MaterialTheme.typography.titleLarge)
                        InsightProgressBadge(
                            current = uiState.daysLoggedThisCycle,
                            total = uiState.daysNeededForSymptomInsights,
                            label = stringResource(R.string.days_logged_this_cycle),
                        )
                        Text(
                            uiState.symptomProgressLabel.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            uiState.symptomSummary.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                ) {
                    Text(uiState.narrativeTitle.resolve(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        uiState.narrativeSummary.resolve(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.your_data_stays_private_on_this_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CycleTrendsScreen(
    uiState: InsightsUiState,
    onBackToOverview: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(CycleCareSpace.space16)
            .testTag(InsightsTags.CycleTrendsScreen),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            TextButton(
                onClick = onBackToOverview,
                modifier = Modifier.testTag(InsightsTags.BackToOverview),
            ) {
                Text(stringResource(R.string.back_to_insights))
            }
        }
        item {
            GradientHeaderCard(
                title = stringResource(R.string.cycle_trends),
                subtitle = uiState.cycleWindowLabel.resolve(),
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        InsightBadge(text = uiState.variabilityLabel.resolve(), emphasized = true)
                        InsightBadge(text = stringResource(R.string.based_on_logged_cycles))
                    }
                },
            )
        }
        item {
            ElevatedInsightCard {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space14)) {
                    Text(stringResource(R.string.last_six_cycle_lengths), style = MaterialTheme.typography.titleLarge)
                    if (uiState.hasEnoughCycleData) {
                        MiniBarChart(
                            points = uiState.cycleTrendBars,
                            accent = MaterialTheme.colorScheme.primary,
                            minBarHeight = 48.dp,
                            maxBarHeight = 150.dp,
                        )
                    } else {
                        InsightEmptyState(
                            title = stringResource(R.string.cycle_trend_chart_locked),
                            detail = stringResource(R.string.log_at_least_two_full_cycles_to_unlock_this_chart_and),
                        )
                    }
                }
            }
        }
        item {
            ElevatedInsightCard {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space14)) {
                    Text(stringResource(R.string.period_length_strip), style = MaterialTheme.typography.titleLarge)
                    if (uiState.hasEnoughCycleData) {
                        PeriodLengthStrip(items = uiState.periodTrendItems)
                    } else {
                        Text(
                            stringResource(R.string.period_length_comparisons_will_appear_here_once_your),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                MetricCard(
                    label = stringResource(R.string.average),
                    value = uiState.averageCycleText.resolve(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                )
                MetricCard(
                    label = stringResource(R.string.range),
                    value = uiState.variabilityText.resolve(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                )
            }
        }
        item {
            ElevatedInsightCard {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10)) {
                    Text(stringResource(R.string.read_this_trend), style = MaterialTheme.typography.titleLarge)
                    Text(
                        uiState.cycleTrendSummary.resolve(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.this_chart_only_uses_recent_logged_or_imported_cycles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SymptomPatternsScreen(
    uiState: InsightsUiState,
    onBackToOverview: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(CycleCareSpace.space16)
            .testTag(InsightsTags.SymptomPatternsScreen),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            TextButton(
                onClick = onBackToOverview,
                modifier = Modifier.testTag(InsightsTags.BackToOverview),
            ) {
                Text(stringResource(R.string.back_to_insights))
            }
        }
        item {
            GradientHeaderCard(
                title = stringResource(R.string.symptom_patterns),
                subtitle = stringResource(R.string.grouped_from_recent_symptom_logs_inside_your_current),
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                    ) {
                        InsightBadge(
                            text = if (uiState.hasEnoughSymptomData) {
                                stringResource(R.string.pattern_depth_unlocked)
                            } else {
                                stringResource(R.string.more_logging_needed)
                            },
                            emphasized = uiState.hasEnoughSymptomData,
                        )
                        InsightBadge(text = stringResource(R.string.local_analysis))
                    }
                },
            )
        }
        item {
            ElevatedInsightCard {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    Text(stringResource(R.string.pattern_summary), style = MaterialTheme.typography.titleLarge)
                    Text(
                        uiState.symptomSummary.resolve(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!uiState.hasEnoughSymptomData) {
            item {
                ElevatedInsightCard {
                    InsightEmptyState(
                        title = stringResource(R.string.pattern_clusters_unlock_after_a_few_more_check_ins),
                        detail = stringResource(R.string.once_you_have_at_least_five_recent_symptom_logs_th),
                    )
                }
            }
        } else {
            if (uiState.phaseHighlights.isNotEmpty()) {
                item {
                    ElevatedInsightCard {
                        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                            Text(stringResource(R.string.phase_callouts), style = MaterialTheme.typography.titleLarge)
                            uiState.phaseHighlights.forEach { item ->
                                PhaseHighlightCard(item = item)
                            }
                        }
                    }
                }
            }
            items(uiState.symptomSections.size) { index ->
                val section = uiState.symptomSections[index]
                ElevatedInsightCard {
                    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                        Text(section.title.resolve(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            sectionHighlight(section),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        section.items.forEachIndexed { itemIndex, item ->
                            BreakdownRow(item = item)
                            if (itemIndex != section.items.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsMetricGrid(uiState: InsightsUiState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackMetrics = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.2f
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                InsightMetricCard(
                    label = stringResource(R.string.avg_cycle),
                    value = uiState.averageCycleText.resolve(),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                )
                InsightMetricCard(
                    label = stringResource(R.string.avg_period),
                    value = uiState.averagePeriodText.resolve(),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                InsightMetricCard(
                    label = stringResource(R.string.avg_cycle),
                    value = uiState.averageCycleText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                )
                InsightMetricCard(
                    label = stringResource(R.string.avg_period),
                    value = uiState.averagePeriodText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f),
                )
            }
        }
    }
}

@Composable
private fun InsightMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    MetricCard(
        label = label,
        value = value,
        modifier = modifier.fillMaxWidth(),
        accent = accent,
    )
}

@Composable
private fun ElevatedInsightCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    CycleCarePanel(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            content = content,
        )
    }
}

@Composable
private fun MiniBarChart(
    points: List<InsightBarPointUi>,
    accent: Color,
    minBarHeight: androidx.compose.ui.unit.Dp = 40.dp,
    maxBarHeight: androidx.compose.ui.unit.Dp = 112.dp,
) {
    val maxValue = points.maxOfOrNull { it.value } ?: 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            val label = point.label.resolve()
            val valueLabel = point.valueLabel.resolve()
            val fraction = point.value.toFloat() / maxValue.toFloat()
            val barHeight = minBarHeight + ((maxBarHeight - minBarHeight) * fraction)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "$label $valueLabel"
                    },
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .semantics { contentDescription = "$label $valueLabel" },
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.88f),
                        modifier = Modifier.matchParentSize(),
                    ) {}
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodLengthStrip(items: List<InsightStripItemUi>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
    ) {
        items.forEach { item ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = "${item.label.resolve()} ${item.valueLabel.resolve()}",
                    modifier = Modifier.padding(horizontal = CycleCareSpace.space12, vertical = CycleCareSpace.space8),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PhaseHighlightCard(item: InsightPhaseHighlightUi) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(CycleCareSpace.space14),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
        ) {
            InsightBadge(text = item.badgeLabel.resolve(), emphasized = true)
            Text(item.title.resolve(), style = MaterialTheme.typography.titleMedium)
            Text(
                item.detail.resolve(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PatternHighlightLine(
    title: UiText,
    detail: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4)) {
        Text(title.resolve(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(detail, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BreakdownRow(item: InsightBreakdownItemUi) {
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.label.resolve(), style = MaterialTheme.typography.bodyLarge)
            Text(item.valueLabel.resolve(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { item.fraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun InsightProgressBadge(
    current: Int,
    total: Int,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
    ) {
        LinearProgressIndicator(
            progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "$current/$total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InsightBadge(
    text: String,
    emphasized: Boolean = false,
) {
    Surface(
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = CycleCareSpace.space12, vertical = CycleCareSpace.space8),
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InsightEmptyState(
    title: String,
    detail: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(CycleCareSpace.space14),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun breakdownSection(
    title: UiText,
    counts: Map<UiText, Int>,
): InsightBreakdownSectionUi {
    val total = counts.values.sum().coerceAtLeast(1)
    val items = counts.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .map { entry ->
            InsightBreakdownItemUi(
                label = entry.key,
                count = entry.value,
                fraction = entry.value.toFloat() / total.toFloat(),
                valueLabel = pluralUiText(R.plurals.n_days, entry.value, entry.value),
            )
        }
    return InsightBreakdownSectionUi(
        title = title,
        topLabel = items.firstOrNull()?.label,
        items = items,
    )
}

@Composable
private fun sectionHighlight(section: InsightBreakdownSectionUi): String =
    section.topLabel?.let { stringResource(R.string.shows_up_most_often, it.resolve()) }
        ?: stringResource(R.string.keep_logging_to_reveal_patterns)

private fun Mood.label(): UiText = when (this) {
    Mood.Stable -> uiText(R.string.mood_stable)
    Mood.Happy -> uiText(R.string.mood_happy)
    Mood.Low -> uiText(R.string.mood_low)
    Mood.Irritable -> uiText(R.string.mood_irritable)
    Mood.Anxious -> uiText(R.string.mood_anxious)
}

private fun EnergyLevel.label(): UiText = when (this) {
    EnergyLevel.Low -> uiText(R.string.energy_low)
    EnergyLevel.Steady -> uiText(R.string.energy_steady)
    EnergyLevel.High -> uiText(R.string.energy_high)
}

private fun PainLevel.label(): UiText = when (this) {
    PainLevel.None -> uiText(R.string.pain_none)
    PainLevel.Light -> uiText(R.string.pain_light)
    PainLevel.Moderate -> uiText(R.string.pain_moderate)
    PainLevel.Strong -> uiText(R.string.pain_strong)
}

private fun DischargeType.label(): UiText = when (this) {
    DischargeType.None -> uiText(R.string.discharge_none)
    DischargeType.Dry -> uiText(R.string.discharge_dry)
    DischargeType.Creamy -> uiText(R.string.discharge_creamy)
    DischargeType.Sticky -> uiText(R.string.discharge_sticky)
    DischargeType.EggWhite -> uiText(R.string.discharge_egg_white)
}

@ScreenDebugPreviews
@Composable
private fun InsightsOverviewPreview() {
    CycleCareTheme {
        InsightsOverviewScreen(
            uiState = previewInsightsUiState(),
            onOpenCycleTrends = {},
            onOpenSymptomPatterns = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun InsightsLowDataPreview() {
    CycleCareTheme {
        InsightsOverviewScreen(
            uiState = previewLowDataInsightsUiState(),
            onOpenCycleTrends = {},
            onOpenSymptomPatterns = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun CycleTrendsPreview() {
    CycleCareTheme {
        CycleTrendsScreen(
            uiState = previewInsightsUiState(),
            onBackToOverview = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun SymptomPatternsPreview() {
    CycleCareTheme {
        SymptomPatternsScreen(
            uiState = previewInsightsUiState(),
            onBackToOverview = {},
        )
    }
}

internal fun previewInsightsUiState(): InsightsUiState = InsightsUiState(
    averageCycleText = UiText.Raw("29 days"),
    averagePeriodText = UiText.Raw("5 days"),
    variabilityLabel = UiText.Raw("Steady lately"),
    variabilityText = UiText.Raw("27-31 day range"),
    variabilityDetail = UiText.Raw("Your cycle is consistent"),
    variabilityInterpretation = UiText.Raw("Recent cycles stay close to your average"),
    cycleWindowLabel = UiText.Raw("Based on 6 logged cycles"),
    hasEnoughCycleData = true,
    hasEnoughSymptomData = true,
    cyclesLoggedCount = 6,
    cyclesNeededForInsights = 2,
    daysLoggedThisCycle = 7,
    daysNeededForSymptomInsights = 5,
    cycleTrendBars = listOf(28, 30, 27, 29, 31, 29).mapIndexed { index, value ->
        InsightBarPointUi(UiText.Raw("C${index + 1}"), value, UiText.Raw("$value days"))
    },
    periodTrendItems = listOf(5, 5, 4, 5, 6, 5).mapIndexed { index, value ->
        InsightStripItemUi(UiText.Raw("P${index + 1}"), UiText.Raw("${value}d"))
    },
    cycleTrendSummary = UiText.Raw("Your last cycle was 29 days, within typical range"),
    symptomSummary = UiText.Raw("Your symptoms show patterns related to your cycle phases"),
    symptomSections = listOf(
        breakdownSection(UiText.Raw("Mood"), mapOf(UiText.Raw("Stable") to 6, UiText.Raw("Low") to 3, UiText.Raw("Happy") to 2, UiText.Raw("Irritable") to 1, UiText.Raw("Anxious") to 1)),
        breakdownSection(UiText.Raw("Energy"), mapOf(UiText.Raw("Steady") to 5, UiText.Raw("Low") to 4, UiText.Raw("High") to 2)),
        breakdownSection(UiText.Raw("Pain"), mapOf(UiText.Raw("None") to 4, UiText.Raw("Light") to 3, UiText.Raw("Moderate") to 4, UiText.Raw("Strong") to 1)),
        breakdownSection(UiText.Raw("Body cues"), mapOf(UiText.Raw("Dry") to 2, UiText.Raw("Creamy") to 3, UiText.Raw("Egg white") to 4)),
    ),
    phaseHighlights = listOf(
        InsightPhaseHighlightUi(
            badgeLabel = UiText.Raw("Period"),
            title = UiText.Raw("Period phase pattern"),
            detail = UiText.Raw("Moderate pain and low energy show up most often during your period."),
        ),
        InsightPhaseHighlightUi(
            badgeLabel = UiText.Raw("Fertile window"),
            title = UiText.Raw("Fertile window pattern"),
            detail = UiText.Raw("Egg white discharge and steady energy appear most often."),
        ),
    ),
    narrativeTitle = UiText.Raw("What we know"),
    narrativeSummary = UiText.Raw("Your cycle looks regular. Recent history shows 27 to 31 day cycles. Stable mood shows up most often."),
    cycleProgressLabel = UiText.Raw("6 of 2 cycles logged"),
    symptomProgressLabel = UiText.Raw("7 of 5 days logged this cycle"),
)

internal fun previewLowDataInsightsUiState(): InsightsUiState = InsightsUiState(
    averageCycleText = UiText.Raw("28 days"),
    averagePeriodText = UiText.Raw("5 days"),
    variabilityLabel = UiText.Raw("Getting started"),
    variabilityText = UiText.Raw("Early baseline"),
    variabilityDetail = UiText.Raw("Your cycle is consistent"),
    variabilityInterpretation = UiText.Raw("Recent cycles stay close to your average"),
    cycleWindowLabel = UiText.Raw("Based on your setup"),
    hasEnoughCycleData = false,
    hasEnoughSymptomData = false,
    cyclesLoggedCount = 1,
    cyclesNeededForInsights = 2,
    daysLoggedThisCycle = 3,
    daysNeededForSymptomInsights = 5,
    cycleTrendSummary = TrendChartsBuildingSummary,
    symptomSummary = SymptomPatternsLockedSummary,
    narrativeTitle = UiText.Raw("Build stronger patterns"),
    narrativeSummary = BuildStrongerPatternsSummary,
    cycleProgressLabel = UiText.Raw("1 of 2 cycles logged"),
    symptomProgressLabel = UiText.Raw("Log a few more days to see patterns"),
)
