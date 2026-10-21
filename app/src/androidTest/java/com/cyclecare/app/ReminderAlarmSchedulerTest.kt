package com.cyclecare.app

import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.domain.prediction.EstimateQuality
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderSchedulePlanner
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.domain.prediction.DateWindow
import com.cyclecare.core.domain.prediction.SamplePrediction
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAlarmSchedulerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var profileRepository: TestProfileRepository
    private lateinit var cycleRepository: TestCycleRepository
    private lateinit var predictionRepository: TestPredictionRepository
    private lateinit var reminderRepository: TestReminderRepository
    private lateinit var reminderChannelSettingsRepository: TestReminderChannelSettingsRepository
    private lateinit var privacyRepository: TestPrivacyRepository
    private lateinit var scheduler: ReminderAlarmScheduler
    private val fakeAlarmFacade = FakeReminderAlarmFacade()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.closeWorkDatabase()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork().result.get()

        profileRepository = TestProfileRepository(
            UserProfile(
                ageBand = com.cyclecare.core.model.AgeBand.Twenties,
                averageCycleLength = 28,
                periodLength = 5,
                lastPeriodStart = LocalDate.of(2026, 3, 10),
                trackingGoal = com.cyclecare.core.model.TrackingGoal.PeriodPrep,
                remindersEnabled = true,
                privacyModeEnabled = true,
            ),
        )
        cycleRepository = TestCycleRepository(emptyList())
        predictionRepository = TestPredictionRepository(prediction())
        reminderRepository = TestReminderRepository(
            listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
                ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
            ),
        )
        reminderChannelSettingsRepository = TestReminderChannelSettingsRepository(
            listOf(
                ReminderChannelSettings(ReminderChannelKey.CycleAlerts, enabled = true, soundEnabled = true, soundPreset = ReminderSoundPreset.GentleBell, vibrationEnabled = true),
                ReminderChannelSettings(ReminderChannelKey.FertilityAlerts, enabled = true, soundEnabled = true, soundPreset = ReminderSoundPreset.WaterDrop, vibrationEnabled = true),
                ReminderChannelSettings(ReminderChannelKey.DailyCheckIn, enabled = true, soundEnabled = true, soundPreset = ReminderSoundPreset.SoftChime, vibrationEnabled = false),
            ),
        )
        privacyRepository = TestPrivacyRepository(
            ConsentState(
                hideNotificationPreview = true,
                localDataAcknowledged = true,
                appLockEnabled = false,
            ),
        )
        scheduler = buildScheduler(fakeAlarmFacade)
        Unit
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        workManager.pruneWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
        Unit
    }

    @Test
    fun refreshSchedules_enqueuesExpectedPeriodFertileAndDailyWork() = runBlocking {
        val schedules = scheduler.refreshSchedules(now = now())

        assertThat(schedules.map { it.ruleId }).containsExactly("fertile", "daily", "period").inOrder()
        assertThat(fakeAlarmFacade.armed).containsExactly(
            "period", ReminderDeliveryTier.EXACT,
            "fertile", ReminderDeliveryTier.INEXACT_WHILE_IDLE,
            "daily", ReminderDeliveryTier.INEXACT_WHILE_IDLE,
        )
        Unit
    }

    @Test
    fun refreshSchedules_whenGlobalRemindersDisabled_cancelsFutureJobs() = runBlocking {
        scheduler.refreshSchedules(now = now())

        profileRepository.updateRemindersEnabled(false)
        val schedules = scheduler.refreshSchedules(now = now())

        assertThat(schedules).isEmpty()
        assertThat(fakeAlarmFacade.cancelledNames).hasSize(3)
        Unit
    }

    @Test
    fun refreshSchedules_whenRuleDisabled_removesOnlyThatRuleWork() = runBlocking {
        scheduler.refreshSchedules(now = now())
        reminderRepository.upsertRule(
            ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = false, daysBefore = 0, hourOfDay = 9, minute = 0),
        )

        val schedules = scheduler.refreshSchedules(now = now())

        assertThat(schedules.map { it.ruleId }).containsExactly("daily", "period").inOrder()
        // The disabled rule's alarm is cancelled, and the other two are
        // still armed.
        assertThat(fakeAlarmFacade.cancelledNames).containsExactly(
            "cyclecare-reminder-rule-fertile",
        )
        assertThat(fakeAlarmFacade.armed.keys).containsExactly("period", "daily")
        Unit
    }

    @Test
    fun refreshSchedules_repeatedCalls_doNotCreateDuplicatePendingWork() = runBlocking {
        scheduler.refreshSchedules(now = now())
        scheduler.refreshSchedules(now = now())

        // Scheduling the same reminder again replaces its alarm rather
        // than adding a second one, so the counts do not grow.
        assertThat(fakeAlarmFacade.armed).containsExactly(
            "period", ReminderDeliveryTier.EXACT,
            "fertile", ReminderDeliveryTier.INEXACT_WHILE_IDLE,
            "daily", ReminderDeliveryTier.INEXACT_WHILE_IDLE,
        )
        Unit
    }

    @Test
    fun refreshSchedules_predictionChange_updatesScheduledDatesWithoutDuplicates() = runBlocking {
        val firstSchedules = scheduler.refreshSchedules(now = now())

        predictionRepository.setPrediction(
            prediction(
                nextPeriodDate = LocalDate.of(2026, 4, 1),
                fertileStart = LocalDate.of(2026, 3, 14),
                fertileEnd = LocalDate.of(2026, 3, 19),
                ovulationDate = LocalDate.of(2026, 3, 18),
                daysUntilNextPeriod = 22,
            ),
        )
        val secondSchedules = scheduler.refreshSchedules(now = now())

        assertThat(secondSchedules.first { it.ruleId == "period" }.scheduledAt)
            .isNotEqualTo(firstSchedules.first { it.ruleId == "period" }.scheduledAt)
        assertThat(secondSchedules.first { it.ruleId == "fertile" }.scheduledAt)
            .isNotEqualTo(firstSchedules.first { it.ruleId == "fertile" }.scheduledAt)
        // The new dates are the ones the alarm now carries.
        assertThat(fakeAlarmFacade.armed).containsExactly(
            "period", ReminderDeliveryTier.EXACT,
            "fertile", ReminderDeliveryTier.INEXACT_WHILE_IDLE,
            "daily", ReminderDeliveryTier.INEXACT_WHILE_IDLE,
        )
        Unit
    }


    // ── the failure cases ───────────────────────────────────────────

    @Test
    fun refreshSchedules_whenExactAlarmsNotGranted_fallsBackToTheInexactTier() = runBlocking {
        // The default path for most users, who never grant the permission.
        val facade = FakeReminderAlarmFacade(exactAlarmsAllowed = false)
        scheduler = buildScheduler(alarmFacade = facade)

        scheduler.refreshSchedules(now = now())

        // Everything is delivered on Tier 2, which needs no permission.
        assertThat(facade.armed.values.toSet())
            .containsExactly(ReminderDeliveryTier.INEXACT_WHILE_IDLE)
        assertThat(facade.armed).hasSize(3)
        Unit
    }

    @Test
    fun refreshSchedules_whenTheExactApiRefuses_downgradesRatherThanCrashing() = runBlocking {
        // The permission can be revoked between the check and the call.
        val facade = FakeReminderAlarmFacade(exactAlarmsAllowed = true).apply {
            exactThrowsSecurityException = true
        }
        scheduler = buildScheduler(alarmFacade = facade)

        scheduler.refreshSchedules(now = now())

        // The exact call was attempted and refused, and the reminder was
        // still delivered, on Tier 2.
        assertThat(facade.armed["period"]).isEqualTo(ReminderDeliveryTier.INEXACT_WHILE_IDLE)
        Unit
    }

    @Test
    fun thePeriodAndLatePeriodTypesAreTheOnlyOnesOnTheExactTier() {
        assertThat(tierFor(ReminderType.PeriodPrep)).isEqualTo(ReminderDeliveryTier.EXACT)
        assertThat(tierFor(ReminderType.LatePeriod)).isEqualTo(ReminderDeliveryTier.EXACT)
        assertThat(tierFor(ReminderType.FertileWindowStart))
            .isEqualTo(ReminderDeliveryTier.INEXACT_WHILE_IDLE)
        assertThat(tierFor(ReminderType.OvulationEstimate))
            .isEqualTo(ReminderDeliveryTier.INEXACT_WHILE_IDLE)
        assertThat(tierFor(ReminderType.DailyLog))
            .isEqualTo(ReminderDeliveryTier.INEXACT_WHILE_IDLE)
        Unit
    }

    @Test
    fun refreshSchedules_armsTheAlarmForThePlannedTime() = runBlocking {
        val schedules = scheduler.refreshSchedules(now = now())

        // The alarm is armed for exactly the time the planner chose, not
        // for a rounded or a recomputed one.
        val period = schedules.first { it.ruleId == "period" }
        assertThat(fakeAlarmFacade.exactTriggerTimes.single())
            .isEqualTo(period.scheduledAt.toInstant().toEpochMilli())
        assertThat(period.scheduledAt).isGreaterThan(now())
        Unit
    }

    @Test
    fun refreshSchedules_whenGlobalRemindersDisabled_cancelsTheArmedAlarms() = runBlocking {
        scheduler.refreshSchedules(now = now())
        fakeAlarmFacade.cancelledNames.clear()

        profileRepository.updateRemindersEnabled(false)
        scheduler.refreshSchedules(now = now())

        assertThat(fakeAlarmFacade.cancelledNames).hasSize(3)
        Unit
    }

    @Test
    fun rescheduleReceiver_enqueuesImmediateRefreshWork() {
        ReminderRescheduleReceiver().onReceive(context, android.content.Intent(android.content.Intent.ACTION_TIMEZONE_CHANGED))

        val infos = workManager.getWorkInfosForUniqueWork(ReminderRefreshWorker.immediateWorkName).get()

        assertThat(infos).isNotEmpty()
        assertThat(infos.any { it.state != WorkInfo.State.CANCELLED }).isTrue()
        Unit
    }

    @Test
    fun rescheduleReceiver_ignoresUnexpectedAction() {
        ReminderRescheduleReceiver().onReceive(context, android.content.Intent("com.cyclecare.UNEXPECTED"))

        val infos = workManager.getWorkInfosForUniqueWork(ReminderRefreshWorker.immediateWorkName).get()

        assertThat(infos).isEmpty()
        Unit
    }

    private fun buildScheduler(alarmFacade: ReminderAlarmFacade): ReminderAlarmScheduler =
        ReminderAlarmScheduler(
            context = context,
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            predictionRepository = predictionRepository,
            reminderRepository = reminderRepository,
            reminderChannelSettingsRepository = reminderChannelSettingsRepository,
            privacyRepository = privacyRepository,
            planner = ReminderSchedulePlanner(),
            alarmFacade = alarmFacade,
        )

    private fun now(): ZonedDateTime = ZonedDateTime.parse("2026-03-10T08:00:00+05:30[Asia/Kolkata]")

    private fun prediction(
        nextPeriodDate: LocalDate = LocalDate.of(2026, 3, 28),
        fertileStart: LocalDate = LocalDate.of(2026, 3, 10),
        fertileEnd: LocalDate = LocalDate.of(2026, 3, 15),
        ovulationDate: LocalDate = LocalDate.of(2026, 3, 14),
        daysUntilNextPeriod: Int = 18,
    ) = SamplePrediction.of(
            generatedForDate = LocalDate.of(2026, 3, 10),
            cycleDay = 1,
            nextPeriodDate = nextPeriodDate,
            ovulationDate = ovulationDate,
            fertileWindow = DateWindow(start = fertileStart, endInclusive = fertileEnd, halfWidthDays = 3),
            quality = EstimateQuality.MODERATE,
            daysUntilNextPeriod = daysUntilNextPeriod,
            // The planner withholds prediction-derived reminders when the
            // engine reports no supportable range, so these fixtures carry
            // a real one. Scheduling itself is what this test exercises.
            nextPeriodWindow = DateWindow(
                start = nextPeriodDate.minusDays(1),
                endInclusive = nextPeriodDate.plusDays(1),
                halfWidthDays = 1,
            ),
        )
}

