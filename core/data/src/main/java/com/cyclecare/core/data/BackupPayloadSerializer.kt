package com.cyclecare.core.data

import com.cyclecare.core.database.CycleRecordEntity
import com.cyclecare.core.database.DailySymptomEntity
import com.cyclecare.core.database.ReminderRuleEntity
import com.cyclecare.core.database.SymptomLogEntity
import com.cyclecare.core.database.UserProfileEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class BackupPayload(
    val profile: UserProfileEntity?,
    val cycles: List<CycleRecordEntity>,
    val symptomLogs: List<SymptomLogEntity>,
    val dailySymptoms: List<DailySymptomEntity>,
    val reminderRules: List<ReminderRuleEntity>,
)

object BackupPayloadSerializer {
    const val CURRENT_PAYLOAD_VERSION = 1

    fun serialize(payload: BackupPayload): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            dos.writeInt(CURRENT_PAYLOAD_VERSION)

            // UserProfile
            if (payload.profile != null) {
                dos.writeBoolean(true)
                dos.writeInt(payload.profile.id)
                dos.writeUTF(payload.profile.ageBand)
                dos.writeInt(payload.profile.averageCycleLength)
                dos.writeInt(payload.profile.periodLength)
                dos.writeLong(payload.profile.lastPeriodStartEpochDay)
                dos.writeUTF(payload.profile.trackingGoal)
                dos.writeBoolean(payload.profile.remindersEnabled)
                dos.writeBoolean(payload.profile.privacyModeEnabled)
            } else {
                dos.writeBoolean(false)
            }

            // Cycles
            dos.writeInt(payload.cycles.size)
            for (cycle in payload.cycles) {
                dos.writeUTF(cycle.id)
                dos.writeLong(cycle.startEpochDay)
                dos.writeBoolean(cycle.endEpochDay != null)
                cycle.endEpochDay?.let { dos.writeLong(it) }
                dos.writeInt(cycle.cycleLength)
                dos.writeUTF(cycle.source)
                dos.writeBoolean(cycle.notes != null)
                cycle.notes?.let { dos.writeUTF(it) }
                dos.writeLong(cycle.createdAtEpochMillis)
                dos.writeLong(cycle.updatedAtEpochMillis)
                dos.writeBoolean(cycle.deletedAtEpochMillis != null)
                cycle.deletedAtEpochMillis?.let { dos.writeLong(it) }
            }

            // Symptom logs
            dos.writeInt(payload.symptomLogs.size)
            for (log in payload.symptomLogs) {
                dos.writeUTF(log.id)
                dos.writeLong(log.dateEpochDay)

                dos.writeBoolean(log.mood != null)
                log.mood?.let { dos.writeUTF(it) }

                dos.writeBoolean(log.painLevel != null)
                log.painLevel?.let { dos.writeUTF(it) }

                dos.writeBoolean(log.energyLevel != null)
                log.energyLevel?.let { dos.writeUTF(it) }

                dos.writeBoolean(log.dischargeType != null)
                log.dischargeType?.let { dos.writeUTF(it) }

                dos.writeBoolean(log.medication != null)
                log.medication?.let { dos.writeUTF(it) }

                dos.writeBoolean(log.notes != null)
                log.notes?.let { dos.writeUTF(it) }

                dos.writeLong(log.createdAtEpochMillis)
                dos.writeLong(log.updatedAtEpochMillis)
                dos.writeBoolean(log.deletedAtEpochMillis != null)
                log.deletedAtEpochMillis?.let { dos.writeLong(it) }
            }

            // Daily symptoms
            dos.writeInt(payload.dailySymptoms.size)
            for (symptom in payload.dailySymptoms) {
                dos.writeUTF(symptom.id)
                dos.writeUTF(symptom.dailyLogId)
                dos.writeUTF(symptom.symptomType)
                dos.writeBoolean(symptom.severity != null)
                symptom.severity?.let { dos.writeUTF(it) }
                dos.writeBoolean(symptom.note != null)
                symptom.note?.let { dos.writeUTF(it) }
            }

