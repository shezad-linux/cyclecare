# 25 — Phase 3 verification, P9-11, and the data-integrity tests

**Date:** 21 October 2026
**Scope:** an independent re-verification of `02` §9.10 against the code as it
stands, the closing of the ledger's open item **P9-11**, and the data-integrity
tests the brief calls for.
**Branch:** `rel/migration`, based on `fdec933`.

## What this document is, and what it is not

The ledger records Phase 3 as *verified complete*. This document does not
repeat that. It re-derives each of the ten `02` §9.10 criteria from the code,
the exported schema and the tests that exist, and says plainly where the
evidence runs out.

**No emulator was available for this work.** Every instrumented test below was
compiled (`:core:database:compileDebugAndroidTestKotlin`,
`:app:compileDebugAndroidTestKotlin`, `:core:data:compileDebugAndroidTestKotlin`)
but **not executed**. No claim in this document rests on an instrumented test
having passed. The JVM tests were executed and their real results are quoted.

## The ten `02` §9.10 criteria

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | v2 → v3 preserves every row and every record ID | **Met** | `Migrations.kt:334-350` copies `cycle_records` with `INSERT … SELECT` and the same shape for `symptom_logs` (`:419-441`); `Migrations.kt:137-167` snapshots every table's IDs and values and `Migrations.kt:174-209` compares them after the swap, so a lost row aborts the migration rather than passing quietly. Tests: `migration2To3_preservesRowCountsAndIdsAcrossEveryTable`, `migration2To3_preservesEveryCycleRecordAndOriginalCycleLength` (instrumented, compiled not run), and the new `aValidRecordMigratesAndSurvivesWithEveryValueIntact`. |
| 2 | No `None` is ever converted to `NULL`, or vice versa | **Met** | The migration copies the four enum columns verbatim (`Migrations.kt:427-428`); nothing rewrites them. The read path preserves the distinction too: `EnumParsers.kt:56-88` returns `null` for `null` and `PainLevel.None` for `"None"`. New JVM test `NoneAndNullStayDistinctTest` (5 cases, run, passing) covers the read path, the write path and the round trip; the new instrumented `aGenuinelyUntrackedValueStaysNullAndIsNeverReadAsNone` covers the row level. |
| 3 | Every original `cycleLength` is preserved in `legacyCycleLength` | **Met** | `Migrations.kt:344` copies `cycleLength` into `legacyCycleLength` for every row *before* anything is recomputed, and `Migrations.kt:355-368` then recomputes `cycleLength` from the start-to-start gap with the prescribed `MIN(next.startEpochDay) - cycle_records.startEpochDay` SQL. The oldest record keeps its stored value as the seed, because the `WHERE EXISTS` clause finds no later start. `Migrations.kt:194-208` verifies the preserved values against the snapshot after the swap, so a row that lost its legacy value aborts the migration. Tests: `migration2To3_preservesEveryCycleRecordAndOriginalCycleLength`, `migration2To3_keepsOldestSeedAndLogsOnlyDivergencesOverSevenDays`, and the new `aCycleLengthThatDisagreesWithTheGapIsRecordedAndBothValuesAreKept` (instrumented, compiled not run). |
| 4 | Divergences are logged **and** surfaced to the user, never silently corrected | **Met** | *Logged:* `Migrations.kt:370-398` writes one `migration_log` row per divergence with `storedValue` and `observedValue`; `Migrations.kt:446-459` writes the duplicate count; `Migrations.kt:526-538` writes what was carried over. *Surfaced:* `MigrationDivergenceRepository.kt:22-27` reads the log, `CycleCareApplication.kt:146` wires `RoomMigrationDivergenceRepository`, `CycleCareNavigationHost.kt:203-204` routes to `MigrationNotesRoute`, and `MigrationNotesScreen.kt:64-80` groups the records by kind. The screen shows the migration's own wording verbatim (`MigrationNotesScreen.kt:197`), and that wording says the gap "is now authoritative; the original value is retained in legacyCycleLength" — it never says anything was corrected. The screen's own copy agrees: *"Nothing in your history was changed. Where two values disagreed, both were kept exactly as you recorded them."* Tests: `MigratedV2DatabaseLaunchTest.theAppShowsTheMigrationRecordOnADatabaseMigratedFromV2` (instrumented, compiled not run), the new JVM `MigrationDivergenceMappingTest` (5 cases, run, passing) and the new instrumented `theMigrationRecordIsReadableThroughTheRepositoryWithBothValuesKept`. |
| 5 | Duplicate dates collapse deterministically, the count is reported, and the older row is retained as a tombstone | **Met** | `Migrations.kt:430-437` keeps the row with the greatest `rowid` live (insertion order, the only signal v2 has) and gives every older row a `deletedAtEpochMillis`. No row is deleted: the IDs and payloads survive, so the collapse is reversible. `Migrations.kt:216-231` counts the rows per date and `Migrations.kt:446-459` records the count with the number of rows retained as tombstones. Tests: `migration2To3_duplicateDatesBecomeTombstonesAndTheCountIsReported`, plus the new instrumented `duplicateDatesKeepTheMostRecentRowLiveAndTheOlderRowsAsTombstones` and `theLiveRowIsTheMostRecentDuplicateAndTheOlderRowIsATombstoneThatKeepsItsDate`. |
| 6 | Backfilled timestamps are never presented as real creation dates | **Met, with one observation** | The migration writes the migration time and marks it: `Migrations.kt:341-343` writes `$migratedAt` and `Migrations.kt:344`/`:438` set `createdAtIsBackfilled` to 1; the entity carries the flag (`CycleCareDatabase.kt:89-91`). Nothing outside the persistence layer may read the columns at all — `AuditTimestampReadGuardTest` fails the build on any such read, and the exported report is built from domain models that have no audit fields. New JVM test `BackfilledTimestampIsNeverExportedTest` (2 cases, run, passing) checks that the backfilled value does not reach the model the screens read and that the report contains no creation date. **Observation:** the *encrypted backup* payload does carry the raw `createdAtEpochMillis` (`BackupPayloadSerializer.kt:55`, `:85`). It is not user-facing text and it is not presented as a "logged on" date, so `02` §9.6 is not broken by it, but the value inside a backup file is the synthetic one. Reported, not changed. |
| 7 | No destructive fallback exists on any production path | **Met** | `fallbackToDestructiveMigration` and `fallbackToDestructiveMigrationOnDowngrade` appear nowhere in the repository — only in prose in `00-current-state-review.md` and `02-database-and-sync.md`. The production builder is `CycleCareApplication.kt:50-54`, which registers `ALL_MIGRATIONS` and nothing else. The new instrumented `aFailedMigrationNeverClearsTheDatabase` asserts the same property from the failure side. |
| 8 | `migration1To2` and `migration2To3` both have passing tests against real fixtures | **Partly met** | Both migrations have instrumented coverage in `CycleCareDatabaseRoomTest` (eight `migration1To2` cases, twelve `migration2To3` cases) and both compile. **I could not execute them**, so "passing" is not something I can confirm — the ledger's claim of 20 passing instrumented cases is not reproducible from this worktree without a device. **The fixtures are synthetic**, which `02` §9.8 explicitly says is not sufficient: *"Built from a production-shaped, fully anonymised snapshot, not only synthetic data. Synthetic fixtures will not surface the duplicate-date or divergence cases that actually occur."* The synthetic fixtures here do construct duplicate-date and divergence cases, but they are the ones this repository imagined, not the ones production produced. A real anonymised snapshot is still owed. |
| 9 | Migration runs in one transaction and aborts without data loss on verification failure | **Met** | Room's `SQLiteOpenHelper` wraps `onUpgrade` in a transaction, and Room validates the migrated schema against the exported `3.json` after the migration runs; a mismatch throws and the transaction rolls back. Inside the migration, `Migrations.kt:137-148` captures the pre-migration snapshot and `Migrations.kt:174-209` compares counts, IDs and every preserved value, throwing `IllegalStateException` on any difference. The `HISTORY_CARRIED_OVER` record is written *before* the verification (`Migrations.kt:526-538`) so it rolls back with everything else. Tests: `migration2To3_verificationFailureRollsBackToV2WithoutLoss`, plus the new instrumented `anAbortedMigrationLeavesEverySourceRowIntact` (an explicit rollback, the shape of a process death mid-migration) and `aFailedMigrationNeverClearsTheDatabase`. |
| 10 | The backup and device-transfer decision in §9.9 is made and implemented before release | **Met for the decision and the implementation** | `app/src/main/res/xml/data_extraction_rules.xml` (API 31+) excludes everything from cloud backup and includes `database/` and `file/datastore/` in `<device-transfer>`; `app/src/main/res/xml/backup_rules.xml` (API 26-30) excludes everything, so device-to-device transfer is not available there. Both files carry the reasoning and cite `02` §9.9. The manifest references both (`AndroidManifest.xml`, `allowBackup="true"` with the two rules files). **Still open, and not mine to close:** `02` §9.9 also asks for the decision to be *stated in the app and in the privacy policy*, and the ledger's own item **P9-10** ("the product copy does not state the backup behaviour") is still open. `feature/*` is outside this workstream's ownership. |

