package com.cyclecare.core.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.CycleRecordEntity
import com.cyclecare.core.database.ReminderEventEntity
import com.cyclecare.core.database.ReminderRuleEntity
import com.cyclecare.core.database.SymptomLogEntity
import com.cyclecare.core.database.UserProfileEntity
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.ImportResult
import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.security.AesGcmPayloadCipher
import com.cyclecare.core.security.DEFAULT_PBKDF2_ITERATIONS
import com.cyclecare.core.security.GCM_NONCE_BYTES
import com.cyclecare.core.security.KDF_SALT_BYTES
import com.cyclecare.core.security.PassphraseKeyDeriver
import com.cyclecare.core.security.PassphrasePolicy
import com.cyclecare.core.security.PayloadCipher
import com.cyclecare.core.security.Pbkdf2PassphraseKeyDeriver
import com.cyclecare.core.security.SyncEnvelope
import com.cyclecare.core.security.VaultException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.SecureRandom
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.prediction.CyclePredictionEngineV2
import com.cyclecare.core.domain.prediction.CyclePredictionInput
import com.cyclecare.core.domain.prediction.ObservedCycleStart
import com.cyclecare.core.domain.prediction.toSnapshot
import com.cyclecare.core.domain.time.TimeProvider
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.SyncAdapter
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "cyclecare_prefs")

private object PreferenceKeys {
    val hideNotificationPreview = booleanPreferencesKey("hide_notification_preview")
    val localDataAcknowledged = booleanPreferencesKey("local_data_acknowledged")
    val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
    val pinHash = stringPreferencesKey("pin_hash")
    val pinSalt = stringPreferencesKey("pin_salt")
    val lastUnlockEpochMillis = longPreferencesKey("last_unlock_epoch_millis")
    val biometricEnabled = booleanPreferencesKey("biometric_enabled")

    fun channelEnabled(key: ReminderChannelKey) = booleanPreferencesKey("reminder_channel_${key.name.lowercase()}_enabled")
    fun channelSoundEnabled(key: ReminderChannelKey) = booleanPreferencesKey("reminder_channel_${key.name.lowercase()}_sound_enabled")
    fun channelSoundPreset(key: ReminderChannelKey) = stringPreferencesKey("reminder_channel_${key.name.lowercase()}_sound_preset")
    fun channelVibrationEnabled(key: ReminderChannelKey) = booleanPreferencesKey("reminder_channel_${key.name.lowercase()}_vibration_enabled")
}

class RoomProfileRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> =
        database.userProfileDao().observeProfile().map { it?.toModel(logger) }

    override suspend fun saveProfile(profile: UserProfile) {
        database.userProfileDao().upsert(profile.toEntity())
    }

    override suspend fun updateRemindersEnabled(enabled: Boolean) {
        database.userProfileDao().updateRemindersEnabled(enabled)
    }
}

class RoomCycleRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : CycleRepository {
    override fun observeCycles(): Flow<List<CycleRecord>> =
        database.cycleRecordDao().observeAll().map { records -> records.map { it.toModel(logger) } }

    override suspend fun upsertCycle(record: CycleRecord) {
        database.cycleRecordDao().upsert(record.toEntity(System.currentTimeMillis()))
    }

    override suspend fun deleteCycle(id: String) {
        database.cycleRecordDao().softDelete(id, System.currentTimeMillis())
    }
}

class RoomSymptomRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : SymptomRepository {
    override fun observeSymptoms(): Flow<List<SymptomLog>> =
        database.symptomLogDao().observeAll().map { logs -> logs.map { it.toModel(logger) } }

    override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> =
        database.symptomLogDao()
            .observeLatestForDate(date.toEpochDay())
            .map { it?.toModel(logger) }

    override suspend fun upsertSymptom(log: SymptomLog) {
        database.symptomLogDao().upsert(log.toEntity(System.currentTimeMillis()))
    }
}

class LocalPredictionRepository(
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val engine: CyclePredictionEngineV2,
    private val timeProvider: TimeProvider,
) : PredictionRepository {
    override fun observePrediction(): Flow<PredictionSnapshot?> =
        combine(profileRepository.observeProfile(), cycleRepository.observeCycles()) { profile, cycles ->
            val today = timeProvider.today()

            // Predicted rows are the v1 engine's own output written back
            // into the cycle table. Feeding them back as history would
            // let an estimate reinforce itself.
            val observed = cycles
                .filter { it.source != CycleSource.Predicted }
                .map { record ->
                    ObservedCycleStart(
                        id = record.id,
                        startDate = record.startDate,
                        endDate = record.endDate,
                        source = record.source,
                    )
                }

            val reported = profile?.averageCycleLength

            engine.predict(
                CyclePredictionInput(
                    observed = observed,
                    userReportedAverageCycleLength = reported,
                    today = today,
                ),
            )?.toSnapshot()
        }.flowOn(Dispatchers.Default)
}

