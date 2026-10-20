package com.cyclecare.app

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import com.cyclecare.core.domain.ads.InMemoryDraftTracker
import com.cyclecare.core.domain.InMemoryReminderIntelligenceRepository
import com.cyclecare.core.domain.time.SystemTimeProvider
import com.cyclecare.core.domain.time.TimeProvider
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.ImportResult
import java.io.InputStream
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.SyncAdapter
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.ScheduledReminder
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application by lazy { context.applicationContext as CycleCareApplication }
    private lateinit var originalContainer: AppContainer

    @Before
    fun setUp() {
        originalContainer = application.appContainer
    }

    @After
    fun tearDown() {
        application.replaceAppContainerForTesting(originalContainer)
    }

    @Test
    fun doWork_foregroundUnlocked_recordsEventWithoutPostingSystemNotification() = runBlocking {
        val env = WorkerTestEnvironment(
            isForeground = true,
            canPostNotifications = true,
        )
        application.replaceAppContainerForTesting(env.container)

        val result = buildWorker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(env.reminderEventsRepository.recordedEvents).hasSize(1)
        assertThat(env.notificationClient.postedNotifications).isEmpty()
        assertThat(env.notificationClient.syncCalls).isEqualTo(0)
        assertThat(env.reminderScheduler.refreshCallCount).isEqualTo(1)
    }

    @Test
    fun doWork_notificationsBlocked_recordsEventWithoutPostingSystemNotification() = runBlocking {
        val env = WorkerTestEnvironment(
            isForeground = false,
            canPostNotifications = false,
        )
        application.replaceAppContainerForTesting(env.container)

        val result = buildWorker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(env.reminderEventsRepository.recordedEvents).hasSize(1)
        assertThat(env.notificationClient.postedNotifications).isEmpty()
        assertThat(env.notificationClient.syncCalls).isEqualTo(0)
        assertThat(env.reminderScheduler.refreshCallCount).isEqualTo(1)
    }

    @Test
    fun doWork_channelSyncFailure_returnsRetry() = runBlocking {
        val env = WorkerTestEnvironment(
            isForeground = false,
            canPostNotifications = true,
        ).apply {
            notificationClient.syncFailure = IllegalStateException("channel sync failed")
        }
        application.replaceAppContainerForTesting(env.container)

        val result = buildWorker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(env.reminderEventsRepository.recordedEvents).hasSize(1)
        assertThat(env.notificationClient.syncCalls).isEqualTo(1)
        assertThat(env.notificationClient.postedNotifications).isEmpty()
        assertThat(env.reminderScheduler.refreshCallCount).isEqualTo(0)
    }

    @Test
    fun doWork_postFailure_returnsRetry() = runBlocking {
        val env = WorkerTestEnvironment(
            isForeground = false,
            canPostNotifications = true,
        ).apply {
            notificationClient.postFailure = IllegalStateException("notify failed")
        }
        application.replaceAppContainerForTesting(env.container)

        val result = buildWorker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(env.reminderEventsRepository.recordedEvents).hasSize(1)
        assertThat(env.notificationClient.syncCalls).isEqualTo(1)
        assertThat(env.notificationClient.postedNotifications).isEmpty()
        assertThat(env.reminderScheduler.refreshCallCount).isEqualTo(0)
    }

    @Test
    fun doWork_postPermissionUnavailable_recordsEventWithoutRetrying() = runBlocking {
        val env = WorkerTestEnvironment(
            isForeground = false,
            canPostNotifications = true,
        ).apply {
            notificationClient.postPermissionUnavailable = true
        }
        application.replaceAppContainerForTesting(env.container)
        val refreshCallsBefore = env.reminderScheduler.refreshCallCount

        val result = buildWorker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(env.reminderEventsRepository.recordedEvents).hasSize(1)
        assertThat(env.notificationClient.postedNotifications).isEmpty()
        assertThat(env.reminderScheduler.refreshCallCount).isAtLeast(refreshCallsBefore + 1)
    }

    @Test
    fun doWork_backgroundAllowed_postsPrivateNotificationWithAppIcon() = runBlocking {
        val env = WorkerTestEnvironment(
            isForeground = false,
            canPostNotifications = true,
            hideNotificationPreview = true,
        )
        application.replaceAppContainerForTesting(env.container)

        val result = buildWorker().doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(env.notificationClient.postedNotifications).hasSize(1)
        val posted = env.notificationClient.postedNotifications.single()
        assertThat(posted.id).isEqualTo(42)
        assertThat(posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            .isEqualTo("Cycle reminder")
        assertThat(posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            .isEqualTo("Cycle reminder available. Open the app to review it.")
        assertThat(posted.notification.smallIcon.resId).isEqualTo(R.drawable.ic_notification_small)
        assertThat(env.reminderScheduler.refreshCallCount).isEqualTo(1)
    }

private val canonicalWorkerData: Map<String, Any> = mapOf(
    ReminderDeliveryWorker.keyNotificationId to 42,
    ReminderDeliveryWorker.keyTitle to "Period reminder",
    ReminderDeliveryWorker.keyBody to "Your period may start soon.",
    ReminderDeliveryWorker.keyPrivateBody to "Cycle reminder available. Open the app to review it.",
    ReminderDeliveryWorker.keyRuleId to "period",
    ReminderDeliveryWorker.keyReminderType to ReminderType.PeriodPrep.name,
    ReminderDeliveryWorker.keyChannelKey to ReminderChannelKey.CycleAlerts.name,
    ReminderDeliveryWorker.keyDeepLinkTarget to ReminderDeepLinkTarget.Home.name,
    ReminderDeliveryWorker.keyTargetEpochDay to LocalDate.of(2026, 3, 20).toEpochDay(),
    ReminderDeliveryWorker.keyScheduledAtEpochMillis to ZonedDateTime.parse("2026-03-17T09:00:00+05:30[Asia/Kolkata]").toInstant().toEpochMilli(),
    // Carried so the scoring gates see a two-day lead: a clean-slate
    // engagement table costs the candidate its fatigue penalty, and without
    // a plausible lead the default fixture would sit under the threshold.
    ReminderDeliveryWorker.keyLeadDays to 2,
)

private fun buildWorker(): ReminderDeliveryWorker =
    TestListenableWorkerBuilder<ReminderDeliveryWorker>(application)
        .setInputData(workDataOf(*canonicalWorkerData.entries.map { it.toPair() }.toTypedArray()))
        .build()
}

private class WorkerTestEnvironment(
    isForeground: Boolean,
    canPostNotifications: Boolean,
    hideNotificationPreview: Boolean = true,
) {
    val reminderEventsRepository = WorkerFakeReminderEventsRepository()
    val notificationClient = FakeReminderNotificationClient()
    val reminderScheduler = RecordingReminderScheduler()
    private val privacyRepository = WorkerFakePrivacyRepository(
        ConsentState(
            hideNotificationPreview = hideNotificationPreview,
            localDataAcknowledged = true,
            appLockEnabled = false,
        ),
    )
    private val notificationPermissionController = WorkerFakeNotificationPermissionController(
        NotificationPermissionStatus(
            runtimePermissionRequired = false,
            runtimePermissionGranted = canPostNotifications,
            notificationsEnabled = canPostNotifications,
            requestAttempted = !canPostNotifications,
            canRequestRuntimePermission = false,
        ),
    )
    private val appVisibilityObserver = WorkerFakeAppVisibilityObserver(isForeground)

    val container = AppContainer(
        profileRepository = NoOpProfileRepository(),
        cycleRepository = NoOpCycleRepository(),
        symptomRepository = NoOpSymptomRepository(),
        predictionRepository = WorkerFakePredictionRepository(),
        insightRepository = NoOpInsightRepository(),
        reminderRepository = NoOpReminderRepository(),
        reminderChannelSettingsRepository = WorkerFakeReminderChannelSettingsRepository(),
        reminderEventsRepository = reminderEventsRepository,
        reminderIntelligenceRepository = InMemoryReminderIntelligenceRepository(),
        privacyRepository = privacyRepository,
        exportRepository = NoOpExportRepository(),
        appResetRepository = NoOpAppResetRepository(),
        reminderScheduler = reminderScheduler,
        notificationPermissionController = notificationPermissionController,
        appVisibilityObserver = appVisibilityObserver,
        reminderNavigationCoordinator = InMemoryReminderNavigationCoordinator(),
        syncAdapter = NoOpSyncAdapterImpl(),
        interstitialStatsRepository = FakeInterstitialStatsRepository(),
        draftTracker = InMemoryDraftTracker(),
        firstRunNudgeRepository = FakeFirstRunNudgeRepository(),
        // The gates run against a pinned noon, so quiet-hours behaviour is
        // stable no matter when the emulator happens to run the suite.
        timeProvider = WorkerFixedClock(),
        reminderNotificationClient = notificationClient,
        appLogger = NoOpAppLogger,
    )
}

/** A clock pinned to nine minutes past noon local, on the day the tests run. */
private class WorkerFixedClock : TimeProvider {
    private val moment = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
        .withHour(12)
        .withMinute(9)
        .withSecond(0)
        .withNano(0)

    override fun today(): LocalDate = moment.toLocalDate()

    override fun now(): ZonedDateTime = moment

    override fun nowEpochMillis(): Long = moment.toInstant().toEpochMilli()
}

/**
 * A prediction the scoring gates treat as real: a supported range with high
 * quality, so the quality bonus does not suppress a candidate the test is
 * trying to prove posts.
 */
private class WorkerFakePredictionRepository : PredictionRepository {
    private val snapshot = PredictionSnapshot(
        generatedAt = LocalDate.of(2026, 3, 17),
        cycleDay = 15,
        nextPeriodDate = LocalDate.of(2026, 3, 24),
        ovulationDate = LocalDate.of(2026, 3, 19),
        fertileWindow = com.cyclecare.core.model.DateRange(
            start = LocalDate.of(2026, 3, 17),
            endInclusive = LocalDate.of(2026, 3, 22),
        ),
        confidenceLabel = "test",
        daysUntilNextPeriod = 7,
        nextPeriodWindow = com.cyclecare.core.model.DateRange(
            start = LocalDate.of(2026, 3, 23),
            endInclusive = LocalDate.of(2026, 3, 25),
        ),
        nextPeriodHalfWidthDays = 1,
        quality = com.cyclecare.core.model.PredictionQuality.HIGH,
    )

    override fun observePrediction(): Flow<PredictionSnapshot?> = MutableStateFlow(snapshot).asStateFlow()
}

private class FakeReminderNotificationClient : ReminderNotificationClient {
    data class PostedNotification(
        val id: Int,
        val notification: Notification,
    )

    var syncCalls: Int = 0
    var syncFailure: Throwable? = null
    var postFailure: Throwable? = null
    var postPermissionUnavailable: Boolean = false
    val postedNotifications = mutableListOf<PostedNotification>()

    override fun syncChannels(settings: List<ReminderChannelSettings>): Result<Unit> {
        syncCalls += 1
        val failure = syncFailure
        return if (failure == null) Result.success(Unit) else Result.failure(failure)
    }

    override fun post(notificationId: Int, notification: Notification): ReminderNotificationPostResult {
        if (postPermissionUnavailable) return ReminderNotificationPostResult.PermissionUnavailable
        val failure = postFailure
        return if (failure == null) {
            postedNotifications += PostedNotification(notificationId, notification)
            ReminderNotificationPostResult.Posted
        } else {
            ReminderNotificationPostResult.Failed(failure)
        }
    }
}

private class WorkerFakeReminderEventsRepository : ReminderEventsRepository {
    private val state = MutableStateFlow<List<ReminderEvent>>(emptyList())
    val recordedEvents: List<ReminderEvent> get() = state.value

    /** What the dedup and budget gates see. */
    var newlyInserted: Boolean = true
    var deliveredBetweenWindow: Int = 0

    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> = state.map { it.take(limit) }

    override fun observeUnreadCount(): Flow<Int> = state.map { events -> events.count { it.readAtEpochMillis == null } }

    override fun observeNextUnread(): Flow<ReminderEvent?> = state.map { events ->
        events.firstOrNull { it.readAtEpochMillis == null && it.dismissedAtEpochMillis == null }
    }

    override suspend fun recordEvent(event: ReminderEvent) {
        state.value = listOf(event) + state.value.filterNot { it.id == event.id }
    }

    override suspend fun markRead(id: String) = Unit

    override suspend fun markAllRead() = Unit

    override suspend fun dismiss(id: String) = Unit

    override suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long? =
        state.value
            .filter { it.deliveredAt.toInstant().toEpochMilli() >= sinceEpochMillis }
            .maxOfOrNull { it.deliveredAt.toInstant().toEpochMilli() }

    override suspend fun insertIfAbsent(event: ReminderEvent): Boolean =
        newlyInserted.also { if (it) recordEvent(event) }

    override suspend fun countDeliveredBetween(fromEpochMillis: Long, toEpochMillis: Long): Int =
        deliveredBetweenWindow

    override suspend fun countDeliveredForType(type: ReminderType): Int =
        state.value.count { it.type == type }

    override suspend fun countDismissedForType(type: ReminderType): Int = 0

    override suspend fun countOpenedForType(type: ReminderType): Int = 0
}

private class WorkerFakeReminderChannelSettingsRepository : ReminderChannelSettingsRepository {
    private val state = MutableStateFlow(
        listOf(
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.CycleAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.GentleBell,
                vibrationEnabled = true,
            ),
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.FertilityAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.WaterDrop,
                vibrationEnabled = true,
            ),
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.DailyCheckIn,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.SoftChime,
                vibrationEnabled = false,
            ),
        ),
    )

    override fun observeChannelSettings(): Flow<List<ReminderChannelSettings>> = state.asStateFlow()

    override suspend fun upsertChannelSettings(settings: ReminderChannelSettings) = Unit
}

