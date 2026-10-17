# 12 — Algorithm Validation, Acceptance Gates, and Regression Harness

**Status:** Implementation contract for verifying the engine specified in `11`.
**Applies to:** `PREDICTION_ALGORITHM_VERSION = "2.0.0"`.
**Depends on:** `11` (constants and outputs), `05` §7 (health-content safety), `06` (general test strategy).

## 1. Purpose and honest limits

Version 2 contains **no fitted parameters**. Every constant in `11` is a fixed, named, hand-chosen value. Nothing is learned from data at runtime.

This has a consequence that shapes the whole document: **the corpus below is for measurement, not training.** It answers "are these constants reasonable?" — it cannot and does not fit them. That is a genuine advantage, because it means a change to the algorithm is a deliberate act with a reviewable diff, not an opaque refit.

**What this document does not establish.** Passing every gate below demonstrates that the estimator behaves as designed on synthetic data. It is **not clinical validation**, it does not establish medical accuracy, and it must never be described as such (`05` §7). No screen, store listing, or export may claim "clinically accurate" or equivalent on the basis of this suite (`00` §"Must not be claimed until verified").

If CycleCare ever wants to make medically meaningful claims, that requires a separate programme: real consented data under appropriate governance, a qualified clinical reviewer, and a validation design agreed with that reviewer — not a passing test suite.

## 2. Why synthetic data, and what that costs

The corpus is generated, never drawn from real users. This is required: shipping users' reproductive data into a test fixture would breach the privacy posture of the product (`04`).

The cost is real and must be stated: a synthetic corpus cannot capture genuine cycle physiology. It verifies **internal consistency, numerical behaviour, robustness and edge-case handling** — not biological accuracy. Any generator assumption that does not hold in reality (for example, that skipped periods are drawn from the same distribution as observed ones) is a known limitation, not a validated claim.

A production-shaped, fully anonymised snapshot may be used to verify **migration and performance** behaviour (`02` §7) but never to validate prediction accuracy.

## 3. Corpus generators

All generators are seeded and deterministic. Located in
`core/domain/src/test/java/com/cyclecare/core/domain/prediction/Corpus.kt`.

| Generator | Parameters | What it exercises |
|---|---|---|
| `stable` | `n`, `mean` | Zero-variance history; window must collapse to the ±1 day floor |
| `regular` | `n`, `mean`, `sigma` | The primary case; nominal coverage must hold |
| `variable` | `n`, `mean`, `sigma` | Realistic spread; window must widen with σ |
| `drifting` | `n`, `mean0`, `slope` | Trend activation at `TREND_ACTIVATION_DAYS`; damping must not overshoot |
| `irregular` | `n`, `outlierCount`, `magnitude` | Requires 2 outliers before `isIrregular`; must inflate σ |
| `skipped` | `n`, `skipRate` | Missing periods produce implausible intervals → excluded, flagged |
| `sparse` | `n` ∈ 1..3 | Must emit `nextPeriodWindow == null` |
| `corrected` | base + retroactively edited date | Estimate must move smoothly, within the prior window |
| `calendarEdge` | fixed | Leap day, month end, year boundary, DST-adjacent dates |
| `dataEntryError` | `n`, `errorIndex`, `magnitude` | Impossible intervals must not influence the centre |

The existing 17 tests in `CyclePredictionEngineTest.kt` cover algorithm v1 behaviour (single fixed date, MAD-to-string labels). They must be **replaced**, not extended — several assert v1 semantics that v2 deliberately changes, and leaving them passing would misrepresent what ships.

## 4. Hold-out protocol

For a history with `k` intervals, evaluate by **rolling origin**: fit nothing, predict interval `k+1` from the first `k`, compare against truth. Repeat for every prefix.

```kotlin
for (prefix in 1 until intervals.size) {
    val actual = predict(observed = history.take(prefix + 1), today = lastObservedStart)
    val predicted = predict(observed = history.take(prefix), today = lastObservedStart)
    record(actual, predicted)
}
```

Every prediction is made with `today` equal to the last observed start, matching real usage.

Results are reported **stratified by history length** (`n` = 3, 4, 5, 6, 8, 12) and by generator. A single aggregate number hides exactly the behaviour that matters — a good average can coexist with useless predictions for new users.

## 5. Metrics

| Metric | Definition | Why |
|---|---|---|
| **Empirical coverage** | Fraction of hold-out cases where the observed next start falls inside `nextPeriodWindow` | The primary metric. A window that misses is worse than no window, because users act on it |
| **Sharpness** | Median and 90th-percentile `halfWidthDays` | Coverage is trivially achievable with an absurdly wide window. This is the guard against gaming the gate |
| **MAE** | Mean `|nextPeriodDate − observedStart|` in days | Typical error |
| **Median AE / P90 AE** | Median and 90th-percentile absolute error | Tail behaviour. A good MAE with a 20-day P90 is a bad product |
| **Availability** | Fraction of cases emitting a non-null window | Should be 1.0 at n ≥ 3 and 0.0 at n < 3 |
| **Outlier sensitivity** | MAE delta when one interval is corrupted | Robustness claim from `11` §4.3 |
| **Stability** | Estimate movement when an old record is edited or deleted | Users must not see predictions swing on data they did not just touch |

## 6. Release gates

All gates must pass for `2.0.0` to ship. Any failure blocks release; constants are then revisited, and the change recorded in the ADR log.

