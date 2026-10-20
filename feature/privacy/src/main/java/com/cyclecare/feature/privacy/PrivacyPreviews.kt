@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.privacy

import androidx.compose.runtime.Composable
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenWidthPreviews
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.ConsentState

@ScreenWidthPreviews
@Composable
internal fun PrivacySetupPreview() {
    CycleCareTheme {
        PrivacyScreen(
            uiState = previewPrivacyState(),
            onToggleAppLock = {},
            onEnableBiometric = {},
            onDisableBiometric = {},
            onDigitPressed = {},
            onDelete = {},
            onSubmitPrimary = {},
            onStartPinSetup = {},
            onStartPinChange = {},
            onRecoverAccess = {},
        )
    }
}

@ScreenWidthPreviews
@Composable
internal fun PrivacyUnlockPreview() {
    CycleCareTheme {
        PrivacyScreen(
            uiState = previewPrivacyState().copy(
                entryMode = PrivacyEntryMode.Unlock,
                message = uiText(R.string.pin_must_be_exactly_4_digits),
            ),
            onToggleAppLock = {},
            onEnableBiometric = {},
            onDisableBiometric = {},
            onDigitPressed = {},
            onDelete = {},
            onSubmitPrimary = {},
            onStartPinSetup = {},
            onStartPinChange = {},
            onRecoverAccess = {},
        )
    }
}

/**
 * The app lock overlay's own screen.
 *
 * The overlay is the same screen in its unlock mode, so the preview is
 * the same composable with the state the overlay puts it in.
 */
@ScreenWidthPreviews
@Composable
internal fun AppLockPreview() {
    CycleCareTheme {
        PrivacyScreen(
            uiState = previewPrivacyState().copy(
                entryMode = PrivacyEntryMode.Unlock,
                appLockEnabled = true,
                hasPinConfigured = true,
                pinEntry = "12",
            ),
            onToggleAppLock = {},
            onEnableBiometric = {},
            onDisableBiometric = {},
            onDigitPressed = {},
            onDelete = {},
            onSubmitPrimary = {},
            onStartPinSetup = {},
            onStartPinChange = {},
            onRecoverAccess = {},
        )
    }
}

/** The privacy screen in its setup mode, with no PIN configured yet. */
internal fun previewPrivacyState() = PrivacyUiState(
    appLockEnabled = false,
    biometricEnabled = false,
    hasPinConfigured = false,
    hideNotificationPreview = true,
    entryMode = PrivacyEntryMode.Setup,
    pinEntry = "",
    secureAuthCapability = SecureAuthCapability.None,
    message = null,
)

/** The consent state the privacy screen reads, for the settings preview. */
internal fun previewPrivacyConsent() = ConsentState(
    hideNotificationPreview = true,
    localDataAcknowledged = true,
    appLockEnabled = false,
)
