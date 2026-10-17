package com.cyclecare.feature.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.DashboardSectionCard

enum class PrivacyEntryMode {
    Dashboard,
    Setup,
    ConfirmSetup,
    VerifyCurrentPin,
    CreateNewPin,
    ConfirmNewPin,
    Unlock,
}

object PrivacyUiTags {
    const val SCREEN = "privacy_screen"
    const val PIN_CARD = "privacy_pin_card"
    const val PIN_MESSAGE = "privacy_pin_message"
    const val PRIMARY_ACTION = "privacy_primary_action"
    const val SECONDARY_ACTION = "privacy_secondary_action"
    const val TERTIARY_ACTION = "privacy_tertiary_action"
    const val APP_LOCK_SWITCH = "privacy_app_lock_switch"
    const val BIOMETRIC_SWITCH = "privacy_biometric_switch"
    const val ENABLE_BIOMETRIC_BUTTON = "privacy_enable_biometric"
    const val UPDATE_PIN_BUTTON = "privacy_update_pin"
    const val RECOVER_BUTTON = "privacy_recover_access"
    const val DELETE_KEY = "privacy_key_delete"
    const val CLEAR_KEY = "privacy_key_clear"
    const val RESET_LOCAL_DATA = "privacy_reset_local_data"
    const val PIN_DOT_PREFIX = "privacy_pin_dot_"
    const val DIGIT_KEY_PREFIX = "privacy_key_"
}

@Composable
fun PrivacyPinEntryCard(
    title: String,
    subtitle: String,
    pinLength: Int,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onDigitPressed: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    primaryEnabled: Boolean = true,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    tertiaryActionLabel: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
    eyebrow: String = "PIN access",
) {
    DashboardSectionCard(
        modifier = modifier.testTag(PrivacyUiTags.PIN_CARD),
        title = title,
        subtitle = subtitle,
        eyebrow = eyebrow,
        accent = MaterialTheme.colorScheme.surface,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrivacyPinIndicatorRow(pinLength = pinLength)
            PrivacyPinKeypad(
                onDigitPressed = onDigitPressed,
                onDelete = onDelete,
            )
            message?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier.testTag(PrivacyUiTags.PIN_MESSAGE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onPrimaryAction,
                enabled = primaryEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PrivacyUiTags.PRIMARY_ACTION),
            ) {
                Text(primaryActionLabel)
            }
            secondaryActionLabel?.let { label ->
                val action = onSecondaryAction ?: return@let
                FilledTonalButton(
                    onClick = action,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PrivacyUiTags.SECONDARY_ACTION),
                ) {
                    Text(label)
                }
            }
            tertiaryActionLabel?.let { label ->
                val action = onTertiaryAction ?: return@let
                OutlinedButton(
                    onClick = action,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PrivacyUiTags.TERTIARY_ACTION),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
fun PrivacyPinIndicatorRow(
    pinLength: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "PIN entry, $pinLength of 4 digits entered"
            },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(4) { index ->
            val filled = index < pinLength
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("${PrivacyUiTags.PIN_DOT_PREFIX}$index")
                    // Filled and empty dots differ only by colour, which
                    // is not conveyed to a screen reader. Each dot states
                    // its own state so the PIN length is readable without
                    // sight.
                    .semantics {
                        contentDescription = "Digit ${index + 1}, " +
                            if (filled) "entered" else "empty"
                    }
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun PrivacyPinKeypad(
    onDigitPressed: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        ).forEach { digits ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                digits.forEach { digit ->
                    PrivacyKeypadButton(
                        label = digit.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}$digit"),
                    ) {
                        onDigitPressed(digit)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f))
            PrivacyKeypadButton(
                label = "0",
                modifier = Modifier
                    .weight(1f)
                    .testTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}0"),
            ) {
                onDigitPressed(0)
            }
            PrivacyKeypadButton(
                label = "Delete",
                modifier = Modifier
                    .weight(1f)
                    .testTag(PrivacyUiTags.DELETE_KEY),
            ) {
                onDelete()
            }
        }
    }
}

@Composable
private fun PrivacyKeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .aspectRatio(1.18f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
