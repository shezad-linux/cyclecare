# 11 — Prediction Engine Specification (Version 2)

**Status:** Implementation contract. Supersedes the method described in `05-cycle-domain-and-health-safety.md` §2 and `docs/prediction-logic.md`.
**Algorithm version:** `2.0.0`
**Depends on:** `05` (health-safety guardrails, which remain binding on all output here).

## 1. Purpose and scope

This document specifies exactly how CycleCare estimates a user's cycle. It replaces the current 70-line heuristic with a robust estimator that produces a **calibrated range instead of a false-precision date**, and it makes every threshold in the system a named, documented, testable constant.

It covers: interval extraction, robust length estimation, drift adjustment, uncertainty, irregularity, phase, and quality. It does **not** cover insight narrative (see `05` §6) or reminder timing (see `13`).

## 2. What changes and why

| Current behaviour | Problem | New behaviour |
|---|---|---|
| `anchor + round(mean of last 6 intervals)` | Single outlier destroys the estimate | Recency-weighted trimmed mean over ≤12 intervals, outliers excluded with corroboration |
| Outliers deliberately retained (`docs/prediction-logic.md:13`) | A 300-day data-entry gap shifts every prediction ~10 months | Implausible intervals excluded from estimation, surfaced to the user for correction |
| Fixed `-14` day ovulation | Magic number, undocumented | Named constant `LUTEAL_PHASE_DEFAULT_DAYS`, with its uncertainty folded into the reported range |
| Fertile window `[ovulation−4, ovulation+1]` presented as fact | Implies knowledge the app does not have | Same window, widened uncertainty, explicit "estimate" framing and contraception guardrail |
| MAD → one of 4 hardcoded `String` labels | Untyped, uncalibrated, meaningless threshold | Typed `EstimateQuality` derived from history depth, dispersion, irregularity and staleness |
| No range at all | UI says "Oct 24–27" but nothing computes a range | Empirical prediction interval with a coverage target |
| `today` defaults to `LocalDate.now()`; production path never injects it | Non-deterministic in production, untestable edge cases | Clock injected explicitly; no default |
| `InsightEngine` reads stored `cycleLength`; `CyclePredictionEngine` reads start gaps | Two engines can disagree for the same user | Single shared interval extractor; `cycleLength` is never an input |
| `daysUntilNextPeriod` drifts negative indefinitely with no interpretation | Users see an unbounded number | Signed value retained for continuity, plus explicit `overdueDays` and `isStale` |

## 3. Domain contract

```kotlin
package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.CycleSource
import java.time.LocalDate

const val PREDICTION_ALGORITHM_VERSION = "2.0.0"

enum class EstimateQuality { INSUFFICIENT_HISTORY, LOW, MODERATE, HIGH }

enum class CyclePhase { MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL, UNKNOWN }

enum class PredictWarning {
    NO_HISTORY,
    IMPOSSIBLE_INTERVAL,
    OUTLIER_EXCLUDED,
    IRREGULAR_HISTORY,
    STALE_HISTORY,
    TREND_DETECTED,
    USER_ASSUMPTION_USED,
}

data class DateWindow(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val halfWidthDays: Int,
)

data class IntervalEstimate(
    val fromRecordId: String,
    val toRecordId: String,
    val startDate: LocalDate,
    val lengthDays: Int,
    val usable: Boolean,
    val exclusionReason: String?,   // user-facing, e.g. "Looks like a 214-day gap"
)

data class PhaseEstimate(
    val phase: CyclePhase,
    val isEstimated: Boolean,       // false only when confirmed by an observed record
)

data class CyclePrediction(
    val algorithmVersion: String,
    val generatedForDate: LocalDate,

    // provenance
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
    val nextPeriodDate: LocalDate,        // centre of the window
    val nextPeriodWindow: DateWindow?,    // null iff quality == INSUFFICIENT_HISTORY
    val ovulationDate: LocalDate,
    val fertileWindow: DateWindow?,       // null iff quality == INSUFFICIENT_HISTORY
    val cycleDay: Int,
    val daysUntilNextPeriod: Int,
    val overdueDays: Int,
    val phase: PhaseEstimate,
    val warnings: List<PredictWarning>,
)
```

