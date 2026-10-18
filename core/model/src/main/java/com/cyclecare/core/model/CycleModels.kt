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

data class PredictionSnapshot(
    val generatedAt: LocalDate,
    val cycleDay: Int,
    val nextPeriodDate: LocalDate,
    val ovulationDate: LocalDate,
    val fertileWindow: DateRange,
    val confidenceLabel: String,
    val daysUntilNextPeriod: Int,

    // ── Added by the v2 engine ─────────────────────────────────────
    // The v1 snapshot could not express a range, a quality tier or a
    // phase, which forced the UI to present a single date as if it were
    // certain. These fields carry that capability through to the UI
    // without waiting for the full screen rewrite.

    /** Null when history is too thin to support any range at all. */
    val nextPeriodWindow: DateRange? = null,
    /**
     * Explicit half-width in days. Carried rather than derived from the
     * window span, so the UI never has to infer it and cannot drift if
     * the window representation changes.
     */
    val nextPeriodHalfWidthDays: Int? = null,
    val quality: PredictionQuality = PredictionQuality.INSUFFICIENT_HISTORY,
    val phase: PredictionPhase = PredictionPhase.UNKNOWN,
    val warnings: Set<PredictionWarning> = emptySet(),
    val overdueDays: Int = 0,
    val algorithmVersion: String = "",
) {
    /**
     * False when the engine declined to emit a range. Callers must show
     * an honest empty state and must not derive reminders from the point
     * estimate alone.
     */
    val supportsRange: Boolean
        get() = nextPeriodWindow != null
}

/**
 * Mirror of the domain quality tier, kept in the model module so the UI
 * does not depend on the prediction package.
 */
enum class PredictionQuality { INSUFFICIENT_HISTORY, LOW, MODERATE, HIGH }

/** Mirror of the domain phase, for the same reason. */
enum class PredictionPhase { MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL, UNKNOWN }

/** Mirror of the domain warnings, for the same reason. */
enum class PredictionWarning {
    NO_HISTORY,
    IMPOSSIBLE_INTERVAL,
    OUTLIER_EXCLUDED,
    IRREGULAR_HISTORY,
    STALE_HISTORY,
    TREND_DETECTED,
    USER_ASSUMPTION_USED,
}

data class ReminderRule(
    val id: String,
    val type: ReminderType,
    val enabled: Boolean,
    val daysBefore: Int,
    val hourOfDay: Int,
    val minute: Int,
)

data class ReminderChannelSettings(
    val channelKey: ReminderChannelKey,
    val enabled: Boolean,
    val soundEnabled: Boolean,
    val soundPreset: ReminderSoundPreset,
    val vibrationEnabled: Boolean,
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
)