class LocalInsightRepository(
    private val cycleRepository: CycleRepository,
    private val symptomRepository: SymptomRepository,
    private val engine: InsightEngine,
) : InsightRepository {
    override fun observeInsights(): Flow<InsightSnapshot> =
        combine(cycleRepository.observeCycles(), symptomRepository.observeSymptoms()) { cycles, symptoms ->
            engine.build(cycles, symptoms)
        }.flowOn(Dispatchers.Default)
}

class RoomReminderRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderRepository {
    override fun observeRules(): Flow<List<ReminderRule>> =
        database.reminderRuleDao().observeAll().map { rules -> rules.map { it.toModel(logger) } }

    override suspend fun upsertRule(rule: ReminderRule) {
        database.reminderRuleDao().upsert(rule.toEntity())
    }
}

class RoomReminderEventsRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderEventsRepository {
    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> =
        database.reminderEventDao().observeRecent(limit).map { events -> events.map { it.toModel(logger) } }

    override fun observeUnreadCount(): Flow<Int> =
        database.reminderEventDao().observeUnreadCount()

    override fun observeNextUnread(): Flow<ReminderEvent?> =
        database.reminderEventDao().observeNextUnread().map { it?.toModel(logger) }

    override suspend fun recordEvent(event: ReminderEvent) {
        database.reminderEventDao().upsert(event.toEntity())
    }

    override suspend fun markRead(id: String) {
        database.reminderEventDao().markRead(id, System.currentTimeMillis())
    }

    override suspend fun markAllRead() {
        database.reminderEventDao().markAllRead(System.currentTimeMillis())
    }

    override suspend fun dismiss(id: String) {
        database.reminderEventDao().dismiss(id, System.currentTimeMillis())
    }
}

class LocalReminderChannelSettingsRepository(
    private val context: Context,
    private val logger: AppLogger = NoOpAppLogger,
) : ReminderChannelSettingsRepository {
    override fun observeChannelSettings(): Flow<List<ReminderChannelSettings>> =
        context.dataStore.data.map { preferences ->
            ReminderChannelKey.entries.map { channelKey ->
                val defaults = defaultReminderChannelSettings(channelKey)
                ReminderChannelSettings(
                    channelKey = channelKey,
                    enabled = preferences[PreferenceKeys.channelEnabled(channelKey)] ?: defaults.enabled,
                    soundEnabled = preferences[PreferenceKeys.channelSoundEnabled(channelKey)] ?: defaults.soundEnabled,
                    soundPreset = preferences[PreferenceKeys.channelSoundPreset(channelKey)]
                        ?.toReminderSoundPreset(logger)
                        ?: defaults.soundPreset,
                    vibrationEnabled = preferences[PreferenceKeys.channelVibrationEnabled(channelKey)] ?: defaults.vibrationEnabled,
                )
            }
        }

    override suspend fun upsertChannelSettings(settings: ReminderChannelSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.channelEnabled(settings.channelKey)] = settings.enabled
            preferences[PreferenceKeys.channelSoundEnabled(settings.channelKey)] = settings.soundEnabled
            preferences[PreferenceKeys.channelSoundPreset(settings.channelKey)] = settings.soundPreset.name
            preferences[PreferenceKeys.channelVibrationEnabled(settings.channelKey)] = settings.vibrationEnabled
        }
    }
}

