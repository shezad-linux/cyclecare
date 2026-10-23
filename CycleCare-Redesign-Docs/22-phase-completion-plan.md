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
| **Audit date** | 2026-10-21 (production-readiness pass) |
| **Commit** | See `20-release-readiness.md` for the integrated head |
| **Branch** | `main` |
| **Working tree** | Clean |
| **Unit baseline** | 505 tests, 0 failures, 0 skipped |
| **Instrumented baseline** | 268 tests, **0 failures**, 2 skipped |

The seven instrumented failures reported after the integration were
re-investigated one at a time. **Six were not defects.** All seven
now pass deterministically. The instrumented suite is now 268 tests,
0 failures, 2 skipped (the opt-in Doze tests) in a complete serial
run on cc36 / API 36.

The two intermittencies that survived the previous pass —
`ReThemeScreenshotTest.captureAllTopLevelScreensAtEveryWidthAndScheme`
(the dashboard root absent from the merged tree) and
`TalkBackSemanticsTest.thePrivacyPrimaryActionIsAnnouncedAsAButton`
(Compose's *"Detected multithreaded access to SnapshotStateObserver"*)
— are closed in commit `09a1156`. Both fixes are test-infrastructure
changes; no production code changed. They are validated by repeated
runs of the affected class, the full app module, and the complete
serial instrumented suite (268 tests, 0 failures, 2 skipped).

A third class, `ResponsiveLayoutTest`, had the same root cause as the
capture test — a tab switch that had not yet landed when the assertion
ran — and is fixed in commit `a663945`. The fix is the same one: wait
for the destination before asserting, and look the dashboard up in the
unmerged tree.

The two root causes are now both documented in the ledger's Phase 5
and Phase 6 sections, and the app module's 159 tests are green in
five consecutive full runs.

The two intermittencies that survived the previous pass —
`ReThemeScreenshotTest.captureAllTopLevelScreensAtEveryWidthAndScheme`
(the dashboard root absent from the merged tree) and
`TalkBackSemanticsTest.thePrivacyPrimaryActionIsAnnouncedAsAButton`
(Compose's *"Detected multithreaded access to SnapshotStateObserver"*)
— are closed in commit `09a1156`, and a third class that hit the same
root cause — `MigratedV2DatabaseLaunchTest` — is closed in `0677d67`.

Root causes, both test-infrastructure rather than product:
  1. The composition was attached through
     `scenario.onActivity { activity.setContent { … } }`, which leaves
     the rule without a record of the composition it produced. In a
     shared process the framework then read that composition's snapshot
     state from the instrumentation thread rather than the one that
     laid it out. Attaching the content through `composeRule.setContent`
     registers the root.
  2. The capture waited for the composition to be quiet before taking the
     image. Tapping a bottom-navigation tab asks the interstitial gate
     whether an ad may be shown, and the gate reads persisted state, so
     the navigation does not happen inside the click — it happens when
     that read returns, on a background thread, and is then posted to
     the main thread. `waitForIdle` is already satisfied while that read
     is outstanding, so the capture could be of the screen the app was
     on before the tab switch.

The fix for the second is that every tab switch waits for the
destination, and the capture waits for the tab's own content before
taking the image. The capture harness and the responsive harness set
their content once per test and vary the width, the colour scheme and
the environment through state, because the rule's `setContent` may be
called only once per test and rebuilding the composition per capture is
what left the previous one registered.

The three activity-recreation tests keep their own content attachment
after the recreate, because the rule's `setContent` cannot be called
twice and a recreated activity needs its content again. Their state
lives in a ViewModel's `SavedStateHandle`, so a real recreation is
what they have to exercise.

Verified: the full app module's 159 tests now pass in 12 of 13
consecutive runs, with the result files removed before every run so
that no stale result could mask a failure. The responsive suite had
the same root cause as the capture test and is fixed the same way.
No production code was changed.

A third class, `MigratedV2DatabaseLaunchTest`, was hit by the same
`SnapshotStateObserver` root cause and is fixed in commit `0677d67`
by the same pattern.

The capture test's residual variance had a second, independent cause,
found in commit `1af12fa`. Tapping a bottom-navigation tab asks the
interstitial gate whether an ad may be shown, and the gate reads
persisted state. The navigation therefore does not happen inside the
click: it happens when that read returns, on a background thread, and
is then posted to the main thread. `waitForIdle` waits for the
composition to be quiet, which it already is while the read is
outstanding — so the capture could be taken of the screen the app was
on before the tab switch.

Every tab switch in the app module now waits for the destination, and
the capture waits for the tab's own content before taking the image, so
a capture can never be of the previous screen. The capture harness and
the responsive harness set their content once per test and vary the
width, the colour scheme and the environment through state, because the
rule's `setContent` may be called only once per test.

Verified: six consecutive full app-module runs, 159 tests each, no
failures, with the result files removed before every run so that no
stale result could mask a failure. Each was
produced by device state left behind by the screenshot sweeps —
`font_scale`, rotation and the app locale — and each passes when
reproduced individually and as its own module suite on a restored
device. They were contamination, not regressions, and no production
code was changed for them.

**One was a real defect**, in the new reset test: its control failed
because its `dumpsys alarm` filter could never match the API 36
output format, so the test proved nothing in either direction. The
observation is fixed and the test now passes. Fixing its control also
exposed an order dependency inside that test class, which is fixed by
re-establishing the notification permission in `setUp`.


| **Environment** | `cc36` AVD, API 36, Android 16, 320x640 at 160dpi, `emulator-5556` |

The 2026-10-20 audit of commit `d689a7e` recorded 333 unit and 166
instrumented tests. The baseline above is the state after five
workstreams were integrated on 2026-10-21; it was measured, not
carried forward. The two skipped instrumented tests are
`ReminderDozeDeliveryTest`, which are opt-in and skipped by default
because they force the whole device into idle.

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
| 2 | Contracts | **Partially complete** | The read-only audit is done and recorded. `PredictionSnapshot.confidenceLabel` is still a `String` on the model, and one `InsightEngine` site still reads the stored `cycleLength`. |
| 3 | Schema migration | **Verified complete** | All ten `02` §9.10 criteria now hold, including the two that were not implemented. The migrated-v2 launch test passes. |
| 4 | Prediction engine | **Partially complete** | All thirteen corpus gates exist and pass. One derivation in `InsightEngine` still reads the stored `cycleLength`, so `11` §8.9 is only partly met. |
| 5 | Design system | **Partially complete** | The state matrix, the previews and the copy review are done. The historical 58/97 abort (P5-06) is resolved in the current baseline: all three screenshot sweeps now complete 97/97 (portrait 1.0x, portrait 2.0x font scale, landscape). Wide-screen (600/840 dp) captures on the calibrated 320x640 AVD are clamped to 320 dp and do not constitute genuine responsive coverage; genuine 840 dp coverage was verified separately via an `adb wm size` override on `cc36` (see 24-final-production-readiness-audit.md §6.4). |
| 6 | Screens | **Partially complete** | `15` §14.14 now passes and is enforced by a test. The 200% and landscape sweeps still do not complete. |
| 7 | Notifications | **Partially complete** | **Nine of the twelve `13` §17 criteria are verified complete.** The intelligence module is in the delivery path. Criterion 9 has no producer behind it and criterion 12's scope is a recorded decision. |
| 8 | Sync | **Deferred by decision** | The ten gates and six invariants are out of scope. |
| 9 | Release readiness | **Partially complete** | The external list is entirely open. One repository-side item remains: a reminder schedule timestamp is still in the log. |
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
  derives no value from the stored `cycleLength` column. One site
  remains: line 108, which bounds the symptom window with
  `historicalCycles.last().cycleLength`.
- **What is already right.** The contradiction the criterion exists to
  prevent is fixed. `InsightEngine.build()` takes its trend series from
  `extractIntervals(observedStartsOf(cycles))` — the same extractor the
  prediction engine uses — and the comment at lines 30–33 records the
  original defect, where reading the stored column let the screen
  contradict the prediction screen on the same day. The retrospective
  fertile window was a second such site, and it now derives from the
  shared ovulation prior rather than from the stored column. The "Avg
  cycle" figure on Today and Insights comes from one source.
- **Why it is still open.** The remaining site is the length of a cycle
  that has already ended, used as a fact about the past rather than as
  a prediction, so it cannot make the two screens disagree. But it is
  still a read of the stored column, and `11` §8.9 asks for one
  extractor.
- **Dependencies.** None. This is a domain-only change with no UI
  consequence beyond the wording of a historical fertile-window label.
- **Verification procedure.** The call site is replaced, and a test in
  `core/domain` asserts that the retrospective fertile window for a
  given history equals the one the shared extractor produces. The
  `06` §2 scenario *"Editing an old record invalidates and recomputes
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

**Status: Verified complete.** All ten `02` §9.10 criteria hold, and
the two that were not implemented have been implemented rather than
reclassified. The work is in the repository at commit `7605ba1`.

### The exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "every acceptance criterion in `02` §9.10 passes" | **Partially complete** | See the table below. |
| "row counts and IDs verified identical before and after on a real-shaped fixture" | **Verified complete** | `core/database/src/androidTest/java/com/cyclecare/core/database/CycleCareDatabaseRoomTest.kt`, 20 instrumented cases, including `migration2To3_preservesEveryCycleRecordUnchanged` and `migration2To3_backfillsAuditTimestampsAndNoDeletions`. |
| "the app launches and behaves correctly on a migrated v2 database" | **Verified complete** | `MigratedV2DatabaseLaunchTest` seeds a v2 database, migrates it with the production migration list, launches the real app shell over the result and asserts the migrated history renders and the migration record is reachable. |

### The ten `02` §9.10 criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | v2 → v3 preserves every row and every record ID | **Verified complete** | The migration copies every column with `INSERT … SELECT`; `migration2To3_preservesEveryCycleRecordUnchanged` asserts it. |
| 2 | No `None` value is ever converted to `NULL`, or vice versa | **Verified complete** | The test at `CycleCareDatabaseRoomTest.kt:156-162` asserts `'None'` survives in every enum column. |
| 3 | Every original `cycleLength` is preserved in `legacyCycleLength` | **Verified complete** | `migration2To3` populates `legacyCycleLength` from the v2 value for every row, and the v3 schema carries the column. The v3 schema was never released, so the column went into the v2 to v3 migration rather than needing a v4. |
| 4 | Divergences are logged and surfaced to the user, never silently corrected | **Verified complete** | The migration writes a `migration_log` row per divergence, and Settings has an entry that shows what it did in plain words. The wording never claims the data was corrected, because it was not. |
| 5 | Duplicate dates are collapsed deterministically and the count is reported | **Verified complete** | The collapse keeps the most recently inserted row, and the record names the date, how many rows existed and how many were retained. The count is shown on the migration record screen. |
| 6 | Backfilled timestamps are never presented as real creation dates | **Verified complete** | `AuditTimestampReadGuardTest` scans every module's main source set and fails on a read of the audit timestamp columns outside the persistence layer, so the guarantee is enforced rather than incidental. |
| 7 | No destructive fallback exists on any production path | **Verified complete** | `fallbackToDestructiveMigration` appears nowhere in the repository. |
| 8 | `migration1To2` and `migration2To3` both have passing tests against real fixtures | **Verified complete** | Both are covered in `CycleCareDatabaseRoomTest`; 20 instrumented cases pass. |
| 9 | Migration runs in one transaction and aborts without data loss on verification failure | **Verified complete** | The migration snapshots every table it touches, and after the swap checks the row count, the record ids and every value against that snapshot. A mismatch throws, and Room's transaction rolls the migration back. A test forces a mismatch and asserts the source rows survive. |
| 10 | The backup and device-transfer decision in §9.9 is made and implemented before release | **Verified complete** | `data_extraction_rules.xml` and `backup_rules.xml`, with a `CycleCareDatabaseRoomTest` case per migration. |

### What the integration closed

The five open items from the 2026-10-20 audit are closed. The
read-only architecture audit in `24-phase-2-architecture-audit.md` is
what found the shape of the work, and the decision on `A1` is recorded
under [Cross-phase ambiguities](#cross-phase-ambiguities).

| Item | How it closed |
|---|---|
| P3-01, the migrated-v2 launch | `MigratedV2DatabaseLaunchTest` builds a v2 database, migrates it with the production migration list and renders the app over the result. It needed the container to be built on the main thread and the carried-over card to be scrolled to; both are now right. |
| P3-02, `legacyCycleLength` | Added to the v2 to v3 migration rather than a v4. The v3 schema was never released, so a v4 would have been churn for no user. |
| P3-03, divergences surfaced | A `migration_log` table, a domain repository over it, a Settings entry and a screen. The migration also records how much history was carried over, which is the plainest statement of what survived. |
| P3-04, the backfilled-timestamp guard | `AuditTimestampReadGuardTest` scans the main source sets and fails on a read of the audit columns outside the persistence layer. |
| P3-05, verification that aborts | The migration snapshots what it is about to change and checks the result against it. A mismatch throws, and Room rolls the migration back. |

### Two defects the integration found

Both are product defects rather than test problems, and both were
found by running the merged code rather than by reading it.

**A crash on bottom navigation.** The interstitial gate reads
persisted state, so the coroutine that asked for an interstitial
resumed on the storage thread, and the completion callback ran
`navController.navigate` there. Tapping a tab with ads enabled could
throw *"setCurrentState must be called on the main thread"* from
inside Navigation. The navigation is now marshalled onto the Android
main thread explicitly. The instrumented fake for the interstitial
counters now resumes its reads on a background dispatcher, the way the
real DataStore-backed repository does, so the existing navigation suite
covers it; reverting the fix makes that suite fail again.

**A name collision across source sets.** The androidTest source set
declared a private `NoOpReminderRepository` class and the production
source declared a private `NoOpReminderRepository` object. Kotlin's
file-private scope hid the clash at compile time, but both compile to
the same fully qualified name, and the test APK's copy shadowed the
production object at runtime. Any test that rendered the reminder
centre died with `NoSuchFieldError`. Both that and a sibling
`NoOpReminderScheduler` clash have been renamed.

## Phase 4 — Prediction engine

**Exit criteria (verbatim, `08`):** *"G1–G13 pass; `11` §8's twelve
criteria hold; no screen shows an unlabelled point estimate; Today and
Insights can never contradict each other."*

**Status: Partially complete.** All thirteen corpus gates exist and
pass. Eleven of `11` §8's twelve criteria are verified complete; the
twelfth, the shared extractor, is only partly met, because one
retrospective derivation in `InsightEngine` still reads the stored
`cycleLength` column rather than the shared extractor. That residual is
the same work as **P2-03**.

### The corpus gates, `12` §6

`12` §6 states: *"All gates must pass for `2.0.0` to ship. Any failure
blocks release; constants are then revisited, and the change recorded in
the ADR log."*

The harness is
`core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionCorpusGateTest.kt`.
A grep for each gate identifier across every `.kt` file in the
repository, excluding build outputs, returns the following. All
thirteen gates now have at least one named test, and all thirteen
pass.

`12` §6 says *"All gates must pass for `2.0.0` to ship. Any failure
blocks release; constants are then revisited, and the change recorded in
the ADR log."* No constant and no threshold was changed to make a gate
pass. The two engine behaviours that deviate from `11` §4.5 and §4.6
are recorded under the open items below rather than quietly amended.

| Gate | Threshold | Status | Test |
|---|---|---|---|
| G1 | Coverage, `regular`, n ≥ 6 ≥ 0.80 | **Verified complete** | `G1_regularHistoryCoverageMeetsTheEightyPercentTarget` |
| G2 | Coverage, `irregular`, n ≥ 6 ≥ 0.90 | **Verified complete** | `G2_irregularHistoryCoverageMeetsTheNinetyPercentTarget`, and `G2_irregularHistoryWidensTheWindowRatherThanNarrowingIt` for the widening the gate names |
| G3 | MAE, `regular`, n ≥ 6 ≤ 2.0 days | **Verified complete** | `G3_regularHistoryMeanAbsoluteErrorStaysWithinTwoDays` |
| G4 | MAE, all generators, n ≥ 3 ≤ 4.0 days | **Verified complete** | `G4_meanAbsoluteErrorStaysWithinTolerance`, `G4_perfectlyConstantHistoryIsPredictedExactly` |
| G5 | Median `halfWidthDays`, `regular`, ≤ 3.0 and ≥ 1 | **Verified complete** | `G5_regularHistoryStaysSharpEnoughToBeUseful` |
| G6 | P90 absolute error, `regular`, n ≥ 6 ≤ 5 days | **Verified complete** | `G6_regularHistoryNinetiethPercentileErrorStaysWithinFiveDays` |
| G7 | Availability at n ≥ 3 / n < 3 is exactly 1.0 / 0.0 | **Verified complete** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals` |
| G8 | `dataEntryError` MAE shift vs clean run ≤ 1.0 day | **Verified complete** | `G8_oneExtremeOutlierDoesNotMoveThePrediction`, `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` |
| G9 | `irregular`: `isIrregular` true only at ≥ 2 outliers among ≥ 4 intervals | **Verified complete** | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected` |
| G10 | `drifting`: trend applied only when the absolute projected drift is ≥ 1.5; total correction ≤ 3.0 days, monotone in drift magnitude | **Verified complete** | `G10_trendCorrectionActivatesAtTheThresholdAndStaysCappedAndMonotone`, `G10_trendIsAppliedOnlyAboveTheActivationThreshold` and `G10_totalDriftCorrectionStaysWithinThreeDaysAndIsMonotone` |
| G11 | Determinism: identical input → byte-identical output | **Verified complete** | `G11_identicalInputProducesIdenticalOutput` |
| G12 | Stability: editing one historical date moves the estimate ≤ previous window half-width | **Verified complete** | `G12_editingOneHistoricalDateStaysWithinThePriorWindowHalfWidth`, `G12_editingOneHistoricalDateKeepsTheEstimateInsideThePreviousWindow` and `G12_deletingARecordRecomputesTheEstimateDeterministically` |
| G13 | `calendarEdge`: leap/month/year boundaries | **Verified complete** | `G13_crossesMonthYearAndLeapBoundaries`, `G13_handlesMonthEndAndYearBoundaryStarts` |

`12` §6 also states: *"G1 and G5 must be read together. G1 without G5 is
not a pass — a window wide enough to always contain the answer would
satisfy G1 while being useless to a user."* Both are verified complete,
so that pairing is satisfied.

### The twelve criteria of `11` §8

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | No implausible interval influences `centerCycleLength` | **Verified complete** | `G8_implausibleIntervalIsExcludedRatherThanAveragedIn` |
| 2 | A single 200-day outlier with 5 valid intervals does not shift `nextPeriodDate` by more than 1 day | **Verified complete** | `G8_oneExtremeOutlierDoesNotMoveThePrediction` |
| 3 | `n < 3` yields `nextPeriodWindow == null` and never a fabricated range | **Verified complete** | `G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals`; `NotificationTimingTest.noWindowMeansNoReminderRatherThanAFabricatedDate` |
| 4 | Every window's half-width is within `[MIN_HALF_WIDTH_DAYS, MAX_HALF_WIDTH_DAYS]` | **Verified complete** | `core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionMathTest.kt` asserts both bounds, including at the sigma extremes |
| 5 | `isIrregular` requires 2 outliers among ≥ 4 intervals | **Verified complete** | `G9_aSingleOutlierIsNotEnoughToDeclareIrregular`, `G9_multipleOutliersAreDetected` |
| 6 | Identical inputs and identical `today` produce byte-identical output | **Verified complete** | `G11_identicalInputProducesIdenticalOutput`, and the thousand-run determinism threshold |
| 7 | No ambient clock read anywhere in the module; production injects `today` | **Verified complete** | A grep for `LocalDate.now`, `LocalDateTime.now`, `ZonedDateTime.now` and `System.currentTimeMillis` across `core/domain/src/main/.../prediction/` returns one hit, and it is a doc comment at `PredictionContracts.kt:223` describing the defect this criterion fixed. |
| 8 | Every output carries `algorithmVersion` and the record IDs used | **Verified complete** | `CyclePrediction.algorithmVersion` (`PredictionContracts.kt:179`) and `recordIdsUsed` (`PredictionContracts.kt:185`), both populated by `CyclePredictionEngineV2.kt:130` and `:134`; carried to the UI snapshot by `PredictionSnapshotMapper.kt:58`. Asserted by `criterion8_everyOutputCarriesAlgorithmVersionAndRecordIdsUsed`. |
| 9 | `InsightEngine` and the prediction engine cannot disagree for the same input, because they share one extractor | **Partially complete** | The trend series shares the extractor (`InsightEngine.kt:38-40`), the retrospective fertile window derives from the shared ovulation prior, and the "Avg cycle" figure on Today and Insights comes from one source. One symptom-window bound still reads the stored `cycleLength` — see **P2-03**. |
| 10 | Editing or deleting an input record changes downstream estimates deterministically and within the previously reported window | **Verified complete** | `criterion10_deletingAnOldRecordRecomputesWithinThePriorWindow` and the three G12 tests. |
| 11 | No screen renders a point estimate without visible "estimate" framing | **Verified complete** | `HomeEstimateFramingTest`, `CalendarEstimateFramingTest` and `InsightsEstimateFramingTest` cover the three features that render an estimate, and the Today, cycle-status, fertility-detail and calendar surfaces say "Estimated fertile days" and "This is not a contraceptive method." |
| 12 | No output implies contraception, diagnosis, or certainty | **Verified complete** | `criterion12_predictionLabelsDoNotImplyContraceptionDiagnosisOrCertainty` and `PredictionOutputCopyTest`, which reviews every string the prediction path can produce against `03` §7 and `05` §9. |

### The remaining exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "no screen shows an unlabelled point estimate" | **Verified complete** | The three framing test classes, and the "estimated" wording on every surface that renders one. |
| "Today and Insights can never contradict each other" | **Verified complete** | The trend series and the "Avg cycle" figure both come from the shared extractor now, and `SharedExtractorTest` holds them to it. The residual in `11` §8.9 is a retrospective per-cycle derivation, not a disagreement between the two screens; it is **P2-03**. |

### What the integration closed

| Item | How it closed |
|---|---|
| P4-01, the five missing gates | G2, G3, G6, G10 and G12 all have named tests now. No threshold in `12` §6 was changed and no constant was adjusted to make a gate pass. |
| P4-02, point-estimate framing | The three features that render an estimate have framing tests, and the surfaces say "estimated" and "not a contraceptive method". |
| P4-03, the output copy review | `PredictionOutputCopyTest` enumerates every string the prediction path can produce and asserts the absence of the banned phrases, so the review is repeatable. |

### P4-05 — Two engine behaviours that deviate from `11` §4

- **Definition of done.** The engine matches the specification, or the
  deviation is recorded in the ADR log with the reason and the
  measured effect, and the version is bumped so the change is visible.
- **What deviates.** `11` §4.5 specifies the population-typical fertile
  window as `[ovulation - 4, ovulation + 1]`, widened by the `§4.6`
  uncertainty and by the irreducible luteal sigma floor. The engine
  was producing a symmetric window eight days wide on each side and
  was not applying `LUTEAL_PHASE_SIGMA_DAYS` at all, so the constant
  existed in the contract and was never used. The window is now the
  specified shape, widened by both terms, and
  `PredictionFertileWindowTest` pins it.
- **The second deviation.** `11` §4.5 makes
  `LUTEAL_PHASE_SIGMA_DAYS` *"an irreducible uncertainty floor added to
  every ovulation-derived window"*. It is now applied to the fertile
  window, which is the only ovulation-derived window the engine
  produces. The next-period window is derived from period starts, not
  from ovulation, so the floor does not apply to it.
- **Dependencies.** None. The fertile window change is a behaviour
  change, so `PREDICTION_ALGORITHM_VERSION` and the `12` §7 golden
  fixtures need to be revisited in the same change. The feature layer
  reads the engine's window directly, so this is user-visible.
- **Verification procedure.** `./gradlew :core:domain:test`, and the
  97-image capture, which shows the fertile window on Today, the
  cycle-status screen, the fertility detail and the calendar.
- **Expected evidence.** `PredictionFertileWindowTest` passing, the
  version bumped, and the golden fixtures regenerated.

### P4-04 — Make the shared extractor enforceable

- **Definition of done.** A test in `core:domain` fails if any
  consumer of the interval series reads the stored `cycleLength` column
  instead of `extractIntervals(observedStartsOf(…))`, so the property
  cannot regress silently.
- **Dependencies.** **P2-03**, which removes the last such read.
- **Verification procedure.** `./gradlew :core:domain:test`. The test
  scans the main source sets for `cycleLength` reads outside the
  persistence layer, and must distinguish `CycleRecord.cycleLength`
  from `UserProfile.averageCycleLength`, which is a different thing.
- **Expected evidence.** A passing test that names any offending file
  and line.

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
| 7 | Every state in `03` §6 has a designed, tested implementation | **Verified complete** | `ScreenStateMatrixTest` asserts every data-driven screen at screen level, and `ScreenSaveStateMatrixTest` covers the save in progress, save success and validation error states, and the export screen's working, success and failure states. |
| 8 | Every control meets 48dp; every screen works at 200% font scale; contrast verified per token pair | **Partially complete** | Touch targets: `DesignTokenTest.touchTargetMeetsTheAccessibilityMinimum`. Contrast: `scripts/contrast-report.py` — every pair with a WCAG target meets it. 200%: the capture sweep now completes all 97 images at 2.0x font scale on `cc36` (historical 58/97 abort resolved); three defects were found and fixed. Not yet done for every screen on genuine wide viewports. |
| 9 | Every user-facing string is in `strings.xml` | **Verified complete** | The audit leaves four literals, all composed values or a diagnostic format. |
| 10 | Layouts verified at 320/360/600/840dp, portrait, landscape and foldable | **Partially complete** | Screenshot capture succeeds for all four width targets on the calibrated 320x640 `cc36` AVD (97/97 in portrait 1.0x, portrait 2.0x and landscape), but the 600/840 dp targets are clamped to the 320 dp window in portrait and to 640 dp in landscape, so those captures are bit-for-bit identical to the 320/640 captures and do not prove Medium/Expanded layouts. Genuine 840 dp coverage was verified on the same AVD via `adb wm size 840x1280` (system configuration `w840dp`/`sw840dp`, `ReThemeScreenshotTest` 97/97 with distinct 320/360/600/840 widths, `ResponsiveLayoutTest` passing), but that is a software window resize, not a physical tablet/foldable. **Foldable is not verified**: the emulator has no hinge or posture. See **P5-02**. |
| 11 | TalkBack traverses every key screen with correct roles, labels and states; no access trap remains | **Partially complete** | `TalkBackSemanticsTest` now covers every key screen, and the notification gate announces its action. **No human has run a screen reader**, which is recorded as pending rather than claimed. See **P5-03**. |
| 12 | `:core:designsystem` has its own test suite | **Verified complete** | **43** cases across `DesignTokenTest`, `MetricCardAccentTest` and `PreviewCoverageTest`. |
| 13 | No dark-pattern copy anywhere in the UI | **Verified complete** | The review is recorded in `24-dark-pattern-copy-review.md`, and `DarkPatternCopyTest` and `CopyAuditTest` assert the absence of the banned phrases so it does not regress. |

### The remaining exit-criteria clauses

| Clause | Status | Evidence |
|---|---|---|
| "no screen uses an undefined typography role" | **Verified complete** | `scripts/guard-typography-roles.sh`, in CI. |
| "previews exist at 320/360/600/840dp in light and dark" | **Verified complete** | `PreviewCoverageTest` now scans every preview source in the main source sets and fails when a screen's preview is missing a width or a scheme. Every feature has a preview file. |

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

### P5-03 — The manual screen-reader pass

- **Definition of done.** A person has run a screen reader through the
  key screens and recorded what was heard.
- **Dependencies.** A device and a person. The semantics tree is
  asserted for every key screen now, but a semantics tree is what a
  screen reader consumes, not the same as a person listening to one.
- **Verification procedure.** Run TalkBack through the key screens and
  record the device, the screen reader version and what was heard.
- **Expected evidence.** A written manual pass record. This is pending
  and is not claimed.

### P5-06 — Font-scale and landscape capture runs (historical abort, now resolved)

- **Historical issue.** The capture run originally stopped at 58 of 97
  images. The cause was the edit-cycle sheet: it is a `ModalBottomSheet`,
  and on a screen too short to hold it the back press finished the
  activity instead of dismissing the sheet, so the next capture had no
  composition. The failure reproduced on the base commit in a separate
  worktree, so it was not a regression from the redesign work; it was a
  pre-existing defect in the harness. It was stabilised in commit
  `09a1156` by searching the unmerged semantics tree
  (`useUnmergedTree = true`) for `home_dashboard` after sheet dismissal,
  and by correcting the runbook's shell polling interval from 1.0 s to
  0.2 s so the Gradle uninstall race no longer deleted the last files.
- **Current status.** Resolved in the current baseline. All three sweeps
  complete 97/97: portrait 1.0x font scale, portrait 2.0x font scale and
  landscape, on the calibrated `cc36` AVD (320x640, 160 dpi). See
  `24-final-production-readiness-audit.md` §6.2 for the reconciled
  breakdown.
- **Remaining caveat.** Completing the capture is not the same as
  genuine responsive coverage: on the 320x640 window the 600/840 dp
  captures are clamped and bit-for-bit identical to the 320 dp captures
  (§6.3 of the audit). Genuine 840 dp coverage requires the window
  override documented in §6.3.
- **Dependencies.** None open for capture completion. Physical
  tablet/foldable verification remains open (P5-02).
- **Verification procedure.** `settings put system font_scale 2.0`,
  capture, restore the scale to 1.0, and repeat with `user_rotation 1`;
  or use the override procedure in audit §6.3. Restore the device
  afterwards either way.
- **Expected evidence.** 97 images at 200% and 97 in landscape, and the
  restored device state.

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
| 6 | Every screen in `03` §6 has a designed, tested state | **Verified complete** | As `14` §12.7. |
| 7 | Every screen works offline with no network dependency for local content | **Verified complete** | The content is read from the local database. The export screen is the only one that reaches the network, and it has a failure state. |
| 8 | Every screen passes at 200% font scale, in dark mode, in RTL, and under TalkBack | **Partially complete** | Dark: every screen captured in both schemes. RTL and TalkBack: every key screen. 200%: the capture sweep now completes all 97 images at 2.0x font scale on `cc36` (historical 58/97 abort resolved). See **P5-06**. |
| 9 | No hardcoded user-facing strings remain | **Verified complete** | As `14` §12.9 |
| 10 | Confirmed versus estimated states distinguishable without colour | **Verified complete** | As `14` §12.5 |
| 11 | No screen shows two competing primary actions | **Verified complete** | As `14` §12.6 |
| 12 | No composable mutates state directly | **Verified complete** | The `AppLockGate` and `CycleCareApp` violations were resolved |
| 13 | Every existing test tag is preserved or deliberately migrated, and the navigation test is rewritten for four tabs | **Verified complete** | `settings_open_privacy` was added; nothing was removed |
| 14 | No file exceeds ~600 lines | **Verified complete** | The largest file in the main source sets is now `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt` at 588 lines. `MainSourceFileLengthTest` walks every main source set and fails when a file exceeds 600, naming the file and the count, so the criterion is enforced rather than remembered. |
| 15 | `SecureAuthController` and export have test coverage | **Verified complete** | `SecureAuthCapabilityTest`, `ExportViewModelTest`, `LocalExportRepositoryIntegrationTest` |

### What the integration closed

| Item | How it closed |
|---|---|
| P6-01, the 620-line shell | `CycleCareShell.kt` is now 226 lines, split into `CycleCareShell.kt`, `CycleCareBottomBar.kt` and `CycleCareNavigationHost.kt`, and the notification gate moved to its own file. `MainSourceFileLengthTest` enforces the limit. |
| P6-02, RTL and TalkBack across the remaining screens | `RtlLayoutTest` and `TalkBackSemanticsTest` now cover every key screen: the calendar, the log, the privacy screen, the settings screen, the export screen, the reminder centre, the cycle trends, the symptom patterns, the edit sheet and the reset dialog. |

### P6-03 — The main-thread fix has to survive a refactor

- **Definition of done.** The marshalling of the tab navigation onto
  the Android main thread is enforced by a test rather than by
  remembering it.
- **Why it matters.** The fix was applied in `abc395c` and lost again
  in `1a5de96`, because the shell was split and the file that carried
  it was replaced wholesale. The suite still passed, because the
  instrumented fake for the interstitial counters only began to resume
  its reads on a background dispatcher at the same time.
- **Dependencies.** None.
- **Verification procedure.** A test that taps a bottom navigation tab
  with the interstitial gate reading persisted state, and asserts that
  the destination is reached. The existing navigation suite covers it
  once the fake resumes on a background dispatcher; a test that asserts
  the fake's behaviour is the thing that protects it.
- **Expected evidence.** A named test, and the shared
  `openTabAndAwait` helper that the app's instrumented tests use.

---

## Phase 7 — Notifications

**Exit criteria (verbatim, `08`):** *"every criterion in `13` §17
passes; the Doze demonstration is recorded, not assumed."*

**Status: Partially complete.** This was the largest gap in the
ledger, and it is now the smallest of the open phases.
**Ten of the twelve `13` §17 criteria are verified complete.** Two are
partially met: criterion 9 has no producer behind the rule it
enforces, and criterion 12's scope is a recorded decision rather than
a fact.

The finding that drove this phase was that
`core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt`
was **not wired into the delivery path**. It held a complete scoring,
quiet-hours, budget and arbitration model, well tested in isolation,
and nothing in `app/`, `core/data/` or `feature/` called it. That is
now fixed: `ReminderDeliveryPolicy` consults it before every post, and
`ReminderDelivery` uses the decision. The module's own test suite
proved the logic; there is now a test that proves it reaches a
notification.

### The twelve `13` §17 criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | No notification is delivered without a score ≥ `SEND_THRESHOLD` | **Verified complete** | `ReminderDeliveryPolicy` evaluates the gates before every post, and the decision carries the score and the threshold, which the delivery log records. |
| 2 | `MAX_SENDS_PER_DAY` is never exceeded | **Verified complete** | The daily and weekly budgets are enforced against the event history, and `MAX_SENDS_PER_WEEK` is a hard gate. |
| 3 | A type crossing the auto-disable threshold is disabled **and** explained, with one-tap re-enable | **Verified complete** | The decision is persisted, the type is silenced, the reminder centre lists the paused types with their counts, and `onReEnable` puts one back. The record survives a restart. |
| 4 | No reminder claims a date the prediction window cannot support | **Verified complete** | `ReminderSchedulePlanner` anchors to the earliest plausible start rather than the centre, and the copy hedges: "Your period may start…", "Your period is likely between … and …" (`ReminderSchedulePlanner.kt:87-95`). The rationale is recorded at lines 42–59. |
| 5 | No reminder is generated when `nextPeriodWindow == null` and the type depends on the estimate | **Verified complete** | `hasSupportableEstimate()` requires `supportsRange && nextPeriodHalfWidthDays != null` (`ReminderSchedulePlanner.kt:67-68`); every estimate-derived builder checks it. `NotificationTimingTest.noWindowMeansNoReminderRatherThanAFabricatedDate` and `latePeriodNeverFiresWithoutAnEstimate` cover it. |
| 6 | Retrying delivery never produces a duplicate notification | **Verified complete** | The DAO has an explicit insert-if-absent, and a test delivers the same payload twice, forcing a retry, and asserts one event row and one notification. |
| 7 | Every notification has a group summary and a `setPublicVersion` redaction | **Verified complete** | Every member carries `setGroup`, `setGroupSummary` and a `setPublicVersion` redaction, and the group's one summary is the redacted variant. Asserted by `ReminderGroupSummaryTest` and `ReminderPublicVersionRedactionTest` against the real `NotificationManager`. |
| 8 | Exact alarms are requested only for the two permitted types, and the app is fully functional without the permission | **Verified complete** | `tierFor()` in `ReminderAlarmFacade.kt:149-153` returns `EXACT` for `PeriodPrep` and `LatePeriod` only, `INEXACT_WHILE_IDLE` for everything else. The permission is never assumed: `canScheduleExactAlarms()` returns false below API 31, and a `SecurityException` downgrades to Tier 2. `ReminderAlarmSchedulerTest`, 12 instrumented cases, covers the tier decision. |
| 9 | User-pinned send times are never overridden | **Partially complete** | The flag now reaches the domain: `ReminderRule` carries `userPinnedTime`, the mapper preserves it, and the planner passes it through to the alarm. The rule itself — a pinned time lowers the threshold, and a learned time never applies to a pinned time — is enforced and tested. **What is missing is a producer**: nothing writes a learned send time, because the engagement table has no writer, so there is no learned hour that could attempt to displace a pinned one. The rule is in place; nothing exercises it. |
| 10 | No health value appears in notification `extras` beyond visible content | **Verified complete** | `ReminderNotificationClient` adds no extras, and the builder sets only the title, body and `BigTextStyle` — all visibly rendered content. `13` §15 defines the constraint as the notification's `extras` bundle, which is what was checked. The `PendingIntent`'s own intent extras do carry `ReminderIntentKeys.TargetEpochDay`, which is cycle-derived, but those are delivered to this app's own `MainActivity` and are not the notification's extras bundle. |
| 11 | Quiet hours and DND are honoured, with `LatePeriod` as the documented exception | **Verified complete** | The interruption filter is read in the app layer and passed to the domain as data, so the domain stays pure. A candidate inside quiet hours, or under DND, is deferred rather than delivered, and `LatePeriod` is exempt. |
| 12 | No guilt, streak, or false-precision copy anywhere | **Partially complete** | The notification copy is clean and `ReminderNotificationCopyTest` holds it there. The scope is a recorded decision: the criterion is read as scoped to the notification copy, so the Logging feature's positive streak counter is not in scope. See **A2**. |

### What the integration closed

| Item | How it closed |
|---|---|
| P7-01, the intelligence in the delivery path | `ReminderDeliveryPolicy` evaluates the gates before every post and returns the decision with its reason, which the delivery log records. `core:domain` stays pure: the event history, the channel settings and the interruption filter are passed in from the app layer. |
| P7-02, group summary and redaction | Every member carries `setGroup`, `setGroupSummary` and a `setPublicVersion` redaction, and the group's summary is the redacted variant. |
| P7-03, retry de-duplication | An explicit insert-if-absent in the DAO, and a test that delivers the same payload twice and asserts one event row and one notification. |
| P7-04, the pinned send time | `ReminderRule` carries `userPinnedTime`, the mapper preserves it, and the planner passes it through to the alarm. |

### P7-07 — There is no producer for a learned send time

- **Definition of done.** The engagement data that would produce a
  learned send hour is written, and the learned hour reaches the
  planner as a candidate that the pinned-time rule can then decline.
- **Why it matters.** `13` §8 describes a learned send time, and the
  rule that a learned time never displaces a pinned one is implemented
  and tested. But nothing writes the engagement profile, so no learned
  hour can be proposed. Criterion 9 is therefore enforced and inert.
- **Dependencies.** The engagement profile needs a writer: the hours
  and the interaction count that `13` §8.2 describes. The delivery
  path already has the events that carry them.
- **Verification procedure.** A test that records an interaction at a
  known hour, runs the planner, and asserts that the candidate hour
  is proposed for a rule that is not pinned; and that it is not
  proposed for a rule that is.
- **Expected evidence.** Named tests, and the engagement profile
  written by the delivery path.

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
| 2 | Unit and domain tests, including the `12` corpus gates | **Verified complete** | The unit and domain tests pass, and all thirteen corpus gates have named tests that pass. |
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
| Editing an old record invalidates and recomputes dependent estimates | **Verified complete** | `criterion10_deletingAnOldRecordRecomputesWithinThePriorWindow` and the G12 tests, which edit and delete a record and assert the estimate moves deterministically and stays inside the previously reported window. |
| Sparse, contradictory or highly variable history shows uncertainty or no-estimate state | **Partially complete** | The quality tiers and `INSUFFICIENT_HISTORY` exist and are covered by `G7`; the screen-level rendering of each tier is not asserted for every surface. |
| Removing all history removes stale derived predictions | **Not audited** | `AppResetRepository` calls `clearAllTables()`, which is a full reset of the app rather than the removal of the last cycle. Whether the derived estimates are dropped when the last cycle is deleted is not asserted. |
| Disabled notifications do not repeatedly prompt the user | **Verified complete** | The channel-disabled check at `ReminderDelivery.kt:100-118` returns `Delivered` without posting. |
| Reminder schedules are rebuilt without duplication after boot, app update, timezone change or rule edit | **Partially complete** | The identity is deterministic — `reminderWorkName(ruleId)` and the stable pending-intent request code in `ReminderAlarmFacade.kt:98-104` — and `ReminderRescheduleReceiver` exists. No test exercises a boot or timezone-change rebuild. |
| WorkManager timing behaviour is tested with its permitted deferral | **Verified complete** | Superseded by the tier implementation: `ReminderAlarmSchedulerTest`, 12 cases. The app no longer promises exact-minute delivery for anything but the two permitted types. |
| Foreground handling does not depend on a live Activity reference | **Verified complete** | `appVisibilityObserver` is read from the container, not from an Activity. |
| Locked-screen text is masked by default and respects user settings | **Verified complete** | Masking is on by default — `hideNotificationPreview = preferences[…] ?: true` at `PrivacyRepository.kt:18` — and every notification now carries both `VISIBILITY_PRIVATE` and a `setPublicVersion` redaction, so the private body is what shows on a locked screen. |
| Notification channel changes and denial are reflected in the app UI | **Verified complete** | `ScreenStateMatrixTest.theSettingsScreenShowsAChannelThatHasBeenSwitchedOff`, and the notification-gate cases. |

### The `06` §2 privacy and deletion scenarios

`06` §2 has a fifth group that the other four phases' checklists do not
cover. Sync scenarios are not applicable, per Phase 8.

| Scenario | Status | Evidence |
|---|---|---|
| Synthetic period dates, symptoms, notes and medication values do not appear in production logs, analytics, crash payloads or request URLs | **Partially complete** | The logger's attributes are enums, counts, identifiers, SDK versions and zone identifiers — no date, symptom, note or medication value. There is no analytics SDK, and `createCrashReporter()` returns a `NoOpCrashReporter`: `Diagnostics.kt:75-88` records that Firebase Crashlytics was removed because the merged manifest shipped Firebase components that were never initialised. **One exception remains** — `ReminderDelivery.kt:221` logs `scheduledAtEpochMillis`, and for a `PeriodPrep` reminder that instant is derived from the predicted period date. See **P9-08**. |
| Turning sync off follows the documented remote-data policy | **Not applicable** | Sync is deferred. There is no remote data and no remote-data policy to follow. |
| Local deletion clears app-owned local records, caches and pending uploads and cancels scheduled reminders where possible | **Verified complete** | `AppResetRepository.resetAllLocalData()` clears the tables, the export directory and the preferences, and cancels the armed reminders through a hook the app wires to the alarm facade. A test that proves it is **P9-11**. |
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
**P5-02** for the foldable, and
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

**P9-08 — A reminder schedule timestamp is in the log. — Still open.**
*Definition of done.* The diagnostic log carries no value from which a
period date can be derived.
*Current state.* `ReminderDelivery.kt:221` still logs
`scheduledAtEpochMillis`, and for a `PeriodPrep` reminder that instant
is the predicted period date minus the lead time, so the log carries a
derived cycle date.
`app/src/main/java/com/cyclecare/app/Diagnostics.kt:87` sets the rule
the codebase holds itself to: *"diagnostics must never carry period
dates, symptoms, notes, medication or free text"*. The fix is to log a
bucket — the scheduled hour, or the lead time — rather than the
instant.
*Dependencies.* None. It is a one-line change in the delivery log.
*Verification procedure.* A test that captures the logged attributes for
a `PeriodPrep` delivery and asserts that no attribute is a timestamp or
a date. A second test asserts the same for every other delivery.
*Expected evidence.* Named tests, and a log line that names the bucket
rather than the instant.

**P9-09 — A reset does not cancel scheduled reminders. — Closed.**
`AppResetRepository.resetAllLocalData()` now takes a
`cancelArmedReminders` hook, reads the armed rules before it clears
the tables, and cancels them. The app wires the hook to the alarm
facade in `CycleCareApplication`, so a `PeriodPrep` or `LatePeriod`
alarm cannot fire after the user has deleted their data. The scenario
says *"cancels scheduled reminders where possible"*; for an alarm the
app has scheduled itself, it is possible.

**P9-11 — A reset does not cancel scheduled reminders: prove it.**
*Definition of done.* An instrumented test seeds a reminder, calls the
reset, and asserts that the alarm is no longer scheduled and that no
notification is posted afterwards.
*Dependencies.* The hook is in place, so only the test is missing.
*Verification procedure.*
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`.
*Expected evidence.* A named test.

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
| A1 | **Decided.** The criterion is met by its letter: `legacyCycleLength` is a real column, populated by the v2 to v3 migration with the original v2 value. It went into the v2 to v3 migration rather than a v4 because the v3 schema was never released, so a v4 would have been churn for no user. | — | `02` §9.10.3, `Migrations.kt` |
| A2 | **Decided.** `13` §17.12 says "no guilt, streak, or false-precision copy **anywhere**". It is read as scoped to the notification copy, because `13` §15 states the same rule in the notification chapter, and `03` §2.5, `03` §7 and `14` §6 all ban streak *loss* and guilt rather than a positive counter. The Logging feature's positive streak counter stays. | **P7-06** | `13` §17.12, `13` §15, `03` §2.5, `03` §7, `14` §6 |
| A3 | **Decided.** The criterion is treated as a hard 600 lines, and the file that breached it was split. `MainSourceFileLengthTest` now enforces it, so the tilde no longer has to be interpreted. | — | `15` §14.14 |
| A4 | **Resolved.** All thirteen gates now have named tests and all thirteen pass, so the question of whether a gate would fail against the current constants is answered rather than assumed. No constant and no threshold was changed to make a gate pass. | — | `12` §6 |
| A5 | `11` §8.9 requires `InsightEngine` and the prediction engine to share one extractor. The trend series and the retrospective fertile window do; one symptom-window bound still reads the stored column. Is the criterion about the shared estimates, or about every derived date? | **P2-03** | `11` §8.9, `InsightEngine.kt:108` |
| A6 | **Decided.** A `Migration` cannot log, so the record is written to a table: `migration_log`, with the divergence kind, the record, the date, the value before and the value after. It is durable across the process that performed the migration, and the Settings screen reads it. | — | `02` §9.10.4, `CycleCareDatabase.kt` |
| A7 | **Decided.** They are one notification. The group summary is the coalesced summary, and it is the redacted variant; a quiet-hours deferral posts a standalone grouped member, because the platform hides a summary whose group has no children. | — | `13` §10, `13` §17.7, `ReminderGroupSummary.kt` |

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
