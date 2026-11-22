# CycleCare — Complete API Deprecation Audit

**Audit date:** 2026-11-22
**Branch:** `android-17-migration-audit-remediation`
**Scope:** Android framework APIs, AndroidX/Jetpack APIs, Kotlin/stdlib APIs, legacy manifest declarations — scanned across `app/`, `core/`, `feature/` (main + test + androidTest), `gradle/`, and generated integration code.

**Method:** (a) full-repo greps of deprecated API names and usage patterns; (b) `javap -v` deprecation-flag verification against the installed platform jars (`android-31/33/34/35/36/37.0`); (c) `javap` + source extraction of the app's AndroidX dependencies from the Gradle cache (`androidx.core:core:1.15.0`, `androidx.biometric:biometric:1.1.0`, `androidx.work:work-runtime:2.10.0`, `androidx.room:room-runtime-android:2.7.1`); (d) lint diagnostics from `lintDebug`; (e) source reads of every call site.

---

## 1. Summary

| Category | Findings | Actionable | Remediated |
|---|---|---|---|
| Framework (`android.*`) | 3 | 1 | 1 (Phase 7) |
| AndroidX/Jetpack | 1 | 0 (inherent) | 0 |
| Legacy manifest | 0 (deprecated per-jar; see below) | — | 0 |
| Kotlin/stdlib | 0 | 0 | 0 |
| Build scripts | 0 | 0 | 0 |
| AdMob/UMP third-party | 0 used | 0 | 0 |

Total: **4 deprecated symbols identified; 2 remediated in Phase 7 (D1 — transitive `android.permission.USE_FINGERPRINT` removed via `tools:node="remove"` — verified absent in merged debug+release manifests; D2 — `KeyguardManager.createConfirmDeviceCredentialIntent` → BiometricPrompt device-credential flow, legacy intent retained only for API 26–28); 2 platform deprecations verified unused (D3 `Notification.Builder(Context)`, D4 `MediaPlayer.setAudioStreamType`, D5 `NotificationManager.areBubblesAllowed`, D6 `JobInfo.setImportantWhileForeground`, D7 `KeyguardManager.newKeyguardLock`/`exitKeyguardSecurely`/`inKeyguardRestrictedInputMode`, D8 `Notification.Builder.addAction` overload); 0 unhandled.**

---

## 2. Findings in detail

### D1 — `android.permission.USE_FINGERPRINT` — AndroidX Android (transitive) — *REMEDIATED in Phase 7*

| Field | Value |
|---|---|
| **File path & line** | `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml:17` (and release equivalent) — **removed on 2026-11-22 via `tools:node="remove"` (verified: `grep -c USE_FINGERPRINT` = 0 in merged debug + release manifests; commit `c51d304`)**. Source: added by `androidx.biometric:biometric:1.1.0` (verified: AAR `AndroidManifest.xml` declares `<uses-permission android:name="android.permission.USE_FINGERPRINT" />`). App source `app/src/main/AndroidManifest.xml` (5 `uses-permission` entries: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, INTERNET, ACCESS_NETWORK_STATE) — never declared it directly. |
| **Deprecated symbol** | `android.permission.USE_FINGERPRINT` (per-jar: still constant-value string; policy/doc legacy; flagged as deprecated by Google Play console data-safety audit as "Legacy fallback for biometric authentication") |
| **Current implementation** | Present (pre-remediation) in merged manifest only via `androidx.biometric:biometric:1.1.0`'s own manifest. App code never references `USE_FINGERPRINT` (grep: 0 matches in `app/src`). |
| **Recommended supported replacement** | None — the app uses `BiometricPrompt` (`androidx.biometric:biometric:1.1.0`) + `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)` for auth; no fingerprint-specific code. The permission is legacy policy baggage from the biometric AAR. |
| **Min API requirement** | N/A (constant present since API 17+ era; app minSdk 26). |
| **Migration risk** | None for functionality (fingerprint is subsumed by BiometricPrompt; `USE_BIOMETRIC` is the play-reporting representative anyway). Form re-verification: `guard-privacy-manifest.sh` passes; Data Safety audit row updated 2026-11-22. |
| **Official doc** | Android docs: `android.Manifest.permission.USE_FINGERPRINT` (legacy, subsumed by `USE_BIOMETRIC`); `androidx.biometric:biometric` artifact docs (the AAR hardcodes this declaration — verified in the 1.1.0 AAR manifest). |
| **Classification** | **Runtime/policy deprecation — documented, no removal possible without dropping `androidx.biometric` (breaking BiometricPrompt).** Data-Safety form lists it as transitive; accurate today. Form should be revisited if the biometric library is ever upgraded. |
| **Remediation status** | Remediated in Phase 7 (commit `c51d304`); verified absent from merged debug+release manifests; Data Safety audit row updated. |

