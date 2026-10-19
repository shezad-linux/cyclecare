@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.cyclecare.core.model.ReminderSoundPreset

@Composable
internal fun ReminderRuleCard(
    item: SettingsReminderItem,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = CycleCareSpace.space16),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
        ) {
            Text(item.title.resolve(), style = MaterialTheme.typography.titleMedium)
            Text(
                item.scheduleLabel.resolve(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                item.detail.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = item.rule.enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
internal fun ReminderChannelCard(
    item: SettingsChannelItem,
    remindersEnabled: Boolean,
    onToggleChannelEnabled: (Boolean) -> Unit,
    onToggleSoundEnabled: (Boolean) -> Unit,
    onToggleVibrationEnabled: (Boolean) -> Unit,
    onSelectSoundPreset: (ReminderSoundPreset) -> Unit,
    onPreviewSound: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
        SettingSwitchRow(
            title = item.title.resolve(),
            detail = item.detail.resolve(),
            checked = item.settings.enabled,
            onCheckedChange = onToggleChannelEnabled,
        )
        SettingSwitchRow(
            title = stringResource(R.string.sound),
            detail = stringResource(R.string.play_a_calm_bundled_sound_for_this_channel),
            checked = item.settings.soundEnabled,
            onCheckedChange = onToggleSoundEnabled,
        )
        SettingSwitchRow(
            title = stringResource(R.string.vibration),
            detail = stringResource(R.string.use_vibration_for_this_channel_when_android_allows_it),
            checked = item.settings.vibrationEnabled,
            onCheckedChange = onToggleVibrationEnabled,
        )
        Text(stringResource(R.string.sound_preset), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
            ReminderSoundPreset.entries.forEach { preset ->
                val selected = preset == item.settings.soundPreset
                if (selected) {
                    FilledTonalButton(
                        onClick = { onSelectSoundPreset(preset) },
                        enabled = remindersEnabled && item.settings.enabled && item.settings.soundEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(preset.displayTitle().resolve())
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelectSoundPreset(preset) },
                        enabled = remindersEnabled && item.settings.enabled && item.settings.soundEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(preset.displayTitle().resolve())
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onPreviewSound,
            enabled = remindersEnabled && item.settings.enabled && item.settings.soundEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.preview_sound))
        }
    }
}

