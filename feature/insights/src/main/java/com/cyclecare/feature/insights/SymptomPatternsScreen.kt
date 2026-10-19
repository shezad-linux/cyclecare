@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.text.resolve

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

