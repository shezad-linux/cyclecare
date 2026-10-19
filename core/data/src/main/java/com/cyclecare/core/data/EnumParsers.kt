package com.cyclecare.core.data

import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal

internal fun String.toAgeBand(logger: AppLogger): AgeBand = parseEnumSafely(
    raw = this,
    fallback = AgeBand.Twenties,
    tag = "ProfileRepository",
    field = "ageBand",
    logger = logger,
)

internal fun String.toTrackingGoal(logger: AppLogger): TrackingGoal = parseEnumSafely(
    raw = this,
    fallback = TrackingGoal.PeriodPrep,
    tag = "ProfileRepository",
    field = "trackingGoal",
    logger = logger,
)

internal fun String.toCycleSource(logger: AppLogger): CycleSource = parseEnumSafely(
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
internal fun String?.toMood(logger: AppLogger): Mood? = parseEnumOrNull(
    raw = this,
    fallback = Mood.Stable,
    tag = "SymptomRepository",
    field = "mood",
    logger = logger,
)

/** Null means not tracked, which is distinct from a recorded "None". */
internal fun String?.toPainLevel(logger: AppLogger): PainLevel? = parseEnumOrNull(
    raw = this,
    fallback = PainLevel.None,
    tag = "SymptomRepository",
    field = "painLevel",
    logger = logger,
)

internal fun String?.toEnergyLevel(logger: AppLogger): EnergyLevel? = parseEnumOrNull(
    raw = this,
    fallback = EnergyLevel.Steady,
    tag = "SymptomRepository",
    field = "energyLevel",
    logger = logger,
)

/** Null means not tracked, which is distinct from a recorded "None". */
internal fun String?.toDischargeType(logger: AppLogger): DischargeType? = parseEnumOrNull(
    raw = this,
    fallback = DischargeType.None,
    tag = "SymptomRepository",
    field = "dischargeType",
    logger = logger,
)

internal fun String.toReminderType(logger: AppLogger): ReminderType = parseEnumSafely(
    raw = this,
    fallback = ReminderType.DailyLog,
    tag = "ReminderRepository",
    field = "type",
    logger = logger,
)

internal fun String.toReminderChannelKey(logger: AppLogger): ReminderChannelKey = parseEnumSafely(
    raw = this,
    fallback = ReminderChannelKey.DailyCheckIn,
    tag = "ReminderRepository",
    field = "channelKey",
    logger = logger,
)

internal fun String.toReminderSoundPreset(logger: AppLogger): ReminderSoundPreset = parseEnumSafely(
    raw = this,
    fallback = ReminderSoundPreset.SoftChime,
    tag = "ReminderRepository",
    field = "soundPreset",
    logger = logger,
)

internal fun String.toReminderDeepLinkTarget(logger: AppLogger): ReminderDeepLinkTarget = parseEnumSafely(
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
internal inline fun <reified T : Enum<T>> parseEnumOrNull(
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

internal inline fun <reified T : Enum<T>> parseEnumSafely(
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

