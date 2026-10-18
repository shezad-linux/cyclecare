package com.cyclecare.app

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyclecare.core.model.ReminderEvent
import kotlinx.coroutines.delay

@Composable
fun ReminderBannerHost(
    event: ReminderEvent?,
    onOpen: (ReminderEvent) -> Unit,
    onDismiss: (ReminderEvent) -> Unit,
) {
    var hiddenEventId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleEvent = event?.takeUnless { it.id == hiddenEventId }

    LaunchedEffect(visibleEvent?.id) {
        val id = visibleEvent?.id ?: return@LaunchedEffect
        delay(6_000)
        if (hiddenEventId != id) {
            hiddenEventId = id
        }
    }

    AnimatedVisibility(
        visible = visibleEvent != null,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
    ) {
        visibleEvent?.let { reminder ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CycleCareSpace.space16, vertical = CycleCareSpace.space12),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(CycleCareSpace.space18),
                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
                ) {
                    Text(
                        reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        reminder.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
                        FilledTonalButton(onClick = { onOpen(reminder) }) {
                            Text(stringResource(R.string.reminder_action_open))
                        }
                        TextButton(
                            onClick = {
                                hiddenEventId = reminder.id
                                onDismiss(reminder)
                            },
                        ) {
                            Text(stringResource(R.string.reminder_action_dismiss))
                        }
                    }
                }
            }
        }
    }
}
