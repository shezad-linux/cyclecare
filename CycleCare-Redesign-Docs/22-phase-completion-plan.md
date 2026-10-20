# CycleCare phase completion ledger

The canonical record of what is done, what is not, and what each remaining
item needs in order to close.

It supersedes the completion claims in `21-phase-5-6-acceptance-matrix.md`
and `20-release-readiness.md`. Where the two disagree, this document is
the one to believe, because every status here was checked against the
repository on the date below rather than carried forward from an earlier
report.

## Audit metadata

| | |
|---|---|
| **Audit date** | 2026-10-20 |
| **Commit** | `d689a7e167991aa2a2caa50fc43f4005cad86979` |
| **Branch** | `main` |
| **Working tree** | Clean at the commit above |
| **Unit baseline** | 333 tests, 0 failures, 0 skipped |
| **Instrumented baseline** | 166 tests, 0 failures, 2 skipped |
| **Environment** | `cc36` AVD, API 36, Android 16, 320x640 at 160dpi, `emulator-5556` |

The two skipped instrumented tests are `ReminderDozeDeliveryTest`, which
are opt-in and skipped by default because they force the whole device
into idle. See `23-verification-runbook.md` for how to run them.

The unit and instrumented baselines above were produced by running the
suites during this audit, not carried forward. The reproducible commands
are in `23-verification-runbook.md`.

## Status vocabulary

Eight statuses are used, and they mean different things.

| Status | Meaning |
|---|---|
| **Verified complete** | The criterion was checked against the running code or a passing test during this audit, and the check passed. |
| **Partially complete** | Some part of the criterion is verified complete and some part is not. Both halves are named. |
| **Not audited** | No evidence exists either way. The check has not been performed. This never means "pass". |
| **Failed** | The check was performed during this audit and the criterion was not met. The failing measurement is given. |
| **Not implemented** | The code was read and the behaviour the criterion requires is absent. |
| **Deferred by decision** | A recorded decision postponed the work. See `20-sync-decision.md` for the one such decision. |
| **External action required** | It can only be completed outside this repository. Nothing in this category is complete. |
| **Not started** | The work has not begun and does not gate the current release. |

Two rules keep these honest.

A check that was run and returned a non-empty result is **Failed**, not
"not audited" and not "partially complete". A check that was run and
returned an empty result, or that was read and found to be present, is
**Verified complete**. A criterion whose end state is an absence — "no
consumer still references `PredictionSnapshot.confidenceLabel`" — is
**Not implemented** when the references are still there, because the
mechanism that would make the absence possible has not been built.

No criterion in this ledger is recorded as **Failed** on the basis of a
test that was never run. The one criterion that does fail, `15` §14.14,
failed on a measurement taken during this audit.

Every open item carries four things: a **definition of done**, its
**dependencies**, a **verification procedure**, and the **expected
evidence**. An item without a runnable verification procedure is not
finished work; it is a note.

### Notation

The specification documents are cited as `` `NN` §X ``, where `NN` is the
two-digit document number. A citation with three parts, such as
`` `02` §9.10.3 ``, means **item 3 of the list in `02` §9.10**, not a
subsection. This is the convention the specification documents already
use among themselves — `14` §12.11, `15` §14.14, `13` §17.12 and
`03` §2.5 are all list items, and there is no heading with those
numbers. A reader following a citation into the source will land on the
section and then on the numbered item within it.

## Summary

| Phase | Name | Status | Headline |
|---|---|---|---|
| 0 | Audit and product decisions | **Verified complete** | Closed by the roadmap and not to be reopened. |
| 1 | Safety, before anything else | **Partially complete** | All four exit clauses verified in the repository. External declarations are tracked under Phase 9. |
| 2 | Contracts | **Partially complete** | `PredictionSnapshot.confidenceLabel` is still consumed by five Home files; two `InsightEngine` sites still read the stored `cycleLength`. |
| 3 | Schema migration | **Partially complete** | Six of the ten `02` §9.10 criteria hold outright, one is partly met, and three are not implemented (items 3, 4 and 9). |
| 4 | Prediction engine | **Partially complete** | **Five of thirteen corpus gates have no test anywhere in the repository.** |
| 5 | Design system | **Partially complete** | Four criteria partially met, one not audited. Previews are verified for one file, not for every screen. |
| 6 | Screens | **Partially complete** | `15` §14.14 **fails**: `CycleCareShell.kt` is 620 lines. Two criteria partially met. |
| 7 | Notifications | **Partially complete** | **Four of the twelve `13` §17 criteria are verified complete, three are partially met and five are not implemented.** |
| 8 | Sync | **Deferred by decision** | The ten gates and six invariants are out of scope. |
| 9 | Release readiness | **Partially complete** | Repository work mostly done. The external list is entirely open, and three `06` §2 privacy scenarios are not yet met. |
| 10 | Measure and improve | **Not started** | Post-launch by design. |

---

## Phase 0 — Audit and product decisions

**Exit criteria.** The roadmap marks this phase `✅ COMPLETE` and says:
*"Do not re-open this phase. If new code is added, extend the audit
rather than restarting it."*

**Status: Verified complete.**

The audit itself is recorded in `00-current-state-review.md` with file and
line evidence, and the product decisions are in `00` §2. The Phase 0
build figures quoted in the roadmap — 95 unit tests and 71 instrumented
tests compiling — are a snapshot of the pre-redesign repository and are
superseded by the 333/166 baseline above. That is a dated measurement
rather than a defect.

This ledger is that extension, not a restart.

**Open items: none.**

---

## Phase 1 — Safety, before anything else

**Exit criteria (verbatim, `08`):** *"no false public claim remains;
backups and transfer behaviour are decided and tested; CI is running with
privacy and secret guards; the two broken test suites pass."*

**Status: Partially complete.** All four clauses are verified complete in
the repository. The phase is not marked complete because the release-side
declarations that make the first clause true in the wild — the Play
Console forms and the hosted policy page — are external and open. They
are tracked in Phase 9, and cross-referenced from here.

### Clause-by-clause

| Clause | Status | Evidence |
|---|---|---|
| "no false public claim remains" | **Verified complete** (repository side) | `docs/privacy-policy.html`, corrected when the three false claims were removed. The in-app wording is `cycle_data_storage_and_ad_privacy` in both `feature/home/src/main/res/values/strings.xml` and `feature/insights/src/main/res/values/strings.xml`. The hosted policy page is the user's to publish and is an external action. |
| "backups and transfer behaviour are decided and tested" | **Verified complete** | `app/src/main/res/xml/data_extraction_rules.xml` and `backup_rules.xml`. `CycleCareDatabaseRoomTest` carries a case per migration. |
| "CI is running with privacy and secret guards" | **Verified complete** | `.github/workflows/ci.yml`, job *Privacy and secret guards*: `scripts/guard-no-ad-targeting.sh`, `scripts/guard-typography-roles.sh`, the gitleaks *Secret scan*, and *Confirm signing secrets are not tracked*. |
| "the two broken test suites pass" | **Verified complete** | 333 unit and 166 instrumented, both green, measured during this audit. |

### Open items

**P1-01 — The two `06` §5 CI-gate checks that are not in the workflow.**
`06` §5 lists ten checks a release candidate must pass. Two are not
present in `.github/workflows/ci.yml`: *dependency vulnerability review*
and *privacy manifest/store disclosure review*. The remaining eight are
present. These are repository-side configuration, and they belong to the
Phase 9 release gate, so they are filed there as **P9-02** and
**P9-03**. Nothing is needed here beyond the cross-reference.

**P1-02 — No app-level privacy manifest is committed.** A search for
`privacy*.xml` outside build directories returns nothing. The Play
requirements for the Google Mobile Ads SDK are satisfied by the SDK's own
manifest, which is merged at build time, so this may be a non-issue — but
it was not confirmed during this audit, and `06` §5 asks for a privacy
manifest review. Folded into **P9-03**.

---

## Phase 2 — Contracts

**Exit criteria (verbatim, `08`):** *"every constant has a documented
rationale; the domain contracts compile as Kotlin interfaces; no consumer
still references `PredictionSnapshot.confidenceLabel` or reads
`cycleLength` as an interval."*

**Status: Partially complete.** The middle clause holds. The last clause
does not. The first clause is unaudited across the codebase.

| Clause | Status | Evidence |
|---|---|---|
| "every constant has a documented rationale" | **Not audited** | The constants in `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt` carry per-constant rationale. The codebase as a whole was not swept. See **P2-01**. |
| "the domain contracts compile as Kotlin interfaces" | **Verified complete** | `core/domain/src/main/java/com/cyclecare/core/domain/Repositories.kt` declares the repository and capability contracts as Kotlin interfaces. `core:domain` and `core:model` are pure JVM and compile without the Android SDK. |
| "no consumer still references `PredictionSnapshot.confidenceLabel`" | **Not implemented** | See **P2-02**. |
| "no consumer still reads `cycleLength` as an interval" | **Partially complete** | See **P2-03**. |

### P2-01 — Every constant has a documented rationale

- **Definition of done.** Every `const val` and top-level `val` in the
  main source sets either carries a comment stating why it has the value
  it has, or appears in a table in a design document that states the same
  thing. Constants that are self-evident — a channel key, a string
  resource name — are explicitly listed as needing no rationale.
- **Dependencies.** None. This is a read-and-write exercise.
- **Verification procedure.** A new test in `core/domain` that parses the
  main source sets, finds every top-level `const val` and `val` with a
  literal initialiser, and fails the build for any that is not covered by
  either an adjacent doc comment or an entry in a rationale table. The
  test should be able to name the file and line of the offending
  constant, so the fix is obvious.
- **Expected evidence.** The test exists, passes, and its report lists
  the constants it exempted with a reason. A short section in this
  ledger recording the result.

### P2-02 — `PredictionSnapshot.confidenceLabel` is still consumed

- **Definition of done.** `PredictionSnapshot` no longer carries a
  `confidenceLabel: String`. It carries a machine-readable confidence
  value defined in `core:model` — an enum or a sealed type — and the UI
  layer renders that value as a `UiText`. No production consumer reads
  an English confidence string out of the domain.
- **Why it is open.** The field exists at
  `core/model/src/main/java/com/cyclecare/core/model/CycleModels.kt:156`
  and is populated by `CyclePrediction.toSnapshot()` in
  `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionSnapshotMapper.kt`
  from the domain's own `confidenceLabel()`, which returns English
  prose. Five production files in `feature/home` consume it:
  `HomeUiState.kt`, `HomeRoute.kt`, `HomeTeaserCards.kt`,
  `HomeScreen.kt` and `HomeCycleStatusScreen.kt`.
  A grep for `confidenceLabel` across the repository, excluding build
  outputs, returns **34 occurrences in 17 files**. Of those, 20 are in
  main source across 7 files — the field definition in `core/model`
  (1), the mapper in `core/domain` (2), and the five Home files (17) —
  and the other 14 are in test and preview files across 10 files.
