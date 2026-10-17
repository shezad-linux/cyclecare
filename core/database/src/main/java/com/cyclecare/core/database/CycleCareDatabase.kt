package com.cyclecare.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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

@Entity(tableName = "cycle_records")
data class CycleRecordEntity(
    @PrimaryKey val id: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val cycleLength: Int,
    val source: String,
    val notes: String?,
)

@Entity(tableName = "symptom_logs")
data class SymptomLogEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val mood: String,
    val painLevel: String,
    val energyLevel: String,
    val dischargeType: String,
    val medication: String?,
    val notes: String?,
)

@Entity(tableName = "reminder_rules")
data class ReminderRuleEntity(
    @PrimaryKey val id: String,
    val type: String,
    val enabled: Boolean,
    val offsetDays: Int,
    val hourOfDay: Int,
    val minute: Int,
)

@Entity(tableName = "reminder_events")
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
    @Query("SELECT * FROM cycle_records ORDER BY startEpochDay ASC, endEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<CycleRecordEntity>>

    @Query("SELECT * FROM cycle_records ORDER BY startEpochDay DESC, endEpochDay DESC, id DESC LIMIT 1")
    suspend fun latest(): CycleRecordEntity?

    @Query(
        """
        SELECT * FROM cycle_records
        WHERE startEpochDay <= :epochDay AND endEpochDay >= :epochDay
        ORDER BY startEpochDay DESC, endEpochDay DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun findForDate(epochDay: Long): CycleRecordEntity?

    @Query("SELECT COUNT(*) FROM cycle_records")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CycleRecordEntity)

    @Query("DELETE FROM cycle_records WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface SymptomLogDao {
    @Query("SELECT * FROM symptom_logs ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<SymptomLogEntity>>

    @Query(
        """
        SELECT * FROM symptom_logs
        WHERE dateEpochDay = :epochDay
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    fun observeLatestForDate(epochDay: Long): Flow<SymptomLogEntity?>

    @Query("SELECT COUNT(*) FROM symptom_logs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SymptomLogEntity)
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

@Database(
    entities = [
        UserProfileEntity::class,
        CycleRecordEntity::class,
        SymptomLogEntity::class,
        ReminderRuleEntity::class,
        ReminderEventEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CycleCareDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun cycleRecordDao(): CycleRecordDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun reminderEventDao(): ReminderEventDao
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
                CASE WHEN type = 'PeriodPrep' THEN -daysBefore ELSE 0 END,
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
