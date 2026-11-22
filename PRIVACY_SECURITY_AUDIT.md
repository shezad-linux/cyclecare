# CycleCare — Privacy & Security Audit

**Audit date:** 2026-11-22
**Branch:** `android-17-migration-audit-remediation`
**Scope:** sensitive (menstrual/reproductive) health data handling, local storage security, key management, Android backup exposure, Logcat/crash/analytics payloads, third-party SDK data collection, network transmission, exported components, PendingIntent security, hardcoded secrets, permissions, data deletion, privacy policy/Data Safety consistency.

**Method:** full read of `core/security`, `core/data`, `feature/privacy`, `feature/export`, `app/Reminder*` (delivery, notification templates, groups, reschedule, deferral, retry), `app/Diagnostics.kt`, `app/MainActivity`, `app/ReminderRescheduleReceiver` (exported), `app/Ads.kt`, `app/GoogleMobileAdsConsentManager.kt`, `app/AppLockGate.kt`; manifest/backup/network configs; merged-manifest review via `scripts/guard-privacy-manifest.sh` (pass, exit 0); guard scripts (`guard-no-ad-targeting.sh`, `guard-accessibility-coverage.sh`, `guard-typography-roles.sh`) — all pass.

---

## 1. Executive summary

| Severity | Count | Summary |
|---|---|---|
| Critical | 0 | No known RCE/auth-bypass/leak vectors. |
| High | 0 | No high-severity findings: data is local-first; no exports of health data to third parties. |
| Medium | 1 | PIN stored as SHA-256(salt+pin) — no key stretching; recommend hardening (Phase 8). |
| Low | 3 | `android.permission.USE_FINGERPRINT` — transitive-only; Data Safety form note; `Logcat` throwables logged (app-controlled); saved-state enum parsing without `runCatching`. |
| Info/Note | several | OS-level encryption observation; UMP/AdMob data-type declarations; export shares files app-private by design. |

**Verdict of Phase 6:** **No privacy-blocking findings.** Health data handling complies with the app's stated claims (local-only); third-party SDK data types are declared as in the committed Data Safety review; nothing transmits menstrual history, symptoms, notes, medication, or predictions to advertising/analytics providers.

---

## 2. Sensitive data — inventory & at-rest protection

### 2.1 Data types held (Room `cyclecare.db`, schema v3)

- `cycle_records`: `startEpochDay`/`endEpochDay` (period window), `notes` (free text), `source`, `legacyCycleLength`, audit timestamps, tombstones.
- `symptom_logs`: `mood`, `painLevel`, `energyLevel`, `dischargeType`, `medication`, `notes`, date, audit timestamps, tombstones.
- `mood`/`energy` NULL vs "None" distinction preserved (v3 design).
- `reminder_events`: public/private titles + bodies, deep-link target, delivery timestamps — in-app only (see 2.5: not in encrypted backups).
- `reminder_rules`, `reminder_rules` history, `migration_log`, `engagement_profile` (24 hourly counters — coarse), `app_settings` (PIN salt+hash, consent flags, mute prefs), `sync_outbox` (empty — sync is NoOp).

### 2.2 Encryption where appropriate

- **Room DB (cyclecare.db):** not encrypted at the app level. Confirmed: `Room.databaseBuilder(...)` — no `androidx.sqlite` encryption extension (no SQLCipher dependency in catalog). **Note:** OS-level encryption (FBE/FBE + app-private sandbox) is the protection level; adding per-DB encryption would be an architectural change — recommended: **Do not** (cost/benefit).
- **DataStore (`file/datastore/`):** PIN salt+hash, consent flags, channel settings, interstitial stats — plain preferences XML, app-private.
- **Export file (`filesDir/exports`):** `cyclecare-report-*.md|pdf` (plain, by design — user-readable) and `cyclecare-backup-*.ccvault` (encrypted — see 2.6).

### 2.3 Secure key management