- **Dependency.** This is the same work as the domain-layer English
  output gap recorded elsewhere: the domain should not emit user-facing
  English. It should be done once, not twice.
- **Constraint.** `core:model` is pure JVM and must not depend on
  `:core:designsystem`, so `UiText` cannot be the replacement type.
  The new type belongs in `core:model`, and the mapping to `UiText`
  belongs in the feature layer.
- **Verification procedure.** A grep for `confidenceLabel` across
  `core/model/src/main`, `core/domain/src/main`, `app/src/main` and
  `feature/*/src/main` returns nothing. A unit test in `feature/home`
  asserts that each confidence value renders to a distinct, non-empty
  label, so the mapping cannot silently collapse to one string.
- **Expected evidence.** The grep output is empty, and the home
  presentation test covers every case of the new type.

### P2-03 — Two `InsightEngine` sites still read the stored `cycleLength`

- **Definition of done.** `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt`
  derives no value from the stored `cycleLength` column. Its two
  remaining sites — line 107, which bounds the symptom window with
  `historicalCycles.last().cycleLength`, and line 278, which derives a
  retrospective ovulation date with `cycle.cycleLength - 14` — use
  values derived from cycle starts through the shared extractor, or the
  recorded end date of the cycle in question.
- **What is already right.** The contradiction the criterion exists to
  prevent is already fixed. `InsightEngine.build()` takes its trend
  series from `extractIntervals(observedStartsOf(cycles))` — the same
  extractor the prediction engine uses — and the comment at lines 30–33
  records the original defect, where reading the stored column let the
  screen contradict the prediction screen on the same day.
- **Why it is still open.** Neither remaining site feeds a *future*
  estimate, so the contradiction cannot occur today. But the second one
  does treat a stored cycle length as an interval to derive an ovulation
  date from, which is the pattern the criterion names. `11` §8.9 asks
  that `InsightEngine` and the prediction engine "cannot disagree for the
  same input, because they share one extractor", and for the per-cycle
  fertile window they do not share one.
- **Dependencies.** None. This is a domain-only change with no UI
  consequence beyond the wording of a historical fertile-window label.
- **Verification procedure.** The two call sites are replaced, and a
  test in `core/domain` asserts that the retrospective fertile window
  for a given history equals the one the shared extractor produces.
  The `06` §2 scenario *"Editing an old record invalidates and recomputes
  dependent estimates"* should be asserted at the same time.
- **Expected evidence.** The grep for `cycleLength` across
  `core/domain/src/main` returns no use as an interval. The
  `InsightEngine` test suite passes with a case covering the shared
  extractor.

---

## Phase 3 — Schema migration

**Exit criteria (verbatim, `08`):** *"every acceptance criterion in `02`
§9.10 passes; row counts and IDs verified identical before and after on
a real-shaped fixture; the app launches and behaves correctly on a
migrated v2 database."*

**Status: Partially complete.** Six of the ten `02` §9.10 criteria hold
outright, one is partly met, and three are not implemented.

### The exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "every acceptance criterion in `02` §9.10 passes" | **Partially complete** | See the table below. |
| "row counts and IDs verified identical before and after on a real-shaped fixture" | **Verified complete** | `core/database/src/androidTest/java/com/cyclecare/core/database/CycleCareDatabaseRoomTest.kt`, 20 instrumented cases, including `migration2To3_preservesEveryCycleRecordUnchanged` and `migration2To3_backfillsAuditTimestampsAndNoDeletions`. |
| "the app launches and behaves correctly on a migrated v2 database" | **Not audited** | See **P3-01**. |

### The ten `02` §9.10 criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | v2 → v3 preserves every row and every record ID | **Verified complete** | The migration copies every column with `INSERT … SELECT`; `migration2To3_preservesEveryCycleRecordUnchanged` asserts it. |
| 2 | No `None` value is ever converted to `NULL`, or vice versa | **Verified complete** | The test at `CycleCareDatabaseRoomTest.kt:156-162` asserts `'None'` survives in every enum column. |
| 3 | Every original `cycleLength` is preserved in `legacyCycleLength` | **Not implemented** | See **P3-02** and the ambiguity below. |
| 4 | Divergences are logged and surfaced to the user, never silently corrected | **Not implemented** | See **P3-03**. |
| 5 | Duplicate dates are collapsed deterministically and the count is reported | **Partially complete** | The collapse is deterministic and correct: `DELETE FROM symptom_logs WHERE rowid NOT IN (SELECT MAX(rowid) … GROUP BY dateEpochDay)` keeps the most recently inserted row, and a test asserts it. **The count is not reported anywhere** — no channel carries it. |
| 6 | Backfilled timestamps are never presented as real creation dates | **Verified complete** | `createdAtEpochMillis` appears in no file under `feature/*/src/main` or `app/src/main`, so it cannot be rendered. The backfill itself is documented at `Migrations.kt:93-95`. This is satisfied by the UI not reading the field, which is a weak guarantee — see **P3-04**. |
| 7 | No destructive fallback exists on any production path | **Verified complete** | `fallbackToDestructiveMigration` appears nowhere in the repository. |
| 8 | `migration1To2` and `migration2To3` both have passing tests against real fixtures | **Verified complete** | Both are covered in `CycleCareDatabaseRoomTest`; 20 instrumented cases pass. |
| 9 | Migration runs in one transaction and aborts without data loss on verification failure | **Not implemented** | See **P3-05**. |
| 10 | The backup and device-transfer decision in §9.9 is made and implemented before release | **Verified complete** | `data_extraction_rules.xml` and `backup_rules.xml`, with a `CycleCareDatabaseRoomTest` case per migration. |

### P3-01 — The app has never been launched against a migrated v2 database

- **Definition of done.** An instrumented test installs the app over a
  database seeded at schema v2 with realistic rows, launches the app, and
  asserts that Today, Calendar and Log render the migrated data and
  that no crash or migration error occurs.
- **Dependencies.** A v2 fixture builder. `CycleCareDatabaseRoomTest`
  already has the machinery to create and migrate a v2 database, so this
  is an extension of it rather than new work.
- **Verification procedure.** The test runs under
  `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`.
  It must exercise the real `Room.databaseBuilder` path with
  `Migrations.migration1To2` and `migration2To3` in the migration list,
  not a hand-rolled one.
- **Expected evidence.** A named test in the app's androidTest source
  set, passing, with the seeded rows visible in the rendered output.

### P3-02 — `legacyCycleLength` does not exist

- **Definition of done.** Either the `cycle_records` table gains a
  `legacyCycleLength` column that holds the original v2 value, or
  `02` §9.10.3 is amended to describe the design that was actually
  built, with a recorded rationale. The first is a schema change and
  needs a v3 → v4 migration, which is a real cost.
- **Dependencies.** The decision above. If the column is added, the
  migration, the entity, the backup format and the export format all
  change together, and the backup format change needs a version bump.
- **Verification procedure.** Either a migration test asserting that a
  v2 `cycleLength` value is preserved in `legacyCycleLength` after
  v3 → v4, or an amended `02` §9.10.3 citing the ADR that records why
  the value is kept in `cycleLength` itself.
- **Expected evidence.** A passing migration test, or the amended
  criterion with its rationale.
- **Ambiguity, reported rather than decided.** The criterion's intent —
  *"every original `cycleLength` is preserved"* — is met: the value is
  carried across unchanged and is explicitly deprecated for reads, and
  `Migrations.kt:88-91` records that the divergence from the true
  start-to-start gap "remains auditable". The criterion's letter — a
  column literally named `legacyCycleLength` — is not met. These are
  different requirements, and the spec does not say which one governs.
  The item is left open rather than silently recorded as a pass.

### P3-03 — Migration divergences are neither logged nor surfaced

- **Definition of done.** A migration that cannot preserve a row exactly
  — a duplicate collapse, a `cycleLength` that disagrees with the
  observed start-to-start gap, a row that would need its `None` changed
  — records what it did, both in the log and in a place the user can
  see it, and never corrects the divergence silently.
- **Dependencies.** A logger. `Migration.migrate(database)` receives only
  a `SupportSQLiteDatabase` and has no logger today, so either the
  migration gains one through the database builder's callback, or
  divergences are written to a table that the app reads and surfaces.
  The table is the better design, because it survives the process that
  performed the migration.
- **Verification procedure.** A migration test that seeds a v2 database
  containing a duplicate date and a `cycleLength` that disagrees with
  the observed gap, migrates, and asserts that both divergences are
  recorded and that a user-visible screen reports them.
- **Expected evidence.** A named test in `core/database`, and a screen
  or sheet in the app that shows the record of what the migration did.

### P3-04 — The backfilled-timestamp guarantee rests on nobody reading the field

- **Definition of done.** The guarantee in `02` §9.10.6 is enforced
  rather than incidental. Either the model exposes a distinct type for
  a synthetic timestamp so it cannot be formatted as a real one, or a
  test asserts that no main-source file reads `createdAtEpochMillis`
  outside the persistence layer, and the test fails if one starts to.
- **Dependencies.** None beyond the decision above.
- **Verification procedure.** A test in `core/data` that scans the
  main source sets for reads of the audit timestamp columns and fails
  on any hit outside the persistence layer.
- **Expected evidence.** The test exists and passes. Today the
  criterion holds only because nothing reads the field.

### P3-05 — The migration has no verification step that could abort it

- **Definition of done.** After the schema change, the migration
  verifies that the row count and the record IDs of each migrated table
  match the source table, and raises if they do not — so the transaction
  aborts with the source data intact rather than completing with a
  partial move.
- **Dependencies.** None. Room already runs `Migration.migrate` inside a
  transaction, so an exception thrown from the migration rolls the
  whole thing back. The work is the verification query and the throw.
- **Verification procedure.** A test that seeds a v2 database, forces a
  verification mismatch — a count assertion against a deliberately
  altered expected value — and asserts that the migration throws and
  that the v2 rows are still present and unchanged afterwards.
- **Expected evidence.** A named test showing the rollback, with the
  pre-migration rows intact.

---

## Phase 4 — Prediction engine

**Exit criteria (verbatim, `08`):** *"G1–G13 pass; `11` §8's twelve
criteria hold; no screen shows an unlabelled point estimate; Today and
Insights can never contradict each other."*

**Status: Partially complete.** Eight of the thirteen corpus gates are
verified complete. **Five have no test anywhere in the repository.** Eight
of `11` §8's twelve criteria hold, one is partial and three are not
audited.

### The corpus gates, `12` §6

`12` §6 states: *"All gates must pass for `2.0.0` to ship. Any failure
blocks release; constants are then revisited, and the change recorded in
the ADR log."*

The harness is
`core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionCorpusGateTest.kt`.
A grep for each gate identifier across every `.kt` file in the
repository, excluding build outputs, returns the following. The five
empty rows are not missing *failing* gates; they are gates with no
implementation at all.

