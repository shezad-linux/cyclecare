package com.cyclecare.app

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderSchedulePlanner
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.reminderTypeChannelKey
import com.cyclecare.core.model.ScheduledReminder
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class WorkManagerReminderScheduler(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val predictionRepository: PredictionRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderChannelSettingsRepository: ReminderChannelSettingsRepository,
    private val privacyRepository: PrivacyRepository,
    private val planner: ReminderSchedulePlanner,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderScheduler {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun refreshSchedules(now: ZonedDateTime): List<ScheduledReminder> {
        var schedulesResult: List<ScheduledReminder> = emptyList()
        val durationMillis = measureTimeMillis {
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
                    tag = "ReminderScheduler",
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
                awaitWorkOperation { workManager.cancelAllWorkByTag(reminderWorkTag) }
                schedulesResult = emptyList()
                return@measureTimeMillis
            }

            val schedules = planner.buildSchedules(
                prediction = predictionRepository.observePrediction().first(),
                rules = enabledRules,
                cycles = cycleRepository.observeCycles().first(),
                now = now,
            )
            val desiredWorkNames = schedules.mapTo(linkedSetOf()) { it.uniqueWorkName }
            schedules.forEach { reminder ->
                val delay = Duration.between(now, reminder.scheduledAt).toMillis().coerceAtLeast(0L)
                val workRequest = OneTimeWorkRequestBuilder<ReminderDeliveryWorker>()
                    .setInputData(reminder.toInputData())
                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .addTag(reminderWorkTag)
                    .build()
                awaitWorkOperation {
                    workManager.enqueueUniqueWork(reminder.uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequest)
                }
            }
            rules
                .map { reminderUniqueWorkName(it.id) }
                .distinct()
                .filterNot(desiredWorkNames::contains)
                .forEach { uniqueWorkName ->
                    awaitWorkOperation { workManager.cancelUniqueWork(uniqueWorkName) }
                }
            logger.info(
                tag = "ReminderScheduler",
                event = "schedules_refreshed",
                attributes = mapOf(
                    "count" to schedules.size.toString(),
                    "zone" to now.zone.id,
                ),
            )
            schedulesResult = schedules
        }
        logger.debug(
            tag = "ReminderScheduler",
            event = "refresh_duration",
            attributes = mapOf("durationMillis" to durationMillis.toString()),
        )
        return schedulesResult
    }

    private suspend fun awaitWorkOperation(
        block: () -> androidx.work.Operation,
    ) = withContext(Dispatchers.IO) {
        block().result.get()
    }
}

private fun ScheduledReminder.toInputData(): Data = Data.Builder()
    .putInt(ReminderDeliveryWorker.keyNotificationId, uniqueWorkName.hashCode())
    .putString(ReminderDeliveryWorker.keyTitle, title)
    .putString(ReminderDeliveryWorker.keyBody, body)
    .putString(ReminderDeliveryWorker.keyPrivateBody, privateBody)
    .putString(ReminderDeliveryWorker.keyRuleId, ruleId)
    .putString(ReminderDeliveryWorker.keyReminderType, type.name)
    .putString(ReminderDeliveryWorker.keyChannelKey, channelKey.name)
    .putString(ReminderDeliveryWorker.keyDeepLinkTarget, deepLinkTarget.name)
    .putLong(ReminderDeliveryWorker.keyTargetEpochDay, targetEpochDay ?: Long.MIN_VALUE)
    .putLong(ReminderDeliveryWorker.keyScheduledAtEpochMillis, scheduledAt.toInstant().toEpochMilli())
    .build()

internal const val reminderWorkTag = "cyclecare-reminder"
internal fun reminderUniqueWorkName(ruleId: String): String = "cyclecare-reminder-rule-${ruleId.lowercase()}"
