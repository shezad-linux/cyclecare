package com.cyclecare.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.data.RoomCycleRepository
import com.cyclecare.core.data.RoomProfileRepository
import com.cyclecare.core.data.RoomReminderRepository
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ReminderDefaults
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.ScheduledReminder
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CycleCareDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun submitTwice_savesBaselineOnlyOnce() = runBlocking {
        val reminderScheduler = RecordingReminderScheduler()
        val viewModel = OnboardingViewModel(
            profileRepository = RoomProfileRepository(database),
            cycleRepository = RoomCycleRepository(database),
            reminderRepository = RoomReminderRepository(database),
            privacyRepository = FakePrivacyRepository(),
            reminderScheduler = reminderScheduler,
            savedStateHandle = SavedStateHandle(),
        )

        val firstCompletion = CompletableDeferred<Unit>()
        viewModel.submit { firstCompletion.complete(Unit) }
        withTimeout(5_000) { firstCompletion.await() }

        val secondCompletion = CompletableDeferred<Unit>()
        viewModel.submit { secondCompletion.complete(Unit) }
        withTimeout(5_000) { secondCompletion.await() }

        assertThat(database.userProfileDao().count()).isEqualTo(1)
        assertThat(database.cycleRecordDao().count()).isEqualTo(1)
        assertThat(database.reminderRuleDao().count())
            .isEqualTo(ReminderDefaults.standardRules(enabled = true, trackingGoal = TrackingGoal.PeriodPrep).size)

        val storedProfile = database.userProfileDao().observeProfile().first()
        val storedCycle = database.cycleRecordDao().observeAll().first().single()

        assertThat(storedProfile?.averageCycleLength).isEqualTo(28)
        assertThat(storedProfile?.periodLength).isEqualTo(5)
        assertThat(storedCycle.startEpochDay).isEqualTo(storedProfile?.lastPeriodStartEpochDay)
        assertThat(reminderScheduler.refreshCalls).isEqualTo(2)
    }
}

private class FakePrivacyRepository : PrivacyRepository {
    private val consent = MutableStateFlow(
        ConsentState(
            hideNotificationPreview = true,
            localDataAcknowledged = false,
            appLockEnabled = false,
        ),
    )
    private val hasPin = MutableStateFlow(false)
    private val lastUnlock = MutableStateFlow<Long?>(null)

    override fun observeConsent(): Flow<ConsentState> = consent.asStateFlow()

    override fun hasPinConfigured(): Flow<Boolean> = hasPin.asStateFlow()

    override fun observeLastUnlockEpochMillis(): Flow<Long?> = lastUnlock.asStateFlow()

    override fun isBiometricEnabled(): Flow<Boolean> = consent.map { it.biometricEnabled }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        consent.value = consent.value.copy(appLockEnabled = enabled)
    }

    override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
        consent.value = consent.value.copy(hideNotificationPreview = hidden)
    }

    override suspend fun acknowledgeLocalData() {
        consent.value = consent.value.copy(localDataAcknowledged = true)
    }

    override suspend fun setPin(pin: String) {
        hasPin.value = true
    }

    override suspend fun clearPin() {
        resetAppLock()
    }

    override suspend fun resetAppLock() {
        hasPin.value = false
        lastUnlock.value = null
        consent.value = consent.value.copy(
            appLockEnabled = false,
            biometricEnabled = false,
        )
    }

    override suspend fun validatePin(pin: String): Boolean = hasPin.value

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        consent.value = consent.value.copy(
            biometricEnabled = enabled && consent.value.appLockEnabled && hasPin.value,
        )
    }

    override suspend fun markUnlocked(epochMillis: Long) {
        lastUnlock.value = epochMillis
    }
}

private class RecordingReminderScheduler : ReminderScheduler {
    var refreshCalls: Int = 0
        private set

    override suspend fun refreshSchedules(now: ZonedDateTime): List<ScheduledReminder> {
        refreshCalls += 1
        return emptyList()
    }
}
