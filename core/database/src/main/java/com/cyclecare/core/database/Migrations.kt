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
 * v2 to v3.
 *
 * Data-preserving. No column value is reinterpreted:
 *
 *  - "None" stays "None". It is NOT converted to NULL, because NULL
 *    means "not tracked" and "None" means "explicitly none". Conflating
 *    them would silently rewrite what users recorded.
 *
 *  - cycleLength is carried across unchanged rather than recomputed.
 *    It is deprecated for reads, but its original value is preserved so
 *    nothing is lost and the divergence from the true start-to-start
 *    gap remains auditable.
 *
 *  - Audit timestamps are backfilled with the migration time because no
 *    creation timestamp exists in v2 to recover. That backfill is
 *    synthetic and must never be presented as a real "logged on" date.
 *
 *  - Duplicate symptom rows for one date are collapsed before the new
 *    schema is created, keeping the most recently inserted. v2 had no
 *    unique constraint so duplicates are possible.
 */
val migration2To3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val migratedAt = System.currentTimeMillis()

        database.execSQL(
            """
            DELETE FROM symptom_logs
            WHERE rowid NOT IN (SELECT MAX(rowid) FROM symptom_logs GROUP BY dateEpochDay)
            """.trimIndent(),
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
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO cycle_records_new (id, startEpochDay, endEpochDay, cycleLength, source, notes, createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis)
            SELECT id, startEpochDay, endEpochDay, cycleLength, source, notes, $migratedAt, $migratedAt, NULL FROM cycle_records
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE cycle_records")
        database.execSQL("ALTER TABLE cycle_records_new RENAME TO cycle_records")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_records_startEpochDay` ON `cycle_records` (`startEpochDay`)")

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
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO symptom_logs_new (id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, medication, notes, createdAtEpochMillis, updatedAtEpochMillis, deletedAtEpochMillis)
            SELECT id, dateEpochDay, mood, painLevel, energyLevel, dischargeType, medication, notes, $migratedAt, $migratedAt, NULL FROM symptom_logs
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE symptom_logs")
        database.execSQL("ALTER TABLE symptom_logs_new RENAME TO symptom_logs")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_symptom_logs_dateEpochDay` ON `symptom_logs` (`dateEpochDay`)")

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
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(migration1To2, migration2To3)