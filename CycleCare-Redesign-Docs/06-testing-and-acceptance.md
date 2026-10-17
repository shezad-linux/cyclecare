# 06 — Testing Strategy, Acceptance Criteria, and Definition of Done

## 1. Testing strategy

The app should not be described as production-ready because a design document lists tests or because a static architecture score is high. Production readiness is a set of repeatable results: functional correctness, migration safety, privacy verification, security isolation, accessibility, performance, and release-policy checks.

### Test layers

| Layer | Examples | Required approach |
|---|---|---|
| Domain unit tests | Cycle validation, estimates, insight thresholds, reminder target dates | Pure deterministic tests with a fixed clock and synthetic records |
| Room DAO tests | Duplicate prevention, date queries, delete behavior, transaction atomicity | In-memory/temporary DB tests plus migration tests |
| Migration tests | Supported version upgrades, enum changes, date preservation | Upgrade representative pre-version databases and compare records |
| Repository tests | Local save while offline, outbox atomicity, mapping | Fake remote transport and deterministic local DB fixtures |
| Encryption tests | Encrypt/decrypt, authentication failure, nonce generation, payload version mismatch | Known-answer tests for vetted libraries plus negative tests; never implement primitives manually |
| Sync integration tests | Retry, revision conflict, tombstones, two devices | Test Supabase project and multiple authenticated identities |
| RLS tests | Cross-account CRUD denial, anonymous access denial | Automated policy tests and direct API tests |
| UI tests | Onboarding, logging, editing, empty/offline states, delete confirmations | Compose semantics and stable test tags where appropriate |
| Accessibility tests | TalkBack roles/order, large font, contrast, target sizes | Manual testing plus automated accessibility checks |
| Performance tests | Cold start, scroll, recomposition, database query timing | Macrobenchmark and profiling on representative devices |
| Privacy tests | No notes/dates in logs, SDK requests, analytics or crash payloads | Synthetic sensitive fixtures plus log/network inspection |
| Release tests | Fresh install, upgrade, permission denial, restore, process death | Device matrix and Play test track |

## 2. P0 end-to-end scenarios

### Core local use

- Fresh install can proceed without account creation.
- A user can add, edit and delete a period record in airplane mode.
- A user can add multiple symptoms to one date and later edit them.
- Today and Calendar render from Room without waiting for a network request.
- Process death does not lose saved records or queued sync operations.
- Date-only values remain stable across timezone changes.

### Cycle and prediction correctness

- Predicted records are never counted as observed period starts.
- Duplicate starts and invalid end dates are rejected or resolved clearly.
- Editing an old record invalidates and recomputes dependent estimates.
- Sparse, contradictory or highly variable history shows uncertainty/no-estimate state.
- Removing all history removes stale derived predictions.

### Reminders

- Disabled notifications do not repeatedly prompt the user.
- Reminder schedules are rebuilt without duplication after boot, app update, timezone change, or rule edit.
- WorkManager timing behavior is tested with its permitted deferral; the app does not promise exact minute delivery.
- Foreground handling does not depend on a live Activity reference.
- Locked-screen text is masked by default and respects user settings.
- Notification channel changes/denial are reflected in the app UI.

### Optional sync

- Sync disabled means no health record is uploaded.
- Local saves succeed when sync fails.
- Pending outbox entries survive process death and restart.
- Repeating an upload cannot create duplicate records.
- Two-device same-record edits show the specified conflict behavior.
- A stale client cannot resurrect a deleted record.
- A new device can decrypt synced records only through the designed recovery flow.
- A wrong/missing key never silently creates an apparently empty or corrupted dataset.
- User A cannot read, change or delete User B's remote rows.
- Remote ciphertext cannot be mistaken for plaintext or accepted when authentication/decryption fails.

### Privacy and deletion

- Synthetic period dates, symptoms, notes and medication values do not appear in production logs, analytics, crash payloads or request URLs.
- Turning sync off follows the documented remote-data policy.
- Local deletion clears app-owned local records, caches and pending uploads and cancels scheduled reminders where possible.
- Cloud deletion handles remote rows and identity; partial failure remains visible and retryable.
- Export contains only the selected data and is never silently transmitted.
- Backup and device-transfer behavior matches product copy across supported Android versions.

## 3. UI acceptance criteria

- Every main screen defines loading, populated, empty, offline, validation error and save result states as applicable.
- All destructive actions show their scope and require clear confirmation.
- The app differentiates a locally saved record from a synchronized record.
- Predictions are visually labeled as estimates and don't rely only on color.
- Primary flows work with large text and TalkBack.
- Compose accessibility semantics identify control role, label, state and actions.
- Screens are usable in light/dark themes and long/localized strings do not clip.
- The same date-entry and save patterns work consistently across screens.

## 4. Performance gates

Set numerical performance budgets after collecting baseline data. Don't choose targets only to make the report look strong. Measure representative low/mid-range Android devices and include:

- Cold-start median and tail behavior.
- Main-screen time to usable local content.
- Calendar scrolling smoothness and dropped frames.
- Large-history query and export time.
- Database migration duration.
- Memory use during repeated navigation and export.
- Battery impact of reminder scheduling and sync retries.

