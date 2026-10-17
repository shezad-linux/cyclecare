package com.cyclecare.app

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.model.ReminderChannelSettings

interface ReminderNotificationClient {
    fun syncChannels(settings: List<ReminderChannelSettings>): Result<Unit>
    fun post(notificationId: Int, notification: Notification): ReminderNotificationPostResult
}

sealed interface ReminderNotificationPostResult {
    data object Posted : ReminderNotificationPostResult
    data object PermissionUnavailable : ReminderNotificationPostResult
    data class Failed(val throwable: Throwable) : ReminderNotificationPostResult
}

class AndroidReminderNotificationClient(
    private val context: Context,
    private val logger: AppLogger,
) : ReminderNotificationClient {
    override fun syncChannels(settings: List<ReminderChannelSettings>): Result<Unit> =
        ReminderChannels.sync(context, settings, logger)

    override fun post(notificationId: Int, notification: Notification): ReminderNotificationPostResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return ReminderNotificationPostResult.PermissionUnavailable
            }
        }

        val notificationManager = notificationManagerCompat(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return ReminderNotificationPostResult.PermissionUnavailable
        }

        return try {
            notificationManager.notify(notificationId, notification)
            ReminderNotificationPostResult.Posted
        } catch (_: SecurityException) {
            ReminderNotificationPostResult.PermissionUnavailable
        } catch (throwable: Throwable) {
            ReminderNotificationPostResult.Failed(throwable)
        }
    }
}

object NoOpReminderNotificationClient : ReminderNotificationClient {
    override fun syncChannels(settings: List<ReminderChannelSettings>): Result<Unit> = Result.success(Unit)

    override fun post(notificationId: Int, notification: Notification): ReminderNotificationPostResult =
        ReminderNotificationPostResult.Posted
}
