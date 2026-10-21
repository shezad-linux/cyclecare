package com.cyclecare.core.model

import java.time.LocalDate
import java.time.ZonedDateTime

enum class AgeBand {
    Teen,
    Twenties,
    Thirties,
    FortiesPlus,
}

enum class TrackingGoal {
    PeriodPrep,
    SymptomAwareness,
    ConceptionAwareness,
}

enum class Mood {
    Stable,
    Happy,
    Low,
    Irritable,
    Anxious,
}

enum class PainLevel {
    None,
    Light,
    Moderate,
    Strong,
}

enum class EnergyLevel {
    Low,
    Steady,
    High,
}

enum class DischargeType {
    None,
    Dry,
    Creamy,
    Sticky,
    EggWhite,
}

enum class ReminderType {
    PeriodPrep,
    FertileWindowStart,
    OvulationEstimate,
    DailyLog,
    LatePeriod,
    Hydration,
    Pill,
}

enum class ReminderChannelKey {
    CycleAlerts,
    FertilityAlerts,
    DailyCheckIn,
}

enum class ReminderSoundPreset {
    GentleBell,
    SoftChime,
    WaterDrop,
}

enum class ReminderDeepLinkTarget {
    Home,
    Calendar,
    Log,
    ReminderCenter,
}

enum class ExportFormat {
    Markdown,
    Pdf,
    EncryptedBackup,
}

enum class CycleSource {
    User,
    Imported,
    Predicted,
}

data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
)

data class UserProfile(
    val ageBand: AgeBand,
    val averageCycleLength: Int,
    val periodLength: Int,
    val lastPeriodStart: LocalDate,
    val trackingGoal: TrackingGoal,
    val remindersEnabled: Boolean,
    val privacyModeEnabled: Boolean,
)

data class CycleRecord(
    val id: String,
    val startDate: LocalDate,
    /** Null while the period is still in progress. */
    val endDate: LocalDate?,
    /**
     * Deprecated. Must not be read as a cycle length: cycle length is
     * derived from consecutive start dates, and this field has never
     * been cross-validated against them.
     */
    val cycleLength: Int,
    val source: CycleSource,
    val notes: String? = null,
) {
    val isInProgress: Boolean get() = endDate == null

    /** Null when the period has not ended yet. */
    val periodLengthDays: Int? get() = endDate?.let { java.time.temporal.ChronoUnit.DAYS.between(startDate, it).toInt() + 1 }
}

/**
 * A daily log.
 *
 * Every tracked field is nullable and null means "not tracked", which is
 * a first-class answer the user can give. This is distinct from a
 * recorded "None" such as PainLevel.None, which means explicitly none.
 *
 * The distinction matters: treating an untracked day as "no pain" would
 * invent a symptom the user never reported.
 */
data class SymptomLog(
    val id: String,
    val date: LocalDate,
    val mood: Mood?,
    val painLevel: PainLevel?,
    val energyLevel: EnergyLevel?,
    val dischargeType: DischargeType?,
    val medication: String? = null,
    val notes: String? = null,
) {
    /** True when the user recorded nothing at all for this day. */
    val isEmpty: Boolean
        get() = mood == null && painLevel == null && energyLevel == null &&
            dischargeType == null && medication.isNullOrBlank() && notes.isNullOrBlank()
}

// A prediction is no longer modelled here.
//
// This used to be `PredictionSnapshot`, a second shape that mirrored the
// prediction engine's own output field by field, together with
// `PredictionQuality`, `PredictionPhase` and `PredictionWarning`
// mirrors of the engine's enums.
//
// Spec `11` §7 requires one shape: "`PredictionSnapshot` is replaced by
// `CyclePrediction`. `confidenceLabel: String` is removed; `quality:
// EstimateQuality` replaces it. Migrate consumers in one change, never
// both shapes at once."
//
// `CyclePrediction` lives in `core:domain`, next to the engine that
// produces it. The modules that consume a prediction already depend on
// `core:domain`, so nothing needed a copy here.

