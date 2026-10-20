# Phase 2 architecture audit

Read-only audit of the Phase 2 exit criteria against the repository, for
the lead to direct implementation from. No production code was changed by
this audit.

## Audit metadata

| | |
|---|---|
| **Audit date** | 2026-10-20 |
| **Commit** | `ec133f2` |
| **Branch** | `workstream/phase2-arch` |
| **Working tree** | Clean at the commit above, apart from this file |
| **Baseline run** | `./gradlew :core:domain:test --offline` → **129 tests, 0 failures, 0 errors, 0 skipped** |
| **Compile check** | `./gradlew :core:domain:compileKotlin :core:model:compileKotlin --offline` → **BUILD SUCCESSFUL** |
| **Device** | Not used. No emulator, no `adb`, no `connectedAndroidTest` was run. |

**Scope.** `core/model`, `core/domain`, `core/data`, `core/database`,
`core/security`, `core/designsystem`, `app`, `feature/*`, and the
`CycleCare-Redesign-Docs` specification set. Build directories excluded
from every count.

**Counting method.** Every count in this document was produced by a
mechanical scan of the working tree, not by reading. The two scans used
were a brace/paren-aware Kotlin declaration scan (for constants) and
`grep -rn` over `.kt` files with `build/` excluded (for identifier
occurrences). Where a number disagrees with the ledger, both numbers are
given and the difference is explained.

---

## Findings summary

| ID | Area | Status | Statement |
|---|---|---|---|
| **F-01** | P2-01 constants | **Measured, not audited before** | 220 `const val` declarations exist in the main source sets across 39 files. 45 carry an adjacent doc comment; **175 do not**. |
| **F-02** | P2-01 ledger evidence | **Ledger evidence incorrect** | `NotificationIntelligence.kt` carries a doc comment on **4 of its 15** constants, not "per-constant rationale" as `22` §Phase 2 claims. Eight of the other eleven have a rationale in `13`/`15`; three do not. |
| **F-03** | P2-01 scope | **New finding** | The cycle-length range `20..45` is a magic literal in **6 production sites across 4 modules** plus one private validator field, and appears in **no** design document. |
| **F-04** | P2-01 tooling | **New finding** | `core/security/.../VaultContracts.kt` contains a raw NUL byte, so `grep` classifies it as binary and silently skips its 10 constants. A grep-based checker will miss them. |
| **F-05** | P2-02 counts | **Ledger count confirmed, split clarified** | `confidenceLabel`: **34 occurrences in 17 `.kt` files** — 20 in production (7 files) and 14 in test-and-preview (10 files). **3 of the 14 preview fixtures live in `src/main`**, so the ledger's own verification grep will not return empty until they are updated. |
| **F-06** | P2-02 consumers | **Narrowed** | Only **2** of the 17 Home occurrences read `PredictionSnapshot.confidenceLabel`; the other 15 thread a same-named `String` through Home's own state. |
| **F-07** | P2-02 constraint | **Confirmed** | `core:model` is `kotlin.jvm` with one dependency; `core:designsystem` is an Android/Compose library. `UiText` is not usable as the model type. |
| **F-08** | P2-02 design | **New finding** | `PredictionSnapshot.quality`, `.phase` and `.warnings` are written by the mapper and read by **no consumer anywhere**. They are dead payload. |
| **F-09** | P2-03 sites | **Confirmed and extended** | **13** sites read the stored `cycleLength` column: 2 in `core/domain` (the ledger names only these), 8 in the Calendar/Export surfaces, 1 in Today, and 4 in the persistence layer (which must not change). |
| **F-10** | P2-03 contradiction | **New, high severity** | Today's "Avg cycle" and Insights' "Avg cycle" are the **same labelled metric from different sources** and can differ by an arbitrary amount for the same user on the same day. |
| **F-11** | Shared extractor | **Partially shared** | The two engines share `extractIntervals`/`observedStartsOf`, but over different windows (≤12 vs 6 intervals) and different statistics (recency-weighted trimmed mean vs plain mean). The shared series does not make the displayed figures equal. |
| **F-12** | Shared extractor | **Missing** | The retrospective fertile window (`InsightEngine.kt:277-280`) has **no** extractor-derived equivalent; the engine's prospective window also uses a different width (±8+hw vs −4/+1). |
| **F-13** | Domain contracts | **Verified complete** | 13 contracts in `Repositories.kt` are Kotlin interfaces. **0 Java files** and **0 typealiases** exist in the repository. `core:model` and `core:domain` compile as pure JVM. |
| **F-14** | Domain contracts | **Scattered** | 7 further domain contracts live outside `Repositories.kt`; 22 more live outside the domain module entirely. 42 interfaces in total. |
| **F-15** | P2-01 scope | **New finding** | No file-level (non-`const`) `val` in the main source sets has a bare literal initialiser; the 26 file-level `val`s are all expressions. The `const val` set is the whole of the "constants" surface, apart from the magic literals in F-03. |
| **F-16** | Cross-screen consistency | **New finding, high severity** | The fertile window is derived **three different ways** — the engine (cycle day ≈5), Today (cycle day ≥17) and Log (cycle day 12) — for the same user on the same day. |
| **F-17** | P2-01 scope | **New finding** | **7** product thresholds are carried as bare literals outside the `const val` set: the check-in score weights (base 75, +4/section, +2/streak capped at 10), the Log fertile-window start day (12), the Insights depth gates (2 cycles, 5 days) and Today's teaser gate (3 cycles). |

### Exit-criteria verdict

| Clause (`08` Phase 2) | Ledger status | Audit verdict |
|---|---|---|
| "every constant has a documented rationale" | Not audited | **Fails.** 175 of 220 `const val` have no adjacent comment; of those, 9 have a rationale in a specification, 23 need one (21 of them appear in no specification at all) and 143 are exempt identifiers. F-03 adds an undocumented product range duplicated across 4 modules; F-17 adds 7 more undocumented thresholds. |
| "the domain contracts compile as Kotlin interfaces" | Verified complete | **Holds.** F-13. |
| "no consumer still references `PredictionSnapshot.confidenceLabel`" | Not implemented | **Fails.** 2 production reads at `HomeRoute.kt:261` and `:265`, plus the definition and the producer. |
| "no consumer still reads `cycleLength` as an interval" | Partially complete | **Fails, and is wider than recorded.** F-09, F-10, F-11, F-12. |

---

## Area 1 — `confidenceLabel` (ledger item P2-02)

### 1.1 Where it is defined, populated and consumed

**Definition.** `core/model/src/main/java/com/cyclecare/core/model/CycleModels.kt:156`

```kotlin
val confidenceLabel: String,
```

It is a **required** constructor parameter of `PredictionSnapshot` (no default), positioned between `fertileWindow` and `daysUntilNextPeriod`.

**Population.** `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionSnapshotMapper.kt:29` writes it from a private extension function at `:69`:

```kotlin
private fun CyclePrediction.confidenceLabel(): String = when {
    quality == EstimateQuality.INSUFFICIENT_HISTORY -> "Baseline estimate"
    isStale -> "Estimate may be out of date"
    quality == EstimateQuality.HIGH -> "Narrow range"
    quality == EstimateQuality.MODERATE -> "Moderate range"
    else -> "Wide range"
}
```

The branch order is observable behaviour: `INSUFFICIENT_HISTORY` is tested before `isStale`, so a stale insufficient-history prediction reads "Baseline estimate", not "Estimate may be out of date". Any replacement must preserve that order.

The production call chain is `core/data/.../PredictionRepository.kt:51` → `toSnapshot()` → `PredictionSnapshot`, observed by `HomeViewModel` through `PredictionRepository.observePrediction()` (`core/domain/.../Repositories.kt:39`).

**Consumption.** Only `feature/home` reads it. `feature/calendar` and `feature/logging` read other fields of the same snapshot (`fertileWindow`, `ovulationDate`, `daysUntilNextPeriod`, `cycleDay`) and never `confidenceLabel`; `app/src/main` never reads it.

### 1.2 Exact occurrence counts

`grep -rn --include='*.kt' 'confidenceLabel' .` excluding `build/`:

**34 occurrences in 17 `.kt` files.** (Adding the 16 markdown mentions gives 50 across the whole tree.)

#### Production — 20 occurrences in 7 files

