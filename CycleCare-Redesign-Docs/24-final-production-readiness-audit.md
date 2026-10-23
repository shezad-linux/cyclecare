# CycleCare — Final Production-Readiness Audit Report

**Audit Decision:** **NO-GO**  
**Audit Date:** 2026-10-23  
**Auditor:** Independent Release Engineer, QA Lead & Production-Readiness Auditor  
**Repository Baseline:** Commit `ba0d859453d397ee20a10396af001c0c1c41bf21` on branch `main` (69 commits ahead of `origin/main`)  
**Tracked Working Tree:** Preserved (1 pre-existing tracked modification in `app/src/androidTest/java/com/cyclecare/app/CycleCareAppStateNavigationTest.kt`; plus documentation-only edits to `22-phase-completion-plan.md`, `23-verification-runbook.md`, and this file)  

---

## 1. Executive Summary & Decision

### Decision: **NO-GO**

CycleCare exhibits exceptional domain engineering, outstanding unit test coverage (505/505 passing), verified mathematical prediction gates (13/13 passing), robust database migration integrity (34/34 passing), and clean local release compilation with R8 shrinking and zero compiler/lint warnings. Furthermore, the instrumented test suite on the reference `cc36` AVD (Android 16, API 36) achieves a clean pass across all 9 modules: **268 total tests, 266 passed, 2 skipped (opt-in Doze), 0 failed, 0 errors, 0 unexecuted**. 

Additionally, the historical claim in `23-verification-runbook.md` that the screenshot sweep aborts at 58/97 images has been thoroughly investigated and reconciled: the crash was an earlier semantics-tree lookup issue on `home_dashboard` after sheet dismissal (commit `26e45c1`), which was already stabilized in commit `09a1156` by searching the unmerged tree. With timing synchronization corrected, **all 97 screenshots complete successfully** in portrait (1.0x font scale), portrait (2.0x font scale), and landscape mode.

However, visual asset inspection uncovered a **critical layout verification limitation**: in the fixed 320x640 emulator window, `Modifier.width(...)` is constrained by the parent window. All portrait captures—including the 600 dp and 840 dp configurations—are clamped to 320x640 px and are bit-for-bit identical to the 320 dp captures. Consequently, wide-screen adaptive layouts (Medium and Expanded WindowSizeClass, navigation rails, expanded sheets) **remain unverified**.

Production release is strictly **NO-GO** due to the following mandatory blockers:

1. **GitHub Actions Release Pipeline Unproven & Unconfigured (Blocker B1):** The protected `release` environment does not exist on GitHub (HTTP 404). None of the four required base64 signing secrets are configured. The release workflow (`.github/workflows/release.yml`) has never executed in CI. Local `main` is 69 commits ahead of `origin/main`, and no release tag exists.
2. **Broken CI Dependency Vulnerability Gate (Blocker B2):** The required repository variable `CYCLECARE_ADVISORY_FEED` is not set on GitHub. As designed, `./scripts/guard-dependency-vulnerabilities.sh` exits with code 2 because an unconfigured advisory feed cannot be treated as a clean security scan.
3. **Physical-Device Doze Requirement Open (Blocker B3 / P7-05):** Spec `13` §17 requires physical-device Doze verification recorded under real battery-saving conditions. Only emulator results under simulated `deviceidle force-idle` exist.
4. **Google Play Console Declarations & Compliance Unsubmitted (Blocker B4):** Data safety form, Advertising ID declaration, Health category declarations, `SCHEDULE_EXACT_ALARM` policy justification, content rating questionnaire, support email verification (`support@cyclecare.app`), and live hosted privacy policy remain completely open.
5. **Unpushed Commits & Release Tagging (Blocker B5):** Local `main` contains 69 commits not yet pushed to `origin/main`. No release tag exists.
6. **Wide-Screen Layout Verification Gap (Blocker B6, partially advanced):** The 600 dp and 840 dp configurations in the calibrated 320x640 captures are clamped and bit-for-bit identical to the 320 dp captures. Genuine 840 dp (Expanded) rendering on `cc36` is now evidenced via `adb wm size 840x1280` (97/97 captures with distinct widths; `ResponsiveLayoutTest` passing), but this is a software window resize; physical tablet/foldable verification and visual sign-off remain open.
7. **Physical Device Performance & Cold-Start Baselines Uncollected (Blocker B7 / P9-05, P9-06):** No physical hardware baseline has been collected for cold start, frame timing, or memory budgets under `06` §4.
8. **Visual Sign-off & Store Assets Pending (Blocker B8 / P9-07):** Formal visual review and sign-off by the product owner of the red/white/black palette and official Play Store listing graphical assets are pending.

