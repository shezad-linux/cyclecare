# 00 — Current-State Review (Repository Audited)

**Status:** The repository, build, Gradle graph, Room schema, domain logic, notification path and design system have now been **inspected directly**. This document replaces the earlier design review, which was based on a described architecture rather than on code.
**Audit date:** 17 October 2026
**Platform in scope:** Android, Kotlin, Jetpack Compose.

Every finding below is verified by reading source, inspecting the exported Room schema, or by running Gradle. Claims that could not be executed are labelled as such.

## 1. Verified build state

| Item | Value |
|---|---|
| Gradle | 9.3.1 |
| Android Gradle Plugin | 9.1.0 |
| Kotlin | 2.2.10 |
| KSP | 2.3.2 (Room only, in `:core:database`) |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| JVM target | 17 (daemon 21) |
| Modules | 16 (`:app`, `:benchmark`, 6 × `:core`, 8 × `:feature`) |
| versionCode / versionName | 7 / "1.0" |
| **Compiles** | **Yes** — `:app:compileDebugKotlin` BUILD SUCCESSFUL in 2m21s, 2 benign warnings |
| **Unit tests** | **95 passing**, 0 failures (`testDebugUnitTest`, ~16s) |
| **Instrumented tests** | 71 tests across 13 files; all compile |
| DI | Hand-rolled `AppContainer` constructed in `CycleCareApplication.onCreate()` — no Hilt/Dagger |
| Network layer | **None.** No Retrofit, OkHttp, serialization, or Supabase dependency anywhere |
| Analytics | None beyond Crashlytics |
| Localization | **None.** `strings.xml` contains only `app_name`; all user-facing copy is hardcoded English in Compose |
| CI | **None.** No `.github/`, no lint/detekt/ktlint, no coverage tooling |

The app is a working, tested, local-only Android application. The problems below are quality and correctness problems, not a broken build.

## 2. Decisions taken during this redesign

| # | Decision | Consequence |
|---|---|---|
| 1 | **Advertising is retained** | Docs 04 §8 and 10 §1 previously instructed removing AdMob/UMP. Superseded by `16-monetization-and-ads.md`. |
| 2 | **Cloud sync is in scope** | `17-sync-and-key-management.md` defines it. `NoOpSyncAdapter` is replaced by a real implementation. |
| 3 | **Whole-app rewrite is permitted** | Schema, module boundaries and screen composition may all change. Data migration must still be strictly lossless — there is a live installed user base. |
| 4 | **Prediction: robust classical estimation with empirical intervals** | `11-prediction-engine.md`, validated per `12`. |
| 5 | **Notifications: hybrid timing with relevance scoring** | `SCHEDULE_EXACT_ALARM` for two types only, WorkManager degradation as the default path, per `13`. |
| 6 | **Four bottom destinations** (Today, Calendar, Log, Insights); Settings moves to the app bar | `15-screen-specifications.md` §1. |

## 3. P0 — must fix before or during release

### 3.1 Privacy claims contradict the shipped SDKs

**Confirmed.** AdMob is deeply integrated: adaptive banner (`Ads.kt:61`), native advanced (`:136`), interstitial with a 60-second cooldown (`:387`), and Google UMP consent (`GoogleMobileAdsConsentManager.kt`). Production ad unit IDs are hardcoded in Kotlin source (`:47-52`).

The merged release manifest contributes `AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION` and `ACCESS_ADSERVICES_TOPICS` — permissions the app never declares itself.

Mitigating and verified: all three request paths use a bare `AdRequest.Builder().build()` with **no targeting parameters**, so no health value is ever passed to Google (`Ads.kt:104, 163, 174, 400`).

The published privacy policy nonetheless asserts "No analytics or tracking SDKs" (`docs/privacy-policy.html:279`). That statement is false as written. Resolution: `16-monetization-and-ads.md`.

### 3.2 App PIN is a single-round SHA-256 verifier

**Confirmed** — `Repositories.kt:684-688`. No PBKDF2, bcrypt or Argon2. A four-digit PIN has 10,000 possibilities; a single SHA-256 round is trivially brute-forced if the verifier is extracted.

Required: biometric/device-credential as the primary path; if a PIN is retained, a vetted slow KDF with escalating throttling. The PIN must never derive the vault key. See `17` §6.2.

### 3.3 Database is not encrypted at rest

**Confirmed** — no SQLCipher or equivalent in any build file. Cycle dates, symptoms, medication and free-text notes sit in `cyclecare.db` unencrypted, protected only by app-sandbox isolation. The published policy's Security section implies protection the app does not have.

### 3.4 The prediction algorithm is unsound for real histories

**Confirmed** — `CyclePredictionEngine.kt` is 70 lines: `anchor + round(mean of last 6 start-to-start intervals)`, fixed `-14` day ovulation, fertile window `[ovulation−4, ovulation+1]`, and MAD mapped to one of four hardcoded strings.

