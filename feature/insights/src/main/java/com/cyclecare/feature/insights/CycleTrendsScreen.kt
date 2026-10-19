@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.ChartFillColor
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.metricCardAccents
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.text.resolve

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
                            accent = ChartFillColor(),
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
                    label = uiText(R.string.average),
                    value = uiState.averageCycleText.resolve(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                )
                MetricCard(
                    label = uiText(R.string.range),
                    value = uiState.variabilityText.resolve(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.88f),
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

