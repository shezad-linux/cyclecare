package com.cyclecare.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The data-integrity cases the migration has to get right.
 *
 * Spec `02` §9.4 to §9.7 and the acceptance criteria in §9.10. The
 * cases here are the ones a user would notice if they were wrong: a
 * period that disappears, a cycle length that is silently rewritten, a
 * duplicate day that is quietly deleted, a "logged on" date that never
 * existed, and a migration that fails halfway and takes the user's
 * history with it.
 *
 * Every case is a *shape* rather than a value: the record is checked
 * against what the user actually had, not against what the migration
 * happened to produce.
 */
@RunWith(AndroidJUnit4::class)
class CycleCareDatabaseDataIntegrityTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CycleCareDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    // ── a valid record ──────────────────────────────────────────────

    @Test
    fun aValidRecordMigratesAndSurvivesWithEveryValueIntact() = runTest {
        seedV2(
            cycleRecords = listOf(
                "('jan', 20456, 20460, 28, 'User', 'first note')",
                "('feb', 20485, 20489, 31, 'Imported', NULL)",
            ),
            symptomLogs = listOf(
                "('log-a', 20456, 'Stable', 'None', 'Steady', 'None', 'Ibuprofen', 'kept')",
            ),
        )

        val cycles = migrate2To3AndQuery(
            "SELECT id, startEpochDay, endEpochDay, source, notes, deletedAtEpochMillis " +
                "FROM cycle_records ORDER BY id",
        )
        val logs = migrate2To3AndQuery(
            "SELECT id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, " +
                "medication, notes, deletedAtEpochMillis FROM symptom_logs",
        )

        // The values the user entered, unchanged and still live.
        assertThat(cycles.map { it[0] }).containsExactly("feb", "jan").inOrder()
        assertThat(cycles.map { it[1] }).containsExactly("20485", "20456").inOrder()
        assertThat(cycles.map { it[2] }).containsExactly("20489", "20460").inOrder()
        assertThat(cycles.map { it[3] }).containsExactly("Imported", "User").inOrder()
        assertThat(cycles[0][4]).isNull()
        assertThat(cycles[1][4]).isEqualTo("first note")
        assertThat(cycles.map { it[5] }).containsExactly(null, null)

        assertThat(logs).hasSize(1)
        assertThat(logs[0][0]).isEqualTo("log-a")
        assertThat(logs[0][1]).isEqualTo("20456")
        assertThat(logs[0][2]).isEqualTo("Stable")
        assertThat(logs[0][3]).isEqualTo("None")
        assertThat(logs[0][4]).isEqualTo("Steady")
        assertThat(logs[0][5]).isEqualTo("None")
        assertThat(logs[0][6]).isEqualTo("Ibuprofen")
        assertThat(logs[0][7]).isEqualTo("kept")
        assertThat(logs[0][8]).isNull()
    }

    @Test
    fun aMigratedRecordIsReadableThroughTheDaosAsALiveRow() = runTest {
        seedV2(cycleRecords = listOf("('jan', 20456, 20460, 28, 'User', NULL)"))

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE, 3, true, migration1To2, migration2To3,
        ).close()

        val reopened = Room.databaseBuilder(context, CycleCareDatabase::class.java, TEST_DATABASE)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // The repository-style reads see the record: it is live, it
            // is the latest one, and it is found by the date it covers.
            assertThat(reopened.cycleRecordDao().count()).isEqualTo(1)
            assertThat(reopened.cycleRecordDao().latest()?.id).isEqualTo("jan")
            assertThat(reopened.cycleRecordDao().observeAll().first().map { it.id })
                .containsExactly("jan")
            assertThat(reopened.cycleRecordDao().findForDate(20458)?.id).isEqualTo("jan")
        } finally {
            reopened.close()
        }
    }

    // ── a record with no timestamp of its own ───────────────────────

    @Test
    fun aRecordWithNoCreationTimestampIsBackfilledAndLabelledSynthetic() {
        seedV2(cycleRecords = listOf("('jan', 20456, 20460, 28, 'User', NULL)"))

        val migrationStartedAt = System.currentTimeMillis()
        val rows = migrate2To3AndQuery(
            "SELECT createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis, " +
                "createdAtIsBackfilled FROM cycle_records",
        )
        val migrationFinishedAt = System.currentTimeMillis()

        assertThat(rows).hasSize(1)
        val createdAt = rows[0][0].toString().toLong()
        // The value is the migration time, and only the migration time.
        // It is not a date derived from the record, so nothing can read
        // it as the day the user logged the period.
        assertThat(createdAt).isAtLeast(migrationStartedAt - TOLERANCE_MILLIS)
        assertThat(createdAt).isAtMost(migrationFinishedAt + TOLERANCE_MILLIS)
        assertThat(rows[0][1]).isEqualTo(rows[0][0])
        // No deletion was invented either.
        assertThat(rows[0][2]).isNull()
        // And it says so: the timestamp is marked as a backfill, so
        // nothing downstream can present it as a real creation date.
        assertThat(rows[0][3]).isEqualTo("1")
    }

    // ── a cycle length that disagrees with the gap ──────────────────
    @Test
    fun aGenuinelyUntrackedValueStaysNullAndIsNeverReadAsNone() = runTest {
        // "None" means the user said there was no pain. Null means the
        // user did not say. The two are different answers, and a row
        // written with one must never come back as the other.
        seedV2(symptomLogs = listOf("('none', 20456, 'Stable', 'None', 'Steady', 'None', NULL, NULL)"))

        val migrated = migrate2To3AndQuery(
            "SELECT painLevel, dischargeType FROM symptom_logs WHERE id = 'none'",
        )
        assertThat(migrated).hasSize(1)
        assertThat(migrated[0][0]).isEqualTo("None")
        assertThat(migrated[0][1]).isEqualTo("None")

        // A row written after the migration, with nothing tracked.
        val database = Room.inMemoryDatabaseBuilder(context, CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.symptomLogDao().upsert(
                SymptomLogEntity(
                    id = "untracked",
                    dateEpochDay = 20457,
                    mood = null,
                    painLevel = null,
                    energyLevel = null,
                    dischargeType = null,
                    medication = null,
                    notes = null,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                    deletedAtEpochMillis = null,
                ),
            )

            val stored = database.symptomLogDao().findById("untracked")
            assertThat(stored?.painLevel).isNull()
            assertThat(stored?.dischargeType).isNull()
            assertThat(stored?.mood).isNull()
            assertThat(stored?.energyLevel).isNull()
        } finally {
            database.close()
        }
    }

    @Test
    fun aCycleLengthThatDisagreesWithTheGapIsRecordedAndBothValuesAreKept() {
        seedV2(
            cycleRecords = listOf(
                "('divergent', 100, 104, 28, 'User', 'kept')",
                "('next', 140, 144, 29, 'User', NULL)",
            ),
        )

        val cycles = migrate2To3AndQuery(
            "SELECT id, cycleLength, legacyCycleLength, notes FROM cycle_records ORDER BY id",
        )

        // The gap is 40 days and the stored value was 28. Both are kept:
        // the gap is what the column carries now, and the original is
        // still there in legacyCycleLength.
        assertThat(cycles[0][0]).isEqualTo("divergent")
        assertThat(cycles[0][1]).isEqualTo("40")
        assertThat(cycles[0][2]).isEqualTo("28")
        // The record itself is untouched.
        assertThat(cycles[0][3]).isEqualTo("kept")

        val logs = migrate2To3AndQuery(
            "SELECT kind, recordId, dateEpochDay, storedValue, observedValue, detail " +
                "FROM migration_log WHERE kind = 'CYCLE_LENGTH_DIVERGENCE'",
        )

        // It is recorded, once, with both values in it.
        assertThat(logs).hasSize(1)
        assertThat(logs[0][1]).isEqualTo("divergent")
        assertThat(logs[0][2]).isEqualTo("100")
        assertThat(logs[0][3]).isEqualTo("28")
        assertThat(logs[0][4]).isEqualTo("40")
        // And the wording does not claim anything was corrected. It
        // says what the gap is and that the original was kept.
        val detail = logs[0][5].toString()
        assertThat(detail).contains("legacyCycleLength")
        assertThat(detail.lowercase()).doesNotContain("corrected")
        assertThat(detail.lowercase()).doesNotContain("fixed")
        assertThat(detail.lowercase()).doesNotContain("repaired")
    }

    // ── duplicate dates ─────────────────────────────────────────────

    @Test
    fun duplicateDatesKeepTheMostRecentRowLiveAndTheOlderRowsAsTombstones() {
        seedV2(
            symptomLogs = listOf(
                "('oldest', 20456, 'Stable', 'None', 'Steady', 'None', NULL, 'old')",
                "('middle', 20456, 'Low', 'Light', 'High', 'Creamy', NULL, 'middle')",
                "('newest', 20456, 'High', 'Medium', 'Low', 'None', NULL, 'new')",
                "('solo', 20457, 'Stable', 'None', 'Steady', 'None', NULL, NULL)",
            ),
        )

        val rows = migrate2To3AndQuery(
            "SELECT id, dateEpochDay, notes, deletedAtEpochMillis FROM symptom_logs ORDER BY id",
        )

        // Nothing was deleted: the older rows are still there, with
        // their dates and their payloads, marked as deleted.
        assertThat(rows.map { it[0] })
            .containsExactly("middle", "newest", "oldest", "solo").inOrder()
        assertThat(rows.map { it[1] }).containsExactly("20456", "20456", "20456", "20457")
        assertThat(rows[0][2]).isEqualTo("middle")
        assertThat(rows[2][2]).isEqualTo("old")

        // The most recently inserted row is the live one; the older
        // ones are tombstones.
        assertThat(rows[0][3]).isNotNull()
        assertThat(rows[1][3]).isNull()
        assertThat(rows[2][3]).isNotNull()
        assertThat(rows[3][3]).isNull()

        val live = migrate2To3AndQuery(
            "SELECT id FROM symptom_logs WHERE dateEpochDay = 20456 AND deletedAtEpochMillis IS NULL",
        )
        assertThat(live).hasSize(1)
        assertThat(live[0][0]).isEqualTo("newest")

        // The count is reported, so the user can be told what happened.
        val logs = migrate2To3AndQuery(
            "SELECT dateEpochDay, storedValue, observedValue, detail FROM migration_log " +
                "WHERE kind = 'DUPLICATE_SYMPTOM_DATE'",
        )
        assertThat(logs).hasSize(1)
        assertThat(logs[0][0]).isEqualTo("20456")
        assertThat(logs[0][1]).isEqualTo("3")
        assertThat(logs[0][2]).isEqualTo("1")
        assertThat(logs[0][3].toString()).contains("2 older row(s) were retained as tombstones")
    }

    @Test
    fun theDateIndexIsDeliberatelyNotUniqueSoATombstonedDateCanBeLoggedAgain() = runTest {
        seedV2(
            symptomLogs = listOf(
                "('older', 20456, 'Stable', 'None', 'Steady', 'None', NULL, 'old')",
                "('newer', 20456, 'Low', 'Light', 'High', 'None', NULL, 'new')",
            ),
        )

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE, 3, true, migration1To2, migration2To3,
        ).close()

        val reopened = Room.databaseBuilder(context, CycleCareDatabase::class.java, TEST_DATABASE)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // The index on the date is not unique, on purpose: a
            // tombstoned row keeps its date, so a unique index would
            // make "log, delete, log again" fail.
            val indexList = queryRows(reopened.openHelper.writableDatabase, "PRAGMA index_list(symptom_logs)")
            val dateIndex = indexList.firstOrNull { it[1] == "index_symptom_logs_dateEpochDay" }
            assertThat(dateIndex).isNotNull()
            assertThat(dateIndex?.get(2)).isEqualTo("0")

            // Which is why a new log for a date that already has a
            // tombstone is accepted.
            reopened.symptomLogDao().upsert(
                SymptomLogEntity(
                    id = "logged-again",
                    dateEpochDay = 20456,
                    mood = "Stable",
                    painLevel = null,
                    energyLevel = null,
                    dischargeType = null,
                    medication = null,
                    notes = "Logged again after the delete",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                    deletedAtEpochMillis = null,
                ),
            )

            // The tombstone is still there, with its date.
            val tombstone = reopened.symptomLogDao().findById("older")
            assertThat(tombstone?.deletedAtEpochMillis).isNotNull()
            assertThat(tombstone?.dateEpochDay).isEqualTo(20456)
        } finally {
            reopened.close()
        }
    }

    // ── an aborted migration ────────────────────────────────────────

    @Test
    fun anAbortedMigrationLeavesEverySourceRowIntact() {
        val db = migrationHelper.createDatabase(TEST_DATABASE, 2)
        try {
            db.execSQL(
                "INSERT INTO user_profile (id, ageBand, averageCycleLength, periodLength, " +
                    "lastPeriodStartEpochDay, trackingGoal, remindersEnabled, privacyModeEnabled) " +
                    "VALUES (1, 'Twenties', 28, 5, 20456, 'PeriodPrep', 1, 1)",
            )
            db.execSQL("INSERT INTO cycle_records VALUES ('jan', 20456, 20460, 28, 'User', 'kept')")
            db.execSQL("INSERT INTO cycle_records VALUES ('feb', 20485, 20489, 31, 'Imported', NULL)")
            db.execSQL("INSERT INTO symptom_logs VALUES ('log-a', 20456, 'Stable', 'None', 'Steady', 'None', NULL, 'kept')")

            // The migration runs to completion and is then abandoned:
            // the transaction is never marked successful, so the
            // platform rolls it back. This is the shape of a process
            // death halfway through the migration.
            db.beginTransaction()
            try {
                migrateV2ToV3(db)
            } finally {
                db.endTransaction()
            }

            // Every source row is exactly where it was.
            val cycles = queryRows(db, "SELECT id, startEpochDay, cycleLength, notes FROM cycle_records ORDER BY id")
            assertThat(cycles.map { it[0] }).containsExactly("feb", "jan").inOrder()
            assertThat(cycles.map { it[1] }).containsExactly("20485", "20456").inOrder()
            assertThat(cycles.map { it[2] }).containsExactly("31", "28").inOrder()
            assertThat(cycles[1][3]).isEqualTo("kept")
            assertThat(queryRows(db, "SELECT id FROM symptom_logs").map { it[0] }).containsExactly("log-a")
            assertThat(queryRows(db, "SELECT COUNT(*) FROM user_profile")[0][0]).isEqualTo("1")

            // And the schema is the v2 one again, not a half-migrated
            // mixture of the two.
            val newTables = queryRows(
                db,
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                    "('migration_log', 'sync_outbox', 'app_settings', 'daily_symptoms')",
            )
            assertThat(newTables).isEmpty()
            val cycleColumns = queryRows(db, "PRAGMA table_info(cycle_records)")
            assertThat(cycleColumns.map { it[1] }).doesNotContain("legacyCycleLength")
            assertThat(cycleColumns.map { it[1] }).doesNotContain("createdAtEpochMillis")
        } finally {
            db.close()
        }
    }

    @Test
    fun aFailedMigrationNeverClearsTheDatabase() {
        val db = migrationHelper.createDatabase(TEST_DATABASE, 2)
        try {
            db.execSQL("INSERT INTO cycle_records VALUES ('jan', 20456, 20460, 28, 'User', 'kept')")

            // A verification that fails, as it would if the migration
            // lost a row.
            val failure = try {
                db.beginTransaction()
                migrateV2ToV3(
                    database = db,
                    verifySnapshot = { migratedDb, snapshot ->
                        verifyV2ToV3Snapshot(
                            migratedDb,
                            snapshot.copy(
                                tables = snapshot.tables + (
                                    "cycle_records" to snapshot.tables.getValue("cycle_records")
                                        .copy(rows = emptyMap())
                                    ),
                            ),
                        )
                    },
                )
                null
            } catch (error: IllegalStateException) {
                error
            } finally {
                db.endTransaction()
            }

            assertThat(failure).isNotNull()
            // The user's row is still there. A failed migration must
            // never be the reason a user's history disappears.
            val cycles = queryRows(db, "SELECT id, notes FROM cycle_records")
            assertThat(cycles.map { it[0] }).containsExactly("jan")
            assertThat(cycles[0][1]).isEqualTo("kept")
        } finally {
            db.close()
        }
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun seedV2(
        cycleRecords: List<String> = emptyList(),
        symptomLogs: List<String> = emptyList(),
    ) {
        migrationHelper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO user_profile (id, ageBand, averageCycleLength, periodLength, " +
                    "lastPeriodStartEpochDay, trackingGoal, remindersEnabled, privacyModeEnabled) " +
                    "VALUES (1, 'Twenties', 28, 5, 20456, 'PeriodPrep', 1, 1)",
            )
            cycleRecords.forEach { execSQL("INSERT INTO cycle_records VALUES $it") }
            symptomLogs.forEach { execSQL("INSERT INTO symptom_logs VALUES $it") }
            close()
        }
    }

    private fun migrate2To3AndQuery(sql: String): List<List<Any?>> =
        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE, 3, true, migration1To2, migration2To3,
        ).use { db -> queryRows(db, sql) }

    private fun queryRows(database: SupportSQLiteDatabase, sql: String): List<List<Any?>> =
        database.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { cursor.getString(it) })
                }
            }
        }

    private companion object {
        const val TEST_DATABASE = "cyclecare-data-integrity-test"

        /** A second of slack for the clock between the two readings. */
        const val TOLERANCE_MILLIS = 1_000L
    }
}
