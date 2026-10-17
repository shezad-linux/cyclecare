package com.cyclecare.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleCareDatabaseRoomTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CycleCareDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

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
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun userProfileUpsert_replacesSingletonRow() = runTest {
        database.userProfileDao().upsert(profileEntity(ageBand = "Twenties", averageCycleLength = 28))
        database.userProfileDao().upsert(profileEntity(ageBand = "Thirties", averageCycleLength = 31))

        assertThat(database.userProfileDao().count()).isEqualTo(1)
        val stored = database.userProfileDao().observeProfile().first()
        assertThat(stored?.ageBand).isEqualTo("Thirties")
        assertThat(stored?.averageCycleLength).isEqualTo(31)
    }

    @Test
    fun cycleQueries_keepDeterministicSortOrderAndReturnLatestRecord() = runTest {
        val january = cycleEntity(id = "jan", start = LocalDate.of(2026, 1, 3), end = LocalDate.of(2026, 1, 7))
        val march = cycleEntity(id = "mar", start = LocalDate.of(2026, 3, 2), end = LocalDate.of(2026, 3, 6))
        val february = cycleEntity(id = "feb", start = LocalDate.of(2026, 2, 1), end = LocalDate.of(2026, 2, 5))

        database.cycleRecordDao().upsert(march)
        database.cycleRecordDao().upsert(january)
        database.cycleRecordDao().upsert(february)

        val ordered = database.cycleRecordDao().observeAll().first()
        assertThat(ordered.map { it.id }).containsExactly("jan", "feb", "mar").inOrder()
        assertThat(database.cycleRecordDao().latest()?.id).isEqualTo("mar")
        assertThat(database.cycleRecordDao().findForDate(LocalDate.of(2026, 2, 4).toEpochDay())?.id).isEqualTo("feb")
    }

    @Test
    fun deletingCycle_removesStoredRecordAndDateLookup() = runTest {
        val cycle = cycleEntity(id = "delete-me", start = LocalDate.of(2026, 3, 10), end = LocalDate.of(2026, 3, 14))
        database.cycleRecordDao().upsert(cycle)

        database.cycleRecordDao().delete(cycle.id)

        assertThat(database.cycleRecordDao().count()).isEqualTo(0)
        assertThat(database.cycleRecordDao().findForDate(LocalDate.of(2026, 3, 12).toEpochDay())).isNull()
        assertThat(database.cycleRecordDao().latest()).isNull()
    }

    @Test
    fun symptomLogs_roundTripEpochDaysWithoutTimezoneShift() = runTest {
        val leapDay = LocalDate.of(2024, 2, 29)
        database.symptomLogDao().upsert(
            SymptomLogEntity(
                id = "leap",
                dateEpochDay = leapDay.toEpochDay(),
                mood = "Stable",
                painLevel = "Light",
                energyLevel = "Steady",
                dischargeType = "None",
                medication = null,
                notes = "Round trip",
            ),
        )

        val stored = database.symptomLogDao().observeAll().first().single()
        assertThat(LocalDate.ofEpochDay(stored.dateEpochDay)).isEqualTo(leapDay)
        assertThat(stored.dateEpochDay).isEqualTo(leapDay.toEpochDay())
    }

    @Test
    fun exportedSchema_canOpenCurrentVersionDatabase() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).close()

        Room.databaseBuilder(context, CycleCareDatabase::class.java, TEST_DATABASE)
            .build()
            .apply {
                openHelper.writableDatabase.close()
                close()
            }
    }

    private fun profileEntity(
        ageBand: String,
        averageCycleLength: Int,
    ) = UserProfileEntity(
        ageBand = ageBand,
        averageCycleLength = averageCycleLength,
        periodLength = 5,
        lastPeriodStartEpochDay = LocalDate.of(2026, 3, 1).toEpochDay(),
        trackingGoal = "PeriodPrep",
        remindersEnabled = true,
        privacyModeEnabled = true,
    )

    private fun cycleEntity(
        id: String,
        start: LocalDate,
        end: LocalDate,
    ) = CycleRecordEntity(
        id = id,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end.toEpochDay(),
        cycleLength = 28,
        source = "User",
        notes = null,
    )

    private companion object {
        const val TEST_DATABASE = "cyclecare-room-schema-test"
    }
}
