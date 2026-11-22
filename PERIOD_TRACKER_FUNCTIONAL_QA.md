# CycleCare — Menstrual Cycle & Notification Functional QA Report

**Audit date:** 2026-11-22
**Branch:** `android-17-migration-audit-remediation`

**Method:** full code review of every feature module, domain engine, repository, and data layer; cross-checking behavior against `docs/` specs (S1/02/05/11/12/13/15) and the Room schema; existing unit/instrumented tests cited; automated runtime checks planned/executed in Phase 8 (marked **[Planned]** or **[Done]** with evidence).

---

## 1. Menstrual cycle tracking

### 1.1 Cycle record storage

- **Storage:** Room `cycle_records` table (schema v3), `CycleRecordEntity`: `id` (String), `startEpochDay` (Long, indexed), `endEpochDay` (nullable Long — null while period in progress), `cycleLength` (Int, deprecated for reads), `source` (User/Imported/Predicted), `notes` (nullable), `createdAtEpochMillis`, `updatedAtEpochMillis`, `deletedAtEpochMillis` (soft-delete), `legacyCycleLength` (nullable), `createdAtIsBackfilled` (default false).
- **Write paths:** `RoomCycleRepository.upsertCycle` (upsert), `softDelete` (tombstone), all callers:
  - `HomeViewModel.saveCycle` / `quickStartPeriodToday` (`feature/home/HomeRoute.kt:388,345`) — validated by `CycleRecordValidator`.
  - `CalendarViewModel.saveCycle` (`feature/calendar/CalendarRoute.kt:271`) — validated.
  - `OnboardingRoute.OnboardingViewModel.submit` (`feature/onboarding/OnboardingRoute.kt:100`) — writes initial cycle.
- **Audit timestamps:** `toEntity(System.currentTimeMillis())` at write; migration-backfilled rows labeled.

### 1.2 Validation (`core/domain/CycleRecordValidator.kt`)

| Rule | Implementation | Test |
|---|---|---|
| Start in the future rejected | `candidate.startDate.isAfter(today)` → "Start date cannot be in the future." | `CycleRecordValidatorTest` |
| End before start rejected | `endDate < candidate.startDate` | same |
| Period length 2–10 days enforced (only when endDate set) | `ChronoUnit.DAYS.between(start,end)+1 !in 2..10` | same |
| Overlap with existing entries rejected | any-record overlap incl. in-progress (`existingEnd == null && candidate.startDate >= existing.startDate`) | same |
| `cycleLength` range not range-checked (deprecated for reads) | comment at validator.kt:61–64 | — |

**Verification:** `CycleRecordValidatorTest` (unit), and functional probes via `LoggingScreenUiTest`/`CalendarScreenUiTest` on emulator.

### 1.3 Cycle length calculation — authoritative definition

- Single definition: `extractIntervals(observedStartsOf(cycles))` (`core/domain/prediction/CycleIntervals.kt`).
- Only start-to-start gaps; period length is never substituted (`ChronoUnit.DAYS.between(from.startDate, to.startDate)`).
- Plausibility window [15, 60] days (`MIN/MAX_PLAUSIBLE_INTERVAL_DAYS`); excluded intervals are retained for reporting but not averaged.
- Stored `cycleLength` never read as a length (only `legacyCycleLength` audit on migration; guarded by `StoredCycleLengthDoesNotChangeAnInsightTest` / `StoredCycleLengthGuardTest`).

### 1.4 Period duration calculation

- `CycleRecord.periodLengthDays = ChronoUnit.DAYS.between(startDate, endDate)+1` (model, `core/model/CycleModels.kt:121`).
- Display: `ChronoUnit.DAYS.between(...)+1` in HomeRoute:250, CalendarUiState:343.

### 1.5 Next-period prediction (`CyclePredictionEngineV2`)