### Where the ledger's evidence is inexact

The ledger table for Phase 3 cites `migration2To3_preservesEveryCycleRecordUnchanged`
and `migration2To3_backfillsAuditTimestampsAndNoDeletions`. Neither name exists.
The tests in the repository are called
`migration2To3_preservesEveryCycleRecordAndOriginalCycleLength` and
`migration2To3_backfillsAuditTimestampsAndLabelsThemSynthetic`. The substance is
there; the names in the ledger are stale. Recorded so the next reader does not
go looking for tests that are not there.

## P9-11 — a reset that cancels reminders: the test

**The open item.** `AppResetRepository.resetAllLocalData()` takes a
`cancelArmedReminders` hook, `CycleCareApplication` wires it to the alarm
facade, and no test proved the alarm was actually cancelled.

**What was written.**

1. `core/data/src/test/java/com/cyclecare/core/data/AppResetReminderCancellationTest.kt`
   — **JVM, run, 5 cases passing.** It covers the part of the contract that
   does not need a device: the reset cancels the armed reminders *before* it
   clears the tables (the rules that address the alarms live in the table
   being cleared), the names it addresses them by are the ones the scheduler
   arms them under (`reminderWorkName`), the hook still runs when there are no
   rules (the coalesced summary and the auto-disable notice have alarms of
   their own), and a cancellation that fails does not leave the user's records
   on the device.
   To make that testable, `AppResetRepository.kt` now holds the reset as an
   `AppResetSequence` of injected steps. The behaviour is unchanged: same
   steps, same order, same `runCatching` around the cancellation.
