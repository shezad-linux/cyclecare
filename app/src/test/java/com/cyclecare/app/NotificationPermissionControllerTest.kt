package com.cyclecare.app

import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPermissionControllerTest {
    @Test
    fun android13_grantedAndEnabled_canPostWithoutRecovery() {
        val status = evaluateNotificationPermissionStatus(
            NotificationPermissionInputs(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                runtimePermissionGranted = true,
                notificationsEnabled = true,
                requestAttempted = true,
                showRequestPermissionRationale = false,
            ),
        )

        assertThat(status.canPostNotifications).isTrue()
        assertThat(status.recoveryAction).isEqualTo(NotificationPermissionRecoveryAction.None)
    }

    @Test
    fun android13_deniedBeforeRequest_canRetryRuntimePermission() {
        val status = evaluateNotificationPermissionStatus(
            NotificationPermissionInputs(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                requestAttempted = false,
                showRequestPermissionRationale = false,
            ),
        )

        assertThat(status.canPostNotifications).isFalse()
        assertThat(status.canRequestRuntimePermission).isTrue()
        assertThat(status.recoveryAction).isEqualTo(NotificationPermissionRecoveryAction.RequestRuntimePermission)
    }

    @Test
    fun android13_deniedAfterRequestWithoutRationale_routesToSettings() {
        val status = evaluateNotificationPermissionStatus(
            NotificationPermissionInputs(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                requestAttempted = true,
                showRequestPermissionRationale = false,
            ),
        )

        assertThat(status.canPostNotifications).isFalse()
        assertThat(status.canRequestRuntimePermission).isFalse()
        assertThat(status.recoveryAction).isEqualTo(NotificationPermissionRecoveryAction.OpenSettings)
    }

    @Test
    fun android12_enabledNotifications_doNotNeedRuntimePermission() {
        val status = evaluateNotificationPermissionStatus(
            NotificationPermissionInputs(
                sdkInt = Build.VERSION_CODES.S,
                runtimePermissionGranted = false,
                notificationsEnabled = true,
                requestAttempted = false,
                showRequestPermissionRationale = false,
            ),
        )

        assertThat(status.runtimePermissionRequired).isFalse()
        assertThat(status.canPostNotifications).isTrue()
        assertThat(status.recoveryAction).isEqualTo(NotificationPermissionRecoveryAction.None)
    }

    @Test
    fun android12_blockedNotifications_routeToSettings() {
        val status = evaluateNotificationPermissionStatus(
            NotificationPermissionInputs(
                sdkInt = Build.VERSION_CODES.S,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                requestAttempted = false,
                showRequestPermissionRationale = false,
            ),
        )

        assertThat(status.runtimePermissionRequired).isFalse()
        assertThat(status.canPostNotifications).isFalse()
        assertThat(status.recoveryAction).isEqualTo(NotificationPermissionRecoveryAction.OpenSettings)
    }
}