class LocalPrivacyRepository(
    private val context: Context,
) : PrivacyRepository {
    override fun observeConsent(): Flow<ConsentState> =
        context.dataStore.data.map { preferences ->
            ConsentState(
                hideNotificationPreview = preferences[PreferenceKeys.hideNotificationPreview] ?: true,
                localDataAcknowledged = preferences[PreferenceKeys.localDataAcknowledged] ?: false,
                appLockEnabled = preferences[PreferenceKeys.appLockEnabled] ?: false,
                biometricEnabled = preferences[PreferenceKeys.biometricEnabled] ?: false,
            )
        }

    override fun hasPinConfigured(): Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            !preferences[PreferenceKeys.pinHash].isNullOrBlank()
        }

    override fun observeLastUnlockEpochMillis(): Flow<Long?> =
        context.dataStore.data.map { preferences -> preferences[PreferenceKeys.lastUnlockEpochMillis] }

    override fun isBiometricEnabled(): Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferenceKeys.biometricEnabled] ?: false
        }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.appLockEnabled] = enabled
            if (!enabled) {
                preferences[PreferenceKeys.biometricEnabled] = false
            }
        }
    }

    override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.hideNotificationPreview] = hidden
        }
    }

    override suspend fun acknowledgeLocalData() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.localDataAcknowledged] = true
        }
    }

    override suspend fun setPin(pin: String) {
        val salt = SecureRandom().let { random ->
            ByteArray(16).also { random.nextBytes(it) }
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.pinSalt] = salt
            preferences[PreferenceKeys.pinHash] = (salt + pin).sha256()
        }
    }

    override suspend fun clearPin() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferenceKeys.pinHash)
            preferences.remove(PreferenceKeys.pinSalt)
            preferences.remove(PreferenceKeys.lastUnlockEpochMillis)
            preferences[PreferenceKeys.appLockEnabled] = false
            preferences[PreferenceKeys.biometricEnabled] = false
        }
    }

    override suspend fun resetAppLock() {
        clearPin()
    }

    override suspend fun validatePin(pin: String): Boolean {
        val preferences = context.dataStore.dataMap()
        val salt = preferences[PreferenceKeys.pinSalt] ?: return false
        return preferences[PreferenceKeys.pinHash] == (salt + pin).sha256()
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val hasPin = !preferences[PreferenceKeys.pinHash].isNullOrBlank()
            val appLockEnabled = preferences[PreferenceKeys.appLockEnabled] ?: false
            preferences[PreferenceKeys.biometricEnabled] = enabled && hasPin && appLockEnabled
        }
    }

    override suspend fun markUnlocked(epochMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.lastUnlockEpochMillis] = epochMillis
        }
    }
}

