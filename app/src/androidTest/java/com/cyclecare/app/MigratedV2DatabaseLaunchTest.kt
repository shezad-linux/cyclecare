package com.cyclecare.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.data.DataStoreInterstitialStatsRepository
import com.cyclecare.core.data.LocalAppResetRepository
import com.cyclecare.core.data.LocalExportRepository
import com.cyclecare.core.data.LocalInsightRepository
import com.cyclecare.core.data.LocalPredictionRepository
import com.cyclecare.core.data.LocalReminderChannelSettingsRepository
import com.cyclecare.core.data.NoOpSyncAdapter
import com.cyclecare.core.data.RoomCycleRepository
import com.cyclecare.core.data.RoomFirstRunNudgeRepository
import com.cyclecare.core.data.RoomMigrationDivergenceRepository
import com.cyclecare.core.data.RoomProfileRepository
import com.cyclecare.core.data.RoomReminderEventsRepository
import com.cyclecare.core.data.RoomReminderRepository
import com.cyclecare.core.data.RoomSymptomRepository
import com.cyclecare.core.database.ALL_MIGRATIONS
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.prediction.CyclePredictionEngineV2
import com.cyclecare.core.domain.time.SystemTimeProvider
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.ScheduledReminder
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app has never been run against a database that was migrated from
 * the schema the live user base is on.
 *
 * Every other migration test opens a database and looks at rows. This
 * one builds a v2 database with the shape the old app wrote, migrates it
 * with the production migration list through the production
 * `Room.databaseBuilder`, and then renders the real app shell over it.
 *
 * The point is the part no row assertion covers: whether the screens
 * that read the migrated data can read it at all.
 */
