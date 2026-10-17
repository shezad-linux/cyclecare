package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.CycleSource
import java.time.LocalDate

/**
 * Algorithm version, independent of the app version.
 *
 * Any behaviour change requires a version bump and a documented reason.
 * A change in golden-fixture output without a version bump is a defect,
 * not an update.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md
 */
const val PREDICTION_ALGORITHM_VERSION = "2.0.0"

// ── Named thresholds ──────────────────────────────────────────────
//
// Every constant in the algorithm is named and documented rather than
// inlined, so each has one definition, one place to review, and one
// place to change. Rationale for each lives in the spec.

/** Maximum intervals considered, most recent first. */
const val MAX_INTERVALS: Int = 12

/** Per-interval recency decay. Most recent weighs 1.0, each prior x0.85. */
const val RECENCY_DECAY: Double = 0.85

/** Below this interval count, trimming discards too much signal. */
const val TRIM_PER_TAIL_MIN_INTERVALS: Int = 5

/** Below this many days, an interval is a data-entry error, not a cycle. */
const val MIN_PLAUSIBLE_INTERVAL_DAYS: Int = 15

/** Above this, the history is not understood and an estimate is suppressed. */
const val MAX_PLAUSIBLE_INTERVAL_DAYS: Int = 60

/** Drift below this many days is noise; the estimate is left alone. */
const val TREND_ACTIVATION_DAYS: Double = 1.5

/** Correct for the next cycle only; never extrapolate further. */
const val TREND_HORIZON_CYCLES: Double = 1.0

/** Maximum drift correction, applied through tanh so it saturates smoothly. */
const val PROJECTED_DRIFT_CAP: Double = 3.0

/** Population prior. Luteal length is NOT observable from period starts. */
const val LUTEAL_PHASE_DEFAULT_DAYS: Int = 14

/**
 * Irreducible uncertainty in the luteal prior.
 *
 * Even a user with perfectly regular cycles cannot have a tighter
 * fertile window than this, because ovulation is being guessed rather
 * than measured. CycleCare does not collect basal body temperature, LH
 * surge or cervical mucus, so this cannot currently be reduced.
 */
const val LUTEAL_PHASE_SIGMA_DAYS: Double = 2.0

/** Scales the weighted MAD to estimate sigma for a normal distribution. */
const val MAD_TO_SIGMA_FACTOR: Double = 1.4826

/** Counteracts the downward bias of a dispersion estimate from few points. */
const val SMALL_SAMPLE_INFLATION: Double = 1.5

/** Extra dispersion applied when the history is classified as irregular. */
const val IRREGULAR_SIGMA_MULTIPLIER: Double = 2.0

/** Normal z for a central 80% interval. */
const val Z_80_TWO_SIDED: Double = 1.2816

/** Never promise better than this. */
const val MIN_HALF_WIDTH_DAYS: Int = 1

/** Beyond a week the "range" is not useful; show variability wording instead. */
const val MAX_HALF_WIDTH_DAYS: Int = 7

/** Below this many intervals, emit no window at all. */
const val INSUFFICIENT_HISTORY_MIN_INTERVALS: Int = 3

/** Below one week, variation is ordinary. */
const val OUTLIER_MIN_DEVIATION_DAYS: Int = 7

/** Adapt to the user's own variability. */
const val OUTLIER_SIGMA_FACTOR: Double = 3.0

/** Two outliers required, so one data-entry slip is not mistaken for biology. */
const val IRREGULAR_MIN_OUTLIERS: Int = 2

/** Do not judge regularity on two data points. */
const val IRREGULAR_MIN_INTERVALS: Int = 4

/** More than a cycle and a half without a logged start means stale history. */
const val STALE_AFTER_CYCLE_MULTIPLE: Double = 1.5

/** Days of slack beyond the estimate's own width before calling a period late. */
const val LATE_PERIOD_GRACE_DAYS: Int = 3

/** Lower-case quality boundary. */
const val QUALITY_LOW_SIGMA: Double = 4.0

/** Upper boundary for the HIGH tier. */
const val QUALITY_HIGH_SIGMA: Double = 2.0

/** Interval count needed for MODERATE. */
const val QUALITY_MODERATE_MIN_INTERVALS: Int = 5

/** Interval count needed for HIGH. */
const val QUALITY_HIGH_MIN_INTERVALS: Int = 8

// ── Contract ──────────────────────────────────────────────────────

enum class EstimateQuality {
    INSUFFICIENT_HISTORY,
    LOW,
    MODERATE,
    HIGH,
}

enum class CyclePhase {
    MENSTRUAL,
    FOLLICULAR,
    OVULATION,
    LUTEAL,
    UNKNOWN,
}

enum class PredictWarning {
    NO_HISTORY,
    IMPOSSIBLE_INTERVAL,
    OUTLIER_EXCLUDED,
    IRREGULAR_HISTORY,
    STALE_HISTORY,
    TREND_DETECTED,
    USER_ASSUMPTION_USED,
}

/**
 * A date range with its half-width, so the UI can explain how the
 * range was derived rather than presenting a bare range.
 */
data class DateWindow(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val halfWidthDays: Int,
)

/**
 * One start-to-start interval between two observed records.
 *
 * Cycle length comes from consecutive period STARTS. A period's own
 * length (endDate - startDate) is a different quantity and must never be
 * mixed in.
 */
data class IntervalEstimate(
    val fromRecordId: String,
    val toRecordId: String,
    val startDate: LocalDate,
    val lengthDays: Int,
    val usable: Boolean,
    val exclusionReason: String?,
)

data class PhaseEstimate(
    val phase: CyclePhase,
    /** False only when confirmed by an observed record. */
    val isEstimated: Boolean,
)

/** An observed record as the engine consumes it. Never a prediction. */
data class ObservedCycleStart(
    val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val source: CycleSource,
)

data class CyclePrediction(
    val algorithmVersion: String,
    val generatedForDate: LocalDate,

    // provenance, so any estimate can be explained and audited
    val anchorRecordId: String?,
    val anchorDate: LocalDate?,
    val recordIdsUsed: List<String>,
    val intervalsUsed: List<IntervalEstimate>,
    val intervalsExcluded: List<IntervalEstimate>,

    // statistics
    val centerCycleLength: Double,
    val dispersionSigma: Double,
    val trendAdjustmentDays: Double,
    val isIrregular: Boolean,
    val isStale: Boolean,
    val quality: EstimateQuality,

    // output
    val nextPeriodDate: LocalDate,
    /** Null iff quality == INSUFFICIENT_HISTORY. */
    val nextPeriodWindow: DateWindow?,
    val ovulationDate: LocalDate,
    /** Null iff quality == INSUFFICIENT_HISTORY. */
    val fertileWindow: DateWindow?,
    val cycleDay: Int,
    val daysUntilNextPeriod: Int,
    val overdueDays: Int,
    val phase: PhaseEstimate,
    val warnings: List<PredictWarning>,
) {
    /**
     * True when the engine had too little history to support a range.
     * The UI must show an honest empty state rather than a fabricated
     * value, and must not send an estimate-derived reminder.
     */
    val supportsRange: Boolean
        get() = nextPeriodWindow != null
}

/**
 * Input to the engine.
 *
 * `today` is required rather than defaulted. The v1 engine defaulted to
 * LocalDate.now() and the production call site omitted it, which made
 * output non-deterministic and rollover behaviour untestable.
 */
data class CyclePredictionInput(
    val observed: List<ObservedCycleStart>,
    val userReportedAverageCycleLength: Int?,
    val today: LocalDate,
)