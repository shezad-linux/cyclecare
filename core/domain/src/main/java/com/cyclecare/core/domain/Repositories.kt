package com.cyclecare.core.domain

import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ScheduledReminder
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun saveProfile(profile: UserProfile)
    suspend fun updateRemindersEnabled(enabled: Boolean)
}

interface CycleRepository {
    fun observeCycles(): Flow<List<CycleRecord>>
    suspend fun upsertCycle(record: CycleRecord)
    suspend fun deleteCycle(id: String)
}

interface SymptomRepository {
    fun observeSymptoms(): Flow<List<SymptomLog>>
    fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?>
    suspend fun upsertSymptom(log: SymptomLog)
}

interface PredictionRepository {
    fun observePrediction(): Flow<PredictionSnapshot?>
}

interface InsightRepository {
    fun observeInsights(): Flow<InsightSnapshot>
}

interface ReminderRepository {
    fun observeRules(): Flow<List<ReminderRule>>
    suspend fun upsertRule(rule: ReminderRule)
}

interface ReminderChannelSettingsRepository {
    fun observeChannelSettings(): Flow<List<ReminderChannelSettings>>
    suspend fun upsertChannelSettings(settings: ReminderChannelSettings)
}

interface ReminderEventsRepository {
    fun observeRecent(limit: Int): Flow<List<ReminderEvent>>
    fun observeUnreadCount(): Flow<Int>
    fun observeNextUnread(): Flow<ReminderEvent?>
    suspend fun recordEvent(event: ReminderEvent)
    suspend fun markRead(id: String)
    suspend fun markAllRead()
    suspend fun dismiss(id: String)
}

interface PrivacyRepository {
    fun observeConsent(): Flow<ConsentState>
    fun hasPinConfigured(): Flow<Boolean>
    fun observeLastUnlockEpochMillis(): Flow<Long?>
    fun isBiometricEnabled(): Flow<Boolean>
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setNotificationPreviewHidden(hidden: Boolean)
    suspend fun acknowledgeLocalData()
    suspend fun setPin(pin: String)
    suspend fun clearPin()
    suspend fun resetAppLock()
    suspend fun validatePin(pin: String): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun markUnlocked(epochMillis: Long = System.currentTimeMillis())
}

interface ExportRepository {
    suspend fun createReport(format: ExportFormat): ExportArtifact
}

interface AppResetRepository {
    suspend fun resetAllLocalData()
}

interface ReminderScheduler {
    suspend fun refreshSchedules(now: ZonedDateTime = ZonedDateTime.now()): List<ScheduledReminder>
}

interface SyncAdapter {
    suspend fun pushPendingChanges()
    suspend fun pullRemoteChanges()
    suspend fun mergePreview(): String
}