private class WorkerFakeNotificationPermissionController(
    private val currentStatus: NotificationPermissionStatus,
) : NotificationPermissionController {
    override fun status(activity: androidx.activity.ComponentActivity?): NotificationPermissionStatus = currentStatus

    override fun markRequestAttempted() = Unit

    override fun notificationSettingsIntent() = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
}

private class WorkerFakeAppVisibilityObserver(isForeground: Boolean) : AppVisibilityObserver {
    override val isForeground: StateFlow<Boolean> = MutableStateFlow(isForeground).asStateFlow()
}

private class WorkerFakePrivacyRepository(
    consentState: ConsentState,
) : PrivacyRepository {
    private val consent = MutableStateFlow(consentState)
    private val hasPin = MutableStateFlow(false)
    private val lastUnlock = MutableStateFlow<Long?>(System.currentTimeMillis())

    override fun observeConsent(): Flow<ConsentState> = consent.asStateFlow()

    override fun hasPinConfigured(): Flow<Boolean> = hasPin.asStateFlow()

    override fun observeLastUnlockEpochMillis(): Flow<Long?> = lastUnlock.asStateFlow()

    override fun isBiometricEnabled(): Flow<Boolean> = consent.map { it.biometricEnabled }

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

private class NoOpProfileRepository : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = MutableStateFlow<UserProfile?>(null).asStateFlow()

    override suspend fun saveProfile(profile: UserProfile) = Unit

    override suspend fun updateRemindersEnabled(enabled: Boolean) = Unit
}