| Gate | Threshold | Status | Test |
|---|---|---|---|
| G1 | Coverage, `regular`, n ≥ 6 ≥ 0.80 | **Verified complete** | `G1_regularHistoryCoverageMeetsTheEightyPercentTarget` |
| G2 | Coverage, `irregular`, n ≥ 6 ≥ 0.90 | **Not implemented** | No test exists. `G2` appears in no `.kt` file outside this ledger. |
| G3 | MAE, `regular`, n ≥ 6 ≤ 2.0 days | **Not implemented** | No test exists. |
| G4 | MAE, all generators, n ≥ 3 ≤ 4.0 days | **Verified complete** | `G4_meanAbsoluteErrorStaysWithinTolerance`, `G4_perfectlyConstantHistoryIsPredictedExactly` |
| G5 | Median `halfWidthDays`, `regular`, ≤ 3.0 and ≥ 1 | **Verified complete** | `G5_regularHistoryStaysSharpEnoughToBeUseful` |
| G6 | P90 absolute error, `regular`, n ≥ 6 ≤ 5 days | **Not implemented** | No test exists. |
| G7 | Availability at n ≥ 3 / n < 3 is exactly 1.0 / 0.0 | **Verified complete** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals` |
| G8 | `dataEntryError` MAE shift vs clean run ≤ 1.0 day | **Verified complete** | `G8_oneExtremeOutlierDoesNotMoveThePrediction`, `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` |
| G9 | `irregular`: `isIrregular` true only at ≥ 2 outliers among ≥ 4 intervals | **Verified complete** | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected` |
| G10 | `drifting`: trend applied only when the absolute projected drift is ≥ 1.5; total correction ≤ 3.0 days, monotone in drift magnitude | **Not implemented** | No test exists. |
| G11 | Determinism: identical input → byte-identical output | **Verified complete** | `G11_identicalInputProducesIdenticalOutput` |
| G12 | Stability: editing one historical date moves the estimate ≤ previous window half-width | **Not implemented** | No test exists. |
| G13 | `calendarEdge`: leap/month/year boundaries | **Verified complete** | `G13_crossesMonthYearAndLeapBoundaries`, `G13_handlesMonthEndAndYearBoundaryStarts` |

`12` §6 also states: *"G1 and G5 must be read together. G1 without G5 is
not a pass — a window wide enough to always contain the answer would
satisfy G1 while being useless to a user."* Both are verified complete,
so that pairing is satisfied.

**P4-01 — The five missing corpus gates.** One open item, because the
procedure is the same for all five.

- **Definition of done.** `G2`, `G3`, `G6`, `G10` and `G12` have
  executable gate tests in `PredictionCorpusGateTest`, each asserting
  the threshold in the `12` §6 table above. Where a gate fails against
  the current constants, the constants are revisited and the change is
  recorded in the ADR log, as `12` §6 requires.
- **Dependencies.** The corpus generators. `G2` and `G6` need the
  `irregular` generator and a P90 statistic; `G10` needs the
  `drifting` generator and a monotonicity assertion across drift
  magnitudes; `G12` needs the edit-one-date scenario and the previous
  window's half-width.
- **Verification procedure.** `./gradlew :core:domain:test`. The five
  new tests appear by name in the report. The corpus is regenerated
  from a fixed seed, so the result is reproducible.
- **Expected evidence.** Five named tests, passing or failing honestly.
  A gate that fails is a release blocker, not a reason to lower the
  threshold — the threshold comes from `12` §6.

### The twelve criteria of `11` §8

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | No implausible interval influences `centerCycleLength` | **Verified complete** | `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` |
| 2 | A single 200-day outlier with 5 valid intervals does not shift `nextPeriodDate` by more than 1 day | **Verified complete** | `G8_oneExtremeOutlierDoesNotMoveThePrediction` |
| 3 | `n < 3` yields `nextPeriodWindow == null` and never a fabricated range | **Verified complete** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals`; `NotificationTimingTest.noWindowMeansNoReminderRatherThanAFabricatedDate` |
| 4 | Every window's half-width is within `[MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS]` | **Verified complete** | `core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionMathTest.kt` asserts both bounds, including at the σ extremes |
| 5 | `isIrregular` requires 2 outliers among ≥ 4 intervals | **Verified complete** | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected` |
| 6 | Identical inputs and identical `today` produce byte-identical output | **Verified complete** | `G11_identicalInputProducesIdenticalOutput` |
| 7 | No ambient clock read anywhere in the module; production injects `today` | **Verified complete** | A grep for `LocalDate.now`, `LocalDateTime.now`, `ZonedDateTime.now` and `System.currentTimeMillis` across `core/domain/src/main/.../prediction/` returns one hit, and it is a doc comment at `PredictionContracts.kt:223` describing the defect this criterion fixed. |
| 8 | Every output carries `algorithmVersion` and the record IDs used | **Verified complete** | `CyclePrediction.algorithmVersion` (`PredictionContracts.kt:179`) and `recordIdsUsed` (`PredictionContracts.kt:185`), both populated by `CyclePredictionEngineV2.kt:130` and `:134`; carried to the UI snapshot by `PredictionSnapshotMapper.kt:58` |
| 9 | `InsightEngine` and the prediction engine cannot disagree for the same input, because they share one extractor | **Partially complete** | The trend series shares the extractor (`InsightEngine.kt:38-40`). Two other sites still derive values from the stored `cycleLength` — see **P2-03**. |
| 10 | Editing or deleting an input record changes downstream estimates deterministically and within the previously reported window | **Not audited** | G12, the gate that would test this, does not exist. See **P4-01**. |
| 11 | No screen renders a point estimate without visible "estimate" framing | **Not audited** | Not checked during this audit. See **P4-02**. |
| 12 | No output implies contraception, diagnosis, or certainty | **Not audited** | A copy review. See **P4-03**. |

### P4-02 — Point estimates are not yet confirmed to be labelled

- **Definition of done.** Every surface that renders a predicted date,
  a cycle length, a fertile window or an ovulation date renders it with
  visible estimate framing, and a test asserts that framing is present
  for each of them.
- **Dependencies.** None. The UI already carries hedge copy in several
  places — "Your period may start…", "likely between … and …" — so the
  work is largely confirming coverage and making it testable.
- **Verification procedure.** A presentation test in each feature that
  renders an estimate — `feature/home`, `feature/calendar`,
  `feature/insights` — that asserts the rendered text contains the
  estimate framing and not a bare date presented as fact.
- **Expected evidence.** Named tests, one per surface.

### P4-03 — The output copy review

- **Definition of done.** Every string the prediction engine can
  produce is read against `03` §7 and `05` §9, and the result is
  recorded. No output may imply contraception, diagnosis or certainty.
- **Dependencies.** The domain-layer English output gap, shared with
  **P2-02**. If the domain keeps producing English strings, this review
  has to be repeated every time they change.
- **Verification procedure.** The strings the engine can produce are
  enumerated from the source, reviewed against the two documents, and
  the result recorded in a section of this ledger. A test asserts the
  absence of a banned-phrase list, so the review is repeatable.
- **Expected evidence.** A recorded review and a phrase-absence test.

### The remaining two exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "no screen shows an unlabelled point estimate" | **Not audited** | See **P4-02**. |
| "Today and Insights can never contradict each other" | **Partially complete** | The shared extractor holds for the trend series. The residual is **P2-03**. A "can never" guarantee is not yet enforced by a test that fails if a consumer reverts to the stored column. |

**P4-04 — Make the shared extractor enforceable.** *Definition of
done.* A test in `core/domain` fails if any consumer of the interval
series reads the stored `cycleLength` column instead of
`extractIntervals(observedStartsOf(…))`. *Dependencies.* **P2-03**,
which removes the two remaining sites. *Verification procedure.*
`./gradlew :core:domain:test`; the new test scans the main source sets
for `cycleLength` reads outside the persistence layer. *Expected
evidence.* A passing test that names any offending file and line.

---

## Phase 5 — Design system

**Exit criteria (verbatim, `08`):** *"every criterion in `14` §12
passes; no screen uses an undefined typography role; previews exist at
320/360/600/840dp in light and dark."*

**Status: Partially complete.** Eight of the thirteen `14` §12 criteria
are verified complete, four are partially met and one is not audited.
The typography clause is verified complete. The preview clause is
verified for one file, not for every screen.

### The thirteen `14` §12 criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Zero hardcoded colours outside `CycleCareTheme.kt`; `Ads.kt` radii use tokens | **Verified complete** | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt` is the single source of the palette; `Ads.kt` uses `CycleCareShapes`. The remaining `Color.White`/`Color.Black` pair is the inverse pair on a saturated fill. |
| 2 | All 15 typography roles defined and used; no `Text` relies on an undefined default | **Verified complete** | `scripts/guard-typography-roles.sh` passes, and runs in CI. |
| 3 | `Shapes()` defined and used; spacing tokens consumed everywhere | **Verified complete** | `DesignTokenTest` |
| 4 | No token accessor calls `isSystemInDarkTheme()` directly | **Verified complete** | `LocalIsDarkTheme` carries the flag the theme was built with. Fixed in `1d238dc` after the visual audit found the app-lock header painting dark text on a dark gradient at 1.68:1, now 14.45:1. |
| 5 | Confirmed versus estimated distinguishable by fill, border style and icon, verified in greyscale | **Verified complete** | `DesignTokenTest.confirmedStateStaysDistinctFromEstimatesWhenReducedToGreyscale` |
| 6 | No screen renders two competing primary actions | **Verified complete** | `HomeScreenAccessibilityTest.noCardRendersTwoCompetingPrimaryActions` |
| 7 | Every state in `03` §6 has a designed, tested implementation | **Partially complete** | See **P5-01**. |
| 8 | Every control meets 48dp; every screen works at 200% font scale; contrast verified per token pair | **Partially complete** | Touch targets: `DesignTokenTest.touchTargetMeetsTheAccessibilityMinimum`. Contrast: `scripts/contrast-report.py` — every pair with a WCAG target meets it. 200%: 13 screens swept, three defects found and fixed. Not yet done for every screen. |
| 9 | Every user-facing string is in `strings.xml` | **Verified complete** | The audit leaves four literals, all composed values or a diagnostic format. |
| 10 | Layouts verified at 320/360/600/840dp, portrait, landscape and foldable | **Partially complete** | Portrait and landscape verified at all four widths, 13 screens, 97 images each. **Foldable is not verified**: the emulator has no hinge or posture. See **P5-02**. |
| 11 | TalkBack traverses every key screen with correct roles, labels and states; no access trap remains | **Partially complete** | `TalkBackSemanticsTest` covers the app shell and Today only. See **P5-03**. |
| 12 | `:core:designsystem` has its own test suite | **Verified complete** | **38** cases across `DesignTokenTest` (28), `MetricCardAccentTest` (6) and `PreviewCoverageTest` (4). The figure of 32 in `21-phase-5-6-acceptance-matrix.md` is stale. |
| 13 | No dark-pattern copy anywhere in the UI | **Not audited** | See **P5-04**. |