| File | Lines | Count | Role |
|---|---|---|---|
| `core/model/src/main/java/com/cyclecare/core/model/CycleModels.kt` | 156 | 1 | Field declaration |
| `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionSnapshotMapper.kt` | 29, 69 | 2 | Producer (write + private function) |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt` | 32, 181, 184, 190 | 4 | UI state field + formatter parameter |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt` | 119, 125, 261, 265 | 4 | ViewModel; **261 and 265 read the model field** |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeTeaserCards.kt` | 112, 139, 140 | 3 | Composable parameter |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeScreen.kt` | 175, 179, 316 | 3 | Composable |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeCycleStatusScreen.kt` | 147, 380, 382 | 3 | Composable |

#### Preview fixtures inside `src/main` — 3 occurrences in 3 files

| File | Line | Value |
|---|---|---|
| `feature/home/src/main/java/com/cyclecare/feature/home/HomePreviews.kt` | 77 | `""` |
| `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarPreviews.kt` | 38 | `"Baseline estimate"` |
| `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingPreviews.kt` | 82 | `"Baseline estimate"` |

#### Test and instrumented-test fixtures — 11 occurrences in 7 files

| File | Lines | Value |
|---|---|---|
| `core/domain/src/test/java/com/cyclecare/core/domain/ReminderSchedulePlannerTest.kt` | 287 | `"Stable"` |
| `feature/home/src/test/java/com/cyclecare/feature/home/HomePresentationTest.kt` | 36, 78 | `"Baseline estimate"` |
| `feature/calendar/src/test/java/com/cyclecare/feature/calendar/CalendarPresentationTest.kt` | 43, 68 | `"Baseline estimate"` |
| `feature/calendar/src/androidTest/java/com/cyclecare/feature/calendar/CalendarScreenUiTest.kt` | 188, 275 | `"Baseline estimate"` |
| `feature/logging/src/test/java/com/cyclecare/feature/logging/LoggingPresentationTest.kt` | 85, 142 | `"Baseline estimate"` |
| `app/src/androidTest/java/com/cyclecare/app/CycleCareAppStateNavigationTest.kt` | 900 | `"Baseline estimate"` |
| `app/src/androidTest/java/com/cyclecare/app/ReminderAlarmSchedulerTest.kt` | 318 | `"Stable"` |

### 1.3 What is a production consumer and what is not

**Production consumers of the model field: 2 sites, both in `feature/home`.**

- `HomeRoute.kt:261` — `confidenceLabel = base.prediction?.confidenceLabel.orEmpty(),` (passed to `buildVariabilityText`)
- `HomeRoute.kt:265` — `confidenceLabel = base.prediction?.confidenceLabel.orEmpty(),` (written into `HomeUiState`)

**Everything else in `feature/home` is the same *name* on a Home-owned `String`**, threaded from those two reads:

- `HomeUiState.kt:32` — `val confidenceLabel: String = ""` (a `HomeUiState` field, not the model field)
- `HomeUiState.kt:181` — the `confidenceLabel: String` parameter of `formatCycleProgressLabel`
- `HomeUiState.kt:184`, `:190` — uses of that parameter
- `HomeRoute.kt:119` — the `confidenceLabel: String` parameter of `buildVariabilityText`
- `HomeRoute.kt:125` — `confidenceLabel.ifBlank { null }?.let { UiText.Raw(it) }` — **this is where the English prose leaks into the UI as `UiText.Raw`**, with a comment at `:122-124` admitting it: *"The confidence label comes from the domain as English prose, which is a known boundary gap."*
- `HomeTeaserCards.kt:112`, `:139`, `:140`
- `HomeScreen.kt:175`, `:179`, `:316`
- `HomeCycleStatusScreen.kt:147`, `:380`, `:382`

**Not consumers:** the 3 preview fixtures and the 11 test fixtures are all `PredictionSnapshot(...)` constructor arguments. They set the field; they do not read it.

**No instrumented test asserts on the confidence text.** A grep for `confidence`, `Baseline estimate`, `Narrow range`, `Wide range` and `Moderate range` across every `androidTest` source set returns no assertion on any of the five labels. The only assertion is the unit test `HomePresentationTest.kt:31-39`.

### 1.4 The constraint on the replacement type

Confirmed from the build files:

- `core/model/build.gradle.kts` — `alias(libs.plugins.kotlin.jvm)`, one dependency (`libs.kotlinx.coroutines.core`). Pure JVM, no Android SDK.
- `core/designsystem/build.gradle.kts` — `alias(libs.plugins.android.library)` + Compose. Android library.
- `core/designsystem/.../text/UiText.kt:31` — `sealed interface UiText`, an Android resource carrier (`@StringRes`, `@PluralsRes`, `stringResource`).

So `UiText` cannot be the type of a `core:model` field. The ledger's constraint is correct.

### 1.5 Existing machine-readable payload that is already on the snapshot

`PredictionSnapshot` already carries three machine-readable fields that the mapper populates and **no consumer reads**:

| Field | Declared | Populated | Read by |
|---|---|---|---|
| `quality: PredictionQuality` | `CycleModels.kt:173` | `PredictionSnapshotMapper.kt:33-38` | **nothing** |
| `phase: PredictionPhase` | `CycleModels.kt:174` | `PredictionSnapshotMapper.kt:39-45` | **nothing** |
| `warnings: Set<PredictionWarning>` | `CycleModels.kt:175` | `PredictionSnapshotMapper.kt:46-56` | **nothing** |

`PredictionQuality` (`CycleModels.kt:192`) is the model mirror of the domain's `EstimateQuality` (`PredictionContracts.kt:113`), and `PredictionWarning.STALE_HISTORY` (`CycleModels.kt:203`) is the model mirror of `PredictWarning.STALE_HISTORY` (`PredictionContracts.kt:132`). The information needed to reproduce all five labels is therefore **already on the snapshot** and simply unused.

This matters for the design: the replacement does not have to invent a type, and it does not have to reach back into the domain package.

---

## Area 2 — `cycleLength` as an interval (ledger item P2-03)

### 2.1 Every read of the stored column, classified

The stored column is `CycleRecord.cycleLength` (`core/model/.../CycleModels.kt:114`), documented as deprecated: *"Must not be read as a cycle length: cycle length is derived from consecutive start dates, and this field has never been cross-validated against them."* It is persisted as `CycleRecordEntity.cycleLength` (`core/database/.../CycleCareDatabase.kt:72`, same deprecation note).

Classification key:
**(a)** fact about a completed historical cycle · **(b)** prediction of a future cycle · **(c)** derived retrospective estimate · **(P)** persistence only, not an interval read.

| # | Site | Class | What it does |
|---|---|---|---|
| 1 | `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:107` | **(c)** | `historicalCycles.last().startDate.plusDays(historicalCycles.last().cycleLength.toLong() - 1L)` — treats the stored value as the length of the last cycle to compute the **end of the symptom relevance window**. |
| 2 | `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:278` | **(c)** | `cycle.startDate.plusDays(cycle.cycleLength.toLong() - 14L)` — treats the stored value as the cycle length to derive a **retrospective ovulation date**, then a fertile window of −4/+1 days. |
| 3 | `core/data/src/main/java/com/cyclecare/core/data/ExportRepository.kt:365` | **(a)** | `"${cycle.startDate} to ${cycle.endDate} (${cycle.cycleLength} day cycle)"` — the stored value is printed to the user in the exported report as a **fact about that cycle**. The report's adjacent `profileLines` print `profile.averageCycleLength` as a separate "Average cycle length", so the report can contain two different cycle-length figures. |
| 4 | `core/data/src/main/java/com/cyclecare/core/data/BackupPayloadSerializer.kt:51` | **(P)** | `dos.writeInt(cycle.cycleLength)` — the value is serialised into the encrypted backup. Must not change; **P3-02 depends on the value surviving**. |
| 5 | `core/data/src/main/java/com/cyclecare/core/data/BackupPayloadSerializer.kt:145`, `:156` | **(P)** | Deserialisation into the model field. |
| 6 | `core/data/src/main/java/com/cyclecare/core/data/EntityMappers.kt:40` | **(P)** | Model → entity. |
| 7 | `core/data/src/main/java/com/cyclecare/core/data/EntityMappers.kt:52` | **(P)** | Entity → model. |
| 8 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:213` | **(a)** | `uiState.value.selectedCycle?.cycleLength?.toString() ?: "28"` — the stored value seeds the **editor's cycle-length field** when creating a new record, pre-filled from a previous record. |
| 9 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:372` | **(a)** | `cycleLengthText = cycleLength.toString()` in `CycleRecord.toEditorState()` — the stored value is pre-filled into the **edit form** for an existing record. |
| 10 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:159-160` | **(a)** | `pluralUiText(R.plurals.history_cycle_length, cycle.cycleLength, cycle.cycleLength)` — the **Calendar history list** shows the stored value as the cycle's length. |
| 11 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:304`, `:306`, `:308`, `:310`, `:312`, `:314` | **(a)** | `selectedCycle.cycleLength` — the **selected-day summary** shows the stored value as the cycle's length (6 sites). |
| 12 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:245` | **(b)** | `base.profile?.averageCycleLength ?: base.cycles.lastOrNull()?.cycleLength ?: 28` — the stored value of the **most recent** cycle is the **fallback cycle length** for Today. This is the denominator of `cycleDay / cycleLength` (`HomeRoute.kt:83`) and the numerator of the "Avg cycle" metric card (`HomeMetricGrid.kt:85`, `:119`) and of `Day X / Y` (`HomeScreen.kt:214`). |
| 13 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:536` | **(a)** | `cycleLengthText = cycleLength.toString()` in `CycleRecord.toEditorState()` — the stored value is pre-filled into the **edit form**. |

**Totals: 13 read sites of the stored column. 1 (a) + 1 (b) + 1 (c) at the domain boundary are the ones the criterion is about; 8 (a) in the Calendar/Export surfaces; 4 (P) persistence; 1 (b) in Today.**

Everything else that the grep for `cycleLength` returns is a **different** quantity with the same word: `UserProfile.averageCycleLength` (`CycleModels.kt:96`, the onboarding baseline), the editors' `cycleLengthText`, and the `uiState.cycleLength` UI-state field. These are not reads of the stored column and must not be swept in by a source-scanning checker without a type distinction.

### 2.2 `InsightEngine` in detail — what it derives from where

`core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt`

| Value | Line(s) | Source |
|---|---|---|
| `recentCycleLengths` (the trend series) | 39-43 | **Shared extractor.** `extractIntervals(observedStartsOf(cycles).takeLast(HISTORY_LIMIT + 1))` → `.filter { it.usable }` → `.takeLast(HISTORY_LIMIT)` → `.map { it.lengthDays }` |
| `averageCycleLength`, `minCycleLength`, `maxCycleLength` | 45, 61, 62 | Derived from `recentCycleLengths` (the extracted series) |
| `irregularCycles` | 51-60 | Derived from `recentCycleLengths` using the engine's own `MAD_TO_SIGMA_FACTOR` and `IRREGULAR_MIN_OUTLIERS` |
| `averagePeriodLength`, `recentPeriodLengths` | 44, 46 | Derived from `cycle.periodLengthDays` (`CycleModels.kt:121`), i.e. `endDate − startDate + 1` — a **recorded end date**, not the stored column |
| `cycleWindowLabel` | 75, 113-121 | Cycle **start dates** |
| `relevantSymptoms` window **start** | 106 | `historicalCycles.first().startDate` — a start date |
| `relevantSymptoms` window **end** | **107** | **Stored `cycleLength`** ← site 1 |
| `buildPeriodHighlight` | 136-179 | `cycle.startDate` … `cycle.endDate` — recorded end date |
| `buildFertileWindowHighlight` window | **277-280** | **Stored `cycleLength`** ← site 2 |
| `narrativeSummary` | 227-272 | Derived from the extracted series |

So: **one** of the two remaining sites (`:107`) bounds a *symptom-inclusion window*; the other (`:278`) derives a *retrospective fertile window*.

The comment at `InsightEngine.kt:31-38` already records the original defect and the fix, and is accurate.

### 2.3 Can `InsightEngine` contradict the prediction engine for the same input?

**`11` §8.9** asks that the two "cannot disagree for the same input, because they share one extractor".

**For the interval series: yes, they cannot disagree.** Both call `extractIntervals(observedStartsOf(...))` (`CycleIntervals.kt:24`, `:46`) and both filter on `usable`. The series is the same data.

**For the displayed figures: they can and do differ**, for three separate reasons.

1. **Different windows.** The engine uses `takeLast(MAX_INTERVALS)` = **12** usable intervals (`CyclePredictionEngineV2.kt:59`, `PredictionContracts.kt:24`). The insight engine uses `takeLast(HISTORY_LIMIT)` = **6** usable intervals (`InsightEngine.kt:42`, `:328`). For a user with 10 observed cycles, the engine centres on 9 gaps and the insight engine on the most recent 6.

2. **Different statistics.** The engine's centre is a **recency-weighted trimmed mean** (`CyclePredictionEngineV2.kt:171-179`, `RECENCY_DECAY = 0.85`, trimming one value from each tail at n ≥ 5). The insight engine's average is a **plain arithmetic mean** (`InsightEngine.kt:45`). Same data, different number.

3. **Different consumers of the number.** The engine's centre drives `nextPeriodDate` (`CyclePredictionEngineV2.kt:92`); the insight engine's average drives `averageCycleLength` (`InsightEngine.kt:45`), which is rendered as "Avg cycle" on Insights (`InsightsUiState.kt:121`, `InsightCard.kt:59-60` and `:72-73`).

**The retrospective fertile window (site 2) has no counterpart in the engine and can contradict it.**

- `InsightEngine.kt:278` derives ovulation as `cycle.startDate + cycleLength − 14`, i.e. from the **stored** length.
- `PredictionMath.kt:131-132` derives ovulation as `ovulationFrom(nextPeriodDate) = nextPeriodDate − LUTEAL_PHASE_DEFAULT_DAYS(14)`, i.e. from the **extracted** length.

For the *second-to-last* cycle, the engine's forward-derived next period is `lastStart + gap`, and the insight engine's retrospective ovulation of the second-to-last cycle is `secondLastStart + storedCycleLength − 14`. When `storedCycleLength ≠ gap` — which is the entire reason the column was deprecated — the two dates differ. The two are computed for different cycles (one past, one future) so they do not appear side by side, but the *rule* is not shared, and `11` §8.9 asks for the rule to be shared.

**The window shapes also differ.** The insight engine's retrospective fertile window is `ovulation − 4 … ovulation + 1` (`InsightEngine.kt:279`), which is exactly the *fallback* shape in `PredictionSnapshotMapper.kt:85-89`. The engine's real fertile window is `ovulation ± (LUTEAL_PHASE_DEFAULT_DAYS/2 + 1 + halfWidth)` = `±(8 + hw)` (`CyclePredictionEngineV2.kt:119-124`). Two fertile-window shapes coexist in the codebase.

**Answer: no, "Today and Insights can never contradict each other" does not currently hold**, and the two `InsightEngine` sites are only part of the reason. See section 3.

---

## Area 3 — Shared interval extraction

### 3.1 How each side obtains intervals

| | `InsightEngine` | Prediction engine |
|---|---|---|
| File | `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt` | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/CyclePredictionEngineV2.kt` |
| Extractor | `extractIntervals(observedStartsOf(cycles))` — `InsightEngine.kt:39-40` | `extractIntervals(observed)` — `CyclePredictionEngineV2.kt:51` |
| Input to the extractor | `observedStartsOf(cycles).takeLast(HISTORY_LIMIT + 1)` = 7 starts — `InsightEngine.kt:39` | `input.observed`, already filtered of `CycleSource.Predicted` — `CyclePredictionEngineV2.kt:42-44`, fed from `PredictionRepository.kt:32-41` |
| Window | last **6** usable intervals — `InsightEngine.kt:42` | last **12** usable intervals — `CyclePredictionEngineV2.kt:59` |
| Statistic | plain mean — `InsightEngine.kt:45` | recency-weighted trimmed mean — `CyclePredictionEngineV2.kt:171-179` |
| Dispersion | `MAD_TO_SIGMA_FACTOR` × mean absolute deviation — `InsightEngine.kt:52-57` | `weightedMad` then `totalSigma` — `CyclePredictionEngineV2.kt:69`, `:77` |
| Irregularity rule | `isOutlier(...)` count ≥ `IRREGULAR_MIN_OUTLIERS` — `InsightEngine.kt:58-60` | `isIrregular(outliers, size, hasImpossibleInterval)` — `CyclePredictionEngineV2.kt:74`, `PredictionMath.kt:82-91` |
| Cycle length used for the *current* cycle | **none** | `centre + trendAdjustment` — `CyclePredictionEngineV2.kt:89-90` |
| Cycle length used for *past* cycles | **stored column** — `InsightEngine.kt:107`, `:278` | not applicable |

### 3.2 Precisely where they share and where they do not

**Shared** — both call the same two functions in
`core/domain/src/main/java/com/cyclecare/core/domain/prediction/CycleIntervals.kt`:

- `observedStartsOf(cycles)` — `:24-34` — filters `source != CycleSource.Predicted`, sorts by `startDate`.
- `extractIntervals(observed)` — `:46-62` — start-to-start gaps, with the plausibility filter (`MIN_PLAUSIBLE_INTERVAL_DAYS` / `MAX_PLAUSIBLE_INTERVAL_DAYS`) and an `exclusionReason`.

Both also share `MAD_TO_SIGMA_FACTOR` (`InsightEngine.kt:55`, `PredictionContracts.kt:61`) and `IRREGULAR_MIN_OUTLIERS` (`InsightEngine.kt:60`, `PredictionContracts.kt:88`).

**Not shared** — six divergences:

| # | Divergence | Evidence |
|---|---|---|
| D1 | Interval window: 6 vs 12 | `InsightEngine.kt:42` vs `CyclePredictionEngineV2.kt:59` |
| D2 | Statistic: plain mean vs recency-weighted trimmed mean | `InsightEngine.kt:45` vs `CyclePredictionEngineV2.kt:171-179` |
| D3 | Dispersion: unweighted MAD vs weighted MAD | `InsightEngine.kt:52-57` vs `CyclePredictionEngineV2.kt:187-192` |
| D4 | Irregularity: no "impossible interval forces it" clause | `InsightEngine.kt:58-60` vs `PredictionMath.kt:86-88` |
| D5 | Retrospective per-cycle lengths: stored column vs not derivable | `InsightEngine.kt:107`, `:278` |
| D6 | Today's cycle-length denominator: profile baseline or stored column vs the engine's centre | `HomeRoute.kt:245` vs `CyclePredictionEngineV2.kt:89-90` |

**The shared extractor guarantees that the two engines read the same gaps. It does not guarantee that they publish the same number, and it does not reach Today at all.**

### 3.3 The concrete contradiction that is still live

Both screens show a metric labelled **`Avg cycle`**:

| Screen | Label | Value | Source |
|---|---|---|---|
| Today | `feature/home/src/main/res/values/strings.xml:125` `avg_cycle` = "Avg cycle" | `uiState.cycleLength` | `HomeRoute.kt:245` — `profile.averageCycleLength ?: lastCycle.cycleLength ?: 28` |
| Insights | `feature/insights/src/main/res/values/strings.xml:4` `avg_cycle` = "Avg cycle" | `insights.averageCycleLength` | `InsightEngine.kt:45` — plain mean of the last 6 extracted intervals |

Rendered at `feature/home/src/main/java/com/cyclecare/feature/home/HomeMetricGrid.kt:85` and `:119` (`pluralStringResource(R.plurals.n_days, uiState.cycleLength, uiState.cycleLength)`) and at `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightCard.kt:59-60` and `:72-73` (`label = uiText(R.string.avg_cycle)`, `value = uiState.averageCycleText.resolve()`).

**Worked counter-example.** A user sets their onboarding baseline to 28 (`UserProfile.averageCycleLength = 28`) and logs three cycles whose real start-to-start gaps are 30, 30 and 30.

- Insights shows **"Avg cycle — 30 days"** (`InsightEngine.kt:45` → `InsightsUiState.kt:121`).
- Today shows **"Avg cycle — 28 days"** (`HomeRoute.kt:245` → `HomeMetricGrid.kt:85`).

