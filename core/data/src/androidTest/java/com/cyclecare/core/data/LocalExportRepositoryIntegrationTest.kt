package com.cyclecare.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.CycleRecordEntity
import com.cyclecare.core.database.DailySymptomEntity
import com.cyclecare.core.database.ReminderRuleEntity
import com.cyclecare.core.database.SymptomLogEntity
import com.cyclecare.core.database.UserProfileEntity
import com.cyclecare.core.domain.ImportResult
import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.experimental.xor

@RunWith(AndroidJUnit4::class)
class LocalExportRepositoryIntegrationTest {

    private lateinit var context: Context
    private lateinit var sourceDb: CycleCareDatabase
    private lateinit var targetDb: CycleCareDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = Room.inMemoryDatabaseBuilder(context, CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        targetDb = Room.inMemoryDatabaseBuilder(context, CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    private fun createRepository(db: CycleCareDatabase): LocalExportRepository {
        val profileRepo = RoomProfileRepository(db)
        val cycleRepo = RoomCycleRepository(db)
        val symptomRepo = RoomSymptomRepository(db)
        val insightRepo = LocalInsightRepository(cycleRepo, symptomRepo, InsightEngine())
        return LocalExportRepository(
            context = context,
            profileRepository = profileRepo,
            cycleRepository = cycleRepo,
            symptomRepository = symptomRepo,
            insightRepository = insightRepo,
            database = db,
        )
    }

    @Test
    fun roundTrip_exportsAndImportsAllEntities_includingTombstones() {
        runBlocking {
        // 1. Seed sourceDb
        sourceDb.userProfileDao().upsert(
            UserProfileEntity(
                ageBand = AgeBand.Twenties.name,
                averageCycleLength = 28,
                periodLength = 5,
                lastPeriodStartEpochDay = LocalDate.of(2026, 3, 1).toEpochDay(),
                trackingGoal = TrackingGoal.PeriodPrep.name,
                remindersEnabled = true,
                privacyModeEnabled = true,
            )
        )

        // Live cycle
        sourceDb.cycleRecordDao().upsert(
            CycleRecordEntity(
                id = "cycle-live-1",
                startEpochDay = LocalDate.of(2026, 3, 1).toEpochDay(),
                endEpochDay = LocalDate.of(2026, 3, 5).toEpochDay(),
                cycleLength = 28,
                source = CycleSource.User.name,
                notes = "Live note",
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
            )
        )
        // Tombstone cycle
        sourceDb.cycleRecordDao().upsert(
            CycleRecordEntity(
                id = "cycle-tombstone-2",
                startEpochDay = LocalDate.of(2026, 2, 1).toEpochDay(),
                endEpochDay = LocalDate.of(2026, 2, 5).toEpochDay(),
                cycleLength = 28,
                source = CycleSource.User.name,
                notes = "Deleted note",
                createdAtEpochMillis = 800L,
                updatedAtEpochMillis = 900L,
                deletedAtEpochMillis = 950L,
            )
        )

        // Live symptom log
        sourceDb.symptomLogDao().upsert(
            SymptomLogEntity(
                id = "log-live-1",
                dateEpochDay = LocalDate.of(2026, 3, 2).toEpochDay(),
                mood = Mood.Happy.name,
                painLevel = PainLevel.Light.name,
                energyLevel = EnergyLevel.Steady.name,
                dischargeType = DischargeType.EggWhite.name,
                medication = "Ibuprofen",
                notes = "Symptom note",
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 1000L,
                deletedAtEpochMillis = null,
            )
        )
        // Tombstone symptom log
        sourceDb.symptomLogDao().upsert(
            SymptomLogEntity(
                id = "log-tombstone-2",
                dateEpochDay = LocalDate.of(2026, 3, 3).toEpochDay(),
                mood = Mood.Low.name,
                painLevel = PainLevel.Moderate.name,
                energyLevel = EnergyLevel.Low.name,
                dischargeType = DischargeType.Dry.name,
                medication = null,
                notes = "Deleted log note",
                createdAtEpochMillis = 1100L,
                updatedAtEpochMillis = 1200L,
                deletedAtEpochMillis = 1250L,
            )
        )

        // Daily symptom category
        sourceDb.dailySymptomDao().upsertAll(
            listOf(
                DailySymptomEntity(
                    id = "ds-1",
                    dailyLogId = "log-live-1",
                    symptomType = "Cramps",
                    severity = "Mild",
                    note = "Mild cramps",
                )
            )
        )

        // Reminder rule
        sourceDb.reminderRuleDao().upsert(
            ReminderRuleEntity(
                id = "rule-1",
                type = ReminderType.PeriodPrep.name,
                enabled = true,
                offsetDays = -2,
                hourOfDay = 9,
                minute = 30,
                userPinnedTime = true,
            )
        )

        val sourceRepo = createRepository(sourceDb)
        val targetRepo = createRepository(targetDb)
        val passphrase = "SecureStrongPassphrase123!".toCharArray()

        // 2. Export encrypted backup
        val artifact = sourceRepo.createEncryptedBackup(passphrase)
        assertThat(artifact.format).isEqualTo(ExportFormat.EncryptedBackup)
        val backupFile = File(artifact.absolutePath)
        assertThat(backupFile.exists()).isTrue()
        assertThat(backupFile.length()).isGreaterThan(0)

        // 3. Import into empty targetDb
        val result = FileInputStream(backupFile).use { stream ->
            targetRepo.importEncryptedBackup(stream, passphrase)
        }

        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        val success = result as ImportResult.Success
        assertThat(success.profileImported).isTrue()
        assertThat(success.cyclesImported).isEqualTo(1) // 1 live cycle imported
        assertThat(success.symptomsImported).isEqualTo(1) // 1 live symptom imported

        // 4. Verify all records in targetDb
        val targetProfile = targetDb.userProfileDao().count()
        assertThat(targetProfile).isEqualTo(1)

        val targetCycles = targetDb.cycleRecordDao().getAllForBackup()
        assertThat(targetCycles).hasSize(2)
        val liveCycle = targetCycles.first { it.id == "cycle-live-1" }
        assertThat(liveCycle.source).isEqualTo(CycleSource.Imported.name)
        assertThat(liveCycle.notes).isEqualTo("Live note")
        assertThat(liveCycle.deletedAtEpochMillis).isNull()

        val deletedCycle = targetCycles.first { it.id == "cycle-tombstone-2" }
        assertThat(deletedCycle.deletedAtEpochMillis).isEqualTo(950L)

        val targetLogs = targetDb.symptomLogDao().getAllForBackup()
        assertThat(targetLogs).hasSize(2)
        val liveLog = targetLogs.first { it.id == "log-live-1" }
        assertThat(liveLog.notes).isEqualTo("Symptom note")
        assertThat(liveLog.deletedAtEpochMillis).isNull()

        val deletedLog = targetLogs.first { it.id == "log-tombstone-2" }
        assertThat(deletedLog.deletedAtEpochMillis).isEqualTo(1250L)

        val targetDailySymptoms = targetDb.dailySymptomDao().getAllForBackup()
        assertThat(targetDailySymptoms).hasSize(1)
        assertThat(targetDailySymptoms[0].symptomType).isEqualTo("Cramps")

        val targetRules = targetDb.reminderRuleDao().getAllForBackup()
        assertThat(targetRules).hasSize(1)
        assertThat(targetRules[0].type).isEqualTo(ReminderType.PeriodPrep.name)

        backupFile.delete()
        }
    }

    @Test
    fun import_withIncorrectPassphrase_failsAndLeavesDatabaseEmpty() {
        runBlocking {
            sourceDb.userProfileDao().upsert(
                UserProfileEntity(
                    ageBand = AgeBand.Twenties.name,
                    averageCycleLength = 28,
                    periodLength = 5,
                    lastPeriodStartEpochDay = LocalDate.of(2026, 3, 1).toEpochDay(),
                    trackingGoal = TrackingGoal.PeriodPrep.name,
                    remindersEnabled = true,
                    privacyModeEnabled = true,
                )
            )

            val sourceRepo = createRepository(sourceDb)
            val targetRepo = createRepository(targetDb)
            val correctPassphrase = "ValidPassword123!".toCharArray()
            val wrongPassphrase = "WrongPassword456!".toCharArray()

            val artifact = sourceRepo.createEncryptedBackup(correctPassphrase)
            val backupFile = File(artifact.absolutePath)

            val result = FileInputStream(backupFile).use { stream ->
                targetRepo.importEncryptedBackup(stream, wrongPassphrase)
            }

            assertThat(result).isInstanceOf(ImportResult.Failure.InvalidPassphrase::class.java)
            // Verify database is completely empty
            assertThat(targetDb.userProfileDao().count()).isEqualTo(0)
            assertThat(targetDb.cycleRecordDao().count()).isEqualTo(0)
            assertThat(targetDb.symptomLogDao().count()).isEqualTo(0)

            backupFile.delete()
        }
    }

    @Test
    fun import_withCorruptedMagicHeader_failsWithCorruptedFile() {
        runBlocking {
            val targetRepo = createRepository(targetDb)
            val fakeBytes = "NOT_A_VAULT_HEADER_DATA_123456789".toByteArray()

            val result = ByteArrayInputStream(fakeBytes).use { stream ->
                targetRepo.importEncryptedBackup(stream, "AnyPassword123!".toCharArray())
            }

            assertThat(result).isInstanceOf(ImportResult.Failure.CorruptedFile::class.java)
            assertThat(targetDb.userProfileDao().count()).isEqualTo(0)
        }
    }

    @Test
    fun import_withTamperedCiphertext_failsWithInvalidPassphraseAndLeavesDbEmpty() {
        runBlocking {
            sourceDb.userProfileDao().upsert(
                UserProfileEntity(
                    ageBand = AgeBand.Twenties.name,
                    averageCycleLength = 28,
                    periodLength = 5,
                    lastPeriodStartEpochDay = LocalDate.of(2026, 3, 1).toEpochDay(),
                    trackingGoal = TrackingGoal.PeriodPrep.name,
                    remindersEnabled = true,
                    privacyModeEnabled = true,
                )
            )

            val sourceRepo = createRepository(sourceDb)
            val targetRepo = createRepository(targetDb)
            val passphrase = "TamperTestPassword123!".toCharArray()

            val artifact = sourceRepo.createEncryptedBackup(passphrase)
            val backupFile = File(artifact.absolutePath)
            val bytes = backupFile.readBytes()

            // Tamper 1 bit in the ciphertext near the end of the file
            bytes[bytes.size - 2] = (bytes[bytes.size - 2] xor 1).toByte()

            val result = ByteArrayInputStream(bytes).use { stream ->
                targetRepo.importEncryptedBackup(stream, passphrase)
            }

            assertThat(result).isInstanceOf(ImportResult.Failure.InvalidPassphrase::class.java)
            assertThat(targetDb.userProfileDao().count()).isEqualTo(0)

            backupFile.delete()
        }
    }
}
