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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.MetricCard

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
            Icon(Icons.Rounded.Notifications, contentDescription = "Open reminder center")
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

        if (useStackedMetrics) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                MetricCard(
                    label = "Avg cycle",
                    value = "${uiState.cycleLength} days",
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                )
                MetricCard(
                    label = "Avg period",
                    value = "${uiState.averagePeriodLength} days",
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                )
                MetricCard(
                    label = "Variability",
                    value = uiState.variabilityText.resolve(),
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                )
                MetricCard(
                    label = "Unread reminders",
                    value = reminderUnreadCount.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
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
                        label = "Avg cycle",
                        value = "${uiState.cycleLength} days",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                    )
                    MetricCard(
                        label = "Avg period",
                        value = "${uiState.averagePeriodLength} days",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                ) {
                    MetricCard(
                        label = "Variability",
                        value = uiState.variabilityText.resolve(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    )
                    MetricCard(
                        label = "Unread reminders",
                        value = reminderUnreadCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

