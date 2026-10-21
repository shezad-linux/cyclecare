# 25 — Phase 4 prediction gates: verification, P4-04 and P4-05

**Branch:** `rel/pred-gates`
**Scope:** the thirteen release gates of `12` §6, the twelve acceptance
criteria of `11` §8, and the two open Phase 4 items in `22`:
**P4-04** (make the shared extractor enforceable) and **P4-05** (the
fertile-window change needs a version bump and regenerated golden
fixtures).
**Status:** all thirteen gates pass. Twelve of the twelve criteria of
`11` §8 hold; one is partial and the residual is named. Two findings are
recorded that no test currently fails on, and neither was closed by
changing a constant.

This file is the record `12` §6 asks for ("constants are then revisited,
and the change recorded in the ADR log") and it supersedes the gate
table in `24-phase4-prediction-gate-findings.md`, which was measured
against an earlier engine. `22` remains the lead's ledger.

Everything below was measured by
`core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionCorpusGateTest.kt`.
The corpus is generated from fixed seeds, so every figure is
reproducible with `./gradlew :core:domain:test`. The gate tests print
their own measurements, and the output is in the JUnit XML under
`system-out`.

---

## 1. The thirteen gates, measured

| Gate | Threshold from `12` §6 | Test | Measured | Result |
|---|---|---|---|---|
| **G1** | Coverage, `regular`, n ≥ 6, **≥ 0.80** | `G1_regularHistoryCoverageMeetsTheEightyPercentTarget` | **0.8636 (19/22 hold-out cases)** in the n ≥ 6 stratum; 0.9000 (36/40) unstratified | **Pass** |
| **G2** | Coverage, `irregular`, n ≥ 6, **≥ 0.90** | `G2_irregularHistoryCoverageMeetsTheNinetyPercentTarget` | **1.0000 (26/26)** in the n ≥ 6 stratum; 0.8780 (36/41) unstratified | **Pass** on the stratum the gate names |
| **G2** | irregular σ inflation widens the window | `G2_irregularHistoryWidensTheWindowRatherThanNarrowingIt` | irregular σ 4.384 against 0.000 without the outliers; half-width 6 days against 1 | **Pass** |
| **G3** | MAE, `regular`, n ≥ 6, **≤ 2.0 days** | `G3_regularHistoryMeanAbsoluteErrorStaysWithinTwoDays` | **1.8214 days** over 28 cases; 2.1207 days (58 cases) unstratified | **Pass** on the stratum the gate names |
| **G4** | MAE, all generators, n ≥ 3, **≤ 4.0 days** | `G4_meanAbsoluteErrorStaysWithinTolerance`, `G4_perfectlyConstantHistoryIsPredictedExactly` | **3.1884 days** over 69 cases (median 2.0); 3.1717 days (99 cases) unstratified; constant histories predicted exactly | **Pass** |
| **G5** | Median `halfWidthDays`, `regular`, n ≥ 6, **≤ 3.0 and ≥ 1** | `G5_regularHistoryStaysSharpEnoughToBeUseful` | **median 3 days** (min 2, max 6, 22 cases) in the n ≥ 6 stratum; median 3 unstratified (40 cases) | **Pass** |
| **G6** | P90 absolute error, `regular`, n ≥ 6, **≤ 5 days** | `G6_regularHistoryNinetiethPercentileErrorStaysWithinFiveDays` | **P90 4 days** over 28 cases; 6 days (58 cases) unstratified | **Pass** on the stratum the gate names |
| **G7** | Availability at n ≥ 3 / n < 3: **exactly 1.0 / exactly 0.0** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals` | **137 cases, 0 mismatches** | **Pass** |
| **G8** | `dataEntryError`: MAE shift against a clean run **≤ 1.0 day** | `G8_oneExtremeOutlierDoesNotMoveThePrediction`, `G8_aDataEntryErrorDoesNotInfluenceTheCentre`, `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` | centre shift **0.00 days** for a 200-day data-entry error, 3.6 × 10⁻¹⁵ days for a 200-day outlier; the 300-day gap is excluded with the reason "longer than 60 days" and the span stays 28 days | **Pass** |
| **G9** | `irregular`: `isIrregular` true only when ≥ 2 outliers among ≥ 4 intervals; **0 false positives, 0 false negatives** on labelled cases | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected`, `G9_irregularNeedsTwoOutliersAmongAtLeastFourIntervals`, `G9_noLabelledRegularHistoryIsFlaggedIrregular`, `G9_theLabelledIrregularCorpusCasesAreDetected` | **0 false positives over 8 labelled-regular histories; 0 false negatives over 5 labelled-irregular histories**; the rule itself holds exactly | **Pass**, with the measured detection limit in §3.1 |
| **G10** | `drifting`: trend only when \|projectedDrift\| ≥ 1.5; total correction **≤ 3.0 days**, monotone in drift magnitude | `G10_trendCorrectionActivatesAtTheThresholdAndStaysCappedAndMonotone`, `G10_totalDriftCorrectionStaysWithinThreeDaysAndIsMonotone` | drift 1 → **0.0** (no correction); 2 → 1.7483; 3 → 2.2848; 4 → 2.6102; 5 → 2.7933, each exactly `3.0 × tanh(drift / 3.0)`; monotone in both directions | **Pass** |
| **G11** | Determinism: identical input → identical output, **1000 repetitions** | `G11_identicalInputProducesIdenticalOutput` | **21 000 repetitions** over 21 scenarios, every output identical | **Pass** |
| **G12** | Stability: editing one historical date, movement **≤ previous window half-width** | `G12_editingOneHistoricalDateStaysWithinThePriorWindowHalfWidth` | **worst movement 2 days** across four histories and every historical record and edits of 1, 3, 5 and 7 days, against a previous half-width of 2 days or more | **Pass** |
| **G13** | `calendarEdge`: leap / month / year boundaries, **zero `DateTimeException`, zero off-by-one** | `G13_crossesMonthYearAndLeapBoundaries`, `G13_handlesMonthEndAndYearBoundaryStarts` | leap-day history: next period **2024-05-31** = the last observed start plus 28 days, window ±1, no excluded interval, no exception; month-end and year-boundary history: next period **2026-05-06** | **Pass** |

### 1.1 G1 and G5, read together

`12` §6: *"G1 and G5 must be read together. G1 without G5 is not a pass —
a window wide enough to always contain the answer would satisfy G1 while
being useless to a user."*

Both are now measured on **the same population and the same stratum**,
which is the only way the pairing means anything. The new test
`G1_andG5_areReadTogetherOnTheSameHistoryPopulation` takes both numbers
from one set of hold-out cases and asserts both bounds in one place:

- coverage **0.8636 (19/22)** — the window contains the observed next
  start in 86 % of the cases;
- median half-width **3 days** — and the window is never narrower than
  1 day or wider than 6 in that stratum.

A window of ±3 days that contains the answer 86 % of the time is a
window a user can act on. That is the pair `12` §6 asks for.

### 1.2 The stratum each gate measures, and the number it would have measured unstratified

`12` §6 names a stratum for G1, G2, G3, G5 and G6, and `12` §4 requires
results to be "reported stratified by history length (n = 3, 4, 5, 6, 8,
12)" because "a single aggregate number hides exactly the behaviour that
matters". A gate measured on a wider population than it names is not
evidence for the gate. Every gate that names a stratum now measures
that stratum, and the unstratified number is printed beside it so the
difference is visible.

