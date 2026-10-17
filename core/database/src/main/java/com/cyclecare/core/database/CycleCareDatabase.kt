package com.cyclecare.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ── v3 entities ───────────────────────────────────────────────────
//
// Changes from v2, all of which exist to fix a real defect rather than
// for tidiness:
//
//  cycle_records.endEpochDay is nullable. A period in progress could not
//  be represented at all in v2, so "Log period end" had nothing to act on.
//
//  symptom_logs mood / energyLevel / painLevel / dischargeType are
//  nullable. v2 declared them NOT NULL and neither Mood nor EnergyLevel
//  has a "None" variant, so every user was forced to answer both on
//  every log. PainLevel and DischargeType do have "None", meaning
//  "explicitly none" rather than "not tracked"; NULL is now distinct
//  from "None" and the two must never be conflated.
//
//  Audit and tombstone columns on the log tables. v2 had none at all, so
//  there was no way to order edits or represent a delete for sync.
//
//  Indices on the columns that are actually queried. v2 had no index and
//  no foreign key on any table.
//
// Note on uniqueness: startEpochDay and dateEpochDay are deliberately
// NOT unique-constrained at the schema level. Once deletes become
// tombstones, a deleted row retains its date, so a UNIQUE index would
// make "log, delete, log again" fail. Uniqueness is enforced by the
// repository over live rows only.

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val ageBand: String,
    val averageCycleLength: Int,
    val periodLength: Int,
    val lastPeriodStartEpochDay: Long,
    val trackingGoal: String,
    val remindersEnabled: Boolean,
    val privacyModeEnabled: Boolean,
)

@Entity(
    tableName = "cycle_records",
    indices = [Index(value = ["startEpochDay"])],
)
data class CycleRecordEntity(
    @PrimaryKey val id: String,
    val startEpochDay: Long,
    /** Null while a period is still in progress. */
    val endEpochDay: Long?,
    /**
     * Deprecated. Kept so existing rows survive the migration and so
     * their original value remains auditable. Nothing may read this as
     * a cycle length: production data can disagree with the real
     * start-to-start gap, because v2 never cross-validated the two.
     * Cycle length is derived from consecutive starts in the domain.
     */
    val cycleLength: Int,
    val source: String,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** Non-null marks a soft-deleted row retained for sync propagation. */
    val deletedAtEpochMillis: Long?,
)

@Entity(
    tableName = "symptom_logs",
    indices = [Index(value = ["dateEpochDay"])],
)
data class SymptomLogEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    /** Null means not tracked. "None" means explicitly none. Distinct. */
    val mood: String?,
    val painLevel: String?,
    val energyLevel: String?,
    val dischargeType: String?,
    val medication: String?,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
)

@Entity(
    tableName = "daily_symptoms",
    indices = [Index(value = ["dailyLogId"])],
)
data class DailySymptomEntity(
    @PrimaryKey val id: String,
    val dailyLogId: String,
    val symptomType: String,
    val severity: String?,
    val note: String?,
)

@Entity(tableName = "reminder_rules")
data class ReminderRuleEntity(
    @PrimaryKey val id: String,
    val type: String,
    val enabled: Boolean,
    /** Signed: negative means before the trigger date. */
    val offsetDays: Int,
    val hourOfDay: Int,
    val minute: Int,
    /**
     * True when the user explicitly chose this time. A pinned time is
     * never overridden by the learned send-time model.
     */
    val userPinnedTime: Boolean,
)

@Entity(
    tableName = "reminder_events",
    indices = [Index(value = ["scheduledAtEpochMillis"])],
)
data class ReminderEventEntity(
    @PrimaryKey val id: String,
    val ruleId: String,
    val type: String,
    val channelKey: String,
    val scheduledAtEpochMillis: Long,
    val deliveredAtEpochMillis: Long,
    val title: String,
    val body: String,
    val privateBody: String,
    val deepLinkTarget: String,
    val targetEpochDay: Long?,
    val readAtEpochMillis: Long?,
    val dismissedAtEpochMillis: Long?,
    /**
     * Guards against duplicate delivery. A retry of an already-posted
     * reminder must not post again; v2 recorded an event without
     * checking whether one already existed.
     */
    val deliveryState: String,
)

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val mutationId: String,
    val recordId: String,
    val operation: String,
    val localRevision: Long,
    val createdAtEpochMillis: Long,
    val attemptCount: Int,
    val lastErrorCategory: String?,
    val state: String,
)

