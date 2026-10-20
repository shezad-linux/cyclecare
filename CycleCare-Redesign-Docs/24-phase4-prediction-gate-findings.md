# 24 — Phase 4 prediction engine: gate results, copy review, and open items

**Branch:** `workstream/phase4-prediction`
**Scope:** the five missing corpus gates of `12` §6 (P4-01), the remaining
criteria of `11` §8 (P4-02, P4-03), and the domain half of the "Today and
Insights can never contradict each other" clause.
**Status:** all thirteen gates pass. `11` §8 is eleven of twelve, with one
partial criterion and three open items that need the lead or the Phase 5/6
workstream to act.

This file is the record `12` §6 asks for ("constants are then revisited, and
the change recorded in the ADR log") and the review `11` §8.12 asks for
("every string the prediction engine can produce is read against `03` §7 and
`05` §9, and the result is recorded"). It does not replace `22`, which the
lead owns.

---

## 1. The thirteen gates, measured

Every figure below is measured by a named test in
`core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionCorpusGateTest.kt`.
The corpus is regenerated from a fixed seed, so the figures are reproducible
with `./gradlew :core:domain:test`.

| Gate | Test | Measured | Threshold | Result |
|---|---|---|---|---|
| G1 | `G1_regularHistoryCoverageMeetsTheEightyPercentTarget` | coverage 0.800 (32/40) over every hold-out prefix; 0.818 (18/22) in the n ≥ 6 stratum | ≥ 0.80 | **Pass** |
| G2 | `G2_irregularHistoryCoverageMeetsTheNinetyPercentTarget` | coverage 1.000 (12/12) in the n ≥ 6 stratum; 0.857 (18/21) over every prefix | ≥ 0.90 | **Pass** (on the stratum the gate names) |
| G2 | `G2_irregularHistoryWidensTheWindowRatherThanNarrowingIt` | irregular σ 2.957 against 0.000 without the outliers; half-width 4 days against 1 | window must widen | **Pass** |
| G3 | `G3_regularHistoryMeanAbsoluteErrorStaysWithinTwoDays` | MAE 1.318 days (22 cases, n ≥ 6); 1.577 (52 cases, every prefix) | ≤ 2.0 days | **Pass** |
| G4 | `G4_meanAbsoluteErrorStaysWithinTolerance`, `G4_perfectlyConstantHistoryIsPredictedExactly` | MAE 3.172 days (99 cases); constant history predicted exactly | ≤ 4.0 days | **Pass** |
| G5 | `G5_regularHistoryStaysSharpEnoughToBeUseful` | median half-width 2 days (min 1, max 5) | ≤ 3.0 and ≥ 1 | **Pass** |
| G6 | `G6_regularHistoryNinetiethPercentileErrorStaysWithinFiveDays` | P90 3.0 days (n ≥ 6); 4.0 days (every prefix) | ≤ 5 days | **Pass** |
| G7 | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals` | window present exactly at n ≥ 3 | exactly 1.0 / 0.0 | **Pass** |
| G8 | `G8_oneExtremeOutlierDoesNotMoveThePrediction`, `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` | the centre moves 0.00 days and the projected span 0 days when a 200-day outlier is added; a 300-day gap is excluded and reported | ≤ 1.0 day | **Pass** |
| G9 | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected` | one outlier: false; two: true | 0 false positives, 0 false negatives | **Pass** |
| G10 | `G10_trendIsAppliedOnlyAboveTheActivationThreshold` | drift 0.0 → correction 0.0; drift 1.0 → 0.0; drift 2.0 → 1.7483488360437307; drift 3.0 → 2.2847824678672954, each equal to `PROJECTED_DRIFT_CAP × tanh(drift / PROJECTED_DRIFT_CAP)` | correction only when \|projectedDrift\| ≥ 1.5 | **Pass** |
| G10 | `G10_totalDriftCorrectionStaysWithinThreeDaysAndIsMonotone` | \|correction\| ≤ 3.0; the predicted date moves ≤ 3 days from the centre; monotone in the magnitude of the drift in both directions | ≤ 3.0 days, monotone | **Pass** |
| G11 | `G11_identicalInputProducesIdenticalOutput` | 20 repetitions over every scenario, byte-identical | identical | **Pass** |
| G12 | `G12_editingOneHistoricalDateKeepsTheEstimateInsideThePreviousWindow` | the worst movement over seven histories, every historical record and edits of 1, 3 and 7 days is 1 day, against a previous half-width of 1 to 2 days | ≤ previous window half-width | **Pass** |
| G12 | `G12_deletingARecordRecomputesTheEstimateDeterministically` | identical deletion gives identical output; the centre moves ≤ 1 day; the total movement ≤ 4 days | see §4 | **Pass on those bounds** |
| G13 | `G13_crossesMonthYearAndLeapBoundaries`, `G13_handlesMonthEndAndYearBoundaryStarts` | no `DateTimeException`, no off-by-one | zero | **Pass** |

G1 and G5 are read together, as `12` §6 requires: coverage 0.800 with a
median half-width of 2 days is a window that contains the answer and is
still narrow enough to be useful.

### 1.1 The stratum the new gates are measured on

`12` §6 names a stratum for G2, G3 and G6 — "n ≥ 6" — and `12` §4 requires
results to be "reported stratified by history length (n = 3, 4, 5, 6, 8, 12)"
because "a single aggregate number hides exactly the behaviour that matters".
The new gates therefore measure the stratum the gate names: hold-out cases
whose history holds at least six intervals.

This matters for G2 and is reported rather than buried:

- In the n ≥ 6 stratum, G2 measures **1.000** (12/12).
- Over every prefix, including histories of three, four and five intervals,
  G2 measures **0.857** (18/21) and would fail.

The two misses are the same case in both scenarios: a history of four
intervals that contains a planted 45-day interval and a planted 15-day
interval. The centre is pulled to 31.85 days by the 45-day interval, the
window is capped at ±7 days, and the truth is 15 days. Nothing in the
algorithm can cover that case inside a seven-day window, so a threshold
change would not fix it; only a shorter history would have to be excluded,
which is what the gate's own stratum does.

The existing G1 implementation measures every prefix, so G1 and G2 are not
computed on identical denominators. G1 passes either way (0.800 over every
prefix, 0.818 in the n ≥ 6 stratum), and it was left as it is rather than
changed. **The lead should decide whether G1 is re-expressed on the n ≥ 6
stratum for consistency.**

---

## 2. Constants changed

**None.** No constant in `11` was changed, and no gate threshold in `12` §6
was changed.

Every gate passes against the constants as they stand, so `12` §6's remedy
("constants are then revisited") was not invoked. The one case where a
constant *could* have been moved — the G2 denominator — was resolved by
reading the gate's own wording rather than by moving a number.

Two engine behaviours were found that deviate from `11` §4.6 and `11` §4.5.
Both were left alone, with the measurements, because each is a behaviour
change to the shipped-candidate estimator rather than a constant the gate
sanctions revisiting:

1. **The dispersion estimate omits the normal consistency factor.**
   `11` §4.6 specifies `sigmaRaw = 1.4826 × weightedMeanAbsoluteDeviation(...)`.
   `CyclePredictionEngineV2.kt:69` uses the weighted MAD directly, and
   `sigmaFromMad` (`PredictionMath.kt:29`) is never called by the engine.
   Every window the engine emits is therefore 1/1.4826 ≈ 32 % narrower than
   the specification describes. Applying the factor would move the G5
   median half-width from 2 days to 3 days, which is exactly at the gate's
   limit, and would widen every window a user sees. `12` §7 requires a
   behaviour change to carry a version bump and a documented reason, and
   `12` §9 requires the resulting windows to be re-reviewed by hand for at
   least three representative users. That is a lead decision, not a gate
   failure.
2. **The fertile window has a different shape from the specification.**
   `11` §4.5 keeps the population-typical window `[ovulation − 4,
   ovulation + 1]` and widens it by the estimate's uncertainty in §4.6.
   `CyclePredictionEngineV2.kt:119` instead emits `ovulation ± (14/2 + 1 +
   halfWidth)`, and the `LUTEAL_PHASE_SIGMA_DAYS` floor that §4.6 calls
   "an irreducible uncertainty" is applied nowhere. For a 28-day cycle the
   engine's fertile window covers cycle days 7 to 23 (half-width 1), where
   the specification's text gives cycle days 10 to 17.

---

## 3. The twelve criteria of `11` §8

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | No implausible interval influences `centerCycleLength` | **Holds** | `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` |
| 2 | A single 200-day outlier with 5 valid intervals does not shift `nextPeriodDate` by more than 1 day | **Holds** | `G8_oneExtremeOutlierDoesNotMoveThePrediction` |
| 3 | `n < 3` yields `nextPeriodWindow == null` | **Holds** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals` |
| 4 | Every half-width is within `[MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS]` | **Holds** | `PredictionMathTest` |
| 5 | `isIrregular` requires 2 outliers among ≥ 4 intervals | **Holds** | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected` |
| 6 | Identical inputs produce byte-identical output | **Holds** | `G11_identicalInputProducesIdenticalOutput` |
| 7 | No ambient clock read; production injects `today` | **Holds** | `LocalPredictionRepository` reads `timeProvider.today()` |
| 8 | Every output carries `algorithmVersion` and the record IDs used | **Holds** | `PredictionOutputCopyTest.everyPredictionCarriesItsVersionAndTheRecordsItUsed` (new test; the criterion had no test before) |
| 9 | `InsightEngine` and the prediction engine cannot disagree | **Holds for the interval series and the average figure** | `SharedExtractorTest.insightEngineAndPredictionEngineReadTheSameIntervals`, `SharedExtractorTest.theAverageCycleLengthFigureHasOneDefinition` |
| 10 | Editing or deleting an input record changes downstream estimates deterministically and within the previously reported window | **Partial** | Editing: `G12_editingOneHistoricalDateKeepsTheEstimateInsideThePreviousWindow` passes. Deleting: deterministic, but the estimate can leave the previously reported window — see §4 |
| 11 | No screen renders a point estimate without visible "estimate" framing | **Holds on the surfaces that carry framing; two gaps** | See §5 |
| 12 | No output implies contraception, diagnosis, or certainty | **Holds** for every string the prediction path produces | `PredictionOutputCopyTest` — see §6 |

---

## 4. The open item: deleting a record can move the estimate outside the previously reported window

`11` §8.10 asks for the movement to stay inside the previously reported
window. Editing satisfies it. Deleting does not, in every case.

**Measurement.** Over seven histories (two perfectly regular, five with
ordinary spread), deleting any historical record:

| History | Previous half-width | Worst movement after a deletion | Inside the previous window? |
|---|---|---|---|
| `stable(6)` — six 28-day cycles | 1 day | 3 days | No |
| `stable(8)` — eight 28-day cycles | 1 day | 3 days | No |
| `regular(6)` | 1 day | 3 days | No |
| `regular(8, sigma 2, seed 3)` | 1 day | 3 days | No |
| `regular(12, sigma 2, seed 11)` | 1 day | 2 days | No |
| `regular(12, sigma 1, mean 31, seed 5)` | 2 days | 1 day | Yes |
| `regular(8)` | 2 days | 2 days | Yes |

**Cause.** Deleting a record merges its two neighbouring intervals into one
long gap. For a regular history that gap is 56 days, which is inside the
plausibility band (`MAX_PLAUSIBLE_INTERVAL_DAYS` is 60), so it is kept as
evidence. The drift regression then reads a long gap at one end of the
history as a trend and applies the full correction:

```
stable(6), delete record 1:  intervals [56, 28, 28, 28, 28]
                             centre 28.0 (unchanged, the trimmed mean holds)
                             drift correction −2.75 days
                             next period 3 days earlier
```

The centre is not the problem: it is a trimmed mean and it does not move at
all. The correction is.

**Why nothing was changed.** The two levers `12` sanctions are both closed
here. `TREND_ACTIVATION_DAYS` is pinned at 1.5 by G10 itself, so it cannot
be raised to suppress the deletion artefact. `MAX_HALF_WIDTH_DAYS` cannot
help either: the bound is the previous half-width of 1 day, and no sane
window width makes a 3-day movement fit inside a 1-day window. Widening
`MIN_HALF_WIDTH_DAYS` to 3 would satisfy the deletion clause for a sharp
history, but it would also push the G5 median half-width to exactly 3 days,
which is the gate's limit, and it would make every window ±3 days for users
whose cycles are perfectly regular.

**What the test pins instead.** `G12_deletingARecordRecomputesTheEstimateDeterministically`
asserts what the engine does guarantee: the same deletion always produces
the same estimate, the centre moves at most 1 day, and the total movement is
at most `PROJECTED_DRIFT_CAP + 1` days. It does not assert the `§8.10`
window bound, and it does not pretend to.

**Options for the lead.** Either (a) accept the behaviour and amend `11`
§8.10 to bound a deletion by the drift cap rather than by the previous
window, or (b) change the estimator so that the drift regression is fitted
on the same trimmed evidence the centre is fitted on — a single long
interval would then not create a trend, the deletion movement would fall to
0, and the G10 figures would be unchanged, because the slope of a perfectly
linear series is its slope whatever subset is fitted. Option (b) is a
behaviour change and needs the `12` §7 version bump and a documented
reason.

---

## 5. "No screen renders a point estimate without visible 'estimate' framing"

Surfaces that render a predicted quantity, and the framing each one
carries:

| Surface | Renders | Framing | Verdict |
|---|---|---|---|
| Today — hero | the next period date, the cycle day | the hero subtitle says "Expected …" and the range pill beside it carries `±N days`; with no range the copy says "not enough history for a range" | Framed |
| Today — fertile card | the fertile window range | the section title is "Fertile window" and the range is bare | **Gap, below** |
| Today — ovulation | the ovulation date | the label reads "Ovulation estimate" | Framed |
| Calendar — day cells | fertile and ovulation marks | the selected-day summary reads "Ovulation estimate" and "This is a future estimate based on your local history" | Framed |
| Calendar — a future date | the expected period date | "Your period is expected around …" | Framed |
| Insights | nothing predicted; every figure is observed history | not applicable | Framed by absence |
| Log — the cycle phase | a phase label derived from the cycle's fraction | not a point estimate | Framed by absence |

**Gap 1 — the Today fertile card.** `11` §5 requires the fertile window to
always read "Estimated fertile days: Oct 20–25. This is not a contraceptive
method." The card at `feature/home/src/main/java/com/cyclecare/feature/home/HomeTeaserCards.kt:109`
renders the range under the title `R.string.fertile_window` with neither
the word "estimated" nor the contraception guardrail. The required change is
in the feature module: title the card "Estimated fertile days" and add the
guardrail sentence.

**Gap 2 — the fabricated fertile window.** `PredictionSnapshotMapper.kt:27`
fills `PredictionSnapshot.fertileWindow` with `fertileWindowFallback()`
when the engine declined to emit one, because the snapshot's field cannot
be null. The Today card reads that field, so a user with one logged cycle
sees a fertile window the engine never produced. The required change is in
the feature module: hide the fertile window when `supportsRange` is false,
or carry the engine's null through.

**Tests added.** `feature/home/.../HomeEstimateFramingTest.kt`,
`feature/calendar/.../CalendarEstimateFramingTest.kt`,
`feature/insights/.../InsightsEstimateFramingTest.kt`, and the domain-level
`PredictionOutputCopyTest.everyPhaseOtherThanAnObservedPeriodIsMarkedEstimated`.

---

## 6. The output copy review (`11` §8.12)

**The strings the prediction path can produce, and their review.**

| String | Where | Review |
|---|---|---|
| "Baseline estimate" | `PredictionSnapshotMapper.confidenceLabel` | Framed, no certainty, no diagnosis, no contraception. |
| "Estimate may be out of date" | same | Framed. |
| "Narrow range" / "Moderate range" / "Wide range" | same | Framed. None of them names a certainty, a condition or a method. |
| "shorter than 15 days" / "longer than 60 days" | `CycleIntervals.extractIntervals` | States the gap, judges nothing. |
| "Your period may start tomorrow." / "Your period may start in N days. Prepare if helpful." / "Your period is likely between X and Y." | `ReminderSchedulePlanner` | Hedged. "may start" is not "will start". |
| "Your fertile window estimate begins today. …" | same | Framed as an estimate. |
| "Today matches your current ovulation estimate. …" | same | Framed as an estimate. |
| "Your period looks later than expected. …" | same | Hedged, and it asks the user to update the cycle rather than asserting a fault. |
| "Period reminder" / "Fertile window reminder" / "Ovulation reminder" / "Daily check-in" / "Cycle timing reminder" | same | Neutral. |
| "Cycle reminder available. Open the app to review it." | same | Neutral, and carries no health content on a lock screen. |
| The insight narrative, the cycle-window label and the two phase highlights | `InsightEngine` | Temporal, not causal. No condition is named. |

**The banned list, and where each entry comes from.** The list lives in
`PredictionOutputCopyTest.bannedPhrases`, with the source document beside
every entry, so it can be re-read against the specifications rather than
taken on trust: "will start" and "definitely" (`03` §7 Avoid, `11` §5 Never
say), "guarantee" (`05` §9), "accurate" and "clinically" (`11` §5, `12` §1),
"100%" (`03` §7 Avoid), "safe from pregnancy", "prevents pregnancy" and
"cannot get pregnant" (`05` §9), "unhealthy", "abnormal" and "you are
irregular" (`03` §7 Avoid, `11` §5 Never say).

**One finding outside the engine's copy.** `feature/onboarding/src/main/res/values/strings.xml:31`
reads "This helps predict your next cycle accurately." The phrase-absence
test covers the strings the prediction path produces, so this one is not
caught by it. It is an accuracy claim about a prediction, and `11` §5 and
`12` §1 both forbid that kind of claim. It belongs to the onboarding
feature, so it is reported rather than changed.

---

## 7. "Today and Insights can never contradict each other"

Both findings from the architecture audit were checked against the code.

### F-10 — the "Avg cycle" figure

**Confirmed, and it is in the feature layer.** Today renders
`uiState.cycleLength` under the label "Avg cycle"
(`feature/home/.../HomeMetricGrid.kt:84` and `:118`), and that value is
the onboarding baseline:
`feature/home/.../HomeRoute.kt:245` reads
`base.profile?.averageCycleLength ?: base.cycles.lastOrNull()?.cycleLength ?: 28`.
Insights renders `insights.averageCycleLength`
(`feature/insights/.../InsightsUiState.kt:121`), which is computed from the
observed start-to-start gaps. The two numbers are independent: a user whose
onboarding baseline says 28 and whose logged cycles run at 31 sees "31 days"
on Insights and "28 days" on Today, on the same day, under the same label.

**Fixed in the domain.** The average now has one definition,
`averageCycleLengthOf(cycles)` in
`core/domain/src/main/java/com/cyclecare/core/domain/prediction/CycleIntervals.kt`,
which reads the shared extractor and ignores the stored `cycleLength`
column. `InsightEngine` uses it
(`core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:46`),
and `SharedExtractorTest.theAverageCycleLengthFigureHasOneDefinition` pins
it.

**Required in the feature module.** `HomeRoute.kt:245` must render the
canonical figure and fall back to the onboarding baseline only when there
is no usable history:

```kotlin
val cycleLength = base.insights?.averageCycleLength
    ?: base.profile?.averageCycleLength
    ?: 28
```

Until that lands, the two screens can still disagree, because the defect is
in the screen, not in the domain.

### F-16 — the fertile window

**Confirmed, in three places, and the domain had a fourth.**

1. The prediction engine derives the window from ovulation:
   `CyclePredictionEngineV2.kt:114`. For a 28-day cycle the window covers
   cycle days 7 to 23 (half-width 1). See §2 for the deviation from
   `11` §4.5.
2. Today labels the phase from the cycle's fraction:
   `feature/home/.../HomeRoute.kt:85-89` puts "Fertile window" between
   0.40 and 0.60 of the cycle length, which is cycle days 12 to 16 of 28,
   and it moves with the cycle length rather than with the estimate.
3. Log hardcodes a day: `feature/logging/.../LoggingUiState.kt:443` sets
   `val fertileStart = 12`, so the countdown to the fertile window is
   right only for a 28-day cycle and ignores both the estimate and the
   user's own cycle length. The same file derives the phase from the same
   0.40–0.60 fractions at line 174.
4. The domain had a fourth derivation, in the insight engine, which dated
   a retrospective ovulation from the stored `cycleLength` column.

**Fixed in the domain.** The insight engine now derives its retrospective
window from the observed next start, through the same ovulation prior and
the same window rule the prediction engine uses
(`InsightEngine.kt:302`, with `typicalFertileWindow` in
`PredictionMath.kt`), so a cycle whose stored length disagrees with the
observed gap no longer produces a different fertile window.
`SharedExtractorTest.retrospectiveFertileWindowUsesTheObservedNextStart`
pins it.

**Required in the feature modules.** Today's phase label and Log's
countdown have to be derived from the estimate the engine produced, not
from the cycle's fraction and not from a literal day number. That is a
change in `feature/home` and `feature/logging`, and it is the Phase 5/6
workstream's to make.

### Still open in the domain

`InsightEngine.kt:108` bounds the symptom window with
`historicalCycles.last().cycleLength`, the second of the two sites the
Phase 2 ledger entry P2-03 names. It was left alone: the stored column is
the only figure that says how long the current cycle runs, and the two
alternatives in P2-03 (the recorded end date, or the next observed start)
would either cut the window to the last period's end or leave the current
cycle out of it. It is reported here so the Phase 2 workstream can decide.

---

## 8. The two engines' dispersion estimates are not the same function

The prediction engine's outlier test uses a recency-weighted, unscaled MAD
(`CyclePredictionEngineV2.kt:69`); the insight engine's uses an unweighted
MAD scaled by 1.4826 (`InsightEngine.kt:56`). The two irregularity labels
are therefore not structurally guaranteed to agree. No disagreement was
found in the thirteen interval patterns measured here, including the ones
that produce the largest deviations (28/44/28/44/28/44), because the
sigma-relative rule is conservative enough to reject both. It is recorded
because the guarantee "can never contradict" is not enforced by the code
today, only by the accident that both rules are lenient in the same
direction.

---

## 9. What changed

| File | Change |
|---|---|
| `core/domain/.../prediction/PredictionContracts.kt` | Added `FERTILE_WINDOW_LEAD_DAYS`, `FERTILE_WINDOW_TRAIL_DAYS`, `AVERAGE_CYCLE_LENGTH_WINDOW`. |
| `core/domain/.../prediction/PredictionMath.kt` | Added `typicalFertileWindow`, the one rule for a fertile window. |
| `core/domain/.../prediction/CycleIntervals.kt` | Added `recentCycleLengthsOf` and `averageCycleLengthOf`, the one definition of the average-cycle figure. |
| `core/domain/.../InsightEngine.kt` | Uses the canonical average; derives the retrospective fertile window from the observed next start through the shared ovulation prior. |
| `core/domain/src/test/.../prediction/Corpus.kt` | `drifting` gained a `noise` parameter so a known drift can be generated; added `percentile`. |
| `core/domain/src/test/.../prediction/PredictionCorpusGateTest.kt` | G2, G3, G6, G10 and G12. |
| `core/domain/src/test/.../prediction/PredictionOutputCopyTest.kt` | New: the copy review, the phrase-absence list, the version and provenance check, the phase framing check. |
| `core/domain/src/test/.../SharedExtractorTest.kt` | New: one extractor, one average figure, one fertile-window rule. |
| `feature/home/src/test/.../HomeEstimateFramingTest.kt` | New. |
| `feature/calendar/src/test/.../CalendarEstimateFramingTest.kt` | New. |
| `feature/insights/src/test/.../InsightsEstimateFramingTest.kt` | New. |
