@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.designsystem.text.resolve
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.metricCardAccents

@Composable
internal fun HomeReminderButton(
    reminderUnreadCount: Int,
    onOpenReminderCenter: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onOpenReminderCenter,
        modifier = Modifier
            .sizeIn(minWidth = CycleCareSpace.minTouchTarget, minHeight = CycleCareSpace.minTouchTarget)
            .testTag(HomeTags.REMINDER_CENTER_BUTTON),
    ) {
        BadgedBox(
            badge = {
                if (reminderUnreadCount > 0) {
                    Badge {
                        Text(reminderUnreadCount.coerceAtMost(99).toString())
                    }
                }
            },
        ) {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = stringResource(R.string.open_reminder_center),
            )
        }
    }
}

@Composable
internal fun HomeMetricGrid(
    uiState: HomeUiState,
    reminderUnreadCount: Int,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStackedMetrics = maxWidth < 420.dp || LocalDensity.current.fontScale > 1.2f

        val accents = metricCardAccents(MaterialTheme.colorScheme)
        val neutralAccent = accents.neutral
        val periodAccent = accents.period

        if (useStackedMetrics) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                MetricCard(
                    label = uiText(R.string.avg_cycle),
                    value = pluralStringResource(R.plurals.n_days, uiState.cycleLength, uiState.cycleLength),
                    modifier = Modifier.fillMaxWidth(),
                    accent = neutralAccent,
                )
                MetricCard(
                    label = uiText(R.string.avg_period),
                    value = pluralStringResource(R.plurals.n_days, uiState.averagePeriodLength, uiState.averagePeriodLength),
                    modifier = Modifier.fillMaxWidth(),
                    accent = periodAccent,
                )
                MetricCard(
                    label = uiText(R.string.variability),
                    value = uiState.variabilityText.resolve(),
                    modifier = Modifier.fillMaxWidth(),
                    accent = neutralAccent,
                )
                MetricCard(
                    label = uiText(R.string.unread_reminders),
                    value = reminderUnreadCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    accent = neutralAccent,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    MetricCard(
                        label = uiText(R.string.avg_cycle),
                        value = pluralStringResource(R.plurals.n_days, uiState.cycleLength, uiState.cycleLength),
                        modifier = Modifier.weight(1f),
                        accent = neutralAccent,
                    )
                    MetricCard(
                        label = uiText(R.string.avg_period),
                        value = pluralStringResource(R.plurals.n_days, uiState.averagePeriodLength, uiState.averagePeriodLength),
                        modifier = Modifier.weight(1f),
                        accent = periodAccent,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    MetricCard(
                        label = uiText(R.string.variability),
                        value = uiState.variabilityText.resolve(),
                        modifier = Modifier.weight(1f),
                        accent = neutralAccent,
                    )
                    MetricCard(
                        label = uiText(R.string.unread_reminders),
                        value = reminderUnreadCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = neutralAccent,
                    )
                }
            }
        }
    }
}