Contract rules:

- Pure functions. No Android, Room, Compose, network, or ambient clock.
- `today` is a required parameter. There is no `= LocalDate.now()` default anywhere in this module.
- All inputs are **observed** records only. `CycleSource.PREDICTED` records must be filtered upstream and are additionally rejected defensively here.
- `recordIdsUsed` and `intervalsExcluded` exist so any estimate can be explained to the user and audited in tests.
- Rounding happens at the presentation boundary. Internals are `Double`.

## 4. Algorithm

### 4.1 Interval extraction

Sort observed records by `startDate`. For each consecutive pair, compute `lengthDays = DAYS.between(earlier.start, later.start)`.

**A period's own length is a different quantity.** Period length comes from `endDate − startDate` on a single record and must never be mixed into cycle length.

```kotlin
val intervals = observed
    .sortedBy { it.startDate }
    .zipWithNext { a, b ->
        IntervalEstimate(
            fromRecordId = a.id,
            toRecordId = b.id,
            startDate = a.startDate,
            lengthDays = DAYS.between(a.startDate, b.startDate).toInt(),
            usable = true,
            exclusionReason = null,
        )
    }
```

### 4.2 Plausibility gate

| Constant | Value | Rationale |
|---|---|---|
| `MIN_PLAUSIBLE_INTERVAL_DAYS` | 15 | Below this it is almost certainly a data-entry error, not a cycle |
| `MAX_PLAUSIBLE_INTERVAL_DAYS` | 60 | Deliberately wider than the app's existing 20..45 input guardrail: that guardrail rejects *user-entered* values, but real observed cycles genuinely exceed 45 |

Intervals outside this band are marked `usable = false` with a user-facing `exclusionReason`, and are **excluded from estimation but retained for irregularity reporting and user correction**. They are never silently dropped — see `05` §4.

> This is the fix for the defect where `CycleRecordValidator` range-checks the typed `cycleLength` field but never compares it against the real start-to-start gap, allowing a 300-day gap to validate and then dominate the average.

### 4.3 Robust centre estimate

Recency-weighted trimmed mean over the most recent `MAX_INTERVALS` usable intervals.

| Constant | Value | Rationale |
|---|---|---|
| `MAX_INTERVALS` | 12 | More context than the current 6; cycle drift is slow |
| `RECENCY_DECAY` | 0.85 | Most recent interval weight 1.0; each prior interval ×0.85. Cycle characteristics change with age, stress, illness and contraception |
| `TRIM_PER_TAIL_MIN_INTERVALS` | 5 | Below this, trimming discards too much signal |

```kotlin
val weighted = usable.takeLast(MAX_INTERVALS)
    .mapIndexed { index, interval -> interval to RECENCY_DECAY.pow(usable.size - 1 - index) }

val trim = if (weighted.size >= TRIM_PER_TAIL_MIN_INTERVALS) 1 else 0
val sorted = weighted.sortedBy { it.first.lengthDays }
val kept = sorted.drop(trim).dropLast(trim)

val centre = kept.sumOf { it.first.lengthDays * it.second } / kept.sumOf { it.second }
```

Use a **trimmed weighted mean** rather than a weighted median: with small `n` a median is heavily quantized and unstable, while a 1-per-tail trim on `n ≥ 5` still removes the single most damaging outlier.

### 4.4 Drift (trend) adjustment

Cycle length drifts over time. Fit a weighted linear regression of interval length against recency rank.

```kotlin
val slopeDaysPerCycle = weightedLinearSlope(x = recencyRanks, y = lengths, w = weights)
val projectedDrift = slopeDaysPerCycle * TREND_HORIZON_CYCLES
val trendAdjustment = (PROJECTED_DRIFT_CAP * tanh(projectedDrift / PROJECTED_DRIFT_CAP)).toDouble()
```

| Constant | Value | Rationale |
|---|---|---|
| `TREND_ACTIVATION_DAYS` | 1.5 | Below this the drift is noise; leave the estimate alone |
| `TREND_HORIZON_CYCLES` | 1 | Correct for the **next** cycle only; do not extrapolate further |
| `PROJECTED_DRIFT_CAP` | 3.0 | Maximum correction of 3 days, applied through `tanh` so it saturates smoothly |