@RunWith(AndroidJUnit4::class)
class MigratedV2DatabaseLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: CycleCareDatabase? = null

    private val today: LocalDate = LocalDate.now()
    private val firstPeriodStart: LocalDate = today.minusDays(56)
    private val secondPeriodStart: LocalDate = today.minusDays(28)
    private val duplicateLogDate: LocalDate = secondPeriodStart.plusDays(2)

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun theAppRendersTheMigratedHistoryOnADatabaseMigratedFromV2() {
        renderAppOnMigratedDatabase()

        // The shell came up on the migrated database: the profile in it
        // was read, so the app did not fall back to onboarding.
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()

        // The migrated cycle record is rendered with the identifier it
        // had in the old schema, which is what a migration that lost or
        // renamed rows could not do.
        composeRule.openTabAndAwait("calendar")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calendar_scroll")
            .performScrollToNode(hasTestTag("calendar_history_migrated-cycle-1"))
        composeRule.onNodeWithTag("calendar_history_migrated-cycle-1").assertIsDisplayed()

        // Back to the start destination before the activity is torn
        // down. The bottom navigation saves the state of the tab it
        // leaves, and a saved entry that is still INITIALIZED when the
        // activity is destroyed trips a lifecycle assertion inside
        // Navigation. Leaving the app on its start destination keeps
        // the teardown ordinary.
        composeRule.openTabAndAwait("home")
        composeRule.waitForIdle()
    }

    @Test
    fun theAppShowsTheMigrationRecordOnADatabaseMigratedFromV2() {
        renderAppOnMigratedDatabase()

        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_open_migration_record"))
        composeRule.onNodeWithTag("settings_open_migration_record").performClick()
        composeRule.waitForIdle()

        // The record of what the migration did is reachable from
        // Settings, and it says what happened rather than claiming that
        // anything was corrected.
        composeRule.onNodeWithTag("migration_notes_list").assertIsDisplayed()
        composeRule.onNodeWithTag("migration_notes_duplicates").assertIsDisplayed()
        composeRule.onNodeWithTag("migration_notes_cycle_lengths").assertIsDisplayed()

        // The carried-over summary is the last card in the list, so it
        // has to be scrolled to rather than simply asserted.
        composeRule.onNodeWithTag("migration_notes_list")
            .performScrollToNode(hasTestTag("migration_notes_carried_over"))
        composeRule.onNodeWithTag("migration_notes_carried_over").assertIsDisplayed()
    }

    /**
     * Builds a v2 database, migrates it with the production path, and
     * renders the real app shell over the result.
     */
    private fun renderAppOnMigratedDatabase() {
        seedV2Database()

        // The production path: the real builder and the real migration
        // list, so the database is opened exactly as the app opens it.
        val migrated = Room.databaseBuilder(context, CycleCareDatabase::class.java, TEST_DATABASE)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        database = migrated

        val logger = AndroidAppLogger(createCrashReporter())
        val profileRepository = RoomProfileRepository(migrated, logger)
        val cycleRepository = RoomCycleRepository(migrated, logger)
        val symptomRepository = RoomSymptomRepository(migrated, logger)
        val reminderRepository = RoomReminderRepository(migrated, logger)
        val reminderEventsRepository = RoomReminderEventsRepository(migrated, logger)
        val reminderChannelSettingsRepository = LocalReminderChannelSettingsRepository(context, logger)
        val privacyRepository = MigratedLaunchPrivacyRepository()
        val insightRepository = LocalInsightRepository(cycleRepository, symptomRepository, InsightEngine())

        // The container is built on the main thread, because the real
        // visibility observer registers a lifecycle observer and
        // LifecycleRegistry refuses to do that anywhere else. The
        // composition itself is then attached through the rule, so the
        // rule owns the ComposeView and its snapshot state — otherwise
        // another class in the same process can read this one from the
        // instrumentation thread and trip *"Detected multithreaded access
        // to SnapshotStateObserver"*.
        val containerHolder = arrayOfNulls<AppContainer>(1)
        composeRule.activityRule.scenario.onActivity {
            containerHolder[0] = AppContainer(
                profileRepository = profileRepository,
                cycleRepository = cycleRepository,
                symptomRepository = symptomRepository,
                predictionRepository = LocalPredictionRepository(
                    profileRepository = profileRepository,
                    cycleRepository = cycleRepository,
                    engine = CyclePredictionEngineV2(),
                    timeProvider = SystemTimeProvider(),
                ),
                insightRepository = insightRepository,
                reminderRepository = reminderRepository,
                reminderChannelSettingsRepository = reminderChannelSettingsRepository,
                reminderEventsRepository = reminderEventsRepository,
                privacyRepository = privacyRepository,
                exportRepository = LocalExportRepository(
                    context = context,
                    profileRepository = profileRepository,
                    cycleRepository = cycleRepository,
                    symptomRepository = symptomRepository,
                    insightRepository = insightRepository,
                    database = migrated,
                ),
                appResetRepository = LocalAppResetRepository(context, migrated),
                reminderScheduler = StubReminderScheduler,
                notificationPermissionController = AndroidNotificationPermissionController(context),
                appVisibilityObserver = ProcessAppVisibilityObserver(onEnteredForeground = {}),
                reminderNavigationCoordinator = InMemoryReminderNavigationCoordinator(),
                syncAdapter = NoOpSyncAdapter(),
                interstitialStatsRepository = DataStoreInterstitialStatsRepository(context),
                draftTracker = com.cyclecare.core.domain.ads.InMemoryDraftTracker(),
                firstRunNudgeRepository = RoomFirstRunNudgeRepository(migrated),
                timeProvider = SystemTimeProvider(),
                migrationDivergenceRepository = RoomMigrationDivergenceRepository(migrated),
                appLogger = logger,
            )
        }
        composeRule.setContent {
            CycleCareTheme {
                CycleCareApp(container = containerHolder[0]!!)
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * A v2 database with the shape the old app wrote.
     *
     * It carries a profile, two period records, a symptom log for a
     * period day, and a second entry for that same day: the duplicate
     * that the old schema allowed and that the migration collapses.
     * The first period record also holds a cycle length that disagrees
     * with the gap to the next one, which is the other divergence the
     * migration has to record.
     */
    private fun seedV2Database() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CycleCareDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO user_profile (id, ageBand, averageCycleLength, periodLength, " +
                    "lastPeriodStartEpochDay, trackingGoal, remindersEnabled, privacyModeEnabled) " +
                    "VALUES (1, 'Twenties', 28, 5, ${secondPeriodStart.toEpochDay()}, " +
                    "'PeriodPrep', 0, 1)",
            )
            execSQL(
                "INSERT INTO cycle_records (id, startEpochDay, endEpochDay, cycleLength, source, notes) " +
                    "VALUES ('migrated-cycle-1', ${firstPeriodStart.toEpochDay()}, " +
                    "${firstPeriodStart.plusDays(4).toEpochDay()}, 18, 'User', 'Recorded before the update')",
            )
            execSQL(
                "INSERT INTO cycle_records (id, startEpochDay, endEpochDay, cycleLength, source, notes) " +
                    "VALUES ('migrated-cycle-2', ${secondPeriodStart.toEpochDay()}, " +
                    "${secondPeriodStart.plusDays(4).toEpochDay()}, 28, 'User', NULL)",
            )
            execSQL(
                "INSERT INTO symptom_logs (id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, " +
                    "medication, notes) VALUES ('migrated-log-older', ${duplicateLogDate.toEpochDay()}, " +
                    "'Stable', 'None', 'Steady', 'None', NULL, NULL)",
            )
            execSQL(
                "INSERT INTO symptom_logs (id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, " +
                    "medication, notes) VALUES ('migrated-log-newer', ${duplicateLogDate.toEpochDay()}, " +
                    "'Low', 'Light', 'High', 'None', NULL, 'Added later')",
            )
            execSQL(
                "INSERT INTO symptom_logs (id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, " +
                    "medication, notes) VALUES ('migrated-log-single', " +
                    "${firstPeriodStart.plusDays(1).toEpochDay()}, 'Happy', 'Light', 'Steady', 'Dry', " +
                    "'Ibuprofen', NULL)",
            )
            // Every rule is switched off, so the app does not ask for
            // notification permission over the top of the migration.
            execSQL(
                "INSERT INTO reminder_rules (id, type, enabled, offsetDays, hourOfDay, minute) " +
                    "VALUES ('PeriodPrep', 'PeriodPrep', 0, -2, 9, 0)",
            )
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "cyclecare-migrated-v2-launch-test"
    }
}

/**
 * A privacy repository with nothing in it.
 *
 * The real one is backed by a process-wide DataStore that another test
 * may have written a PIN into, and a PIN would put the lock overlay
 * between the test and the screen it is trying to read.
 */
private class MigratedLaunchPrivacyRepository : PrivacyRepository {
    private val consent = ConsentState(
        hideNotificationPreview = true,
        localDataAcknowledged = true,
        appLockEnabled = false,
    )

    override fun observeConsent(): Flow<ConsentState> = flowOf(consent)
    override fun hasPinConfigured(): Flow<Boolean> = flowOf(false)
    override fun observeLastUnlockEpochMillis(): Flow<Long?> = flowOf(null)
    override fun isBiometricEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setAppLockEnabled(enabled: Boolean) = Unit
    override suspend fun setNotificationPreviewHidden(hidden: Boolean) = Unit
    override suspend fun acknowledgeLocalData() = Unit
    override suspend fun setPin(pin: String) = Unit
    override suspend fun clearPin() = Unit
    override suspend fun resetAppLock() = Unit
    override suspend fun validatePin(pin: String): Boolean = false
    override suspend fun setBiometricEnabled(enabled: Boolean) = Unit
    override suspend fun markUnlocked(epochMillis: Long) = Unit
}

/**
 * A scheduler that schedules nothing.
 *
 * The reminders in the fixture are all switched off, so there is
 * nothing to schedule, and a test has no business posting alarms.
 */
/**
 * A scheduler that arms nothing.
 *
 * Named distinctly from the production `NoOpReminderScheduler` in
 * `ReminderCenterScreen.kt`. Both are file-private in Kotlin, so the
 * compiler is happy, but they share a fully qualified name at the
 * bytecode level, and the androidTest APK's copy shadows the production
 * one. A sibling clash on `NoOpReminderRepository` produced a
 * `NoSuchFieldError` at runtime, so neither is left to chance.
 */
private object StubReminderScheduler : ReminderScheduler {
    override suspend fun refreshSchedules(now: ZonedDateTime): List<ScheduledReminder> = emptyList()
}
