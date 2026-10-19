@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.metricCardAccents
import com.cyclecare.core.designsystem.text.uiText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardKeyValueRow
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.MetricCard

@Composable
fun HomeFertilityDetailRoute(
    factory: HomeViewModelFactory,
    onBackToDashboard: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeFertilityDetailScreen(
        uiState = uiState,
        onBack = onBackToDashboard,
        onOpenCalendar = onOpenCalendar,
        onOpenInsights = onOpenInsights,
    )
}

@Composable
internal fun HomeFertilityDetailScreen(
    uiState: HomeUiState,
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    HomeDetailShell(
        title = stringResource(R.string.fertility_detail),
        subtitle = stringResource(
            R.string.fertility_detail_subtitle,
            uiState.fertileWindowText.resolve(),
            uiState.ovulationEstimateText.resolve(),
        ),
        modifier = Modifier.testTag(HomeTags.FERTILITY_DETAIL_SCREEN),
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                MetricCard(
                    label = uiText(R.string.fertile_window),
                    value = uiState.fertileWindowText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = metricCardAccents(MaterialTheme.colorScheme).neutral,
                )
                MetricCard(
                    label = uiText(R.string.ovulation),
                    value = uiState.ovulationEstimateText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = metricCardAccents(MaterialTheme.colorScheme).neutral,
                )
            }
            DashboardSectionCard(
                title = stringResource(R.string.how_this_estimate_is_built),
                subtitle = uiState.cycleSourceSummary.resolve(),
                eyebrow = stringResource(R.string.from_your_cycle_history),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10)) {
                    Text(
                        stringResource(R.string.fertility_timing_is_estimated_from_your_saved_baseline_and_r),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DashboardKeyValueRow(
                        label = stringResource(R.string.current_window),
                        value = uiState.fertileWindowText.resolve(),
                    )
                    DashboardKeyValueRow(
                        label = stringResource(R.string.ovulation_date),
                        value = uiState.ovulationEstimateText.resolve(),
                    )
                }
            }
            Button(onClick = onOpenCalendar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_calendar))
            }
            OutlinedButton(onClick = onOpenInsights, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_insights))
            }
        }
    }
}

