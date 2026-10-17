package com.cyclecare.app

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cyclecare.core.data.notificationPermissionPrefsName
import com.cyclecare.core.data.notificationPermissionRequestedKey

data class NotificationPermissionStatus(
    val runtimePermissionRequired: Boolean,
    val runtimePermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val requestAttempted: Boolean,
    val canRequestRuntimePermission: Boolean,
) {
    val canPostNotifications: Boolean =
        notificationsEnabled && (!runtimePermissionRequired || runtimePermissionGranted)

    val recoveryAction: NotificationPermissionRecoveryAction = when {
        canPostNotifications -> NotificationPermissionRecoveryAction.None
        canRequestRuntimePermission -> NotificationPermissionRecoveryAction.RequestRuntimePermission
        else -> NotificationPermissionRecoveryAction.OpenSettings
    }
}

enum class NotificationPermissionRecoveryAction {
    None,
    RequestRuntimePermission,
    OpenSettings,
}

internal data class NotificationPermissionInputs(
    val sdkInt: Int,
    val runtimePermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val requestAttempted: Boolean,
    val showRequestPermissionRationale: Boolean,
)

internal fun evaluateNotificationPermissionStatus(
    inputs: NotificationPermissionInputs,
): NotificationPermissionStatus {
    val runtimePermissionRequired = inputs.sdkInt >= Build.VERSION_CODES.TIRAMISU
    val canRequestRuntimePermission = runtimePermissionRequired &&
        !inputs.runtimePermissionGranted &&
        (!inputs.requestAttempted || inputs.showRequestPermissionRationale)

    return NotificationPermissionStatus(
        runtimePermissionRequired = runtimePermissionRequired,
        runtimePermissionGranted = if (runtimePermissionRequired) {
            inputs.runtimePermissionGranted
        } else {
            true
        },
        notificationsEnabled = inputs.notificationsEnabled,
        requestAttempted = inputs.requestAttempted,
        canRequestRuntimePermission = canRequestRuntimePermission,
    )
}

interface NotificationPermissionController {
    fun status(activity: ComponentActivity? = null): NotificationPermissionStatus
    fun markRequestAttempted()
    fun notificationSettingsIntent(): Intent
}

class AndroidNotificationPermissionController(
    private val context: Context,
) : NotificationPermissionController {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(notificationPermissionPrefsName, Context.MODE_PRIVATE)

    override fun status(activity: ComponentActivity?): NotificationPermissionStatus =
        evaluateNotificationPermissionStatus(
            NotificationPermissionInputs(
                sdkInt = Build.VERSION.SDK_INT,
                runtimePermissionGranted = isRuntimePermissionGranted(),
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                requestAttempted = preferences.getBoolean(notificationPermissionRequestedKey, false),
                showRequestPermissionRationale = activity?.let(::shouldShowNotificationPermissionRationale) ?: false,
            ),
        )

    override fun markRequestAttempted() {
        preferences.edit().putBoolean(notificationPermissionRequestedKey, true).apply()
    }

    override fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun isRuntimePermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowNotificationPermissionRationale(activity: ComponentActivity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

internal tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
