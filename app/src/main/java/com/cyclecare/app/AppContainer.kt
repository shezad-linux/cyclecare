package com.cyclecare.app

import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.CrashReporter
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.NoOpCrashReporter
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.SyncAdapter
import com.cyclecare.core.domain.SymptomRepository

data class AppContainer(
    val profileRepository: ProfileRepository,
    val cycleRepository: CycleRepository,
    val symptomRepository: SymptomRepository,
    val predictionRepository: PredictionRepository,
    val insightRepository: InsightRepository,
    val reminderRepository: ReminderRepository,
    val reminderChannelSettingsRepository: ReminderChannelSettingsRepository,
    val reminderEventsRepository: ReminderEventsRepository,
    val privacyRepository: PrivacyRepository,
    val exportRepository: ExportRepository,
    val appResetRepository: AppResetRepository,
    val reminderScheduler: ReminderScheduler,
    val notificationPermissionController: NotificationPermissionController,
    val appVisibilityObserver: AppVisibilityObserver,
    val reminderNavigationCoordinator: ReminderNavigationCoordinator,
    val syncAdapter: SyncAdapter,
    val processStartEpochMillis: Long = System.currentTimeMillis(),
    val reminderNotificationClient: ReminderNotificationClient = NoOpReminderNotificationClient,
    val crashReporter: CrashReporter = NoOpCrashReporter,
    val appLogger: AppLogger = NoOpAppLogger,
)