Performance changes require before/after evidence and regression checks.

## 5. CI gate

A release candidate must pass:

- Formatting/lint/static analysis.
- Unit and domain tests, including the `12` corpus gates.
- Room DAO and migration tests, including the `02` §9.8 lossless-migration fixtures.
- UI and accessibility checks where automatable, at font scale 2.0 and in RTL.
- **Ad-targeting API scan — fails the build if targeting appears in the app module** (`16` §7).
- Security-sensitive source scanning and secret scanning, including keystore passwords.
- Supabase migration/RLS policy tests in a non-production project.
- Dependency vulnerability review.
- Build/signing validation against real `proguard-rules.pro`.
- Privacy manifest/store disclosure review.

Never run destructive database tests against a production Supabase project. Keep development, staging and production environments and credentials separate.

## 6. Definition of done for a feature

A feature is done only when:

1. Requirements and edge states are documented.
2. UI and copy match the UX specification.
3. Domain logic is outside composables and covered by deterministic tests.
4. Database writes and migration changes are safe.
5. Offline/process-death behavior is verified.
6. Sensitive data flow is documented and privacy tests pass.
7. Accessibility has been tested.
8. Errors and retry behavior are user-visible and not silently swallowed.
9. Analytics contains no sensitive fields and is on the approved allowlist.
10. Documentation is updated alongside code.

## 7. Production release blockers

Do not release cloud sync or make strong security claims while any of the following remain true:

- Remote schema/RLS is not covered by automated isolation tests.
- Key recovery is undefined or implemented with a raw short PIN.
- Account deletion is only a UI button without remote deletion verification.
- The privacy policy does not match SDK traffic.
- Predictions imply contraception or medical diagnosis.
- Reminder timing is promised more precisely than Android can guarantee.
- Critical migration or deletion paths have no regression tests.

## 8. Redesign-specific gates

This document predates the redesign and remains the general strategy. The redesign adds three specialised suites, each specified in full elsewhere. All are mandatory.

### 8.1 Algorithm gates — `12-algorithm-validation.md`

Thirteen numeric gates (G1–G13) over a synthetic corpus with a rolling-origin hold-out protocol, plus golden fixtures pinned per algorithm version.

The two that matter most: **G1** requires empirical interval coverage ≥ 0.80 at n ≥ 6, and **G5** requires median half-width ≤ 3 days. **G1 without G5 is not a pass** — a window wide enough to always contain the answer would satisfy G1 while being useless.

Gates are read stratified by history length, never as a single aggregate.

### 8.2 Notification gates — `13` §16

Pure stages (candidate generation, relevance scoring, budget, timing resolution, arbitration, batching) are unit-tested in `:core:domain` with an injected clock. Instrumented tests cover real `AlarmManager` registration per tier, permission grant and revocation, boot/timezone rescheduling, and **a device test under Doze demonstrating that Tier 2 delivery is materially better than the current WorkManager behaviour** — that claim justifies the architecture change and must be demonstrated, not assumed.

### 8.3 Migration gates — `02` §9.8

`migration1To2` and `migration2To3` must both have passing tests against fixtures built from a **production-shaped, anonymised snapshot**. Synthetic fixtures alone will not surface the duplicate-date rows and `cycleLength` divergences that actually occur in a live user base.

Migration must be verified lossless: row counts and record IDs identical before and after, `None` never converted to `NULL`, every original `cycleLength` preserved.

### 8.4 Ad-privacy invariant — `16` §7

The claim "no health data reaches the ad SDK" is upheld today only by the absence of targeting code. Make it structural:

1. A single ad-request factory that accepts no user content.
2. A unit test asserting default-only `AdRequest` construction.
3. **A CI check that fails the build if `setCustomTargeting`, `addKeyword`, `setPublisherProvidedId`, `setLocation` or `NativeAdOptions.requestCustomAssets` appears anywhere in the app module.**

Item 3 is what prevents a silent privacy regression by a future contributor.

### 8.5 Known existing test defects to fix

These currently fail or do not exercise what they claim:

| Defect | Location |
|---|---|
| Macrobenchmark test never compiles — source in `src/androidTest` of a `com.android.test` module with non-`benchmark` variants disabled | `benchmark/build.gradle.kts:35-39` |
| Migration test opens a v1 database on a v2 database with no migration registered — will throw | `core/database/src/androidTest/.../CycleCareDatabaseRoomTest.kt:104-114` |
| `feature:privacy` — PIN keypad, `SecureAuthController`, biometric unlock — has **zero tests** | `feature/privacy` |
| `core:designsystem` has zero tests and no `ui-test-junit4` dependency | `core/designsystem` |
| `feature:export` has zero tests despite writing files to disk | `feature/export` |
| Production timezone-change wiring path is uncovered (the test injects an explicit `ZoneId`) | `ReminderSchedulePlannerTest.kt:164` |
| Bottom-nav icons have `contentDescription = null`; no `stateDescription` anywhere; no large-font or RTL UI test | `CycleCareApp.kt:532` |

The 17 existing `CyclePredictionEngineTest` cases assert algorithm v1 semantics and must be **replaced**, not extended — several describe behaviour v2 deliberately changes.