private class NoOpCycleRepository : CycleRepository {
    override fun observeCycles(): Flow<List<CycleRecord>> = MutableStateFlow(emptyList<CycleRecord>()).asStateFlow()

    override suspend fun upsertCycle(record: CycleRecord) = Unit

    override suspend fun deleteCycle(id: String) = Unit
}

private class NoOpSymptomRepository : SymptomRepository {
    override fun observeSymptoms(): Flow<List<SymptomLog>> = MutableStateFlow(emptyList<SymptomLog>()).asStateFlow()

    override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> = MutableStateFlow<SymptomLog?>(null).asStateFlow()

    override suspend fun upsertSymptom(log: SymptomLog) = Unit
}

private class NoOpPredictionRepository : PredictionRepository {
    override fun observePrediction(): Flow<PredictionSnapshot?> = MutableStateFlow<PredictionSnapshot?>(null).asStateFlow()
}

private class NoOpInsightRepository : InsightRepository {
    override fun observeInsights(): Flow<InsightSnapshot> = MutableStateFlow(
        InsightSnapshot(
            averageCycleLength = 28,
            averagePeriodLength = 5,
            irregularCycles = false,
            recentCycleLengths = emptyList(),
            recentPeriodLengths = emptyList(),
            minCycleLength = 28,
            maxCycleLength = 28,
            cycleWindowLabel = "",
            hasEnoughCycleData = false,
            moodCounts = mapOf(
                Mood.Stable to 0,
                Mood.Happy to 0,
                Mood.Low to 0,
                Mood.Irritable to 0,
                Mood.Anxious to 0,
            ),
            energyCounts = mapOf(
                EnergyLevel.Low to 0,
                EnergyLevel.Steady to 0,
                EnergyLevel.High to 0,
            ),
            painCounts = mapOf(
                PainLevel.None to 0,
                PainLevel.Light to 0,
                PainLevel.Moderate to 0,
                PainLevel.Strong to 0,
            ),
            dischargeCounts = mapOf(
                DischargeType.None to 0,
                DischargeType.Dry to 0,
                DischargeType.Creamy to 0,
                DischargeType.Sticky to 0,
                DischargeType.EggWhite to 0,
            ),
            phaseHighlights = emptyList(),
            hasEnoughSymptomData = false,
            narrativeSummary = "",
        ),
    ).asStateFlow()
}

