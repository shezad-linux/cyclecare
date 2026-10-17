package com.cyclecare.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.UserProfileEntity
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesIntegrationTest {
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
    fun saveProfileTwice_keepsSingleStoredRow() = runBlocking {
        val repository = RoomProfileRepository(database)

        repository.saveProfile(profile(averageCycleLength = 28, lastPeriodStart = LocalDate.of(2026, 3, 1)))
        repository.saveProfile(profile(averageCycleLength = 31, lastPeriodStart = LocalDate.of(2026, 3, 4)))

        assertThat(database.userProfileDao().count()).isEqualTo(1)
        val stored = repository.observeProfile().first()
        assertThat(stored?.averageCycleLength).isEqualTo(31)
        assertThat(stored?.lastPeriodStart).isEqualTo(LocalDate.of(2026, 3, 4))
    }

    @Test
    fun upsertCycleWithSameId_replacesWithoutDuplicate() = runBlocking {
        val repository = RoomCycleRepository(database)
        val initial = cycle(id = "cycle-1", start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 3, 5))
        val updated = initial.copy(endDate = LocalDate.of(2026, 3, 6), notes = "Updated locally")

        repository.upsertCycle(initial)
        repository.upsertCycle(updated)

        assertThat(database.cycleRecordDao().count()).isEqualTo(1)
        val stored = repository.observeCycles().first().single()
        assertThat(stored.endDate).isEqualTo(LocalDate.of(2026, 3, 6))
        assertThat(stored.notes).isEqualTo("Updated locally")
        assertThat(database.cycleRecordDao().latest()?.id).isEqualTo("cycle-1")
    }

    @Test
    fun deletingCycle_emitsFreshRepositoryState() = runBlocking {
        val repository = RoomCycleRepository(database)
        val emissions = Channel<List<CycleRecord>>(Channel.UNLIMITED)
        val cycle = cycle(id = "cycle-2", start = LocalDate.of(2026, 3, 8), end = LocalDate.of(2026, 3, 12))

        val collectJob = launch {
            repository.observeCycles().collect { emissions.send(it) }
        }

        assertThat(withTimeout(5_000) { emissions.receive() }).isEmpty()

        repository.upsertCycle(cycle)
        assertThat(withTimeout(5_000) { emissions.receive() }.map { it.id }).containsExactly(cycle.id)

        repository.deleteCycle(cycle.id)
        assertThat(withTimeout(5_000) { emissions.receive() }).isEmpty()

        collectJob.cancel()
    }

    @Test
    fun symptomRoundTrip_preservesDateWithoutTimezoneShift() = runBlocking {
        val repository = RoomSymptomRepository(database)
        val original = SymptomLog(
            id = "symptom-1",
            date = LocalDate.of(2024, 2, 29),
            mood = Mood.Happy,
            painLevel = PainLevel.Moderate,
            energyLevel = EnergyLevel.Steady,
            dischargeType = DischargeType.EggWhite,
            medication = "Tea",
            notes = "Leap day log",
        )

        repository.upsertSymptom(original)

        val stored = repository.observeSymptoms().first().single()
        assertThat(stored.date).isEqualTo(original.date)
        assertThat(database.symptomLogDao().count()).isEqualTo(1)
    }

    @Test
    fun observeSymptomForDate_returnsOnlyMatchingEntry() = runBlocking {
        val repository = RoomSymptomRepository(database)
        val today = LocalDate.of(2026, 3, 16)
        repository.upsertSymptom(
            SymptomLog(
                id = "today",
                date = today,
                mood = Mood.Stable,
                painLevel = PainLevel.None,
                energyLevel = EnergyLevel.Steady,
                dischargeType = DischargeType.None,
                medication = null,
                notes = null,
            ),
        )
        repository.upsertSymptom(
            SymptomLog(
                id = "older",
                date = today.minusDays(1),
                mood = Mood.Low,
                painLevel = PainLevel.Light,
                energyLevel = EnergyLevel.Low,
                dischargeType = DischargeType.Dry,
                medication = null,
                notes = null,
            ),
        )

        val stored = repository.observeSymptomForDate(today).first()

        assertThat(stored?.id).isEqualTo("today")
        assertThat(stored?.date).isEqualTo(today)
    }

    @Test
    fun invalidStoredEnums_fallBackToSafeDefaults() = runBlocking {
        database.userProfileDao().upsert(
            UserProfileEntity(
                ageBand = "UnknownAge",
                averageCycleLength = 28,
                periodLength = 5,
                lastPeriodStartEpochDay = LocalDate.of(2026, 3, 1).toEpochDay(),
                trackingGoal = "UnknownGoal",
                remindersEnabled = true,
                privacyModeEnabled = true,
            ),
        )

        val repository = RoomProfileRepository(database)
        val stored = repository.observeProfile().first()

        assertThat(stored?.ageBand).isEqualTo(AgeBand.Twenties)
        assertThat(stored?.trackingGoal).isEqualTo(TrackingGoal.PeriodPrep)
    }

    private fun profile(
        averageCycleLength: Int,
        lastPeriodStart: LocalDate,
    ) = UserProfile(
        ageBand = AgeBand.Twenties,
        averageCycleLength = averageCycleLength,
        periodLength = 5,
        lastPeriodStart = lastPeriodStart,
        trackingGoal = TrackingGoal.PeriodPrep,
        remindersEnabled = true,
        privacyModeEnabled = true,
    )

    private fun cycle(
        id: String,
        start: LocalDate,
        end: LocalDate,
    ) = CycleRecord(
        id = id,
        startDate = start,
        endDate = end,
        cycleLength = 28,
        source = CycleSource.User,
        notes = null,
    )
}
