package com.cyclecare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderSoundPreset

object ReminderChannels {
    const val remindersGroupKey = "cyclecare-reminders"
    private const val currentChannelSchemaVersion = 2
    private var previewPlayer: MediaPlayer? = null

    fun sync(
        context: Context,
        settings: List<ReminderChannelSettings>,
        logger: AppLogger,
    ): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Result.success(Unit)
        return runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            settings.forEach { channelSettings ->
                val currentId = physicalChannelId(channelSettings)
                manager.createNotificationChannel(channelFor(context, channelSettings))
                knownChannelIds(channelSettings.channelKey)
                    .filterNot { it == currentId }
                    .forEach(manager::deleteNotificationChannel)
            }
        }.onFailure { error ->
            logger.error(
                tag = "ReminderChannels",
                event = "channel_sync_failed",
                throwable = error,
            )
        }
    }

    fun channelId(settings: ReminderChannelSettings): String = physicalChannelId(settings)

    fun playPreview(
        context: Context,
        preset: ReminderSoundPreset,
        logger: AppLogger,
    ) {
        runCatching {
            replacePreviewPlayer(
                createPreviewPlayer(
                    context = context.applicationContext,
                    preset = preset,
                    logger = logger,
                ),
            )
        }.onFailure { error ->
            logger.error(
                tag = "ReminderChannels",
                event = "sound_preview_failed",
                attributes = mapOf("preset" to preset.name),
                throwable = error,
            )
        }
    }

    private fun channelFor(
        context: Context,
        settings: ReminderChannelSettings,
    ): NotificationChannel {
        val (name, description, importance) = when (settings.channelKey) {
            ReminderChannelKey.CycleAlerts -> Triple(
                "Cycle alerts",
                "Period preparation and late-cycle reminders.",
                NotificationManager.IMPORTANCE_HIGH,
            )
            ReminderChannelKey.FertilityAlerts -> Triple(
                "Fertility alerts",
                "Fertile window and ovulation reminders.",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            ReminderChannelKey.DailyCheckIn -> Triple(
                "Daily check-in",
                "Daily symptom logging reminders.",
                NotificationManager.IMPORTANCE_LOW,
            )
        }
        return NotificationChannel(
            physicalChannelId(settings),
            name,
            importance,
        ).apply {
            this.description = description
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(settings.vibrationEnabled)
            if (settings.soundEnabled) {
                setSound(
                    soundUri(context, settings.soundPreset),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
            } else {
                setSound(null, null)
            }
        }
    }

    private fun soundUri(
        context: Context,
        preset: ReminderSoundPreset,
    ): Uri = Uri.parse("android.resource://${context.packageName}/${soundResId(preset)}")

    private fun soundResId(preset: ReminderSoundPreset): Int = when (preset) {
        ReminderSoundPreset.GentleBell -> R.raw.gentle_bell
        ReminderSoundPreset.SoftChime -> R.raw.soft_chime
        ReminderSoundPreset.WaterDrop -> R.raw.water_drop
    }

    private fun createPreviewPlayer(
        context: Context,
        preset: ReminderSoundPreset,
        logger: AppLogger,
    ): MediaPlayer {
        val mediaPlayer = MediaPlayer()
        var prepared = false
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mediaPlayer.setDataSource(context, soundUri(context, preset))
            mediaPlayer.setOnCompletionListener { player ->
                synchronized(this) {
                    if (previewPlayer === player) {
                        previewPlayer = null
                    }
                }
                player.release()
            }
            mediaPlayer.setOnErrorListener { player, what, extra ->
                logger.warn(
                    tag = "ReminderChannels",
                    event = "sound_preview_playback_error",
                    attributes = mapOf(
                        "preset" to preset.name,
                        "what" to what.toString(),
                        "extra" to extra.toString(),
                    ),
                )
                synchronized(this) {
                    if (previewPlayer === player) {
                        previewPlayer = null
                    }
                }
                player.release()
                true
            }
            mediaPlayer.prepare()
            prepared = true
            mediaPlayer.start()
            return mediaPlayer
        } catch (error: Throwable) {
            if (prepared) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
            throw error
        }
    }

    @Synchronized
    private fun replacePreviewPlayer(nextPlayer: MediaPlayer) {
        previewPlayer?.runCatching {
            stop()
            release()
        }
        previewPlayer = nextPlayer
    }

    private fun physicalChannelId(settings: ReminderChannelSettings): String =
        physicalChannelId(
            settings = settings,
            schemaVersion = currentChannelSchemaVersion,
        )

    private fun physicalChannelId(
        settings: ReminderChannelSettings,
        schemaVersion: Int,
    ): String = when (schemaVersion) {
        1 -> "cyclecare_${settings.channelKey.name.lowercase()}_${settings.soundPreset.name.lowercase()}_s${settings.soundEnabled}_v${settings.vibrationEnabled}"
        else -> "cyclecare_v${schemaVersion}_${settings.channelKey.name.lowercase()}_${settings.soundPreset.name.lowercase()}_s${settings.soundEnabled}_v${settings.vibrationEnabled}"
    }

    private fun knownChannelIds(channelKey: ReminderChannelKey): List<String> =
        listOf(1, currentChannelSchemaVersion).distinct().flatMap { schemaVersion ->
            ReminderSoundPreset.entries.flatMap { preset ->
                listOf(
                    ReminderChannelSettings(channelKey, true, true, preset, true),
                    ReminderChannelSettings(channelKey, true, true, preset, false),
                    ReminderChannelSettings(channelKey, true, false, preset, true),
                    ReminderChannelSettings(channelKey, true, false, preset, false),
                ).map { settings ->
                    physicalChannelId(
                        settings = settings,
                        schemaVersion = schemaVersion,
                    )
                }
            }
        }
}

internal fun notificationManagerCompat(context: Context): NotificationManagerCompat =
    NotificationManagerCompat.from(context)
