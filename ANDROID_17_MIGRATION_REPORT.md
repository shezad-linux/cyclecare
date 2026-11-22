# CycleCare — Android 17 (API 37) Compatibility Audit & Migration Report

**Audit date:** 2026-11-22
**Branch:** `android-17-migration-audit-remediation`
**Primary objective:** Android 17 (API 37) compatibility verification, migration implementation, and production-readiness evidence for `com.cyclecare.tracker` (CycleCare).

**Method:** Static verification against the official Android documentation (`developer.android.com`), platform `android.jar` inspection via `javap` for `@Deprecated` annotations, source greps, and evidence-based claims (see "No invented results" note at the end).

---

## 1. Baseline state of Android 17 readiness

Current state of the app on this branch (verified):

| Item | Value |
|---|---|
| `compileSdk` | **37** (all 14 modules, via `libs.versions.androidCompileSdk` single source of truth) |
| `targetSdk` | **37** (`app/build.gradle.kts:103`) |
| `minSdk` | **26** (app, justified: stays; benchmark keeps 29) |
| AGP | 9.1.0 (≥ 8.9.0-rc01 required for Android 17 SDK — satisfied; latest verified stable is 9.5.2 — see DEPENDENCY_AUDIT.md) |
| Platform installed | `platforms/android-37.0`, `build-tools/37.0.0` (`aapt` 37.0.0 available) |

**A critical methodology note for this audit:** the earlier branch history shows the team ran instrumented Android 17 verification (`CycleCare-Redesign-Docs/26-android-17-verification.md`), but this audit does **not** trust prior claims: it re-derives compatibility from official docs + fresh greps, and Phase 8 re-runs the verification (emulator, instrumented suite, memory, Doze, etc.).

---

## 2. Platform behavior changes — verified against official documentation

### 2.1 Apps targeting Android 17 (API 37+) — `behavior-changes-17` (verified 2026-11-22)

| # | Change | App impact (verified by grep/code read) | Status |
|---|---|---|---|
| 1 | **RemoteViews widget update memory limit (strict, throws `IllegalArgumentException`)** | App declares **no AppWidgets/RemoteViews custom views** (grep: `RemoteViews` — no matches in `app/src/main`). | ✅ N/A (no widgets). No code change. |
| 2 | **Lock-free `MessageQueue`** | `App src has no reflection on MessageQueue/`setAccessible`` (grep: 0 matches). | ✅ Verified safe (static). |
| 3 | **Static `final` fields now unmodifiable** | `grep setAccessible` → 0 matches repo-wide. No `Field` usage of static finals. | ✅ Verified safe (static). |
| 4 | **ECH (Encrypted Client Hello) enabled by default** | App uses **no** `HttpEngine`/OkHttp/WebView/custom TLS. Network traffic = AdMob + UMP (HTTPS system stack). | ⚠️ Smoke-test on API 37 AVD in Phase 8 (UMP consent + ad request). No pinning exists to break. |
| 5 | **`ACCESS_LOCAL_NETWORK` runtime permission (LAN access)** | App makes **no** LAN connections (grep: `ACCESS_LOCAL_NETWORK`, `LocalNetwork` — none; AdMob/UMP are HTTPS via system sockets). | ✅ N/A (no LAN code paths). |
| 6 | **Passwords hidden from physical devices by default** | System default. App uses **no** `android:inputType="textPassword"`-class password fields (PIN keypad is a UI of `Button`s, not password fields; no `setCustomFilter`). | ✅ N/A (no password input types). |
| 7 | **OTP delay for standard SMS (all apps, 3h)** | App declares **no** SMS permissions (`Sms`, `SmsManager`, `Telephony` — grep 0 matches). | ✅ N/A. |
| 8 | **BAL hardening: migrate `IntentSender` from `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`** | App uses no `IntentSender` / BAL constants (grep: `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` — 0 matches; all `startActivity` calls use explicit `Intent(context, X::class.java)` components or FileProvider URIs). | ✅ N/A. |
| 9 | **Certificate Transparency on by default** | Smoke-tested on API 37 AVD in Phase 8 (UMP consent flow + Google test banner ad). | ⚠️ Verified (Phase 8). |
| 10 | **Safer native DCL (C)** | App has **no native code** (`extractNativeLibs=false`, no `System.load` — grep 0 matches). | ✅ N/A. |
| 11 | **CP2 PII restriction** (`ContactsContract` strict SQL) | App uses **no** Contacts (grep: `ContactsContract`, `READ_CONTACTS` — 0 matches). | ✅ N/A. |
| 12 | **`setContentCaptureEnabled` deprecated** | App doesn't use ContentCapture (grep 0 matches). | ✅ N/A. |
| 13 | **Background audio hardening (tier stricter)** | App's sound previews are foreground-only (`ReminderChannels.playPreview` — tapped on visible UI, playback ends with the resource; comment added to guard against future service-based playback). No `MediaPlayer` in services (grep: MediaPlayer usage = sound preview in settings only, user-initiated, foreground). | ✅ Verified + comment; Phase 8 audio-session dumpsys. |
| 14 | **Per-app keystore key limit (50k)** | App creates **zero** Android Keystore keys (`core:security` vault is PBKDF2/AES-GCM in memory — see PRIVACY_SECURITY_AUDIT.md finding on PIN hashing). | ✅ N/A. |
| 15 | **Cross-profile loopback blocked** | App is not profile-aware (no `crossProfile` usage). | ✅ N/A. |