| ID | Gate | Threshold |
|---|---|---|
| **G1** | Coverage, `regular` generator, n ≥ 6 | **≥ 0.80** |
| **G2** | Coverage, `irregular` generator, n ≥ 6 | **≥ 0.90** (σ inflation should over-cover) |
| **G3** | MAE, `regular`, n ≥ 6 | **≤ 2.0 days** |
| **G4** | MAE, all generators, n ≥ 3 | **≤ 4.0 days** |
| **G5** | Median `halfWidthDays`, `regular`, n ≥ 6 | **≤ 3.0 days** and **≥ 1 day** |
| **G6** | P90 absolute error, `regular`, n ≥ 6 | **≤ 5 days** |
| **G7** | Availability at n ≥ 3 / n < 3 | **exactly 1.0** / **exactly 0.0** |
| **G8** | `dataEntryError`: MAE shift vs clean run | **≤ 1.0 day** |
| **G9** | `irregular`: `isIrregular` true only when ≥2 outliers among ≥4 intervals | **0 false positives**, **0 false negatives** on labelled cases |
| **G10** | `drifting`: trend applied only when `|projectedDrift| ≥ 1.5`; total correction | **≤ 3.0 days**, monotone in drift magnitude |
| **G11** | Determinism: identical input → identical output | byte-identical over 1000 repetitions |
| **G12** | Stability: editing one historical date | movement **≤ previous window half-width** |
| **G13** | `calendarEdge`: leap/month/year boundaries | zero `DateTimeException`, zero off-by-one |

G1 and G5 must be read together. **G1 without G5 is not a pass** — a window wide enough to always contain the answer would satisfy G1 while being useless to a user.

## 7. Golden fixtures

Pinned per algorithm version, under
`core/domain/src/test/resources/prediction/v2/`.

Each fixture stores the full observed history, the `today` used, and the **exact expected output** — including `nextPeriodDate`, `nextPeriodWindow`, `centerCycleLength`, `dispersionSigma`, `trendAdjustmentDays`, `isIrregular`, `isStale`, `quality` and `warnings`.

```
fixtures/v2/
  stable_28_8cycles.json
  regular_28_sigma2_6cycles.json
  single_200day_outlier.json
  impossible_gap_excluded.json
  insufficient_history_2cycles.json
  drifting_up.json
  corrected_retroactive_edit.json
  leap_year_boundary.json
```

Two rules make these trustworthy:

1. **Fixtures are generated, then reviewed and committed.** Never regenerate them to make a test pass — that is the mechanism by which a suite quietly stops testing anything.
2. **Any behaviour change requires a version bump and a documented reason.** A diff in fixture output without a version change is a defect, not an update. Record the reason in the ADR log alongside `01`'s ADR-001.

Fixtures are the primary regression defence. Corpus gates are the primary design evidence. Both are required.

## 8. Test harness

The repository uses **JUnit 4 and Truth exclusively** — no JUnit 5, no Robolectric (verified: 95 unit tests, all JUnit 4). The harness must match.

```kotlin
@RunWith(Parameterized::class)
class PredictionCorpusGateTest(
    private val scenario: CorpusScenario,
) {
    @Test fun coverageMeetsGate() { /* assert against G1/G2 */ }

    @Test fun sharpnessMeetsGate() { /* assert against G5 */ }

    @Test fun errorDistributionMeetsGate() { /* assert against G3/G4/G6 */ }
}
```

Companion object supplies scenarios from `Corpus.kt` at the stratified `n` values in §4.

Existing test assets that must change:

| Asset | Action |
|---|---|
| `CyclePredictionEngineTest.kt` (17 tests) | Replace. Asserts v1 point-date and `confidenceLabel` semantics that v2 intentionally replaces |
| `InsightEngineTest.kt` (3 tests) | Update. `InsightEngine` must consume the shared extractor, so fixtures change shape |
| New: `PredictionCorpusGateTest` | All gates in §6 |
| New: `PredictionGoldenFixtureTest` | Every fixture in §7 |
| New: `PredictionDeterminismTest` | G11 |
| New: `PredictionStabilityTest` | G12, edit/delete scenarios |
| New: `PredictionCalendarEdgeTest` | G13 |

Wiring: these are pure-Kotlin unit tests in `:core:domain` and need no device. They run under `./gradlew :core:domain:test` and under the existing `testDebugUnitTest` aggregate, which currently passes in ~16s — the corpus must not push that materially beyond a minute.

## 9. Tuning discipline

The corpus makes it easy to overfit constants to synthetic data. Constraints:

- Prefer a constant that is **explainable** ("widen the window when history is sparse") over one tuned to maximise a gate number.
- Never tune against a single generator's metrics in isolation; changes must be justified across the stratified table in §4.
- Changing any constant in `11` requires re-running the full corpus and re-reviewing the resulting windows by hand for at least three representative users (regular, variable, new-with-two-cycles).
- If a gate cannot be met without an implausible constant, the correct response is to **widen the window** — under-covering is a worse product failure than imprecision, because users make decisions on it.

## 10. Acceptance criteria for the algorithm

The engine is releasable when:

1. Every gate in §6 passes.
2. Every fixture in §7 is committed, reviewed and passing.
3. The v1 tests are removed, so no assertion describes superseded behaviour.
4. `InsightEngine` and the prediction engine provably share one interval extractor — asserted by a test, not by convention.
5. `11` §8's twelve functional criteria all hold.
6. `core/data` passes `today` explicitly; no ambient clock read remains on the production path.
7. Every "How is this estimated?" surface renders `algorithmVersion` and the record IDs used.
8. No screen, export, or listing copy claims clinical accuracy.
9. A named owner has reviewed the health-facing copy produced by every `quality` and warning branch against `05` §9.