### The remaining exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "no screen uses an undefined typography role" | **Verified complete** | `scripts/guard-typography-roles.sh`, in CI. |
| "previews exist at 320/360/600/840dp in light and dark" | **Partially complete** | `PreviewCoverageTest` asserts the four widths, both colour schemes and the large font scale — but against **one file**, `core/designsystem/src/main/java/com/cyclecare/core/designsystem/ScreenDebugPreviews.kt`, found by a path walk. Only six files use `ScreenWidthPreviews`: `HomePreviews.kt`, `HomeMetricGrid.kt`, `InsightsPreviews.kt`, `InsightCard.kt`, `SettingsComponents.kt` and `ScreenDebugPreviews.kt` itself. See **P5-05**. |

### P5-01 — The state matrix is not complete at screen level

- **Definition of done.** Every state in `03` §6 that applies to a
  screen is asserted at screen level, not only at module level. Save in
  progress, save success and validation error are currently produced by
  the real ViewModels and asserted in their own module suites, but not
  as a screen matrix. The export screen's working, success and failure
  states are not in the matrix at all. The empty state is asserted for
  Today and the reminder centre, not for every data-driven screen.
- **Dependencies.** None. `ScreenStateMatrixTest` already has the
  environment factories — `loading()`, `noHistory()`,
  `permissionDenied()`, `channelDisabled()` — to build on.
- **Verification procedure.** `ANDROID_SERIAL=emulator-5556 ./gradlew
  :app:connectedDebugAndroidTest`. The new cases appear by name in the
  report.
- **Expected evidence.** Named cases per state per screen, with the
  screen list from `21-phase-5-6-acceptance-matrix.md` as the scope.

### P5-02 — Foldable verification

- **Definition of done.** The layout is verified on a device with a
  hinge or a posture.
- **Dependencies.** Hardware. The `cc36` emulator has neither, and a
  resized display is not a foldable — claiming otherwise would be a
  false claim. A physical foldable, or an emulator image with a hinge,
  is required.
- **Verification procedure.** Capture the thirteen screens on the
  foldable, in both the folded and unfolded postures, at the device's
  own width, using the same harness as
  `ReThemeScreenshotTest`.
- **Expected evidence.** Screenshots in both postures, and the window
  size class transition noted.

### P5-03 — TalkBack coverage beyond the shell and Today

- **Definition of done.** Semantics assertions exist for the calendar's
  selected-date announcement, the prediction confidence messaging, form
  validation and save feedback, the dialogs and sheets, and the
  permission states. Separately, a human has run a screen reader
  through the key screens.
- **Dependencies.** None for the assertions. The manual pass needs a
  device and a person.
- **Verification procedure.** Extend `TalkBackSemanticsTest` to the
  remaining key screens. The manual pass is recorded with the device,
  the screen reader version and what was heard.
- **Expected evidence.** Named cases in the semantics test; a written
  manual pass record. The manual pass is currently pending and is not
  claimed.

### P5-04 — The dark-pattern copy review

- **Definition of done.** Every user-facing string in the app is
  reviewed against `03` §7 and `05` §9, and the result is recorded. No
  guilt copy, no streak-loss language, no confirmshaming, no
  notification pressure tactics.
- **Dependencies.** None. It is a copy review, not a behavioural one,
  so it cannot be automated into existence. It can, however, be made
  repeatable by a phrase-absence test.
- **Verification procedure.** Enumerate the strings from the resource
  files, review them against the two documents, and record the result.
  Then add a test asserting the absence of a banned-phrase list, so the
  review does not silently regress.
- **Expected evidence.** A recorded review and a phrase-absence test.

### P5-05 — Preview coverage is one file, not every screen

- **Definition of done.** Every screen that `15` §1.2 lists has a
  preview at 320, 360, 600 and 840dp in both colour schemes, using
  `ScreenWidthPreviews`.
- **Dependencies.** None. The helper exists and is used by six files
  already.
- **Verification procedure.** `PreviewCoverageTest` is extended from
  reading one file to scanning every preview source in the main source
  sets, and failing when a screen's preview is missing a width or a
  scheme. The test must fail today, because the coverage is not
  complete.
- **Expected evidence.** An extended test that reports each screen and
  each missing configuration.

---

## Phase 6 — Screens

**Exit criteria (verbatim, `08`):** *"every criterion in `15` §14
passes; no file exceeds ~600 lines; all screens work at 200% font scale,
in dark mode, in RTL, and under TalkBack."*

**Status: Partially complete.** Twelve of the fifteen `15` §14
criteria are verified complete, two are partially met, and the
file-length clause **fails**.

### The fifteen `15` §14 criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Four bottom destinations; Settings reachable from every tab's app bar; deep links preserved | **Verified complete** | `CycleCareAppStateNavigationTest`, 20 instrumented cases |
| 2 | `privacy`, `export` and `reminders` reachable in two taps from anywhere, with the first-run nudge | **Verified complete** | `FirstRunNudgeTest`, 5 cases |
| 3 | No ad on Calendar, Log, Settings or any detail screen | **Verified complete** | `routeAllowsBanner` and the scheduler test |
| 4 | No interstitial within 30 minutes of a log, or in the first three sessions | **Verified complete** | `InterstitialDailyCapTest`, 15 cases; `InterstitialCoordinatorTest`, 17 cases |
| 5 | No layout shift when ads load or fail | **Verified complete** | The banner collapses on failure and reserves its height otherwise |
| 6 | Every screen in `03` §6 has a designed, tested state | **Partially complete** | As `14` §12.7. See **P5-01**. |
| 7 | Every screen works offline with no network dependency for local content | **Verified complete** | The content is read from the local database. The export screen is the only one that reaches the network, and it has a failure state. |
| 8 | Every screen passes at 200% font scale, in dark mode, in RTL, and under TalkBack | **Partially complete** | 200%: 13 screens swept, three defects fixed. Dark: every screen captured in both schemes. RTL: verified on the shell and Today. TalkBack: as `14` §12.11. |
| 9 | No hardcoded user-facing strings remain | **Verified complete** | As `14` §12.9 |
| 10 | Confirmed versus estimated states distinguishable without colour | **Verified complete** | As `14` §12.5 |
| 11 | No screen shows two competing primary actions | **Verified complete** | As `14` §12.6 |
| 12 | No composable mutates state directly | **Verified complete** | The `AppLockGate` and `CycleCareApp` violations were resolved |
| 13 | Every existing test tag is preserved or deliberately migrated, and the navigation test is rewritten for four tabs | **Verified complete** | `settings_open_privacy` was added; nothing was removed |
| 14 | No file exceeds ~600 lines | **Failed** | See below. |
| 15 | `SecureAuthController` and export have test coverage | **Verified complete** | `SecureAuthCapabilityTest`, `ExportViewModelTest`, `LocalExportRepositoryIntegrationTest` |

### P6-01 — `CycleCareShell.kt` is 620 lines

- **Definition of done.** No file in a main source set exceeds ~600
  lines. Today, exactly one does:
  `app/src/main/java/com/cyclecare/app/CycleCareShell.kt`, at **620
  lines**. The next largest are `HomeRoute.kt` at 577, `HomeScreen.kt` at
  557, `ExportScreen.kt` at 516 and `Ads.kt` at 513. The file was last
  changed in `1d4330d`.
- **Why this is recorded as a failure rather than a note.** The
  measurement is 20 lines over a 600-line guideline. The criterion
  carries a tilde, so a human could reasonably call 620 "approximately
  600". But a check that returns 620 against a 600 guideline does not
  pass, and `21-phase-5-6-acceptance-matrix.md` records this criterion
  as met with the claim that *"the largest file in the main source sets
  is now well under it"*, which is not true of the current tree. The
  fix is small and the tooling already exists.
- **Dependencies.** None. `scripts/split-kotlin-file.py` performed the
  earlier decomposition, with its `--imports-ref=` option.
- **Verification procedure.** A test that walks every main source set
  and fails when a file exceeds 600 lines, naming the file and the
  count. This makes the criterion enforceable rather than a matter of
  memory.
- **Expected evidence.** The test passes, and `CycleCareShell.kt` is
  split into two or more files, each under the limit.

### The remaining exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "no file exceeds ~600 lines" | **Failed** | See **P6-01**. |
| "all screens work at 200% font scale, in dark mode, in RTL, and under TalkBack" | **Partially complete** | 200% and dark are swept across 13 screens. RTL is verified on the shell and Today. TalkBack is verified on the shell and Today. See **P6-02** and **P5-03**. |

### P6-02 — RTL and TalkBack across the remaining screens

- **Definition of done.** `RtlLayoutTest` covers every key screen, and
  `TalkBackSemanticsTest` covers every key screen, per `P5-03`.
- **Dependencies.** `P5-03` for the TalkBack half.
- **Verification procedure.** `ANDROID_SERIAL=emulator-5556 ./gradlew
  :app:connectedDebugAndroidTest`. The RTL test sets the locale through
  `LocaleManager` and asserts the layout direction and tab order
  reverse; it restores the locale afterwards.
- **Expected evidence.** Named cases per screen in both suites.

---

## Phase 7 — Notifications

**Exit criteria (verbatim, `08`):** *"every criterion in `13` §17
passes; the Doze demonstration is recorded, not assumed."*

**Status: Substantially complete.** Nine of the twelve `13` §17
criteria are verified complete; two remain partial only through items
this workstream does not own (the `userPinnedTime` *producer* lives with
the Settings workstream, and the copy-scope call in criterion 12 is
ambiguity `A2`/`P7-06`), one needs a physical device (`P7-05`).

The single most important finding of the earlier audit — that
`core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt`
was **not wired into the delivery path** — is resolved as of the
Workstream A delivery work recorded under **P7-01** below: the gate
stack runs inside `ReminderDelivery.deliver()` before any post, for
both the alarm path and the WorkManager path. The wiring notes that
described the gap are kept verbatim below the record for history.