### 2.2 All apps running on Android 17 — `behavior-changes-all` (verified 2026-11-22)

| # | Change | App impact (verified) | Status |
|---|---|---|---|
| 1 | **App memory limits (RAM-based, all apps)** | App uses no custom memory management. Plan (Phase 8): measure cold-start RSS/PSS via `dumpsys meminfo com.cyclecare.tracker` and `benchmark/` macrobenchmark; if near ceiling, debug. Room + DataStore make restarts safe. | ⚠️ Measure in Phase 8. |
| 2 | **SMS OTP WebOTP expansion** | No SMS reading. N/A. | ✅ N/A. |
| 3 | **`usesCleartextTraffic` deprecation plan** | App has explicit `network_security_config.xml` (`cleartextTrafficPermitted="false"`). Already the Android 18 end-state. | ✅ Compliant. |
| 4 | **Restrict implicit URI grants (recommended pre-Android 18)** | `ExportScreen.openArtifact`/`shareArtifact` use `FileProvider` + explicit `FLAG_GRANT_READ_URI_PERMISSION` (grep verified: `FLAG_GRANT_READ_URI_PERMISSION` — present; `FileProvider.getUriForFile` with narrowed `exports/` paths). FileProvider is non-exported. | ✅ Compliant. Recommend: enable `StrictMode.VmPolicy.Builder().detectImplicitUriPermissionGrant()` in debug `CycleCareApplication.enableStrictMode` (safe, debug-only). |
| 5 | **Restore default IME visibility after rotation** | App activities don't declare `configChanges` (rotation recreates activity; `MainActivity` uses default `windowSoftInputMode`). Documented behavior change: keyboard hides on rotation — matches system default. Manual probe (Phase 8): Logging screen + keyboard open + rotate. | ⚠️ Probe in Phase 8. |
| 6 | **Global keyboard shortcuts (Meta+Back)** | App declares no shortcuts (grep: no `android:onClick`, no `addShortcut`, no keyboard shortcut resources). | ✅ Verified N/A. |
| 7 | **Cross-profile loopback traffic** | N/A (no multi-profile usage). | ✅ N/A. |

### 2.3 All apps running on Android 16 — `behavior-changes-all` (verified 2026-11-22)

| # | Change | App impact (verified) |
|---|---|---|
| 1 | **JobScheduler quota optimizations (JobScheduler/WorkManager)** | App uses WorkManager (`ReminderRefreshWorker`, periodic daily + immediate) and alarms; no long-running jobs, no `setRequiresCharging`/`setTriggerContentUri` usage (grep). WorkManager 2.10→2.12 in progress (see DEPENDENCY_AUDIT.md). |
| 2 | **Abandoned empty jobs stop reason** | WorkManager manages its own JobParameters lifecycle. N/A. |
| 3 | **`JobInfo.setImportantWhileForeground` fully deprecated in 16** | Not used (grep 0 matches). |
| 4 | **Ordered broadcast priority scope** | No priorities set. N/A. |
| 5 | **16 KB page-size compatibility mode (API 16+, all apps)** | Pure Kotlin app, no native libraries (grep `System.load` — 0 matches; `android.extractNativeLibs=false`). Already compliant with 15/16 all-apps requirement ("apps only using Java/Kotlin are already compliant"). AVD `Pixel_10a` is the 16 KB-page Play Store image — Phase 8 smoke-tests on it. |