class LocalExportRepository(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val symptomRepository: SymptomRepository,
    private val insightRepository: InsightRepository,
    private val database: CycleCareDatabase? = null,
    private val cipher: PayloadCipher = AesGcmPayloadCipher(),
    private val keyDeriver: PassphraseKeyDeriver = Pbkdf2PassphraseKeyDeriver(),
) : ExportRepository {
    companion object {
        val BACKUP_MAGIC = "CYCLECARE_VAULT\u0001".toByteArray(Charsets.UTF_8)
        const val BACKUP_SCHEMA_VERSION = 1
        const val BACKUP_KEY_VERSION = 1
        const val BACKUP_RECORD_ID = "cyclecare_local_backup"
    }

    override suspend fun createReport(format: ExportFormat): ExportArtifact {
        return withContext(Dispatchers.IO) {
            val profile = profileRepository.observeProfile().firstValue()
            val cycles = cycleRepository.observeCycles().firstValue()
            val symptoms = symptomRepository.observeSymptoms().firstValue()
            val insights = insightRepository.observeInsights().firstValue()
            val report = buildReportContent(
                generatedAt = LocalDate.now(),
                profile = profile,
                cycles = cycles,
                symptoms = symptoms,
                insights = insights,
            )

            when (format) {
                ExportFormat.Markdown -> createMarkdownArtifact(report)
                ExportFormat.Pdf -> createPdfArtifact(report)
                ExportFormat.EncryptedBackup -> throw IllegalArgumentException(
                    "Call createEncryptedBackup(passphrase) to generate an encrypted backup."
                )
            }
        }
    }

    override suspend fun createEncryptedBackup(passphrase: CharArray): ExportArtifact = withContext(Dispatchers.IO) {
        val db = checkNotNull(database) { "CycleCareDatabase must be provided for encrypted backups" }
        val validation = PassphrasePolicy.validate(passphrase)
        if (validation != null) {
            throw VaultException(validation)
        }

        val salt = ByteArray(KDF_SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val vaultKey = keyDeriver.deriveVaultKey(passphrase, salt, DEFAULT_PBKDF2_ITERATIONS)

        val profile = db.userProfileDao().observeProfile().first()
        val cycles = db.cycleRecordDao().getAllForBackup()
        val logs = db.symptomLogDao().getAllForBackup()
        val dailySymptoms = db.dailySymptomDao().getAllForBackup()
        val rules = db.reminderRuleDao().getAllForBackup()

        val payload = BackupPayload(
            profile = profile,
            cycles = cycles,
            symptomLogs = logs,
            dailySymptoms = dailySymptoms,
            reminderRules = rules,
        )

        val plaintext = BackupPayloadSerializer.serialize(payload)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = BACKUP_RECORD_ID,
            schemaVersion = BACKUP_SCHEMA_VERSION,
            keyVersion = BACKUP_KEY_VERSION,
            key = vaultKey,
        )
        plaintext.fill(0)

        val file = File(exportDirectory(), "cyclecare-backup-${LocalDate.now()}.ccvault")
        DataOutputStream(FileOutputStream(file)).use { dos ->
            dos.write(BACKUP_MAGIC)
            dos.writeInt(envelope.schemaVersion)
            dos.writeInt(envelope.keyVersion)
            dos.write(salt)
            dos.writeInt(DEFAULT_PBKDF2_ITERATIONS)
            dos.write(envelope.nonce)
            dos.writeInt(envelope.ciphertext.size)
            dos.write(envelope.ciphertext)
        }

        ExportArtifact(
            format = ExportFormat.EncryptedBackup,
            displayName = file.name,
            absolutePath = file.absolutePath,
            mimeType = "application/octet-stream",
            generatedAt = LocalDate.now(),
        )
    }

    override suspend fun importEncryptedBackup(
        inputStream: InputStream,
        passphrase: CharArray,
    ): ImportResult = withContext(Dispatchers.IO) {
        val db = checkNotNull(database) { "CycleCareDatabase must be provided for encrypted backups" }
        val magic = ByteArray(BACKUP_MAGIC.size)
        val schemaVersion: Int
        val keyVersion: Int
        val salt = ByteArray(KDF_SALT_BYTES)
        val iterations: Int
        val nonce = ByteArray(GCM_NONCE_BYTES)
        val ciphertext: ByteArray

        try {
            val dis = DataInputStream(inputStream)
            dis.readFully(magic)
            if (!magic.contentEquals(BACKUP_MAGIC)) {
                return@withContext ImportResult.Failure.CorruptedFile
            }
            schemaVersion = dis.readInt()
            if (schemaVersion > BACKUP_SCHEMA_VERSION) {
                return@withContext ImportResult.Failure.UnsupportedVersion(schemaVersion)
            }
            keyVersion = dis.readInt()
            dis.readFully(salt)
            iterations = dis.readInt()
            dis.readFully(nonce)
            val ciphertextLength = dis.readInt()
            if (ciphertextLength <= 0 || ciphertextLength > 50 * 1024 * 1024) {
                return@withContext ImportResult.Failure.CorruptedFile
            }
            ciphertext = ByteArray(ciphertextLength)
            dis.readFully(ciphertext)
        } catch (_: Exception) {
            return@withContext ImportResult.Failure.CorruptedFile
        }

        val vaultKey = try {
            keyDeriver.deriveVaultKey(passphrase, salt, iterations)
        } catch (_: Exception) {
            return@withContext ImportResult.Failure.InvalidPassphrase
        }

        val envelope = SyncEnvelope(
            schemaVersion = schemaVersion,
            keyVersion = keyVersion,
            nonce = nonce,
            ciphertext = ciphertext,
        )

        val decryptResult = cipher.decrypt(envelope, recordId = BACKUP_RECORD_ID, key = vaultKey)
        if (decryptResult.isFailure) {
            return@withContext ImportResult.Failure.InvalidPassphrase
        }

        val plaintext = decryptResult.getOrThrow()
        val payload = try {
            BackupPayloadSerializer.deserialize(plaintext)
        } catch (_: Exception) {
            return@withContext ImportResult.Failure.CorruptedFile
        } finally {
            plaintext.fill(0)
        }

        try {
            db.withTransaction {
                payload.profile?.let { importedProfile ->
                    db.userProfileDao().upsert(importedProfile)
                }

                var importedCyclesCount = 0
                payload.cycles.forEach { importedCycle ->
                    val existing = db.cycleRecordDao().findById(importedCycle.id)
                    if (existing == null) {
                        if (importedCycle.deletedAtEpochMillis == null) {
                            val liveCount = db.cycleRecordDao().countLiveForStart(importedCycle.startEpochDay)
                            if (liveCount == 0) {
                                db.cycleRecordDao().upsert(importedCycle.copy(source = CycleSource.Imported.name))
                                importedCyclesCount++
                            }
                        } else {
                            db.cycleRecordDao().upsert(importedCycle)
                        }
                    } else {
                        val existingDeleted = existing.deletedAtEpochMillis != null
                        val importedDeleted = importedCycle.deletedAtEpochMillis != null
                        if (existingDeleted && !importedDeleted) {
                            if (existing.deletedAtEpochMillis!! < importedCycle.updatedAtEpochMillis) {
                                db.cycleRecordDao().upsert(importedCycle.copy(source = CycleSource.Imported.name))
                                importedCyclesCount++
                            }
                        } else if (!existingDeleted && importedDeleted) {
                            if (importedCycle.deletedAtEpochMillis!! >= existing.updatedAtEpochMillis) {
                                db.cycleRecordDao().upsert(importedCycle)
                            }
                        } else {
                            if (importedCycle.updatedAtEpochMillis > existing.updatedAtEpochMillis) {
                                db.cycleRecordDao().upsert(importedCycle.copy(source = CycleSource.Imported.name))
                                importedCyclesCount++
                            }
                        }
                    }
                }

                var importedSymptomsCount = 0
                payload.symptomLogs.forEach { importedLog ->
                    val existing = db.symptomLogDao().findById(importedLog.id)
                    if (existing == null) {
                        if (importedLog.deletedAtEpochMillis == null) {
                            val liveCount = db.symptomLogDao().countLiveForDate(importedLog.dateEpochDay)
                            if (liveCount == 0) {
                                db.symptomLogDao().upsert(importedLog)
                                importedSymptomsCount++
                            }
                        } else {
                            db.symptomLogDao().upsert(importedLog)
                        }
                    } else {
                        if (importedLog.updatedAtEpochMillis > existing.updatedAtEpochMillis) {
                            db.symptomLogDao().upsert(importedLog)
                            importedSymptomsCount++
                        }
                    }
                }

                if (payload.dailySymptoms.isNotEmpty()) {
                    db.dailySymptomDao().upsertAll(payload.dailySymptoms)
                }

                payload.reminderRules.forEach { rule ->
                    db.reminderRuleDao().upsert(rule)
                }

                ImportResult.Success(
                    cyclesImported = importedCyclesCount,
                    symptomsImported = importedSymptomsCount,
                    profileImported = payload.profile != null,
                )
            }
        } catch (e: Exception) {
            ImportResult.Failure.Error(e.message ?: "Transaction failed")
        }
    }

    private fun createMarkdownArtifact(report: WellnessReportContent): ExportArtifact {
        val file = File(exportDirectory(), "cyclecare-report-${report.generatedAt}.md")
        file.writeText(report.markdown)
        return ExportArtifact(
            format = ExportFormat.Markdown,
            displayName = file.name,
            absolutePath = file.absolutePath,
            mimeType = "text/markdown",
            generatedAt = report.generatedAt,
        )
    }

    private fun createPdfArtifact(report: WellnessReportContent): ExportArtifact {
        val file = File(exportDirectory(), "cyclecare-report-${report.generatedAt}.pdf")
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 48f
        val topMargin = 56f
        val lineHeight = 20f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = topMargin

        fun startNewPage() {
            document.finishPage(page)
            pageNumber += 1
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = topMargin
        }

        report.pdfLines.forEach { line ->
            if (y >= pageHeight - topMargin) {
                startNewPage()
            }
            canvas.drawText(line, leftMargin, y, paint)
            y += lineHeight
        }

        document.finishPage(page)
        FileOutputStream(file).use { output -> document.writeTo(output) }
        document.close()

        return ExportArtifact(
            format = ExportFormat.Pdf,
            displayName = file.name,
            absolutePath = file.absolutePath,
            mimeType = "application/pdf",
            generatedAt = report.generatedAt,
        )
    }

    private fun exportDirectory(): File = File(context.filesDir, "exports").apply { mkdirs() }
}

