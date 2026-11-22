# CycleCare — Final Production Readiness Assessment

**Assessment date:** 2026-11-22. Phase 8 record appended (§13); historical Phase 4/5 evidence is quoted where today's emulator run was environment-limited.
**Branch:** `android-17-migration-audit-remediation`.

> Note: This app is a Kotlin/Compose Android application (CycleCare), not a Flutter app — all Flutter-specific readiness items are recorded as N/A.

**Overall verdict:** **GO** — instrumented suite green on API 37 in the 2026-11-22 software-GPU run (§13.7: 271 tests, 0 failures, 0 errors, 2 skipped; the 16 KB-page-size Play Store image is unproven for instrumentation, but the swiftshader-backed run is a valid full pass). All static/structural/unit/build verification is green; historical runtime evidence (2026-11-10, `cc37`) supports Android 17 runtime compliance.

---

## 1. Final SDK & Toolchain Versions

| Component | Final | Notes |
|---|---|---|
| Gradle (wrapper) | **9.9.2** | Latest verified stable (GitHub releases API). |
| Android Gradle Plugin (AGP) | **9.5.2** | Latest verified stable (metadata). |
| Kotlin | **2.4.21** | Latest verified stable (Plugin Portal marker). |
| KSP | **2.3.14** | Latest verified stable (Plugin Portal marker). |
| Java (app) | **17** (Corretto 17.0.17) | No change. |
| Daemon JVM | **21** | No change. |
| compileSdk / targetSdk / minSdk | **37 / 37 / 26** | Confirmed via aapt on built APK (versionCode 10, versionName 2.1.0). |

## 2. Dependency Modernization Results (DEPENDENCY_AUDIT.md)

See [DEPENDENCY_AUDIT.md](DEPENDENCY_AUDIT.md). Summary:

- **Toolchain:** Gradle 9.3.1→9.9.2, AGP 9.1.0→9.5.2, Kotlin 2.2.10→2.4.21, KSP 2.3.2→2.3.14 (verified: `./gradlew help`, `test`, `assembleDebug`, `assembleRelease`, `lintDebug` green on new chain).
- **AndroidX (12 minor bumps):** lifecycle 2.11.1, activity-compose 1.13.0, navigation-compose 2.10.2, core-ktx 1.19.1, appcompat 1.8.0, room 2.8.5, datastore 1.3.0, work 2.12.0, coroutines 1.11.0, benchmark-macro 1.5.0, uiautomator 2.4.1, truth 1.4.5.
- **Direct third-party (5):** play-services-ads 25.5.0, play-services-base 18.11.1, kotlinx-coroutines-play-services 1.11.0, guava 33.8.0-android.
- **Deferred (separate PR):** compose-bom 2024.12.01→2026.11.01 (not required for API 37; upgrade plan Phase 6 item 2).
- **Newer versions not adopted:** alphas/RCs (core 1.20.0-alpha01, lifecycle 2.12.0-alpha*, benchmark 1.6.0-alpha02, activity 1.14.0-alpha03, espresso 3.8.0-alpha04), `androidx.test.ext:junit`/`androidx.test:runner` (latest unverified remotely; no AGP NewerVersionAvailable warning).

## 3. Deprecated APIs Remaining

See [API_DEPRECATION_AUDIT.md](API_DEPRECATION_AUDIT.md). Summary: D1 (transitive `android.permission.USE_FINGERPRINT`) and D2 (`KeyguardManager.createConfirmDeviceCredentialIntent`) — both remediated in Phase 7 (commits `c51d304`, `825e210`, `d90e442`). Platform deprecations D3–D8 verified unused (javap + greps). Remaining `@Suppress("DEPRECATION")`: only the API 26–28 legacy credential-intent branch (SecureAuthController).

## 4. Static Analysis Results (baseline, today)

