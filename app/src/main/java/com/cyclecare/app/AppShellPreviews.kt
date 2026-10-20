@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.app

import androidx.compose.runtime.Composable
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenWidthPreviews

/**
 * The notification-permission gate.
 *
 * The gate is a full-screen replacement for the app's content rather
 * than a dialog, so it is a screen in its own right and it is previewed
 * like one.
 */
@ScreenWidthPreviews
@Composable
private fun NotificationPermissionRequiredGatePreview() {
    CycleCareTheme {
        NotificationPermissionRequiredGate(
            actionLabel = "Allow notifications",
            onResolveNotificationAccess = {},
        )
    }
}