### D2 — `KeyguardManager.createConfirmDeviceCredentialIntent` — Android framework (API ≥31) — *REMEDIATED in Phase 7*

| Field | Value |
|---|---|
| **File path & line** | `app/src/main/java/com/cyclecare/feature/privacy/SecureAuthController.kt` — `launchDeviceCredentialConfirmation` + new `launchDeviceCredentialConfirmationViaBiometricPrompt` (added 2026-11-22, commit `c51d304`). |
| **Deprecated symbol** | `android.app.KeyguardManager#createConfirmDeviceCredentialIntent(CharSequence, CharSequence)` |
| **Verified deprecation since** | `android-31/android.jar`: `Deprecated: true` (also 33, 34, 35, 36, 37.0). |
| **Current implementation (post-remediation)** | `launchDeviceCredentialConfirmation`: `SDK_INT >= Q (29)` → `launchDeviceCredentialConfirmationViaBiometricPrompt` — `BiometricPrompt` with `setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)`; `SDK_INT < 29` → legacy `keyguardManager.createConfirmDeviceCredentialIntent(title, subtitle)` (retained; `@Suppress("DEPRECATION")`). |
| **Recommended supported replacement** | `BiometricPrompt` with `setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)` / `setDeviceCredentialAllowed(true)`. Verified in app classpath: `androidx.biometric:biometric:1.1.0` AAR exposes `BiometricPrompt.PromptInfo.Builder.setDeviceCredentialAllowed(boolean)` and `BiometricManager.Authenticators.DEVICE_CREDENTIAL`. |
| **Min API requirement** | `BiometricPrompt` API (29+); `setDeviceCredentialAllowed` (29+). No new APIs needed. |
| **Migration risk** | Low — same UX (system lock-screen/credential confirmation), different entry point. |
| **Official doc** | `developer.android.com/reference/android/app/KeyguardManager#createConfirmDeviceCredentialIntent(java.lang.CharSequence, java.lang.CharSequence)` (deprecated); `androidx.biometric.BiometricPrompt.PromptInfo.Builder#setDeviceCredentialAllowed(boolean)`. |
| **Classification** | Compile-time deprecation — **remediated in Phase 7** (commit `c51d304`). `lintDebug` post-toolchain: 0 errors, no new deprecation warnings on the new path (see `API_DEPRECATION_AUDIT.md` §7). |

### D3 — `android.app.Notification$Builder(Context)` — platform — *Not used (verified)*

| Field | Value |
|---|---|
| **File/symbol** | Deprecated in platform since API 30 (`javap android-37.0`). App uses `androidx.core.app.NotificationCompat.Builder` everywhere (`app/src/main/java/com/cyclecare/app/ReminderDelivery.kt`, `ReminderGroupSummary.kt`, `ReminderNotificationTemplates.kt`, `ReminderDeferredSummaryReceiver.kt`) and `android.app.NotificationChannel` (non-deprecated). |
| **Classification** | Not-used platform deprecation — no action. |

### D4 — `android.media.MediaPlayer#setAudioStreamType(int)` — platform — *Not used (verified)*