class LocalAppResetRepository(
    private val context: Context,
    private val database: CycleCareDatabase,
) : AppResetRepository {
    override suspend fun resetAllLocalData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            File(context.filesDir, "exports").deleteRecursively()
            context.dataStore.edit { preferences -> preferences.clear() }
            context.getSharedPreferences(notificationPermissionPrefsName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}

class NoOpSyncAdapter : SyncAdapter {
    override suspend fun pushPendingChanges() = Unit

    override suspend fun pullRemoteChanges() = Unit

    override suspend fun mergePreview(): String = "Sync is not enabled in v1."
}

private suspend fun androidx.datastore.core.DataStore<Preferences>.dataMap(): Preferences = data.first()

private suspend fun <T> Flow<T>.firstValue(): T = first()

private fun UserProfile.toEntity() = UserProfileEntity(
    ageBand = ageBand.name,
    averageCycleLength = averageCycleLength,
    periodLength = periodLength,
    lastPeriodStartEpochDay = lastPeriodStart.toEpochDay(),
    trackingGoal = trackingGoal.name,
    remindersEnabled = remindersEnabled,
    privacyModeEnabled = privacyModeEnabled,
)

private fun UserProfileEntity.toModel(logger: AppLogger) = UserProfile(
    ageBand = ageBand.toAgeBand(logger),
    averageCycleLength = averageCycleLength,
    periodLength = periodLength,
    lastPeriodStart = LocalDate.ofEpochDay(lastPeriodStartEpochDay),
    trackingGoal = trackingGoal.toTrackingGoal(logger),
    remindersEnabled = remindersEnabled,
    privacyModeEnabled = privacyModeEnabled,
)

private fun CycleRecord.toEntity(nowEpochMillis: Long) = CycleRecordEntity(
    id = id,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate?.toEpochDay(),
    cycleLength = cycleLength,
    source = source.name,
    notes = notes,
    createdAtEpochMillis = nowEpochMillis,
    updatedAtEpochMillis = nowEpochMillis,
    deletedAtEpochMillis = null,
)

private fun CycleRecordEntity.toModel(logger: AppLogger) = CycleRecord(
    id = id,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = endEpochDay?.let { LocalDate.ofEpochDay(it) },
    cycleLength = cycleLength,
    source = source.toCycleSource(logger),
    notes = notes,
)

private fun SymptomLog.toEntity(nowEpochMillis: Long) = SymptomLogEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    mood = mood?.name,
    painLevel = painLevel?.name,
    energyLevel = energyLevel?.name,
    dischargeType = dischargeType?.name,
    medication = medication,
    notes = notes,
    createdAtEpochMillis = nowEpochMillis,
    updatedAtEpochMillis = nowEpochMillis,
    deletedAtEpochMillis = null,
)