@Entity(tableName = "engagement_profile")
data class EngagementProfileEntity(
    @PrimaryKey val id: Int = 1,
    /** 24 comma-separated hourly interaction counters. */
    val hourHistogram: String,
    val interactionCount: Int,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String?,
)

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT COUNT(*) FROM user_profile")
    suspend fun count(): Int

    @Query("UPDATE user_profile SET remindersEnabled = :enabled WHERE id = 1")
    suspend fun updateRemindersEnabled(enabled: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface CycleRecordDao {
    @Query("SELECT * FROM cycle_records WHERE deletedAtEpochMillis IS NULL ORDER BY startEpochDay ASC, endEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<CycleRecordEntity>>

    @Query("SELECT * FROM cycle_records WHERE deletedAtEpochMillis IS NULL ORDER BY startEpochDay DESC, endEpochDay DESC, id DESC LIMIT 1")
    suspend fun latest(): CycleRecordEntity?

    @Query(
        """
        SELECT * FROM cycle_records
        WHERE deletedAtEpochMillis IS NULL
          AND startEpochDay <= :epochDay AND (endEpochDay IS NULL OR endEpochDay >= :epochDay)
        ORDER BY startEpochDay DESC, endEpochDay DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findForDate(epochDay: Long): CycleRecordEntity?

    @Query("SELECT COUNT(*) FROM cycle_records WHERE deletedAtEpochMillis IS NULL")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CycleRecordEntity)

    @Query("UPDATE cycle_records SET deletedAtEpochMillis = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long): Int

    @Query("SELECT COUNT(*) FROM cycle_records WHERE startEpochDay = :epochDay AND deletedAtEpochMillis IS NULL")
    suspend fun countLiveForStart(epochDay: Long): Int
}

@Dao
interface SymptomLogDao {
    @Query("SELECT * FROM symptom_logs WHERE deletedAtEpochMillis IS NULL ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<SymptomLogEntity>>

    @Query(
        """
        SELECT * FROM symptom_logs
        WHERE deletedAtEpochMillis IS NULL AND dateEpochDay = :epochDay
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    fun observeLatestForDate(epochDay: Long): Flow<SymptomLogEntity?>

    @Query("SELECT COUNT(*) FROM symptom_logs WHERE deletedAtEpochMillis IS NULL")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SymptomLogEntity)

    @Query("UPDATE symptom_logs SET deletedAtEpochMillis = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long): Int

    @Query("SELECT COUNT(*) FROM symptom_logs WHERE dateEpochDay = :epochDay AND deletedAtEpochMillis IS NULL")
    suspend fun countLiveForDate(epochDay: Long): Int
}

@Dao
interface DailySymptomDao {
    @Query("SELECT * FROM daily_symptoms WHERE dailyLogId = :dailyLogId")
    fun observeForLog(dailyLogId: String): Flow<List<DailySymptomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(symptoms: List<DailySymptomEntity>)

    @Query("DELETE FROM daily_symptoms WHERE dailyLogId = :dailyLogId")
    suspend fun deleteForLog(dailyLogId: String): Int
}

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rules ORDER BY type ASC, id ASC")
    fun observeAll(): Flow<List<ReminderRuleEntity>>

    @Query("SELECT COUNT(*) FROM reminder_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ReminderRuleEntity)
}

@Dao
interface ReminderEventDao {
    @Query(
        """
        SELECT * FROM reminder_events
        WHERE dismissedAtEpochMillis IS NULL
        ORDER BY deliveredAtEpochMillis DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<ReminderEventEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM reminder_events
        WHERE dismissedAtEpochMillis IS NULL AND readAtEpochMillis IS NULL
        """,
    )
    fun observeUnreadCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM reminder_events
        WHERE dismissedAtEpochMillis IS NULL AND readAtEpochMillis IS NULL
        ORDER BY deliveredAtEpochMillis DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeNextUnread(): Flow<ReminderEventEntity?>

    /**
     * Returns 1 when the event was newly inserted, 0 when an event with
     * this id already existed. Callers post a notification only when
     * this returns 1, which makes a retried delivery at-most-once.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: ReminderEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: ReminderEventEntity)

    @Query("UPDATE reminder_events SET readAtEpochMillis = :readAtEpochMillis WHERE id = :id")
    suspend fun markRead(id: String, readAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE reminder_events
        SET readAtEpochMillis = :readAtEpochMillis
        WHERE dismissedAtEpochMillis IS NULL AND readAtEpochMillis IS NULL
        """,
    )
    suspend fun markAllRead(readAtEpochMillis: Long): Int

    @Query("UPDATE reminder_events SET dismissedAtEpochMillis = :dismissedAtEpochMillis WHERE id = :id")
    suspend fun dismiss(id: String, dismissedAtEpochMillis: Long): Int
}

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE state IN ('PENDING', 'RETRYABLE_ERROR') ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SyncOutboxEntity)

    @Query("UPDATE sync_outbox SET state = :state, attemptCount = attemptCount + 1, lastErrorCategory = :errorCategory WHERE mutationId = :mutationId")
    suspend fun markAttempt(mutationId: String, state: String, errorCategory: String?): Int

    @Query("DELETE FROM sync_outbox WHERE mutationId = :mutationId")
    suspend fun remove(mutationId: String): Int

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int
}

@Dao
interface EngagementProfileDao {
    @Query("SELECT * FROM engagement_profile WHERE id = 1")
    suspend fun get(): EngagementProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: EngagementProfileEntity)
}

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)
}

@Database(
    entities = [
        UserProfileEntity::class,
        CycleRecordEntity::class,
        SymptomLogEntity::class,
        DailySymptomEntity::class,
        ReminderRuleEntity::class,
        ReminderEventEntity::class,
        SyncOutboxEntity::class,
        EngagementProfileEntity::class,
        AppSettingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CycleCareDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun cycleRecordDao(): CycleRecordDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun dailySymptomDao(): DailySymptomDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun engagementProfileDao(): EngagementProfileDao
    abstract fun appSettingDao(): AppSettingDao
}

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