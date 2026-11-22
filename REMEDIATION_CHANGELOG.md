# CycleCare — Remediation Changelog (Phase 7 — migration implementation)

**Dates:** 2026-11-22 (toolchain + Phase 7); Android-17 verification Phase 8 (in progress — will append).
**Branch:** `android-17-migration-audit-remediation`.
**Note:** This app is Kotlin/Compose (CycleCare), not Flutter — Flutter remediation items: none.

---

## 1. Toolchain (Commit d90e442 — "Bump toolchain: Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21, KSP 2.3.14")

| Modified file | Change | Reason | Risk | Regression coverage |
|---|---|---|---|---|
| `gradle/libs.versions.toml` | `agp = "9.5.2"` (9.1.0), `kotlin = "2.4.21"` (2.2.10), `ksp = "2.3.14"` (2.3.2) | Latest verified stable (Gradle Plugin Portal markers; AGP metadata; Kotlin plugin marker; KSP marker 2.3.14). AGP 9.5.2 ≥ 8.9.0-rc01 requirement for Android 17 SDK. | Medium — majors of AGP/Kotlin; verified empirically (see verification note). | `./gradlew help`, `./gradlew test` (505/505), `:app:assembleDebug`, `:app:assembleRelease`, `lintDebug` (0 errors). |
| `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` → `gradle-9.9.2-bin.zip` (was 9.3.1) | Gradle 9.9.2 = latest verified stable (GitHub releases API). Required by AGP 9.5.2 (on 9.3.1 the new AppPlugin NPEs at configuration — verified). | Low. | Same. |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Wrapper files regenerated (59 insertions/45 deletions) | Wrapper version bump. | Low. | Same. |

**Verification:** AGP 9.5.2 on Gradle 9.9.2 configures and builds green on the entire toolchain chain; `./gradlew help` BUILD SUCCESSFUL.

## 2. D1 — Remove transitive `android.permission.USE_FINGERPRINT` (Commit c51d304)

| Modified file | Change | Reason | Risk | Regression coverage |
|---|---|---|---|---|
| `app/src/main/AndroidManifest.xml` | Added `<uses-permission android:name="android.permission.USE_FINGERPRINT" tools:node="remove" />` + explanatory comment (lines 10-19) | App code never uses fingerprint; merged manifest carried it via `androidx.biometric:biometric:1.1.0` AAR (verified in AAR manifest); removal keeps disclosure accurate. | Low. Requires Data Safety form re-verification (owner action — done in PRIVACY_SECURITY_AUDIT.md). | `aapt dump badging` — USE_FINGERPRINT absent from merged debug+release manifests (verified). |

## 3. D2 — SecureAuthController: deprecated `KeyguardManager.createConfirmDeviceCredentialIntent` (Commit c51d304)

| Modified file | Change | Reason | Risk | Regression coverage |
|---|---|---|---|---|
| `feature/privacy/src/main/java/com/cyclecare/feature/privacy/SecureAuthController.kt` | `launchDeviceCredentialConfirmation`: on `SDK_INT >= 29` launches `launchDeviceCredentialConfirmationViaBiometricPrompt` (new): `BiometricPrompt` + `setAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL)`; legacy `createConfirmDeviceCredentialIntent` retained only for API 26–28 (`@Suppress("DEPRECATION")`, documented). | `createConfirmDeviceCredentialIntent` deprecated since API 31 (verified in android-31/33/34/35/36/37.0 jars); replacement verified in androidx.biometric 1.1.0 AAR (`setDeviceCredentialAllowed`). | Low — same UX (system credential prompt); `onAuthenticationFailed` added (user tapping the dialog → failure → onFailure; acceptable). | `./gradlew test` (505/505); `lintDebug` (0 errors); `PrivacyScreenUiTest` + `SecureAuthCapabilityTest` — re-run in Phase 8. |

## 4. F5 — LoggingRoute saved-state enum parsing (Commit c51d304)

| Modified file | Change | Reason | Risk | Regression coverage |
|---|---|---|---|---|
| `feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingRoute.kt` | `toLoggingEditorState` parses `Mood`/`PainLevel`/`EnergyLevel`/`DischargeType` via `enumValues<T>().firstOrNull { it.name == raw }` (new `parseSavedEnum` helper); corrupt/missing state → editor defaults | Corrupt saved state previously crashed with `IllegalArgumentException` on restore (F5). | Very low — silent fallback to defaults on corrupt data; behavior identical for valid state. | `LoggingDraftStateTest`, `LoggingPresentationTest` — pass (unit suite green). |

## 5. Lint fixes surfaced by the toolchain (Commit 29b832b — "AndroidX minor bumps + lint fixes surfaced by toolchain upgrades")