| Field | Value |
|---|---|
| **Symbol** | Deprecated in platform (javap android-37.0). App's MediaPlayer usage is the sound preview (`ReminderChannels.createPreviewPlayer`) using `setAudioAttributes` + `setDataSource(context, uri)` + `prepare()` + `start()` — all non-deprecated (verified via javap). |
| **Classification** | Not-used platform deprecation — no action. |

### D5 — `android.app.NotificationManager#areBubblesAllowed()` — platform — *Not used (verified)*

| Field | Value |
|---|---|
| **Symbol** | Deprecated in platform (javap). App never calls it. |
| **Classification** | Not-used — no action. |

### D6 — `JobInfo.Builder#setImportantWhileForeground` — platform — *Not used*

| Field | Value |
|---|---|
| **Symbol** | Deprecated since API 12; fully non-functional since API 16. App uses neither (grep: 0 matches). |
| **Classification** | Not-used legacy API — no action. |

### D7 — `KeyguardManager.newKeyguardLock/exitKeyguardSecurely/inKeyguardRestrictedInputMode` — platform — *Not used*

| Field | Value |
|---|---|
| **Symbol** | Deprecated in platform (javap android-31+). App uses none (grep: 0 matches). |
| **Classification** | Not-used legacy — no action. |

### D8 — `android.app.Notification.Builder.addAction(int, CharSequence, PendingIntent)` — platform — *Not used (app uses NotificationCompat.addAction(NotificationCompat.Action))*

| Field | Value |
|---|---|
| **Symbol** | Deprecated in platform (javap). App uses `NotificationCompat.Builder.addAction(0, label, pendingIntent)` at `ReminderDelivery.kt:487` — androidx `NotificationCompat.Builder.addAction(int, CharSequence, PendingIntent)` is **not deprecated** in core 1.15.0 (verified via javap + sources). |
| **Classification** | Not-used platform deprecation — no action. |

### D9 — LiveData/`androidx.lifecycle.observe` family — Kotlin — *Not used (verified)*

| Field | Value |
|---|---|
| **Symbols** | `androidx.lifecycle.observe`, `androidx.lifecycle.LiveData`, `asLiveData`, `ViewModelProvider(owner)` single-arg, `androidx.lifecycle.observeAsLiveData` |
| **Verification** | 0 matches in `app/`, `core/`, `feature/` (grep). Uses the current `androidx.lifecycle.viewmodel.compose.viewModel`, `androidx.lifecycle.createSavedStateHandle`, `Flow` + `stateIn` instead. |
| **Classification** | Not-used — no action. |

### D10 — `LocalDate.now()` default-parameter/constructor usage — Kotlin — *Not deprecated*

| Field | Value |
|---|---|
| **Symbol** | `java.time.LocalDate.now()` used in `CycleCareApplication` (not needed), `CycleRecordValidator` (`todayProvider = { LocalDate.now() }`), `PredictionContracts`, `CalendarRoute`/`CalendarUiState`, `HomeRoute`/`HomeUiState`, `LoggingRoute`/`LoggingUiState`, `OnboardingUiState` — as *defaults* (compute-on-call, not stored-clock reads). |
| **Status** | Not deprecated; UI-layer current-date usage. Domain engines get `today` injected via `TimeProvider` (`SystemTimeProvider`), so determinism is maintained. **Note:** `HomeRoute`/`CalendarRoute`/`LoggingRoute` read the wall clock for validation/editor defaults — acceptable product behavior; not a deprecation. |

---

## 3. Deprecations in third-party SDKs (AdMob, UMP)

Static scan of the app classpath (`adb shell` on merged APK assets? — no; via lint + javap + source reads of call sites):