private fun SymptomLogEntity.toModel(logger: AppLogger) = SymptomLog(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    mood = mood.toMood(logger),
    painLevel = painLevel.toPainLevel(logger),
    energyLevel = energyLevel.toEnergyLevel(logger),
    dischargeType = dischargeType.toDischargeType(logger),
    medication = medication,
    notes = notes,
)

private fun ReminderRule.toEntity() = ReminderRuleEntity(
    id = id,
    type = type.name,
    enabled = enabled,
    offsetDays = daysBefore,
    hourOfDay = hourOfDay,
    minute = minute,
    // Any time reaching the database before v3 came from a seed, never
    // from the user, so nothing is marked pinned yet.
    userPinnedTime = false,
)

private fun ReminderRuleEntity.toModel(logger: AppLogger) = ReminderRule(
    id = id,
    type = type.toReminderType(logger),
    enabled = enabled,
    daysBefore = offsetDays,
    hourOfDay = hourOfDay,
    minute = minute,
)

private fun ReminderEvent.toEntity() = ReminderEventEntity(
    id = id,
    ruleId = ruleId,
    type = type.name,
    channelKey = channelKey.name,
    scheduledAtEpochMillis = scheduledAt.toInstant().toEpochMilli(),
    deliveredAtEpochMillis = deliveredAt.toInstant().toEpochMilli(),
    title = title,
    body = body,
    privateBody = privateBody,
    deepLinkTarget = deepLinkTarget.name,
    targetEpochDay = targetEpochDay,
    readAtEpochMillis = readAtEpochMillis,
    dismissedAtEpochMillis = dismissedAtEpochMillis,
    deliveryState = "DELIVERED",
)

private fun ReminderEventEntity.toModel(logger: AppLogger) = ReminderEvent(
    id = id,
    ruleId = ruleId,
    type = type.toReminderType(logger),
    channelKey = channelKey.toReminderChannelKey(logger),
    scheduledAt = java.time.Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
    deliveredAt = java.time.Instant.ofEpochMilli(deliveredAtEpochMillis).atZone(java.time.ZoneId.systemDefault()),
    title = title,
    body = body,
    privateBody = privateBody,
    deepLinkTarget = deepLinkTarget.toReminderDeepLinkTarget(logger),
    targetEpochDay = targetEpochDay,
    readAtEpochMillis = readAtEpochMillis,
    dismissedAtEpochMillis = dismissedAtEpochMillis,
)