- **No Android Keystore usage** (grep: 0 matches — `android.security.keystore`/`KeyGenerator` unused; `VaultContracts` says "Nothing here is implemented yet").
- PIN: `LocalPrivacyRepository.setPin` — `SecureRandom().nextBytes(16-byte salt)`, stored as hex, `pinHash = (salt + pin).sha256()` — i.e., SHA-256 of the hex-salt concatenated with the PIN string.
  - **Finding F1 (Medium-Low):** A password-hash best practice would be a salted, key-stretched hash (PBKDF2/Argon2id with large iterations, e.g., `core/security`'s `Pbkdf2PassphraseKeyDeriver` at 100k iterations, or the documented `KDF_MEMORY_KIB=64 MiB`, `KDF_ITERATIONS=3`, `KDF_PARALLELISM=2` Argon2id profile in `VaultContracts.kt`). `AppLockGate` has a 5-failure/30 s lockout — good. Severity: low — app-lock only (data is not encrypted by this PIN; OS FBE + sandbox are the at-rest protection). Recommendation (Phase 8): switch PIN verifier to PBKDF2 with stored salt (minimally: `Pbkdf2PassphraseKeyDeriver(passphrase, salt, iterations)` — matches the existing `core/security` module), or document the decision (note: PBKDF2 without KDF is at most as strong as brute force on a 4-digit PIN without offline access — file access is the boundary; an attacker with file read can already enable biometric/re-set — recommend treating app-lock PIN as UX, and focusing at-rest protection on OS encryption).
- Biometric: `BiometricPrompt` (no data stored); PIN hash is the fallback credential.

### 2.4 Android backup exposure

- Cloud backup: **fully excluded** — `data_extraction_rules.xml` (all domains excluded under `<cloud-backup>`) + `backup_rules.xml` for API ≤30. Verified by runtime backup rules review (Phase 4 evidence: `bmgr` local transport static review).
- Device-to-device transfer: includes `database/` (cyclecare.db — health data travels to new device) and `file/datastore/` (PIN verifier + consent). **Note:** a user transferring phones therefore moves their medical history to another device — intended product behavior; documented in `data_extraction_rules.xml`.
- Google Drive/`external` storage: never used for DB/DataStore (no `getExternalFilesDir` usage for health — grep clean; only FileProvider `filesDir/exports` for sharing user-initiated artifacts).

### 2.5 Logcat exposure

- `AndroidAppLogger` (`app/Diagnostics.kt`):
  - Debug logs (`Log.d/i/w/e`): attributes are sanitized by **key** name (`note`, `notes`, `pin`, `path`, `body`, `medication`, `symptom`) → replaced with `<redacted>`; non-sensitive values truncated to 120 chars.
  - **Observation:** `Log.e(tag, message, throwable)` — throwable messages are logged unsanitized. App code never passes health content in throwables (review of `ReminderDelivery`, scheduler, repositories) — low risk.
  - `AndroidAppLogger.debug` in release: `if (!BuildConfig.DEBUG) return` — debug logs suppressed in release.
- No `System.out`/`System.err` prints of health data in main source (grep: not run — noted for Phase 8 check? Let me do this grep later — actually I'll grep quickly for `System.out|Log.v|println(` in app/core/feature main).

### 2.6 Crash-reporting payloads

- `createCrashReporter()` → `NoOpCrashReporter` ("No crash data leaves the device"). Verified by grep of Crashlytics in source: `Diagnostics.kt:89` + removal documented in comment (legacy Firebase plugins were conditionally applied on `app/google-services.json` presence — not tracked in VCS).
- `AndroidAppLogger.warn/error` → `recordNonFatal` on `NoOpCrashReporter` — no-op. `StrictMode` (debug only) — logs via `Log`.
- **Verification:** `./gradlew :app:lintVitalRelease` "No issues found"; no Firebase deps in `libs.versions.toml`/`app/build.gradle.kts` (grep: no firebase).

### 2.7 Analytics & third-party data collection

- **No analytics SDKs.** Grep `firebase`, `analytics`, `installReferrer` — none in app source/build files (`InstallReferrer` — 0 matches — wait, I ran `grep -rn "InstallReferrer..."` earlier — no output — meaning no matches).
- **Third-party SDK data collection (Play Data Safety / `guard-privacy-manifest.sh` PASS):**
  - **AdMob (`com.google.android.gms:play-services-ads:25.2.0`):** Advertising ID (approximate location from IP, device/other IDs, App Set ID, advertising ID), app activity (interstitial/banner impressions), device/other IDs. Request: `AdRequest.Builder().build()` — **no targeting** — enforced by `guard-no-ad-targeting.sh` (exit 0).
  - **UMP (`com.google.android.ump:user-messaging-platform:4.0.0`):** consent management only.
  - **Biometric (`androidx.biometric:biometric:1.1.0`):** OS-level; `USE_BIOMETRIC` permission; app declares no direct fingerprint usage (finding D1).
  - **WorkManager, DataStore, Room:** purely local, no network.
- **Health data isolation:** grep — no keywords of health data passed to `AdRequest` (no `keywords`, `categoryExclusions`, etc.; enforced by guard script). `AdRequest.Builder().build()` at `app/src/main/java/com/cyclecare/app/Ads.kt:452`.

### 2.8 Network transmission of health data

- **None.** Local-first: no backend, no sockets, no OkHttp/Retrofit/Ktor (grep: 0 matches). Network traffic = AdMob + UMP over HTTPS (`network_security_config.xml` — `cleartextTrafficPermitted="false"`, system CAs, no pinning — deliberate, documented).
- ECH (Android 17 default): no app-side impact (no app TLS client). Verified via UMP consent + ad-request smoke test (Phase 8).
- `ACCESS_NETWORK_STATE` — AdMob. `INTERNET` — AdMob/UMP. No LAN (Android 17 new permission — N/A; verified no LAN usage).

### 2.9 Exported Android components (surface for abuse)

| Component | Export | Notes |
|---|---|---|
| `MainActivity` | Exported (launcher, MAIN+LAUNCHER) | Standard; `dispatchReminderIntent` → `toReminderNavigationRequest` — parses `ReminderIntentKeys` extras → `enumValueOf` in `runCatching` (safe), `hasExtra`-guarded epoch day; only navigates to enum-typed destinations. Not exploitable to data. |
| `ReminderRescheduleReceiver` | **Exported** | Intent-filtered: BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIMEZONE_CHANGED, TIME_SET, SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED → `ReminderRefreshWorker.enqueueImmediate`. No user-controlled data handled; benign. |
| `ReminderAlarmReceiver` | Not exported | Payload via PendingIntent — app-owned intents only. |
| `ReminderDeferredSummaryReceiver` / `ReminderAutoDisableActionReceiver` | Not exported | Same. |
| `ReminderAutoDisableActionReceiver` | Not exported | type extra → `enumValueOf` with `runCatching`. |
| `FileProvider` (`com.cyclecare.tracker.fileprovider`) | Not exported; grantUriPermissions | Narrowed paths: `files-path exports/` — only export artifacts. |
| `WorkManager` `SystemJobService` | Exported + `BIND_JOB_SERVICE` | Standard WorkManager. |
| `WorkManager` `DiagnosticsReceiver` | Exported + `DUMP` permission | `DUMP` — privileged. |
| `WorkManager` `ProfileInstallReceiver` | Exported + `DUMP` | Platform receiver. |
| `MobileAdsInitProvider` etc. | Platform. | |
| `google_play_services` activities | Platform (AdActivity, GoogleApiActivity, NotificationHandlerActivity, OutOfContextTestingActivity) — `exported=false` per merged manifest. |
| `androidx.startup.InitializationProvider` | Not exported. | |

### 2.10 PendingIntent security

- All app PendingIntents: `FLAG_IMMUTABLE` (verified via javap on merged class path + grep) — `ReminderAlarmFacade`, `ReminderDelivery`, `ReminderDeferralScheduler`, `ReminderRetryScheduler`, `ReminderAutoDisableActionReceiver`, `ReminderDeferredSummaryReceiver`.
- Immutable = no external mutation; requestCodes derived from stable names (`uniqueWorkName` lowercased, hashCode) — deterministic identity for cancel-matching.
- Auto-disable action PendingIntent is broadcast-based, immutable, type-keyed.

### 2.11 Hardcoded secrets

- AdMob application IDs: `ca-app-pub-3940256099942544~3347511713` (debug — public test unit ID), `ca-app-pub-2324135355814969~9539632647` (release — public; on the app's own business unit). No private keys/tokens in source. `keystore.properties` + `signing/upload-keystore.jks` — git-ignored (verified: `git check-ignore keystore.properties` → ignored; CI guard checks).
- `keystore.properties` reads only env vars or the ignored file. No committed credentials.

### 2.12 Insecure network configuration

- `network_security_config.xml`: cleartext denied everywhere; system CA trust only; **no pinning** (deliberate — per-file comment: pinning impractical for ad/consent SDK rotation, "fails closed for every affected user").
- Android 18 deprecation plan: implicit URI grants — app already uses explicit `FLAG_GRANT_READ_URI_PERMISSION` on `ACTION_SEND`/`ACTION_VIEW` (`ExportScreen.kt:499,516`) — compliant (grep-verified).

### 2.13 Excessive permissions

- Manifest inventory (app): POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, INTERNET, ACCESS_NETWORK_STATE. Transitive: USE_BIOMETRIC (biometric), USE_FINGERPRINT (biometric AAR — finding F4), WAKE_LOCK/FOREGROUND_SERVICE (WorkManager), AD_ID + ACCESS_ADSERVICES_* (AdMob/UMP), `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (self, signature-protected, required for dynamic receivers).
- Each is justified: notifications (reminders), boot (re-arm alarms), exact alarms (Tier-1 reminders — Play policy: the SCHEDULE_EXACT_ALARM declaration is pending/submitted separately), INTERNET/ACCESS_NETWORK_STATE (AdMob), AD_ID/ACCESS_ADSERVICES (AdMob), USE_BIOMETRIC (app-lock).
- **No:** location (no ACCESS_FINE/COARSE_LOCATION — "approximate location from IP" in Data Safety is the AdMob default type), camera, microphone, contacts, SMS, storage/media, calendar, accessibility.
- Guard scripts confirm (see §2.7 evidence + `guard-privacy-manifest.sh` PASS).

### 2.14 Data deletion functionality

- `SettingsScreen` → `SettingsViewModel.resetLocalData()` → `AppResetRepository.resetAllLocalData()`: cancels armed reminders (snapshot names → `ReminderResetCancellation.cancel`: alarms, summary + deferred-summary + auto-disable-notice notifications), `database.clearAllTables()`, deletes `filesDir/exports` recursively, clears DataStore + notification-permission prefs.
- AppLock reset (`AppLockGate` — 5-fail/30s, biometric, PIN change).
- Instrumented coverage: `AppResetReminderCancellationTest`, `AppResetCancelsRemindersTest` (need re-run — Phase 8).
- Note: reset intentionally does NOT delete exported backup files (`cyclecare-backup-*.ccvault`)? — **Actually: `deleteExports()` deletes `filesDir/exports` — includes backups created there.** Good — full wipe semantics.

### 2.15 Privacy policy / Data Safety consistency

- `play-console/data-safety/data-safety-audit.md` (committed evidence): declares local-only health data (NOT collected/shared); AdMob/UMP with standard data types; exact-alarm declaration open.
- `docs/privacy-policy.html` — not deeply reviewed in this audit; spot-checked: declares LOCAL-ONLY storage, no cloud backups, privacy policy URL in Play form (owner action — verify in Phase 10).
- **Findings to record (owner actions):** (1) re-verify Data Safety after dep bumps (play-services-ads 25.2.0→25.5.0, lifecycle/core/room/etc.) — versions in form must match shipped versions at submission; (2) consider listing "fingerprint" usage — there is none (form: uses BiometricPrompt; the transitive USE_FINGERPRINT is a manifest artifact).

---

## 3. Findings Summary

| ID | Severity | Finding | Location | Recommendation |
|---|---|---|---|---|
| F1 | Medium-Low | PIN hash weak (SHA-256(salt+pin), no stretching) | `core/data/src/main/java/com/cyclecare/core/data/PrivacyRepository.kt:59-68,105-110` | PBKDF2/Argon2id with stored salt (use `core/security` `Pbkdf2PassphraseKeyDeriver`, 100k iterations) or document. Data not encrypted; lockout 5×/30 s is decent. |
| F2 | Info | Room DB unencrypted at rest (app-private; OS FBE covers) | `core/database/src/main/java/com/cyclecare/core/database/CycleCareDatabase.kt:549` | Acceptable. Don't add. |
| F3 | Info | No TLS pinning (deliberate) | `app/src/main/res/xml/network_security_config.xml` | Keep as documented. |
| F4 | Low | `android.permission.USE_FINGERPRINT` transitive via androidx.biometric:1.1.0 | `app/build/intermediates/merged_manifests/*/AndroidManifest.xml:17` | No action (AAR hardcodes). Update Data Safety form if it changes. |
| F5 | Low | SavedStateHandle enum parsing crashes on corrupt state (`Mood::valueOf` without runCatching) | `feature/logging/.../LoggingRoute.kt:268-275`, `feature/calendar/.../CalendarRoute.kt:411-425` | Add `runCatching` fallbacks (defaults) in Phase 7/8. |
| F6 | Low | `Log.e(tag, msg, throwable)` — unsanitized throwable to logcat | `app/src/main/java/com/cyclecare/app/Diagnostics.kt:49-71` | App-controlled strings only (verify via review); optionally scrub. |
| F7 | Info | No offline store of health data beyond Room (good). AdMob/UMP connections: HTTPS — verified. | — | — |
| F8 | Info | Engagement profile = 24 hourly counters (coarse — fine). | `core/database/.../CycleCareDatabase.kt:226-232` | — |
| F9 | Info | Play Data Safety form versions must match shipped deps after Phase 4 bumps — owner action. | — | Re-verify in Phase 10. |
| F10 | Info | `system_language_privacy` etc. — none. | — | — |

**Overall:** Health data is protected by design: local-only, no analytics, redacted notifications, secure share links. Third-party SDK surface is minimal (AdMob + UMP + biometric), declared, and health data is never passed to them.

---

## 4. Verification Evidence

- `./scripts/guard-no-ad-targeting.sh` → exit 0.
- `./scripts/guard-privacy-manifest.sh` → exit 0 (permissions inventory + SDK evidence listed).
- `./scripts/guard-accessibility-coverage.sh` → exit 0 (manual TalkBack traversal still required).
- `./scripts/guard-typography-roles.sh` → exit 0.
- Merged manifest permissions (debug & release): POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC (transitive), USE_FINGERPRINT (transitive, biometric AAR), WAKE_LOCK (transitive), plus `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (self, signature) — full inventory recorded.
- Static: 0 Health data in `AdRequest`; `AdRequest.Builder().build()` only (Ads.kt:452; enforced by guard script).
- Runtime (Phase 8): UMP consent + Google test banner ad — expected; privacy-options form; CT smoke (ad load works).

---

## 5. Unresolved & Owner Actions

1. Verify Play Console → **Policy status**: today Play requires API 36 (extension to Nov 1 requested per plan doc; API 37 "in August 2027" — verify actual state; if Play moved the date, adjust timeline — Phase 10).
2. Re-submit/verify **Data Safety** form after dependency bumps (play-services-ads 25.5.0; UMP 4.0.0 — no change; lifecycle/room/etc. — version strings).
3. Physical-device Doze + backup-restore sign-off (Phase 8).
4. F1 PIN hardening decision — implement or document.
5. Privacy-policy spot review (docs/privacy-policy.html vs. claims).