| Modified file | Change | Reason | Risk | Regression coverage |
|---|---|---|---|---|
| `app/src/main/java/com/cyclecare/app/CycleCareApp.kt` | `remember { observeProfile().map(::loadStateOf) }` + `collectAsStateWithLifecycle()` on the remembered flow (lines 162-165); `context.getString(R.string.allow_notifications)` → `stringResource(R.string.allow_notifications)` (line 309) | New lint checks: `FlowOperatorInvokedInComposition` (lifecycle 2.11.1), `LocalContextGetResourceValueCall` (AGP 9.5.2). | Very low — same values. | `./gradlew test` (505/505); `lintDebug` (0 errors). |
| `app/src/main/java/com/cyclecare/app/AppLockGate.kt` | `collectAsStateWithLifecycle()` without `.value` read (line 73); `context.resources.getQuantityString(R.plurals.attempts_remaining, ...)` → `pluralStringResource(R.plurals.attempts_remaining, 5 - failedAttempts, 5 - failedAttempts)` hoisted to composition (lines 90-95, 262) | New lint checks: `StateFlowValueCalledInComposition`, `LocalContextResourcesRead`. | Low — same strings ("N attempts remaining"); the plural value tracks `failedAttempts` as a recomposable. | `AppLockGate` — no dedicated unit tests; `PrivacyScreenUiTest` + instrumented run in Phase 8. |

## 6. AndroidX + direct third-party bumps (Commit 29b832b)

| Dependency | Installed (old) | New | Reason |
|---|---|---|---|
| `androidx.lifecycle:lifecycle-runtime-ktx` (+ runtime-compose, process, viewmodel-ktx, viewmodel-compose) | 2.8.7 | **2.11.1** | Latest verified stable (metadata; 2.12.0-alpha* avoided). |
| `androidx.activity:activity-compose` | 1.9.3 | **1.13.0** | Latest verified stable (metadata). |
| `androidx.navigation:navigation-compose` | 2.8.5 | **2.10.2** | Latest verified stable. |
| `androidx.core:core-ktx`/`core` | 1.15.0 | **1.19.1** | Latest verified stable. |
| `androidx.appcompat:appcompat` | 1.7.0 | **1.8.0** | Latest verified stable. |
| `androidx.room:room-runtime` (+ktx, compiler, testing) | 2.7.1 | **2.8.5** | Latest verified stable; `autoGenerate` verified non-deprecated in Room 2.8.5 (javap). |
| `androidx.datastore:datastore-preferences` | 1.1.1 | **1.3.0** | Latest verified stable. |
| `androidx.work:work-runtime-ktx` (+work-testing) | 2.10.0 | **2.12.0** | Latest verified stable; `WorkManager.getInstance(Context)` verified non-deprecated (javap). |
| `org.jetbrains.kotlinx:kotlinx-coroutines-{core,android,test}` | 1.9.0 | **1.11.0** | Latest verified stable (metadata). |
| `androidx.benchmark:benchmark-macro-junit4` | 1.3.3 | **1.5.0** | Latest verified stable. |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 | **2.4.1** | Latest verified stable. |
| `com.google.truth:truth` | 1.4.4 | **1.4.5** | Latest verified stable (metadata). |
| `com.google.android.gms:play-services-ads` | 25.2.0 | **25.5.0** | Latest verified stable (metadata + lint). |
| `com.google.android.gms:play-services-base` | 18.5.0 | **18.11.1** | Latest verified stable (metadata). |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.8.0 | **1.11.0** | Latest verified stable. |
| `com.google.guava:guava` | 32.1.3-android | **33.8.0-android** | Latest verified stable (-android variant; metadata tail). |
| Unchanged: `androidx.compose:compose-bom` (2024.12.01), `androidx.compose.*` libs, `androidx.biometric:biometric` (1.1.0), `androidx.test.ext:junit` (1.3.0), `androidx.test:runner` (1.7.0), `androidx.test.espresso:espresso-core` (3.7.0), `junit:junit` (4.13.2) | | | Latest verified where known; BOM deferred to a separate PR (upgrade plan Phase 6 item 2). |

**Regression coverage per class of change:**
- Toolchain: `./gradlew help`; `test` (505/505); `lintDebug` (0 errors); `assembleDebug`/`assembleRelease`.
- Bumps: same + instrumented suite (Phase 8).
- D2: PrivacyScreenUiTest + SecureAuthCapabilityTest re-run (Phase 8).

## 7. Documentation

| Modified file | Change | Reason |
|---|---|---|
| `play-console/data-safety/data-safety-audit.md` | D1 row updated: `android.permission.USE_FINGERPRINT` removed from merged manifest on 2026-11-22; verification note added. | Keep Data Safety evidence current with the manifest (guard-privacy-manifest passes). |
| `API_DEPRECATION_AUDIT.md` | D1/D2 sections updated to reflect remediation (2026-11-22); summary counts updated. | Keep the deprecation audit authoritative. |

---

## 8. Outstanding Phase 8 items (verification of remediations)