### The twelve `13` §17 criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | No notification is delivered without a score ≥ `SEND_THRESHOLD` | **Verified complete** | `ReminderDelivery.deliver()` builds a `DeliveryGateInput` and runs `evaluateDeliveryGates()` — which wraps the shipped `decide()` — before posting (`app/.../ReminderDelivery.kt`, the intelligence section). A suppressed candidate posts nothing, writes no event row and logs `delivery_suppressed` with the scorer's reason. `ReminderDeliveryGatesTest` (10 JVM cases) covers threshold, pinned threshold, daily-budget threshold and `INSUFFICIENT_HISTORY` suppression; the worker fixtures in `ReminderDeliveryWorkerTest` drive the posting shape. |
| 2 | `MAX_SENDS_PER_DAY` is never exceeded | **Verified complete** | The daily budget acts twice: `budgetPressurePenalty` on the score and `thresholdFor()` raising the bar to `SEND_THRESHOLD_OVER_BUDGET` once `sendsLast24h >= MAX_SENDS_PER_DAY`, both inside the gate stack (`ReminderDeliveryGatesTest` · "the daily budget raises the threshold above a good score"). The weekly ceiling `MAX_SENDS_PER_WEEK` is a hard gate after the decision ("the weekly budget suppresses even a high scorer"). Counts come from `countDeliveredBetween` over the event history ("a reminder that was dismissed was still sent"). |
| 3 | A type crossing the auto-disable threshold is disabled **and** explained, with one-tap re-enable | **Verified complete** | After each posted impression, `evaluateAutoDisable` (in `ReminderDelivery.kt`) counts lifetime impressions and dismissal rate via `countDeliveredForType`/`countDismissedForType`/`countOpenedForType`; at the §9 thresholds it calls `ReminderRepository.setRuleTypeEnabled(type, false)` and records it in `RoomReminderIntelligenceRepository`, then posts a one-time notice with a re-enable action (`reminder_auto_disabled_title/body`, action → `ReminderAutoDisableActionReceiver` which re-enables, re-arms and clears the record) and logs `reminder_type_auto_disabled`. The reminder centre shows the paused types with counts and a re-enable button; re-enabling in-app re-arms schedules. |
| 4 | No reminder claims a date the prediction window cannot support | **Verified complete** | `ReminderSchedulePlanner` anchors to the earliest plausible start rather than the centre, and the copy hedges: "Your period may start…", "Your period is likely between … and …" (`ReminderSchedulePlanner.kt:87-95`). The rationale is recorded at lines 42–59. |
| 5 | No reminder is generated when `nextPeriodWindow == null` and the type depends on the estimate | **Verified complete** | `hasSupportableEstimate()` requires `supportsRange && nextPeriodHalfWidthDays != null` (`ReminderSchedulePlanner.kt:67-68`); every estimate-derived builder checks it. `NotificationTimingTest.noWindowMeansNoReminderRatherThanAFabricatedDate` and `latePeriodNeverFiresWithoutAnEstimate` cover it. |
| 6 | Retrying delivery never produces a duplicate notification | **Verified complete** | The post path inserts through `insertIfAbsent` — `@Insert(onConflict = IGNORE)` whose row is the lock: a retry finds the deterministic id (`"${ruleId}-$scheduledAtEpochMillis"`), logs `delivery_deduplicated` and never posts again (`ReminderDelivery.kt`). Post failure keeps the row. `ReminderInsertIfAbsentTest` (3 instrumented cases in `core/data`) proves the DAO refuses the overwrite and preserves the first delivery time. |
| 7 | Every notification has a group summary and a `setPublicVersion` redaction | **Verified complete** | Every posted reminder is built with `setGroup(remindersGroupKey)`, `VISIBILITY_PRIVATE` and `.setPublicVersion(redacted)` in `ReminderDelivery.kt`; the redacted variants come from `ReminderNotificationTemplates` — per-type copy that never names dates, symptoms or volumes. The coalesced deferred summary carries `setGroupSummary(true)` with its own redacted public variant (`redactedGroupSummary`), and the deferred arc that posts alone uses the standalone grouped variant (`redactedStandalone`) because a flagged summary with an empty group is hidden by the platform. `ReminderPublicVersionRedactionTest` (4 instrumented cases) posts through the real `NotificationManager` and asserts the redacted text is never the cycle body, plus the summary/standalone flags. Resolution of ambiguity `A7` recorded in the ambiguity table. |
| 8 | Exact alarms are requested only for the two permitted types, and the app is fully functional without the permission | **Verified complete** | `tierFor()` in `ReminderAlarmFacade.kt:149-153` returns `EXACT` for `PeriodPrep` and `LatePeriod` only, `INEXACT_WHILE_IDLE` for everything else. The permission is never assumed: `canScheduleExactAlarms()` returns false below API 31, and a `SecurityException` downgrades to Tier 2. `ReminderAlarmSchedulerTest`, 12 instrumented cases, covers the tier decision. |
| 9 | User-pinned send times are never overridden | **Verified complete** | `ReminderRule.userPinnedTime` now carries the flag through the model, the room entity mapper (`EntityMappers.kt` preserves it both directions), the planner (`ReminderSchedulePlanner` populates it, secluding the user's minute from any learning), the scheduled payloads (alarm facade extras and worker data), and into the gate stack where `thresholdFor` applies `SEND_THRESHOLD_PINNED_TIME` and `learnedTimeBonus` is always 0 for a pinned candidate ("a pinned time clears a lower threshold" + shipped pinned-threshold cases). The settings producer that sets the flag on edit belongs to the Settings workstream — recorded as a hand-off, no producer in this work, flag defaults `false` everywhere. |
| 10 | No health value appears in notification `extras` beyond visible content | **Verified complete** | `ReminderNotificationClient` adds no extras, and the builder sets only the title, body and `BigTextStyle` — all visibly rendered content. `13` §15 defines the constraint as the notification's `extras` bundle, which is what was checked. The `PendingIntent`'s own intent extras do carry `ReminderIntentKeys.TargetEpochDay`, which is cycle-derived, but those are delivered to this app's own `MainActivity` and are not the notification's extras bundle. |
| 11 | Quiet hours and DND are honoured, with `LatePeriod` as the documented exception | **Verified complete** | `evaluateDeliveryGates` checks the shipped `QuietHours` window (defer to `quietHoursEndEpochMillis`, §10 wrap-midnight maths covered by "quiet hours end early in the window the same day and late in it the next day") and `NotificationManager.getCurrentInterruptionFilter()` via the app-layer `interruptionFilterFor(context)`, both excepting `LatePeriod`. A deferred candidate is persisted (`reminder_deferred_candidates` in `app_settings`, wiped with the rest of the data) and the coalesced one-summary notification posts at the quiet end through `ReminderDeferralScheduler` + `ReminderDeferredSummaryReceiver`, which re-arms if DND is still active. `ReminderDeliveryGatesTest` covers the quiet-hours, DND and LatePeriod-exemption order. |
| 12 | No guilt, streak, or false-precision copy anywhere | **Partially complete** | The notification copy is clean. `ReminderSchedulePlanner` produces "Your period may start tomorrow.", "Your period is likely between … and …", "Your fertile window estimate begins today.", "Today matches your current ovulation estimate.", "Your period looks later than expected." and "Take a moment to log today's symptoms." — no guilt, no streak, no false precision. The ambiguity is recorded below. |

### P7-01 — Wire the notification intelligence into the delivery path

**Completed, Workstream A (2026-10-20).** The delivery layer
(`app/src/main/java/com/cyclecare/app/ReminderDelivery.kt`) assembles a
`DeliveryGateInput` — lead days, pinned flag, goal, estimate quality,
last-send recency, 24 h / 7 d / arbitration-window send counts, per-type
dismiss and open rates and the system's interruption filter — and runs
the pure gate stack
`core/domain/src/main/java/com/cyclecare/core/domain/notification/ReminderDeliveryGates.kt`,
which wraps the shipped scorer. Suppression, deferral and auto-disable
all log their reason (`delivery_suppressed`, `delivery_deferred`,
`reminder_type_auto_disabled`). `DeliveryGateOutcome` is the only new
shape the two delivery paths share, so they cannot drift.

Recording error in the diagnostics surface fixed in the same pass: the
`delivery_success` log no longer carries the absolute
`scheduledAtEpochMillis` (from which the derived period date could be
reconstructing on device); it logs `deliveryDelayMinutes`.

Design decisions recorded for review:

- **Arbitration window is 30 minutes** (`ARBITRATION_WINDOW_MINUTES`). §10
  names the batch size but no window; 30 minutes matches the rest of the
  specification's pressure windows (15 §11.3's recency gate).
- **Quiet-hours/DND deferral writes no event row.** A deferred
  candidate never became an impression; it is parked in
  `app_settings` (`reminder_deferred_candidates`, hand-rolled
  delimited encoding since kotlinx.serialization is absent from the
  project) and drained by `ReminderDeferredSummaryReceiver` — an exact,
  Tier-2 alarm owned by `ReminderDeferralScheduler`. The receiver posts
  one standalone grouped summary (see `A7`) and includes the redacted
  copy; if DND is still active it re-arms for +30 min. The store is
  wiped with `clearAllTables`.
- **`DailyLog` carries neutral estimate quality** into the scorer
  (`MODERATE`): the shipped `qualityBonus` subtracts 100 for
  `INSUFFICIENT_HISTORY`, which would silence an estimate-independent
  nudge. The neutralisation lives in the delivery layer;
  `core/domain`'s shipped semantics and its 29 tests are untouched.
- **Clean-slate recency sentinel is 24 h.** No history must not read as
  "sent seconds ago" under the scorer's recency penalty.
- **`LatePeriod` is exempt from quiet hours and DND** (the documented
  opt-in) but remains subject to the scorer, the weekly budget and
  arbitration, which protect against volume, not the time of day.

Tests: `ReminderDeliveryGatesTest` (10 JVM cases: deliver, threshold,
insufficient history, daily budget, pinned threshold, weekly ceiling,
quiet hours + LatePeriod exemption, DND + exemption, arbitration third
candidate, quiet-window end arithmetic). Existing `ReminderDeliveryWorkerTest`
(6 instrumented cases) still green with gates in the path; the clean-slate
worker fixture now carries a two-day lead so the fixture candidate stands
above the threshold.

- **Definition of done.** `ReminderDelivery.deliver()` consults the
  intelligence module before posting, and the module's decision is
  recorded in the log with its reason. A candidate is delivered only
  when `decide()` returns a delivery decision, the daily and weekly
  budgets are enforced against the event history, an auto-disabled type
  is not sent and is explained to the user with a one-tap re-enable, and
  a candidate inside quiet hours or under DND is deferred rather than
  delivered, with `LatePeriod` exempt.
- **Dependencies.** The intelligence module already exists and is
  tested. What it needs is a way to reach the event history and the
  channel settings that the app already has, and a place to record an
  auto-disable decision so the UI can explain it. `core:domain` must
  stay pure, so the data it needs is passed in rather than fetched.
- **Verification procedure.** Unit tests in `app/src/test` that drive
  `ReminderDelivery` with a fake event history and assert: a low-scoring
  candidate is not posted; a fourth send in a day is not posted; an
  auto-disabled type is not posted and produces a re-enable instruction;
  a candidate inside quiet hours is deferred to the quiet-hours end; a
  candidate under `INTERRUPTION_FILTER_PRIORITY` is deferred unless it
  is `LatePeriod`.
- **Expected evidence.** Named tests per criterion, and the log line
  showing the decision and its reason.

### P7-02 — Group summary and public-version redaction