The `tanh` damping is deliberate. An uncapped regression will happily apply a 10-day correction off two noisy points; a hard clamp creates a visible discontinuity as the estimate crosses the threshold. `tanh` saturates smoothly.

Set `TREND_DETECTED` when `|projectedDrift| ≥ TREND_ACTIVATION_DAYS`.

`effectiveCycleLength = centre + trendAdjustment`.

### 4.5 Luteal phase — an explicit assumption

Cycle length minus luteal length gives the follicular length. **Luteal length cannot be observed from period starts alone.** It requires ovulation measurement (basal body temperature, LH surge, cervical mucus) which CycleCare does not collect.

| Constant | Value |
|---|---|
| `LUTEAL_PHASE_DEFAULT_DAYS` | 14 |
| `LUTEAL_PHASE_SIGMA_DAYS` | 2.0 |

Therefore:

- `ovulationDate = nextPeriodDate.minusDays(LUTEAL_PHASE_DEFAULT_DAYS)` — a **population prior**, not a measurement.
- `LUTEAL_PHASE_SIGMA_DAYS` is an irreducible uncertainty floor added to every ovulation-derived window (§4.6). Even a user with perfectly regular cycles cannot have a tighter fertile window than this, because the app is guessing at ovulation.
- `fertileWindow = [ovulation − 4, ovulation + 1]` is retained as the population-typical window, widened by §4.6.

> **Extension path.** If ovulation observations are ever collected, estimate a per-user luteal mean from those observations and replace the prior, shrinking this uncertainty term. Do not add ovulation inputs to the model without also adding them to the validation corpus in `12`. Until then, no claim about individual ovulation timing may be made.

### 4.6 Uncertainty and the prediction window

This is the primary user-visible improvement: the app stops pretending to know a single day.

**Dispersion estimate.** Scale the weighted MAD by 1.4826 so it estimates the standard deviation of a normal distribution (the current code uses a raw MAD against a rounded mean, which is neither):

```kotlin
val sigmaRaw = 1.4826 * weightedMeanAbsoluteDeviation(lengths, centre, weights)
```

**Small-sample inflation.** A dispersion estimate from 3 intervals is badly biased low. Inflate it, with the multiplier decaying as evidence accumulates:

```kotlin
val sigmaHistory = sigmaRaw * (1.0 + SMALL_SAMPLE_INFLATION / max(n, 1))
```

| Constant | Value | Effect |
|---|---|---|
| `SMALL_SAMPLE_INFLATION` | 1.5 | n=3 → ×1.50, n=6 → ×1.25, n=12 → ×1.125 |

**Irregularity inflation.** Multiply by `IRREGULAR_SIGMA_MULTIPLIER` when §4.7 classifies the history as irregular.

**Total uncertainty.**

```kotlin
val sigma = sigmaHistory * (if (isIrregular) IRREGULAR_SIGMA_MULTIPLIER else 1.0)
```

**Window half-width** for a nominal two-sided 80% interval under a normal approximation is `1.2816 × sigma`, then constrained:

| Constant | Value | Rationale |
|---|---|---|
| `Z_80_TWO_SIDED` | 1.2816 | Normal z for a central 80% interval |
| `MIN_HALF_WIDTH_DAYS` | 1 | Never promise better than ±1 day |
| `MAX_HALF_WIDTH_DAYS` | 7 | Beyond a week the "range" is not useful; show variability copy instead |
| `INSUFFICIENT_HISTORY_MIN_INTERVALS` | 3 | Below this, emit **no** window at all |

```kotlin
val halfWidth = (Z_80_TWO_SIDED * sigma)
    .roundToInt()
    .coerceIn(MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS)
```

**When `n < 3`, return `nextPeriodWindow = null` and `fertileWindow = null`**, with `quality = INSUFFICIENT_HISTORY`. The UI shows a point estimate with "Not enough history to estimate a range yet" rather than a fabricated ±1 day.

The nominal 80% coverage is a **target to be verified**, not a guarantee. `12` specifies the held-out protocol and the release gate.

### 4.7 Irregularity

