package com.cyclecare.core.domain.prediction

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Prediction engine, algorithm version 2.
 *
 * Replaces the v1 heuristic, which was: anchor plus the unweighted mean
 * of the last six start-to-start intervals, a fixed fourteen-day
 * ovulation, a hardcoded fertile window, and a MAD threshold mapped to
 * one of four strings.
 *
 * What changed, and why:
 *
 *  - The centre is a recency-weighted trimmed mean over up to twelve
 *    intervals, so one bad row cannot move the estimate the way an
 *    unweighted mean of six could.
 *  - Implausible intervals are excluded from estimation but retained for
 *    reporting and user correction. v1 averaged a 300-day gap straight
 *    into the prediction.
 *  - A prediction interval is produced with a measurable coverage target
 *    instead of a single date implying a precision the app does not have.
 *  - Irregularity requires corroboration: two outliers among at least four
 *    intervals, not one.
 *  - Below three intervals no interval is emitted at all, rather than a
 *    fabricated plus or minus one day.
 *
 * Pure and deterministic. Takes `today` as input and reads no clock.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md
 */
class CyclePredictionEngineV2 {

    /**
     * Returns null when there is no observed record to anchor a
     * prediction to. There is nothing to estimate from a single date and
     * no user assumption that can substitute for one.
     */
    fun predict(input: CyclePredictionInput): CyclePrediction? {
        val observed: List<ObservedCycleStart> = input.observed
            .filter { it.source != com.cyclecare.core.model.CycleSource.Predicted }
            .sortedBy { it.startDate }

        if (observed.isEmpty()) return null

        val today = input.today
        val anchor = observed.last()

        val intervals: List<IntervalEstimate> = extractIntervals(observed)
        val usable: List<IntervalEstimate> = intervals.filter { it.usable }
        val excluded: List<IntervalEstimate> = intervals.filterNot { it.usable }

        val warnings: MutableList<PredictWarning> = mutableListOf()
        if (excluded.isNotEmpty()) warnings += PredictWarning.IMPOSSIBLE_INTERVAL
        if (excluded.isEmpty() && intervals.isEmpty()) warnings += PredictWarning.NO_HISTORY

        val recent: List<IntervalEstimate> = usable.takeLast(MAX_INTERVALS)

        val centre: Double = if (recent.isNotEmpty()) {
            recencyWeightedTrimmedMean(recent)
        } else {
            input.userReportedAverageCycleLength?.toDouble()
                ?: DEFAULT_ASSUMED_CYCLE_LENGTH.toDouble()
        }
        if (recent.isEmpty()) warnings += PredictWarning.USER_ASSUMPTION_USED

        val sigmaRaw: Double = weightedMad(recent, centre)
        val outliers: Int = recent.count { isOutlier((it.lengthDays - centre).roundToInt(), sigmaRaw) }
        if (outliers > 0) warnings += PredictWarning.OUTLIER_EXCLUDED

        val hasImpossibleInterval: Boolean = excluded.isNotEmpty()
        val irregular: Boolean = isIrregular(outliers, recent.size, hasImpossibleInterval)
        if (irregular) warnings += PredictWarning.IRREGULAR_HISTORY

        val sigma: Double = totalSigma(sigmaRaw, recent.size, irregular)

        val slope: Double = weightedSlope(recent)
        val projectedDrift: Double = slope * TREND_HORIZON_CYCLES
        val trendAdjustment: Double = if (kotlin.math.abs(projectedDrift) < TREND_ACTIVATION_DAYS) {
            0.0
        } else {
            val capped = PROJECTED_DRIFT_CAP
            capped * Math.tanh(projectedDrift / capped)
        }
        if (trendAdjustment != 0.0) warnings += PredictWarning.TREND_DETECTED

        val effectiveCycleLength: Double =
            (centre + trendAdjustment).coerceAtLeast(MIN_EFFECTIVE_CYCLE_LENGTH.toDouble())

        val nextPeriodDate = anchor.startDate.plusDays(effectiveCycleLength.roundToInt().toLong())

        val stale: Boolean = isStale(anchor.startDate, today, effectiveCycleLength)
        if (stale) warnings += PredictWarning.STALE_HISTORY

        val quality: EstimateQuality = qualityFor(recent.size, sigma, irregular, stale)

        // No range below the sufficiency floor. A narrower window would be
        // more confident than the evidence supports.
        val supportsRange: Boolean = recent.size >= INSUFFICIENT_HISTORY_MIN_INTERVALS
        val halfWidth: Int? = if (supportsRange) halfWidthFor(sigma) else null

        val ovulationDate: LocalDate = ovulationFrom(nextPeriodDate)

        val nextPeriodWindow = halfWidth?.let {
            DateWindow(
                start = nextPeriodDate.minusDays(it.toLong()),
                endInclusive = nextPeriodDate.plusDays(it.toLong()),
                halfWidthDays = it,
            )
        }

        val fertileWindow = halfWidth?.let {
            val ovulation = ovulationDate
            // Widen by the luteal uncertainty: ovulation is a population
            // prior, not a measurement, so the window cannot be tighter
            // than the prior's own spread.
            val span = (LUTEAL_PHASE_DEFAULT_DAYS / 2 + 1) + it
            DateWindow(
                start = ovulation.minusDays(span.toLong()),
                endInclusive = ovulation.plusDays(span.toLong()),
                halfWidthDays = it,
            )
        }

        val cycleDay: Int = cycleDayFor(anchor.startDate, today)

        return CyclePrediction(
            algorithmVersion = PREDICTION_ALGORITHM_VERSION,
            generatedForDate = today,
            anchorRecordId = anchor.id,
            anchorDate = anchor.startDate,
            recordIdsUsed = observed.map { it.id },
            intervalsUsed = recent,
            intervalsExcluded = excluded,
            centerCycleLength = centre,
            dispersionSigma = sigma,
            trendAdjustmentDays = trendAdjustment,
            isIrregular = irregular,
            isStale = stale,
            quality = quality,
            nextPeriodDate = nextPeriodDate,
            nextPeriodWindow = nextPeriodWindow,
            ovulationDate = ovulationDate,
            fertileWindow = fertileWindow,
            cycleDay = cycleDay,
            daysUntilNextPeriod = daysUntilFor(today, nextPeriodDate),
            overdueDays = overdueDaysFor(today, nextPeriodDate),
            phase = resolvePhase(
                today = today,
                anchor = anchor,
                cycleDay = cycleDay,
                ovulationDate = ovulationDate,
                nextPeriodDate = nextPeriodDate,
            ),
            warnings = warnings.distinct(),
        )
    }