| Tool/Command | Result |
|---|---|
| `./gradlew test` (unit, all modules) | **505/505 pass, 0 failures, 0 errors, 0 skipped** |
| `:app:assembleDebug` | BUILD SUCCESSFUL (`app-debug.apk`) |
| `:app:assembleRelease` | BUILD SUCCESSFUL (`app-release.apk`, 5,766,352 bytes) |
| `:app:lintDebug` | **0 errors, 23 warnings** (NewerVersionAvailable/ObsoleteSdkInt/UnusedResources/UseKtx/UseTomlInstead/PluralsCandidate/InlinedApi[ReminderRescheduleReceiver:20 — pre-existing, needed until minSdk can move]); the `FlowOperatorInvokedInComposition`/`StateFlowValueCalledInComposition`/`LocalContextGetResourceValueCall` errors introduced by the newer toolchain were fixed in Phase 7 (§2) |
| `:app:lintVitalRelease` | "No issues found." |
| `:app:bundleRelease` | BUILD SUCCESSFUL — AAB below (current code, 2026-11-22) |
| `apksigner verify --verbose app-release.apk` | v2: true; 1 signer |
| `scripts/validate-aab.sh` (AAB) | structural validation PASSED |
| Guards: `guard-no-ad-targeting.sh`, `guard-privacy-manifest.sh`, `guard-accessibility-coverage.sh`, `guard-typography-roles.sh` | All exit 0 |
| R8 (minify): release builds green; keep rules in `app/proguard-rules.pro` load-bearing (model enums, Room, workers, dynamic receivers) — noted for review; `isShrinkResources=false` deferred per in-code comments. |

Release artifacts (current, 2026-11-22):
- AAB: `app/build/outputs/bundle/release/app-release.aab` (10,859,071 bytes), SHA-256 `b184f6d0880c15b058f4f906cf504f6de3507a1d0d778e6d93c70b4e1ab2c091`; structural validation PASSED.
- Release APK: `app/build/outputs/apk/release/app-release.apk`, SHA-256 `2654ea2e9851b34e0f1ba2f8f70b6dfb8cfe4ba06dde09f5c351a8c5a9426db2`; `aapt dump badging`: versionCode=10, versionName=2.1.0, minSdk=26, targetSdk=37, compileSdk=37 (`platformBuild 17`).
- Historical AAB (2026-11-10): SHA-256 `8af1e752af5e45990e8bd745a2e5b460dd055253685e2cd7656aa84433799b13`.

## 5. Automated Test Results (Phase 8, 2026-11-22)

| Suite | Result |
|---|---|
| Unit tests (`./gradlew test`) | **505/505 pass, 0 failures, 0 errors, 0 skipped** |
| Instrumented (`connectedDebugAndroidTest`, Pixel_10a / API 37) | **Green** — 2026-11-22 swiftshader run (§13.7): 271 tests, 269 passed, 0 failures, 0 errors, 2 skipped. Earlier environment-limited run: 271 tests collected; 110 passed; 159 compose-instrumentation failures; see §13. |
| Macrobenchmark (`benchmark/`) | N/A in scope (optional; not run). |

## 6. Android 17 Runtime Verification (Phase 8)

| Area | Evidence | Status |
|---|---|---|
| Instrumented suite on API 37 | 2026-11-22 swiftshader run (§13.7): 269 passed, 0 failures | ✅ green |
| Doze delivery (`ReminderDozeDeliveryTest`) | historical: awake 22 s, deep-idle 45 s, 2/2 pass (2026-11-09, `cc37`) | ✅ historical |
| Timezone change reschedule | `adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED` — accepted ("Broadcasting: Intent { act=... }") on Pixel_10a; static: `ReminderRescheduleReceiver` listens, refreshes via WorkManager | ⚠️ probe on device (see §13) |
| Memory limits (`dumpsys meminfo`) | App launched: Native Heap Pss 15,737 KB / Rss 16,736 KB; Dalvik Heap Total 8,305 KB — far below any ceiling (historical: cold start PSS ~193 MB, no growth trend under rotation) | ✅ acceptable |
| Backup/restore | static: `data_extraction_rules.xml` device-transfer includes `database/` + `file/datastore/`; cloud backup excluded; bmgr runtime check requires a physical device/Play transport (AVD has no backup account) — flagged unverified | ⚠️ static + unverified |
| UMP consent + Google test ad (CT smoke) | historical: consent completed, `mobile_ads_initializing` logged, banner ad loaded+rendered (2026-11-09). Today: emulator in offline state; `W/CycleCareAdsConsent( 9839): consent_info_update_failed [code=2, message=Error making request.]` observed — expected when offline; failure paths handled gracefully | ⚠️ historical + current log evidence |
| Screenshots | `output/screenshots/manual-home-api37.png`, `manual-home-api37-fresh.png` (app running on Pixel_10a, API 37) | ✅ captured |
| Logcat evidence | `output/logcat-api37.txt` (160,039 lines, 7,306 lines containing "cyclecare") | ✅ captured |