The current rule — `any { |length − mean| ≥ 6 }` — lets one bad row relabel a user's entire history as irregular. Require corroboration instead.

An interval is an **outlier** when both conditions hold:

```kotlin
deviation >= OUTLIER_MIN_DEVIATION_DAYS && deviation > OUTLIER_SIGMA_FACTOR * sigma
```

| Constant | Value | Rationale |
|---|---|---|
| `OUTLIER_MIN_DEVIATION_DAYS` | 7 | Below one week, variation is ordinary |
| `OUTLIER_SIGMA_FACTOR` | 3.0 | Adapt to the user's own variability — a person with σ=4 is not made irregular by a 12-day interval |
| `IRREGULAR_MIN_OUTLIERS` | 2 | **Two** outliers required, so one data-entry slip is not mistaken for biology |
| `IRREGULAR_MIN_INTERVALS` | 4 | Do not judge regularity on two data points |

```kotlin
val isIrregular = usable.size >= IRREGULAR_MIN_INTERVALS &&
    outliers.count { it.usable } >= IRREGULAR_MIN_OUTLIERS
```

Impossible intervals (§4.2) also force `isIrregular = true` and set `IMPOSSIBLE_INTERVAL`, because a 300-day gap genuinely means "we do not understand this history."

Irregularity is **not** a health judgment. It widens the window, changes the wording, and offers to help the user correct a date. It never says a cycle is abnormal or unhealthy (`05` §5, §9).

### 4.8 Cycle day, overdue and staleness

```kotlin
val cycleDay = max(1, DAYS.between(anchorDate, today).toInt() + 1)
val daysUntilNextPeriod = DAYS.between(today, nextPeriodDate).toInt()   // signed, may be negative
val overdueDays = max(0, -daysUntilNextPeriod)
```

The signed value is retained because existing UI and 36 passing tests depend on it, and removing a number users have learned to read is a regression. `overdueDays` gives the UI something bounded and humane to display.

**Staleness** is a quality signal, not an error:

```kotlin
val isStale = DAYS.between(anchorDate, today) > STALE_AFTER_CYCLE_MULTIPLE * effectiveCycleLength
```

| Constant | Value | Rationale |
|---|---|---|
| `STALE_AFTER_CYCLE_MULTIPLE` | 1.5 | More than a cycle and a half without a logged start means predictions rest on stale history |

Stale history forces `quality` down and produces copy that prompts the user to log, never shame (`03` §2.5).

### 4.9 Phase

Derive from interval position rather than fixed day numbers:

| Phase | Boundary | `isEstimated` |
|---|---|---|
| `MENSTRUAL` | cycle day 1 .. observed period length | `false` — confirmed by an observed record |
| `FOLLICULAR` | day after observed period end .. ovulation − 1 | `true` |
| `OVULATION` | ovulation date | `true` |
| `LUTEAL` | ovulation + 1 .. next predicted start − 1 | `true` |
| `UNKNOWN` | when `n < 1` | `true` |

Only `MENSTRUAL` days backed by an observed record may ever be presented as confirmed. This is the `05` §1 separation of observed from derived, enforced structurally rather than by convention.

### 4.10 Estimate quality

Replaces `PredictionSnapshot.confidenceLabel: String`. Derived from four independent signals — no raw MAD thresholds.

| Quality | Condition |
|---|---|
| `INSUFFICIENT_HISTORY` | `n < INSUFFICIENT_HISTORY_MIN_INTERVALS` (3) |
| `LOW` | `n < 5` **or** `sigma > 4` **or** `isIrregular` **or** `isStale` |
| `MODERATE` | `n ≥ 5` **and** `sigma ≤ 4` **and** not irregular **and** not stale |
| `HIGH` | `n ≥ 8` **and** `sigma ≤ 2` **and** not irregular **and** not stale |

```kotlin
fun qualityFor(n: Int, sigma: Double, irregular: Boolean, stale: Boolean): EstimateQuality = when {
    n < INSUFFICIENT_HISTORY_MIN_INTERVALS -> EstimateQuality.INSUFFICIENT_HISTORY
    n < 5 || sigma > 4.0 || irregular || stale -> EstimateQuality.LOW
    n >= 8 && sigma <= 2.0 -> EstimateQuality.HIGH
    else -> EstimateQuality.MODERATE
}
```