data class ReminderRule(
    val id: String,
    val type: ReminderType,
    val enabled: Boolean,
    val daysBefore: Int,
    val hourOfDay: Int,
    val minute: Int,
    /**
     * True when the user explicitly chose this time.
     *
     * Spec `13` §8: a pinned time is the user's own agency — the learned
     * send-time model must never override it, and a pinned candidate is
     * scored against a lower threshold because the intent is explicit.
     * Seeded rules are never pinned; only a settings editor that lets the
     * user move a time sets this.
     */
    val userPinnedTime: Boolean = false,
)

data class ReminderChannelSettings(
    val channelKey: ReminderChannelKey,
    val enabled: Boolean,
    val soundEnabled: Boolean,
    val soundPreset: ReminderSoundPreset,
    val vibrationEnabled: Boolean,
)

/**
 * A reminder type the app paused on its own, and the lifetime counts that
 * explain it.
 *
 * Spec `13` §9: a type whose dismiss rate exceeds the threshold across a
 * lifetime minimum of impressions is disabled by the app, and the record
 * has to be honest about why — the counts are the explanation and they
 * are what the re-enable surface shows.
 */
data class ReminderAutoDisabled(
    val type: ReminderType,
    val dismissedCount: Int,
    val openedCount: Int,
    val disabledAtEpochMillis: Long,
)

/**
 * A reminder candidate that was deferred instead of posted, e.g. into
 * quiet hours or an arbitration fold. Enough to summarise what is
 * waiting; the summary text carries a count only, never cycle detail.
 */
data class DeferredReminder(
    val ruleId: String,
    val type: ReminderType,
    val scheduledAtEpochMillis: Long,
    val reason: String,
)

data class ReminderEvent(
    val id: String,
    val ruleId: String,
    val type: ReminderType,
    val channelKey: ReminderChannelKey,
    val scheduledAt: ZonedDateTime,
    val deliveredAt: ZonedDateTime,
    val title: String,
    val body: String,
    val privateBody: String,
    val deepLinkTarget: ReminderDeepLinkTarget,
    val targetEpochDay: Long? = null,
    val readAtEpochMillis: Long? = null,
    val dismissedAtEpochMillis: Long? = null,
)

enum class InsightPhase {
    Period,
    FertileWindow,
}

data class InsightPhaseHighlight(
    val phase: InsightPhase,
    val title: String,
    val description: String,
)

data class InsightSnapshot(
    val averageCycleLength: Int,
    val averagePeriodLength: Int,
    val irregularCycles: Boolean,
    val recentCycleLengths: List<Int>,
    val recentPeriodLengths: List<Int>,
    val minCycleLength: Int,
    val maxCycleLength: Int,
    val cycleWindowLabel: String,
    val hasEnoughCycleData: Boolean,
    val moodCounts: Map<Mood, Int>,
    val energyCounts: Map<EnergyLevel, Int>,
    val painCounts: Map<PainLevel, Int>,
    val dischargeCounts: Map<DischargeType, Int>,
    val phaseHighlights: List<InsightPhaseHighlight>,
    val hasEnoughSymptomData: Boolean,
    val narrativeSummary: String,
)

data class ConsentState(
    val hideNotificationPreview: Boolean,
    val localDataAcknowledged: Boolean,
    val appLockEnabled: Boolean,
    val biometricEnabled: Boolean = false,
)

data class ExportArtifact(
    val format: ExportFormat,
    val displayName: String,
    val absolutePath: String,
    val mimeType: String,
    val generatedAt: LocalDate,
)

data class ScheduledReminder(
    val uniqueWorkName: String,
    val ruleId: String,
    val type: ReminderType,
    val channelKey: ReminderChannelKey,
    val scheduledAt: ZonedDateTime,
    val title: String,
    val body: String,
    val privateBody: String,
    val deepLinkTarget: ReminderDeepLinkTarget,
    val targetEpochDay: Long? = null,
    /**
     * The rule's lead, in absolute days, carried so the delivery-time
     * relevance score can reward a well-sized lead. Signed offsets are the
     * rule's own concern; the scorer asks "how far ahead is this", so only
     * the magnitude travels.
     */
    val leadDays: Int = 0,
    /**
     * Half the width of the prediction window the reminder is anchored to,
     * so delivery-time scoring can respect the estimate's uncertainty.
     * Zero when the reminder does not rest on an estimate.
     */
    val windowHalfWidthDays: Int = 0,
    /** Carried from the rule so a pinned candidate gets its own threshold. */
    val userPinnedTime: Boolean = false,
)