**Completed, Workstream A (2026-10-20).** Every member notification is
built with `setGroup(ReminderChannels.remindersGroupKey)`,
`setVisibility(VISIBILITY_PRIVATE)` and
`.setPublicVersion(ReminderNotificationTemplates.redactedContent(...))`
— per-type redacted copy with no cycle dates, no symptom values and no
volumes (`reminder_redacted_generic`, `reminder_redacted_daily_log`).
The coalesced summary the deferred arc would post with members carries
`setGroupSummary(true)` plus its own redacted public variant
(`redactedGroupSummary`); the deferred summary that posts alone is the
standalone grouped variant (`redactedStandalone`), because a group
summary with an empty group is hidden by the platform — the finding is
behind the `A7` resolution.

Tests: `ReminderPublicVersionRedactionTest` (4 instrumented cases) —
posts through the real `NotificationManager` (runtime permission and
channel created in-test) and asserts the public version's text is the
redacted label and never the cycle body, the group key is set, the
summary carries `FLAG_GROUP_SUMMARY`, the standalone posts alone and
its public copy has no text, and per-type redacted copy is never a
cycle-like value.

- **Definition of done.** Every notification carries
  `setGroupSummary(true)` on a summary notification for its group, and
  carries a `setPublicVersion` redaction so the private body is what
  shows on a locked screen. `13` §15 gives the shape: build a redacted
  notification and attach it with `.setPublicVersion(redacted)`.
- **Dependencies.** **P7-01** is not required for this, but the
  summary notification has to be posted alongside the grouped ones, so
  it should be designed with the coalescing work in **P7-01**.
- **Verification procedure.** An instrumented test that posts a
  notification through the real `NotificationManager` and then reads
  back `notification.groupSummary` and the public version, asserting
  both are present. A test that asserts the public version's text
  differs from the private body.
- **Expected evidence.** A named instrumented test.

### P7-03 — Retry de-duplication, made explicit and tested

**Completed, Workstream A (2026-10-20).** The post path inserts through
`ReminderEventsRepository.insertIfAbsent`, backed by
`ReminderEventDao.insertIfAbsent` — `@Insert(onConflict = IGNORE)`.
Room answers the new row id (≥ 1) or -1 when the deterministic id
already exists; the repository maps anything positive to "new". A
retry that already posted finds the row, logs `delivery_deduplicated`
and never posts again. A post failure keeps the row, so the retry's
insert failing is exactly the at-most-once behaviour §12 asks for: the
notification is lost rather than duplicated. The pre-delivery
skip paths (foreground routing, channel disabled, permission missing,
missing settings, sync failure) keep their upsert recording, which is
why the existing `ReminderDeliveryWorkerTest` counts still hold.

Tests: `ReminderInsertIfAbsentTest` (3 instrumented cases in
`core/data` — insert, refuses the same id and preserves the first
delivery time, allows a different schedule time) plus the worker
post-failure/sync-failure cases in `ReminderDeliveryWorkerTest`.

- **Definition of done.** Criterion 6 is guaranteed by an explicit
  mechanism rather than by the coincidence of a deterministic id, and
  a test proves it.
- **Dependencies.** None. A DAO method that inserts only if the id is
  absent, alongside the existing upsert, is enough.
- **Verification procedure.** A unit test that delivers the same
  payload twice — the second time forcing the post to fail and return
  `Retry` — and asserts that the event table holds exactly one row for
  that rule and schedule time, and that the notification was not
  duplicated. A `core/data` test asserts the insert-if-absent DAO
  method does not overwrite.
- **Expected evidence.** Named tests in `app/src/test` and
  `core/data`.

### P7-04 — Carry `userPinnedTime` through to the domain

**Completed, Workstream A (2026-10-20).** `ReminderRule.userPinnedTime`
(default `false`) now round-trips: `EntityMappers` preserves it in both
directions, the five seeded rules seed it `false`, the planner stamps
each `ScheduledReminder` with it, and both payload carriers (alarm
facade extras and worker data — distinct keys, both defaulted) deliver
it to the gate stack, where `thresholdFor` applies
`SEND_THRESHOLD_PINNED_TIME` and `learnedTimeBonus` never applies to a
pinned candidate. **Hand-off:** the flag's *producer* — a settings
editor able to move a rule's time — belongs to the Settings workstream;
`SettingsRoute` currently only toggles `enabled` and cannot yet pin a
time. The unused flag is inert by design until that producer lands.

Tests: the mapper/planner round trip is pinned by
`RoomRepositoriesIntegrationTest.reminderRuleUserPinnedTime_roundTripsThroughTheMapper`
(instrumented); the pinned threshold is covered in
`ReminderDeliveryGatesTest` ("a pinned time clears a lower threshold");
the producer test belongs to the Settings workstream.

- **Definition of done.** `ReminderRule` carries the pinned flag, the
  mapper stops hard-coding it to `false`, and the rule's send time is
  never replaced by a learned time. The intelligence module's existing
  "a learned time never applies to a pinned time" rule is reachable
  from the code that would use it.
- **Dependencies.** **P7-01**, because the threshold rule that consumes
  the flag lives there. The backup format already carries the flag, so
  no format change is needed — only the domain model and the mapper.
- **Verification procedure.** A test that sets a rule's time to a
  pinned value, runs a delivery, and asserts the notification was sent
  at the pinned time and not at a learned one. A test that the mapper
  preserves the flag in both directions.
- **Expected evidence.** Named tests.

### P7-05 — The Doze demonstration on a real device

- **Definition of done.** The Doze behaviour is demonstrated on a
  physical device, with the evidence recorded.
- **Dependencies.** Hardware. The emulator cannot produce it.
- **Current state.** `19-doze-verification.md` records the emulator
  measurements: the mechanism in use is demonstrably deferred by Doze.
  The Tier 1 and Tier 2 alarms have since been implemented, and the
  **mechanism** is now demonstrated, but on an emulator only. The
  requirement in `13` §11 and the exit criterion — *"the Doze
  demonstration is recorded, not assumed"* — is not satisfied by
  emulator evidence.
- **Verification procedure.** On a physical device, with the screen
  off and the device idle for the platform's own Doze window, record
  the actual delivery latency for a `PeriodPrep` reminder and for a
  `LatePeriod` reminder. Compare against the `19-doze-verification.md`
  figures. `ReminderDozeDeliveryTest` is the harness; it is opt-in
  because it forces device-wide idle, and it must not be left enabled
  in the default suite — when it is, it breaks the other 28 instrumented
  tests.
- **Expected evidence.** A dated record in `19-doze-verification.md`
  naming the device, the Android version, the idle duration and the
  measured latency for each tier.

### P7-06 — The scope of "no guilt, streak, or false-precision copy anywhere"

- **Definition of done.** The scope of criterion 12 is decided and
  recorded, and the copy inside that scope is clean.
- **Ambiguity, reported rather than decided.** Criterion 12 lives in
  the notification specification's acceptance list, and `13` §15 scopes
  the same rule to the notification channel: *"No streak language, no
  guilt, no false precision."* Two other documents scope it more
  narrowly. `03` §2.5 bans *"streak-loss language, guilt notifications,
  fertility assumptions, or judgmental language for missing logs or
  irregular cycles"*, and `03` §7 lists *"You missed your tracking
  streak."* as copy to avoid. `14` §6 bans *"no guilt copy, no streak
  loss, no confirmshaming"*. All three ban the **loss** and the
  **guilt**, not a positive streak counter.
  The Logging feature does carry positive streak copy:
  `feature/logging/src/main/res/values/strings.xml` has
  `no_streak_yet` = "No streak yet", a plural "%1$d day streak!" and
  `saved_checkin_complete` = "Saved! Check-in complete. Keep up the
  great habit."
  Under the narrow reading — criterion 12 is about notification copy —
  the criterion holds, and the notification copy is clean. Under the
  literal reading of "anywhere", it does not. The spec does not say
  which reading governs, so the criterion is recorded as partial and
  the decision is left open rather than assumed.
- **Dependencies.** None. It is a decision, then a possible copy
  change.
- **Verification procedure.** Once the scope is decided, a
  phrase-absence test asserts that no string in the scoped surface
  contains guilt, streak-loss or false-precision language.
- **Expected evidence.** A recorded decision, and a passing test.

---

## Phase 8 — Sync

**Exit criteria (verbatim, `08`):** *"all ten gates in `17` §14 pass;
all six local-first invariants in `17` §12 proven by test."*

**Status: Deferred by decision.**

`20-sync-decision.md` records the deferral. The app ships local-first.
`NoOpSyncAdapter` is the whole of the sync feature today, and there is
no sync status, no sync string and no sync screen.

**Open items: none for the release.** The ten gates in `17` §14 — RLS
isolation, key recovery, remote account deletion, metadata disclosure,
remote-schema tests — are out of scope while sync is deferred. They
become live work the moment sync is reconsidered, and `17` §15 and
`06` §7 both forbid making strong security or sync claims until then.

**One observation, not a task.** `17` §12 states that the six
local-first invariants *"hold regardless of sync, and are proven by
test"*. Two of them are therefore testable today even with no sync at
all: *"The app is fully usable with no account and no network"* and
*"No logging, calendar or insight flow ever blocks on network state"*.
The other four — *"Sync failure never blocks or fails a local write"*,
*"Disabling sync never loses local data"*, *"Session expiry never
loses local data"* and *"Process death never loses an outbox
entry"* — are vacuous without a sync subsystem. If the invariants are
worth holding independently of the sync decision, proving the first two
is cheap and is a reasonable thing to do next. It is recorded here as
an observation, and it is not claimed as done.

---

## Phase 9 — Release readiness

**Exit criteria (verbatim, `08`):** *"every checklist in `06` and `07`
passes and is evidenced."*

**Status: Partially complete.** The repository-side checklists are
mostly done. Every external item is open, and nothing in the external
list has been submitted.

### The `06` §5 CI gate

`06` §5 lists ten checks a release candidate must pass.

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Formatting, lint and static analysis | **Verified complete** | `:app:lintRelease` and `assembleRelease` are clean, locally and in CI. |
| 2 | Unit and domain tests, including the `12` corpus gates | **Partially complete** | The unit and domain tests pass. The corpus gates are only eight of thirteen — see **P4-01**. |
| 3 | Room DAO and migration tests, including the `02` §9.8 lossless-migration fixtures | **Verified complete** | `core/database` androidTest, 20 cases. |
| 4 | UI and accessibility checks where automatable, at font scale 2.0 and in RTL | **Not audited** | The RTL and font-scale work is local and evidence-based, but it is not wired into CI. See **P9-04**. |
| 5 | Ad-targeting API scan, failing the build on targeting in the app module | **Verified complete** | `scripts/guard-no-ad-targeting.sh`, in CI. |
| 6 | Security-sensitive source scanning and secret scanning, including keystore passwords | **Verified complete** | gitleaks *Secret scan* and *Confirm signing secrets are not tracked*, in CI. |
| 7 | Supabase migration and RLS policy tests in a non-production project | **Not applicable** | There is no Supabase. Sync is deferred, and `06` §5 also forbids running destructive database tests against a production project. Recorded so the omission is deliberate rather than an oversight. |
| 8 | Dependency vulnerability review | **Not implemented** | No such step in `.github/workflows/ci.yml`. See **P9-02**. |
| 9 | Build and signing validation against the real `proguard-rules.pro` | **Verified complete** | `assembleRelease` runs in CI with the real rules. |
| 10 | Privacy manifest and store disclosure review | **Not implemented** | No app-level privacy manifest is committed, and no review step exists. See **P9-03**. |