private fun defaultReminderChannelSettings(
    channelKey: ReminderChannelKey,
) = when (channelKey) {
    ReminderChannelKey.CycleAlerts -> ReminderChannelSettings(
        channelKey = channelKey,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.GentleBell,
        vibrationEnabled = true,
    )
    ReminderChannelKey.FertilityAlerts -> ReminderChannelSettings(
        channelKey = channelKey,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.WaterDrop,
        vibrationEnabled = true,
    )
    ReminderChannelKey.DailyCheckIn -> ReminderChannelSettings(
        channelKey = channelKey,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.SoftChime,
        vibrationEnabled = false,
    )
}

private fun String.toAgeBand(logger: AppLogger): AgeBand = parseEnumSafely(
    raw = this,
    fallback = AgeBand.Twenties,
    tag = "ProfileRepository",
    field = "ageBand",
    logger = logger,
)

private fun String.toTrackingGoal(logger: AppLogger): TrackingGoal = parseEnumSafely(
    raw = this,
    fallback = TrackingGoal.PeriodPrep,
    tag = "ProfileRepository",
    field = "trackingGoal",
    logger = logger,
)

private fun String.toCycleSource(logger: AppLogger): CycleSource = parseEnumSafely(
    raw = this,
    fallback = CycleSource.User,
    tag = "CycleRepository",
    field = "source",
    logger = logger,
)

/**
 * Null means not tracked and is preserved as null.
 *
 * This must not fall back to a default: the v2 schema declared these
 * NOT NULL and neither Mood nor EnergyLevel has a "None" variant, so
 * every user was forced to answer both on every log. Defaulting an
 * untracked day would count a value the user never gave.
 *
 * An unrecognised non-null value still falls back, and is logged, so
 * that an unexpected stored string degrades rather than throwing.
 */
private fun String?.toMood(logger: AppLogger): Mood? = parseEnumOrNull(
    raw = this,
    fallback = Mood.Stable,
    tag = "SymptomRepository",
    field = "mood",
    logger = logger,
)

/** Null means not tracked, which is distinct from a recorded "None". */
private fun String?.toPainLevel(logger: AppLogger): PainLevel? = parseEnumOrNull(
    raw = this,
    fallback = PainLevel.None,
    tag = "SymptomRepository",
    field = "painLevel",
    logger = logger,
)

private fun String?.toEnergyLevel(logger: AppLogger): EnergyLevel? = parseEnumOrNull(
    raw = this,
    fallback = EnergyLevel.Steady,
    tag = "SymptomRepository",
    field = "energyLevel",
    logger = logger,
)

/** Null means not tracked, which is distinct from a recorded "None". */
private fun String?.toDischargeType(logger: AppLogger): DischargeType? = parseEnumOrNull(
    raw = this,
    fallback = DischargeType.None,
    tag = "SymptomRepository",
    field = "dischargeType",
    logger = logger,
)

private fun String.toReminderType(logger: AppLogger): ReminderType = parseEnumSafely(
    raw = this,
    fallback = ReminderType.DailyLog,
    tag = "ReminderRepository",
    field = "type",
    logger = logger,
)

private fun String.toReminderChannelKey(logger: AppLogger): ReminderChannelKey = parseEnumSafely(
    raw = this,
    fallback = ReminderChannelKey.DailyCheckIn,
    tag = "ReminderRepository",
    field = "channelKey",
    logger = logger,
)

private fun String.toReminderSoundPreset(logger: AppLogger): ReminderSoundPreset = parseEnumSafely(
    raw = this,
    fallback = ReminderSoundPreset.SoftChime,
    tag = "ReminderRepository",
    field = "soundPreset",
    logger = logger,
)

private fun String.toReminderDeepLinkTarget(logger: AppLogger): ReminderDeepLinkTarget = parseEnumSafely(
    raw = this,
    fallback = ReminderDeepLinkTarget.Home,
    tag = "ReminderEventsRepository",
    field = "deepLinkTarget",
    logger = logger,
)

/**
 * Nullable variant used by the daily log fields.
 *
 * A null column means the user did not track that field, and that is a
 * real answer that must survive the round trip. Only a non-null but
 * unrecognised value falls back, and that is logged.
 */