            // Reminder rules
            dos.writeInt(payload.reminderRules.size)
            for (rule in payload.reminderRules) {
                dos.writeUTF(rule.id)
                dos.writeUTF(rule.type)
                dos.writeBoolean(rule.enabled)
                dos.writeInt(rule.offsetDays)
                dos.writeInt(rule.hourOfDay)
                dos.writeInt(rule.minute)
                dos.writeBoolean(rule.userPinnedTime)
            }
        }
        return baos.toByteArray()
    }

    fun deserialize(bytes: ByteArray): BackupPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            val payloadVersion = dis.readInt()
            if (payloadVersion != CURRENT_PAYLOAD_VERSION) {
                throw IllegalArgumentException("Unsupported payload version: $payloadVersion")
            }

            val hasProfile = dis.readBoolean()
            val profile = if (hasProfile) {
                UserProfileEntity(
                    id = dis.readInt(),
                    ageBand = dis.readUTF(),
                    averageCycleLength = dis.readInt(),
                    periodLength = dis.readInt(),
                    lastPeriodStartEpochDay = dis.readLong(),
                    trackingGoal = dis.readUTF(),
                    remindersEnabled = dis.readBoolean(),
                    privacyModeEnabled = dis.readBoolean(),
                )
            } else null

            val cyclesCount = dis.readInt()
            val cycles = ArrayList<CycleRecordEntity>(cyclesCount)
            for (i in 0 until cyclesCount) {
                val id = dis.readUTF()
                val startEpochDay = dis.readLong()
                val endEpochDay = if (dis.readBoolean()) dis.readLong() else null
                val cycleLength = dis.readInt()
                val source = dis.readUTF()
                val notes = if (dis.readBoolean()) dis.readUTF() else null
                val createdAt = dis.readLong()
                val updatedAt = dis.readLong()
                val deletedAt = if (dis.readBoolean()) dis.readLong() else null
                cycles.add(
                    CycleRecordEntity(
                        id = id,
                        startEpochDay = startEpochDay,
                        endEpochDay = endEpochDay,
                        cycleLength = cycleLength,
                        source = source,
                        notes = notes,
                        createdAtEpochMillis = createdAt,
                        updatedAtEpochMillis = updatedAt,
                        deletedAtEpochMillis = deletedAt,
                    ),
                )
            }

            val logsCount = dis.readInt()
            val logs = ArrayList<SymptomLogEntity>(logsCount)
            for (i in 0 until logsCount) {
                val id = dis.readUTF()
                val dateEpochDay = dis.readLong()
                val mood = if (dis.readBoolean()) dis.readUTF() else null
                val pain = if (dis.readBoolean()) dis.readUTF() else null
                val energy = if (dis.readBoolean()) dis.readUTF() else null
                val discharge = if (dis.readBoolean()) dis.readUTF() else null
                val med = if (dis.readBoolean()) dis.readUTF() else null
                val notes = if (dis.readBoolean()) dis.readUTF() else null
                val createdAt = dis.readLong()
                val updatedAt = dis.readLong()
                val deletedAt = if (dis.readBoolean()) dis.readLong() else null
                logs.add(
                    SymptomLogEntity(
                        id = id,
                        dateEpochDay = dateEpochDay,
                        mood = mood,
                        painLevel = pain,
                        energyLevel = energy,
                        dischargeType = discharge,
                        medication = med,
                        notes = notes,
                        createdAtEpochMillis = createdAt,
                        updatedAtEpochMillis = updatedAt,
                        deletedAtEpochMillis = deletedAt,
                    ),
                )
            }

            val symptomsCount = dis.readInt()
            val dailySymptoms = ArrayList<DailySymptomEntity>(symptomsCount)
            for (i in 0 until symptomsCount) {
                val id = dis.readUTF()
                val dailyLogId = dis.readUTF()
                val symptomType = dis.readUTF()
                val severity = if (dis.readBoolean()) dis.readUTF() else null
                val note = if (dis.readBoolean()) dis.readUTF() else null
                dailySymptoms.add(
                    DailySymptomEntity(
                        id = id,
                        dailyLogId = dailyLogId,
                        symptomType = symptomType,
                        severity = severity,
                        note = note,
                    ),
                )
            }

            val rulesCount = dis.readInt()
            val rules = ArrayList<ReminderRuleEntity>(rulesCount)
            for (i in 0 until rulesCount) {
                val id = dis.readUTF()
                val type = dis.readUTF()
                val enabled = dis.readBoolean()
                val offset = dis.readInt()
                val hour = dis.readInt()
                val minute = dis.readInt()
                val pinned = dis.readBoolean()
                rules.add(
                    ReminderRuleEntity(
                        id = id,
                        type = type,
                        enabled = enabled,
                        offsetDays = offset,
                        hourOfDay = hour,
                        minute = minute,
                        userPinnedTime = pinned,
                    ),
                )
            }

            return BackupPayload(
                profile = profile,
                cycles = cycles,
                symptomLogs = logs,
                dailySymptoms = dailySymptoms,
                reminderRules = rules,
            )
        }
    }
}
