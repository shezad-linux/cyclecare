package com.cyclecare.core.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.database.ALL_MIGRATIONS
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE
import com.cyclecare.core.database.MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE
import com.cyclecare.core.model.MigrationDivergenceKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The repository's view of a database that has just been migrated.
 *
 * Spec `02` §9.10.1, §9.10.5 and §9.10.6. The migration tests in
 * `core:database` look at the rows; this one looks at the rows through
 * the repositories the app actually reads them with, because that is
 * where a tombstone that is treated as a live row, or a live row that
 * is hidden, would show up.
 *
 * The unique constraint on a date is not in the schema, and that is
 * deliberate: a tombstoned row keeps its date, so a unique index would
 * make "log, delete, log again" fail. The uniqueness is therefore the
 * repository's, over live rows only, and these tests are where that is
 * pinned down.
 */
@RunWith(AndroidJUnit4::class)
class MigratedRepositoryConsistencyTest {

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
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun aSuccessfulMigrationLeavesTheRepositoriesConsistentWithTheSourceData() = runBlocking {
        seedMigratedV2Database()

        val cycleRepository = RoomCycleRepository(database)
        val symptomRepository = RoomSymptomRepository(database)

        val cycles = cycleRepository.observeCycles().first()
        val symptoms = symptomRepository.observeSymptoms().first()

        // What the user recorded before the migration, read back
        // through the repositories the screens use.
        assertThat(cycles.map { it.id }).containsExactly("migrated-cycle-1", "migrated-cycle-2").inOrder()
        assertThat(cycles[0].startDate).isEqualTo(LocalDate.ofEpochDay(20456))
        assertThat(cycles[0].endDate).isEqualTo(LocalDate.ofEpochDay(20460))
        assertThat(cycles[0].notes).isEqualTo("Recorded before the update")
        assertThat(cycles[1].startDate).isEqualTo(LocalDate.ofEpochDay(20485))

        // The duplicate day collapses to the row that was added last.
        assertThat(symptoms.map { it.id }).containsExactly("migrated-log-newer", "migrated-log-single")
        assertThat(symptomRepository.observeSymptomForDate(LocalDate.ofEpochDay(20458)).first()?.id)
            .isEqualTo("migrated-log-newer")
    }

    @Test
    fun theLiveRowIsTheMostRecentDuplicateAndTheOlderRowIsATombstoneThatKeepsItsDate() = runBlocking {
        seedMigratedV2Database()

        // The repository sees one live row for the duplicated date.
        assertThat(database.symptomLogDao().countLiveForDate(20458)).isEqualTo(1)
        assertThat(database.symptomLogDao().observeLatestForDate(20458).first()?.id)
            .isEqualTo("migrated-log-newer")

        // The older row is still there, marked as deleted, with its date
        // and its payload intact. A tombstone is not a deletion: it is
        // what makes the collapse reversible.
        val tombstone = database.symptomLogDao().findById("migrated-log-older")
        assertThat(tombstone).isNotNull()
        assertThat(tombstone?.deletedAtEpochMillis).isNotNull()
        assertThat(tombstone?.dateEpochDay).isEqualTo(20458)
        assertThat(tombstone?.notes).isEqualTo("Added first")
    }

    @Test
    fun liveRowUniquenessHoldsWhileTheDateIndexStaysNonUnique() = runBlocking {
        seedMigratedV2Database()
        val repository = RoomSymptomRepository(database)
        val date = LocalDate.ofEpochDay(20458)

        // One live row for the date, and the tombstones do not count.
        assertThat(database.symptomLogDao().countLiveForDate(20458)).isEqualTo(1)
        // Both rows are still in the table, which is why the index on
        // the date cannot be unique.
        assertThat(database.symptomLogDao().getAllForBackup().count { it.dateEpochDay == 20458L })
            .isEqualTo(2)

        // The user deletes the live row and logs the day again. The
        // tombstone keeps the date, so this only works because the
        // index is not unique.
        database.symptomLogDao().softDelete("migrated-log-newer", 1_000L)
        repository.upsertSymptom(
            com.cyclecare.core.model.SymptomLog(
                id = "logged-again",
                date = date,
                mood = com.cyclecare.core.model.Mood.Stable,
                painLevel = com.cyclecare.core.model.PainLevel.None,
                energyLevel = com.cyclecare.core.model.EnergyLevel.Steady,
                dischargeType = com.cyclecare.core.model.DischargeType.None,
                medication = null,
                notes = "Logged again",
            ),
        )

        // The live row for the date is the new one, and only the new
        // one: the tombstones are still there and are still not live.
        assertThat(database.symptomLogDao().countLiveForDate(20458)).isEqualTo(1)
        assertThat(database.symptomLogDao().observeLatestForDate(20458).first()?.id)
            .isEqualTo("logged-again")
        assertThat(database.symptomLogDao().getAllForBackup().count { it.dateEpochDay == 20458L })
            .isEqualTo(3)
    }