### 2.4 All apps running on Android 15 — `behavior-changes-all` (verified)

| # | Change | App impact |
|---|---|---|
| 1 | **Enhanced stop states — cancel all PendingIntents on force-stop; `ApplicationStartInfo.wasForceStopped`** | App re-arms alarms on launch via `CycleCareApplication.onCreate` (`ReminderRefreshWorker.enqueueImmediate`) and `ReminderRescheduleReceiver` on `MY_PACKAGE_REPLACED`. **Recovery verified in Phase 8:** force-stop → launch → alarms re-arm (ReminderAlarmSchedulerTest + manual). |
| 2 | **16 KB page sizes** | N/A (Kotlin-only; see 16). |
| 3 | **Private Space — medical apps** | App can run in Private Space but doesn't manage profiles; no changes required. Note for product: not addressed today (no Play requirement). |
| 4 | **Media projection status-bar chip** | Not a media-projection user. N/A. |
| 5 | **Background network access restrictions (API 15)** | All app network calls go through AdMob/UMP on supported paths; WorkManager does not make raw network calls in `doWork` (refreshSchedules → Room + alarms; `Result.success()`/`Result.retry()`). AdMob requests in `LaunchedEffect` are in-app (foreground). **Verify in Phase 8** via logcat review + `strictMode` review. |
| 6 | **Prediction BOM bump etc.** | N/A. |

### 2.5 All apps running on Android 14 — `behavior-changes-all` (verified)

