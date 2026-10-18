package com.cyclecare.core.data

import com.cyclecare.core.database.CycleRecordEntity
import com.cyclecare.core.database.DailySymptomEntity
import com.cyclecare.core.database.ReminderRuleEntity
import com.cyclecare.core.database.SymptomLogEntity
import com.cyclecare.core.database.UserProfileEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupPayloadSerializerTest {

    @Test
    fun roundTripSerializationPreservesAllFieldsAndNulls() {
        val profile = UserProfileEntity(
            id = 1,
            ageBand = "TWENTIES",
            averageCycleLength = 29,
            periodLength = 5,
            lastPeriodStartEpochDay = 20500L,
            trackingGoal = "TrackCycle",
            remindersEnabled = true,
            privacyModeEnabled = false,
        )

        val cycle1 = CycleRecordEntity(
            id = "c1",
            startEpochDay = 20400L,
            endEpochDay = 20405L,
            cycleLength = 28,
            source = "User",
            notes = "Normal flow",
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1100L,
            deletedAtEpochMillis = null,
        )

        val cycle2 = CycleRecordEntity(
            id = "c2",
            startEpochDay = 20430L,
            endEpochDay = null, // In progress
            cycleLength = 28,
            source = "Imported",
            notes = null,
            createdAtEpochMillis = 2000L,
            updatedAtEpochMillis = 2100L,
            deletedAtEpochMillis = 2200L, // Tombstone
        )

        val symptom1 = SymptomLogEntity(
            id = "s1",
            dateEpochDay = 20400L,
            mood = "Calm",
            painLevel = "Mild",
            energyLevel = "Medium",
            dischargeType = "None",
            medication = "Ibuprofen",
            notes = "Took 200mg",
            createdAtEpochMillis = 3000L,
            updatedAtEpochMillis = 3100L,
            deletedAtEpochMillis = null,
        )

        val symptom2 = SymptomLogEntity(
            id = "s2",
            dateEpochDay = 20401L,
            mood = null,
            painLevel = null,
            energyLevel = null,
            dischargeType = null,
            medication = null,
            notes = null,
            createdAtEpochMillis = 4000L,
            updatedAtEpochMillis = 4100L,
            deletedAtEpochMillis = 4200L,
        )

        val daily1 = DailySymptomEntity(
            id = "d1",
            dailyLogId = "s1",
            symptomType = "Cramps",
            severity = "Moderate",
            note = "Morning only",
        )

        val rule1 = ReminderRuleEntity(
            id = "r1",
            type = "PeriodApproaching",
            enabled = true,
            offsetDays = -2,
            hourOfDay = 9,
            minute = 0,
            userPinnedTime = true,
        )

        val payload = BackupPayload(
            profile = profile,
            cycles = listOf(cycle1, cycle2),
            symptomLogs = listOf(symptom1, symptom2),
            dailySymptoms = listOf(daily1),
            reminderRules = listOf(rule1),
        )

        val bytes = BackupPayloadSerializer.serialize(payload)
        assertThat(bytes.size).isGreaterThan(0)

        val restored = BackupPayloadSerializer.deserialize(bytes)
        assertThat(restored.profile).isEqualTo(profile)
        assertThat(restored.cycles).containsExactly(cycle1, cycle2).inOrder()
        assertThat(restored.symptomLogs).containsExactly(symptom1, symptom2).inOrder()
        assertThat(restored.dailySymptoms).containsExactly(daily1).inOrder()
        assertThat(restored.reminderRules).containsExactly(rule1).inOrder()
    }

    @Test
    fun roundTripSerializationWithEmptyDataSucceeds() {
        val payload = BackupPayload(
            profile = null,
            cycles = emptyList(),
            symptomLogs = emptyList(),
            dailySymptoms = emptyList(),
            reminderRules = emptyList(),
        )

        val bytes = BackupPayloadSerializer.serialize(payload)
        val restored = BackupPayloadSerializer.deserialize(bytes)

        assertThat(restored.profile).isNull()
        assertThat(restored.cycles).isEmpty()
        assertThat(restored.symptomLogs).isEmpty()
        assertThat(restored.dailySymptoms).isEmpty()
        assertThat(restored.reminderRules).isEmpty()
    }

    @Test
    fun deserializeRejectsUnsupportedVersion() {
        val baos = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(baos).use { dos ->
            dos.writeInt(999) // Invalid future version
        }

        assertThrows(IllegalArgumentException::class.java) {
            BackupPayloadSerializer.deserialize(baos.toByteArray())
        }
    }
}
