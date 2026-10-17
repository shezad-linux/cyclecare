# 05 — Cycle Domain, Prediction Quality, and Health-Content Safety

> **Status: safety and guardrail layer.** The sections below remain **binding** on all estimation output.
>
> The algorithm specification itself lives in **`11-prediction-engine.md`** (method, constants, contract) and **`12-algorithm-validation.md`** (corpus, gates, regression harness). §2 and §3 below correctly identify the defects in the shipped estimator but do not define its replacement — `11` does.
>
> **Every threshold, label, range and warning introduced by `11` is subject to the constraints in this document.** In particular: no output may imply contraception, diagnosis, or certainty; sparse data must produce a visible uncertainty state rather than a fabricated value; and no insight may infer causation from temporal association.

## 1. Separate observed data from estimates

CycleCare must distinguish three concepts in code, database, UI and exported reports:

- **Observed record:** information entered by the user or imported with provenance.
- **Derived estimate:** a calculation based on observed records, preferences and a versioned algorithm.
- **Health information:** general educational copy that needs careful wording and review; it is not a diagnosis.

A derived estimate must never be saved as if the user confirmed a period start, symptom, ovulation date or fertile day. If a user confirms a predicted period start, create a separate observed record with an explicit source.

## 2. Assessment of the current proposed method

The supplied design describes:

- A rolling average from up to six cycle lengths.
- A predicted next period based on the latest period start plus that average.
- Ovulation estimated as 14 days before the next predicted period.
- A fixed fertile-window offset around estimated ovulation.
- Confidence labels based on mean absolute deviation (MAD).
- Irregularity detection triggered by variation of at least six days.

These are understandable heuristics, but the documentation does not establish that they provide calibrated probability or individual fertility accuracy. In particular:

- Six cycles may be unavailable or atypical; a mean is sensitive to outliers.
- Actual cycle timing varies and may change over time.
- Estimating ovulation backwards from the expected next period is a general assumption, not a direct measurement.
- A fixed fertile-window range should not be presented as a guarantee about pregnancy risk.
- A MAD threshold can describe variability, but cannot be called a percentage chance that a prediction is correct without calibration data.
- A fixed six-day rule is a product heuristic unless clinically justified and validated.

Treat the current algorithm as a **baseline estimate only**, not as clinically validated. The first release should not claim that it detects ovulation, diagnoses conditions, or predicts fertility with medical accuracy.

## 3. Domain API contract

Prefer pure, deterministic functions with explicit inputs and outputs, for example:

```kotlin
data class CycleInput(
    val observedStarts: List<LocalDate>,
    val lastConfirmedPeriodEnd: LocalDate?,
    val today: LocalDate,
    val userReportedAverageCycleDays: Int?,
    val algorithmVersion: String
)

data class CycleEstimate(
    val nextPeriodStartRange: ClosedRange<LocalDate>?,
    val estimateQuality: EstimateQuality,
    val basis: EstimateBasis,
    val warnings: List<EstimateWarning>,
    val algorithmVersion: String
)
```

This is illustrative contract code, not a final implementation. Keep domain inputs independent of Android, Room, Supabase, Compose, wall-clock globals or network state. Pass a clock/date explicitly so tests are deterministic.

Every estimate should carry enough metadata to explain:

- Which observed records were used.
- Which algorithm version generated it.
- Whether history was insufficient or irregular.
- Whether a user-entered assumption was used.
- Whether the output is a date, a range, or unavailable.

Avoid exposing internal statistical terms like “MAD” without a plain-language explanation.

## 4. Validation and edge cases

The domain layer needs explicit behavior for:

- No logged period start.
- One cycle only and insufficient history.
- Multiple identical starts or conflicting imports.
- End date before start date.
- Very short or long intervals that might be input errors.
- Long gaps, missing periods, and irregular history.
- User edits to historical dates that invalidate prior estimates.
- Date boundaries across leap years/months.
- Device timezone change and a changed local date.
- Future-dated entries.
- Deletion of the most recent cycle.
- Empty symptom logs and multiple symptoms on a single date.

Avoid silently discarding unusual but possibly valid data. Provide validation warnings and an edit/confirm path when a record is outside configured guardrails. Guardrail ranges must be justified, adjustable when appropriate and covered by tests.

## 5. Uncertainty and language

Prefer ranges over exact dates when history is sparse or highly variable. Examples:

- “Estimated next period: Oct 24–27.”
- “Estimate based on 3 recorded cycles; timing can vary.”
- “Not enough history to estimate this yet.”
- “This calendar is for personal tracking, not diagnosis or contraception.”

Avoid a numeric “confidence %” unless the algorithm has been calibrated against appropriate held-out data and the displayed percentage means what users reasonably believe it means. A label such as “more variable history” may be more honest than an unvalidated probability score.

**Fertility safety:** Never state that a day is “safe” for unprotected sex or that the app can be used to avoid pregnancy unless the product has undergone the required clinical, regulatory and validation review. An estimated fertile window is not a contraceptive method.

## 6. Insights

- Describe what was logged, not what caused it.
- Include the denominator and time span when showing counts or proportions.
- Don't infer a condition from symptom combinations.
- Don't make causal statements (“your mood is caused by…”) from temporal overlap alone.
- Provide an explanation or “Why am I seeing this?” path.
- Avoid insight output when logging history is sparse, contradictory or stale.
- Never send symptom histories to a remote LLM for narrative generation in the MVP.

Human-readable templates can be generated locally from validated domain results. Content related to diagnosis, urgent symptoms, fertility, pregnancy, medication or treatment should receive appropriate qualified review before release.

## 7. Algorithm verification

Build a test corpus with synthetic data, not production users' records. Include stable cycles, variable cycles, missing periods, data-entry mistakes, leap years, and corrections to old records.

Measure at least:

- Prediction availability rate at different amounts of history.
- Mean/median absolute date error against held-out observed dates where such validation is appropriate.
- Error distribution and worst-case cases, not only a mean.
- Coverage of any displayed date range: how often the observed event falls within it on held-out data.
- Results separated by history length and variability.
- Stability after editing or deleting an input record.

Do not claim clinical validation from synthetic tests. If the product makes medically meaningful claims, a qualified clinical review and a suitable validation approach are required.

## 8. Versioning and auditability

- Version the algorithm independently of the app version.
- Keep deterministic fixtures for each algorithm version.
- Add regression tests whenever behavior changes.
- Recompute derived estimates after a relevant data or algorithm change.
- Do not overwrite the confirmed record history with results from a new model.
- In exports, label estimate dates as estimates and include generation date/version if that materially helps interpret the report.

## 9. Health-content release checklist

- No screen presents predictions as certainty.
- Fertile-window copy never implies contraception.
- Sparse data produces a useful empty/uncertain state.
- Insight copy distinguishes temporal association from causation.
- All thresholds have a documented rationale.
- Health-content wording has an identified reviewer/approval status.
- The app contains a clear scope disclaimer without using it as a substitute for safe product behavior.
