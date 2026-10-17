package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionPhase
import com.cyclecare.core.model.PredictionQuality
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.PredictionWarning

/**
 * Adapter from the v2 engine contract to the UI-facing snapshot.
 *
 * The duplication between the domain and model enums is deliberate and
 * temporary: `core:model` must not depend on the prediction package, and
 * the UI is being rewritten in Phase 6. Once the UI consumes
 * [CyclePrediction] directly, this file and the mirror enums can go.
 *
 * One thing is intentionally NOT adapted: an absent range stays absent.
 * The old snapshot had no way to say "I do not know", so the UI rendered
 * a bare date as if it were exact. [PredictionSnapshot.nextPeriodWindow]
 * is null in that case and callers are expected to honour it.
 */
fun CyclePrediction.toSnapshot(): PredictionSnapshot = PredictionSnapshot(
    generatedAt = generatedForDate,
    cycleDay = cycleDay,
    nextPeriodDate = nextPeriodDate,
    ovulationDate = ovulationDate,
    fertileWindow = (fertileWindow ?: fertileWindowFallback())
        .let { window -> DateRange(start = window.start, endInclusive = window.endInclusive) },
    confidenceLabel = confidenceLabel(),
    daysUntilNextPeriod = daysUntilNextPeriod,
    nextPeriodWindow = nextPeriodWindow?.let { DateRange(it.start, it.endInclusive) },
    quality = when (quality) {
        EstimateQuality.INSUFFICIENT_HISTORY -> PredictionQuality.INSUFFICIENT_HISTORY
        EstimateQuality.LOW -> PredictionQuality.LOW
        EstimateQuality.MODERATE -> PredictionQuality.MODERATE
        EstimateQuality.HIGH -> PredictionQuality.HIGH
    },
    phase = when (phase.phase) {
        CyclePhase.MENSTRUAL -> PredictionPhase.MENSTRUAL
        CyclePhase.FOLLICULAR -> PredictionPhase.FOLLICULAR
        CyclePhase.OVULATION -> PredictionPhase.OVULATION
        CyclePhase.LUTEAL -> PredictionPhase.LUTEAL
        CyclePhase.UNKNOWN -> PredictionPhase.UNKNOWN
    },
    warnings = warnings.map { warning ->
        when (warning) {
            PredictWarning.NO_HISTORY -> PredictionWarning.NO_HISTORY
            PredictWarning.IMPOSSIBLE_INTERVAL -> PredictionWarning.IMPOSSIBLE_INTERVAL
            PredictWarning.OUTLIER_EXCLUDED -> PredictionWarning.OUTLIER_EXCLUDED
            PredictWarning.IRREGULAR_HISTORY -> PredictionWarning.IRREGULAR_HISTORY
            PredictWarning.STALE_HISTORY -> PredictionWarning.STALE_HISTORY
            PredictWarning.TREND_DETECTED -> PredictionWarning.TREND_DETECTED
            PredictWarning.USER_ASSUMPTION_USED -> PredictionWarning.USER_ASSUMPTION_USED
        }
    }.toSet(),
    overdueDays = overdueDays,
    algorithmVersion = algorithmVersion,
)

/**
 * Wording the user actually sees.
 *
 * Deliberately never says "accurate" or "certain". The v1 engine claimed
 * "High confidence estimate" purely from mean absolute deviation, with no
 * regard for sample size, so a single-interval user could be told their
 * prediction was high confidence.
 */
private fun CyclePrediction.confidenceLabel(): String = when {
    quality == EstimateQuality.INSUFFICIENT_HISTORY -> "Baseline estimate"
    isStale -> "Estimate may be out of date"
    quality == EstimateQuality.HIGH -> "Narrow range"
    quality == EstimateQuality.MODERATE -> "Moderate range"
    else -> "Wide range"
}

/**
 * Fertility window used only when the engine declined to emit one.
 *
 * Keeps the old `DateRange`-only shape usable so the existing screens do
 * not crash during the transition. Callers should prefer
 * [CyclePrediction.fertileWindow] and treat this as a presentation
 * fallback, not an estimate.
 */
private fun CyclePrediction.fertileWindowFallback(): DateWindow = DateWindow(
    start = ovulationDate.minusDays(4),
    endInclusive = ovulationDate.plusDays(1),
    halfWidthDays = 0,
)