- Inputs: observed starts (excluding Predicted), `today` (injected via `SystemTimeProvider`).
- Algorithm: recency-weighted trimmed mean (decay 0.85, trim ≥5 intervals, skip-trim below 5), weighted MAD → sigma (×1.4826), sample-size inflation ×1.5→1.125, irregularity flag, weighted least-squares trend (horizon 1 cycle, activation 1.5 d, cap 3 d, tanh saturation), effective cycle length (min 14), next period = anchor + effective length, day of week? no.
- Output: `nextPeriodDate`, `nextPeriodWindow` (±halfWidth, floor 1, cap 7; only when ≥3 usable intervals — `supportsRange`), `ovulationDate` (= nextPeriodDate − 14), `fertileWindow` ([ovulation−4, ovulation+1] + widen = halfWidth + LUTEAL_PHASE_SIGMA_DAYS=2), `cycleDay`, `daysUntilNextPeriod` (signed), `overdueDays`, `phase`, `quality` (INSUFFICIENT_HISTORY / LOW / MODERATE / HIGH).
- **Irregularity (honest UI):** ≤1 outlier → not irregular; ≥2 outliers among ≥4 intervals OR impossible interval → irregular (with warnings; sigma ×2).
- **Uncertainty representation:** `DateWindow.halfWidthDays` — range shown on Home/Calendar as "between X and Y" (`formatNextPeriodRange`).

**Verification:** `PredictionMathTest`, `PredictionFertileWindowTest`, `PredictionGoldenFixtureTest` + emulator-verified `HomeScreenUiTest`, `CalendarScreenUiTest`.

### 1.6 Calendar display

- `buildCalendarDays` — period days, fertile window, ovulation, today, selected.
- Selected-day summary: `buildSelectedDaySummary` — period/ovulation/fertile/future/neutral statuses with actions (Log period).
- History: `buildHistoryItems` — 6 most recent, derived intervals, "cycle length unknown yet" for the most recent.
- Week header uses locale-aware `WeekFields.firstDayOfWeek` (locale sensitive).

### 1.7 Editing/deleting entries

- Edit via calendar (`openEditSelectedCycle`, `openHistoryEditor`), delete with confirmation (`showDeleteConfirmation`/`deleteSelectedCycle` → `softDelete` + `reminderScheduler.refreshSchedules()`).
- `ReminderCenterScreen` — unread reminders + auto-disabled types with re-enable action.

### 1.8 Timezone & device-time handling

- `SystemTimeProvider.today()` reads system date — device time changes affect new predictions; stored `epochDay`s are absolute so history is stable.
- Alarms are absolute `RTC_WAKEUP`; `TIMEZONE_CHANGED`/`TIME_SET` broadcasts → `ReminderRescheduleReceiver` → `ReminderRefreshWorker` re-schedules via `ReminderAlarmScheduler.refreshSchedules`. **[Planned]** Probe in Phase 8 (set TZ, verify rescheduled alarms via `adb shell dumpsys alarm`).

### 1.9 Prediction updates after history edits

- `predictionRepository` = `LocalPredictionRepository` — computed from current Room rows on each flow emission; not persisted as rows.
- Save paths call `reminderScheduler.refreshSchedules()` (calendar/home/onboarding) → predictions reflected in alarms.

### 1.10 Irregular cycles / leap years / month boundaries

- `LocalDate.plusDays`/`atDay` handle DST-free civil dates; leap days handled by `java.time` (no assumptions in code).
- Irregularity: see 1.5.

### 1.11 Ovulation/fertility (no medical claims)

- Ovulation = prior estimate (`ovulationFrom(nextPeriodDate)` − 14).
- Fertile window = `[ovulation−4, ovulation+1]` + uncertainty widening (halfWidth + 2 d luteal floor).
- Copy uses "estimate"/"likely" wording; `PredictWarning` surfaced; home shows "likely between X and Y" (honest).
- **No contraceptive reliability claims anywhere** (grep verified in copy; safety statements remain).

---

## 2. Reminders & notifications

### 2.1 Scheduling