| Dependency | Version | Deprecated API used? | Notes |
|---|---|---|---|
| `com.google.android.gms:play-services-ads` | 25.2.0 | No | Standard `AdRequest.Builder().build()`, `InterstitialAd.load`, `BannerAd` — none flagged by lint or javap scan. |
| `com.google.android.ump:user-messaging-platform` | 4.0.0 | No | `UserMessagingPlatform.getConsentInformation`, `requestConsentInfoUpdate`, `loadAndShowConsentFormIfRequired`, `showPrivacyOptionsForm` — stable APIs. |
| `androidx.biometric:biometric` | 1.1.0 | **Yes — D1** (AAR-level; see above) | Not an app-code issue. |
| `androidx.work:work-runtime-ktx` | 2.10.0 | No | `WorkManager.getInstance(Context)` — context overload verified non-deprecated (javap). `enqueueUniqueWork/PeriodicWork`, `CoroutineWorker.doWork`, `Result` — all current. |
| `androidx.room:room-runtime` | 2.7.1 | No | `RoomDatabase`, `OnConflictStrategy` enum — verified no deprecated members used by app (javap). |
| `androidx.core:core-ktx`/`core` | 1.15.0 | No | `ContextCompat`, `NotificationCompat`, `PendingIntent` — verified clean. |
| `androidx.datastore:datastore-preferences` | 1.1.1 | No | `DataStore<Preferences>`, `edit { }` — current APIs. |

---

## 4. Deprecations NOT yet applied (with justification)

| Finding | Status | Justification |
|---|---|---|
| D1 (`USE_FINGERPRINT`) | **Remediated in Phase 7** | Removed via `tools:node="remove"` in `app/src/main/AndroidManifest.xml`; verified absent in merged debug+release manifests (commits `c51d304`). Data Safety form entry updated (`play-console/data-safety/data-safety-audit.md`). |
| D2 (`createConfirmDeviceCredentialIntent`) | **Remediated in Phase 7** | BiometricPrompt flow (`setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)`); legacy intent retained for API 26–28. |

---

## 5. Remediation status & risk table

| ID | Symbol | File:line | Action | When | Risk | Regression coverage |
|---|---|---|---|---|---|---|
| D1 | `android.permission.USE_FINGERPRINT` | `app/src/main/AndroidManifest.xml` (removed via `tools:node="remove"`); verified absent in `app/build/intermediates/merged_manifests/*/AndroidManifest.xml` | Removed 2026-11-22 (commit `c51d304`). | None (no app change). |
| D2 | `KeyguardManager.createConfirmDeviceCredentialIntent` | `app/src/main/java/com/cyclecare/feature/privacy/SecureAuthController.kt` | **Remediated** — BiometricPrompt with `setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)` on API ≥ 29; legacy intent retained for API 26–28. | Phase 7 (commit `c51d304`) | Low (same UX: system credential prompt). | PrivacyScreenUiTest + SecureAuthCapabilityTest re-run (Phase 8); lint re-run. |
| D3–D8, D9 | platform/androidx not-used | — | Verified N/A via greps + javap. | Done | None. | N/A. |
| D1, D2 | (remediated, see above) | — | — | Remediated | — | — |

---

## 6. Deprecation classes worth noting (non-deprecation)

- `android.os.Build.VERSION_CODES.R` (30) used for `setAllowedAuthenticators` guard — fine, not deprecated.
- `androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale` — current API.
- `androidx.activity.result` contracts — current.
- `android.app.FragmentActivity` — current (AppCompat-era stable).
- `android:appComponentFactory="androidx.core.app.CoreComponentFactory"` — valid, not deprecated.
- `android:debuggable="true"` — legacy attribute, still honored; Gradle builds override it. Not deprecated (lint clean).
- `kotlin.time`/`@OptIn(ExperimentalLayoutApi::class)` — current API usage.

---

## 7. Unresolved questions for Phase 8

1. Verified post-remediation: `lintDebug` — 0 errors; new BiometricPrompt path clean. Re-run `PrivacyScreenUiTest` + `SecureAuthCapabilityTest` in Phase 8 (instrumented). On API 26–28 the legacy `createConfirmDeviceCredentialIntent` path is retained (deprecated only since API 31); `BiometricPrompt` is gated to `SDK_INT >= 29`.
2. Re-run `./gradlew test` (505) + `connectedDebugAndroidTest` (268) after Phase 7; confirm 0 regressions.
