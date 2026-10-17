package com.cyclecare.core.domain.prediction

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The numeric core of the prediction engine, as pure functions.
 *
 * These are the parts of the algorithm that are fully determined by
 * named constants and contain no input handling, weighting or
 * classification decisions, so they can be specified and tested
 * independently. Phase 4 supplies interval extraction, recency
 * weighting, drift regression, outlier classification at the record
 * level, and phase resolution on top of these.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md sections 4.4
 * to 4.8.
 */

/**
 * Converts a mean absolute deviation into a standard-deviation estimate.
 *
 * 1.4826 is the consistency constant for the normal distribution: a
 * raw MAD computed against the true mean underestimates sigma by this
 * factor. The v1 engine used the raw MAD directly against a rounded
 * mean, which is neither.
 */
fun sigmaFromMad(mad: Double): Double = mad * MAD_TO_SIGMA_FACTOR

/**
 * Inflates a dispersion estimate to compensate for small samples.
 *
 * A sigma estimated from three intervals is badly biased low. The
 * multiplier decays as evidence accumulates: 1.50 at n=3, 1.25 at n=6,
 * 1.125 at n=12.
 */
fun inflateForSampleSize(sigma: Double, intervalCount: Int): Double =
    sigma * (1.0 + SMALL_SAMPLE_INFLATION / maxOf(intervalCount, 1))

/** Dispersion actually used for interval width, including irregularity. */
fun totalSigma(rawSigma: Double, intervalCount: Int, isIrregular: Boolean): Double {
    val adjusted = inflateForSampleSize(rawSigma, intervalCount)
    return if (isIrregular) adjusted * IRREGULAR_SIGMA_MULTIPLIER else adjusted
}

/**
 * Half-width for a nominal central 80% interval, clamped to a usable
 * range.
 *
 * The floor exists so the app never promises better than plus or minus
 * one day. The ceiling exists because beyond a week a "range" stops
 * being useful information and variability wording is more honest.
 */
fun halfWidthFor(sigma: Double): Int {
    if (sigma.isNaN() || sigma <= 0.0) return MIN_HALF_WIDTH_DAYS
    return Math.round((Z_80_TWO_SIDED * sigma).toFloat())
        .coerceIn(MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS)
}

/**
 * True when a single interval is far enough from the centre to count as
 * an outlier.
 *
 * Both conditions must hold: an absolute floor, because variation below
 * one week is ordinary, and a sigma-relative threshold, so a user with
 * large real variability is not labelled irregular for it.
 */
fun isOutlier(deviationDays: Int, sigma: Double): Boolean =
    abs(deviationDays) >= OUTLIER_MIN_DEVIATION_DAYS &&
        abs(deviationDays) > OUTLIER_SIGMA_FACTOR * sigma

/**
 * Irregularity requires corroboration.
 *
 * The v1 rule was "any interval deviating by six days or more", so a
 * single bad row relabelled a user's entire history. Two outliers
 * among at least four usable intervals are required, and an impossible
 * interval forces the flag because such a gap genuinely means the
 * history is not understood.
 */
fun isIrregular(
    outlierCount: Int,
    usableIntervalCount: Int,
    hasImpossibleInterval: Boolean,
): Boolean {
    if (hasImpossibleInterval) return true
    return usableIntervalCount >= IRREGULAR_MIN_INTERVALS &&
        outlierCount >= IRREGULAR_MIN_OUTLIERS
}

/**
 * True when history has gone stale: no logged start for more than a
 * cycle and a half.
 *
 * Stale is a quality signal, not an error, and it downgrades quality
 * rather than suppressing the estimate.
 */
fun isStale(anchorDate: LocalDate, today: LocalDate, effectiveCycleLength: Double): Boolean =
    ChronoUnit.DAYS.between(anchorDate, today) > STALE_AFTER_CYCLE_MULTIPLE * effectiveCycleLength

/**
 * Quality tier, derived from four independent signals.
 *
 * Derived from depth, dispersion, irregularity and staleness rather
 * than from raw MAD thresholds, so a low-variance but two-cycle history
 * is not reported as reliable.
 */
fun qualityFor(
    intervalCount: Int,
    sigma: Double,
    isIrregular: Boolean,
    isStale: Boolean,
): EstimateQuality = when {
    intervalCount < INSUFFICIENT_HISTORY_MIN_INTERVALS -> EstimateQuality.INSUFFICIENT_HISTORY
    intervalCount < QUALITY_MODERATE_MIN_INTERVALS ||
        sigma > QUALITY_LOW_SIGMA ||
        isIrregular ||
        isStale -> EstimateQuality.LOW
    intervalCount >= QUALITY_HIGH_MIN_INTERVALS && sigma <= QUALITY_HIGH_SIGMA -> EstimateQuality.HIGH
    else -> EstimateQuality.MODERATE
}

/**
 * Ovulation is derived from a population prior, not a measurement.
 *
 * Kept as a function so the assumption is visible at every call site.
 * Luteal length cannot be observed from period starts alone; CycleCare
 * does not collect basal body temperature, LH surge or cervical mucus.
 */
fun ovulationFrom(estimatedNextPeriodStart: LocalDate): LocalDate =
    estimatedNextPeriodStart.minusDays(LUTEAL_PHASE_DEFAULT_DAYS.toLong())

/**
 * Cycle day, 1 on the anchor date.
 *
 * Continues past the expected cycle length rather than wrapping, so an
 * overdue cycle stays readable.
 */
fun cycleDayFor(anchorDate: LocalDate, today: LocalDate): Int =
    maxOf(1L, ChronoUnit.DAYS.between(anchorDate, today) + 1L).toInt()

/**
 * Signed days until the predicted start.
 *
 * Kept negative when overdue for continuity: existing UI and tests
 * depend on the signed value. [overdueDays] gives the UI something
 * bounded and humane to display instead of an ever-growing negative
 * number.
 */
fun daysUntilFor(today: LocalDate, estimatedNextPeriodStart: LocalDate): Int =
    ChronoUnit.DAYS.between(today, estimatedNextPeriodStart).toInt()

fun overdueDaysFor(today: LocalDate, estimatedNextPeriodStart: LocalDate): Int =
    maxOf(0, -daysUntilFor(today, estimatedNextPeriodStart))