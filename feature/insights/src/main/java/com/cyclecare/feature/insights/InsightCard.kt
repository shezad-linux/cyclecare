@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.metricCardAccents
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import kotlinx.coroutines.flow.map

@Composable
internal fun InsightsMetricGrid(uiState: InsightsUiState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackMetrics = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.2f
        val accents = metricCardAccents(MaterialTheme.colorScheme)
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                InsightMetricCard(
                    label = uiText(R.string.avg_cycle),
                    value = uiState.averageCycleText.resolve(),
                    accent = accents.neutral,
                )
                InsightMetricCard(
                    label = uiText(R.string.avg_period),
                    value = uiState.averagePeriodText.resolve(),
                    accent = accents.period,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                InsightMetricCard(
                    label = uiText(R.string.avg_cycle),
                    value = uiState.averageCycleText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = accents.neutral,
                )
                InsightMetricCard(
                    label = uiText(R.string.avg_period),
                    value = uiState.averagePeriodText.resolve(),
                    modifier = Modifier.weight(1f),
                    accent = accents.period,
                )
            }
        }
    }
}

@Composable
internal fun InsightMetricCard(
    label: UiText,
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
internal fun ElevatedInsightCard(
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
internal fun MiniBarChart(
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
internal fun PeriodLengthStrip(items: List<InsightStripItemUi>) {
    // A flowing row wrapped the six chips four and then two, which read as
    // a broken grid rather than as a set of chips. They are laid out on a
    // fixed grid instead: one row when there is room for them, and three
    // to a row when there is not, so the arrangement is the same at every
    // width rather than depending on how the words happen to fall.
    if (items.isEmpty()) return
    val columns = if (items.size <= 4) items.size else 3
    val rows = items.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
            ) {
                row.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text = "${item.label.resolve()} ${item.valueLabel.resolve()}",
                            modifier = Modifier.padding(
                                horizontal = CycleCareSpace.space12,
                                vertical = CycleCareSpace.space8,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // A short final row would otherwise leave the chips narrower
                // than the ones above it.
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun PhaseHighlightCard(item: InsightPhaseHighlightUi) {
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
internal fun PatternHighlightLine(
    title: UiText,
    detail: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4)) {
        Text(title.resolve(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(detail, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun BreakdownRow(item: InsightBreakdownItemUi) {
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
internal fun InsightProgressBadge(
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
internal fun InsightBadge(
    text: String,
    emphasized: Boolean = false,
) {
    DashboardPill(
        text = text,
        emphasized = emphasized,
    )
}

@Composable
internal fun InsightEmptyState(
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

internal fun breakdownSection(
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
internal fun sectionHighlight(section: InsightBreakdownSectionUi): String =
    section.topLabel?.let { stringResource(R.string.shows_up_most_often, it.resolve()) }
        ?: stringResource(R.string.keep_logging_to_reveal_patterns)

internal fun Mood.label(): UiText = when (this) {
    Mood.Stable -> uiText(R.string.mood_stable)
    Mood.Happy -> uiText(R.string.mood_happy)
    Mood.Low -> uiText(R.string.mood_low)
    Mood.Irritable -> uiText(R.string.mood_irritable)
    Mood.Anxious -> uiText(R.string.mood_anxious)
}

internal fun EnergyLevel.label(): UiText = when (this) {
    EnergyLevel.Low -> uiText(R.string.energy_low)
    EnergyLevel.Steady -> uiText(R.string.energy_steady)
    EnergyLevel.High -> uiText(R.string.energy_high)
}

internal fun PainLevel.label(): UiText = when (this) {
    PainLevel.None -> uiText(R.string.pain_none)
    PainLevel.Light -> uiText(R.string.pain_light)
    PainLevel.Moderate -> uiText(R.string.pain_moderate)
    PainLevel.Strong -> uiText(R.string.pain_strong)
}

internal fun DischargeType.label(): UiText = when (this) {
    DischargeType.None -> uiText(R.string.discharge_none)
    DischargeType.Dry -> uiText(R.string.discharge_dry)
    DischargeType.Creamy -> uiText(R.string.discharge_creamy)
    DischargeType.Sticky -> uiText(R.string.discharge_sticky)
    DischargeType.EggWhite -> uiText(R.string.discharge_egg_white)
}