/**
 * Records the alarms the scheduler arms.
 *
 * The model is the armed state rather than the call history, because the
 * behaviour that matters is whether a reminder ends up with one alarm or
 * two. Re-scheduling the same reminder replaces its alarm, so the armed
 * set does not grow.
 */
private class FakeReminderAlarmFacade(
    private val exactAlarmsAllowed: Boolean = true,
) : ReminderAlarmFacade {
    /** The alarms currently armed, by rule. */
    val armed = linkedMapOf<String, ReminderDeliveryTier>()

    /** The unique name each armed alarm was scheduled under. */
    private val namesByRule = linkedMapOf<String, String>()

    /** The unique names of the alarms that were cancelled. */
    val cancelledNames = mutableListOf<String>()

    /** The trigger times of the exact alarms, in the order they were set. */
    val exactTriggerTimes = mutableListOf<Long>()

    /** Set to make the exact API throw, as a revoked permission would. */
    var exactThrowsSecurityException = false

    override fun canScheduleExactAlarms(): Boolean = exactAlarmsAllowed

    override fun setExactAndAllowWhileIdle(triggerAtMillis: Long, request: ReminderAlarmRequest) {
        if (exactThrowsSecurityException) throw SecurityException("exact alarms not permitted")
        armed[request.ruleId] = ReminderDeliveryTier.EXACT
        namesByRule[request.ruleId] = request.uniqueName
        exactTriggerTimes.add(triggerAtMillis)
    }

    override fun setAndAllowWhileIdle(triggerAtMillis: Long, request: ReminderAlarmRequest) {
        armed[request.ruleId] = ReminderDeliveryTier.INEXACT_WHILE_IDLE
        namesByRule[request.ruleId] = request.uniqueName
    }

    override fun cancel(uniqueName: String) {
        cancelledNames.add(uniqueName)
        // Cancelling addresses the alarm by its unique name, so the armed
        // entry it removes is found the same way.
        namesByRule.entries.firstOrNull { it.value == uniqueName }?.let { (rule, _) ->
            armed.remove(rule)
            namesByRule.remove(rule)
        }
    }
}