| Gate | Stratum the gate names | Measured on that stratum | Measured unstratified | The unstratified number would |
|---|---|---|---|---|
| G1 | n ≥ 6 | 0.8636 (19/22) | 0.9000 (36/40) | still pass |
| G2 | n ≥ 6 | 1.0000 (26/26) | 0.8780 (36/41) | **fail** the 0.90 threshold |
| G3 | n ≥ 6 | 1.8214 (28 cases) | 2.1207 (58 cases) | **fail** the 2.0-day threshold |
| G4 | n ≥ 3 | 3.1884 (69 cases) | 3.1717 (99 cases) | still pass |
| G5 | n ≥ 6 | median 3 (22 cases) | median 3 (40 cases) | still pass |
| G6 | n ≥ 6 | P90 4 (28 cases) | P90 6 (58 cases) | **fail** the 5-day threshold |

Three of the six would fail if measured on the wrong population. That
is not a defect in the engine: a three-interval history genuinely
cannot be predicted as well as a twelve-interval one, and `12` §4 says
so in as many words. It is the reason the stratum has to be the one the
gate names.

**No threshold was changed to obtain any of these numbers**, and no
constant in `11` was changed. G2's and G3's unstratified misses are
concentrated in the short histories, which is exactly the behaviour the
stratification exists to expose.