- `ReminderAlarmScheduler.refreshSchedules(now)`: reads profile/consent/channelSettings/rules/prediction/cycles; builds `ScheduledReminder`s via `ReminderSchedulePlanner`; schedules via `ReminderAlarmFacade`.
- Tiers: `PeriodPrep`, `LatePeriod` → **Tier 1** `setExactAndAllowWhileIdle` (requires `SCHEDULE_EXACT_ALARM`); everything else → **Tier 2** `setAndAllowWhileIdle` (no permission).
- Degradation: `SecurityException` → downgrade to Tier 2 + `exact_alarm_denied` log.
- `ReminderRefreshWorker` — periodic daily + immediate; re-arms on launch.

### 2.2 Notification permission (Android 13+)

- `POST_NOTIFICATIONS` in manifest (app).
- `AndroidNotificationPermissionController` — `canPostNotifications = notificationsEnabled && (!required || granted)`; request path on API 33+ with rationale handling; Settings-open fallback.
- `NotificationPermissionGate` — app blocks screens when reminders enabled and permissions missing; `CycleCareApp` shows the gate (no back-trap).
- Delivery path re-checks at post time (`AndroidReminderNotificationClient.post` — permission check + `areNotificationsEnabled()`; `SecurityException` → `PermissionUnavailable`).
- **[Planned]** Phase 8: grant on API 37 AVD, verify alarms + notifications visible.

### 2.3 Android 14+ exact-alarm restrictions