private class TestProfileRepository(
    initial: UserProfile?,
) : ProfileRepository {
    private val state = MutableStateFlow(initial)

    override fun observeProfile(): Flow<UserProfile?> = state.asStateFlow()

    override suspend fun saveProfile(profile: UserProfile) {
        state.value = profile
    }

    override suspend fun updateRemindersEnabled(enabled: Boolean) {
        state.value = state.value?.copy(remindersEnabled = enabled)
    }
}

private class TestPredictionRepository(
    initial: CyclePrediction?,
) : PredictionRepository {
    private val state = MutableStateFlow(initial)

    override fun observePrediction(): Flow<CyclePrediction?> = state.asStateFlow()

    fun setPrediction(prediction: CyclePrediction?) {
        state.value = prediction
    }
}

private class TestCycleRepository(
    initial: List<CycleRecord>,
) : CycleRepository {
    private val state = MutableStateFlow(initial)

    override fun observeCycles(): Flow<List<CycleRecord>> = state.asStateFlow()

    override suspend fun upsertCycle(record: CycleRecord) {
        state.value = state.value.filterNot { it.id == record.id } + record
    }

    override suspend fun deleteCycle(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}

private class TestReminderRepository(
    initial: List<ReminderRule>,
) : ReminderRepository {
    private val state = MutableStateFlow(initial)

    override fun observeRules(): Flow<List<ReminderRule>> = state.asStateFlow()

    override suspend fun upsertRule(rule: ReminderRule) {
        state.value = state.value.filterNot { it.id == rule.id } + rule
    }

    override suspend fun setRuleTypeEnabled(type: ReminderType, enabled: Boolean) {
        state.value = state.value.map { rule ->
            if (rule.type == type) rule.copy(enabled = enabled) else rule
        }
    }
}

private class TestReminderChannelSettingsRepository(
    initial: List<ReminderChannelSettings>,
) : ReminderChannelSettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observeChannelSettings(): Flow<List<ReminderChannelSettings>> = state.asStateFlow()

    override suspend fun upsertChannelSettings(settings: ReminderChannelSettings) {
        state.value = state.value.filterNot { it.channelKey == settings.channelKey } + settings
    }
}

