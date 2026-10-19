package com.cyclecare.core.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.withTransaction
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.ImportResult
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
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

    internal fun createMarkdownArtifact(report: WellnessReportContent): ExportArtifact {
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

    internal fun createPdfArtifact(report: WellnessReportContent): ExportArtifact {
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

    internal fun exportDirectory(): File = File(context.filesDir, "exports").apply { mkdirs() }
}

internal data class WellnessReportContent(
    val generatedAt: LocalDate,
    val markdown: String,
    val pdfLines: List<String>,
)

internal fun buildReportContent(
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

internal fun <T> topCountLabel(counts: Map<T, Int>, fallback: T): String? {
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

internal fun wrapPdfLine(line: String, maxLength: Int = 78): List<String> {
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