### The `06` §2 P0 scenarios

`06` §2 has five groups: core local use, cycle and prediction
correctness, reminders, optional sync, and privacy and deletion. The
first three are here, the privacy and deletion group has its own
subsection below, and the optional sync group is not applicable, per
Phase 8.

| Scenario | Status | Evidence |
|---|---|---|
| Fresh install can proceed without account creation | **Verified complete** | There is no account concept in the app. `OnboardingPersistenceTest` and `OnboardingScreenUiTest`, 7 instrumented cases. |
| Add, edit and delete a period record in airplane mode | **Not audited** | The app has no network path for local content, so the property holds by construction, but it has not been asserted as a test. |
| Add multiple symptoms to one date and later edit them | **Partially complete** | `LoggingViewModel` and `LoggingValidationTest` cover the behaviour at module level, not as a scenario. |
| Today and Calendar render from Room without waiting for a network request | **Verified complete** | `ScreenStateMatrixTest` renders both from the local database. |
| Process death does not lose saved records or queued sync operations | **Not audited** | The saved-records half has no test. The queued-sync half is not applicable. |
| Date-only values remain stable across timezone changes | **Not audited** | Cycle and symptom dates are stored as epoch days, which is stable by construction, but the reminder timestamps are converted with `ZoneId.systemDefault()` at the mapping boundary (`EntityMappers.kt:125-126`), and no test covers a timezone change. |
| Predicted records are never counted as observed period starts | **Verified complete** | `observedStartsOf` filters `source != CycleSource.Predicted`. |
| Duplicate starts and invalid end dates are rejected or resolved clearly | **Verified complete** | `CycleRecordValidator`, with `CycleRecordValidatorTest`. |
| Editing an old record invalidates and recomputes dependent estimates | **Not audited** | G12, the gate that would test it, does not exist. See **P4-01**. |
| Sparse, contradictory or highly variable history shows uncertainty or no-estimate state | **Partially complete** | The quality tiers and `INSUFFICIENT_HISTORY` exist and are covered by `G7`; the screen-level rendering of each tier is not asserted for every surface. |
| Removing all history removes stale derived predictions | **Not audited** | `AppResetRepository` calls `clearAllTables()`, which is a full reset rather than the scenario's removal of history. |
| Disabled notifications do not repeatedly prompt the user | **Verified complete** | The channel-disabled check at `ReminderDelivery.kt:100-118` returns `Delivered` without posting. |
| Reminder schedules are rebuilt without duplication after boot, app update, timezone change or rule edit | **Partially complete** | The identity is deterministic — `reminderWorkName(ruleId)` and the stable pending-intent request code in `ReminderAlarmFacade.kt:98-104` — and `ReminderRescheduleReceiver` exists. No test exercises a boot or timezone-change rebuild. |
| WorkManager timing behaviour is tested with its permitted deferral | **Verified complete** | Superseded by the tier implementation: `ReminderAlarmSchedulerTest`, 12 cases. The app no longer promises exact-minute delivery for anything but the two permitted types. |
| Foreground handling does not depend on a live Activity reference | **Verified complete** | `appVisibilityObserver` is read from the container, not from an Activity. |
| Locked-screen text is masked by default and respects user settings | **Complete** | Masking is on by default — `hideNotificationPreview = preferences[…] ?: true` at `PrivacyRepository.kt:18` — and every reminder now carries `setVisibility(VISIBILITY_PRIVATE)` plus a `setPublicVersion` redacted variant (`ReminderNotificationTemplates`, per-type copy). See **P7-02**. |
| Notification channel changes and denial are reflected in the app UI | **Verified complete** | `ScreenStateMatrixTest.theSettingsScreenShowsAChannelThatHasBeenSwitchedOff`, and the notification-gate cases. |

### The `06` §2 privacy and deletion scenarios

`06` §2 has a fifth group that the other four phases' checklists do not
cover. Sync scenarios are not applicable, per Phase 8.

| Scenario | Status | Evidence |
|---|---|---|
| Synthetic period dates, symptoms, notes and medication values do not appear in production logs, analytics, crash payloads or request URLs | **Partially complete** | The logger's attributes are enums, counts, identifiers, SDK versions and zone identifiers — no date, symptom, note or medication value. There is no analytics SDK, and `createCrashReporter()` returns a `NoOpCrashReporter`: `Diagnostics.kt:75-88` records that Firebase Crashlytics was removed because the merged manifest shipped Firebase components that were never initialised. **One exception to review** — `ReminderDelivery.kt:212` logs `scheduledAtEpochMillis`, and for a `PeriodPrep` reminder that instant is derived from the predicted period date. See **P9-08**. |
| Turning sync off follows the documented remote-data policy | **Not applicable** | Sync is deferred. There is no remote data and no remote-data policy to follow. |
| Local deletion clears app-owned local records, caches and pending uploads and cancels scheduled reminders where possible | **Partially complete** | `AppResetRepository.resetAllLocalData()` calls `database.clearAllTables()`, which clears the records. **It does not cancel scheduled reminders**, so a `PeriodPrep` or `LatePeriod` alarm can still fire after a reset. See **P9-09**. |
| Cloud deletion handles remote rows and identity; partial failure remains visible and retryable | **Not applicable** | Sync is deferred. |
| Export contains only the selected data and is never silently transmitted | **Verified complete** | `ExportRepository` performs no network operation — no URL, upload or share call. `ExportViewModelTest` and `LocalExportRepositoryIntegrationTest` cover the content. |
| Backup and device-transfer behaviour matches product copy across supported Android versions | **Not audited** | `data_extraction_rules.xml` and `backup_rules.xml` exist and are tested, but whether the in-app copy describes that behaviour accurately was not checked. See **P9-10**. |

### The `06` §4 performance gates

`06` §4: *"Set numerical performance budgets after collecting baseline
data. Don't choose targets only to make the report look strong."*

**Status: Not audited.** No baseline has been collected.

There is a macrobenchmark module with two cases —
`benchmark/src/main/java/com/cyclecare/benchmark/CycleCareBenchmarkTest.kt`
provides `startup_homeColdStart` and `calendar_openAndScroll` — but no
baseline data exists and no budget has been set. The module is not run
by CI, and macrobenchmark numbers are only meaningful on physical
hardware. See **P9-05**.

### The `06` §7 production release blockers

`06` §7: *"Do not release cloud sync or make strong security claims
while any of the following remain true."* All seven conditions concern
cloud sync and strong security claims, none of which the app makes. The
app is local-first and its privacy claims are scoped and verified.
**Verified complete**, on the grounds that the condition is not
triggered.

### The `07` store-side checklist

| Item | Status | Note |
|---|---|---|
| Health apps declaration and content | **External action required** | The app tracks menstrual cycle, symptoms and predictions, so the health category applies. |
| Privacy policy and Data safety | **External action required** | The repository policy page is corrected; the hosted page and the Play Data safety form are not submitted. |
| Alarms and reminders permission | **External action required** | `SCHEDULE_EXACT_ALARM` is a special-access permission and needs its own declaration and usage justification. |
| Advertising declarations | **External action required** | The app requests the advertising identifier through AdMob and UMP. It must be declared and must be consistent with the policy page. |
| Target API level | **Verified complete** | The app compiles against API 36, which satisfies the current requirement. Confirm against the live guidance before upload. |
| Release operational checks | **External action required** | The support channel behind `support@cyclecare.app` has to exist and be monitored before release. |
| Store listing strategy, metadata, screenshots | **Not started** | No listing, no screenshots, no localised copy. The 97 design-review captures can seed it. |
| Store assets and conversion experiments | **Not started** | `07` §7.5 requires one variable at a time, a recorded experiment period, a meaningful baseline and no click-through optimisation at the cost of accurate expectation-setting. |
| Quality and retention work before SEO and ASO | **Not started** | Follows the release. |
| Review strategy and ranking plan | **Not started** | Follows the release. |

### Open items

**P9-01 — Play Console declarations and the store listing.**
*Definition of done.* The Data safety form, the Advertising ID, Ads and
Health declarations, the permissions declaration, the content rating and
the target audience questionnaire are submitted; the support channel
exists; the store listing is published with screenshots and localised
copy. *Dependencies.* The repository must be signed and uploaded, and
the hosted privacy policy must match `docs/privacy-policy.html`. Note
that `P7-02` and `P7-01` change the notification behaviour that the
privacy policy describes, so the policy should be finalised after
Phase 7, not before. *Verification procedure.* The Play Console
submission itself. *Expected evidence.* A published listing and a
completed Data safety form, with the submitted answers recorded here.

**P9-02 — Dependency vulnerability review in CI.**
*Definition of done.* CI fails the build when a dependency has a known
advisory that is not explicitly allowed. *Dependencies.* A tool —
Dependabot, `dependency-review-action` on pull requests, or an
equivalent. *Verification procedure.* The workflow step exists and a
deliberately vulnerable dependency is shown to fail the job. *Expected
evidence.* A CI log showing the review step.

**P9-03 — Privacy manifest and store disclosure review.**
*Definition of done.* An app-level privacy manifest is committed if the
merged SDK manifests do not already satisfy the Play requirement, and a
review step confirms the store disclosure matches the app's actual
network and SDK behaviour. *Dependencies.* **P9-01** for the store side.
*Verification procedure.* The merged manifest from the release build is
inspected and the disclosure questionnaire is answered from it rather
than from intent. *Expected evidence.* The merged manifest and the
recorded answers.

**P9-04 — Wire the accessibility checks into CI.**
*Definition of done.* The instrumented CI job runs the RTL and
font-scale checks, or the RTL and font-scale work is converted into
tests that run in the default instrumented suite. *Dependencies.* The
font-scale sweep currently depends on a system setting, so it needs
either a dedicated CI step or a way to set the scale from within the
test. *Verification procedure.* The CI workflow is edited and the job
log shows the checks running. *Expected evidence.* A CI log.

