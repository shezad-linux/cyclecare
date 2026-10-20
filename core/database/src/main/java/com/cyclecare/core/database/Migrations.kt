package com.cyclecare.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migration1To2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reminder_rules_new` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `offsetDays` INTEGER NOT NULL,
                `hourOfDay` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO reminder_rules_new (id, type, enabled, offsetDays, hourOfDay, minute)
            SELECT
                CASE WHEN type = 'OvulationEstimate' AND id = 'OvulationEstimate' THEN 'FertileWindowStart' ELSE id END,
                CASE WHEN type = 'OvulationEstimate' THEN 'FertileWindowStart' ELSE type END,
                enabled,
                CASE WHEN type = 'LatePeriod' THEN daysBefore ELSE -daysBefore END,
                hourOfDay,
                minute
            FROM reminder_rules
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE reminder_rules")
        database.execSQL("ALTER TABLE reminder_rules_new RENAME TO reminder_rules")

        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reminder_events` (
                `id` TEXT NOT NULL,
                `ruleId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `channelKey` TEXT NOT NULL,
                `scheduledAtEpochMillis` INTEGER NOT NULL,
                `deliveredAtEpochMillis` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `privateBody` TEXT NOT NULL,
                `deepLinkTarget` TEXT NOT NULL,
                `targetEpochDay` INTEGER,
                `readAtEpochMillis` INTEGER,
                `dismissedAtEpochMillis` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        val trackingGoalCursor = database.query("SELECT trackingGoal FROM user_profile WHERE id = 1 LIMIT 1")
        val trackingGoal = trackingGoalCursor.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val fertilityEnabled = if (trackingGoal == "ConceptionAwareness") 1 else 0

        database.execSQL(
            """
            INSERT OR IGNORE INTO reminder_rules (id, type, enabled, offsetDays, hourOfDay, minute)
            VALUES ('OvulationEstimate', 'OvulationEstimate', $fertilityEnabled, 0, 9, 0)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT OR IGNORE INTO reminder_rules (id, type, enabled, offsetDays, hourOfDay, minute)
            VALUES ('LatePeriod', 'LatePeriod', 0, 3, 9, 0)
            """.trimIndent(),
        )
    }
}

/**
 * A stable fingerprint of the v2 data that the migration must preserve.
 * It deliberately excludes `cycleLength`, which the migration is allowed
 * to recompute, and the new audit/tombstone columns, which did not exist
 * in v2. `cycleLengths` is verified separately against
 * `legacyCycleLength`, because preserving the source value is a stronger
 * guarantee than keeping a column with the same name.
 */
internal data class TableSnapshot(
    val rows: Map<String, List<String?>>,
)

internal data class MigrationSnapshot(
    val tables: Map<String, TableSnapshot>,
    val cycleLengths: Map<String, Long?>,
)

private data class SnapshotSpec(
    val table: String,
    val valueColumns: List<String>,
)

private val snapshotSpecs = listOf(
    SnapshotSpec(
        table = "user_profile",
        valueColumns = listOf(
            "ageBand", "averageCycleLength", "periodLength", "lastPeriodStartEpochDay",
            "trackingGoal", "remindersEnabled", "privacyModeEnabled",
        ),
    ),
    SnapshotSpec(
        table = "cycle_records",
        // cycleLength is reconciled by design and is checked through
        // legacyCycleLength, so it is not part of the unchanged snapshot.
        valueColumns = listOf("startEpochDay", "endEpochDay", "source", "notes"),
    ),
    SnapshotSpec(
        table = "symptom_logs",
        valueColumns = listOf(
            "dateEpochDay", "mood", "painLevel", "energyLevel", "dischargeType",
            "medication", "notes",
        ),
    ),
    SnapshotSpec(
        table = "reminder_rules",
        valueColumns = listOf("type", "enabled", "offsetDays", "hourOfDay", "minute"),
    ),
    SnapshotSpec(
        table = "reminder_events",
        valueColumns = listOf(
            "ruleId", "type", "channelKey", "scheduledAtEpochMillis",
            "deliveredAtEpochMillis", "title", "body", "privateBody",
            "deepLinkTarget", "targetEpochDay", "readAtEpochMillis",
            "dismissedAtEpochMillis",
        ),
    ),
)

internal fun captureV2Snapshot(database: SupportSQLiteDatabase): MigrationSnapshot {
    val tables = snapshotSpecs.associate { spec ->
        spec.table to captureTable(database, spec)
    }
    val cycleLengths = linkedMapOf<String, Long?>()
    database.query("SELECT id, cycleLength FROM cycle_records ORDER BY id").use { cursor ->
        while (cursor.moveToNext()) {
            cycleLengths[cursor.getString(0)] = if (cursor.isNull(1)) null else cursor.getLong(1)
        }
    }
    return MigrationSnapshot(tables = tables, cycleLengths = cycleLengths)
}

private fun captureTable(
    database: SupportSQLiteDatabase,
    spec: SnapshotSpec,
): TableSnapshot {
    val columns = listOf("id") + spec.valueColumns
    val rows = linkedMapOf<String, List<String?>>()
    database.query(
        "SELECT ${columns.joinToString()} FROM ${spec.table} ORDER BY id",
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(0) ?: continue
            rows[id] = (1 until cursor.columnCount).map { index ->
                if (cursor.isNull(index)) null else cursor.getString(index)
            }
        }
    }
    return TableSnapshot(rows)
}

/**
 * Verifies counts, record IDs and every preserved column after the schema
 * change. Any mismatch throws, which aborts the surrounding Room
 * transaction and leaves the source database untouched.
 */
internal fun verifyV2ToV3Snapshot(
    database: SupportSQLiteDatabase,
    before: MigrationSnapshot,
) {
    for (spec in snapshotSpecs) {
        val expected = before.tables[spec.table]
            ?: throw IllegalStateException("v2->v3 verification has no pre-migration snapshot for ${spec.table}")
        val actual = captureTable(database, spec)
        if (actual.rows != expected.rows) {
            val missing = expected.rows.keys - actual.rows.keys
            val unexpected = actual.rows.keys - expected.rows.keys
            val changed = expected.rows.keys.intersect(actual.rows.keys)
                .filter { expected.rows[it] != actual.rows[it] }
            throw IllegalStateException(
                "v2->v3 verification failed for ${spec.table}: " +
                    "missing=$missing unexpected=$unexpected changed=$changed",
            )
        }
    }

    val actualLengths = linkedMapOf<String, Long?>()
    database.query("SELECT id, legacyCycleLength FROM cycle_records ORDER BY id").use { cursor ->
        while (cursor.moveToNext()) {
            actualLengths[cursor.getString(0)] = if (cursor.isNull(1)) null else cursor.getLong(1)
        }
    }
    if (actualLengths != before.cycleLengths) {
        val missing = before.cycleLengths.keys - actualLengths.keys
        val changed = before.cycleLengths.keys.intersect(actualLengths.keys)
            .filter { before.cycleLengths[it] != actualLengths[it] }
        throw IllegalStateException(
            "v2->v3 verification failed for legacyCycleLength: " +
                "missing=$missing changed=$changed",
        )
    }
}

private data class DuplicateDate(
    val dateEpochDay: Long,
    val rowCount: Int,
)

private fun findDuplicateDates(database: SupportSQLiteDatabase): List<DuplicateDate> =
    buildList {
        database.query(
            """
            SELECT dateEpochDay, COUNT(*)
            FROM symptom_logs
            GROUP BY dateEpochDay
            HAVING COUNT(*) > 1
            ORDER BY dateEpochDay
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                add(DuplicateDate(cursor.getLong(0), cursor.getInt(1)))
            }
        }
    }

private fun insertMigrationLog(
    database: SupportSQLiteDatabase,
    migratedAt: Long,
    kind: String,
    recordId: String?,
    dateEpochDay: Long?,
    storedValue: Long?,
    observedValue: Long?,
    detail: String,
) {
    database.execSQL(
        """
        INSERT INTO migration_log (
            migrationFrom, migrationTo, kind, recordId, dateEpochDay,
            storedValue, observedValue, detail, createdAtEpochMillis,
            acknowledgedAtEpochMillis
        ) VALUES (2, 3, ?, ?, ?, ?, ?, ?, ?, NULL)
        """.trimIndent(),
        arrayOf<Any?>(kind, recordId, dateEpochDay, storedValue, observedValue, detail, migratedAt),
    )
}

/**
 * v2 to v3.
 *
 * Data-preserving, with the reconciliation policy from `02` §9.4-§9.6:
 *
 *  - "None" stays "None". It is NOT converted to NULL, because NULL
 *    means "not tracked" and "None" means "explicitly none". Conflating
 *    them would silently rewrite what users recorded.
 *
 *  - The original cycleLength is copied to legacyCycleLength for every
 *    row before anything is recomputed. Where a later start exists, the
 *    start-to-start gap becomes authoritative. The oldest record keeps
 *    the stored value as its seed. A disagreement greater than seven
 *    days is written to migration_log, never corrected silently.
 *
 *  - Duplicate symptom rows for one date are not deleted. The most
 *    recently inserted row stays live and the older rows become
 *    tombstones, so the source IDs and payloads survive and the
 *    collapse is reversible. The count per date is written to
 *    migration_log.
 *
 *  - Audit timestamps are backfilled with the migration time because no
 *    creation timestamp exists in v2 to recover. createdAtIsBackfilled
 *    labels them as synthetic so they can never be presented as a real
 *    "logged on" date.
 *
 *  - The whole move is verified against a pre-migration snapshot of
 *    counts, IDs and preserved columns. A mismatch throws, which rolls
 *    the Room transaction back and leaves v2 intact.
 */
internal fun migrateV2ToV3(
    database: SupportSQLiteDatabase,
    captureSnapshot: (SupportSQLiteDatabase) -> MigrationSnapshot = ::captureV2Snapshot,
    verifySnapshot: (SupportSQLiteDatabase, MigrationSnapshot) -> Unit = ::verifyV2ToV3Snapshot,
) {
    val migratedAt = System.currentTimeMillis()
    val before = captureSnapshot(database)
    val duplicateDates = findDuplicateDates(database)

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `migration_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `migrationFrom` INTEGER NOT NULL,
            `migrationTo` INTEGER NOT NULL,
            `kind` TEXT NOT NULL,
            `recordId` TEXT,
            `dateEpochDay` INTEGER,
            `storedValue` INTEGER,
            `observedValue` INTEGER,
            `detail` TEXT,
            `createdAtEpochMillis` INTEGER NOT NULL,
            `acknowledgedAtEpochMillis` INTEGER
        )
        """.trimIndent(),
    )
    database.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_migration_log_acknowledgedAtEpochMillis` " +
            "ON `migration_log` (`acknowledgedAtEpochMillis`)",
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `cycle_records_new` (
            `id` TEXT NOT NULL,
            `startEpochDay` INTEGER NOT NULL,
            `endEpochDay` INTEGER,
            `cycleLength` INTEGER NOT NULL,
            `source` TEXT NOT NULL,
            `notes` TEXT,
            `createdAtEpochMillis` INTEGER NOT NULL,
            `updatedAtEpochMillis` INTEGER NOT NULL,
            `deletedAtEpochMillis` INTEGER,
            `legacyCycleLength` INTEGER,
            `createdAtIsBackfilled` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO cycle_records_new (
            id, startEpochDay, endEpochDay, cycleLength, source, notes,
            createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis,
            legacyCycleLength, createdAtIsBackfilled
        )
        SELECT
            id, startEpochDay, endEpochDay, cycleLength, source, notes,
            $migratedAt, $migratedAt, NULL,
            cycleLength, 1
        FROM cycle_records
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE cycle_records")
    database.execSQL("ALTER TABLE cycle_records_new RENAME TO cycle_records")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_records_startEpochDay` ON `cycle_records` (`startEpochDay`)")

    // Where a following record exists, the observed start-to-start gap is
    // authoritative. The oldest record has no following interval and keeps
    // the value copied above as its seed.
    database.execSQL(
        """
        UPDATE cycle_records
        SET cycleLength = (
            SELECT MIN(next.startEpochDay) - cycle_records.startEpochDay
            FROM cycle_records AS next
            WHERE next.startEpochDay > cycle_records.startEpochDay
        )
        WHERE EXISTS (
            SELECT 1 FROM cycle_records AS next
            WHERE next.startEpochDay > cycle_records.startEpochDay
        )
        """.trimIndent(),
    )

    database.query(
        """
        SELECT id, startEpochDay, legacyCycleLength, cycleLength
        FROM cycle_records
        WHERE legacyCycleLength IS NOT NULL
          AND ABS(legacyCycleLength - cycleLength) > 7
        ORDER BY startEpochDay ASC, id ASC
        """.trimIndent(),
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val recordId = cursor.getString(0)
            val startEpochDay = cursor.getLong(1)
            val legacy = cursor.getLong(2)
            val computed = cursor.getLong(3)
            val difference = kotlin.math.abs(legacy - computed)
            insertMigrationLog(
                database = database,
                migratedAt = migratedAt,
                kind = "CYCLE_LENGTH_DIVERGENCE",
                recordId = recordId,
                dateEpochDay = startEpochDay,
                storedValue = legacy.toLong(),
                observedValue = computed.toLong(),
                detail = "Stored cycleLength $legacy differs from the $computed-day " +
                    "start-to-start gap by $difference days. The gap is now authoritative; " +
                    "the original value is retained in legacyCycleLength.",
            )
        }
    }

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `symptom_logs_new` (
            `id` TEXT NOT NULL,
            `dateEpochDay` INTEGER NOT NULL,
            `mood` TEXT,
            `painLevel` TEXT,
            `energyLevel` TEXT,
            `dischargeType` TEXT,
            `medication` TEXT,
            `notes` TEXT,
            `createdAtEpochMillis` INTEGER NOT NULL,
            `updatedAtEpochMillis` INTEGER NOT NULL,
            `deletedAtEpochMillis` INTEGER,
            `createdAtIsBackfilled` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO symptom_logs_new (
            id, dateEpochDay, mood, painLevel, energyLevel, dischargeType,
            medication, notes, createdAtEpochMillis, updatedAtEpochMillis,
            deletedAtEpochMillis, createdAtIsBackfilled
        )
        SELECT
            source.id, source.dateEpochDay, source.mood, source.painLevel,
            source.energyLevel, source.dischargeType, source.medication,
            source.notes, $migratedAt, $migratedAt,
            CASE
                WHEN source.rowid = (
                    SELECT MAX(newest.rowid)
                    FROM symptom_logs AS newest
                    WHERE newest.dateEpochDay = source.dateEpochDay
                ) THEN NULL
                ELSE $migratedAt
            END,
            1
        FROM symptom_logs AS source
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE symptom_logs")
    database.execSQL("ALTER TABLE symptom_logs_new RENAME TO symptom_logs")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_symptom_logs_dateEpochDay` ON `symptom_logs` (`dateEpochDay`)")

    for (duplicate in duplicateDates) {
        insertMigrationLog(
            database = database,
            migratedAt = migratedAt,
            kind = "DUPLICATE_SYMPTOM_DATE",
            recordId = null,
            dateEpochDay = duplicate.dateEpochDay,
            storedValue = duplicate.rowCount.toLong(),
            observedValue = 1L,
            detail = "${duplicate.rowCount} symptom rows existed for this date. " +
                "The most recently inserted row remains live and " +
                "${duplicate.rowCount - 1} older row(s) were retained as tombstones.",
        )
    }

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `daily_symptoms` (
            `id` TEXT NOT NULL,
            `dailyLogId` TEXT NOT NULL,
            `symptomType` TEXT NOT NULL,
            `severity` TEXT,
            `note` TEXT,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_symptoms_dailyLogId` ON `daily_symptoms` (`dailyLogId`)")

    database.execSQL("ALTER TABLE reminder_rules ADD COLUMN userPinnedTime INTEGER NOT NULL DEFAULT 0")
    database.execSQL("ALTER TABLE reminder_events ADD COLUMN deliveryState TEXT NOT NULL DEFAULT 'DELIVERED'")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_events_scheduledAtEpochMillis` ON `reminder_events` (`scheduledAtEpochMillis`)")

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `sync_outbox` (
            `mutationId` TEXT NOT NULL,
            `recordId` TEXT NOT NULL,
            `operation` TEXT NOT NULL,
            `localRevision` INTEGER NOT NULL,
            `createdAtEpochMillis` INTEGER NOT NULL,
            `attemptCount` INTEGER NOT NULL,
            `lastErrorCategory` TEXT,
            `state` TEXT NOT NULL,
            PRIMARY KEY(`mutationId`)
        )
        """.trimIndent(),
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `engagement_profile` (
            `id` INTEGER NOT NULL,
            `hourHistogram` TEXT NOT NULL,
            `interactionCount` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
    )
    database.execSQL(
        "INSERT OR IGNORE INTO engagement_profile (id, hourHistogram, interactionCount) " +
            "VALUES (1, '${"0,".repeat(23)}0', 0)",
    )

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `app_settings` (
            `key` TEXT NOT NULL,
            `value` TEXT,
            PRIMARY KEY(`key`)
        )
        """.trimIndent(),
    )

    verifySnapshot(database, before)
}

val migration2To3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) = migrateV2ToV3(database)
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(migration1To2, migration2To3)