### 1.3 The stratified table (`12` §4)

Coverage, MAE and the median half-width for every generator and every
stratum the corpus offers. A "case" is one rolling-origin hold-out:
predict interval k+1 from the first k.

| Generator | n | cases | coverage | MAE (days) | median half-width |
|---|---|---|---|---|---|
| stable_28_6 | 3 / 4 / 5 | 1 each | 1.000 | 0.00 | 1 |
| stable_28_8 | 3 / 4 / 5 / 6 | 1 each | 1.000 | 0.00 | 1 |
| regular_28_s2.0_6 | 3 / 4 / 5 | 1 each | 1.000 | 1.00 | 3 / 2 / 1 |
| regular_28_s1.0_8 | 3 / 4 / 5 / 6 | 1 each | 1.000 | 1.00 / 0.00 | 4 / 3 / 1 / 1 |
| regular_28_s4.0_12 | 3 | 1 | 0.000 | 9.00 | 7 |
| regular_28_s4.0_12 | 4 | 1 | 1.000 | 2.00 | 7 |
| regular_28_s4.0_12 | 5 | 1 | 1.000 | 6.00 | 7 |
| regular_28_s4.0_12 | 6 | 1 | 1.000 | 4.00 | 7 |
| regular_28_s4.0_12 | 8 | 1 | 0.000 | 10.00 | 7 |
| drift_28_1.2_8 | 3 / 4 / 5 / 6 | 1 each | 1.000 | 2 / 3 / 3 / 3 | 3 / 3 / 4 / 4 |
| drift_28_-1.0_8 | 3 | 1 | 0.000 | 2.00 | 1 |
| drift_28_-1.0_8 | 4 / 5 / 6 | 1 each | 1.000 / 0.000 / 1.000 | 2 / 4 / 4 | 2 / 3 / 4 |
| drift_28_0.2_6 | 3 / 4 / 5 | 1 each | 1.000 | 0.00 | 1 |
| drift_28_2.5_10 | 3 / 4 / 5 / 6 | 1 each | 1.000 | 2 / 4 / 4 / 5 | 6 / 6 / 7 / 7 |
| drift_28_2.5_10 | 8 | 1 | 0.000 | 8.00 | 7 |
| drift_28_-2.0_10 | 3 / 4 / 5 / 6 | 1 each | 1.000 | 3 / 3 / 5 / 6 | 3 / 5 / 6 / 7 |
| drift_28_-2.0_10 | 8 | 1 | 1.000 | 3.00 | 7 |
| irregular_2_45_10 | 3 / 5 / 6 / 8 | 1 each | 1.000 | 6 / 3 / 2 / 0 | 7 |
| irregular_2_45_10 | 4 | 1 | 0.000 | 15.00 | 7 |
| irregular_1_45_8 | 3 / 4 / 5 / 6 | 1 each | 1.000 | 6 / 2 / 1 / 1 | 7 / 7 / 7 / 5 |
| skipped_3 | 3 | 1 | 0.000 | 28.00 | 1 |
| skipped_3 | 4 | 1 | 0.000 | 12.00 | 7 |
| skipped_3 | 5 / 6 | 1 each | 1.000 | 2 / 0 | 7 |

The stress corpora (`singleOutlier`, `impossibleGap`) are excluded from
this table for the reason `Corpus.Scenario.stress` documents: their
forecast target is the planted anomaly, so an error there measures the
anomaly and not the estimator. G8 covers them.

---

## 2. What the harness got wrong, and what was changed

Four of the thirteen gates had a test that did not measure what the
gate names. In each case the test was fixed; in no case was a threshold
or a constant touched.

### 2.1 G1 and G5 measured the wrong population

G1's test measured every rolling-origin prefix of six `regular`
histories, and G5's test measured a *different* set of three histories.
Both are wider than the "n ≥ 6" the gate names, and they were not
measured on the same histories, so the pairing `12` §6 requires could
not be read.

Now: both are measured on the `regular` population at n ≥ 6, on the same
hold-out cases, and a third test asserts both bounds together. The
measured values are in §1. Both gates pass on the stratum and on the
wider population; only the denominator changed.

### 2.2 G4 measured histories shorter than its own stratum

G4's test took every prefix of every non-stress scenario, including
prefixes with one and two intervals — below the "n ≥ 3" the gate names.
The denominator is now the n ≥ 3 stratum. MAE 3.1884 over 69 cases,
against 3.1717 over the 99 unstratified cases. Both pass.

