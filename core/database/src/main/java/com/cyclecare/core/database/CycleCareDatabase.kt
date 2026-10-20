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
     * Deprecated for interval reads. For rows migrated from v2 this is
     * recomputed from the authoritative start-to-start gap. New interval
     * calculations must use consecutive starts in the domain rather than
     * this stored field, because production data can disagree with the
     * real gap. The original v2 value lives in [legacyCycleLength].
     */
    val cycleLength: Int,
    val source: String,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** Non-null marks a soft-deleted row retained for sync propagation. */
    val deletedAtEpochMillis: Long?,
    /**
     * The v2 `cycleLength` exactly as the user or importer stored it.
     * Null for rows created after the migration, which have no legacy
     * value to preserve. Never null for a row migrated from v2.
     */
    val legacyCycleLength: Int? = null,
    /**
     * True when the audit timestamps above are the synthetic migration
     * backfill rather than the time the user actually recorded the row.
     * `updatedAtEpochMillis` is synthetic too when this is true.
     */
    @ColumnInfo(defaultValue = "0")
    val createdAtIsBackfilled: Boolean = false,
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
    /**
     * True when the audit timestamps above are the synthetic migration
     * backfill rather than the time the user actually recorded the row.
     */
    @ColumnInfo(defaultValue = "0")
    val createdAtIsBackfilled: Boolean = false,
)

/**
 * The kinds of divergence a migration records.
 *
 * They are constants rather than literals because two modules read
 * them: the migration that writes them and the data layer that maps
 * them back for the user-facing record. A record whose kind is not
 * recognised is still shown, so a later build's kind never breaks an
 * earlier one.
 */
const val MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE = "CYCLE_LENGTH_DIVERGENCE"
const val MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE = "DUPLICATE_SYMPTOM_DATE"
const val MIGRATION_LOG_KIND_HISTORY_CARRIED_OVER = "HISTORY_CARRIED_OVER"

/**
 * A durable record of what a migration changed. The migration cannot
 * show UI itself, so it writes here and the presentation layer reads it
 * after the database opens. Rows survive the process that migrated the
 * database and are cleared only by an explicit user reset.
 */
@Entity(
    tableName = "migration_log",
    indices = [Index(value = ["acknowledgedAtEpochMillis"])],
)
data class MigrationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val migrationFrom: Int,
    val migrationTo: Int,
    /** One of the `MIGRATION_LOG_KIND_` constants. */
    val kind: String,
    val recordId: String?,
    val dateEpochDay: Long?,
    /** The value the source database held, before reconciliation. */
    val storedValue: Long?,
    /** The value written after reconciliation. */
    val observedValue: Long?,
    val detail: String?,
    val createdAtEpochMillis: Long,
    /** Non-null once the user has been shown this record. */
    val acknowledgedAtEpochMillis: Long?,
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

    @Query("SELECT * FROM cycle_records WHERE id = :id")
    suspend fun findById(id: String): CycleRecordEntity?

    @Query("SELECT * FROM cycle_records")
    suspend fun getAllForBackup(): List<CycleRecordEntity>
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

    @Query("SELECT * FROM symptom_logs WHERE id = :id")
    suspend fun findById(id: String): SymptomLogEntity?

    @Query("SELECT * FROM symptom_logs")
    suspend fun getAllForBackup(): List<SymptomLogEntity>
}

@Dao
interface MigrationLogDao {
    @Query("SELECT * FROM migration_log ORDER BY createdAtEpochMillis ASC, id ASC")
    fun observeAll(): Flow<List<MigrationLogEntity>>

    @Query(
        """
        SELECT * FROM migration_log
        WHERE acknowledgedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, id ASC
        """,
    )
    fun observeUnacknowledged(): Flow<List<MigrationLogEntity>>

    @Query("SELECT * FROM migration_log ORDER BY createdAtEpochMillis ASC, id ASC")
    suspend fun getAll(): List<MigrationLogEntity>

    @Query("UPDATE migration_log SET acknowledgedAtEpochMillis = :acknowledgedAt WHERE id = :id")
    suspend fun acknowledge(id: Long, acknowledgedAt: Long): Int

    @Query("UPDATE migration_log SET acknowledgedAtEpochMillis = :acknowledgedAt WHERE acknowledgedAtEpochMillis IS NULL")
    suspend fun acknowledgeAll(acknowledgedAt: Long): Int

    @Query("SELECT COUNT(*) FROM migration_log WHERE kind = :kind")
    suspend fun countByKind(kind: String): Int
}

@Dao
interface DailySymptomDao {
    @Query("SELECT * FROM daily_symptoms WHERE dailyLogId = :dailyLogId")
    fun observeForLog(dailyLogId: String): Flow<List<DailySymptomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(symptoms: List<DailySymptomEntity>)

    @Query("DELETE FROM daily_symptoms WHERE dailyLogId = :dailyLogId")
    suspend fun deleteForLog(dailyLogId: String): Int

    @Query("SELECT * FROM daily_symptoms")
    suspend fun getAllForBackup(): List<DailySymptomEntity>
}

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rules ORDER BY type ASC, id ASC")
    fun observeAll(): Flow<List<ReminderRuleEntity>>

    @Query("SELECT COUNT(*) FROM reminder_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ReminderRuleEntity)

    @Query("SELECT * FROM reminder_rules")
    suspend fun getAllForBackup(): List<ReminderRuleEntity>
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

    /**
     * The most recent delivery time at or after [sinceEpochMillis].
     *
     * Spec 15 §11.3 condition 5 asks whether a reminder was delivered in the
     * last 30 minutes. This deliberately does not filter on
     * `dismissedAtEpochMillis`: a reminder that was delivered and then
     * dismissed was still delivered, and dismissing it is not the user
     * asking for another interruption.
     */
    @Query(
        """
        SELECT MAX(deliveredAtEpochMillis) FROM reminder_events
        WHERE deliveredAtEpochMillis >= :sinceEpochMillis
        """,
    )
    suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long?

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
        MigrationLogEntity::class,
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
    abstract fun migrationLogDao(): MigrationLogDao
}

