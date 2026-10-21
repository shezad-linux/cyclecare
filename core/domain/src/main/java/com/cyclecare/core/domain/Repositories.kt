package com.cyclecare.core.domain

import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.DeferredReminder
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.ReminderAutoDisabled
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.ScheduledReminder
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.UserProfile
import java.io.InputStream
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

/**
 * The prediction the app is currently showing.
 *
 * Spec `11` §7: "`PredictionSnapshot` is replaced by `CyclePrediction`."
 * The repository therefore yields the engine's own output rather than a
 * second shape that mirrors it, and there is no longer an adapter
 * between the two.
 */
interface PredictionRepository {
    fun observePrediction(): Flow<CyclePrediction?>
}

interface InsightRepository {
    fun observeInsights(): Flow<InsightSnapshot>
}

interface ReminderRepository {
    fun observeRules(): Flow<List<ReminderRule>>
    suspend fun upsertRule(rule: ReminderRule)

    /**
     * Enables or disables every rule of one type at once.
     *
     * Spec `13` §9 silences a reminder type rather than an individual
     * rule: auto-disable is a reaction to how the type has been received,
     * and half-measures would let the same interruption continue through
     * another rule with a different id.
     */
    suspend fun setRuleTypeEnabled(type: ReminderType, enabled: Boolean)
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

    /**
     * The most recent delivery time at or after [sinceEpochMillis], or null
     * when nothing was delivered in that window.
     *
     * Spec 15 §11.3 condition 5 needs to know whether a reminder was
     * delivered in the last 30 minutes. This is a one-off query rather than
     * an observation because the gate evaluates it at the moment an
     * interstitial would be shown.
     */
    suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long?

    /**
     * Inserts the event only when no event with this id exists, and
     * returns whether it was newly inserted.
     *
     * Spec `13` §12: a retried delivery must never post again. The insert
     * is the lock — the caller inserts first and posts only when this
     * returns true, so a crash between insert and post loses one
     * notification instead of duplicating one.
     */
    suspend fun insertIfAbsent(event: ReminderEvent): Boolean

    /**
     * Deliveries inside the half-open window [fromEpochMillis, toEpochMillis).
     *
     * Spec `13` §7 daily and weekly budgets and §10 arbitration all need
     * "how many sends happened in the recent past" at delivery time.
     */
    suspend fun countDeliveredBetween(fromEpochMillis: Long, toEpochMillis: Long): Int

    /** Lifetime impressions of one reminder type. Spec `13` §9. */
    suspend fun countDeliveredForType(type: ReminderType): Int

    /** Lifetime dismissals of one reminder type. Spec `13` §9. */
    suspend fun countDismissedForType(type: ReminderType): Int

    /** Lifetime opens of one reminder type. Spec `13` §9. */
    suspend fun countOpenedForType(type: ReminderType): Int
}

/**
 * The state the notification intelligence needs but that no other
 * repository owns: auto-disabled reminder types, and the candidates that
 * were deferred past quiet hours or an arbitration window.
 */
interface ReminderIntelligenceRepository {
    /** The types currently auto-disabled, with the counts that explain them. */
    fun observeAutoDisabled(): Flow<List<ReminderAutoDisabled>>

    /** Whether [type] is currently auto-disabled. */
    suspend fun isAutoDisabled(type: ReminderType): Boolean

    /**
     * Records that [type] was auto-disabled at [disabledAtEpochMillis]
     * because of these lifetime counts.
     */
    suspend fun disable(
        type: ReminderType,
        dismissedCount: Int,
        openedCount: Int,
        disabledAtEpochMillis: Long,
    )

    /** Undoes an auto-disable after the user has asked for the type back. */
    suspend fun reEnable(type: ReminderType)

    /**
     * Adds a candidate to the deferred set. Returns the total number of
     * distinct candidates waiting, so the caller can log the deferral.
     */
    suspend fun defer(
        ruleId: String,
        type: ReminderType,
        scheduledAtEpochMillis: Long,
        reason: String,
    ): Int

    /**
     * Removes everything from the deferred set and returns it, oldest
     * first. The summaries are posted from what this returns.
     */
    suspend fun takeDeferred(): List<DeferredReminder>

    /** How many distinct candidates are currently deferred, or null when none. */
    suspend fun deferredCount(): Int?
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

sealed class ImportResult {
    data class Success(
        val cyclesImported: Int,
        val symptomsImported: Int,
        val profileImported: Boolean,
    ) : ImportResult()

    sealed class Failure : ImportResult() {
        data object InvalidPassphrase : Failure()
        data object CorruptedFile : Failure()
        data class UnsupportedVersion(val version: Int) : Failure()
        data class Error(val message: String) : Failure()
    }
}

interface ExportRepository {
    suspend fun createReport(format: ExportFormat): ExportArtifact
    suspend fun createEncryptedBackup(passphrase: CharArray): ExportArtifact
    suspend fun importEncryptedBackup(inputStream: InputStream, passphrase: CharArray): ImportResult
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
