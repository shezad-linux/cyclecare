@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.MetricPill
import com.cyclecare.core.designsystem.MetricPillTone
import com.cyclecare.core.designsystem.PeriodActiveAccentColor
import com.cyclecare.core.designsystem.PeriodActiveContainerColor
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel

@Composable
internal fun LoggingHeroCard(
    uiState: LoggingUiState,
    onMoodSelected: (Mood?) -> Unit,
    onPainSelected: (PainLevel?) -> Unit,
    onEnergySelected: (EnergyLevel?) -> Unit,
    onDischargeSelected: (DischargeType?) -> Unit,
    onApplyYesterdayPattern: () -> Unit,
) {
    val isActivePeriod = uiState.heroMode == PeriodHeroMode.Active
    CycleCarePanel(
        modifier = Modifier
            .testTag(LoggingTags.HeroCard),
        containerColor = if (isActivePeriod) {
            PeriodActiveContainerColor()
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
        ) {
            LoggingHeroText(uiState = uiState)
            if (uiState.cyclePhase != null) {
                val smartInsight = buildSmartLoggingInsight(
                    uiState,
                    uiState.cyclePhase,
                )
                Text(
                    text = smartInsight.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActivePeriod) {
                        PeriodActiveAccentColor()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (uiState.hasYesterdayEntry) {
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.repeat_yesterday_s_pattern),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onApplyYesterdayPattern,
                        contentPadding = PaddingValues(horizontal = CycleCareSpace.space12, vertical = CycleCareSpace.space6)
                    ) {
                        Text(
                            text = stringResource(R.string.same_as_yesterday),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isActivePeriod) PeriodActiveAccentColor() else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LoggingHeroText(
    uiState: LoggingUiState,
) {
    val completionProgress = (
        uiState.completedSections.toFloat() / uiState.totalSections.coerceAtLeast(1).toFloat()
    ).coerceIn(0f, 1f)
    val isActivePeriod = uiState.heroMode == PeriodHeroMode.Active
    val accentColor = if (isActivePeriod) PeriodActiveAccentColor() else MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = CycleCareSpace.space12),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
            ) {
                Text(
                    text = loggingHeroEyebrow(uiState).resolve(),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.log_today),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = uiState.dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MetricPill(
                    text = stringResource(R.string.streak_with_8s_check_in, uiState.streakLabel.resolve()),
                    tone = MetricPillTone.Neutral,
                )
            }
            if (uiState.completedSections > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = CycleCareSpace.space14, vertical = CycleCareSpace.space10),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space2),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.count_ratio,
                                uiState.completedSections,
                                uiState.totalSections,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.done),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.entry_progress),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.count_ratio,
                        uiState.completedSections,
                        uiState.totalSections,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                )
            }
            LinearProgressIndicator(
                progress = { completionProgress },
                modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
        ) {
            uiState.cycleProgressLabel?.let { label ->
                MetricPill(text = label.resolve())
            }
            uiState.cyclePhase?.let { phase ->
                MetricPill(
                    text = phase.displayKey().resolve(),
                    tone = MetricPillTone.Highlight,
                )
            }
            MetricPill(
                text = stringResource(R.string.score_value, uiState.cycleScore),
                tone = MetricPillTone.Highlight,
            )
            uiState.entryStatusLabel?.let { label ->
                MetricPill(
                    text = label.resolve(),
                    tone = MetricPillTone.Highlight,
                )
            }
        }
        Text(
            text = uiState.privacyNote.resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