---

## 2. Audit Baseline & Repository Provenance

| Parameter | Value | Verification Notes |
|---|---|---|
| **Audit Date** | 2026-10-23 | Verified live against local repository |
| **Commit SHA** | `ba0d859453d397ee20a10396af001c0c1c41bf21` | HEAD of local `main` |
| **Branch** | `main` | `## main...origin/main [ahead 69]` |
| **Remote HEAD** | `90d78a2` (`origin/main`) | 69 unpushed local commits ahead of remote |
| **Configured Remotes** | `origin https://github.com/shezad-linux/cyclecare.git` | Checked via `git remote -v` |
| **Release Tags** | None | `git tag -l` returned empty |
| **Working Tree State** | 1 tracked modification, 1 untracked file | Detailed below |

### Working Tree Differentiation:
- **Pre-existing Tracked Modification (preserved, unchanged):** `app/src/androidTest/java/com/cyclecare/app/CycleCareAppStateNavigationTest.kt`  
  *Nature of change:* Refactored test harness to separate standard Compose rule composition (`renderApp`) from direct activity composition (`renderAppOnActivity`) specifically for activity recreation tests (`calendarSelection_survivesTabSwitchRepeatedTapAndRecreate`, `onboardingDraft_survivesActivityRecreate`, `logDraft_survivesActivityRecreate`). This prevents multithreaded `SnapshotStateObserver` collisions while testing `SavedStateHandle` survival. Preserved unchanged throughout this audit.
- **Untracked Audit File (preserved):** `CycleCare-Redesign-Docs/24-final-production-readiness-audit.md` (this audit report).
- **Documentation edits made by this audit (tracked):** `CycleCare-Redesign-Docs/22-phase-completion-plan.md`, `CycleCare-Redesign-Docs/23-verification-runbook.md`, and this file. These reconcile contradictory screenshot-status statements, record the bundletool and wide-width evidence, and correct stale test counts. No application source code, Gradle configuration, CI workflow, or signing configuration was altered.
- **Zero Production Changes:** No application source code, Gradle configurations, CI workflows, or signing configurations were altered during this audit. All commits preserved; branch remains 69 commits ahead of `origin/main`; no tags created.

---

## 3. Production Build & Release Artifact Verification

### 3.1 Artifact Attributes

| Property | Value | Evidence / Command |
|---|---|---|
| **Artifact Path** | `app/build/outputs/bundle/release/app-release.aab` | Produced by `./gradlew bundleRelease` |
| **File Size** | 9,862,215 bytes | Verified via `ls -la` and `wc -c` |
| **SHA-256** | `a12b2cae46ffc2c4cd1f40ae12c4a2e50e936f03e892641dfebc01e948457849` | Computed via `shasum -a 256` |
| **Application ID** | `com.cyclecare.tracker` | `app/build.gradle.kts` line 101 |
| **Version Code** | `7` | `app/build.gradle.kts` line 28 |
| **Version Name** | `1.0` | `app/build.gradle.kts` line 29 |
| **Compile / Target SDK** | `36` / `36` | Matches Android 16 requirement |
| **Minimum SDK** | `26` | Android 8.0 Oreo minimum |
| **Signing Status** | Signed (`jar verified`) | Signed with self-signed 4096-bit RSA upload key |
| **Signer Certificate** | `CN=CycleCare Upload, OU=Mobile, O=CycleCare, L=Bengaluru, ST=Karnataka, C=IN` | Valid from 2026-03-23 to 2053-08-08 |
| **AAB Validation** | **PASSED (official bundletool-all 1.18.3, exit 0)** | `java -jar /tmp/bundletool-all-1.18.3.jar validate --bundle=...` (structural fallback also passed) |
| **R8 / Minification** | Enabled (`isMinifyEnabled = true`) | Real `proguard-rules.pro` applied; enum names kept |
| **Resource Shrinking** | Disabled (`isShrinkResources = false`) | Deliberately deferred for AdMob/UMP dynamic resources |

### 3.2 AAB Validation & Tooling Status

