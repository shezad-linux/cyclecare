package com.cyclecare.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.cyclecare.core.designsystem.token.CycleCareSpace

/**
 * The notification-permission gate.
 *
 * Spec 15 §1.3.4: the gate "must not trap back/switch-access users", so it
 * deliberately installs no back handler. Letting the system back through is
 * the real exit: the activity closes, and the gate is still waiting when the
 * user comes back, because reminders are enabled and notifications are not
 * permitted.
 */
@Composable
internal fun NotificationPermissionRequiredGate(
    actionLabel: String,
    onResolveNotificationAccess: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(NotificationPermissionTags.REQUIRED_GATE),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(CycleCareSpace.space24),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.notifications_required),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = CycleCareSpace.space16),
            )
            Text(
                text = stringResource(R.string.notifications_required_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CycleCareSpace.space8),
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CycleCareSpace.space24)
                    .testTag(NotificationPermissionTags.REQUIRED_ACTION),
                onClick = onResolveNotificationAccess,
            ) {
                Text(actionLabel)
            }
        }
    }
}
