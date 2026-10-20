package com.cyclecare.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderSchedulePlanner
import com.cyclecare.core.domain.reminderTypeChannelKey
import com.cyclecare.core.domain.reminderWorkName
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.model.ScheduledReminder
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * Schedules reminders as alarms rather than as deferred work.
 *
 * Spec `13` §11 sets three tiers, and this is tiers 1 and 2. Tier 3, the
 * periodic refresh that recomputes these schedules, stays on WorkManager,
 * because that is deferrable work and WorkManager already persists it.
 *
 * The change from WorkManager for the delivery itself is the point. A
 * `OneTimeWorkRequest` with a delay is deferred without bound while the
 * device is idle, so a reminder could arrive hours late. Both alarm APIs
 * here are delivered through Doze, which is what the specification calls
 * the important upgrade.
 *
 * Degradation is the default path rather than an edge case. Most users
 * will never grant exact alarm access, so the app has to be fully
 * functional on Tier 2, and a `SecurityException` from the exact API is
 * treated as a downgrade rather than a crash.
 */
internal class ReminderAlarmScheduler(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val predictionRepository: PredictionRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderChannelSettingsRepository: ReminderChannelSettingsRepository,
    private val privacyRepository: PrivacyRepository,
    private val planner: ReminderSchedulePlanner,
    private val alarmFacade: ReminderAlarmFacade = AndroidReminderAlarmFacade(context),
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderScheduler {

    /**
     * WorkManager, for cancelling the deliveries an older version queued.
     *
     * Resolved lazily and defensively. It is reached only when the
     * schedules are refreshed, never while the application is starting, so
     * the app does not depend on WorkManager being in a particular state
     * before it can compose.
     */
    private val workManager: WorkManager? by lazy {
        runCatching { WorkManager.getInstance(context) }.getOrNull()
    }

    override suspend fun refreshSchedules(now: ZonedDateTime): List<ScheduledReminder> {
        val profile = profileRepository.observeProfile().first()
        val consent = privacyRepository.observeConsent().first()
        val channelSettings = reminderChannelSettingsRepository.observeChannelSettings().first()
        val rules = reminderRepository.observeRules().first()
        val enabledRules = rules.filter { rule ->
            rule.enabled && channelSettings.firstOrNull { it.channelKey == reminderTypeChannelKey(rule.type) }?.enabled != false
        }
        if (
            profile == null ||
            !profile.remindersEnabled ||
            !consent.localDataAcknowledged ||
            enabledRules.isEmpty()
        ) {
            logger.info(
                tag = SCHEDULER_TAG,
                event = "schedules_cleared",
                attributes = mapOf(
                    "reason" to when {
                        profile == null -> "missing_profile"
                        !profile.remindersEnabled -> "reminders_disabled"
                        !consent.localDataAcknowledged -> "consent_missing"
                        else -> "no_enabled_rules"
                    },
                ),
            )
            cancelAllAlarms(rules.map { reminderWorkName(it.id) })
            return emptyList()
        }

        val schedules = planner.buildSchedules(
            prediction = predictionRepository.observePrediction().first(),
            rules = enabledRules,
            cycles = cycleRepository.observeCycles().first(),
            now = now,
        )

        val desired = schedules.mapTo(linkedSetOf()) { it.uniqueWorkName }
        val exactAvailable = alarmFacade.canScheduleExactAlarms()

        schedules.forEach { reminder ->
            schedule(reminder, exactAvailable)
        }
        rules
            .map { reminderWorkName(it.id) }
            .distinct()
            .filterNot(desired::contains)
            .forEach { name -> cancelAlarm(name) }

        // An install that was upgraded from the WorkManager delivery path
        // can still have one-time deliveries queued, and those would arrive
        // a second time. They are cancelled here so a reminder is delivered
        // once, by the alarm.
        cancelLegacyDeliveries()

        logger.info(
            tag = SCHEDULER_TAG,
            event = "schedules_refreshed",
            attributes = mapOf(
                "count" to schedules.size.toString(),
                "zone" to now.zone.id,
                "exactAlarms" to exactAvailable.toString(),
            ),
        )
        return schedules
    }

    /**
     * Schedules one reminder on the tier its type asks for, falling back to
     * Tier 2 whenever the exact API is unavailable.
     */
    private fun schedule(reminder: ScheduledReminder, exactAvailable: Boolean) {
        val request = requestFor(reminder)
        val triggerAt = reminder.scheduledAt.toInstant().toEpochMilli()
        val tier = tierFor(reminder.type)
        if (tier == ReminderDeliveryTier.EXACT && exactAvailable) {
            try {
                alarmFacade.setExactAndAllowWhileIdle(triggerAt, request)
                logger.debug(
                    tag = SCHEDULER_TAG,
                    event = "reminder_scheduled",
                    attributes = mapOf(
                        "ruleId" to reminder.ruleId,
                        "tier" to "exact",
                    ),
                )
                return
            } catch (security: SecurityException) {
                // The permission can be revoked between the check and the
                // call. Downgrade rather than crash.
                logger.info(
                    tag = SCHEDULER_TAG,
                    event = "exact_alarm_denied",
                    attributes = mapOf("ruleId" to reminder.ruleId),
                )
            }
        }
        alarmFacade.setAndAllowWhileIdle(triggerAt, request)
        logger.debug(
            tag = SCHEDULER_TAG,
            event = "reminder_scheduled",
            attributes = mapOf(
                "ruleId" to reminder.ruleId,
                "tier" to "inexact_while_idle",
            ),
        )
    }

    /**
     * Cancels the one-time WorkManager deliveries an older version queued.
     *
     * They are tagged separately from the periodic refresh, so the refresh
     * is left alone.
     */
    private fun cancelLegacyDeliveries() {
        val work = workManager ?: return
        runCatching {
            work.cancelAllWorkByTag(legacyDeliveryWorkTag).result.get()
        }.onFailure { error ->
            logger.warn(
                tag = SCHEDULER_TAG,
                event = "legacy_delivery_cancellation_failed",
                throwable = error,
            )
        }
    }

    /** What the alarm for a reminder carries. */
    private fun requestFor(reminder: ScheduledReminder): ReminderAlarmRequest =
        ReminderAlarmRequest(
            ruleId = reminder.ruleId,
            uniqueName = reminder.uniqueWorkName,
            payload = ReminderAlarmPayload.from(reminder),
        )

    private fun cancelAllAlarms(uniqueWorkNames: List<String>) {
        uniqueWorkNames.forEach { name -> cancelAlarm(name) }
    }

    private fun cancelAlarm(uniqueWorkName: String) {
        alarmFacade.cancel(uniqueWorkName)
    }

    internal companion object {
        const val SCHEDULER_TAG = "ReminderScheduler"

        /** The tag an older version put on its one-time deliveries. */
        const val legacyDeliveryWorkTag = "cyclecare-reminder"
    }
}
