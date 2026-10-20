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
import com.cyclecare.core.domain.NoOpReminderIntelligenceRepository
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.ReminderIntelligenceRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.SyncAdapter
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.domain.FirstRunNudgeRepository
import com.cyclecare.core.domain.ads.DraftTracker
import com.cyclecare.core.domain.ads.InterstitialStatsRepository
import com.cyclecare.core.domain.time.TimeProvider

data class AppContainer(
    val profileRepository: ProfileRepository,
    val cycleRepository: CycleRepository,
    val symptomRepository: SymptomRepository,
    val predictionRepository: PredictionRepository,
    val insightRepository: InsightRepository,
    val reminderRepository: ReminderRepository,
    val reminderChannelSettingsRepository: ReminderChannelSettingsRepository,
    val reminderEventsRepository: ReminderEventsRepository,
    /**
     * The state the notification intelligence owns: auto-disabled types
     * and deferred candidates. Defaults to the never-changing store so a
     * container built for a narrow test still composes.
     */
    val reminderIntelligenceRepository: ReminderIntelligenceRepository = NoOpReminderIntelligenceRepository,
    val privacyRepository: PrivacyRepository,
    val exportRepository: ExportRepository,
    val appResetRepository: AppResetRepository,
    val reminderScheduler: ReminderScheduler,
    val notificationPermissionController: NotificationPermissionController,
    val appVisibilityObserver: AppVisibilityObserver,
    val reminderNavigationCoordinator: ReminderNavigationCoordinator,
    val syncAdapter: SyncAdapter,
    /**
     * Persisted counters for the interstitial gate. Spec 15 §11.3 conditions
     * 2, 3 and 8 all depend on state that must outlive the process.
     */
    val interstitialStatsRepository: InterstitialStatsRepository,
    /**
     * Whether an unsaved draft exists anywhere in the app. Spec 15 §11.3
     * condition 4.
     */
    val draftTracker: DraftTracker,
    /**
     * Whether the one-time nudge towards Settings still has to be shown.
     * Spec 15 §1.3.4.
     */
    val firstRunNudgeRepository: FirstRunNudgeRepository,
    /**
     * The app's single clock. Injected rather than read ambiently so that
     * time-dependent behaviour such as the interstitial gate is testable.
     */
    val timeProvider: TimeProvider,
    val processStartEpochMillis: Long = System.currentTimeMillis(),
    val reminderNotificationClient: ReminderNotificationClient = NoOpReminderNotificationClient,
    val crashReporter: CrashReporter = NoOpCrashReporter,
    val appLogger: AppLogger = NoOpAppLogger,
)