- `AlarmManager.canScheduleExactAlarms()` checked immediately before scheduling (API 31+; false pre-31).
- `SCHEDULE_EXACT_ALARM` declared; `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → `ReminderRescheduleReceiver` → refresh.
- Settings UI: `ExactAlarmAccess` card (`SettingsScreen.kt:291`) only when not granted && canRequest; `SettingsScreenUiTest` (Settings module) — **[Planned]** run on AVD.
- **Policy:** exact alarm requested only for Tier 1 (justified); documented in `play-console/data-safety/` (submission pending).

### 2.4 Delivery pipeline (`ReminderDelivery.deliver`)

1. **In-app route:** if app foreground && !locked → record event + log; no notification (ReminderCenter is the in-app surface).
2. **Channel disabled** → record + log.
3. **Notifications blocked** → record + log.
4. **Missing channel settings** → record + log.
5. **Channel sync** → `ReminderChannels.sync`.
6. **Gates:** `ReminderDeliveryPolicy.decide` → auto-disable check → `evaluateDeliveryGates`: relevance (score ≥ threshold), weekly budget (12/week), quiet hours (22→8 default; `LatePeriod` exempt), DND (strict filter; `LatePeriod` exempt), arbitration fold (2 per 30 min).
7. **Defer:** `ReminderIntelligenceRepository.defer` + `ReminderDeferralScheduler.schedule` (Tier 2 inexact summary at end of quiet hours).
8. **Post:** `publishOnce` — `insertIfAbsent` lock (idempotence); `NotificationCompat.Builder` with `VISIBILITY_PRIVATE`, `setPublicVersion` (redacted), `BigTextStyle`, group, per-channel priority; group summary posted via `ReminderGroupSummary`.
9. **Retry:** `Failed` → `ReminderRetryScheduler` (5 min, Tier 2).

### 2.5 Doze/background behavior

- Tier 2 (`setAndAllowWhileIdle`) delivers in Doze (measured in history: awake 22 s, deep-idle 45 s on API 36). **[Planned]** re-verify on Pixel_10a API 37 via `ReminderDozeDeliveryTest` (opt-in, 2/2 expected).
- Tier 1 (`setExactAndAllowWhileIdle`) — same.
- `android.app.Instrumentation`? N/A.

### 2.6 Notification channels

- `ReminderChannels.sync` (API 26+): creates/updates channels, deletes stale ones (schema versioned IDs).
- Channels: CycleAlerts (HIGH), FertilityAlerts (DEFAULT), DailyCheckIn (LOW).
- `setVisibility(VISIBILITY_PRIVATE)`, `lockscreenVisibility` — no health data on lockscreen; `setPublicVersion` redaction.

### 2.7 Timezone/DST

- `ReminderSchedulePlanner.scheduleDateTime` — absolute via `ZonedDateTime.of(date, LocalTime, zone)`.
- `TIMEZONE_CHANGED`/`TIME_SET` → reschedule (see 1.8).

### 2.8 Duplicate prevention

- `reminder_events.id` = `"$ruleId-$scheduledAtEpochMillis"`; `insertIfAbsent` (ON CONFLICT IGNORE, >= 1 → posted).
- Reminder re-arm uses same PendingIntent identity (requestCode = hash of unique name; `FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE`) → alarm replacement, no dupes.

### 2.9 Notification cancellation & rescheduling

- Disabled rule → `refreshSchedules` cancels alarm; legacy WorkManager deliveries cancelled (`cancelLegacyDeliveries`).
- App reset → `ReminderResetCancellation` cancels alarms + notifications (app, summary, auto-disable).

---

## 3. Data persistence

### 3.1 Database

- Room DB `cyclecare.db`, schema v3, `exportSchema = true`; schemas in `core/database/schemas/` (referenced by `:app` androidTest via `sourceSets["androidTest"].assets`).
- Tables: `user_profile`, `cycle_records` (idx startEpochDay), `symptom_logs` (idx dateEpochDay), `migration_log`, `daily_symptoms`, `reminder_rules`, `reminder_events` (idx scheduledAtEpochMillis), `sync_outbox`, `engagement_profile`, `app_settings`.
- DAOs use `Flow` for observe + `suspend` for mutations; `@Query` strings are authoritative; enum persistence as constant names (R8-kept via `-keep class com.cyclecare.core.model.**`).

### 3.2 Migrations

- `migration1To2`: rule restructure, symptom_logs creation, seed rules.
- `migration2To3` (`migrateV2ToV3`): data-preserving v2→v3 migration:
  - Snapshot-based verification (`verifyV2ToV3Snapshot` — compares rows/IDs/columns before/after; throws → transaction rollback, v2 intact).
  - Legacy cycleLength → `legacyCycleLength` preserved; recomputed from start-to-start gaps (oldest keeps stored seed).
  - Duplicate symptom rows → most recent live; older → tombstones (`deletedAtEpochMillis`); `DUPLICATE_SYMPTOM_DATE` migration-log records.
  - `CYCLE_LENGTH_DIVERGENCE` migration-log for >7d disagreements (never silently corrected).
  - `HISTORY_CARRIED_OVER` summary record (counts).
  - Backfilled audit timestamps labeled via `createdAtIsBackfilled`.
- `MigrationDivergenceRepository` + `MigrationNotesScreen` — surfaces records to users (spec 02 §9.10.4).
- **[Planned]** Phase 8: migration suite re-run on API 37 AVD (34/34 expected).

### 3.3 Backup & restore

- Cloud backup: fully excluded (`data_extraction_rules.xml`, `backup_rules.xml`).
- Device-to-device transfer: `database/` + `file/datastore/` (PIN verifier transfers with new device).
- User-initiated encrypted backup: `LocalExportRepository.createEncryptedBackup` — PBKDF2WithHmacSHA256 (100k iterations, `Pbkdf2PassphraseKeyDeriver`), AES-GCM 128-bit (`AesGcmPayloadCipher`), magic `CYCLECARE_VAULT\u0001`, schema v1, key v1, salt (16 B), nonce (12 B), iterations, ciphertext; AAD-bound to recordId/schemaVersion/keyVersion. Passphrase policy: min 12 chars (`PassphrasePolicy.isAcceptable`, `MIN_RECOVERY_PASSPHRASE_LENGTH`).
- Import: `importEncryptedBackup` — magic/version checks, size caps (ciphertext ≤ 50 MB), decrypt, deserialize (bounds-checked, magic checked), transactional merge: tombstone-aware (deleted-wins, newer-wins), dedupes live rows per date (countLiveForStart/countLiveForDate), re-sources to `Imported`, cycles up to user-entry-limit? (not checked here — noted as observation), symptoms: newest wins, daily_symptoms replaced per log.
- Export artifacts (Markdown/PDF): `LocalExportRepository.createReport` — WellnessReportContent with derived intervals ("cycle length not known yet" for the most recent).
- **Never delete/reset data to fix a migration** — migration code verified read-only on source data + snapshot verification.

### 3.4 DataStore

- Main datastore (consent: hideNotificationPreview=true default, localDataAcknowledged, appLockEnabled, biometricEnabled; channel settings per key; notification permission request flag).
- Engagement counters in `app_settings` (`ReminderIntelligenceStore` — auto-disabled + deferred lists, encoded with `ReminderIntelligenceCodec`, corruption-logged).
- `DataStoreInterstitialStatsRepository`.

### 3.5 Offline operation

- Fully offline: Room + DataStore; no network calls on data paths; AdMob/UMP network calls are ad paths only.

---

## 3.6 Instrumented-suite status (2026-11-22)

- **On API 37 (`Pixel_10a`):** environment-limited — 271 tests collected; 0 errors; 110 passed; **159 compose-instrumentation failures** (`No compose hierarchies found`) — see `REMEDIATION_CHANGELOG.md` §9 and `FINAL_PRODUCTION_READINESS.md` §13 for the full analysis (instrumentation automation can't see the app tree on this AVD; app renders fine manually; disk-full prevented alternative AVD; failure reproduces with fully-historical code).
- **Historical green evidence (2026-11-10, `cc37`, API 37 — `CycleCare-Redesign-Docs/26-android-17-verification.md`):** 268 tests, 0 failures, 2 skipped (Doze opt-in re-run: 2/2; awake ~22 s, deep-idle ~45 s).

| # | Scenario | Status | Evidence |
|---|---|---|---|
| 1 | First-time install (onboarding) | **[Planned]** | Emulator installDebug, OnboardingScreenUiTest (6/6) — prior evidence |
| 2 | Existing-user upgrade (v2.1.0 → current) | **[Planned]** | MigratedV2DatabaseLaunchTest + migration suite 34/34 — prior evidence |
| 3 | Cycle entry creation (Home editor, Calendar editor, quick-start, onboarding) | **[Done — code-verified + tests]** | `CycleRecordValidatorTest`, `HomeScreenUiTest`, `CalendarScreenUiTest` |
| 4 | Cycle editing | **[Done — code-verified]** | Calendar/Home editor flows; `openEditSelectedCycle`/`openHistoryEditor` |
| 5 | Cycle deletion | **[Done — code-verified]** | `deleteSelectedCycle` → softDelete + refresh |
| 6 | Prediction recalculation on history edit | **[Done — code-verified + unit]** | `predictionRepository` recomputes from Room; save → refreshSchedules |
| 7 | Calendar navigation | **[Done — code-verified]** | month/selected-date state machine, RTL mirroring |
| 8 | Reminder creation/cancel/reschedule | **[Done — code-verified + tests]** | `ReminderAlarmSchedulerTest`, `ReminderDeliveryPolicyTest`, `ReminderDeliveryGatesTest`, `ReminderDozeDeliveryTest` |
| 9 | Background reminder delivery | **[Planned]** | `ReminderDozeDeliveryTest` on API 37 (expected 22 s/45 s — prior) |
| 10 | Process termination/restart | **[Planned]** | Adb kill + restart + prediction + alarm checks |
| 11 | Device reboot | **[Planned]** | Emulator reboot + alarm re-arm (`ReminderRescheduleReceiver`); prior evidence: Room durability verified |
| 12 | Offline operation | **[Done — code-verified]** | no network on data paths |
| 13 | Database migration | **[Done — code-verified + prior]** | Migrations.kt; `connectedDebugAndroidTest` on cc37: 34/34 |
| 14 | Data export/import | **[Done — code-verified]** | ExportRepository/ExportScreen; `LocalExportRepositoryIntegrationTest` |
| 15 | Theme & layout behavior | **[Done — code-verified]** | CycleCareTheme + CenterConstrainedBox; Tablet sweeps (Phase 3 prior) |
| 16 | Android system navigation | **[Done — code-verified]** | Back handler, predictive back (AdActivity declares `enableOnBackInvokedCallback=false`) |
| 17 | Notifications visible on 17 | **[Planned]** | Emulator: grant POST_NOTIFICATIONS, post test reminder via adb (`am broadcast`) |
| 18 | Irregular cycle detection | **[Done — unit tests]** | `InsightEngineTest`, `PredictionFertileWindowTest`, `NotificationIntelligenceTest` |
| 19 | Leap years/month boundaries | **[Done — java.time]** | `CalendarEstimateFramingTest`, `CalendarHistoryIntervalTest` |
| 20 | Timezone change (reminders reschedule) | **[Planned]** | adb timezone + `dumpsys alarm` |

---

## 5. Defects & observations found (Phase 5)

1. **Onboarding flash on activity recreation (pre-existing, Phase 5-independent):** `CycleCareApp` renders `OnboardingRoute` when `profile == null` while the first profile emission is pending — ~2–4 s flash on every rotation/recreation. Phase 3 fixed it? No — it's still present in `CycleCareApp.kt:283-303` (`ProfileLoadState.Loading` paints background; `Absent` renders Onboarding). This is a product defect (not an Android 17 issue) — recommend tri-state fix (owner approval).
2. **Latent NPE risk for in-progress cycles (null endDate):** app never writes null `endDate` (all save paths require non-null); comparators `today.isAfter(cycle.endDate)` (`HomeRoute.kt:537`, `InsightEngine.kt:154`, `CalendarRoute`: `base.selectedDate <= it.endDate`) would NPE if imported/legacy data contained one. `CycleRecord.isInProgress` exists but is unused. Recommend: harden with `endDate ?: cycle.startDate.plusDays(...)` or reject at import. Severity: low (no data path writes null end dates).
3. **SavedStateHandle enum parsing** (`LoggingRoute.toLoggingEditorState`, `CalendarRoute.toCycleEditorState`): `Mood::valueOf` without `runCatching` — a corrupt saved state string crashes on restore. Severity: low (app-private state; migration-safe on upgrades).
4. **Pin brute-force surface:** PIN hash (`(salt+pin).sha256()`) stored alongside salt in DataStore — no lockout delay (AppLockGate has 5-failure/30 s cooldown — good), but offline brute-force possible with file access. Not a vuln per se (PIN is local UI gate), noted for security audit.
5. **`feature/billing` orphan module** — not in build graph; flagged in Phase 1.
6. **`CycleCareApplication.seedDefaultReminderRules`** — seeds LatePeriod disabled by default — fine.
7. **Reminder body language**: "Period reminder", "Your period is likely between X and Y" — honest.
8. **`cycie day` progress on Home with no current cycle**: `cycleDay` null → progress 0 — fine.

---

## 6. Functional verification summary (baseline evidence)

- Unit tests (505) pass — includes `PredictionMathTest`, `PredictionFertileWindowTest`, `PredictionGoldenFixtureTest`, `InsightEngineTest`, `NotificationIntelligenceTest`, `NotificationTimingTest`, `ReminderDeliveryGatesTest`, `CycleRecordValidatorTest`, `MigrationDivergenceMappingTest`, `PrivacyRulesTest`, etc.
- Instrumented: 34/34 (`core:database`), 36/36 (`core:data`) — Phase 4 prior evidence.
- Emulator API 37 (Pixel_10a): Doze delivery 2/2, onboarding/calendar/home suites green (prior evidence — to be re-run in Phase 8).
- Prediction correctness: verified against golden fixtures (`GoldenFixtures`/`Corpus`) — representation invariants.