    // ── internals ───────────────────────────────────────────────────

    /**
     * Cycle length comes from consecutive period STARTS.
     *
     * A period's own length is a different quantity and must never be
     * mixed in.
     */
    private fun extractIntervals(observed: List<ObservedCycleStart>): List<IntervalEstimate> =
        observed.zipWithNext { earlier, later ->
            val length = ChronoUnit.DAYS.between(earlier.startDate, later.startDate).toInt()
            val plausible = length in MIN_PLAUSIBLE_INTERVAL_DAYS..MAX_PLAUSIBLE_INTERVAL_DAYS
            IntervalEstimate(
                fromRecordId = earlier.id,
                toRecordId = later.id,
                startDate = earlier.startDate,
                lengthDays = length,
                usable = plausible,
                exclusionReason = if (plausible) {
                    null
                } else {
                    "There is a $length-day gap between ${earlier.startDate} and ${later.startDate}. " +
                        "Was that a missed period, or a date to correct?"
                },
            )
        }

    /**
     * Recency-weighted trimmed mean.
     *
     * Trimming is skipped below five intervals, where dropping values
     * would discard more signal than it protects. Above it, one value is
     * dropped from each tail, which removes the most damaging outlier
     * while retaining more efficiency than a plain median.
     */
    private fun recencyWeightedTrimmedMean(intervals: List<IntervalEstimate>): Double {
        val weighted: List<Pair<IntervalEstimate, Double>> = intervals.mapIndexed { index, interval ->
            interval to Math.pow(RECENCY_DECAY, (intervals.size - 1 - index).toDouble())
        }
        val trim = if (weighted.size >= TRIM_PER_TAIL_MIN_INTERVALS) 1 else 0
        val sorted = weighted.sortedBy { it.first.lengthDays }
        val kept = sorted.drop(trim).dropLast(trim).ifEmpty { sorted }
        return kept.sumOf { it.first.lengthDays * it.second } / kept.sumOf { it.second }
    }