private inline fun <reified T : Enum<T>> parseEnumOrNull(
    raw: String?,
    fallback: T,
    tag: String,
    field: String,
    logger: AppLogger,
): T? {
    if (raw == null) return null
    return runCatching { enumValueOf<T>(raw) }
        .getOrElse {
            logger.warn(
                tag = tag,
                event = "invalid_enum_fallback",
                attributes = mapOf(
                    "field" to field,
                    "raw" to raw,
                    "fallback" to fallback.name,
                ),
            )
            fallback
        }
}

private inline fun <reified T : Enum<T>> parseEnumSafely(
    raw: String,
    fallback: T,
    tag: String,
    field: String,
    logger: AppLogger,
): T = runCatching { enumValueOf<T>(raw) }
    .getOrElse { error ->
        logger.warn(
            tag = tag,
            event = "invalid_enum_fallback",
            attributes = mapOf(
                "field" to field,
                "raw" to raw,
                "fallback" to fallback.name,
            ),
            throwable = error,
        )
        fallback
    }

private fun String.sha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

private data class WellnessReportContent(
    val generatedAt: LocalDate,
    val markdown: String,
    val pdfLines: List<String>,
)

private fun buildReportContent(
    generatedAt: LocalDate,
    profile: UserProfile?,
    cycles: List<CycleRecord>,
    symptoms: List<SymptomLog>,
    insights: InsightSnapshot,
): WellnessReportContent {
    val cycleLines = if (cycles.isEmpty()) {
        listOf("No cycle history has been logged yet.")
    } else {
        cycles.takeLast(8).map { cycle ->
            "${cycle.startDate} to ${cycle.endDate} (${cycle.cycleLength} day cycle)"
        }
    }
    val profileLines = listOf(
        "Age band: ${profile?.ageBand ?: "Not set"}",
        "Goal: ${profile?.trackingGoal ?: "Not set"}",
        "Average cycle length: ${profile?.averageCycleLength ?: "N/A"}",
        "Period length: ${profile?.periodLength ?: "N/A"}",
    )
    val symptomLines = listOf(
        "Logged entries: ${symptoms.size}",
        "Cycle window: ${insights.cycleWindowLabel}",
        "Cycle regularity: ${if (insights.irregularCycles) "Recent cycles vary more than usual." else "Recent cycles look fairly consistent."}",
        "Top mood signal: ${topCountLabel(insights.moodCounts, Mood.Stable) ?: "Not enough symptom data yet."}",
        "Top energy signal: ${topCountLabel(insights.energyCounts, EnergyLevel.Steady) ?: "Not enough symptom data yet."}",
        "Summary: ${insights.narrativeSummary}",
    )
    val markdown = buildString {
        appendLine("# CycleCare Wellness Report")
        appendLine()
        appendLine("Generated: $generatedAt")
        appendLine()
        appendLine("## Profile")
        profileLines.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Cycle History")
        cycleLines.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Symptom Summary")
        symptomLines.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Disclaimer")
        appendLine("This report is for wellness tracking only and should not be used as medical guidance.")
    }
    val pdfLines = listOf(
        "CycleCare Wellness Report",
        "",
        "Generated: $generatedAt",
        "",
        "Profile",
    ) + profileLines.flatMap(::wrapPdfLine) +
        listOf("", "Cycle History") +
        cycleLines.flatMap(::wrapPdfLine) +
        listOf("", "Symptom Summary") +
        symptomLines.flatMap(::wrapPdfLine) +
        listOf(
            "",
            "Disclaimer",
            "This report is for wellness tracking only and should not be used as medical guidance.",
        ).flatMap(::wrapPdfLine)

    return WellnessReportContent(
        generatedAt = generatedAt,
        markdown = markdown,
        pdfLines = pdfLines,
    )
}

private fun <T> topCountLabel(counts: Map<T, Int>, fallback: T): String? {
    val top = counts
        .entries
        .filter { it.value > 0 }
        .maxByOrNull { it.value }
        ?.key
        ?: return null
    return when (top) {
        is Mood -> top.name
        is EnergyLevel -> top.name
        is PainLevel -> top.name
        is DischargeType -> when (top) {
            DischargeType.EggWhite -> "Egg white"
            else -> top.name
        }
        else -> fallback.toString()
    }
}

private fun wrapPdfLine(line: String, maxLength: Int = 78): List<String> {
    if (line.length <= maxLength) return listOf(line)
    val words = line.split(' ')
    val wrapped = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val next = if (current.isBlank()) word else "$current $word"
        if (next.length > maxLength) {
            wrapped += current
            current = word
        } else {
            current = next
        }
    }
    if (current.isNotBlank()) wrapped += current
    return wrapped
}
