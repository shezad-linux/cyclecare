package com.cyclecare.feature.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the app may schedule exact alarms.
 *
 * `SCHEDULE_EXACT_ALARM` is denied by default on a fresh install and the
 * user can revoke it at any time, so it is never assumed. Spec `13` §11
 * requires the grant to be checked immediately before scheduling, and this
 * is the same check made visible to the person using the app, so the
 * prompt only appears when it would actually do something.
 */
@Composable
internal fun rememberExactAlarmAccess(): ExactAlarmAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.canScheduleExactAlarms()) }

    return ExactAlarmAccess(
        isGranted = granted,
        refresh = { granted = context.canScheduleExactAlarms() },
    )
}

/** The state of the app's exact alarm access, and a way to re-read it. */
internal data class ExactAlarmAccess(
    val isGranted: Boolean,
    val refresh: () -> Unit,
)

/** Whether the app may schedule exact alarms right now. */
internal fun Context.canScheduleExactAlarms(): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        // The permission does not exist before API 31, so there is nothing
        // to ask for and nothing to check.
        return false
    }
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return alarmManager.canScheduleExactAlarms()
}

/**
 * Whether the system can show the exact alarm request screen.
 *
 * The request itself only exists from API 31, so on an older device there
 * is nothing to offer and the entry is hidden.
 */
internal fun Context.canRequestExactAlarms(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

/**
 * The screen that asks the user for exact alarm access.
 *
 * The app works without it, on the inexact tier, so the request is a
 * request and not a requirement, and the copy says so.
 */
internal fun exactAlarmRequestIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