    /**
     * Weighted mean absolute deviation about the centre.
     *
     * Uses the exact centre rather than the rounded value v1 compared
     * against.
     */
    private fun weightedMad(intervals: List<IntervalEstimate>, centre: Double): Double {
        if (intervals.isEmpty()) return 0.0
        val deviations: List<Double> = intervals.map { kotlin.math.abs(it.lengthDays - centre) }
        val weights = intervals.mapIndexed { index, _ -> Math.pow(RECENCY_DECAY, (intervals.size - 1 - index).toDouble()) }
        return weightedMean(deviations, weights)
    }

    /**
     * Weighted linear slope of interval length against recency rank.
     *
     * Positive means cycles have been getting longer.
     */
    private fun weightedSlope(intervals: List<IntervalEstimate>): Double {
        if (intervals.size < 3) return 0.0
        val n = intervals.size
        val xs: List<Double> = (0 until n).map { it.toDouble() }
        val ys: List<Double> = intervals.map { it.lengthDays.toDouble() }
        val ws: List<Double> = (0 until n).map { Math.pow(RECENCY_DECAY, (n - 1 - it).toDouble()) }

        val sumW = ws.sum()
        val sumX = xs.zip(ws).sumOf { (x, w) -> x * w }
        val sumY = ys.zip(ws).sumOf { (y, w) -> y * w }
        val sumXy = xs.zip(ys).zip(ws).sumOf { (xy, w) -> xy.first * xy.second * w }
        val sumX2 = xs.zip(ws).sumOf { (x, w) -> x * x * w }

        // Weighted least squares. Every term below is weighted, including
        // the leading coefficient: using the raw count `n` here instead
        // of `sumW` makes a constant series appear to slope whenever the
        // weights decay, because the numerator then collapses to
        // y * sumX * (n - sumW) rather than to zero.
        val denominator = sumW * sumX2 - sumX * sumX
        if (kotlin.math.abs(denominator) < 1e-9) return 0.0
        return (sumW * sumXy - sumX * sumY) / denominator
    }

    private fun weightedMean(values: List<Double>, weights: List<Double>): Double {
        val totalWeight: Double = weights.sum()
        if (totalWeight == 0.0) return values.average()
        return values.zip(weights).sumOf { (v, w) -> v * w } / totalWeight
    }

    /**
     * Phase derived from interval position rather than fixed day numbers.
     *
     * Only menstrual days backed by an observed record are marked as not
     * estimated; every other phase is derived and says so.
     */
    private fun resolvePhase(
        today: LocalDate,
        anchor: ObservedCycleStart,
        cycleDay: Int,
        ovulationDate: LocalDate,
        nextPeriodDate: LocalDate,
    ): PhaseEstimate {
        val observedPeriodLength = observedPeriodLengthDays(anchor)

        if (observedPeriodLength != null && cycleDay in 1..observedPeriodLength) {
            return PhaseEstimate(CyclePhase.MENSTRUAL, isEstimated = false)
        }
        return when {
            today == ovulationDate -> PhaseEstimate(CyclePhase.OVULATION, isEstimated = true)
            today.isBefore(ovulationDate) -> PhaseEstimate(CyclePhase.FOLLICULAR, isEstimated = true)
            today.isBefore(nextPeriodDate) -> PhaseEstimate(CyclePhase.LUTEAL, isEstimated = true)
            else -> PhaseEstimate(CyclePhase.LUTEAL, isEstimated = true)
        }
    }

    companion object {
        /** Used only when the user has supplied no assumption and no history exists. */
        const val DEFAULT_ASSUMED_CYCLE_LENGTH = 28

        /** Guards against a nonsensical effective length from a collapsed history. */
        const val MIN_EFFECTIVE_CYCLE_LENGTH = 14
    }
}

/**
 * Period length in days, or null while the period is still in progress.
 */
private fun observedPeriodLengthDays(record: ObservedCycleStart): Int? =
    record.endDate?.let { ChronoUnit.DAYS.between(record.startDate, it).toInt() + 1 }