Specific defects:
- No interval is ever validated. A 300-day start gap is averaged in and pushes the next prediction ~10 months out. `CycleRecordValidator.kt:43` range-checks the typed `cycleLength` field but **never compares it to the real start gap**.
- Outliers are retained deliberately (`docs/prediction-logic.md:13`).
- No prediction range is produced, yet `docs/system-design.md` and UI copy imply dated ranges.
- `confidenceLabel` is a raw `String` (`CycleModels.kt:129`).
- `daysUntilNextPeriod` drifts negative indefinitely with no interpretation.
- `today` defaults to `LocalDate.now()` and the production call site omits it (`Repositories.kt:142`), so production is non-deterministic and edge cases are untestable.

Resolution: `11-prediction-engine.md`.

### 3.5 Two engines can disagree about the same user's data

**Confirmed** — `CyclePredictionEngine.kt:45` derives intervals from start dates; `InsightEngine.kt:26` reads the stored `cycleLength`. Nothing reconciles them, and `cycleLength` is never cross-validated on write. For a live user base, production data may already contain this divergence.

Consequences: Insights and Today can show different numbers, and dropping `cycleLength` in a migration would silently rewrite history. Resolution: `11` §7 and `02` §9.4.

### 3.6 No prediction metadata; predictions are not persisted or versioned

**Confirmed** — no prediction cache table exists in v2; `PredictionSnapshot` is recomputed on every Flow collection (`Repositories.kt:135-144`). `today` rolls over at midnight and cycle-day, days-remaining and reminder trigger dates change silently, with no version and no invalidation story.

Resolution: `11` §6.

### 3.7 Schema migration is untested and one migration test is broken

**Confirmed.** `migration1To2` (`CycleCareDatabase.kt:221-291`) has **zero** test coverage despite `exportSchema = true` and committed `1.json`/`2.json`.

`CycleCareDatabaseRoomTest.exportedSchema_canOpenCurrentVersionDatabase` (`:104-114`) creates a v1 database and opens it against a v2 database with **no migration registered** and no `fallbackToDestructiveMigration`. It will throw `IllegalStateException: A migration from 1 to 2 was required but not found`.

### 3.8 The published privacy policy overstates device data handling

**Confirmed.** `docs/privacy-policy.html:293` claims "Uninstalling the app permanently deletes all stored data." There is no `dataExtractionRules.xml` and `allowBackup="false"` alone does not reliably prevent Android 12+ device-to-device transfer. The claim is unsafe as written. Same class of problem: §5 lists 2 permissions where 14 reach the merged manifest, and §2 never discloses that age band, medication and notes are collected.

## 4. P1 — quality, reliability and correctness

### 4.1 Notification path

All confirmed in `13` §1. Summary:

- **WorkManager only.** No `AlarmManager` usage exists anywhere. A 09:00 reminder can arrive hours late under Doze.
- `setGroup()` is set (`ReminderDeliveryWorker.kt:177`) with **no group summary** — an orphan group.
- `recordEvent` (`:56`) performs no existence check, so a `Result.retry()` re-posts the same notification.
- `setPublicVersion()` is never called, so `VISIBILITY_PRIVATE` alone may still leak on some lock screens.
- One hardcoded redaction string for all five types (`ReminderSchedulePlanner.kt:66, 89, 113, 133, 163`).
- No quiet hours, no DND awareness, no frequency cap — up to 5 notifications/day.
- No snooze, no notification actions.
- **`ReminderType.Hydration` and `Pill` are dead stubs** (`ReminderSchedulePlanner.kt:35-37`): enum values, two settings strings each, no planner branch, no seed rule, no test.
- `LatePeriod` is a fixed `+3` offset from a prediction, not a lateness detector.
- Fertility rules are seeded once and never re-evaluated when `trackingGoal` changes.

### 4.2 Reminder settings are not editable

**Confirmed** — `SettingsScreen.kt:115-133` renders `daysBefore`/`hourOfDay`/`minute` as read-only labels. The only mutation is `toggleReminderRule`. Users cannot change lead time or send time.

### 4.3 A timezone change is not handled in the production path

**Confirmed** — `ReminderSchedulePlanner` captures `ZoneId.systemDefault()` at construction (`:18`), and `WorkManagerReminderScheduler` constructs it once in `Application.onCreate` (`:78`). The TZ-change test constructs a planner with an explicit `ZoneId`, so the production wiring path is uncovered.

### 4.4 State mutation from composable scope

**Confirmed.** `AppLockGate.kt` receives `privacyRepository` and `appResetRepository` as Composable parameters and performs PIN validation and a **destructive `resetAllLocalData()`** during composition. `CycleCareApp.kt:147-160` and `:383-384, 427, 482, 494` read repositories and call mutations directly in composables.

Feature modules are clean — all 10 screens correctly hoist state through ViewModels. The violations are confined to the app shell.

### 4.5 Design system incompleteness