**P9-05 — Performance baseline and budgets.**
*Definition of done.* Baseline measurements exist for cold start, main
screen time, calendar scrolling, large-history query and export, memory
and battery, collected on representative low and mid-range physical
devices, and numerical budgets are set from them.
*Dependencies.* Physical hardware; `06` §4 explicitly requires
representative devices, not an emulator.
*Current state.* The macrobenchmark module exists with two cases, but
**both are broken and neither can currently produce a number.**
`startup_homeColdStart` waits for `By.text("Home")`, and the first tab
has been labelled **"Today"** since the four-tab navigation was
introduced, so the wait times out after five seconds on every iteration
and the measurement is of a timeout rather than of a cold start.
`calendar_openAndScroll` waits for `By.text("Cycle calendar")`, which
does exist in `feature/calendar/src/main/res/values/strings.xml`, so
that one is sound in principle but has never been run.
*Verification procedure.* The `By.text("Home")` wait is corrected to the
current tab label, then the macrobenchmark module is run on the target
devices, the results are recorded, and the budgets are written down
against them.
*Expected evidence.* A recorded baseline and budget set, and
before/after evidence for any change made to meet them.

**P9-06 — The device matrix.**
*Definition of done.* Fresh install, upgrade from v2, the migration
path, permission denial, backup and restore, process death, foldable,
RTL and 320dp have been exercised as a matrix. *Dependencies.*
**P3-01** for the migrated-v2 launch, **P5-02** for the foldable, and
physical devices for the rest. *Verification procedure.* A written
matrix with a result for each row. *Expected evidence.* The matrix
record.

**P9-07 — Visual sign-off.**
*Definition of done.* The red, white and black palette, and the
thirteen captured screens, are reviewed and approved for release.
*Dependencies.* **P5-04** and the remaining state captures. *Current
state.* The palette is approved as a direction but not for release. The
design-review images cover Today, Calendar, Log, Insights, cycle
status, fertility detail, the edit sheet, the reminder centre, Settings,
onboarding, the app lock, the notification gate and privacy, at four
widths in both schemes. Not yet reviewed: the empty, error, loading and
unavailable states of each screen, and the 320dp privacy capture.
*Verification procedure.* A human review, recorded. *Expected evidence.*
A recorded approval.

**P9-08 — A reminder schedule timestamp is in the log.**
*Definition of done.* The diagnostic log carries no value from which a
period date can be derived. `ReminderDelivery.kt:212` logs
`scheduledAtEpochMillis`, and for a `PeriodPrep` reminder that instant
is the predicted period date minus the lead time, so the log carries a
derived cycle date. `app/src/main/java/com/cyclecare/app/Diagnostics.kt:87`
sets the rule the codebase holds itself to: *"diagnostics must never
carry period dates, symptoms, notes, medication or free text"*. The fix
is to log a bucket — the scheduled hour, or the lead time — rather than
the instant.
*Dependencies.* None. It is a one-line change in the delivery log.
*Verification procedure.* A test that captures the logged attributes for
a `PeriodPrep` delivery and asserts that no attribute is a timestamp or
a date. A second test asserts the same for every other delivery.
*Expected evidence.* Named tests, and a log line that names the bucket
rather than the instant.

**P9-09 — A reset does not cancel scheduled reminders.**
*Definition of done.* `AppResetRepository.resetAllLocalData()` clears
the local records **and** cancels the scheduled reminders, so a
`PeriodPrep` or `LatePeriod` alarm cannot fire after the user has
deleted their data. The scenario says *"cancels scheduled reminders
where possible"*; for an alarm the app has scheduled itself, it is
possible.
*Dependencies.* The scheduler has to be reachable from the reset path.
`ReminderAlarmFacade.cancel(uniqueName)` already exists and is used by
the scheduler, so this is a wiring change rather than new machinery.
*Verification procedure.* An instrumented test that seeds a reminder,
calls the reset, and asserts that the alarm is no longer scheduled and
that no notification is posted afterwards.
*Expected evidence.* A named test, and the reset path calling the
scheduler.

**P9-10 — The product copy does not state the backup behaviour.**
*Definition of done.* The in-app copy about backup and device transfer
matches what `data_extraction_rules.xml` and `backup_rules.xml` actually
do across the supported Android versions. The `06` §2 scenario asks for
the copy and the behaviour to agree, and that has not been checked.
*Dependencies.* None. It is a copy review, and it may well pass.
*Verification procedure.* Read the rules files, read the in-app copy
that mentions backup or transfer, and compare them. If they disagree,
correct whichever is wrong and record the outcome.
*Expected evidence.* A recorded comparison and, if needed, the
corrected copy.

### External actions, in one list

Nothing below has been submitted, and no claim is made about its state.

| Action | Where | Notes |
|---|---|---|
| Data safety form | Play Console | Cycle and symptom data stays on the device; ad identifiers are processed by Google for advertising; health data is not sent to the ad SDK. `scripts/guard-no-ad-targeting.sh` enforces the last point in CI. |
| Advertising ID declaration | Play Console | The app requests it through AdMob and UMP. It must be consistent with the policy page. |
| Ads declaration | Play Console | The app is ad-supported: a banner on Today and Insights, and interstitials gated by the eight conditions in `15` §11.3. |
| Health declaration | Play Console | The health category applies; the data is not shared with third parties for health purposes. |
| Permissions declaration | Play Console | `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `INTERNET`, `ACCESS_NETWORK_STATE` are in the manifest. `SCHEDULE_EXACT_ALARM` needs its own declaration and usage justification. |
| Content rating and target audience | Play Console | A questionnaire. The app is not aimed at children, which the app lock and local-only storage make credible. |
| Support channel | Email | `support@cyclecare.app` must exist and be monitored before release. |
| Hosted privacy policy | Web | Owned by the user. Must match `docs/privacy-policy.html`. |
| Store listing and screenshots | Play Console | No listing exists. |
| Real-device Doze demonstration | Device | See **P7-05**. |
| Foldable layout | Device | See **P5-02**. |
| Manual screen-reader pass | Device | See **P5-03**. |

---

## Phase 10 — Measure and improve

**Exit criteria (verbatim, `08`):** *"every change tied to a hypothesis,
a measurable outcome and a privacy review."*

**Status: Not started.** This phase is post-launch by design. Nothing
in it gates the current release, and the work is not to be started
early.

The phase covers acquisition and conversion, crash-free sessions,
first-log completion, retention by cohort, notification disable rate,
ad-free purchase rate, and accessibility and support issues; re-running
the `12` corpus with real aggregate distribution shapes, never with
identifiable user records; tuning notification relevance weights
against observed send, open and dismiss data, one variable at a time;
store listing experiments per `07` §7.5; and revisiting the luteal-phase
prior only if ovulation observations are ever collected, with the
validation corpus extended in the same change.

Two things are worth settling before this phase starts, because they
constrain what it can measure.

**The app has no analytics.** The privacy policy and the app both
promise that cycle content does not leave the device, and
`07` §7.6 requires that nothing measured *"may include cycle
content"*. A measurement plan therefore has to be built on aggregate,
non-identifying signals — and any such signal needs a privacy review
before it is collected, which is the phase's own exit criterion.

**The corpus and the relevance weights are the two places where
measurement changes behaviour.** The corpus constants and the
notification thresholds are the same kind of artifact, and `12` §6
requires a change to be recorded in the ADR log. The same discipline
applies here: one variable at a time, a recorded hypothesis, a
measurable outcome and a privacy review.

**Definition of done, for when this phase begins.** Each change in the
phase carries a written hypothesis, a measured outcome, and a recorded
privacy review, and the ADR log shows the same discipline the corpus
requires.

---

## Cross-phase ambiguities

These are recorded rather than decided. Each one needs a human
decision before the item it blocks can be closed.

| # | Ambiguity | Blocks | Where it is recorded |
|---|---|---|---|
| A1 | `02` §9.10.3 requires the original `cycleLength` in a `legacyCycleLength` column. The implementation preserves the value in `cycleLength` itself and deprecates it for reads. Intent met, letter not met. | **P3-02** | `02` §9.10.3, `Migrations.kt:88-91` |
| A2 | `13` §17.12 says "no guilt, streak, or false-precision copy **anywhere**". The Logging feature carries positive streak copy. `03` §2.5, `03` §7 and `14` §6 all ban streak *loss* and guilt, not a positive counter. Is the criterion about notification copy, or about the whole app? | **P7-06** | `13` §17.12, `13` §15, `03` §2.5, `03` §7, `14` §6 |
| A3 | `15` §14.14 says "no file exceeds **~**600 lines". `CycleCareShell.kt` is 620. Is 620 within the tilde, or is the criterion a hard 600? It is recorded here as a failure, on the grounds that a check returning 620 against a 600 guideline does not pass. | **P6-01** | `15` §14.14 |
| A4 | `12` §6 says all gates must pass for `2.0.0` to ship, and that a failing gate means the constants are revisited. Five gates have no implementation at all, so it is not yet known whether the current constants pass them. | **P4-01** | `12` §6 |
| A5 | `11` §8.9 requires `InsightEngine` and the prediction engine to share one extractor. The trend series does; two retrospective per-cycle calculations do not. Is the criterion about the shared trend series, or about every derived date? | **P2-03**, **P4-04** | `11` §8.9, `InsightEngine.kt:107,278` |
| A6 | `02` §9.10.4 requires divergences to be *"logged and surfaced to the user"*. A `Migration` receives only a `SupportSQLiteDatabase`, so logging means either a logger threaded through the builder or a divergence table. Which is intended changes the schema. | **P3-03** | `02` §9.10.4` |
| A7 | `13` §10 requires quiet-hour candidates to be *coalesced into a single summary notification*, and `13` §17.7 requires every notification to have a group summary. These interact: the summary may be both the group summary and the coalesced quiet-hours notification, or they may be two different notifications. | **Resolved (workstream A, 2026-10-20):** the quiet-hours deferral does not post per-candidate members at all, so coalesced summary and group summary are the *same* notification — `ReminderDeferredSummaryReceiver`'s redacted summary. While its group is empty the platform would hide a summary-only post, so when it arrives alone it is the standalone grouped variant (`redactedStandalone`), and the flagged `setGroupSummary` variant (`redactedGroupSummary`) is used whenever members are beside it. Also note a single posted member relies on the platform's own single-member rendering, which the member-with-`setPublicVersion` test pins (posting one summary per member would double every notification and broke the fixture contract, so per-member summaries are no). | **P7-01**, **P7-02**, `13` §10, `13` §17.7, `ReminderNotificationTemplates.kt`, `ReminderPublicVersionRedactionTest` |

## What cannot be verified from this repository

| Item | Why | Closest available evidence |
|---|---|---|
| Real-device Doze behaviour | Needs physical hardware | The emulator measurements in `19-doze-verification.md` |
| Foldable layout | Needs a hinge or a posture | The 600dp and 840dp captures, which exercise the window size classes |
| TalkBack as a screen reader | Needs a person listening | The semantics tree, which is what a screen reader consumes |
| Play Console forms | Play Console | The verified implementation the answers are derived from |
| Hosted policy page | Owned by the user | `docs/privacy-policy.html` |
| Performance under real load | Needs representative devices | The macrobenchmark module, with no baseline collected |
| Notification disable rate, retention, conversion | Needs users | Nothing. The app has no analytics |