Structural fallback (unchanged, still not bundletool validation):
Command executed:
```bash
./scripts/validate-aab.sh
```
Output:
```text
Validating Android App Bundle: app/build/outputs/bundle/release/app-release.aab
  OK: file exists (9862215 bytes).
  bundletool: NOT AVAILABLE on this machine.
  Falling back to the structural check. This is NOT a bundletool
  validation and it does not prove the bundle is uploadable.
    present: base/manifest/AndroidManifest.xml
    present: BundleConfig.pb
  OK: structural check passed (base/manifest/AndroidManifest.xml, BundleConfig.pb).
  SHA-256: a12b2cae46ffc2c4cd1f40ae12c4a2e50e936f03e892641dfebc01e948457849
Validation PASSED (structural fallback).
```

Official `bundletool-all` validation (updated evidence):
- `bundletool` is absent from PATH. The official executable distribution for the CI version was retrieved from the release URL:
  `https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar` → `/tmp/bundletool-all-1.18.3.jar` (32,520,401 bytes; `java -jar ... version` reports `1.18.3`).
- An earlier local check used the Unity-bundled `bundletool-all-1.17.2.jar` (also exit 0); the CI-intended version 1.18.3 now validates the same artifact.

Exact command executed:
```bash
java -jar /tmp/bundletool-all-1.18.3.jar \
  validate --bundle=app/build/outputs/bundle/release/app-release.aab
```
Result: **exit code 0**, 534 lines of output beginning with `App Bundle information` / `Feature module: base`; no `ERROR`/`FAIL`/validation-failure text. **Validation PASSED** with the official executable `bundletool-all` distribution (v1.18.3).

Interpretation:
- The release AAB is structurally and semantically valid per official bundletool 1.18.3 (protobuf manifest, splits, resource tables parsed).
- This is local validation of the artifact; it does not replace execution of the protected CI release workflow (B1), which must still run end-to-end and validate its own produced artifact.
- The structural fallback above remains documented for environments without bundletool.

### 3.3 Secret Isolation & CI Infrastructure Findings

- **Local Keystore Isolation:** `keystore.properties` is untracked and verified ignored by `.gitignore`. `git ls-files` confirms zero secret keys or credentials tracked.
- **Missing CI Environment:** `gh api repos/shezad-linux/cyclecare/environments` confirms **0 environments exist**. The `release` environment required by `.github/workflows/release.yml` does not exist.
- **Missing CI Secrets:** `gh secret list --env release` returned HTTP 404. None of the four signing secrets are configured on GitHub.
- **Workflow Execution History:** `gh run list` confirms `.github/workflows/release.yml` has **never executed**.

---

## 4. Test Execution & Reconciled Evidence

### 4.1 Unit Test Suite

Command executed:
```bash
./gradlew test
```
**Result:** **505 tests, 505 passed, 0 failures, 0 errors, 0 skipped, 0 unexecuted** (Exit code: 0)

| Module | Tests | Passed | Failures | Errors | Skipped | Status |
|---|---|---|---|---|---|---|
| `:app` | 75 | 75 | 0 | 0 | 0 | **PASS — verified** |
| `:core:data` | 25 | 25 | 0 | 0 | 0 | **PASS — verified** |
| `:core:designsystem` | 43 | 43 | 0 | 0 | 0 | **PASS — verified** |
| `:core:domain` | 203 | 203 | 0 | 0 | 0 | **PASS — verified** |
| `:core:security` | 25 | 25 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:calendar` | 28 | 28 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:export` | 13 | 13 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:home` | 31 | 31 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:insights` | 4 | 4 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:logging` | 26 | 26 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:onboarding` | 7 | 7 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:privacy` | 18 | 18 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:settings` | 7 | 7 | 0 | 0 | 0 | **PASS — verified** |
| **Total** | **505** | **505** | **0** | **0** | **0** | **PASS — verified** |

### 4.2 Prediction Corpus Gates