**Confirmed.**
- 4 of 15 typography roles defined; **106 of ~160 `Text` calls** use unstyled Material defaults. `displayMedium` is defined and used **zero** times.
- **No `Shapes()` anywhere.**
- `CycleCareSpacing` is declared and has **zero consumers**; screens use raw `16.dp`/`24.dp` literals.
- Colours hardcoded outside the theme at `CycleCareTheme.kt:153-176`.
- **`isSystemInDarkTheme()` is called inside token functions** (`:154, 172, 176`) while `CycleCareTheme` accepts an explicit `darkTheme` parameter (`:139`). Forcing `darkTheme = false` on a dark-mode device returns dark values. Real bug.

### 4.6 Accessibility defects

**Confirmed.**
- Bottom-navigation icons use `contentDescription = null` (`CycleCareApp.kt:532`) — all five icon nodes are unlabelled for TalkBack.
- `BackHandler(enabled = true) {}` at `:317` traps back and switch-access users in the notification gate with no exit.
- `contentDescription = null` appears in 11 places across `CycleCareApp`, `OnboardingScreen`, `HomeScreen` and `CalendarScreen`.
- No `stateDescription` anywhere.
- No large-font UI test, no RTL test despite `supportsRtl="true"`.
- Compose `AccessibilityChecks` are not enabled in any test.

### 4.7 Test coverage gaps

Zero tests: `:core:model`, `:core:designsystem`, `:core:testing`, `:feature:privacy` (1102 lines including the PIN keypad, `SecureAuthController` and biometric logic), `:feature:export` (writes files to disk). No UI tests for settings, privacy, export or the reminder centre.

`feature/privacy` being untested is the most serious of these — it holds security-critical code.

### 4.8 Build hygiene

- `:benchmark`'s test file is in `src/androidTest` of a `com.android.test` module with all non-`benchmark` variants disabled (`benchmark/build.gradle.kts:35-39`). Verified: `compileBenchmarkKotlin` → `NO-SOURCE`; no `compileBenchmarkAndroidTestKotlin` task exists; `assembleAndroidTest` → `SKIPPED`. **The macrobenchmark never compiles.**
- No `proguard-rules.pro` exists anywhere, yet a release build type is configured.
- `material3Adaptive` is declared in `libs.versions.toml:14` and referenced by zero modules. `androidx-compose-runtime` likewise.
- Seven deprecated AGP backport flags in `gradle.properties:5-14`, all of which will break on a future AGP.
- `keystore.properties` contains **plaintext production keystore passwords** on disk (gitignored, but present).
- Stale IDE output in `core/*/bin/` duplicates 15 source files and will confuse search tools.

## 5. P2 — hygiene

- `core/testing` is declared in `settings.gradle.kts` and depended on by nothing.
- `feature/billing` is not in `settings.gradle.kts` and has zero source files, but stale `build/` artefacts remain on disk.
- `ReminderBell` (`CycleCareApp.kt:732-747`) is dead code — defined, never called.
- No `network_security_config.xml`; cleartext relies on the `targetSdk ≥ 28` default.
- Route/label mismatch: destination label "Track" maps to route `log`.
- `SyncAdapter` is a `NoOpSyncAdapter` returning the literal string `"Sync is not enabled in v1."`.

## 6. What was reported before and is now resolved

| Earlier finding | Outcome |
|---|---|
| "Supabase may be added without a data-flow model" | Resolved — no Supabase exists; `17` defines the full model |
| "A future Supabase database conflicts with the zero-cloud premise" | Resolved — local-first plus opt-in encrypted sync (`17` §2) |
| "A four-digit PIN hashed with salted SHA-256 is vulnerable" | **Confirmed**, and the hash has no KDF at all |
| "Prediction math carries confidence labels implying too much certainty" | **Confirmed**, and there is no range at all |
| "Prediction snapshots may be persisted as observed records" | **Not a live defect** — `CycleSource.Predicted` is filtered everywhere but is never actually written |
| "WorkManager may be treated as exact-time delivery" | **Confirmed** — no AlarmManager exists |
| "`allowBackup=false` is described as an absolute block on extraction" | **Confirmed** — no `dataExtractionRules.xml` exists |
| "Numerical quality scores are unsupported" | Confirmed and removed from the redesign set |

## 7. Must still not be claimed

- "Zero data leaves the device" — false with ads (`16` §4), and with sync enabled.
- "No tracking" — false; AdMob tracks users even while receiving no health data.
- "Fully end-to-end encrypted" — metadata remains visible (`17` §9).
- "Secure against rooted devices or forensic access" — out of scope (`04` §3).
- "Exact notifications" — only two types may use exact alarms, and only with a user-granted permission (`13` §11).
- "Clinically accurate predictions" — synthetic tests are not clinical validation (`12` §1).
- "100% private", "completely private" — not provable while ad SDKs run.
- Any numerical security or reliability rating.

Use narrower statements that match inspected code and test results.