private class TestPrivacyRepository(
    initial: ConsentState,
) : PrivacyRepository {
    private val consent = MutableStateFlow(initial)
    private val hasPin = MutableStateFlow(false)
    private val lastUnlock = MutableStateFlow<Long?>(null)

    override fun observeConsent(): Flow<ConsentState> = consent.asStateFlow()

    override fun hasPinConfigured(): Flow<Boolean> = hasPin.asStateFlow()

    override fun observeLastUnlockEpochMillis(): Flow<Long?> = lastUnlock.asStateFlow()

    override fun isBiometricEnabled(): Flow<Boolean> = consent.map { it.biometricEnabled }

    override suspend fun setAppLockEnabled(enabled: Boolean) = Unit

    override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
        consent.value = consent.value.copy(hideNotificationPreview = hidden)
    }

    override suspend fun acknowledgeLocalData() {
        consent.value = consent.value.copy(localDataAcknowledged = true)
    }

    override suspend fun setPin(pin: String) = Unit

    override suspend fun clearPin() = Unit

    override suspend fun resetAppLock() = Unit

    override suspend fun validatePin(pin: String): Boolean = false

    override suspend fun setBiometricEnabled(enabled: Boolean) = Unit

    override suspend fun markUnlocked(epochMillis: Long) {
        lastUnlock.value = epochMillis
    }
}

private fun WorkInfo.isActive(): Boolean = when (state) {
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.RUNNING,
    WorkInfo.State.BLOCKED,
    -> true
    else -> false
}