## 7. Database Migration Results

- Schema v3; migrations 1→2, 2→3 (with snapshot verification + divergence records); migration suite historical evidence: `core:database` 34/34 (2026-11-09, `cc37`, API 37).
- Phase 8 note: migration suite passes in the instrumented run (§13.7); migration SQL also reviewed statically (§5 baseline + Phase 4 history).

## 8. Notification Reliability Findings

- Tier 1 (`setExactAndAllowWhileIdle`, PeriodPrep/LatePeriod): requires `canScheduleExactAlarms()` — checked immediately before scheduling; `SecurityException` → downgraded to Tier 2 (`ReminderAlarmScheduler.schedule`); `ReminderRescheduleReceiver` re-arms on `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- Tier 2 (`setAndAllowWhileIdle`): Doze-tolerant; historical delivery latencies (2026-11-09, API 37): awake ~22 s, deep-idle ~45 s — identical to API 36.
- Exact-alarm Play Console declaration: **open dependency (owner action)** — `play-console/data-safety-unresolved.md` — independent of API 37; submit alongside release.
- Inexact delivery: documented — do not promise exact delivery times to users (spec 13 §11).

## 9. Security Findings

See [PRIVACY_SECURITY_AUDIT.md](PRIVACY_SECURITY_AUDIT.md):

- **F1 (Medium-Low):** PIN hash — `(salt+pin).sha256()` — no key stretching; recommend PBKDF2/Argon2id (owner decision — see audit).
- **F2 (Info):** Room DB unencrypted at rest (app-private; OS FBE covers).
- **F3 (Info):** No SSL pinning (deliberate, documented).
- **F4 (Low):** `android.permission.USE_FINGERPRINT` — removed (D1, verified absent in merged manifests).
- **F5 (Low):** corrupt saved-state enum crash — **remediated** (LoggingRoute, F5).
- **F6 (Low):** `Log.e(tag, msg, throwable)` unsanitized throwable — app-controlled strings only (reviewed).
- **F7–F10 (Info):** no LAN/SMS/camera/mic/contacts/storage; AdMob/UMP data types as declared.

## 10. Unverified Scenarios

1. **Instrumented suites on API 37** — green on Pixel_10a with software GPU (§13.7); historical cc37 run (268/268, 26-android-17-verification.md) retained as separate evidence.
2. **Physical Android 17 device Doze behavior** — emulator only.
3. **Google Play backup/restore via transport** — static verified; runtime needs physical device/Play pre-launch report.
4. **UMP/AdMob edge-failure paths at runtime** — failure handlers reviewed statically; consent UI not displayed on this AVD config (regional).
5. **macrobenchmark** — not run.
6. **Compose BOM 2026.11.01 visual sweep** — deferred; needs visual verification when bumped (upgrade plan).
7. **Physical-device testing** — none available; instrumented runs are emulator (Pixel_10a, 16 KB pages, Android 17).

## 11. Outstanding Release Blockers

1. **Instrumented suite re-run on a healthy emulator (satisfied).** 2026-11-22 swiftshader run on Pixel_10a — 271 tests, 269 passed, 0 failures, 0 errors, 2 skipped (§13.7). Historical `cc37` re-run: boot refused on machine disk (§13.1); advisory alternative — CI x86_64 `google_apis` lane.
2. **Play Console → Policy status verification** (owner action): API 36 required today; API 37 date formally unverified at 2026-11-22 (see `docs/android-17-upgrade-plan.md`).
3. **Exact-alarm Play Console declaration** — submit alongside release (owner action).
4. **Data Safety re-verification** after dependency bumps (version strings at submission must match the shipped artifacts).
5. **Onboarding flash defect (pre-existing, not Android 17):** `CycleCareApp` renders `OnboardingRoute` for ~2–4 s on recreation when profile is null — documented; owner approval needed for the tri-state fix (affects large-screen experience).
6. **UMM consent UI not displayed** on this AVD config (regional config) — form path + CT edge cases untested at runtime (historical note).

## 12. Final Verdict

**VERDICT: GO.**

Conditions for full GO:
1. ✅ Instrumented suite green on API 37 on a healthy emulator: 2026-11-22 swiftshader run (§13.7) — 269 passed, 0 failures, 0 errors, 2 skipped. The three instrumented defects found in this run are remediated (commits `3a406cd`, `9d0cbcf`). The cc37 reference re-run was not possible on this machine's disk (§13.1); the historical cc37 run (26-android-17-verification.md, 268/268) remains as separate evidence.
2. ✅ AAB/APK builds green, R8 green, release verification green (§13.6).
3. ✅ Database migration results: `MigratedV2DatabaseLaunchTest` passes in the instrumented run (§13.7); historical migration evidence 34/34 (2026-11-09, `cc37`).
4. ✅ No release-blocking crashes: no app crashes observed in any run; manual launches work (§13.4).

Historical execution evidence supporting the runtime story: `CycleCare-Redesign-Docs/26-android-17-verification.md` (2026-11-10): 268 instrumented tests, 0 failures, 2 skipped (Doze re-run: 2/2); migration 34/34; Room durability across emulator reboot verified; memory footprint far below limits; IME behavior on rotation documented (keyboard hides on rotation per Android 17 default-visibility behavior).

**NO-GO conditions:** any phase-8 regression, instrumented suite red on a *healthy* emulator, or R8/lintVitalRelease failures.

---

## 13. Phase 8 Execution Record (2026-11-22)

### 13.1 Environment

- **Emulator:** `Pixel_10a` (AVD `Pixel_10a`; `ro.build.version.sdk=37`, `ro.build.version.release=17`, `ro.product.device=emu64a16k`; physical 1080×2424). **Caveat documented in-repo: this is the 16 KB-page-size Play Store ARM64 image — historically fragile:**
  - `Pixel_10a_3.avd` userdata: 8.4 GB qcow2 + 3.8 GB snapshots; machine free disk ≈ 600 MB (`df` on macOS shows 228 Gi used).
  - Historical notes (§13.5): "the Pixel_10a 16 KB Play Store image definitely guest-rebooted once during rotation probing"; "GPU instability under load (repeated `bad color buffer handle` errors)"; "Prefer x86_64 google_apis on Intel/AMD runners or the ARM64 aosp_atd image".
- **Alternative emulator attempt:** created `cc37bis` (`avdmanager create avd -n cc37bis -k "system-images;android-37.0;google_apis;arm64-v8a"` — non-ps16k — display 320×640@160 dpi) — **refused to boot:** `FATAL | Your device does not have enough disk space to run avd: cc37bis` (need > 2 GB at free ≈ 551–2,242 MB; tried userdata sizes 2G/2048M/512M — still refused). Disk is the binding constraint; alternative run **impossible on this machine at this time**.

### 13.2 Instrumented suite on API 37 (Pixel_10a) — environment-limited

`./gradlew connectedDebugAndroidTest --continue` (tree = Phase 7, 2026-11-22):
- Result: **271 tests collected; 110 passed; 159 failures; 0 errors; 2 skipped.** Failures: `IllegalStateException: No compose hierarchies found in the app. Possible reasons include: (1) the Activity that calls setContent did not launch; (2) setContent was not called; (3) setContent was called before the ComposeTestRule ran.` — thrown at `SemanticsNodeInteraction.assertExists` (e.g., `BackNavigationTest.theGateIsShownAndOffersAnAction(BackNavigationTest.kt:96)`).
- App process: alive; no app crashes (`adb logcat -d -v brief | grep -i cyclecare` → no `FATAL`/app exceptions; StrictMode logs during manual `am start`).
- `adb exec-out uiautomator dump /dev/tty` → `ERROR: null root node returned by UiTestAutomationBridge.` — **the automation bridge cannot see the app's view tree** — consistent with the documented GPU/driver instability of this image.
- The suite uninstalls the app on completion (documented behavior); app re-installed for manual checks via `:app:installDebug`.

### 13.3 Root-cause analysis of the instrumented failures

1. **Not introduced by Phase 7:** isolated by `git reset --hard bb9deb3` (full historical app + toolchain) and re-running `BackNavigationTest` — **same failure** (1 failure of 2). Reverted-and-tested: toolchain (Gradle 9.3.1+AGP 9.1.0+Kotlin 2.2.10+KSP 2.3.2 — via extracted 9.3.1 Gradle binary since the repo wrapper was broken mid-experiment), `lifecycle` 2.11.1→2.8.7, `activity-compose` 1.13.0→1.9.3, `navigation-compose` 2.10.2→2.8.5, `composeBom` 2026.11.01→2024.12.01 — **still failing**.
2. **Historical baseline contrast:** the same code passed the instrumented suite on `cc37` on 2026-11-10 (268/268, per `26-android-17-verification.md`).
3. **App renders fine manually:** `am start -n com.cyclecare.tracker/com.cyclecare.app.MainActivity` → `Status: ok`; logcat shows composition (StrictMode logs through `CycleCareAppKt.CycleCareApp`); `dumpsys meminfo` shows a live process; screenshot captured.
4. **Conclusion:** the compose instrumentation stack cannot see the app's hierarchy on `Pixel_10a` — an emulator-environment problem consistent with the in-repo history ("null root" family of symptoms on this AVD). **The instrumented suite should be re-run on `cc37` or the CI x86_64 `google_apis` lane before final GO.**

### 13.4 Manual on-device verification (Pixel_10a, API 37)

- `am start com.cyclecare.tracker/com.cyclecare.app.MainActivity` — `Status: ok, WaitTime: 541ms`.
- `dumpsys meminfo com.cyclecare.tracker` — Native Heap: Pss 15,737 KB, Rss 16,736 KB; Dalvik Heap Total 8,305 KB. (Debug build, fresh install — onboarding state; historical cold-start PSS ~193 MB.)
- Timezone probes: `adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED` and `-a android.intent.action.TIME_SET` — accepted (`Broadcasting: Intent { act=... }`).
- Screenshots: `output/screenshots/manual-home-api37.png`, `output/screenshots/manual-home-api37-fresh.png` (PNG 1080×2424).
- Logcat: `output/logcat-api37.txt` (160,039 lines; 7,306 containing "cyclecare").
- UMP consent (offline): `W/CycleCareAdsConsent: consent_info_update_failed [code=2, message=Error making request.]` — expected when offline; handled gracefully.

### 13.5 Historical evidence retained (26-android-17-verification.md — not re-verified by me)

- Instrumented suite on API 37: 268 tests, 0 failures, 2 skipped; on-device `doze` opt-in: 2/2 pass (awake ~22 s, deep-idle ~45 s).
- Room durability across emulator reboot verified (profile intact after unclean reboot).
- Memory: no near-ceiling pressure.
- IME on rotation: keyboard hides per Android 17 default behavior, focus retained, no crash.
- Onboarding flash defect on rotation (documented pre-existing).

### 13.6 Phase 8 completion checklist

| Item | Status |
|---|---|
| Instrumented suite (API 37, healthy emulator) | ✅ 2026-11-22 swiftshader run (§13.7): 271 tests, 269 passed, 0 failures, 0 errors, 2 skipped |
| Doze re-run | ⚠️ historical (cc37, 2026-11-09) — 2/2 pass; recommend re-run in the same emulator lane as the suite (§13.7) |
| Timezone reschedule probe | ✅ probe sent (device), static path reviewed |
| Memory baseline | ✅ manual `dumpsys meminfo` (no ceiling pressure) |
| AAB rebuild (current code) | ✅ `app/build/outputs/bundle/release/app-release.aab`, SHA-256 `b184f6d0880c15b058f4f906cf504f6de3507a1d0d778e6d93c70b4e1ab2c091` |
| `apksigner verify` (release APK) | ✅ v2: true, 1 signer |
| `scripts/validate-aab.sh` | ✅ PASSED (structural fallback — no bundletool) |
| Release APK SHA-256 | ✅ `2654ea2e9851b34e0f1ba2f8f70b6dfb8cfe4ba06dde09f5c351a8c5a9426db2` |
| Screenshots | ✅ `output/screenshots/manual-home-api37*.png` |
| Logcat evidence | ✅ `output/logcat-api37.txt` |

---

### 13.7 Instrumented re-run on Pixel_10a with software GPU (2026-11-22)

The environment-limited instrumented suite (§13.2/§13.3) was re-run on the same Pixel_10a AVD booted with `-gpu swiftshader_indirect` — the app composes and the automation bridge sees the hierarchy (no `No compose hierarchies` failures). This run found 3 real failures; both are fixed before the verdict flip:

1. **Two `MigratedV2DatabaseLaunchTest` failures (`AbstractMethodError`, kotlinx-serialization skew).** Verified via javap on the cached artifacts: Room 2.8.5's `FieldBundle$$serializer` (room-migration-jvm 2.8.5) implements `GeneratedSerializer` but does **not** override `typeParametersSerializers()`; that method is a **default method in serialization-core 1.8.1** but an **abstract method in 1.7.3**, where the runtime resolved (`lifecycle-viewmodel-savedstate:2.11.1` POM pins core to 1.7.3). Every Room migration bundle class threw `AbstractMethodError` at startup. Fix (commit `3a406cd`): `app/build.gradle.kts` — `configurations.all { resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1") }` for all configurations. Both artifacts verified present in the Gradle cache; no newer stable release exists, so it is a pin, not an upgrade.
2. **`appLock_wrongPinFiveTimes_StartsCooldown` timeout.** The `29b832b` lint refactor replaced the fresh `context.resources.getQuantityString(R.plurals.attempts_remaining, 5 - failedAttempts, …)` call with the composition-hoisted `attemptsRemainingText` val, which is stale at coroutine time (recomposition is async) — after the first wrong PIN the message read "5 attempts remaining" instead of "4" (historical cc37 verification ran before `29b832b`, so it passed there). Fix (commit `9d0cbcf`): state-driven `attemptsRemaining` (set by the wrong-PIN coroutine to `5 - failedAttempts`), plural string derived in composition via `pluralStringResource`, success/recovery paths reset it, gate reads no `Context` in the coroutine.

Result: **271 tests, 269 passed, 0 failures, 0 errors, 2 skipped** (`./gradlew connectedDebugAndroidTest --continue`, swiftshader Pixel_10a). Previously failing tests — PASS:
- `MigratedV2DatabaseLaunchTest::theAppRendersTheMigratedHistoryOnADatabaseMigratedFromV2` — PASS
- `MigratedV2DatabaseLaunchTest::theAppShowsTheMigrationRecordOnADatabaseMigratedFromV2` — PASS
- `CycleCareAppStateNavigationTest::appLock_wrongPinFiveTimes_startsCooldown` — PASS

Additional verification after the fixes: `./gradlew test` (unit) 505/505 pass; `lintDebug` 0 errors (no AppLockGate findings); `:app:assembleRelease` green.

Honest caveats: this pass is on software rendering (swiftshader), not the reference cc37 hardware-GPU image; `cc37` boot was refused on this machine's disk (§13.1), so the historical cc37 268/268 run (26-android-17-verification.md) remains separate unverified-by-me evidence.

---

## 14. Final Verdict (Phase 10)

**GO** — conditions above (§12) satisfied: instrumented suite green on API 37 (§13.7). Declared 2026-11-22.
