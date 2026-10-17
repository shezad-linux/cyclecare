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

        database.cycleRecordDao().softDelete(cycle.id, 1234L)

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
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
                deletedAtEpochMillis = null,
            ),
        )

        val stored = database.symptomLogDao().observeAll().first().single()
        assertThat(LocalDate.ofEpochDay(stored.dateEpochDay)).isEqualTo(leapDay)
        assertThat(stored.dateEpochDay).isEqualTo(leapDay.toEpochDay())
    }

    // ── migration1To2 ────────────────────────────────────────────────
    //
    // migration1To2 has never had coverage. It rebuilds reminder_rules to
    // convert a positive daysBefore into a signed offsetDays, renames the
    // OvulationEstimate reminder type, creates reminder_events, and seeds
    // two rules based on the user's tracking goal.

    @Test
    fun migration1To2_producesSchemaMatchingVersion2() {
        seedV1(reminderRules = emptyList())

        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 2, true, migration1To2)
    }

    // ── migration2To3 ───────────────────────────────────────────────

    @Test
    fun migration2To3_producesSchemaMatchingVersion3() {
        seedV2(reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, -2, 9, 0)"))

        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 3, true, migration1To2, migration2To3)
    }

    @Test
    fun migration2To3_preservesEveryCycleRecordUnchanged() {
        seedV2(
            cycleRecords = listOf(
                "('jan', 20456, 20460, 28, 'User', 'first note')",
                "('feb', 20485, 20489, 31, 'Imported', NULL)",
            ),
        )

        val rows = migrate2To3AndQuery(
            "SELECT id, startEpochDay, endEpochDay, cycleLength, source, notes, deletedAtEpochMillis FROM cycle_records ORDER BY id",
        )

        assertThat(rows.map { it[0] }).containsExactly("feb", "jan").inOrder()
        assertThat(rows.map { it[1] }).containsExactly("20485", "20456").inOrder()
        assertThat(rows.map { it[2] }).containsExactly("20489", "20460").inOrder()
        assertThat(rows.map { it[4] }).containsExactly("Imported", "User").inOrder()
        assertThat(rows.last()[5]).isEqualTo("first note")
        assertThat(rows.first()[5]).isNull()
        assertThat(rows.map { it[6] }).containsExactly(null, null)
    }

    @Test
    fun migration2To3_preservesNoneAsNoneRatherThanNull() {
        seedV2(
            symptomLogs = listOf(
                "('log-a', 20456, 'Stable', 'None', 'Steady', 'None', NULL, 'kept')",
                "('log-b', 20457, 'Low', 'Light', 'High', 'Creamy', 'Ibuprofen', NULL)",
            ),
        )

        val rows = migrate2To3AndQuery(
            "SELECT id, mood, painLevel, energyLevel, dischargeType, medication, notes FROM symptom_logs ORDER BY id",
        )

        // "None" is a recorded value meaning explicitly none. It must NOT
        // become NULL, which would mean not tracked.
        assertThat(rows[0][2]).isEqualTo("None")
        assertThat(rows[0][4]).isEqualTo("None")
        assertThat(rows[0][1]).isEqualTo("Stable")
        assertThat(rows[1][2]).isEqualTo("Light")
        assertThat(rows[1][6]).isNull()
        assertThat(rows[0][6]).isEqualTo("kept")
    }

    @Test
    fun migration2To3_backfillsAuditTimestampsAndNoDeletions() {
        seedV2(
            symptomLogs = listOf("('log-a', 20456, 'Stable', 'None', 'Steady', 'None', NULL, NULL)"),
            cycleRecords = listOf("('jan', 20456, 20460, 28, 'User', NULL)"),
        )

        val logs = migrate2To3AndQuery("SELECT createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis FROM symptom_logs")
        val cycles = migrate2To3AndQuery("SELECT createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis FROM cycle_records")

        listOf(logs, cycles).forEach { rows ->
            assertThat(rows).hasSize(1)
            assertThat(rows[0][0].toString().toLong()).isGreaterThan(0L)
            assertThat(rows[0][1].toString().toLong()).isGreaterThan(0L)
            assertThat(rows[0][2]).isNull()
        }
    }

    @Test
    fun migration2To3_collapsesDuplicateSymptomRowsForOneDate() {
        seedV2(
            symptomLogs = listOf(
                "('older', 20456, 'Stable', 'None', 'Steady', 'None', NULL, NULL)",
                "('newer', 20456, 'Low', 'Light', 'High', 'None', NULL, NULL)",
            ),
        )

        val rows = migrate2To3AndQuery("SELECT id FROM symptom_logs")

        assertThat(rows).hasSize(1)
        assertThat(rows[0][0]).isEqualTo("newer")
    }

    @Test
    fun migration2To3_addsUserPinnedTimeDefaultingToFalse() {
        seedV2(reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, -2, 9, 0)"))

        val rows = migrate2To3AndQuery("SELECT id, userPinnedTime FROM reminder_rules")

        assertThat(rows).hasSize(1)
        assertThat(rows[0][1].toString()).isEqualTo("0")
    }

    @Test
    fun migration2To3_addsDeliveryStateAndBackfillsExistingEvents() {
        seedV2(
            reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, -2, 9, 0)"),
            reminderEvents = listOf(
                "('ev-1', 'PeriodPrep', 'PeriodPrep', 'CycleAlerts', 1000, 2000, 't', 'b', 'p', 'Home', NULL, NULL, NULL)",
            ),
        )

        val rows = migrate2To3AndQuery("SELECT id, deliveryState FROM reminder_events")

        assertThat(rows).hasSize(1)
        assertThat(rows[0][1]).isEqualTo("DELIVERED")
    }

    @Test
    fun migration2To3_createsOutboxEngagementAndSettingsTables() {
        seedV2()

        assertThat(migrate2To3AndQuery("SELECT COUNT(*) FROM sync_outbox")[0][0].toString()).isEqualTo("0")
        assertThat(migrate2To3AndQuery("SELECT COUNT(*) FROM app_settings")[0][0].toString()).isEqualTo("0")
        assertThat(migrate2To3AndQuery("SELECT COUNT(*) FROM engagement_profile")[0][0].toString()).isEqualTo("1")
    }

    @Test
    fun migration1To2_preservesCycleRecordsUnchanged() {
        val januaryStart = LocalDate.of(2026, 1, 3).toEpochDay()
        val januaryEnd = LocalDate.of(2026, 1, 7).toEpochDay()
        val februaryStart = LocalDate.of(2026, 2, 1).toEpochDay()
        val februaryEnd = LocalDate.of(2026, 2, 5).toEpochDay()

        seedV1(
            cycleRecords = listOf(
                "('jan', $januaryStart, $januaryEnd, 28, 'User', 'first note')",
                "('feb', $februaryStart, $februaryEnd, 31, 'Imported', NULL)",
            ),
        )

        val rows = migrateAndQuery("SELECT id, startEpochDay, endEpochDay, cycleLength, source, notes FROM cycle_records ORDER BY id")

        assertThat(rows.map { it[0] }).containsExactly("feb", "jan").inOrder()
        assertThat(rows.map { it[1].toString() }).containsExactly("$februaryStart", "$januaryStart").inOrder()
        assertThat(rows.map { it[2].toString() }).containsExactly("$februaryEnd", "$januaryEnd").inOrder()
        assertThat(rows.map { it[3].toString() }).containsExactly("31", "28").inOrder()
        assertThat(rows.map { it[4] }).containsExactly("Imported", "User").inOrder()
        assertThat(rows.first()[5]).isNull()
        assertThat(rows.last()[5]).isEqualTo("first note")
    }

    @Test
    fun migration1To2_preservesSymptomLogsAndNullMedication() {
        val firstDay = LocalDate.of(2026, 1, 3).toEpochDay()
        val secondDay = LocalDate.of(2026, 1, 4).toEpochDay()

        seedV1(
            symptomLogs = listOf(
                "('log-a', $firstDay, 'Stable', 'Light', 'Steady', 'None', NULL, 'kept')",
                "('log-b', $secondDay, 'Low', 'None', 'High', 'Creamy', 'Ibuprofen', NULL)",
            ),
        )

        val rows = migrateAndQuery("SELECT id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, medication, notes FROM symptom_logs ORDER BY id")

        assertThat(rows.map { it[0] }).containsExactly("log-a", "log-b").inOrder()
        assertThat(rows.map { it[1].toString() }).containsExactly("$firstDay", "$secondDay").inOrder()
        assertThat(rows[0][6]).isNull()
        assertThat(rows[1][6]).isEqualTo("Ibuprofen")
        assertThat(rows[0][3]).isEqualTo("Light")
        assertThat(rows[1][3]).isEqualTo("None")
        assertThat(rows[0][7]).isEqualTo("kept")
    }

    @Test
    fun migration1To2_convertsPositiveDaysBeforeToNegativeOffsetForEveryRule() {
        seedV1(
            reminderRules = listOf(
                "('PeriodPrep', 'PeriodPrep', 1, 2, 9, 0)",
                "('Fertile', 'FertileWindowStart', 1, 3, 10, 30)",
                "('Daily', 'DailyLog', 1, 0, 20, 0)",
            ),
        )

        val rows = migrateAndQuery("SELECT id, offsetDays, hourOfDay, minute FROM reminder_rules WHERE id IN ('PeriodPrep', 'Fertile', 'Daily') ORDER BY id")

        assertThat(rows.map { it[0] }).containsExactly("Daily", "Fertile", "PeriodPrep").inOrder()
        assertThat(rows.map { it[1].toString() }).containsExactly("0", "-3", "-2").inOrder()
        assertThat(rows.map { it[2].toString() }).containsExactly("20", "10", "9").inOrder()
        assertThat(rows.map { it[3].toString() }).containsExactly("0", "30", "0").inOrder()
    }

    @Test
    fun migration1To2_renamesOvulationEstimateRuleAndKeepsItsLeadTime() {
        seedV1(
            trackingGoal = "PeriodPrep",
            reminderRules = listOf(
                "('OvulationEstimate', 'OvulationEstimate', 1, 1, 9, 0)",
            ),
        )

        val rows = migrateAndQuery("SELECT id, type, offsetDays FROM reminder_rules WHERE offsetDays = -1 OR type = 'FertileWindowStart'")

        assertThat(rows).isNotEmpty()
        val renamed = rows.first()
        assertThat(renamed[0]).isEqualTo("FertileWindowStart")
        assertThat(renamed[1]).isEqualTo("FertileWindowStart")
        assertThat(renamed[2].toString()).isEqualTo("-1")
    }

    @Test
    fun migration1To2_seedsLatePeriodRuleDisabled() {
        seedV1(trackingGoal = "PeriodPrep", reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, 2, 9, 0)"))

        val rows = migrateAndQuery("SELECT id, type, enabled, offsetDays FROM reminder_rules WHERE id = 'LatePeriod'")

        assertThat(rows).hasSize(1)
        assertThat(rows[0][1]).isEqualTo("LatePeriod")
        assertThat(rows[0][2].toString()).isEqualTo("0")
    }

    @Test
    fun migration1To2_enablesSeededFertilityRuleOnlyForConceptionAwareness() {
        seedV1(trackingGoal = "ConceptionAwareness", reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, 2, 9, 0)"))
        val enabled = migrateAndQuery("SELECT enabled FROM reminder_rules WHERE id = 'OvulationEstimate'").single()
        assertThat(enabled[0].toString()).isEqualTo("1")

        tearDown()
        setUp()
        seedV1(trackingGoal = "SymptomAwareness", reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, 2, 9, 0)"))
        val disabled = migrateAndQuery("SELECT enabled FROM reminder_rules WHERE id = 'OvulationEstimate'").single()
        assertThat(disabled[0].toString()).isEqualTo("0")
    }

    @Test
    fun migration1To2_createsReminderEventsTable() {
        seedV1(reminderRules = listOf("('PeriodPrep', 'PeriodPrep', 1, 2, 9, 0)"))

        val rows = migrateAndQuery("SELECT count(*) FROM reminder_events")

        assertThat(rows.single()[0].toString()).isEqualTo("0")
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun seedV2(
        reminderRules: List<String> = emptyList(),
        cycleRecords: List<String> = emptyList(),
        symptomLogs: List<String> = emptyList(),
        reminderEvents: List<String> = emptyList(),
    ) {
        migrationHelper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO user_profile (id, ageBand, averageCycleLength, periodLength, " +
                    "lastPeriodStartEpochDay, trackingGoal, remindersEnabled, privacyModeEnabled) " +
                    "VALUES (1, 'Twenties', 28, 5, 20456, 'PeriodPrep', 1, 1)",
            )
            cycleRecords.forEach { execSQL("INSERT INTO cycle_records VALUES $it") }
            symptomLogs.forEach { execSQL("INSERT INTO symptom_logs VALUES $it") }
            reminderRules.forEach {
                execSQL(
                    "INSERT INTO reminder_rules (id, type, enabled, offsetDays, hourOfDay, minute) " +
                        "VALUES $it",
                )
            }
            reminderEvents.forEach { execSQL("INSERT INTO reminder_events VALUES $it") }
            close()
        }
    }

    private fun migrate2To3AndQuery(sql: String): List<List<Any?>> =
        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE, 3, true, migration1To2, migration2To3,
        ).use { db ->
            db.query(sql).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add((0 until cursor.columnCount).map { cursor.getString(it) })
                    }
                }
            }
        }

    private fun seedV1(
        trackingGoal: String = "PeriodPrep",
        reminderRules: List<String> = emptyList(),
        cycleRecords: List<String> = emptyList(),
        symptomLogs: List<String> = emptyList(),
    ) {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO user_profile (id, ageBand, averageCycleLength, periodLength, " +
                    "lastPeriodStartEpochDay, trackingGoal, remindersEnabled, privacyModeEnabled) " +
                    "VALUES (1, 'Twenties', 28, 5, 20417, '$trackingGoal', 1, 1)",
            )
            cycleRecords.forEach { execSQL("INSERT INTO cycle_records VALUES $it") }
            symptomLogs.forEach { execSQL("INSERT INTO symptom_logs VALUES $it") }
            reminderRules.forEach {
                execSQL(
                    "INSERT INTO reminder_rules (id, type, enabled, daysBefore, hourOfDay, minute) " +
                        "VALUES $it",
                )
            }
            close()
        }
    }

    private fun migrateAndQuery(sql: String): List<List<Any?>> =
        migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 2, true, migration1To2).use { db ->
            db.query(sql).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            (0 until cursor.columnCount).map { cursor.getString(it) },
                        )
                    }
                }
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
        end: LocalDate?,
    ) = CycleRecordEntity(
        id = id,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end?.toEpochDay(),
        cycleLength = 28,
        source = "User",
        notes = null,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        deletedAtEpochMillis = null,
    )

    private companion object {
        const val TEST_DATABASE = "cyclecare-room-schema-test"
    }
}