@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.token.CycleCareShapes
import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.designsystem.text.resolve
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.DashboardSectionCard

@Composable
internal fun HomeDashboardHeader(
    reminderUnreadCount: Int,
    onOpenReminderCenter: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStackedHeader = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f

        if (useStackedHeader) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HomeReminderButton(
                        reminderUnreadCount = reminderUnreadCount,
                        onOpenReminderCenter = onOpenReminderCenter,
                    )
                }
                Text(
                    text = stringResource(R.string.cycle_reminders_and_insights_in_one_local_view),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.cycle_reminders_and_insights_in_one_local_view),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HomeReminderButton(
                    reminderUnreadCount = reminderUnreadCount,
                    onOpenReminderCenter = onOpenReminderCenter,
                )
            }
        }
    }
}

@Composable
internal fun FertilityTeaserCard(
    fertileWindowText: String,
    ovulationEstimateText: String,
    confidenceLabelRes: Int?,
    onOpenFertilityDetail: () -> Unit,
) {
    DashboardSectionCard(
        title = stringResource(R.string.fertile_window),
        subtitle = fertileWindowText,
        modifier = Modifier.testTag(HomeTags.FERTILE_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            // At a large font scale the label and the estimate sat side by
            // side, and the label wrapped onto three lines and overlapped
            // the estimate. They stack instead.
            val largeFontScale = LocalDensity.current.fontScale > 1.2f
            val estimate = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4)) {
                    Text(
                        stringResource(R.string.ovulation_estimate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ovulationEstimateText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            val confidence = @Composable {
                confidenceLabelRes?.let { confidenceRes ->
                    DashboardPill(text = uiText(confidenceRes).resolve())
                }
            }
            if (largeFontScale) {
                estimate()
                confidence()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    estimate()
                    confidence()
                }
            }
            FilledTonalButton(
                onClick = onOpenFertilityDetail,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTags.FERTILITY_DETAIL_CTA),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Text(stringResource(R.string.view_fertility_details))
            }
            // Spec 11 §5: a fertile window is always framed as an
            // estimate and always says it is not a contraceptive method.
            // A bare range with no framing reads as a fact.
            Text(
                text = stringResource(R.string.not_a_contraceptive_method),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReminderTeaserCard(
    teaser: HomeReminderTeaser,
    unreadCount: Int,
    onOpenReminderCenter: () -> Unit,
    onTrackToday: () -> Unit,
) {
    val isDailyCheckIn = teaser.isActionable
    DashboardSectionCard(
        title = teaser.title.resolve(),
        subtitle = teaser.detail.resolve(),
        modifier = Modifier.testTag(HomeTags.REMINDER_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
            ) {
                if (unreadCount > 0) {
                    DashboardPill(
                        text = pluralStringResource(R.plurals.n_unread, unreadCount, unreadCount),
                        emphasized = true,
                    )
                }
                DashboardPill(text = teaser.supporting.resolve())
            }
            if (isDailyCheckIn) {
                Button(
                    onClick = onTrackToday,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.check_in_now))
                }
            } else {
                FilledTonalButton(
                    onClick = onOpenReminderCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HomeTags.REMINDER_DETAIL_CTA),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(stringResource(R.string.view_details))
                }
            }
        }
    }
}

@Composable
internal fun InsightTeaserCard(
    teaser: HomeInsightTeaser,
    onOpenInsights: () -> Unit,
) {
    DashboardSectionCard(
        title = teaser.title.resolve(),
        subtitle = teaser.detail.resolve(),
        modifier = Modifier.testTag(HomeTags.INSIGHTS_CARD),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            if (teaser.trendPoints.isNotEmpty()) {
                HomeMiniTrendStrip(points = teaser.trendPoints)
            }
            DashboardPill(
                text = teaser.supporting.resolve(),
                emphasized = teaser.trendPoints.isEmpty(),
            )
            OutlinedButton(
                onClick = onOpenInsights,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.view_insights))
            }
        }
    }
}

@Composable
internal fun QuickActionButton(
    modifier: Modifier = Modifier,
    action: HomeQuickAction,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CycleCareShapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = CycleCareSpace.space16, vertical = CycleCareSpace.space18),
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space2),
        ) {
            Text(
                text = action.label.resolve(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = action.description.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