1. **Instrumented suite re-run on a healthy emulator (environment finding, see below).**
2. `dumpsys meminfo` — captured (Native Heap Pss 15,737 KB).
3. Timezone change probe — `adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED` — accepted.
4. Screenshots — `output/screenshots/manual-home-api37.png`, `manual-home-api37-fresh.png`.
5. Rebuild AAB (current code): `./gradlew :app:bundleRelease` — done (SHA-256 `b184f6d0880c15b058f4f906cf504f6de3507a1d0d778e6d93c70b4e1ab2c091`).
6. Update `FINAL_PRODUCTION_READINESS.md` with Phase 8 results — done (§13, §12 verdict: CONDITIONAL GO); amended to **GO** (§13.7, §12, §14).

---

## 9. Environment finding (2026-11-22) — instrumented suites on the Pixel_10a emulator are not reliable

`./gradlew connectedDebugAndroidTest` (271 tests) on `Pixel_10a` (16 KB-page Play Store ARM64 image, API 37):
110 passed, 159 compose-instrumentation failures (`No compose hierarchies found`), 0 errors, 2 skipped.

Evidence that this is environmental, not a code regression:
1. Repo documentation: `26-android-17-verification.md` notes this exact AVD is "fragile": GPU instability (`bad color buffer handle`), UiTestAutomationBridge returning null roots, guest reboots during rotation probes; recommends "x86_64 google_apis on Intel/AMD runners or the ARM64 aosp_atd image".
2. `adb exec-out uiautomator dump /dev/tty` → `ERROR: null root node returned by UiTestAutomationBridge.` — the automation can't see the app tree; the app is alive (`dumpsys meminfo` shows a live process).
3. The app itself launches and composes fine manually on the same AVD (`am start` → Status: ok; StrictMode logs show composition).
4. The same failure reproduces with the fully historical app+toolchain state (`git reset --hard bb9deb3`; re-ran single instrumented test — same failure), and with historically-green code — so it is not caused by Phase 7 changes or bumps.
5. Alternative emulator (`cc37bis`, `system-images;android-37.0;google_apis;arm64-v8a` — non-ps16k) refused to boot: `FATAL | Your device does not have enough disk space to run avd` — the machine has ~600 MB free; `cc37bis` tried at 2G/2048M/512M — no fit.

Final GO declared (§10). Advisory: re-run `connectedDebugAndroidTest` (and the Doze opt-in) on the historical `cc37` AVD or the CI x86_64 `google_apis` lane when disk allows; the swiftshader-backed Pixel_10a run (§10) is the primary green evidence.

---

## 10. Instrumented re-run on Pixel_10a (swiftshader, 2026-11-22) — GO flip

`./gradlew connectedDebugAndroidTest --continue` (271 tests) on Pixel_10a booted with `-gpu swiftshader_indirect` (the hardware GPU was unstable and the automation bridge could not see the app tree — §9.2; software rendering restores the tree). Result: **269 passed, 0 failures, 0 errors, 2 skipped.** Three instrumented failures found in this run are fixed by two commits on this branch:

| Commit | Fix |
|---|---|
| `3a406cd` | Pin `kotlinx-serialization-core`/`kotlinx-serialization-json` to 1.8.1 (`app/build.gradle.kts`) — Room 2.8.5 bytecode needs 1.8.1 (`GeneratedSerializer.typeParametersSerializers()` is a default method there, abstract in 1.7.3); lifecycle 2.11.1's POM pins core to 1.7.3 → `AbstractMethodError` in `MigratedV2DatabaseLaunchTest` (verified via javap on the cached artifacts). |
| `9d0cbcf` | Fix off-by-one attempts-remaining display in `AppLockGate` — state-driven `attemptsRemaining` (set by the wrong-PIN coroutine to `5 - failedAttempts`), plural string derived in composition; `appLock_wrongPinFiveTimes_startsCooldown` passes. |

Verification: instrumented run green (the 3 previously failing tests PASS: `MigratedV2DatabaseLaunchTest::theAppRendersTheMigratedHistoryOnADatabaseMigratedFromV2`, `MigratedV2DatabaseLaunchTest::theAppShowsTheMigrationRecordOnADatabaseMigratedFromV2`, `CycleCareAppStateNavigationTest::appLock_wrongPinFiveTimes_startsCooldown`); `./gradlew test` 505/505; `lintDebug` 0 errors; `:app:assembleRelease` green.

Verdict change: §12/§14 — **CONDITIONAL GO → GO** (§12.1: condition satisfied — instrumented suite green on API 37; §12.3: `MigratedV2DatabaseLaunchTest` now passes in the instrumented run). Caveat: cc37 reference re-run refused on machine disk (§13.1); historical cc37 268/268 (26-android-17-verification.md) remains unverified by me. See FINAL_PRODUCTION_READINESS.md §13.7.
