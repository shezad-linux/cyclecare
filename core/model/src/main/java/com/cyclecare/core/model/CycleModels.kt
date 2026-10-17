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
    val endDate: LocalDate,
    val cycleLength: Int,
    val source: CycleSource,
    val notes: String? = null,
)

data class SymptomLog(
    val id: String,
    val date: LocalDate,
    val mood: Mood,
    val painLevel: PainLevel,
    val energyLevel: EnergyLevel,
    val dischargeType: DischargeType,
    val medication: String? = null,
    val notes: String? = null,
)

data class PredictionSnapshot(
    val generatedAt: LocalDate,
    val cycleDay: Int,
    val nextPeriodDate: LocalDate,
    val ovulationDate: LocalDate,
    val fertileWindow: DateRange,
    val confidenceLabel: String,
    val daysUntilNextPeriod: Int,
)

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
