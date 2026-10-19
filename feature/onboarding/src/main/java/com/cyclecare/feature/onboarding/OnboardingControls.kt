@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.resolve
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun OnboardingBottomBar(
    currentStep: Int,
    saving: Boolean,
    enabled: Boolean,
    onStep1Continue: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CycleCareSpace.space16, vertical = CycleCareSpace.space12),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
        ) {
            if (currentStep == 1) {
                Button(
                    onClick = onStep1Continue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTags.START_TRACKING_BUTTON),
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.continue_button))
                }
            } else {
                FinalStepActions(
                    saving = saving,
                    enabled = enabled,
                    onBack = onBack,
                    onContinue = onContinue,
                )
            }
            FooterPrivacyNote()
        }
    }
}

@Composable
internal fun FinalStepActions(
    saving: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10)) {
                Column(
                    modifier = Modifier.testTag("onboarding_final_step"),
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTags.START_TRACKING_BUTTON),
                        enabled = enabled,
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.saving else R.string.start_tracking_my_cycle,
                            ),
                        )
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.back))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.back))
                }
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("onboarding_final_step"),
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTags.START_TRACKING_BUTTON),
                        enabled = enabled,
                    ) {
                        Text(
                            stringResource(
                                if (saving) R.string.saving else R.string.start_tracking_my_cycle,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StepperField(
    valueLabel: UiText,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    contentDescriptionBase: String,
    valueTestTag: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CycleCareSpace.space14, vertical = CycleCareSpace.space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onDecrement, enabled = decrementEnabled) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.decrease_x, contentDescriptionBase),
                )
            }
            Text(
                valueLabel.resolve(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier
            )
            FilledTonalIconButton(onClick = onIncrement, enabled = incrementEnabled) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.increase_x, contentDescriptionBase),
                )
            }
        }
    }
}

@Composable
internal fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(CycleCareSpace.space14),
            horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(top = CycleCareSpace.space2),
            )
        }
    }
}

