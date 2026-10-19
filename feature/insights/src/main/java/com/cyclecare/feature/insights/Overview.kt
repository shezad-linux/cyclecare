@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.cyclecare.core.designsystem.ChartFillColor
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.text.resolve

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
                    }
                    // The source context is the line below, not a second
                    // badge. A white pill on a white card wrapped awkwardly
                    // and said the same thing the line does. The exact
                    // figures, "based on 6 logged cycles from Oct 2025 to
                    // Mar 2026", are on the cycle trends screen.
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
                            accent = ChartFillColor(),
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
                        stringResource(R.string.cycle_data_storage_and_ad_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

