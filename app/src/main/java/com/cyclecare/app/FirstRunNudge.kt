package com.cyclecare.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * The one-time nudge towards Settings.
 *
 * Spec `15` §1.3.4: moving Settings out of the thumb zone is only
 * acceptable with a one-time, dismissible nudge after onboarding, never
 * repeated and never a badge.
 *
 * It is a dialog rather than a badge on the Settings action, because a
 * badge that never clears is exactly what the specification rules out.
 *
 * The flag is set as the nudge appears rather than when it is dismissed,
 * so closing the app without dismissing it does not bring the nudge back
 * on the next launch.
 */
@Composable
internal fun FirstRunNudge(
    repository: com.cyclecare.core.domain.FirstRunNudgeRepository,
    onOpenSettings: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        if (repository.shouldShowFirstRunNudge()) {
            repository.markFirstRunNudgeShown()
            visible = true
        }
    }

    if (!visible) return

    AlertDialog(
        onDismissRequest = { visible = false },
        title = { Text(stringResource(R.string.first_run_nudge_title)) },
        text = { Text(stringResource(R.string.first_run_nudge_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    visible = false
                    onOpenSettings()
                },
            ) {
                Text(stringResource(R.string.first_run_nudge_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = { visible = false }) {
                Text(stringResource(R.string.first_run_nudge_dismiss))
            }
        },
    )
}