    @Test
    fun theMigrationRecordIsReadableThroughTheRepositoryWithBothValuesKept() = runBlocking {
        seedMigratedV2Database()

        val repository = RoomMigrationDivergenceRepository(database)
        val divergences = repository.observeDivergences().first()

        // The duplicate that was collapsed.
        val duplicate = divergences.first { it.kind == MigrationDivergenceKind.DuplicateSymptomRowCollapsed }
        assertThat(duplicate.dateEpochDay).isEqualTo(20458L)
        assertThat(duplicate.storedValue).isEqualTo(2L)
        assertThat(duplicate.observedValue).isEqualTo(1L)
        assertThat(duplicate.tableName).isEqualTo("symptom_logs")
        assertThat(duplicate.sourceVersion).isEqualTo(2)
        assertThat(duplicate.targetVersion).isEqualTo(3)

        // The cycle length that disagreed with the gap, with both the
        // stored and the observed value in the record.
        val cycleLength = divergences.first { it.kind == MigrationDivergenceKind.CycleLengthDivergence }
        assertThat(cycleLength.recordId).isEqualTo("migrated-cycle-1")
        assertThat(cycleLength.storedValue).isEqualTo(18L)
        assertThat(cycleLength.observedValue).isEqualTo(29L)
        assertThat(cycleLength.tableName).isEqualTo("cycle_records")

        // The wording says what happened and what was kept. It never
        // claims the data was corrected, because it was not.
        listOf(duplicate.detail, cycleLength.detail).forEach { detail ->
            assertThat(detail.lowercase()).doesNotContain("corrected")
            assertThat(detail.lowercase()).doesNotContain("fixed")
            assertThat(detail.lowercase()).doesNotContain("repaired")
        }
        assertThat(cycleLength.detail).contains("legacyCycleLength")
    }

    @Test
    fun theMigrationRecordIsAcknowledgedOnlyWhenTheUserHasSeenIt() = runBlocking {
        seedMigratedV2Database()

        // Everything the migration recorded starts unread, so the record
        // is shown to the user rather than quietly filed away.
        assertThat(database.migrationLogDao().observeUnacknowledged().first()).isNotEmpty()
        assertThat(database.migrationLogDao().countByKind(MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE))
            .isEqualTo(1)
        assertThat(database.migrationLogDao().countByKind(MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE))
            .isEqualTo(1)

        // Once the user has been shown it, it stops asking to be shown.
        assertThat(database.migrationLogDao().acknowledgeAll(1_000L)).isAtLeast(2)
        assertThat(database.migrationLogDao().observeUnacknowledged().first()).isEmpty()
    }

    /**
     * A v2 database with the shape the old app wrote, migrated with the
     * production migration list.
     */
    private fun seedMigratedV2Database() {
        migrationHelper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO user_profile (id, ageBand, averageCycleLength, periodLength, " +
                    "lastPeriodStartEpochDay, trackingGoal, remindersEnabled, privacyModeEnabled) " +
                    "VALUES (1, 'Twenties', 28, 5, 20485, 'PeriodPrep', 1, 1)",
            )
            execSQL(
                "INSERT INTO cycle_records (id, startEpochDay, endEpochDay, cycleLength, source, notes) " +
                    "VALUES ('migrated-cycle-1', 20456, 20460, 18, 'User', 'Recorded before the update')",
            )
            execSQL(
                "INSERT INTO cycle_records (id, startEpochDay, endEpochDay, cycleLength, source, notes) " +
                    "VALUES ('migrated-cycle-2', 20485, 20489, 28, 'User', NULL)",
            )
            execSQL(
                "INSERT INTO symptom_logs (id, dateEpochDay, mood, painLevel, energyLevel, " +
                    "dischargeType, medication, notes) VALUES " +
                    "('migrated-log-older', 20458, 'Stable', 'None', 'Steady', 'None', NULL, 'Added first')",
            )
            execSQL(
                "INSERT INTO symptom_logs (id, dateEpochDay, mood, painLevel, energyLevel, " +
                    "dischargeType, medication, notes) VALUES " +
                    "('migrated-log-newer', 20458, 'Low', 'Light', 'High', 'None', NULL, 'Added later')",
            )
            execSQL(
                "INSERT INTO symptom_logs (id, dateEpochDay, mood, painLevel, energyLevel, " +
                    "dischargeType, medication, notes) VALUES " +
                    "('migrated-log-single', 20457, 'Happy', 'Light', 'Steady', 'Dry', 'Ibuprofen', NULL)",
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE, 3, true, *ALL_MIGRATIONS,
        ).close()

        database = Room.databaseBuilder(context, CycleCareDatabase::class.java, TEST_DATABASE)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private companion object {
        const val TEST_DATABASE = "cyclecare-migrated-repository-test"
    }
}