Never render a numeric probability (`05` §5). The user sees a range and a plain-language quality word.

## 5. User-facing copy

Rules only; final strings live in `strings.xml` per `14`/`15`.

| Situation | Say | Never say |
|---|---|---|
| `HIGH`/`MODERATE` with a window | "Estimated next period: Oct 24–27." | "Your period will start on Oct 25." |
| `LOW` with a window | "Based on 4 recorded cycles, which have varied. Estimated Oct 24–29." | "You're irregular." |
| `INSUFFICIENT_HISTORY` | "Not enough history to estimate a range yet. Log a few more cycles." | Any date with a false range |
| Impossible interval | "There is a 214-day gap between Mar 3 and Oct 3. Was that a missed period, or a date to correct?" | Silent exclusion |
| Irregular | "Your last few cycles have varied by more than a week." | "Your cycle is unhealthy/abnormal." |
| Stale | "Log your last period start to refresh this estimate." | "You are overdue." |
| Fertile window, always | "Estimated fertile days: Oct 20–25. This is not a contraceptive method." | "You are safe from pregnancy." |

Every estimate is displayable with a "How is this estimated?" affordance that reveals `recordIdsUsed`, `intervalsUsed`, `intervalsExcluded`, `centerCycleLength`, `dispersionSigma`, `quality` and `algorithmVersion`.

## 6. Determinism, versioning and persistence

- `PREDICTION_ALGORITHM_VERSION = "2.0.0"`, independent of the app version.
- The clock is injected. `core/data` must pass `today` explicitly — the current production call site (`Repositories.kt:142`) omits it and must be fixed.
- Predictions are **not persisted**. There is no cache table in schema v2 and this version does not add one; recomputation from ≤12 intervals is cheap. If a cache is added later it must carry `algorithmVersion`, an input fingerprint and a generated-at marker, and must be discarded on algorithm change.
- Because predictions are not persisted, upgrading from algorithm v1 to v2 requires **no data migration** — but it changes every estimate's shape from one date to a range. This needs release notes and a "what changed" surface.
- Golden fixtures are pinned per version. Any behaviour change requires updating `12` fixtures and recording why.

## 7. Downstream contracts

| Consumer | Change required |
|---|---|
| `InsightEngine` | Must use the same interval extractor (§4.1–4.2) and never read stored `cycleLength`. The current disagreement between `InsightEngine.kt:26` and `CyclePredictionEngine.kt:45` becomes structurally impossible |
| `PredictionSnapshot` | Replaced by `CyclePrediction`. `confidenceLabel: String` is removed; `quality: EstimateQuality` replaces it. Migrate consumers in one change, never both shapes at once |
| `ReminderSchedulePlanner` | Lead times now anchor to `nextPeriodDate`; the notification layer (`13`) owns uncertainty-aware timing and must not assume a fixed date |
| UI (Today, Calendar, Insights) | Must render `nextPeriodWindow` and distinguish it visually from an observed record using icon + label, never colour alone (`03` §5.3) |
| Export | Estimates must be labelled as estimates, with generation date and `algorithmVersion` (`05` §8) |

## 8. Acceptance criteria

Implementation is complete only when all of the following hold. Quantitative verification lives in `12`.

1. No implausible interval influences `centerCycleLength`.
2. A single 200-day outlier with 5 valid intervals does not shift `nextPeriodDate` by more than 1 day.
3. `n < 3` yields `nextPeriodWindow == null` and never a fabricated range.
4. Every window's half-width is within `[MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS]`.
5. `isIrregular` requires 2 outliers among ≥4 intervals; one outlier does not trigger it.
6. Identical inputs and identical `today` produce byte-identical output.
7. No ambient clock read anywhere in the module; production injects `today`.
8. Every output carries `algorithmVersion` and the record IDs used.
9. `InsightEngine` and the prediction engine cannot disagree for the same input, because they share one extractor.
10. Editing or deleting an input record changes downstream estimates deterministically and within the previously reported window.
11. No screen renders a point estimate without visible "estimate" framing.
12. No output implies contraception, diagnosis, or certainty.