Command executed:
```bash
./gradlew :core:domain:test --tests "*PredictionCorpusGateTest" --rerun-tasks
```
**Result:** All 13 gates specified in `12` §6 pass deterministically across 29 unit tests:
- `G1`: Regular history coverage >= 80% (**PASS**)
- `G2`: Irregular history coverage >= 90% and window widening (**PASS**)
- `G3`: Regular history MAE <= 2.0 days (**PASS**)
- `G4`: Mean absolute error within tolerance & constant history exactness (**PASS**)
- `G5`: Window sharpness on regular history (**PASS**)
- `G6`: 90th percentile error <= 5.0 days (**PASS**)
- `G7`: Exact minimum interval requirement (**PASS**)
- `G8`: Plausible interval filtering & extreme outlier exclusion (**PASS**)
- `G9`: Irregularity classification rules (2+ outliers among >= 4 intervals) (**PASS**)
- `G10`: Trend correction capping (<= 3 days) and monotonicity (**PASS**)
- `G11`: Determinism (identical input produces bit-identical output) (**PASS**)
- `G12`: Single-edit window stability (**PASS**)
- `G13`: Calendar boundary crossing (leap year, month-end, year-end) (**PASS**)

### 4.3 Reconciled Instrumented Test Suite (XML Reports)

Test Environment: AVD `cc36`, Android 16 (API 36), 320x640 at 160dpi, serial `emulator-5556`.  
Command executed:
```bash
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```
Parsed XML Reports from `**/build/outputs/androidTest-results/connected/debug/*.xml` (artifacts dated 2026-10-23 00:30; re-aggregated during this audit):

| Module | Total Tests | Passed | Skipped | Failed | Errors | Unexecuted | Status |
|---|---|---|---|---|---|---|---|
| `:app` | 159 | 157 | 2* | 0 | 0 | 0 | **PASS — verified** |
| `:core:data` | 36 | 36 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:core:database` | 34 | 34 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:calendar` | 6 | 6 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:home` | 12 | 12 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:insights` | 2 | 2 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:logging` | 2 | 2 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:onboarding` | 7 | 7 | 0 | 0 | 0 | 0 | **PASS — verified** |
| `:feature:privacy` | 10 | 10 | 0 | 0 | 0 | 0 | **PASS — verified** |
| **Total** | **268** | **266** | **2** | **0** | **0** | **0** | **PASS — verified** |

*\*Note on Skipped Tests:*  
The 2 skipped tests in `:app` are:
1. `ReminderDozeDeliveryTest#anAlarmFiresPromptlyWhenTheDeviceIsAwake`
2. `ReminderDozeDeliveryTest#anAlarmFiresWhileTheDeviceIsIdle`  

These tests are skipped by design (`Assume.assumeTrue(runDozeTest)`) during regular test runs because forcing the device into deep Doze (`dumpsys deviceidle force-idle`) disrupts the remaining 26+ instrumented test suites. They are executed in isolation via `-Pandroid.testInstrumentationRunnerArguments.runDozeTest=true`. Zero tests were skipped unintentionally. Zero tests failed. Zero tests remain unexecuted.

---

## 5. Security, Privacy & Compliance Findings

| Gate / Guard | Command / Script | Result | Finding / Evidence |
|---|---|---|---|
| **Ad-Targeting Guard** | `./scripts/guard-no-ad-targeting.sh` | **PASS — verified** | Zero ad-targeting APIs in `app/src/main`. No cycle, symptom, note, or medication data reaches AdMob. |
| **Secret Tracking** | Git tree inspection | **PASS — verified** | Confirmed `keystore.properties`, `*.jks`, `google-services.json` are not tracked in Git. |
| **Privacy Manifest Guard** | `./scripts/guard-privacy-manifest.sh` | **PASS — verified** | Merged manifest analyzed. Zero analytics/crash tracking SDKs. AdServices permissions properly declared. |
| **Notification Log Privacy** | `ReminderDelivery.kt` | **PASS — verified** | `scheduledAtEpochMillis` removed. Latency classified into coarse buckets; period dates cannot be derived. |
| **Data Reset Security** | `AppResetRepository.kt` | **PASS — verified** | Clears Room tables, SharedPreferences, export directory, and cancels armed reminder alarms. |
| **Local Export Encryption** | `core/security` | **PASS — verified** | PBKDF2 key derivation, AES-256-GCM authenticated encryption, AAD binding, fail-closed on tamper. |
| **Dependency Vulnerability** | `./scripts/guard-dependency-vulnerabilities.sh` | **FAIL — reproduced** | `CYCLECARE_ADVISORY_FEED` is unset. Script exited with code 2. Security gate is non-operational. |

---

## 6. Visual, Accessibility & Responsive Layout Audit

### 6.1 Contrast and Design Tokens
- `python3 scripts/contrast-report.py`: **PASS — verified**. All color token pairs with WCAG targets meet or exceed requirements (Light theme body text 19.8:1, Dark theme 18.16:1, primary buttons > 6.5:1).
- `./scripts/guard-typography-roles.sh`: **PASS — verified**. All 15 typography roles are defined and referenced.

