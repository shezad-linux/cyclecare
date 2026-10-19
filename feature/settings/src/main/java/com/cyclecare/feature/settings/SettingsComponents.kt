@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.MetricCard

@Composable
internal fun SettingsMetricGrid(
    remindersEnabled: Boolean,
    unreadReminderCount: Int,
    previewMode: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackMetrics = maxWidth < 430.dp || LocalDensity.current.fontScale > 1.2f
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                SettingsMetricCard(
                    label = stringResource(R.string.reminders),
                    value = stringResource(if (remindersEnabled) R.string.on else R.string.off),
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                )
                SettingsMetricCard(
                    label = stringResource(R.string.unread_center),
                    value = unreadReminderCount.toString(),
                    accent = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f),
                )
                SettingsMetricCard(
                    label = stringResource(R.string.preview_mode),
                    value = previewMode,
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                    SettingsMetricCard(
                        label = stringResource(R.string.reminders),
                        value = stringResource(if (remindersEnabled) R.string.on else R.string.off),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                    )
                    SettingsMetricCard(
                        label = stringResource(R.string.unread_center),
                        value = unreadReminderCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f),
                    )
                }
                SettingsMetricCard(
                    label = stringResource(R.string.preview_mode),
                    value = previewMode,
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
internal fun SettingsMetricCard(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
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
internal fun SectionCard(
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    DashboardSectionCard(
        title = title,
        subtitle = body,
        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        eyebrow = stringResource(R.string.notification_access),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            action()
        }
    }
}

@Composable
internal fun ActionCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionModifier: Modifier = Modifier,
) {
    DashboardSectionCard(
        title = title,
        subtitle = body,
        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        eyebrow = stringResource(R.string.action),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            FilledTonalButton(onClick = onAction, modifier = actionModifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun SettingSwitchRow(
    modifier: Modifier = Modifier,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = CycleCareSpace.space16),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingsBadge(
    text: String,
    emphasized: Boolean = false,
) {
    DashboardPill(text = text, emphasized = emphasized)
}