Two screens, same label, same user, same day, different numbers. The prediction engine, meanwhile, centres on 30 and predicts the next period from it.

The gap is not bounded by anything: `profile.averageCycleLength` is a value the user typed at onboarding (`OnboardingRoute.kt:112`) and is never reconciled with observed history. When there is **no** profile, Today falls back to the **stored** `cycleLength` of the most recent record (`HomeRoute.kt:245`), which the domain explicitly declares must not be read as a cycle length (`CycleModels.kt:109-113`) and which `CycleRecordValidator.kt:47-49` declines to range-check.

The same `uiState.cycleLength` is also the denominator of the cycle-progress bar (`HomeScreen.kt:154-157`, `HomeCycleStatusScreen.kt:133-135`), of the `Day X / Y` pill (`HomeScreen.kt:214`), and of the cycle-phase band (`HomeRoute.kt:83`, `HomeUiState.kt:257-262`). So one wrong denominator mislabels the phase, the progress bar and the day counter.

**Related and unclaimed:** `PredictionSnapshot.phase` (`CycleModels.kt:174`) carries the engine's own `PredictionPhase`, and **no consumer reads it**. Today recomputes the phase itself from `cycleDay / profileCycleLength` (`HomeRoute.kt:78-91`), against a denominator the engine did not use.

### 3.4 F-16 — the fertile window is derived three different ways

The same user, the same day, three answers about when the fertile window is.

| Where | Rule | Cycle day the fertile window starts (28-day cycle) |
|---|---|---|
| **The engine** | `ovulationDate = nextPeriodDate − 14` (`CyclePredictionEngineV2.kt:104`, `PredictionMath.kt:131-132`); `fertileWindow.start = ovulation − (LUTEAL_PHASE_DEFAULT_DAYS/2 + 1 + halfWidth)` = `ovulation − 8 − hw` (`CyclePredictionEngineV2.kt:119-124`) | `anchor + 14 − 8 − hw` = **cycle day 5** at hw = 2 |
| **Today** | `cycleProgress < 0.60f` → `R.string.fertile_window` (`HomeRoute.kt:87`), against `cycleLength` = profile baseline or stored column | **cycle day 17** |
| **Log** | `val fertileStart = 12` (`feature/logging/.../LoggingUiState.kt:443`), counted down to in the hero insight | **cycle day 12** |

The engine's own `PredictionPhase` (which would settle the question) is on the snapshot and read by nobody (F-08). Two of the three derivations also use a different cycle-length denominator than the engine.

This is the same family of defect as F-10 and it is **not** in the ledger. It is recorded here because `11` §8.9 asks about contradiction between the two engines, and this is a contradiction between the two engines *as the UI consumes them*.

### 3.5 What a test enforcing "can never contradict" would need to assert

Three layers, in increasing cost:

**Layer 1 — domain, cheap, provable today.** A `core/domain` test that, for a fixture with deliberately contradictory stored `cycleLength` values, asserts that `InsightEngine.build()` returns the same series as `extractIntervals(observedStartsOf(cycles))` and that every value in `InsightSnapshot` is unchanged when the stored column is mutated. `InsightEngineTest.kt:18-33` already does the first half for `recentCycleLengths` and `averageCycleLength`; it does **not** cover `phaseHighlights`, which is where sites 1 and 2 live.

**Layer 2 — source scan, the P4-04 mechanism.** A test in `core/domain` that walks the main source sets and fails on any read of `CycleRecord.cycleLength` outside the persistence layer, naming the file and line. **It must distinguish the model field from `UserProfile.averageCycleLength` and from the editors' `cycleLengthText`** — the word `cycleLength` appears on **122 lines** in the main source sets and only **20** of them read the stored column, so a naive substring match produces **102 false positives** (see the table in 2.1 and the ledger's own P4-04 wording).

**Layer 3 — the cross-screen guarantee, which is the one the phrase actually means.** A test that asserts Today's and Insights' "Avg cycle" are the same number for the same input:

```kotlin
// feature/home or app/src/test
val cycles = fixtureCycles()          // observed starts
val insights = InsightEngine().build(cycles, symptoms)
val home = HomeUiState(cycleLength = <today's value>, ...)
assertThat(home.cycleLength).isEqualTo(insights.averageCycleLength)
```

This test **fails today** (28 vs 30 in the example above) and cannot be written until F-10 is fixed. It is the only assertion that makes the phrase "Today and Insights can never contradict each other" true.

---

## Area 4 — Constants rationale (ledger item P2-01)

### 4.1 Method and totals

Scope: every `const val` and every literal-initialised `val` in the main source sets, `build/` excluded.

A mechanical scan found:

| Measure | Count |
|---|---|
| `const val` declarations in main source sets | **220** |
| Files containing them | **39** |
| `const val` with an adjacent doc comment (KDoc or line comment immediately above) | **45** |
| `const val` without an adjacent doc comment | **175** |
| — of those 175, classified **design-doc** (a stated rationale exists in a specification) | **9** |
| — of those 175, classified **NEEDS-RATIONALE** (see 4.3) | **23** |
| — of those 175, classified **exempt-by-rule** (an identifier or a unit conversion, see 4.6) | **143** |
| File-level (non-`const`) `val` declarations | **26** |
| — of those 26, with a bare literal initialiser | **0** — all are expressions (formatters, colour schemes, route sets, migration objects) |
| Member-level (non-`const`) `val` with a literal initialiser | **10** — 5 are PDF page geometry (`ExportRepository.kt:303-307`), 1 is a benchmark package name (`CycleCareBenchmarkTest.kt:20`), 4 are product thresholds |
| Product thresholds carried as literals elsewhere (function-local, inside an expression, or as a default parameter) | **7** (F-17) |
| `const val` per file, top 5 | `PredictionContracts.kt` 28, `HomeUiState.kt` 17, `PrivacyComponents.kt` 16, `NotificationIntelligence.kt` 15, `VaultContracts.kt` 10 |

Cross-check: `grep -rna --include='*.kt' -E 'const[[:space:]]+val[[:space:]]' . | grep '/src/main/'` returns **220**. The scan and the grep agree.

Two of the 23 NEEDS-RATIONALE constants are named in a specification **without** a rationale — `MAX_SENDS_PER_WEEK` at `13`:254 (rationale cell empty) and `MIN_SESSIONS_BEFORE_INTERSTITIAL` at `15`:251 / `18`:15 (restated, never justified). The other **21** appear in no specification at all. Of the 143 exempt constants, 14 are named in a specification incidentally (route names, field names, `Mood`, `Notes`) and 129 are not.

### 4.2 The ledger's Phase 2 evidence line is wrong

`22` §Phase 2 says:

> *"The constants in `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt` carry per-constant rationale."*

Measured: that file has **15** `const val`. **4** carry a doc comment (`:27`, `:30`, `:33`, `:50`); **11 do not**:

| Line | Constant | Value | Rationale? |
|---|---|---|---|
| 35 | `MAX_SENDS_PER_DAY` | 3 | `13`:253 — "Current worst case is 5 with no cap" ✓ |
| 36 | `MAX_SENDS_PER_WEEK` | 12 | `13`:254 — **rationale cell is empty** ✗ |
| 38 | `DEFAULT_SEND_HOUR` | 9 | `13`:239 — "Reasonable default, and outside default quiet hours" ✓ |
| 39 | `MIN_INTERACTIONS_FOR_LEARNING` | 10 | `13`:240 — "Below this the histogram is noise" ✓ |
| 41 | `MIN_SESSIONS_BEFORE_INTERSTITIAL` | 3 | `15`:251, `18`:15 — **restatement only, no reason for 3** ✗ |
| 42 | `MIN_MINUTES_BETWEEN_INTERSTITIALS` | 10 | `15`:252, `18` §Cooldown — partial (replaces the old 60-second cooldown) ~ |
| 43 | `POST_LOG_GRACE_MINUTES` | 30 | `15`:240-241 — "interrupting someone immediately after they log a period is the single worst moment in this app to show an advertisement" ✓ |
| 44 | `MAX_ARBITRATION_BATCH` | 2 | `13`:270 — "This is what prevents the '5 notifications on the same morning' failure" ✓ |
| 46 | `AUTO_DISABLE_MIN_IMPRESSIONS` | 10 | `13`:255 — "Do not judge on a handful" ✓ |
| 47 | `AUTO_DISABLE_DISMISS_RATE` | 0.60 | `13`:256 — "If 60%+ are dismissed, the reminder is not wanted" ✓ |
| 51 | `DEFAULT_QUIET_HOURS_END_HOUR` | 8 | `13`:266 — "default 22:00–08:00" — the **value** is documented, the name is not ✓ |

Eight of the eleven have a usable rationale somewhere. **Two have none**: `MAX_SENDS_PER_WEEK` (empty cell at `13`:254) and `MIN_SESSIONS_BEFORE_INTERSTITIAL` (restated but never justified). **One is partial**: `MIN_MINUTES_BETWEEN_INTERSTITIALS` is justified only by what it replaced, not by why ten.

### 4.3 (a) Genuinely unexplained product constants that need a rationale — 23

These encode a product, algorithm, security or design decision. Each needs a comment or a design-document row stating **why it has that value**.

| File:line | Name | Value | Why it needs one |
|---|---|---|---|
| `core/domain/.../InsightEngine.kt:328` | `HISTORY_LIMIT` | 6 | How many cycles the trend series shows. Nothing states why 6. |
| `core/domain/.../InsightEngine.kt:329` | `DEFAULT_CYCLE_LENGTH` | 28 | The fallback cycle length when no history exists. Duplicates the engine's `DEFAULT_ASSUMED_CYCLE_LENGTH` (28) with no cross-reference. |
| `core/domain/.../InsightEngine.kt:330` | `DEFAULT_PERIOD_LENGTH` | 5 | The fallback period length when no history exists. Nothing states why 5. |
| `core/domain/.../PrivacyRules.kt:4` | `unlockGracePeriodMillis` | 5 min | How long the app lock stays unlocked. A security-relevant window with no rationale. |
| `core/domain/.../notification/NotificationIntelligence.kt:36` | `MAX_SENDS_PER_WEEK` | 12 | `13`:254 lists the value with an **empty** rationale cell. |
| `core/domain/.../notification/NotificationIntelligence.kt:41` | `MIN_SESSIONS_BEFORE_INTERSTITIAL` | 3 | Restated at `15`:251 and `18`:15; never justified. |
| `core/security/.../AesGcmPayloadCipher.kt:11` | `DEFAULT_PBKDF2_ITERATIONS` | 100 000 | A PBKDF2 work factor is exactly the kind of number that needs a recorded reason, and it is the value a security review will ask about. |
| `core/security/.../VaultContracts.kt:19` | `VAULT_KEY_BITS` | 256 | AES key size. |
| `core/security/.../VaultContracts.kt:20` | `VAULT_KEY_BYTES` | 256/8 | Derived; the derivation is the rationale. |
| `core/security/.../VaultContracts.kt:25` | `GCM_TAG_BITS` | 128 | GCM tag size. |
| `core/security/.../VaultContracts.kt:29` | `KDF_ITERATIONS` | 3 | Argon2id time cost. |
| `core/security/.../VaultContracts.kt:30` | `KDF_PARALLELISM` | 2 | Argon2id lanes. |
| `core/security/.../VaultContracts.kt:31` | `KDF_SALT_BYTES` | 16 | Argon2id salt size. |
| `core/security/.../VaultContracts.kt:32` | `KDF_OUTPUT_BYTES` | 32 | Argon2id output size. |
| `core/designsystem/.../CycleCareTokens.kt:224` | `DURATION_SHORT` | 100 | Animation duration; the sibling tokens `:239-248` all carry comments, these three do not. |
| `core/designsystem/.../CycleCareTokens.kt:225` | `DURATION_MEDIUM` | 200 | As above. |
| `core/designsystem/.../CycleCareTokens.kt:226` | `DURATION_LONG` | 350 | As above. |
| `core/data/.../BackupPayloadSerializer.kt:22` | `CURRENT_PAYLOAD_VERSION` | 1 | The backup format version. Bumping it is a breaking change; nothing says so here. |
| `core/data/.../ExportRepository.kt:59` | `BACKUP_SCHEMA_VERSION` | 1 | As above. |
| `core/data/.../ExportRepository.kt:60` | `BACKUP_KEY_VERSION` | 1 | Key-rotation version. |
| `app/.../ReminderChannels.kt:19` | `currentChannelSchemaVersion` | 2 | The notification-channel schema version. |
| `app/.../ReminderRetryScheduler.kt:21` | `RETRY_DELAY_MILLIS` | 5 min | The retry backoff. |
| `feature/logging/.../LoggingScreen.kt:89` | `MAX_SYMPTOM_NOTE_LENGTH` | 500 | The free-text note limit. A privacy-relevant cap with no rationale. |

One further constant is **partially** documented and should be tightened rather than added:
`core/domain/.../notification/NotificationIntelligence.kt:42` `MIN_MINUTES_BETWEEN_INTERSTITIALS = 10` — `15`:252 states it and `18` §Cooldown justifies it only by the 60-second cooldown it replaced, not by why ten. It is counted as design-doc above, and the rationale should be made explicit.


### 4.4 F-03 — an undocumented product range duplicated across four modules

The cycle-length range `20..45` is a product rule with **no named constant** and **no rationale anywhere**:

| File:line | Form |
|---|---|
| `core/domain/.../CycleRecordValidator.kt:13` | `private val validCycleLengthRange: IntRange = 20..45` |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:304` | `cycleLength !in 20..45` |
| `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:193` | `cycleLength !in 20..45` (and the literal `20, 45` passed as format args) |
| `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingSteps.kt:97` | `coerceIn(20, 45)` |
| `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:74` | `coerceIn(20, 45)` |
| `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:32` | `cycleLength in 20..45` |

The period-length range `2..10` is likewise duplicated (`CycleRecordValidator.kt:14`, `OnboardingUiState.kt:34`).

Neither range appears in any document under `CycleCare-Redesign-Docs/` (grep for `20 to 45`, `45 days`, `20 days`, `between 20` returns nothing).

**This is a Phase 2 constants problem, not a Phase 6 polish problem**, and it is worse than a missing comment: six independent copies of one product rule, in four modules, with no single definition and no reason. A `const val MIN_CYCLE_LENGTH_DAYS = 20` / `MAX_CYCLE_LENGTH_DAYS = 45` pair in `core:model` (or `core:domain`) with a rationale would close it.

**It also contradicts the domain.** `CycleRecordValidator.kt:47-49` says: *"cycleLength is deprecated and is no longer range-checked: it is never read as a cycle length now that the domain derives it from consecutive starts, and stored values can legitimately disagree with the true gap."* The domain therefore accepts any stored length, while three UI modules refuse to let the user enter one outside 20..45. Either the range is a real product rule and the domain comment is wrong, or it is a legacy UI constraint and the column should not be surfaced at all. **That is a decision the lead must take**, because it determines whether the P2-03 work removes the editor's cycle-length field.

### 4.4a F-17 — product thresholds carried as function-local literals

Outside the `const val` set, seven function-local `val`s carry product thresholds with no rationale and no name:

| File:line | Literal | What it decides |
|---|---|---|
| `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingUiState.kt:269` | `val base = 75` | The base of the daily check-in score |
| `feature/logging/.../LoggingUiState.kt:270` | `completedSections * 4` | Points per completed symptom section |
| `feature/logging/.../LoggingUiState.kt:271` | `(streak * 2).coerceAtMost(10)` | Points per consecutive day, capped |
| `feature/logging/.../LoggingUiState.kt:443` | `val fertileStart = 12` | The cycle day at which the fertile window is said to begin (see 3.4 / F-16) |
| `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:116` | `val cyclesNeeded = 2` | Cycles needed before the trends unlock — duplicated from `InsightEngine.kt:64` |
| `feature/insights/.../InsightsUiState.kt:118` | `val daysNeeded = 5` | Days needed before symptom insights unlock — duplicated from `InsightEngine.kt:65` |
| `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:183` | `cyclesNeeded: Int = 3` | The "N of M cycles logged" figure on the Home insight teaser (`HomeRoute.kt:188`, `:295`) |

Two of these are worth naming explicitly:

- The Insights depth gates (`2`, `5`) duplicate `InsightEngine.kt:64-65` as literals in the feature layer. If the engine's thresholds move, the UI's do not follow, and the "unlock" copy and the engine's own gate will disagree.
- **Home says "N of 3 cycles logged" and Insights says "N of 2 cycles logged"**, while the engine unlocks at 2 (`InsightEngine.kt:64`). With exactly two logged cycles, Insights unlocks and Today's teaser says to keep logging. Home's `HomeUiState.cyclesNeededForInsights = 3` (`HomeUiState.kt:53`, set at `HomeRoute.kt:301`) is **dead** — no code in `feature/home` reads it; the figure that reaches the user comes from the local `cyclesNeeded = 3` at `HomeRoute.kt:183`.

The streak scoring (`base = 75`, `+4/section`, `+2/streak` capped at 10) is also the mechanism behind the streak copy that ambiguity **A2** in `22` §Cross-phase ambiguities is about; it has no recorded rationale and no test in this audit.

### 4.5 F-04 — a grep-based checker will silently miss `VaultContracts.kt`

`core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt` contains a **raw NUL byte** at offset 6707:

```
passphrase.indices.forEach { passphrase[it] = '<NUL>' }
```

`file` reports the file as `data`, and `grep` classifies it as binary and skips it entirely (the earlier `grep -rn` output ends with *"Binary file … matches"*). Consequences:

- A `grep`-based P2-01 checker will find **210** constants, not 220, and will not see any of the 10 `VaultContracts.kt` constants — including the six Argon2id parameters.
- A grep-based P2-02/P2-03 checker will also miss that file.

The audit's own numbers above were produced with `grep -a` (treat as text) and with a byte-level Python scan, which is why they are 220.

**The P2-01 checker must parse bytes, not grep.** The literal should also be written as `'\u0000'` rather than a raw NUL byte.

### 4.6 (b) Self-evident constants a checker should exempt by rule — 143

All of the following are identifiers, not decisions. A checker should exempt them **by rule**, not by an explicit allow-list of 143 names, so the exemption survives renames.

| Rule | Count | Examples |
|---|---|---|
| Compose test tag | **100** | `HomeTags.*` (17), `PrivacyComponents` tags (16), `SettingsUiState` tags (9), `CalendarUiState` tags (5), `LoggingUiState` tags (5), `InsightsUiState` tags (6), `OnboardingUiState` tags (6), `AppLockGate` tags (3), `CycleCareApp` tags (2), `CalendarRoute`/`HomeRoute`/`LoggingRoute`/`OnboardingRoute` state keys (32) |
| Intent / data extra key | **17** | `ReminderAlarmFacade.key*` (10), `ReminderDeliveryWorker.key*` (10 → 10 counted above), `ReminderNavigation.Target/EventId/TargetEpochDay` (3) |
| Storage / preference / work identifier | **13** | `notificationPermissionPrefsName`, `notificationPermissionRequestedKey`, `FIRST_RUN_NUDGE_SHOWN_KEY`, `breadcrumbKey`, `SETTINGS_ROUTE`, `BACKUP_RECORD_ID`, `IsoDateFormatHint`, `SCHEDULER_TAG`, `legacyDeliveryWorkTag`, `periodicWorkName`, `immediateWorkName`, `remindersGroupKey` |
| External AdMob unit id | **6** | `PROD_*_AD_UNIT_ID`, `TEST_*_AD_UNIT_ID` |
| Unit conversion | **2** | `MILLIS_PER_MINUTE` (`app/.../InterstitialCoordinator.kt:160`, `core/domain/.../ads/InterstitialGate.kt:206`) |
| Miscellaneous identifiers | **5** | `EditorVisible` / `EditorExpanded` / `EditorEditingId` / `EditorStartDateText` / `EditorEndDateText` / `EditorCycleLengthText` / `EditorNotes` variants already counted above; `Month`, `SelectedDate`, `AnchorDayOfMonth` |

The cleanest rule for a checker is structural, not name-based: **exempt a `const val` whose value is a `String` that is used only as a key, tag, name or identifier, and exempt a `const val` that is a pure unit conversion.** Everything else needs either an adjacent doc comment or a design-document row.

### 4.7 The verification procedure must survive `VaultContracts.kt`

The ledger's P2-01 procedure ("a new test in `core/domain` that parses the main source sets, finds every top-level `const val` and `val` with a literal initialiser, and fails the build for any that is not covered") is sound. Three requirements the implementer should honour:

1. **Read bytes, not lines through a text filter.** The NUL byte at `VaultContracts.kt:207` breaks naive tooling (4.5).
2. **Distinguish the stored column from same-named values.** See 3.4 Layer 2.
3. **Report the exemption reason.** The ledger requires the report to list the exempted constants with a reason; the structural rules in 4.6 give that reason automatically.

---

## Area 5 — Domain contracts as Kotlin interfaces

### 5.1 Verification

**The claim holds.** Evidence:

- **Zero Java files** in the repository: `find . -name '*.java' -not -path '*/build/*'` returns nothing.
- **Zero typealiases**: `grep -rn --include='*.kt' 'typealias ' .` returns nothing.
- **42 interfaces** across the main source sets, all Kotlin:

| Module | Interfaces |
|---|---|
| `core/domain` | **20** |
| `core/database` | 9 (Room DAOs) |
| `core/security` | 3 |
| `app` | 7 |
| `core/designsystem` | 1 (`UiText`, `sealed interface`) |
| `feature/export` | 1 (`ExportStatus`, `sealed interface`) |
| `feature/privacy` | 1 (`SecureAuthController`) |
| **Total** | **42** |

- **`core:model` and `core:domain` are pure JVM.** `core/model/build.gradle.kts` and `core/domain/build.gradle.kts` both apply `alias(libs.plugins.kotlin.jvm)` with `jvmToolchain(17)`; neither declares the Android plugin. `core:domain` depends only on `:core:model` and kotlinx-coroutines-core.
- **They compile.** `./gradlew :core:domain:compileKotlin :core:model:compileKotlin --offline` → BUILD SUCCESSFUL in the worktree at `ec133f2`.
- **They are tested.** `./gradlew :core:domain:test --offline` → 129 tests, 0 failures, 0 errors, 0 skipped.

### 5.2 The contracts in `Repositories.kt`

`core/domain/src/main/java/com/cyclecare/core/domain/Repositories.kt` declares **13 interfaces**, all Kotlin:

| Line | Interface | Line | Interface |
|---|---|---|---|
| 20 | `ProfileRepository` | 77 | `PrivacyRepository` |
| 26 | `CycleRepository` | 108 | `ExportRepository` |
| 32 | `SymptomRepository` | 114 | `AppResetRepository` |
| 38 | `PredictionRepository` | 118 | `ReminderScheduler` |
| 42 | `InsightRepository` | 122 | `SyncAdapter` |
| 46 | `ReminderRepository` | | |
| 51 | `ReminderChannelSettingsRepository` | | |
| 56 | `ReminderEventsRepository` | | |

**One non-interface contract type in the same file**: `ImportResult` at `:93` is a `sealed class` with nested `Success` / `Failure` (`:94`, `:100`). A sealed class is a valid Kotlin contract, but it is **not** an interface, and it is the only one in the file. If the criterion is read strictly as "all contracts are interfaces", `ImportResult` is the single exception in `Repositories.kt`.

### 5.3 Contracts outside `Repositories.kt`

7 more in the domain module:

| File:line | Contract | Kind |
|---|---|---|
| `core/domain/.../Diagnostics.kt:3` | `CrashReporter` | interface |
| `core/domain/.../Diagnostics.kt:27` | `AppLogger` | interface |
| `core/domain/.../FirstRunNudgeRepository.kt:14` | `FirstRunNudgeRepository` | interface |
| `core/domain/.../ads/DraftTracker.kt:18` | `DraftTracker` | interface |
| `core/domain/.../ads/InterstitialStatsRepository.kt:12` | `InterstitialStatsRepository` | interface |
| `core/domain/.../ads/InterstitialGate.kt:122` | `InterstitialGateDecision` | **sealed interface** |
| `core/domain/.../time/TimeProvider.kt:19` | `TimeProvider` | interface |

And 15 more outside the domain module (3 in `core:security`, 9 Room DAOs in `core:database`, 7 in `app`, 2 in features).

**No contract anywhere is a Java interface, a typealias, an abstract class, or a class standing in for an interface.** The two `sealed interface`s (`InterstitialGateDecision`, `UiText`) and the one `sealed class` (`ImportResult`) are idiomatic Kotlin contract types.

---

## Implementation plan — P2-02 (`confidenceLabel`)

### The design decision to make first

The ledger's definition of done (`22` §P2-02) says the snapshot must carry *"a machine-readable confidence value defined in `core:model` — an enum or a sealed type"*. `11` §7 says *"`confidenceLabel: String` is removed; `quality: EstimateQuality` replaces it."* The two are compatible only if the model-level mirror is used. Two options:

**Option A (recommended) — a dedicated `PredictionConfidence` enum in `core:model`.**

- Matches the ledger's wording exactly.
- One field on the snapshot; the UI reads one thing.
- Costs one new enum and one new mapping function.

**Option B — delete `confidenceLabel` and derive from the existing `quality` + `warnings`.**

- `quality` gives INSUFFICIENT_HISTORY / LOW / MODERATE / HIGH; `warnings.contains(STALE_HISTORY)` gives staleness.
- No new type; but the "stale wins over the tier" rule — which currently lives in the domain — moves into the feature layer, and it would have to be reimplemented by every feature that ever shows a confidence label.
- Note that `quality` and `warnings` are currently read by nobody (F-08), so Option B also revives two dead fields.

**Recommendation: Option A.** It keeps the branch order in the domain, where the current behaviour lives, and it matches `22`'s wording.

### Step-by-step

Ordered by dependency. Each step compiles on its own.

**Step 1 — `core/model/src/main/java/com/cyclecare/core/model/CycleModels.kt`**

1. Add, next to `PredictionQuality` (`:192`):

   ```kotlin
   /**
    * How confident the estimate is, as a machine-readable value.
    *
    * Replaces the old `confidenceLabel: String`, which carried English
    * prose out of the domain. The UI maps this to a localised string.
    */
   enum class PredictionConfidence { BASELINE, STALE, NARROW, MODERATE, WIDE }
   ```

2. Replace `:156` `val confidenceLabel: String,` with `val confidence: PredictionConfidence = PredictionConfidence.BASELINE,`.

   **Give it a default.** `quality` (`:173`), `phase` (`:174`), `warnings` (`:175`), `overdueDays` (`:176`) and `algorithmVersion` (`:177`) already have defaults, so this is consistent with the file's style, and it means the **14 fixture call sites do not need to change** (see Step 7 for the consequence).

   If the lead prefers the field to be required, then **all 14 fixture sites listed in 1.2 must be updated** as well.

*Dependency: none. This is the first step and it blocks nothing else except the mapper.*

**Step 2 — `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionSnapshotMapper.kt`**

1. `:29` — `confidenceLabel = confidenceLabel(),` → `confidence = confidence(),`
2. `:69-75` — replace the private extension with a non-English one, **preserving the branch order exactly**:

   ```kotlin
   private fun CyclePrediction.confidence(): PredictionConfidence = when {
       quality == EstimateQuality.INSUFFICIENT_HISTORY -> PredictionConfidence.BASELINE
       isStale -> PredictionConfidence.STALE
       quality == EstimateQuality.HIGH -> PredictionConfidence.NARROW
       quality == EstimateQuality.MODERATE -> PredictionConfidence.MODERATE
       else -> PredictionConfidence.WIDE
   }
   ```

3. Add `import com.cyclecare.core.model.PredictionConfidence`.
4. The KDoc at `:61-68` must be rewritten: the "Wording the user actually sees" framing goes away, and the "Deliberately never says 'accurate' or 'certain'" note moves to the feature layer, where the words now live.

*Dependency: Step 1. Blocks Steps 3-7.*

**Step 3 — `feature/home/src/main/res/values/strings.xml`** (new strings; none of the five labels exist in any `strings.xml` today — verified)

```xml
<string name="confidence_baseline">Baseline estimate</string>
<string name="confidence_stale">Estimate may be out of date</string>
<string name="confidence_narrow">Narrow range</string>
<string name="confidence_moderate">Moderate range</string>
<string name="confidence_wide">Wide range</string>
```

*Dependency: none, but do it before Step 4 so the feature layer compiles in one pass.*

**Step 4 — `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt`** (4 occurrences)

1. `:32` — `val confidenceLabel: String = ""` → `val confidence: PredictionConfidence? = null` (null = no confidence available; today the empty string means the same thing).
2. Add a mapping helper next to `formatCycleProgressLabel` (named `confidenceUiText`, not `confidenceText`, so it does not shadow the composable parameter of the same name in Step 6):

   ```kotlin
   internal fun confidenceUiText(confidence: PredictionConfidence?): UiText? = when (confidence) {
       null -> null
       PredictionConfidence.BASELINE -> uiText(R.string.confidence_baseline)
       PredictionConfidence.STALE -> uiText(R.string.confidence_stale)
       PredictionConfidence.NARROW -> uiText(R.string.confidence_narrow)
       PredictionConfidence.MODERATE -> uiText(R.string.confidence_moderate)
       PredictionConfidence.WIDE -> uiText(R.string.confidence_wide)
   }
   ```

3. `:178-192` — `formatCycleProgressLabel(cycleDay, cycleLength, confidenceLabel: String)` → the third parameter becomes `confidence: PredictionConfidence?`, and the body maps it through `confidenceUiText(...)`. The `.isBlank()` test becomes a `null` test.
4. Add `import com.cyclecare.core.model.PredictionConfidence`.

*Dependency: Steps 1 and 3.*

**Step 5 — `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt`** (4 occurrences)

1. `:117-131` — `buildVariabilityText(insights, confidenceLabel: String)` → `confidence: PredictionConfidence?`.
2. `:122-126` — delete the `UiText.Raw(it)` path and the comment admitting it is a known boundary gap. Replace with `confidenceUiText(confidence) ?: uiText(R.string.from_your_setup)`.
3. `:261` — `confidenceLabel = base.prediction?.confidenceLabel.orEmpty()` → `confidence = base.prediction?.confidence`.
4. `:265` — same.

*Dependency: Step 4. **This is the step that actually closes the criterion**: after it, no production code reads an English confidence string.*

**Step 6 — the three remaining Home files** (9 occurrences)

| File | Lines | Change |
|---|---|---|
| `feature/home/.../HomeTeaserCards.kt` | 112 | `confidenceLabel: String` → `confidenceText: UiText?` |
| | 139-140 | `if (confidenceLabel.isNotBlank()) { DashboardPill(text = confidenceLabel) }` → `confidenceText?.let { DashboardPill(text = it.resolve()) }` |
| `feature/home/.../HomeScreen.kt` | 175 | `remember(uiState.cycleDay, uiState.cycleLength, uiState.confidenceLabel)` → `uiState.confidence` |
| | 179 | pass `confidence = uiState.confidence` |
| | 316 | `confidenceLabel = uiState.confidenceLabel` → `confidenceText = confidenceUiText(uiState.confidence)` |
| `feature/home/.../HomeCycleStatusScreen.kt` | 147 | `formatCycleProgressLabel(uiState.cycleDay, uiState.cycleLength, uiState.confidenceLabel)` → `uiState.confidence` |
| | 380-383 | `if (uiState.confidenceLabel.isNotBlank()) { MetricPill(text = uiState.confidenceLabel) }` → `confidenceUiText(uiState.confidence)?.let { MetricPill(text = it.resolve()) }` |

*Dependency: Step 4 (the `confidenceText` helper must exist).*

**Step 7 — fixtures (conditional)**

If Step 1 gave the field a default, **no fixture needs to change to compile**. Two consequences to decide:

- `core/domain/src/test/.../ReminderSchedulePlannerTest.kt:287` and `app/src/androidTest/.../ReminderAlarmSchedulerTest.kt:318` currently set `confidenceLabel = "Stable"`, which is **not one of the five labels the domain can produce**. With the default they silently become `BASELINE`. Nothing asserts on them (`ReminderSchedulePlanner` does not read the field), so this is harmless — but it should be checked and recorded.
- `feature/home/src/test/.../HomePresentationTest.kt:36` asserts `formatCycleProgressLabel(cycleDay = 6, cycleLength = 28, confidenceLabel = "Baseline estimate")` equals `uiText(R.string.day_slash_total_with_confidence, "6", 28, "Baseline estimate")`. **This test must change** — the third argument becomes `PredictionConfidence.BASELINE` and the expected value becomes `uiText(R.string.day_slash_total_with_confidence, "6", 28, uiText(R.string.confidence_baseline))`.

**If the field is required rather than defaulted, all 14 fixture sites in 1.2 must be updated**, replacing the string with the corresponding enum constant.

*Dependency: Step 1.*

**Step 8 — the test that proves it**

`feature/home/src/test/java/com/cyclecare/feature/home/HomePresentationTest.kt` — a new case that covers **every** case of the new enum, as the ledger requires (*"each confidence value renders to a distinct, non-empty label, so the mapping cannot silently collapse to one string"*):

```kotlin
@Test
fun everyConfidenceValueMapsToADistinctNonEmptyLabel() {
    val labels = PredictionConfidence.entries.map { confidenceUiText(it) }
    assertThat(labels).containsNoNulls()
    // UiText.resolve() is @Composable, so a JVM unit test cannot call it.
    // Assert on the resource identity instead.
    assertThat(labels.map { (it as UiText.Resource).resId }.toSet())
        .hasSize(PredictionConfidence.entries.size)
}
```

`UiText.resolve()` is a `@Composable` function (`core/designsystem/.../UiText.kt:65`), so a JVM unit test **cannot** call it. The assertion must be made against the `UiText` **identity** (`UiText.Resource(resId, args)`) rather than the resolved string. A test that needs the rendered text has to be an instrumented test.

**Step 9 — the verification the ledger specifies**

`grep -rn --include='*.kt' 'confidenceLabel' core/model/src/main core/domain/src/main app/src/main feature/*/src/main` must return **nothing**. Note that this scope includes the **three preview fixtures** (`HomePreviews.kt:77`, `CalendarPreviews.kt:38`, `LoggingPreviews.kt:82`), which live in `src/main` — so with the defaulted field they still contain the *string* `"Baseline estimate"` but not the *identifier* `confidenceLabel`, and the grep is satisfied. If the lead prefers the preview fixtures to exercise the real value, set `confidence = PredictionConfidence.BASELINE` explicitly in those three.

Also re-run `./gradlew :core:domain:test :feature:home:testDebugUnitTest` — the domain test is affected by `ReminderSchedulePlannerTest.kt:287`, the Home test by `HomePresentationTest.kt:36`.

### Cross-workstream conflicts in P2-02

| Agent | Conflict |
|---|---|
| **Phase 4 (prediction engine)** | `PredictionSnapshotMapper.kt` is the adapter between the domain contract and the UI. `11` §7 says the snapshot is "Replaced by `CyclePrediction`" and that consumers should migrate "in one change, never both shapes at once". If the Phase 4 agent deletes `PredictionSnapshot` entirely and moves the features onto `CyclePrediction`, then P2-02's Step 1 (adding `PredictionConfidence` to `core:model`) and Step 2 (the mapper) become dead code. **Coordinate: decide before either starts.** |
| **Phase 4** | `CyclePrediction` is in `core:domain.prediction` and carries `quality: EstimateQuality` and `isStale: Boolean` directly. `core:domain` cannot be referenced from `core:model`, so if the features move to `CyclePrediction`, the UI needs its own model-level mirror — which is exactly what `PredictionConfidence` in `core:model` is. The two changes converge; they must not race. |
| **Phase 5/6 (feature UI)** | Steps 4-6 touch 5 Home files. `HomeScreen.kt` is 557 lines and `HomeRoute.kt` is 577 lines — both within 20 lines of the `15` §14.14 600-line limit that **currently fails** (`22` §P6-01, `CycleCareShell.kt` at 620). Editing these files can push them over 600. **The Phase 6 agent must sequence its split after or together with P2-02.** |
| **Phase 4 (P4-03)** | The "no banned phrases" test for the prediction engine's output copy (`22` §P4-03) is explicitly blocked on this same work: *"If the domain keeps producing English strings, this review has to be repeated every time they change."* P2-02 unblocks it. |
| **Phase 3 (migration)** | None. `PredictionSnapshot` is not persisted. |

---

## Implementation plan — P2-03 (`cycleLength` as an interval)

### The decision to make first

Two decisions, in this order:

**Decision 1 — does the UI keep a cycle-length field at all?**

`CycleRecordValidator.kt:47-49` says the stored `cycleLength` is deprecated and not a cycle length. The UI nonetheless asks the user for one in three places (Home editor, Calendar editor, Onboarding slider) and range-checks it in three more. If the column is not a cycle length, the **editors' cycle-length field is the thing to remove**, and sites 8, 9, 13 in the 2.1 table disappear with it. If the column *is* still what the user means by "my cycle length", then the deprecation comment is wrong and the whole Phase 3 ambiguity A1 needs revisiting.

**This decision changes the size of P2-03 by a factor of three** and it touches Phase 3 (A1), Phase 5/6 (editors) and Phase 9 (export). **It is the lead's call and it must be made before the work starts.**

**Decision 2 — which number is "Avg cycle"?**

F-10 shows Today's and Insights' "Avg cycle" come from different sources. Today reads the profile baseline or the stored column; Insights reads the extracted series. Until one of them changes, "Today and Insights can never contradict each other" is false regardless of what is done to `InsightEngine`.

### Step-by-step

**Step 1 — `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:277-280` (site 2, the retrospective fertile window)**

Replace the stored-column derivation with the extractor-derived one.

The cycle that begins at `C.startDate` has length `nextStart − C.startDate`, which is exactly the interval the shared extractor produces. So the retrospective ovulation becomes `nextStart − 14`, using the engine's own prior constant:

```kotlin
private fun fertileWindowFor(
    cycle: CycleRecord,
    nextStart: LocalDate?,
): Pair<LocalDate, LocalDate>? {
    if (nextStart == null) return null
    val ovulationDate = ovulationFrom(nextStart)   // nextStart - LUTEAL_PHASE_DEFAULT_DAYS
    return ovulationDate.minusDays(4) to ovulationDate.plusDays(1)
}
```

and the call site at `:185-190` supplies `nextStart` from a map built from `extractIntervals(observedStartsOf(cycles))`, keyed by `fromRecordId`.

Two consequences to handle:

- **The last observed cycle has no retrospective fertile window**, because its length is not derivable from starts — that is precisely what the deprecation means. `buildFertileWindowHighlight` must skip it (return the highlight only if at least one *earlier* cycle has a window). This changes the highlight's content for users whose last cycle is the only one with data.
- **Replace the hardcoded `14L` with `LUTEAL_PHASE_DEFAULT_DAYS`** from `PredictionContracts.kt:48`, so the two sides cannot drift apart again.

The window *shape* (−4/+1) should be reviewed at the same time: the engine's real fertile window is `±(8 + halfWidth)` (`CyclePredictionEngineV2.kt:119-124`) and the `−4/+1` shape is the mapper's *fallback* (`PredictionSnapshotMapper.kt:85-89`). Keeping two shapes is itself an inconsistency (F-12).

*Dependency: none. Domain-only.*

**Step 2 — `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:107` (site 1, the symptom window end)**

Replace `historicalCycles.last().cycleLength` with the **recorded end date of the cycle in question**, as the ledger prescribes:

```kotlin
val last = historicalCycles.last()
val end = last.endDate ?: <fallback>
```

**The fallback needs a decision**, because `endDate` is nullable while a period is in progress (`CycleModels.kt:107-108`):

- **(a)** `last.startDate` — when the last cycle is in progress, the window closes at its start. Simple, but it silently drops every symptom logged during the current cycle. This would change what the symptom insights see for every user whose period is currently open.
- **(b)** the latest symptom date — clock-free and data-driven, but it makes the window depend on the data it is filtering, which is circular and can admit an out-of-window symptom.
- **(c)** add a `today` parameter to `InsightEngine.build(...)` — the engine currently has no clock, and `11` §8.7 requires that the domain module never reads an ambient clock. Adding an injected `today` is the cleanest, and `core/domain` already has `TimeProvider` (`core/domain/.../time/TimeProvider.kt:19`) for exactly this.

**Recommendation: (c)**, with (a) as the fallback when no clock is supplied. (b) should be rejected.

*Dependency: (c) changes `InsightEngine.build`'s signature, so every caller must be updated — `core/data/.../InsightRepository.kt` (the production caller) and `core/domain/src/test/.../InsightEngineTest.kt` (4 call sites).*

**Step 3 — `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:245` (site 12, the "Avg cycle" fallback)**

```
val cycleLength = base.profile?.averageCycleLength ?: base.cycles.lastOrNull()?.cycleLength ?: 28
```

This is the only site where the stored column is read as a **future** estimate (class b), and it is the one that makes F-10 live.

It must stop reading the stored column. What it should read instead is Decision 2:

- **If Today is to agree with Insights:** `insights.averageCycleLength`, which `HomeViewModel` already has (`HomeRoute.kt:224` combines `insightRepository.observeInsights()`).
- **If Today is to agree with the prediction engine:** a new `centerCycleLength` on `PredictionSnapshot` — but then Insights' plain mean must change to the same number, or the two screens will still disagree (D1, D2).

**Recommendation: make one definition the source of both.** Concretely, add a single shared derivation in `core/domain` — for example `core/domain/prediction/CycleStatistics.kt`, built on `extractIntervals(observedStartsOf(...))` — that returns the canonical cycle-length series and its summary; have `InsightEngine` use it, have the prediction engine use it, and have both screens read the resulting figure. That is the only shape in which "can never contradict" becomes true rather than merely likely.

*Dependency: this touches the prediction engine (Phase 4) and both feature modules (Phase 5/6). It must not be done piecemeal — a partial change leaves the contradiction in place.*

**Step 4 — the Calendar and Export surfaces (sites 3, 10, 11)**

`feature/calendar/.../CalendarUiState.kt:159-160` and `:304-314`, and `core/data/.../ExportRepository.kt:365`, all present the stored value to the user as a fact about a completed cycle.

- **Classification: (a)** — a fact about a *completed historical cycle*, recorded by the user at logging time. This is the one category where the criterion arguably does not bite, because the value is not being used as an *interval* to derive anything.
- **But** it is the value the domain has declared unreliable, and it will disagree with the derived figure shown elsewhere. `ExportRepository.kt:365` is the sharpest case: the exported report prints both `(${cycle.cycleLength} day cycle)` per cycle **and** `Average cycle length: ${profile.averageCycleLength}` in the same document.

**Recommendation:** leave the Calendar history label alone for this phase (it is a historical record, and `05` separates observed from derived), but fix the export report to label the per-cycle number for what it is — the value the user recorded — and to use the derived figure for the report's "average cycle length" line. Alternatively, remove the per-cycle number from the export entirely.

**This decision touches `05` §8 and `15`, and the export format, so it is a Phase 9 / Release-readiness item as much as a Phase 2 one.**

**Step 5 — the persistence sites (4, 5, 6, 7) must not change.**

`BackupPayloadSerializer.kt:51` and `EntityMappers.kt:40`/`:52` carry the stored value across the backup and the database boundary. `22` §P3-02 depends on the value surviving. **A source-scanning checker must exempt the persistence layer.** The ledger's own P4-04 wording says "outside the persistence layer", which is right, but the exemption must be by *path*, not by name.

**Step 6 — the editors (sites 8, 9, 13) — depends on Decision 1**

If the cycle-length field is removed from the editors, these three sites go. If it stays, they stay, and the range needs a named constant and a rationale (F-03).

### The tests that would prove P2-03

**T1 — the retrospective fertile window equals the extractor's.** In `core/domain/src/test/java/com/cyclecare/core/domain/InsightEngineTest.kt`:

```kotlin
@Test
fun retrospectiveFertileWindowMatchesTheSharedExtractor() {
    val cycles = fixture()   // stored cycleLength values deliberately wrong
    val snapshot = InsightEngine().build(cycles, symptoms)
    val expected = extractIntervals(observedStartsOf(cycles))
        .associate { it.fromRecordId to it.toRecordId }
    // assert the highlight's window for each cycle equals
    //   ovulationFrom(nextStart) - 4 .. ovulationFrom(nextStart) + 1
}
```

**T2 — the stored column cannot influence any output.** Mutate only the stored `cycleLength` on every fixture record, rebuild, and assert the entire `InsightSnapshot` is equal. `InsightEngineTest.kt:18-33` does this for `recentCycleLengths` and `averageCycleLength` only; extend it to `phaseHighlights` and `narrativeSummary`.

**T3 — the source scan (P4-04).** A test in `core/domain` that fails on any read of `CycleRecord.cycleLength` outside `core/data/src/main/java/com/cyclecare/core/data/{EntityMappers,BackupPayloadSerializer}.kt` and `core/database/`. It must match `CycleRecord.cycleLength` / `.cycleLength` on a `CycleRecord`-typed receiver, **not** the bare word `cycleLength`: the word appears on **122 lines** across the main source sets, and only **20** of those read the stored column. A substring match would produce false positives on the other 102 (see 2.1 and 3.5 Layer 2).

**T4 — the cross-screen guarantee.** The assertion in 3.4 Layer 3. It fails today and cannot pass until Step 3 is done.

**T5 — the `06` §2 scenario.** *"Editing an old record invalidates and recomputes dependent estimates"*, which the ledger asks to be asserted at the same time as P2-03. It is the gate G12 scenario (`22` §P4-01) and it is currently **Not implemented**.

### Cross-workstream conflicts in P2-03

| Agent | Conflict |
|---|---|
| **Phase 4 (prediction engine)** | Step 3 changes what the prediction engine's centre means for the UI, and Step 1 changes the retrospective fertile window. If the Phase 4 agent lands the five missing corpus gates (`22` §P4-01) against the current constants, the constants may move, and Step 3's shared definition must be written against the final constants. **P4-01 should land before P2-03 Step 3.** |
| **Phase 4** | `11` §8.9 (criterion 9) is recorded as *partially complete* precisely because of `InsightEngine.kt:107` and `:278`. **This work is a prerequisite for closing `11` §8.9** and for `22` §P4-04. |
| **Phase 3 (migration)** | **P3-02 and A1 depend on the stored value surviving** (Steps 4-7 above). The migration already documents this at `Migrations.kt:88-91`. **Do not remove or repurpose the column in Phase 2.** If Decision 1 removes the editors' cycle-length field, the column becomes write-only apart from the backup — and then **P3-02's `legacyCycleLength` question becomes live**: the value is only preserved in `cycleLength` itself, never in a `legacyCycleLength` column. A1 must be revisited before the migration layer is touched. |
| **Phase 3** | The schema and the backup format must not change for Phase 2. `BackupPayloadSerializer.kt:22` `CURRENT_PAYLOAD_VERSION = 1` must not be bumped by this work. |
| **Phase 7 (notifications)** | `core/domain/.../ReminderSchedulePlanner.kt` takes a `PredictionSnapshot` (`:21`, `:72`, `:111`, `:136`, `:179`) and reads `nextPeriodWindow`, `nextPeriodDate`, `fertileWindow`, `cycleDay`. **It does not read `confidenceLabel` and does not read `cycleLength`**, so P2-02 and P2-03 do not touch it. But if the snapshot's shape changes (Decision on `PredictionSnapshot` being replaced by `CyclePrediction`, `11` §7), the planner must change with it — and the Phase 7 agent is currently the one wiring `NotificationIntelligence` into the delivery path (`22` §P7-01). **Coordinate the snapshot's final shape before P7-01 lands.** |
| **Phase 5/6 (feature UI)** | Steps 3, 4 and 6 touch `HomeRoute.kt` (577 lines), `HomeScreen.kt` (557) and `CalendarUiState.kt` (439). `15` §14.14 currently fails on `CycleCareShell.kt` (620 lines) and `22` §P6-01 is open. **All of these edits must be sequenced with the P6-01 split.** |
| **Phase 9 (release readiness)** | The export change in Step 4 changes what the exported report says. `22` §P9-01 records that the policy page should be finalised after Phase 7; the export change should be included in that sequencing. |

---

## Cross-workstream dependencies

### Blocking order

```
P4-01 (five missing corpus gates)      ─┐
                                        ├─► P2-03 Step 3 (one shared cycle-length definition)
P2-02 Step 1-2 (core:model + mapper)   ─┘
                                                 │
                                                 ├─► P2-03 Step 1-2 (InsightEngine)
                                                 ├─► P4-02 / P4-03 (estimate framing, copy review)
                                                 ├─► P7-01 (notification intelligence wired in)
                                                 └─► P6-01 (file split) — coordinate file-level edits
```

### By agent

| Agent | Depends on | What must happen first |
|---|---|---|
| **Phase 4 — prediction engine** | P2-02 Steps 1-2; P2-03 Step 1 | The **snapshot's final shape** must be decided: does `PredictionSnapshot` survive, or do the features move onto `CyclePrediction` (`11` §7)? P2-02 adds `PredictionConfidence` to `core:model`; if Phase 4 deletes `PredictionSnapshot`, that type is dead on arrival. Also: P4-01's gates may move the constants that P2-03 Step 3 must be written against. |
| **Phase 3 — migration** | P2-03 Steps 4-7 (persistence must not change); Decision 1 | The stored column must survive Phase 2 untouched. If Decision 1 removes the editors' cycle-length field, the column becomes backup-only and **ambiguity A1 / P3-02 must be re-decided before the migration layer is touched**. The backup payload version must not be bumped. |
| **Phase 7 — notifications** | P2-02 Step 2 (the mapper) | `ReminderSchedulePlanner` consumes `PredictionSnapshot`. It reads neither `confidenceLabel` nor `cycleLength`, so it is not blocked by the *content* changes — but it **is** blocked by a *shape* change. P7-01 must not land against a snapshot shape that is about to change. |
| **Phase 5/6 — feature UI** | P2-02 Steps 4-6; P2-03 Steps 3, 4, 6 | Five Home files and two Calendar files are edited by both. `HomeRoute.kt` (577) and `HomeScreen.kt` (557) are within 23 and 43 lines of the 600-line limit that **already fails** elsewhere. **Sequence the P6-01 split with, or after, this work — not before.** |
| **Phase 9 — release readiness** | P2-03 Step 4 (export) | The exported report's content changes. `22` §P9-01 already sequences the privacy policy after Phase 7; the export change belongs in that sequence. |
| **Phase 4 — P4-03 (copy review)** | P2-02 | Explicitly blocked on this work. Once the domain emits no English, the copy review becomes a one-time review instead of a recurring one. |

### Conflicts a lead must arbitrate

1. **`PredictionSnapshot` survives, or does not?** `11` §7 says it is "Replaced by `CyclePrediction`". P2-02 adds a field to it. Both cannot be right without a decision.
2. **Which number is "Avg cycle"?** Today, Insights and the prediction engine currently have three different definitions. Only one can be displayed.
3. **Does the UI keep a cycle-length field?** This determines whether the stored column is user-facing at all, and therefore whether A1 must be re-opened.
4. **What is the retrospective fertile window for the most recent cycle?** It is not derivable from starts. It must either be dropped, or take a different definition, or the criterion must be narrowed.

---

## What could not be verified from this repository

| Item | Why | Closest available evidence |
|---|---|---|
| The rendered appearance of any new confidence label | Requires a device and a locale; this audit ran no emulator | The five English strings at `PredictionSnapshotMapper.kt:70-74` are the current wording, and the mapping's branch order is preserved by the plan above |
| Whether the 14 fixture sites' `confidenceLabel` values are asserted anywhere | Read statically, not executed | No `androidTest` source set asserts on any of the five labels (grep across every `androidTest` source set returns no hit) |
| The full unit-test baseline across all modules | Only `:core:domain` was run, to avoid contending with the other agents' device and Gradle usage | `core:domain` alone: **129 tests, 0 failures** |
| The behaviour of `ReminderSchedulePlanner` with a stale or insufficient snapshot | Read, not executed | `ReminderSchedulePlanner.kt:67-68` `hasSupportableEstimate()` and `NotificationTimingTest` |
| Whether the exported report's two cycle-length figures have ever been noticed by a user | Not a repository question | `ExportRepository.kt:365` and `:371` (the `profileLines` block) |
| Real-device rendering of the "Avg cycle" metric at 200% font scale, dark mode and RTL | Requires a device | `22` §P6-02 |

---

## Appendix A — every `const val` in the main source sets

220 declarations across 39 files. "Rationale" is `doc-comment` (an adjacent KDoc or line comment above the declaration), `design-doc` (named in a specification document with a stated reason), `exempt-by-rule` (an identifier or unit conversion, per 4.6), or **NEEDS-RATIONALE**.

| # | File:line | Name | Value | Rationale |
|---|---|---|---|---|
| 1 | `app/src/main/java/com/cyclecare/app/ReminderChannels.kt:19` | **`currentChannelSchemaVersion`** | `2` | **NEEDS A RATIONALE** — no rationale in any specification |
| 2 | `app/src/main/java/com/cyclecare/app/ReminderRetryScheduler.kt:21` | **`RETRY_DELAY_MILLIS`** | `5 * 60 * 1000L` | **NEEDS A RATIONALE** — no rationale in any specification |
| 3 | `core/data/src/main/java/com/cyclecare/core/data/BackupPayloadSerializer.kt:22` | **`CURRENT_PAYLOAD_VERSION`** | `1` | **NEEDS A RATIONALE** — no rationale in any specification |
| 4 | `core/data/src/main/java/com/cyclecare/core/data/ExportRepository.kt:59` | **`BACKUP_SCHEMA_VERSION`** | `1` | **NEEDS A RATIONALE** — no rationale in any specification |
| 5 | `core/data/src/main/java/com/cyclecare/core/data/ExportRepository.kt:60` | **`BACKUP_KEY_VERSION`** | `1` | **NEEDS A RATIONALE** — no rationale in any specification |
| 6 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:224` | **`DURATION_SHORT`** | `100` | **NEEDS A RATIONALE** — no rationale in any specification |
| 7 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:225` | **`DURATION_MEDIUM`** | `200` | **NEEDS A RATIONALE** — no rationale in any specification |
| 8 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:226` | **`DURATION_LONG`** | `350` | **NEEDS A RATIONALE** — no rationale in any specification |
| 9 | `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:328` | **`HISTORY_LIMIT`** | `6` | **NEEDS A RATIONALE** — no rationale in any specification |
| 10 | `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:329` | **`DEFAULT_CYCLE_LENGTH`** | `28` | **NEEDS A RATIONALE** — no rationale in any specification |
| 11 | `core/domain/src/main/java/com/cyclecare/core/domain/InsightEngine.kt:330` | **`DEFAULT_PERIOD_LENGTH`** | `5` | **NEEDS A RATIONALE** — no rationale in any specification |
| 12 | `core/domain/src/main/java/com/cyclecare/core/domain/PrivacyRules.kt:4` | **`unlockGracePeriodMillis`** | `5 * 60 * 1000` | **NEEDS A RATIONALE** — no rationale in any specification |
| 13 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:36` | **`MAX_SENDS_PER_WEEK`** | `12` | **NEEDS A RATIONALE** — `13`:254 lists the value with an **empty** rationale cell |
| 14 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:41` | **`MIN_SESSIONS_BEFORE_INTERSTITIAL`** | `3` | **NEEDS A RATIONALE** — `15`:251 and `18`:15 restate the value; nothing states why 3 |
| 15 | `core/security/src/main/java/com/cyclecare/core/security/AesGcmPayloadCipher.kt:11` | **`DEFAULT_PBKDF2_ITERATIONS`** | `100_000` | **NEEDS A RATIONALE** — no rationale in any specification |
| 16 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:19` | **`VAULT_KEY_BITS`** | `256` | **NEEDS A RATIONALE** — no rationale in any specification |
| 17 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:20` | **`VAULT_KEY_BYTES`** | `VAULT_KEY_BITS / 8` | **NEEDS A RATIONALE** — no rationale in any specification |
| 18 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:25` | **`GCM_TAG_BITS`** | `128` | **NEEDS A RATIONALE** — no rationale in any specification |
| 19 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:29` | **`KDF_ITERATIONS`** | `3` | **NEEDS A RATIONALE** — no rationale in any specification |
| 20 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:30` | **`KDF_PARALLELISM`** | `2` | **NEEDS A RATIONALE** — no rationale in any specification |
| 21 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:31` | **`KDF_SALT_BYTES`** | `16` | **NEEDS A RATIONALE** — no rationale in any specification |
| 22 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:32` | **`KDF_OUTPUT_BYTES`** | `32` | **NEEDS A RATIONALE** — no rationale in any specification |
| 23 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingScreen.kt:89` | **`MAX_SYMPTOM_NOTE_LENGTH`** | `500` | **NEEDS A RATIONALE** — no rationale in any specification |
| 24 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:35` | `MAX_SENDS_PER_DAY` | `3` | design doc — `13`:253, `13`:415 |
| 25 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:38` | `DEFAULT_SEND_HOUR` | `9` | design doc — `13`:230, `13`:232 |
| 26 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:39` | `MIN_INTERACTIONS_FOR_LEARNING` | `10` | design doc — `13`:229, `13`:240 |
| 27 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:42` | `MIN_MINUTES_BETWEEN_INTERSTITIALS` | `10` | design doc — `15`:252 and `18` "Cooldown" — justified only by the 60 s cooldown it replaced |
| 28 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:43` | `POST_LOG_GRACE_MINUTES` | `30` | design doc — `15`:240, `18`:88 |
| 29 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:44` | `MAX_ARBITRATION_BATCH` | `2` | design doc — `13`:270 |
| 30 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:46` | `AUTO_DISABLE_MIN_IMPRESSIONS` | `10` | design doc — `13`:255 |
| 31 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:47` | `AUTO_DISABLE_DISMISS_RATE` | `0.60` | design doc — `13`:256 |
| 32 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:51` | `DEFAULT_QUIET_HOURS_END_HOUR` | `8` | design doc — `13`:266 documents the value ("default 22:00–08:00"); the name is not in any doc |
| 33 | `app/src/main/java/com/cyclecare/app/Ads.kt:78` | `AD_BANNER_TAG` | `"ad_banner"` | doc comment — Test tag for the banner container. |
| 34 | `app/src/main/java/com/cyclecare/app/ReminderAlarmScheduler.kt:213` | `legacyDeliveryWorkTag` | `"cyclecare-reminder"` | doc comment — The tag an older version put on its one-time deliveries. |
| 35 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:239` | `CONTRAST_BODY` | `4.5` | doc comment — Body text against its background. |
| 36 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:242` | `CONTRAST_LARGE` | `3.0` | doc comment — Large text and UI component boundaries. |
| 37 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:245` | `MAX_FONT_SCALE` | `2.0` | doc comment — Font scale the UI must remain usable at. |
| 38 | `core/designsystem/src/main/java/com/cyclecare/core/designsystem/token/CycleCareTokens.kt:248` | `PREVIEW_FONT_SCALE` | `1.3` | doc comment — Previews must be produced at this scale as well as 1.0. |
| 39 | `core/domain/src/main/java/com/cyclecare/core/domain/ads/InterstitialGate.kt:40` | `MAX_INTERSTITIALS_PER_DAY` | `3` | doc comment — Maximum interstitials shown in one calendar day. |
| 40 | `core/domain/src/main/java/com/cyclecare/core/domain/ads/InterstitialGate.kt:55` | `MIN_MINUTES_AFTER_REMINDER` | `30` | doc comment — Minimum minutes after a reminder was delivered before an interstitial may |
| 41 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:27` | `SEND_THRESHOLD` | `40` | doc comment — Minimum score to deliver at all. |
| 42 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:30` | `SEND_THRESHOLD_OVER_BUDGET` | `75` | doc comment — Applied once the daily budget is spent; only the strongest survive. |
| 43 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:33` | `SEND_THRESHOLD_PINNED_TIME` | `25` | doc comment — A user who pinned a time has expressed intent; honour it more readily. |
| 44 | `core/domain/src/main/java/com/cyclecare/core/domain/notification/NotificationIntelligence.kt:50` | `DEFAULT_QUIET_HOURS_START_HOUR` | `22` | doc comment — Default quiet hours, applied until the user sets their own. |
| 45 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/CyclePredictionEngineV2.kt:256` | `DEFAULT_ASSUMED_CYCLE_LENGTH` | `28` | doc comment — Used only when the user has supplied no assumption and no history exists. |
| 46 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/CyclePredictionEngineV2.kt:259` | `MIN_EFFECTIVE_CYCLE_LENGTH` | `14` | doc comment — Guards against a nonsensical effective length from a collapsed history. |
| 47 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:15` | `PREDICTION_ALGORITHM_VERSION` | `"2.0.0"` | doc comment — Algorithm version, independent of the app version. |
| 48 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:24` | `MAX_INTERVALS` | `12` | doc comment — Maximum intervals considered, most recent first. |
| 49 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:27` | `RECENCY_DECAY` | `0.85` | doc comment — Per-interval recency decay. Most recent weighs 1.0, each prior x0.85. |
| 50 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:30` | `TRIM_PER_TAIL_MIN_INTERVALS` | `5` | doc comment — Below this interval count, trimming discards too much signal. |
| 51 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:33` | `MIN_PLAUSIBLE_INTERVAL_DAYS` | `15` | doc comment — Below this many days, an interval is a data-entry error, not a cycle. |
| 52 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:36` | `MAX_PLAUSIBLE_INTERVAL_DAYS` | `60` | doc comment — Above this, the history is not understood and an estimate is suppressed. |
| 53 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:39` | `TREND_ACTIVATION_DAYS` | `1.5` | doc comment — Drift below this many days is noise; the estimate is left alone. |
| 54 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:42` | `TREND_HORIZON_CYCLES` | `1.0` | doc comment — Correct for the next cycle only; never extrapolate further. |
| 55 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:45` | `PROJECTED_DRIFT_CAP` | `3.0` | doc comment — Maximum drift correction, applied through tanh so it saturates smoothly. |
| 56 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:48` | `LUTEAL_PHASE_DEFAULT_DAYS` | `14` | doc comment — Population prior. Luteal length is NOT observable from period starts. |
| 57 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:58` | `LUTEAL_PHASE_SIGMA_DAYS` | `2.0` | doc comment — Irreducible uncertainty in the luteal prior. |
| 58 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:61` | `MAD_TO_SIGMA_FACTOR` | `1.4826` | doc comment — Scales the weighted MAD to estimate sigma for a normal distribution. |
| 59 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:64` | `SMALL_SAMPLE_INFLATION` | `1.5` | doc comment — Counteracts the downward bias of a dispersion estimate from few points. |
| 60 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:67` | `IRREGULAR_SIGMA_MULTIPLIER` | `2.0` | doc comment — Extra dispersion applied when the history is classified as irregular. |
| 61 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:70` | `Z_80_TWO_SIDED` | `1.2816` | doc comment — Normal z for a central 80% interval. |
| 62 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:73` | `MIN_HALF_WIDTH_DAYS` | `1` | doc comment — Never promise better than this. |
| 63 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:76` | `MAX_HALF_WIDTH_DAYS` | `7` | doc comment — Beyond a week the "range" is not useful; show variability wording instead. |
| 64 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:79` | `INSUFFICIENT_HISTORY_MIN_INTERVALS` | `3` | doc comment — Below this many intervals, emit no window at all. |
| 65 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:82` | `OUTLIER_MIN_DEVIATION_DAYS` | `7` | doc comment — Below one week, variation is ordinary. |
| 66 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:85` | `OUTLIER_SIGMA_FACTOR` | `3.0` | doc comment — Adapt to the user's own variability. |
| 67 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:88` | `IRREGULAR_MIN_OUTLIERS` | `2` | doc comment — Two outliers required, so one data-entry slip is not mistaken for biology. |
| 68 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:91` | `IRREGULAR_MIN_INTERVALS` | `4` | doc comment — Do not judge regularity on two data points. |
| 69 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:94` | `STALE_AFTER_CYCLE_MULTIPLE` | `1.5` | doc comment — More than a cycle and a half without a logged start means stale history. |
| 70 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:97` | `LATE_PERIOD_GRACE_DAYS` | `3` | doc comment — Days of slack beyond the estimate's own width before calling a period late. |
| 71 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:100` | `QUALITY_LOW_SIGMA` | `4.0` | doc comment — Lower-case quality boundary. |
| 72 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:103` | `QUALITY_HIGH_SIGMA` | `2.0` | doc comment — Upper boundary for the HIGH tier. |
| 73 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:106` | `QUALITY_MODERATE_MIN_INTERVALS` | `5` | doc comment — Interval count needed for MODERATE. |
| 74 | `core/domain/src/main/java/com/cyclecare/core/domain/prediction/PredictionContracts.kt:109` | `QUALITY_HIGH_MIN_INTERVALS` | `8` | doc comment — Interval count needed for HIGH. |
| 75 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:23` | `GCM_NONCE_BYTES` | `12` | doc comment — AES-GCM nonce. Unique per encryption under the same key. |
| 76 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:28` | `KDF_MEMORY_KIB` | `65_536` | doc comment — Argon2id parameters for passphrase-derived wrapping keys. |
| 77 | `core/security/src/main/java/com/cyclecare/core/security/VaultContracts.kt:41` | `MIN_RECOVERY_PASSPHRASE_LENGTH` | `12` | doc comment — Minimum accepted passphrase length. |
| 78 | `app/src/main/java/com/cyclecare/app/Ads.kt:57` | `PROD_BANNER_AD_UNIT_ID` | `"ca-app-pub-2324135355814969/5000575657"` | exempt: ad unit id (external AdMob identifier) |
| 79 | `app/src/main/java/com/cyclecare/app/Ads.kt:58` | `PROD_NATIVE_AD_UNIT_ID` | `"ca-app-pub-2324135355814969/5327878717"` | exempt: ad unit id (external AdMob identifier) |
| 80 | `app/src/main/java/com/cyclecare/app/Ads.kt:59` | `PROD_INTERSTITIAL_AD_UNIT_ID` | `"ca-app-pub-2324135355814969/3684915559"` | exempt: ad unit id (external AdMob identifier) |
| 81 | `app/src/main/java/com/cyclecare/app/Ads.kt:60` | `TEST_BANNER_AD_UNIT_ID` | `"ca-app-pub-3940256099942544/9214589741"` | exempt: ad unit id (external AdMob identifier) |
| 82 | `app/src/main/java/com/cyclecare/app/Ads.kt:61` | `TEST_NATIVE_AD_UNIT_ID` | `"ca-app-pub-3940256099942544/2247696110"` | exempt: ad unit id (external AdMob identifier) |
| 83 | `app/src/main/java/com/cyclecare/app/Ads.kt:62` | `TEST_INTERSTITIAL_AD_UNIT_ID` | `"ca-app-pub-3940256099942544/1033173712"` | exempt: ad unit id (external AdMob identifier) |
| 84 | `app/src/main/java/com/cyclecare/app/AppLockGate.kt:49` | `OVERLAY` | `"app_lock_overlay"` | exempt: compose test tag |
| 85 | `app/src/main/java/com/cyclecare/app/AppLockGate.kt:50` | `BIOMETRIC_UNLOCK` | `"app_lock_biometric_unlock"` | exempt: compose test tag |
| 86 | `app/src/main/java/com/cyclecare/app/AppLockGate.kt:51` | `RESET_DIALOG_CONFIRM` | `"app_lock_reset_confirm"` | exempt: compose test tag |
| 87 | `app/src/main/java/com/cyclecare/app/CycleCareApp.kt:113` | `REQUIRED_GATE` | `"notification_permission_required_gate"` | exempt: compose test tag |
| 88 | `app/src/main/java/com/cyclecare/app/CycleCareApp.kt:114` | `REQUIRED_ACTION` | `"notification_permission_required_action"` | exempt: compose test tag |
| 89 | `app/src/main/java/com/cyclecare/app/CycleCareNavigation.kt:89` | `SETTINGS_ROUTE` | `"settings"` | exempt: storage / resource identifier |
| 90 | `app/src/main/java/com/cyclecare/app/Diagnostics.kt:8` | `breadcrumbKey` | `"breadcrumb"` | exempt: storage / resource identifier |
| 91 | `app/src/main/java/com/cyclecare/app/InterstitialCoordinator.kt:160` | `MILLIS_PER_MINUTE` | `60_000L` | exempt: unit conversion |
| 92 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:187` | `keyNotificationId` | `"reminder_notification_id"` | exempt: intent/data extra key |
| 93 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:188` | `keyTitle` | `"reminder_title"` | exempt: intent/data extra key |
| 94 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:189` | `keyBody` | `"reminder_body"` | exempt: intent/data extra key |
| 95 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:190` | `keyPrivateBody` | `"reminder_private_body"` | exempt: intent/data extra key |
| 96 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:191` | `keyRuleId` | `"reminder_rule_id"` | exempt: intent/data extra key |
| 97 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:192` | `keyReminderType` | `"reminder_type"` | exempt: intent/data extra key |
| 98 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:193` | `keyChannelKey` | `"reminder_channel_key"` | exempt: intent/data extra key |
| 99 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:194` | `keyDeepLinkTarget` | `"reminder_deep_link_target"` | exempt: intent/data extra key |
| 100 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:195` | `keyTargetEpochDay` | `"reminder_target_epoch_day"` | exempt: intent/data extra key |
| 101 | `app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt:196` | `keyScheduledAtEpochMillis` | `"reminder_scheduled_at_epoch_millis"` | exempt: intent/data extra key |
| 102 | `app/src/main/java/com/cyclecare/app/ReminderAlarmScheduler.kt:210` | `SCHEDULER_TAG` | `"ReminderScheduler"` | exempt: work/channel identifier |
| 103 | `app/src/main/java/com/cyclecare/app/ReminderChannels.kt:18` | `remindersGroupKey` | `"cyclecare-reminders"` | exempt: work/channel identifier |
| 104 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:35` | `keyNotificationId` | `"notification_id"` | exempt: intent/data extra key |
| 105 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:36` | `keyTitle` | `"title"` | exempt: intent/data extra key |
| 106 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:37` | `keyBody` | `"body"` | exempt: intent/data extra key |
| 107 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:38` | `keyPrivateBody` | `"private_body"` | exempt: intent/data extra key |
| 108 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:39` | `keyRuleId` | `"rule_id"` | exempt: intent/data extra key |
| 109 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:40` | `keyReminderType` | `"reminder_type"` | exempt: intent/data extra key |
| 110 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:41` | `keyChannelKey` | `"channel_key"` | exempt: intent/data extra key |
| 111 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:42` | `keyDeepLinkTarget` | `"deep_link_target"` | exempt: intent/data extra key |
| 112 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:43` | `keyTargetEpochDay` | `"target_epoch_day"` | exempt: intent/data extra key |
| 113 | `app/src/main/java/com/cyclecare/app/ReminderDeliveryWorker.kt:44` | `keyScheduledAtEpochMillis` | `"scheduled_at_epoch_millis"` | exempt: intent/data extra key |
| 114 | `app/src/main/java/com/cyclecare/app/ReminderNavigation.kt:36` | `Target` | `"reminder_target"` | exempt: intent extra key |
| 115 | `app/src/main/java/com/cyclecare/app/ReminderNavigation.kt:37` | `EventId` | `"reminder_event_id"` | exempt: intent extra key |
| 116 | `app/src/main/java/com/cyclecare/app/ReminderNavigation.kt:38` | `TargetEpochDay` | `"reminder_target_epoch_day"` | exempt: intent extra key |
| 117 | `app/src/main/java/com/cyclecare/app/ReminderRefreshWorker.kt:24` | `periodicWorkName` | `"cyclecare-reminder-refresh-periodic"` | exempt: work/channel identifier |
| 118 | `app/src/main/java/com/cyclecare/app/ReminderRefreshWorker.kt:25` | `immediateWorkName` | `"cyclecare-reminder-refresh-immediate"` | exempt: work/channel identifier |
| 119 | `core/data/src/main/java/com/cyclecare/core/data/ExportRepository.kt:61` | `BACKUP_RECORD_ID` | `"cyclecare_local_backup"` | exempt: storage / resource identifier |
| 120 | `core/data/src/main/java/com/cyclecare/core/data/NotificationPermissionStorage.kt:3` | `notificationPermissionPrefsName` | `"cyclecare_notification_permissions"` | exempt: storage / resource identifier |
| 121 | `core/data/src/main/java/com/cyclecare/core/data/NotificationPermissionStorage.kt:4` | `notificationPermissionRequestedKey` | `"notification_permission_requested"` | exempt: storage / resource identifier |
| 122 | `core/data/src/main/java/com/cyclecare/core/data/RoomFirstRunNudgeRepository.kt:29` | `FIRST_RUN_NUDGE_SHOWN_KEY` | `"first_run_nudge_shown"` | exempt: storage / resource identifier |
| 123 | `core/domain/src/main/java/com/cyclecare/core/domain/ads/InterstitialGate.kt:206` | `MILLIS_PER_MINUTE` | `60_000L` | exempt: unit conversion |
| 124 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:55` | `Month` | `"month"` | exempt: saved-state / navigation key |
| 125 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:56` | `SelectedDate` | `"selected_date"` | exempt: saved-state / navigation key |
| 126 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:57` | `AnchorDayOfMonth` | `"anchor_day_of_month"` | exempt: saved-state / navigation key |
| 127 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:58` | `EditorVisible` | `"editor_visible"` | exempt: saved-state / navigation key |
| 128 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:59` | `EditorEditingId` | `"editor_editing_id"` | exempt: saved-state / navigation key |
| 129 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:60` | `EditorStartDateText` | `"editor_start_date_text"` | exempt: saved-state / navigation key |
| 130 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:61` | `EditorEndDateText` | `"editor_end_date_text"` | exempt: saved-state / navigation key |
| 131 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:62` | `EditorCycleLengthText` | `"editor_cycle_length_text"` | exempt: saved-state / navigation key |
| 132 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarRoute.kt:63` | `EditorNotes` | `"editor_notes"` | exempt: saved-state / navigation key |
| 133 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarScreen.kt:106` | `IsoDateFormatHint` | `"YYYY-MM-DD"` | exempt: storage / resource identifier |
| 134 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:107` | `MONTH_LABEL` | `"calendar_month_label"` | exempt: compose test tag |
| 135 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:108` | `PREVIOUS_MONTH` | `"calendar_previous_month"` | exempt: compose test tag |
| 136 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:109` | `NEXT_MONTH` | `"calendar_next_month"` | exempt: compose test tag |
| 137 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:110` | `SELECTED_DAY_PANEL` | `"calendar_selected_day_panel"` | exempt: compose test tag |
| 138 | `feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarUiState.kt:111` | `SCROLL` | `"calendar_scroll"` | exempt: compose test tag |
| 139 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:60` | `EditorVisible` | `"editor_visible"` | exempt: saved-state / navigation key |
| 140 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:61` | `EditorExpanded` | `"editor_expanded"` | exempt: saved-state / navigation key |
| 141 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:62` | `EditorEditingId` | `"editor_editing_id"` | exempt: saved-state / navigation key |
| 142 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:63` | `EditorStartDateText` | `"editor_start_date_text"` | exempt: saved-state / navigation key |
| 143 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:64` | `EditorEndDateText` | `"editor_end_date_text"` | exempt: saved-state / navigation key |
| 144 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:65` | `EditorCycleLengthText` | `"editor_cycle_length_text"` | exempt: saved-state / navigation key |
| 145 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeRoute.kt:66` | `EditorNotes` | `"editor_notes"` | exempt: saved-state / navigation key |
| 146 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:118` | `DASHBOARD` | `"home_dashboard"` | exempt: compose test tag |
| 147 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:119` | `HERO_CARD` | `"home_hero_card"` | exempt: compose test tag |
| 148 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:120` | `NEXT_PERIOD_RANGE` | `"home_next_period_range"` | exempt: compose test tag |
| 149 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:121` | `EDIT_CYCLE_BUTTON` | `"home_edit_cycle"` | exempt: compose test tag |
| 150 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:122` | `PROGRESS_BAR` | `"home_progress_bar"` | exempt: compose test tag |
| 151 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:123` | `FERTILE_CARD` | `"home_fertile_card"` | exempt: compose test tag |
| 152 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:124` | `REMINDER_CARD` | `"home_reminder_card"` | exempt: compose test tag |
| 153 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:125` | `INSIGHTS_CARD` | `"home_insights_card"` | exempt: compose test tag |
| 154 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:126` | `TODAY_ACTIONS` | `"home_today_actions"` | exempt: compose test tag |
| 155 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:127` | `REMINDER_CENTER_BUTTON` | `"home_reminder_center"` | exempt: compose test tag |
| 156 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:128` | `CYCLE_STATUS_CTA` | `"home_cycle_status_cta"` | exempt: compose test tag |
| 157 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:129` | `FERTILITY_DETAIL_CTA` | `"home_fertility_detail_cta"` | exempt: compose test tag |
| 158 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:130` | `REMINDER_DETAIL_CTA` | `"home_reminder_detail_cta"` | exempt: compose test tag |
| 159 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:131` | `INSIGHTS_DETAIL_CTA` | `"home_insights_detail_cta"` | exempt: compose test tag |
| 160 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:132` | `CYCLE_STATUS_SCREEN` | `"home_cycle_status_screen"` | exempt: compose test tag |
| 161 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:133` | `CYCLE_STATUS_HERO` | `"home_cycle_status_hero"` | exempt: compose test tag |
| 162 | `feature/home/src/main/java/com/cyclecare/feature/home/HomeUiState.kt:134` | `FERTILITY_DETAIL_SCREEN` | `"home_fertility_detail_screen"` | exempt: compose test tag |
| 163 | `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:14` | `OverviewScreen` | `"insights_overview"` | exempt: compose test tag |
| 164 | `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:15` | `CycleTrendsScreen` | `"insights_cycle_trends"` | exempt: compose test tag |
| 165 | `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:16` | `SymptomPatternsScreen` | `"insights_symptom_patterns"` | exempt: compose test tag |
| 166 | `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:17` | `CycleTrendsCta` | `"insights_cycle_trends_cta"` | exempt: compose test tag |
| 167 | `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:18` | `SymptomPatternsCta` | `"insights_symptom_patterns_cta"` | exempt: compose test tag |
| 168 | `feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsUiState.kt:19` | `BackToOverview` | `"insights_back_to_overview"` | exempt: compose test tag |
| 169 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:49` | `HydrationKey` | `"hydration_key"` | exempt: saved-state / navigation key |
| 170 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:50` | `ExistingEntryId` | `"existing_entry_id"` | exempt: saved-state / navigation key |
| 171 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:51` | `Mood` | `"mood"` | exempt: saved-state / navigation key |
| 172 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:52` | `PainLevel` | `"pain_level"` | exempt: saved-state / navigation key |
| 173 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:53` | `EnergyLevel` | `"energy_level"` | exempt: saved-state / navigation key |
| 174 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:54` | `DischargeType` | `"discharge_type"` | exempt: saved-state / navigation key |
| 175 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:55` | `Medication` | `"medication"` | exempt: saved-state / navigation key |
| 176 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:56` | `Notes` | `"notes"` | exempt: saved-state / navigation key |
| 177 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt:57` | `DetailsExpanded` | `"details_expanded"` | exempt: saved-state / navigation key |
| 178 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingUiState.kt:95` | `LIST` | `"logging_list"` | exempt: compose test tag |
| 179 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingUiState.kt:96` | `HeroCard` | `"logging_hero_card"` | exempt: compose test tag |
| 180 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingUiState.kt:97` | `SaveBar` | `"logging_save_bar"` | exempt: compose test tag |
| 181 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingUiState.kt:100` | `NotesField` | `"logging_notes_field"` | exempt: compose test tag |
| 182 | `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingUiState.kt:101` | `NotesError` | `"logging_notes_error"` | exempt: compose test tag |
| 183 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:44` | `AgeBand` | `"age_band"` | exempt: saved-state / navigation key |
| 184 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:45` | `CycleLength` | `"cycle_length"` | exempt: saved-state / navigation key |
| 185 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:46` | `PeriodLength` | `"period_length"` | exempt: saved-state / navigation key |
| 186 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:47` | `LastPeriodStartEpochDay` | `"last_period_start_epoch_day"` | exempt: saved-state / navigation key |
| 187 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:48` | `Goal` | `"goal"` | exempt: saved-state / navigation key |
| 188 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:49` | `RemindersEnabled` | `"reminders_enabled"` | exempt: saved-state / navigation key |
| 189 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingRoute.kt:50` | `PrivacyModeEnabled` | `"privacy_mode_enabled"` | exempt: saved-state / navigation key |
| 190 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:45` | `CYCLE_LENGTH_VALUE` | `"onboarding_cycle_length_value"` | exempt: compose test tag |
| 191 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:46` | `CYCLE_LENGTH_SLIDER` | `"onboarding_cycle_length_slider"` | exempt: compose test tag |
| 192 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:47` | `LAST_PERIOD_ROW` | `"onboarding_last_period_row"` | exempt: compose test tag |
| 193 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:48` | `DATE_PICKER` | `"onboarding_date_picker"` | exempt: compose test tag |
| 194 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:49` | `DATE_PICKER_CONFIRM` | `"onboarding_date_picker_confirm"` | exempt: compose test tag |
| 195 | `feature/onboarding/src/main/java/com/cyclecare/feature/onboarding/OnboardingUiState.kt:50` | `START_TRACKING_BUTTON` | `"onboarding_start_tracking"` | exempt: compose test tag |
| 196 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:45` | `SCREEN` | `"privacy_screen"` | exempt: compose test tag |
| 197 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:46` | `PIN_CARD` | `"privacy_pin_card"` | exempt: compose test tag |
| 198 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:47` | `PIN_MESSAGE` | `"privacy_pin_message"` | exempt: compose test tag |
| 199 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:48` | `PRIMARY_ACTION` | `"privacy_primary_action"` | exempt: compose test tag |
| 200 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:49` | `SECONDARY_ACTION` | `"privacy_secondary_action"` | exempt: compose test tag |
| 201 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:50` | `TERTIARY_ACTION` | `"privacy_tertiary_action"` | exempt: compose test tag |
| 202 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:51` | `APP_LOCK_SWITCH` | `"privacy_app_lock_switch"` | exempt: compose test tag |
| 203 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:52` | `BIOMETRIC_SWITCH` | `"privacy_biometric_switch"` | exempt: compose test tag |
| 204 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:53` | `ENABLE_BIOMETRIC_BUTTON` | `"privacy_enable_biometric"` | exempt: compose test tag |
| 205 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:54` | `UPDATE_PIN_BUTTON` | `"privacy_update_pin"` | exempt: compose test tag |
| 206 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:55` | `RECOVER_BUTTON` | `"privacy_recover_access"` | exempt: compose test tag |
| 207 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:56` | `DELETE_KEY` | `"privacy_key_delete"` | exempt: compose test tag |
| 208 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:57` | `CLEAR_KEY` | `"privacy_key_clear"` | exempt: compose test tag |
| 209 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:58` | `RESET_LOCAL_DATA` | `"privacy_reset_local_data"` | exempt: compose test tag |
| 210 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:59` | `PIN_DOT_PREFIX` | `"privacy_pin_dot_"` | exempt: compose test tag |
| 211 | `feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyComponents.kt:60` | `DIGIT_KEY_PREFIX` | `"privacy_key_"` | exempt: compose test tag |
| 212 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:48` | `LIST` | `"settings_list"` | exempt: compose test tag |
| 213 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:49` | `OPEN_RESET` | `"settings_open_reset"` | exempt: compose test tag |
| 214 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:50` | `CONFIRM_RESET` | `"settings_confirm_reset"` | exempt: compose test tag |
| 215 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:51` | `REMINDERS_ENABLED` | `"settings_reminders_enabled"` | exempt: compose test tag |
| 216 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:52` | `NOTIFICATION_RECOVERY` | `"settings_notification_recovery"` | exempt: compose test tag |
| 217 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:53` | `OPEN_REMINDER_CENTER` | `"settings_open_reminder_center"` | exempt: compose test tag |
| 218 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:54` | `OPEN_AD_PRIVACY` | `"settings_open_ad_privacy"` | exempt: compose test tag |
| 219 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:55` | `OPEN_EXACT_ALARMS` | `"settings_open_exact_alarms"` | exempt: compose test tag |
| 220 | `feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsUiState.kt:56` | `OPEN_PRIVACY` | `"settings_open_privacy"` | exempt: compose test tag |
