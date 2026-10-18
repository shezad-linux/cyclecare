package com.cyclecare.app

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.room.Room
import com.cyclecare.core.data.LocalAppResetRepository
import com.cyclecare.core.data.LocalExportRepository
import com.cyclecare.core.data.LocalInsightRepository
import com.cyclecare.core.data.LocalPredictionRepository
import com.cyclecare.core.data.LocalPrivacyRepository
import com.cyclecare.core.data.LocalReminderChannelSettingsRepository
import com.cyclecare.core.data.NoOpSyncAdapter
import com.cyclecare.core.data.RoomCycleRepository
import com.cyclecare.core.data.RoomProfileRepository
import com.cyclecare.core.data.RoomReminderEventsRepository
import com.cyclecare.core.data.RoomReminderRepository
import com.cyclecare.core.data.RoomSymptomRepository
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.ALL_MIGRATIONS
import com.cyclecare.core.domain.prediction.CyclePredictionEngineV2
import com.cyclecare.core.domain.time.SystemTimeProvider
import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.domain.ReminderDefaults
import com.cyclecare.core.domain.ReminderSchedulePlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CycleCareApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val crashReporter = createCrashReporter()
        val appLogger = AndroidAppLogger(crashReporter)
        enableStrictMode(appLogger)

        val database = Room.databaseBuilder(
            applicationContext,
            CycleCareDatabase::class.java,
            "cyclecare.db",
        ).addMigrations(*ALL_MIGRATIONS).build()

        val profileRepository = RoomProfileRepository(database, appLogger)
        val cycleRepository = RoomCycleRepository(database, appLogger)
        val symptomRepository = RoomSymptomRepository(database, appLogger)
        // One clock for the whole app, injected rather than defaulted.
        // The v1 engine called LocalDate.now() internally, which made
        // rollover behaviour untestable and impossible to reason about.
        val timeProvider = SystemTimeProvider()

        val predictionRepository = LocalPredictionRepository(
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            engine = CyclePredictionEngineV2(),
            timeProvider = timeProvider,
        )
        val insightRepository = LocalInsightRepository(cycleRepository, symptomRepository, InsightEngine())
        val reminderRepository = RoomReminderRepository(database, appLogger)
        val reminderChannelSettingsRepository = LocalReminderChannelSettingsRepository(applicationContext, appLogger)
        val reminderEventsRepository = RoomReminderEventsRepository(database, appLogger)
        val privacyRepository = LocalPrivacyRepository(applicationContext)
        val notificationPermissionController = AndroidNotificationPermissionController(applicationContext)
        val appVisibilityObserver = ProcessAppVisibilityObserver()
        val reminderNavigationCoordinator = InMemoryReminderNavigationCoordinator()
        val reminderNotificationClient = AndroidReminderNotificationClient(
            context = applicationContext,
            logger = appLogger,
        )
        val appResetRepository = LocalAppResetRepository(
            context = applicationContext,
            database = database,
        )
        val reminderScheduler = WorkManagerReminderScheduler(
            context = applicationContext,
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            predictionRepository = predictionRepository,
            reminderRepository = reminderRepository,
            reminderChannelSettingsRepository = reminderChannelSettingsRepository,
            privacyRepository = privacyRepository,
            planner = ReminderSchedulePlanner(),
            logger = appLogger,
        )

        appContainer = AppContainer(
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            symptomRepository = symptomRepository,
            predictionRepository = predictionRepository,
            insightRepository = insightRepository,
            reminderRepository = reminderRepository,
            reminderChannelSettingsRepository = reminderChannelSettingsRepository,
            reminderEventsRepository = reminderEventsRepository,
            privacyRepository = privacyRepository,
            exportRepository = LocalExportRepository(
                context = applicationContext,
                profileRepository = profileRepository,
                cycleRepository = cycleRepository,
                symptomRepository = symptomRepository,
                insightRepository = insightRepository,
                database = database,
            ),
            appResetRepository = appResetRepository,
            reminderScheduler = reminderScheduler,
            notificationPermissionController = notificationPermissionController,
            appVisibilityObserver = appVisibilityObserver,
            reminderNavigationCoordinator = reminderNavigationCoordinator,
            syncAdapter = NoOpSyncAdapter(),
            reminderNotificationClient = reminderNotificationClient,
            crashReporter = crashReporter,
            appLogger = appLogger,
        )

        ReminderRefreshWorker.enqueuePeriodic(this)
        ReminderRefreshWorker.enqueueImmediate(this)

        applicationScope.launch {
            seedDefaultReminderRules()
            val channelSettings = appContainer.reminderChannelSettingsRepository.observeChannelSettings().first()
            appContainer.reminderNotificationClient.syncChannels(channelSettings)
        }
    }

    internal fun replaceAppContainerForTesting(container: AppContainer): AppContainer {
        val previous = appContainer
        appContainer = container
        return previous
    }

    private fun enableStrictMode(logger: AndroidAppLogger) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build(),
            )
        }.onFailure { error ->
            logger.warn(
                tag = "CycleCareStrictMode",
                event = "strict_mode_enable_failed",
                attributes = mapOf("sdk" to Build.VERSION.SDK_INT.toString()),
                throwable = error,
            )
        }
    }

    private suspend fun seedDefaultReminderRules() {
        val profile = appContainer.profileRepository.observeProfile().first() ?: return
        val existingIds = appContainer.reminderRepository.observeRules().first().mapTo(mutableSetOf()) { it.id }
        ReminderDefaults.standardRules(profile.remindersEnabled, profile.trackingGoal)
            .filterNot { existingIds.contains(it.id) }
            .forEach { rule -> appContainer.reminderRepository.upsertRule(rule) }
    }
}