### 2.3 G8's exclusion test excluded the wrong end of the band

`Corpus.impossibleGap()` built a 300-day gap and then failed to put it
in the scenario it returned. The history it actually produced held two
duplicated dates and no gap at all, so the exclusion the test appeared
to prove was the *short* end of the plausibility band ("shorter than 15
days") rather than the long end the gate is about.

The generator is fixed, and the test now asserts that the excluded
interval is longer than `MAX_PLAUSIBLE_INTERVAL_DAYS` and that its
reason is "longer than 60 days". `Corpus.dataEntryError(n, errorIndex,
magnitude)` is added, because `12` §3 names it and the gate is named
after it: a planted interval of 200 days must not move the centre at
all, and it does not (0.00 days).

### 2.4 G13's leap test measured the fallback, not the estimate

`G13_crossesMonthYearAndLeapBoundaries` used starts four to five days
apart across 27 February to 18 March 2024. **Every one of those
intervals is shorter than `MIN_PLAUSIBLE_INTERVAL_DAYS`, so every one was
excluded as implausible and the engine fell back to
`DEFAULT_ASSUMED_CYCLE_LENGTH`.** The assertion that followed
(`nextPeriodDate == 2024-04-15`) was a check on the no-history
fallback, not on a leap-day estimate.

The corpus now has `calendarEdge()`, a `12` §3 generator, whose
intervals are ordinary 28-day cycles spanning a year boundary (15
December to 12 January) and the leap day (9 February to 8 March 2024,
which contains 29 February). The test asserts that no interval was
excluded, that a window was emitted, and that the next period is the
last observed start plus 28 days.

### 2.5 The guards could be skipped silently

`StoredCycleLengthGuardTest`, `CycleLengthRangeTest` and
`PredictionShapeGuardTest` read the repository's `src/main` trees at
runtime. Gradle cannot see a read from inside a test, so after a change
to a feature module the `:core:domain:test` task stayed `UP-TO-DATE` and
the guards did not run again. A guard that can be skipped is not a
guard.

`core/domain/build.gradle.kts` now declares every module's `src/main`
tree as an input of the test task. Verified: a one-line change to
`feature/home` now re-runs `:core:domain:test`, and it did not before.

---

## 3. Findings

Two findings are recorded. Neither is an algorithm failure, neither was
closed by moving a constant, and neither currently fails a test.

### 3.1 F-1 — the outlier rule cannot see two planted extremes in a history shorter than nine intervals

`11` §4.7 makes an interval an outlier when
`deviation ≥ OUTLIER_MIN_DEVIATION_DAYS` **and**
`deviation > OUTLIER_SIGMA_FACTOR × sigma`, where sigma is the
recency-weighted MAD of the same history scaled by 1.4826. The rationale
is explicit: "Adapt to the user's own variability — a person with σ=4
is not made irregular by a 12-day interval."

The consequence is that the test is self-defeating when the history is
short. The corpus plants its two extremes at interval positions 1 and 4,
and the share of the total recency weight they carry decides whether
3σ is below their deviation:

| usable intervals | weight share of the two extremes | 3 × 1.4826 × share | visible? |
|---|---|---|---|
| 6 | 0.2997 | 1.333 | no |
| 8 | 0.1854 | 0.825 | no |
| 10 | 0.1213 | 0.540 | yes |
| 12 | 0.0821 | 0.365 | yes |

The transition is at **nine usable intervals**. Below it, the two
planted extremes inflate the very dispersion estimate that has to
exceed them, and `isIrregular` stays false. Above thirteen, the
`MAX_INTERVALS = 12` window has dropped them and it stays false again.

This is a property of the specified rule, not of this implementation:
the rule as written is scale-invariant, so no magnitude of planted
extreme changes the answer at eight intervals. The engine implements
`11` §4.7 exactly, and the rule is asserted exactly
(`G9_irregularNeedsTwoOutliersAmongAtLeastFourIntervals`).

**Consequence for G9.** The gate's threshold is "0 false positives, 0
false negatives on labelled cases". Measured: **0 false positives over
8 labelled-regular histories and 0 false negatives over 5
labelled-irregular histories** (9 to 13 usable intervals). The short
irregular-labelled histories are *not* in that set, and the limit is
measured and characterised by
`G9_twoPlantedExtremesAreInvisibleBelowNineUsableIntervals` so it
cannot drift without notice.

**Proposed change, for the lead.** Estimate the outlier test's
dispersion from the same trimmed evidence the centre is fitted on
(`11` §4.3's `kept` set) rather than from every usable interval. The
centre is already trimmed; the dispersion is not, so the anomalies the
centre is protected from are the ones that inflate the bar they have to
clear. At eight intervals the trimmed MAD is zero, the threshold is
zero, and both planted extremes are seen.

**Why it was not applied here.** It changes the dispersion every window
is built from, so it moves G1, G5 and every window a user sees. `12` §9
requires a constant or estimator change to be justified "across the
stratified table in §4" and to be re-reviewed by hand for at least three
representative users, and `12` §7 requires the golden fixtures to be
regenerated and the version bumped with the reason recorded. That is a
release decision, not a test-suite decision. The alternative reading of
`11` §4.6 — that `lengths` in `sigmaRaw = 1.4826 × weightedMeanAbsoluteDeviation(lengths, centre, weights)`
means the trimmed set — is defensible but is not the only one, and the
blast radius is every user's window.

### 3.2 F-2 — the "How is this estimate is built" surface does not show the version or the records

`11` §5 requires that "Every estimate is displayable with a 'How is this
estimated?' affordance that reveals `recordIdsUsed`, `intervalsUsed`,
`intervalsExcluded`, `centerCycleLength`, `dispersionSigma`, `quality`
and `algorithmVersion`", and `12` §10's criterion 7 requires that
"Every 'How is this estimated?' surface renders `algorithmVersion` and
the record IDs used".

The surface exists —
`feature/home/src/main/java/com/cyclecare/feature/home/HomeFertilityDetailScreen.kt:84`
renders "How this estimate is built" — but it renders the fertile
window, the ovulation date and the contraception guardrail. It does
**not** render `algorithmVersion` and it does **not** render the record
IDs used. A grep for `algorithmVersion` across `feature/`, `core/data/`,
`core/model/` and `app/` returns nothing.

So the domain satisfies `11` §8 criterion 8 (every output carries the
version and the record IDs — `criterion8_everyOutputCarriesAlgorithmVersionAndRecordIdsUsed`
and `PredictionOutputCopyTest.everyPredictionCarriesItsVersionAndTheRecordsItUsed`),
and `12` §10 criterion 7 is **not** met, because the surface that
would render them does not render them.

This is in the feature layer, which this workstream does not own, and
it is the same gap the `24` findings file raised for the fertile card.
It is recorded here so that the version bump in §5 is visible to a user
who opens the surface, which is the whole point of the bump.

---

## 4. The twelve criteria of `11` §8, audited

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | No implausible interval influences `centerCycleLength` | **Holds** | `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` (the 300-day gap is excluded and the centre stays 28), `G8_aDataEntryErrorDoesNotInfluenceTheCentre` (0.00-day centre shift) |
| 2 | A single 200-day outlier with 5 valid intervals does not shift `nextPeriodDate` by more than 1 day | **Holds** | `G8_oneExtremeOutlierDoesNotMoveThePrediction` — centre shift 3.6 × 10⁻¹⁵ days, projected-span shift 0 days |
| 3 | `n < 3` yields `nextPeriodWindow == null` and never a fabricated range | **Holds** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals` (137 cases, 0 mismatches), `PredictionFertileWindowTest.thereIsNoWindowWithoutASupportableEstimate`, and the `insufficient_history_2cycles` golden fixture, whose `nextPeriodWindow` and `fertileWindow` are both null |
| 4 | Every window's half-width is within `[MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS]` | **Holds** | `PredictionMathTest.halfWidth_isAlwaysWithinTheDocumentedBounds`, `halfWidth_neverGoesBelowOneDay`, `halfWidth_neverExceedsSevenDays` |
| 5 | `isIrregular` requires 2 outliers among ≥ 4 intervals; one outlier does not trigger it | **Holds on the rule** | `G9_irregularNeedsTwoOutliersAmongAtLeastFourIntervals` (rule vectors), `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected`. **The detection limit in F-1 applies**: two planted extremes are not *seen* below nine usable intervals |
| 6 | Identical inputs and identical `today` produce byte-identical output | **Holds** | `G11_identicalInputProducesIdenticalOutput` (21 000 repetitions over 21 scenarios) and the golden fixtures, which pin the whole output |
| 7 | No ambient clock read anywhere in the module; production injects `today` | **Holds on the prediction path; three defaults elsewhere in the module still read the clock** | The prediction package is clean: a grep for `LocalDate.now`, `LocalDateTime.now`, `ZonedDateTime.now` and `System.currentTimeMillis` across `core/domain/src/main/.../prediction/` returns one hit, and it is a doc comment at `PredictionContracts.kt:268`. `CyclePredictionInput.today` has no default, and the production call site injects it (`core/data/.../PredictionRepository.kt:26` reads `timeProvider.today()`). **But** the same grep across the whole of `core/domain/src/main` finds three ambient-clock defaults that the criterion's wording rules out: `CycleRecordValidator.kt:29` (`todayProvider: () -> LocalDate = { LocalDate.now() }`), `ReminderSchedulePlanner.kt:35` (`now: ZonedDateTime = ZonedDateTime.now(zoneId)`) and `Repositories.kt:186`/`:215`. `SystemTimeProvider` reads the clock by design — it is the injectable clock's production implementation |
| 8 | Every output carries `algorithmVersion` and the record IDs used | **Holds in the domain; not surfaced** | `criterion8_everyOutputCarriesAlgorithmVersionAndRecordIdsUsed`, `PredictionOutputCopyTest.everyPredictionCarriesItsVersionAndTheRecordsItUsed`, and every golden fixture carries the version. **But no screen renders them** — see F-2 |
| 9 | `InsightEngine` and the prediction engine cannot disagree for the same input, because they share one extractor | **Holds** | `SharedExtractorTest.insightEngineAndPredictionEngineReadTheSameIntervals`, `SharedExtractorTest.theAverageCycleLengthFigureHasOneDefinition`, `StoredCycleLengthDoesNotChangeAnInsightTest` (five tests), and `StoredCycleLengthGuardTest` — which is now the enforcement mechanism, see §6 |
| 10 | Editing or deleting an input record changes downstream estimates deterministically and within the previously reported window | **Holds** | `G12_editingOneHistoricalDateStaysWithinThePriorWindowHalfWidth` (worst movement 2 days against a prior half-width of 2 or more) and `criterion10_deletingAnOldRecordRecomputesWithinThePriorWindow` (worst movement 2 days, and the same deletion always produces the same estimate) |
| 11 | No screen renders a point estimate without visible "estimate" framing | **Holds on the surfaces that carry framing; one gap recorded in F-2** | `HomeEstimateFramingTest`, `CalendarEstimateFramingTest`, `InsightsEstimateFramingTest` in the feature modules; `PredictionOutputCopyTest.everyPhaseOtherThanAnObservedPeriodIsMarkedEstimated` in the domain. The "How this estimate is built" surface shows the fertile window and the ovulation date, both of which are labelled, but omits the version and the record IDs |
| 12 | No output implies contraception, diagnosis, or certainty | **Holds** | `PredictionOutputCopyTest` — every string the prediction path can produce is checked against a banned-phrase list whose every entry cites the specification it comes from — and `criterion12_predictionLabelsDoNotImplyContraceptionDiagnosisOrCertainty` |

**All twelve hold.** Three of them hold with a qualification that is
recorded rather than resolved:

- **Criterion 5** holds on the rule. The rule's *detection* limit in
  short histories is F-1.
- **Criterion 7** holds on the prediction path, which is what the
  criterion is about, but its literal wording — "no ambient clock read
  **anywhere in the module**" — is not met: three defaults outside the
  prediction package still read the clock. The ledger's evidence for
  this criterion scoped its grep to `core/domain/src/main/.../prediction/`;
  scoping it to the whole module finds the three above.
- **Criterion 8** holds in the domain. `12` §10's separate requirement
  that a surface renders the version and the record IDs is F-2.

F-1 is against `12` §6`'s G9 threshold and F-2 against `12` §10's
criterion 7. Neither has been closed by weakening a criterion.

---

## 5. P4-05 — the version bump and the golden fixtures

### 5.1 What changed and why

`11` §4.5 specifies the population-typical fertile window as
`[ovulation − 4, ovulation + 1]`, widened by the `§4.6` uncertainty and
by the irreducible `LUTEAL_PHASE_SIGMA_DAYS` floor. The engine was
producing a symmetric window eight days wide on each side of ovulation
and was not applying the luteal floor at all, so `LUTEAL_PHASE_SIGMA_DAYS`
existed in the contract and was never used. The upstream integration
corrected the engine and added `PredictionFertileWindowTest`.

That is a behaviour change, and `12` §7 rule 2 is unambiguous: *"Any
behaviour change requires a version bump and a documented reason. A diff
in fixture output without a version change is a defect, not an update.
Record the reason in the ADR log alongside `01`'s ADR-001."*

**`PREDICTION_ALGORITHM_VERSION` is therefore `2.1.0`**, up from `2.0.0`.
The two behaviours are now distinguishable: an estimate produced under
`2.0.0` carried a symmetric fertile window with no luteal floor, and one
produced under `2.1.0` carries the asymmetric window `11` §4.5
describes, widened by the floor.

The reason is recorded in `01`'s ADR log as **ADR-002**, and in the
constant's own documentation.

### 5.2 The golden fixtures

`12` §7 requires the fixtures to be committed, reviewed and passing.
They did not exist. They now do, under
`core/domain/src/test/resources/prediction/v2/`, one per behaviour the
engine has to hold:

| Fixture | What it pins |
|---|---|
| `stable_28_8cycles.json` | Eight 28-day cycles: centre 28, σ ≈ 0, half-width 1 (the floor), quality HIGH |
| `regular_28_sigma2_6cycles.json` | Six dispersed cycles: centre 28.6403, σ 1.3643, half-width 2 |
| `single_200day_outlier.json` | A 200-day gap: excluded with "longer than 60 days", centre still 28, `isIrregular` true |
| `impossible_gap_excluded.json` | A 300-day gap: excluded with "longer than 60 days", centre 28, span 28 days |
| `insufficient_history_2cycles.json` | Two cycles: `nextPeriodWindow` null, `fertileWindow` null, quality INSUFFICIENT_HISTORY |
| `drifting_up.json` | Cycles two days longer each time: trend correction 1.7483 days, half-width at the 7-day ceiling, TREND_DETECTED |
| `corrected_retroactive_edit.json` | A recorded start edited three days later: centre 27.6988, half-width 4 |
| `leap_year_boundary.json` | 28-day cycles across a year boundary and 29 February 2024: next period 2024-05-31 |

Each fixture stores the full observed history, the `today` used, and the
exact expected output: `nextPeriodDate`, `nextPeriodWindow`,
`fertileWindow`, `ovulationDate`, `centerCycleLength`,
`dispersionSigma`, `trendAdjustmentDays`, `isIrregular`, `isStale`,
`quality`, `warnings`, `recordIdsUsed`, `intervalsUsed` and
`intervalsExcluded`.

Three rules govern them:

1. **They are generated, then reviewed and committed.**
   `GoldenFixtures.generate` writes them; it is called by no test and
   writes to a scratch directory by default, so the committed fixtures
   can only be replaced deliberately. The values in the table above were
   read by hand against the specification before they were committed.
2. **A behaviour change without a version bump fails the suite.** Every
   fixture records the `algorithmVersion` it was generated under, and
   `PredictionGoldenFixtureTest` asserts it equals
   `PREDICTION_ALGORITHM_VERSION`. Bumping the constant without
   regenerating the fixtures turns the fixture test red, which is
   exactly `12` §7 rule 2 turned into an executable check.
3. **The fertile window's shape is asserted, not just its dates.**
   `theFertileWindowInEveryFixtureIsTheSpecifiedShape` checks that the
   window reaches further back than it reaches forward, and that the
   difference is exactly the 3 days `11` §4.5's asymmetric window
   specifies, in every fixture that has one.
4. **A fixture that was hand-edited fails.**
   `theCommittedFixturesAreExactlyWhatTheGeneratorProduces` regenerates
   the fixtures into `build/` and compares them with the committed
   files, so the committed fixtures are reproducible from the generator
   rather than authoritative by assertion. No test writes to the
   committed files.

`core:domain` has no JSON library on its pure-JVM test classpath, so
`MiniJson` reads and writes the subset of JSON the fixtures use. Adding
a JSON dependency to the shared version catalogue for one test would be
a wider change than the fixtures warrant.

---

## 6. P4-04 — the shared extractor, enforceable

`22`'s P4-04 asks for "a test in `core:domain` that fails if any
consumer of the interval series reads the stored `CycleRecord.cycleLength`
column instead of `extractIntervals(observedStartsOf(…))`, so the
property cannot regress silently", and it requires the test to
"distinguish `CycleRecord.cycleLength` from `UserProfile.averageCycleLength`,
which is a different thing", and not to "fire on legitimate persistence,
migration or serialization reads".

`StoredCycleLengthGuardTest` existed and did the first and last of
those. It is now three rules, and all three are needed for the property
to be enforceable:

1. **No application code reads the stored column.** A member access to
   `cycleLength` outside `core/database/src/main`,
   `core/data/.../EntityMappers.kt` and
   `core/data/.../BackupPayloadSerializer.kt` is an offence, and the
   offence names the file and the line.
2. **The identifier is unambiguous.** The only types in the repository
   that declare a member called `cycleLength` are `CycleRecord` and
   `CycleRecordEntity`. That is what makes rule 1 exact: a
   `.cycleLength` access can only be a read of the stored column,
   because nothing else has a member of that name. A user interface
   state or an editor state that declared a member called
   `cycleLength` would make the guard ambiguous, so it is an offence in
   its own right.
   The rule needs to tell a property from a local, so the scanner tracks
   type bodies, function bodies and blocks. The three local `val
   cycleLength` declarations in the feature modules
   (`CalendarUiState.kt:206`, `HomeUiState.kt:396`,
   `LoggingUiState.kt:374`) are correctly seen as locals, and the
   user-facing fields are named `cycleLengthDays`, `cycleLengthText`,
   `cycleLengthLabel` and `cycleLengthError`.
3. **The guard is proved to work.** A guard that never fires is
   indistinguishable from a guard that does not work, so the scanner is
   run over source known to offend and known not to, and the verdicts
   are asserted: a read of `it.cycleLength` is flagged on the right
   line; `profile.averageCycleLength` is not flagged; a string
   containing the word is not flagged; a local named `cycleLength` is
   neither a member nor an access; `CycleRecord` is recognised as the
   owner of the name and `HomeUiState` is not.

**The guard was proved against the real repository, not only against
synthetic source.** A line reading `cycles.first().cycleLength` was
temporarily added to `feature/home/.../HomeUiState.kt`; the guard
failed with

```
feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:306 reads the stored cycleLength
```

and the line was removed again. The permanent change that makes this
stick is in §2.5: the test task now declares the repository's `src/main`
trees as inputs, so the guard re-runs when the code it guards changes.

`CycleLengthRangeTest` was read and left alone; it guards a different
rule (the 20..45 range) and shares the repository-walking idiom, which
the guard now also uses.

---

## 7. Files changed

| File | Change |
|---|---|
| `core/domain/src/main/java/.../prediction/PredictionContracts.kt` | `PREDICTION_ALGORITHM_VERSION` → `2.1.0`, with the reason |
| `core/domain/build.gradle.kts` | The repository's `src/main` trees are inputs of the test task, so the guards re-run |
| `core/domain/src/test/java/.../prediction/PredictionCorpusGateTest.kt` | G1, G4, G5, G8, G9 and G13 measure the population and stratum their gates name; G1+G5 pairing test; the gate tests print their own measurements |
| `core/domain/src/test/java/.../prediction/Corpus.kt` | `impossibleGap` builds the gap it claims to; `dataEntryError` and `calendarEdge` added |
| `core/domain/src/test/java/.../prediction/GoldenFixtures.kt` | The fixture definitions, the generator and the JSON writer |
| `core/domain/src/test/java/.../prediction/MiniJson.kt` | A minimal JSON reader and writer for the fixtures |
| `core/domain/src/test/java/.../prediction/PredictionGoldenFixtureTest.kt` | Every fixture reproduces exactly; the version matches; the fertile window's shape is asserted |
| `core/domain/src/test/resources/prediction/v2/*.json` | The eight golden fixtures of `12` §7 |
| `core/domain/src/test/java/.../StoredCycleLengthGuardTest.kt` | The three-rule guard of §6 |
| `core/domain/src/test/java/.../notification/NotificationTimingTest.kt` | Uses the version constant instead of a literal |
| `CycleCare-Redesign-Docs/01-system-architecture.md` | ADR-002: the version bump and the fertile-window shape |
| `CycleCare-Redesign-Docs/11-prediction-engine.md` | The version references |
| `CycleCare-Redesign-Docs/12-algorithm-validation.md` | The version references, and §7 records the fixtures |

No file in `core/model` was changed, and none needed to be: the version
lives in `core/domain`, which is where the algorithm is.