private class NoOpReminderRepository : ReminderRepository {
    override fun observeRules(): Flow<List<ReminderRule>> = MutableStateFlow(emptyList<ReminderRule>()).asStateFlow()

    override suspend fun upsertRule(rule: ReminderRule) = Unit

    override suspend fun setRuleTypeEnabled(type: ReminderType, enabled: Boolean) = Unit
}

private class NoOpExportRepository : ExportRepository {
    override suspend fun createReport(format: ExportFormat): ExportArtifact =
        ExportArtifact(
            format = format,
            displayName = "noop",
            absolutePath = "/tmp/noop",
            mimeType = "text/plain",
            generatedAt = LocalDate.of(2026, 3, 17),
        )

    override suspend fun createEncryptedBackup(passphrase: CharArray): ExportArtifact =
        ExportArtifact(
            format = ExportFormat.EncryptedBackup,
            displayName = "noop.ccvault",
            absolutePath = "/tmp/noop.ccvault",
            mimeType = "application/octet-stream",
            generatedAt = LocalDate.of(2026, 3, 17),
        )

    override suspend fun importEncryptedBackup(inputStream: InputStream, passphrase: CharArray): ImportResult =
        ImportResult.Success(0, 0, false)
}

private class NoOpAppResetRepository : AppResetRepository {
    override suspend fun resetAllLocalData() = Unit
}

private class RecordingReminderScheduler : ReminderScheduler {
    var refreshCallCount: Int = 0
        private set
    var lastRefreshAt: ZonedDateTime? = null
        private set

    override suspend fun refreshSchedules(now: ZonedDateTime): List<ScheduledReminder> {
        refreshCallCount += 1
        lastRefreshAt = now
        return emptyList()
    }
}

private class NoOpSyncAdapterImpl : SyncAdapter {
    override suspend fun pushPendingChanges() = Unit

    override suspend fun pullRemoteChanges() = Unit

    override suspend fun mergePreview(): String = "disabled"
}