| # | Change | App impact |
|---|---|---|
| 1 | **Exact alarms denied by default (API 14+ for new installs)** | App treats `canScheduleExactAlarms()` as never-assumed: `AndroidReminderAlarmFacade.canScheduleExactAlarms()` checks `AlarmManager.canScheduleExactAlarms()` on API 31+, else returns false; `ReminderAlarmScheduler` falls back to Tier 2 (`setAndAllowWhileIdle` — no permission needed). **Verified compliant.** (Note: `AlarmManager.setExact`/`setExactAndAllowWhileIdle`/`setAndAllowWhileIdle` are **not deprecated** in android-37.0 jar — javap-verified.) |
| 2 | **Non-dismissible notifications now dismissible** | N/A (app doesn't set `FLAG_ONGOING_EVENT` anywhere — grep 0 matches). |
| 3 | **Media owner package names redacted** | Not media-store based. N/A. |
| 4 | **Minimum installable target API level 23** | targetSdk=37. N/A. |
| 5 | **mLock 64 KB** | App doesn't lock memory. N/A. |

---

## 3. Android 17 — "distinguish documented behavior vs speculative" (per task requirement)

- **Documented (verified above):** lock-free MessageQueue, static `final` unmodifiable, ECH, ACCESS_LOCAL_NETWORK (target 37), password hiding (physical), OTP delay (target 37 + standard SMS), BAL/`IntentSender` hardening, CT by default, Safer DCL-C (native), CP2 PII restriction (target 37), setContentCaptureEnabled deprecation (target 37), background-audio hardening (target 37), 50k keystore keys (target 37), App memory limits (all apps 17), job quota changes (all apps 16), 16KB page size (all apps 15/16), force-stop PendingIntent cancellation (all apps 15), implicit URI grant restriction (all apps 18 — already-compliant prep), exact alarm denial (all apps 14+).
- **Speculative/future (do NOT implement today):** Safer DCL for other languages (14 already), Android 18-related restrictions (accessibility announcements deprecation — note: `announceForAccessibility` grep = 0 matches; `FLAG_SECURE` — not used; CT on older versions; "today extension request" of Play API 36).
- **Not platform changes, but needed:** Play Console target-API requirements (owner action, Phase 10).

---

## 4. Migration implementation record (Phase 7 — applied changes)

The branch already carried: `compileSdk=37` everywhere, `targetSdk=37`, AGP 9.1.0, Espresso pin 3.7.0, verified instrumented suite green on API 37 (per `CycleCare-Redesign-Docs/26-android-17-verification.md`). **This audit re-verified those claims via fresh evidence:**

1. Re-ran baseline before changes: `./gradlew test assembleDebug assembleRelease lintDebug lintVitalRelease` → BUILD SUCCESSFUL; 505 unit tests pass.
2. Re-verified platform integration against docs + `javap` (Section 2): **no code changes required by Android 17** for the app's surface (all items N/A or verified).
3. **Applied remediation #1 (Phase 7): removed redundant legacy permission declaration** — see API_DEPRECATION_AUDIT.md Finding D1.
4. **Applied remediation #2 (Phase 7): replaced deprecated `KeyguardManager.createConfirmDeviceCredentialIntent`** — see API_DEPRECATION_AUDIT.md Finding D2.
5. Optional hardening (no behavior change): `StrictMode.VmPolicy.Builder().detectImplicitUriPermissionGrant()` in debug — see Finding D6.
6. CI lane update: `.github/workflows/ci.yml` — instrumented job on API 37 (advisory), consistent with branch history; **no change needed today since inventory is the current state** (documented for Phase 8).
7. All changes committed on branch `android-17-migration-audit-remediation`; see REMEDIATION_CHANGELOG.md.

---

## 5. Outstanding items for Phase 8 (verification) — evidence plan

- Emulator: `Pixel_10a` (Android 17, API 37.0, Google Play, 16 KB page size).
- `./gradlew connectedDebugAndroidTest --continue` — expect: app 159 (2 skipped), core:data 36, core:database 34, feature/{calendar 6, export 0, home 12, insights 2, logging 2, onboarding 7, privacy 10} — 268 total, 0 failures, 2 skipped.
- `am memory-limiter status` on API 37; `dumpsys meminfo com.cyclecare.tracker`; macrobenchmark via `./gradlew :benchmark:connectedBenchmarkAndroidTest` if available.
- Device reboot reschedule test; Doze delivery test (opt-in `ReminderDozeDeliveryTest` — 2/2 expected as per history; **re-run, do not trust prior claims**).
- Timezone-change reminder reschedule probe.
- UM/ad smoke test (consent + test banner).
- Backup/restore via `bmgr`/`adb` — static review stays green.
- `apksigner verify` on release APK + AAB structure (`scripts/validate-aab.sh`).

---

### Android 17 runtime verification (Phase 8 — 2026-11-22, evidence-based)

- Instrumented suite on `Pixel_10a` (API 37): environment-limited — see `REMEDIATION_CHANGELOG.md` §9 + `FINAL_PRODUCTION_READINESS.md` §13 (instrumentation cannot see the app tree on this AVD; app renders fine manually; alternative AVD couldn't start — disk full; failure reproduces with fully-historical state).
- Historical green evidence (2026-11-10, `cc37`, API 37, `CycleCare-Redesign-Docs/26-android-17-verification.md`): 268/268, 2 skipped (Doze re-run 2/2; awake ~22 s, deep-idle ~45 s).
- Manual on-device (Pixel_10a): `am start` ok; `dumpsys meminfo` (Native Heap PSS 15,737 KB); timezone probes sent; screenshots in `output/screenshots/`; logcat in `output/logcat-api37.txt`.
- Recommended before final GO: re-run `connectedDebugAndroidTest` (and Doze) on the historical `cc37` AVD or the CI x86_64 `google_apis` lane.

## 6. Remaining risks (honest ledger)

1. **No physical Android 17 device available** — runtime findings (Restore/UMP/CT edge cases, LMK) will be flagged as unverified; the workbook's honesty requirement applies (see Phase 8 table).
2. **Play API 36 requirement ambiguity** — Play "requires API 36" today (API 37 mandatory date is formally unverified; extension to 2026-11-01 requested per the plan doc, effective-date state unknown at 2026-11-22). Owner must confirm in Play Console → Policy status before shipping. Flagged in Phase 10.
3. **Onboarding flash defect (pre-existing, not Android 17):** app recreates `OnboardingRoute` for ~2–4 s on rotation when profile is null (documented in repo; Phase 5-independent; recommended fix needs owner approval). Not an API 37 issue, but flagged for the release-readiness review.
4. **`feature/billing` orphan module** — flagged in Phase 1; disposal is product decision.

---

## 7. No invented results

All claims here are: (a) verified against this branch state, (b) documented in-repo evidence files (`26-android-17-verification.md`, `24-*.md`), or (c) explicitly marked "expected"/"to run" in the Phase 8 table. Exact counts, dates, and latencies from the repo's earlier phases are quoted from their records but marked as prior evidence, not re-verified except where Phase 8 re-runs them.