2. `app/src/androidTest/java/com/cyclecare/app/AppResetCancelsRemindersTest.kt`
   — **instrumented, compiled, NOT executed.** It arms a real alarm through
   the real `AndroidReminderAlarmFacade`, addresses it exactly as the
   scheduler does, reads the platform's own alarm queue before and after the
   reset, and asserts the alarm is in the queue before and gone after. The
   "before" reading is the control: if the alarm were not in the queue the
   test fails rather than passing vacuously. It also posts a notification
   under one of the app's own reminder ids, checks it is there before the
   reset and gone after, and asserts that no reminder notification remains.

**Why the instrumented test reads the alarm queue.** There is no public API
that says "is this alarm still scheduled". The platform keeps the queue and
`dumpsys alarm` is the only way to read it, which the test does through
`UiAutomation.executeShellCommand` — the same mechanism
`ReminderDozeDeliveryTest` already uses for `dumpsys deviceidle`. The alarm is
identified by the request code it was addressed by, which is derived from a
rule id that nothing else on the device uses.

**What this does not prove.** Whether `dumpsys alarm` prints the request code
in the form this test expects is a property of the platform's dump format. If
it does not, the *first* assertion fails loudly with the relevant lines
logged, rather than the test passing quietly. This needs a device to settle.

## The data-integrity tests

| Test | Layer | Executed? | Result |
|---|---|---|---|
| `AppResetReminderCancellationTest` (5 cases) | `core:data` JVM | **Yes** | 5/5 pass |
| `NoneAndNullStayDistinctTest` (5 cases) | `core:data` JVM | **Yes** | 5/5 pass |
| `MigrationDivergenceMappingTest` (5 cases) | `core:data` JVM | **Yes** | 5/5 pass |
| `BackfilledTimestampIsNeverExportedTest` (2 cases) | `core:data` JVM | **Yes** | 2/2 pass |
| `CycleCareDatabaseDataIntegrityTest` (9 cases) | `core:database` instrumented | **No** — compiled only | not run |
| `MigratedRepositoryConsistencyTest` (5 cases) | `core:data` instrumented | **No** — compiled only | not run |
| `AppResetCancelsRemindersTest` (3 cases) | `app` instrumented | **No** — compiled only | not run |

What each covers, against the brief's list:

- *A valid record migrates and survives* —
  `aValidRecordMigratesAndSurvivesWithEveryValueIntact`,
  `aMigratedRecordIsReadableThroughTheDaosAsALiveRow`.
- *A record with a missing timestamp is handled, not silently invented* —
  `aRecordWithNoCreationTimestampIsBackfilledAndLabelledSynthetic` (the
  value is the migration time and is labelled `createdAtIsBackfilled`, and
  no deletion is invented), and at the JVM level
  `BackfilledTimestampIsNeverExportedTest`.
- *A `cycleLength` that disagrees with the gap is a divergence and both
  values are retained* —
  `aCycleLengthThatDisagreesWithTheGapIsRecordedAndBothValuesAreKept` (row
  level, with the wording checked for "corrected"/"fixed"/"repaired"),
  `MigrationDivergenceMappingTest` (the user-facing half).
- *Duplicate dates with tombstones; the live row is the most recent; the
  repository's uniqueness applies to live rows only* —
  `duplicateDatesKeepTheMostRecentRowLiveAndTheOlderRowsAsTombstones`,
  `theLiveRowIsTheMostRecentDuplicateAndTheOlderRowIsATombstoneThatKeepsItsDate`,
  `liveRowUniquenessHoldsWhileTheDateIndexStaysNonUnique`.
- *A successful migration leaves the repository consistent* —
  `aSuccessfulMigrationLeavesTheRepositoriesConsistentWithTheSourceData`.
- *Failure handling: a failed or aborted operation does not discard user
  records* — `aFailedMigrationNeverClearsTheDatabase`,
  `anAbortedMigrationLeavesEverySourceRowIntact`.
- *Rollback or safe recovery* — `anAbortedMigrationLeavesEverySourceRowIntact`
  rolls the transaction back explicitly, the shape of a process death halfway
  through, and asserts the v2 schema and every source row are back.
- *Repository-level consistency after migration: live-row uniqueness holds,
  tombstone dates are retained, and the date indices remain intentionally
  non-unique* — `liveRowUniquenessHoldsWhileTheDateIndexStaysNonUnique` and
  `theDateIndexIsDeliberatelyNotUniqueSoATombstonedDateCanBeLoggedAgain`. The
  date index is **not** unique, on purpose, and the tests assert that it is
  not: a tombstoned row keeps its date, so a unique index would make "log,
  delete, log again" fail.

## Commands run, and what they said

```
./gradlew :core:data:test --console=plain                       BUILD SUCCESSFUL  (25 tests, 0 failures)
./gradlew :core:domain:test --console=plain                     BUILD SUCCESSFUL
./gradlew :core:database:compileDebugAndroidTestKotlin          BUILD SUCCESSFUL
./gradlew :app:compileDebugAndroidTestKotlin                    BUILD SUCCESSFUL
./gradlew test --console=plain                                  BUILD SUCCESSFUL  (292 unit tests, 0 failures, 0 skipped)
./scripts/guard-no-ad-targeting.sh                              OK: no ad-targeting API found.
```

The whole-repository unit suite is 292 tests across 48 suites, 0 failures,
0 skipped. `core:data` is 25 of them, 17 of which are new.

## What could not be verified without the device

- Every instrumented test in this document. 17 new cases across three
  files, compiled and not run.
- `02` §9.10.8 — whether the existing `migration1To2` and `migration2To3`
  cases pass. The ledger says they do; this worktree cannot confirm it.
- The `dumpsys alarm` format assumption in
  `AppResetCancelsRemindersTest`.
- The real production-shape fixture `02` §9.8 asks for. It does not exist in
  the repository, and a synthetic one cannot stand in for it.

## Observations for the lead

None of these were changed: they are outside this workstream's ownership or
outside the brief.

1. **The encrypted backup carries the synthetic creation timestamp**
   (`BackupPayloadSerializer.kt:55`, `:85`). Harmless for `02` §9.6 as
   written, but a backup file that claims a creation time the app invented is
   worth a decision.
2. **The reset swallows a `CancellationException`.** `AppResetSequence.run()`
   wraps the cancellation in `runCatching`, which is the pre-existing
   behaviour, and `runCatching` catches `Throwable`. A cancelled coroutine
   would therefore continue on to clear the tables. Not changed here, because
   the brief forbids behaviour changes and the change would need its own
   test.
3. **P9-10 is still open** and touches `feature/*`.
4. **`02` §9.8's fixture requirement is unmet.** The migration tests use
   synthetic fixtures only.