### 6.2 Reconciliation of Screenshot Sweep & Abort Investigation

#### Detailed Reconciled Screenshot Breakdown:
All three screenshot sweeps were freshly executed and cataloged into temporary audit directories:

| Configuration | Directory | Image Count | Breakdown by Screen | Physical Pixel Dimensions |
|---|---|---|---|---|
| **Portrait (1.0x font scale)** | `/tmp/shots/cc_shots` | **97 / 97** | `app_lock` (8), `calendar` (8), `cycle_status` (8), `edit_cycle_sheet` (8), `fertility_detail` (8), `home` (8), `insights` (8), `log` (8), `notification_gate` (8), `onboarding` (8), `privacy` (1), `reminder_centre` (8), `settings` (8) | **320 x 640** (all 97 images) |
| **Portrait (2.0x font scale)** | `/tmp/shots/font200/cc_shots_font200` | **97 / 97** | Same 13 screens (8 each, privacy 1) | **320 x 640** (all 97 images) |
| **Landscape (1.0x font scale)** | `/tmp/shots/landscape/cc_shots_landscape` | **97 / 97** | Same 13 screens (8 each, privacy 1) | **320x320** (320dp targets)<br>**360x320** (360dp targets)<br>**600x320** (600dp targets)<br>**640x320** (840dp targets) |

#### Investigation of the "58/97 Abort" Claim:
- **Historical Root Cause:** When `captureDetailScreens` was first introduced in commit `26e45c1`, the 58th image was `edit_cycle_sheet_320dp_light.png`. Immediately following sheet dismissal, the test attempted `composeRule.onNodeWithTag("home_dashboard")`. In earlier Jetpack Compose code, the `LazyColumn` for `home_dashboard` had merged its semantics with children, causing `assertExists()` to fail. The author misdiagnosed this as `pressBackUnconditionally()` killing the activity on short screens and recorded it in P5-06 and `23-verification-runbook.md`.
- **Actual Fix Already in Codebase:** In commit `09a1156`, `useUnmergedTree = true` was added to `home_dashboard` lookups, and `installContent` stabilized the composition lifecycle. As verified in this audit, the test harness **does not abort**: all 5 test cases and all 97 captures execute to completion.
- **Runbook Script Race Condition:** The apparent missing files in past runs stemmed from the runbook's shell polling loop (`sleep 1`). Because Gradle automatically uninstalls the test package upon test completion, app files in `/sdcard/Android/data/...` were deleted before a 1.0s sleep could sync the last test's files (`captureAppLock`). Polling at 0.2s completely eliminates this race condition and preserves all 97 images.

### 6.3 Critical Wide-Screen (600 dp / 840 dp) Layout Finding

Inspection of the actual PNG binary headers revealed a major verification gap:
1. **Portrait Captures (320x640 AVD):** The root Compose view is hosted within an activity whose window width is physically 320 px (320 dp at 160 dpi). When `ReThemeScreenshotTest` sets `Modifier.width(600.dp)` or `Modifier.width(840.dp)`, Compose layout constraints clamp the composable width to the parent window width (320 dp).
   - As evidence, SHA-256 hashes of `calendar_320dp_dark.png`, `calendar_360dp_dark.png`, `calendar_600dp_dark.png`, and `calendar_840dp_dark.png` are **100% bit-for-bit identical** (`36f09f82...`).
   - The same holds true across `home`, `log`, `insights`, and other screens.
   - **Conclusion:** The portrait captures for 600 dp and 840 dp are purely redundant 320 dp captures. They provide **zero verification** of tablet or desktop layouts.
2. **Landscape Captures (640x320 AVD):** The display width is 640 dp. While 320 dp, 360 dp, and 600 dp render at their respective widths, the 840 dp target is clamped to 640 px wide.
   - **Conclusion:** 840 dp (Expanded WindowSizeClass) is never evaluated in either orientation on the calibrated window.

### 6.4 Genuine wide-width verification performed on `cc36` (override)

To obtain genuine 600 dp / 840 dp evidence without creating another AVD, the calibrated `cc36` AVD (320x640, 160 dpi) was reconfigured with a software window override:

Command executed:
```bash
adb -s emulator-5556 shell wm size 840x1280
adb -s emulator-5556 shell wm density 160
```
Verification of effective width / WindowSizeClass:
```bash
adb -s emulator-5556 shell wm size
# Override size: 840x1280
# system config reports w840dp / sw840dp (Expanded)
```
- Effective window width: **840 dp** (840 px at 160 dpi).
- Resulting WindowSizeClass: **Expanded** (`sw840dp` / `w840dp`).

Screenshot sweep under the override (same harness, 0.5 s polling):
```bash
rm -rf /tmp/shots_wide && mkdir -p /tmp/shots_wide
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.ReThemeScreenshotTest
```
Result: **97 / 97 images**, widths now non-clamped: 320 (24), 360 (25), 600 (24), 840 (24) at 1280 px height; SHA-256 distinct across widths (`calendar_320dp_dark.png` `36f09f82…` under 320 window vs `a2ed9720…` under override; `calendar_600dp_dark.png` `c827e87c…`; `calendar_840dp_dark.png` `432f4c1e…`). `ResponsiveLayoutTest` also passed under the override (3 tests, 0 failures).

Limitations of this evidence (must be stated alongside any claim):
- The underlying physical display remains 320x640; the override is a software window resize, not a physical tablet/desktop.
- Touch-target, font-scale and TalkBack assertions were not re-run under the override.
- Foldable posture (hinge) is still unverified (P5-02).
- No production code changes; no application behaviour modified.

Device restored after the run:
```bash
adb -s emulator-5556 shell wm size reset
adb -s emulator-5556 shell wm density reset
# verified: Physical size 320x640, Physical density 160
```

Audit conclusion: the calibrated 320x640 captures alone do **not** verify 600/840 dp layouts, but genuine 840 dp (Expanded) rendering on `cc36` is now evidenced via the override, with distinct non-clamped captures. Physical tablet/foldable and real-hardware verification remain open.

3. **Audit Requirement (unchanged):** The 600 dp and 840 dp configurations **must not be counted as verified wide-screen layouts** on the basis of the 320x640 captures alone. True physical-hardware verification still requires a tablet/foldable device.

---

## 7. Notification Reliability & Doze

- **Intelligence & Delivery Path:** Production delivery path in `ReminderDelivery.kt` integrates scoring, quiet hours, DND filtering, auto-disable explanation notices, idempotence locks, `setPublicVersion` private body redaction, and `VISIBILITY_PRIVATE` masking.
- **Alarm Policies:** Tier 1 alarms (`PeriodPrep`, `LatePeriod`) use `setExactAndAllowWhileIdle` when `SCHEDULE_EXACT_ALARM` is granted, gracefully degrading to Tier 2 (`setAndAllowWhileIdle`) when revoked.
- **Doze Exit Criterion (P7-05):** **BLOCKED / OPEN**. Spec `13` §17 requires physical-device demonstration under battery-saving Doze. Emulator tests demonstrated delivery under simulated `dumpsys deviceidle force-idle`, but physical device verification is unperformed.

---

## 8. Comprehensive Phase Acceptance Ledger (Phases 0–10)

| Phase | Description | Status | Evidence / Notes |
|---|---|---|---|
| **Phase 0** | Audit and product decisions | **PASS — verified** | Architecture established; roadmap baselined. |
| **Phase 1** | Safety & Disclosures | **PARTIAL** | In-app disclosures verified. External Play Store declarations unsubmitted. |
| **Phase 2** | Contracts & Architecture | **PASS — verified** | `PredictionSnapshot` and `confidenceLabel` retired. `cycleLength` column bypassed in favor of canonical interval extractor (`StoredCycleLengthGuardTest` passes). |
| **Phase 3** | Database Migrations | **PASS — verified** | v1 -> v2 -> v3 migrations lossless, tombstones preserved, divergence tracked, abort/rollback verified (34/34 tests pass). |
| **Phase 4** | Prediction Engine | **PASS — verified** | All 13 prediction corpus gates pass (`PredictionCorpusGateTest`). Canonical intervals used end-to-end. |
| **Phase 5** | Design System & A11y | **PARTIAL** | Contrast and typography clean. 97/97 captures produced in portrait 1.0x, portrait 2.0x and landscape. Genuine 840 dp (Expanded) rendering now evidenced on `cc36` via `wm size` override (97 captures with distinct widths); physical tablet/foldable still unverified. |
| **Phase 6** | Screen Implementation | **PARTIAL** | 13 screens rendered and testable. Genuine 840 dp (Expanded) rendering evidenced on `cc36` via `wm size` override; physical tablet/foldable wide viewports remain unverified. |
| **Phase 7** | Notifications & Doze | **PARTIAL** | Intelligence and alarm tiers wired. Physical-device Doze verification unperformed (P7-05). |
| **Phase 8** | Cloud Sync | **DEFERRED — approved decision** | Explicitly deferred per `20-sync-decision.md`. Encrypted local export/import is the official transfer method. |
| **Phase 9** | Release Readiness | **FAIL — reproduced** | CI release workflow unexecuted; signing secrets unconfigured; advisory feed missing; Play Console items unsubmitted. |
| **Phase 10**| Post-Launch Monitoring | **DEFERRED — future work** | Out of scope for pre-launch release gate. |

---

## 9. Prioritized Release Blockers & Next Actions

| # | Priority | Blocker Description | Owner | Required Action to Close | Exit Evidence |
|---|---|---|---|---|---|
| **B1** | **P0** | CI Release Environment & Secrets Missing | Release Ops / Repo Owner | Create `release` environment in GitHub repository settings; configure the 4 required base64 secrets (`CYCLECARE_KEYSTORE_BASE64`, `CYCLECARE_STORE_PASSWORD`, `CYCLECARE_KEY_ALIAS`, `CYCLECARE_KEY_PASSWORD`). | `gh secret list --env release` returns all 4 secrets; `.github/workflows/release.yml` executes successfully on tag. |
| **B2** | **P0** | Missing Advisory Feed Variable | Security Lead | Set repository variable `CYCLECARE_ADVISORY_FEED` to an active, valid vulnerability feed URL. | `./scripts/guard-dependency-vulnerabilities.sh` exits with code 0. |
| **B3** | **P0** | Physical-Device Doze Demonstration | QA Lead | Install release build on physical Android hardware; record actual arrival latency under awake and deep Doze. | Documented test run with device model, OS version, timestamps, and arrival latencies in `19-doze-verification.md`. |
| **B4** | **P0** | Play Console Mandatory Declarations | Product Owner | Complete Data safety questionnaire, Advertising ID declaration, Health category justification, `SCHEDULE_EXACT_ALARM` justification, and content rating questionnaire. Verify support email and publish privacy policy. | Play Console submission receipts / approved dashboard status. |
| **B5** | **P1** | Unpushed Commits & Release Tagging | Release Engineer | Push `main` to `origin/main` (69 commits ahead) and push release tag (e.g. `v1.0`). | Tag visible on GitHub remote; triggers release pipeline. |
| **B6** | **P1** | Wide-Screen (600 dp / 840 dp) Layout Verification | UI / QA Engineer | Partially advanced: genuine 840 dp (Expanded) rendering on `cc36` is now evidenced via `adb wm size 840x1280` (97/97 captures with distinct 320/360/600/840 widths; `ResponsiveLayoutTest` passing). Remaining: physical tablet/foldable verification, touch-target/font-scale/TalkBack re-run under the override, and formal visual review of the wide captures. | Review captures on physical tablet/foldable hardware; signed visual sign-off. |
| **B7** | **P1** | Physical Device Matrix & Performance Baseline | QA Lead | Execute cold-start and frame-timing macrobenchmarks on physical low/mid-range hardware; verify budgets per `06` §4. | Documented benchmark run results in `06-testing-and-acceptance.md`. |
| **B8** | **P2** | Visual Palette & Store Listing Approval | Design Lead / Owner | Conduct formal visual review of captured assets and red/white/black palette. Prepare official store listing assets. | Signed visual sign-off document. |

---

## 10. Final Decision Rationale

Release to production is strictly **NO-GO**. 

While the engineering team has achieved remarkable technical rigor within the local repository—exemplified by 505 passing unit tests, 268 instrumented tests (266 passed, 2 opt-in Doze skipped, 0 failures), zero compiler/lint warnings, full mathematical prediction validation, and strict privacy guards—the application cannot legally, safely, or operationally be deployed to end users today. 

The automated CI release pipeline has never executed, the signing secrets do not exist in GitHub infrastructure, the mandatory vulnerability scanning gate fails, Google Play regulatory declarations have not been submitted, wide-screen adaptive layouts remain unverified on genuine wide viewports, and the core battery-saving notification promise has not been validated on real Android hardware. 

Release approval must be withheld until Blockers B1 through B5 are fully resolved.
