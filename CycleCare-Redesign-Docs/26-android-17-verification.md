# Android 17 (API 37) verification — evidence record

Date: 2026-11-09 → 2026-11-10. Branch: `android-17-api37`.
This document records what was verified, on what, and what remains open.

## Outcome

**CycleCare compiles against, and now targets, Android 17 (API 37).**
Everything that previously gated the API 37 upgrade has been executed and
recorded. Nothing was uploaded or published to any store; release packaging
was built and validated locally, and the Play Console work remains the
owner's.

## Environment

| | |
|---|---|
| Host | Apple Silicon, macOS; ANDROID_SERIAL=`emulator-5554` |
| AVD | `cc37` — created for this verification; `android-37.0;google_apis;arm64-v8a`, `small_phone` profile with display forced to **320x640 @ 160dpi** (same calibration geometry as the retired `cc36`), `<temp>` 4 GB userdata |
| SDK | `platforms/android-37.0`, build-tools `37.0.0`, ARM64 `google_apis` image |
| App | debug build (`applicationId` `com.cyclecare.tracker`, `debug` build type) and release build (`app-release.aab`) |

Historical note: `cc36` (Android 16, API 36, 320x640 @ 160dpi) no longer
exists. All 2026-10-20/23 API 36 numbers were taken on it.

## What changed in this upgrade

| Change | Where |
|---|---|
| `compileSdk` 35/36 → **37** for all **14 Android modules**, via a single source of truth (`androidCompileSdk = "37"` in `gradle/libs.versions.toml`) | 14 × `build.gradle.kts`, `gradle/libs.versions.toml` |
| `targetSdk` 36 → **37**; `versionCode` 7→8 (2.0) → **9 (2.1.0)** | `app/build.gradle.kts` |
| Espresso pinned at **3.7.0** in the 6 Compose-test feature modules (they resolved 3.5.0 transitively, which fails every Compose UI test on Android 17 with `NoSuchMethodException: android.hardware.input.InputManager.getInstance()`) | `feature:{home,calendar,insights,logging,onboarding,privacy}/build.gradle.kts` |
| `RtlLayoutTest` derived the selected-day tag from the hardcoded `2026-10-21`; now `LocalDate.now()` | `app/.../RtlLayoutTest.kt` |
| **Layout defect fixed**: the calendar header "November 2026" could measure wider than the row at narrow widths and clip the next-month button out of composition entirely (zero bounds). The text column now takes `Modifier.weight(1f)` | `feature/calendar/CalendarScreen.kt` |
| Background-audio restriction documented at the reminder sound preview path | `app/.../ReminderChannels.kt` |
| CI instrumented lane now targets **API 37** (advisory, x86_64 `google_apis`, unchanged job structure); stale "WHY API 36 AND NOT 37" comment replaced with the actual root cause + this record | `.github/workflows/ci.yml` |
| `docs/release-checklist.md`, runbook env/Doze sections | docs |

## Verification results

### Before the fixes (API 37, `cc37`)

- Instrumented suite: `:core:database` 34/34 pass, `:core:data` 36/36 pass,
  `:feature:onboarding` persistence 1/1 pass; **`OnboardingScreenUiTest`
  6/6 FAIL** — root-caused to `espresso-core:3.5.0` transitive
  (`ui-test-junit4` → `ui-test-junit4-android`), not Android 17 itself.
- App module: 159 tests, 4 failures — 1 stale hardcoded date (fixed),
  3 width-dependent assertions calibrated for 720x1280; they pass at the
  calibrated 320x640 geometry. The calendar failure exposed the real
  width-dependent layout defect (fixed).

### After the fixes

| Check | API 37 (`cc37`) | API 36 baseline (2026-10-20, `cc36`) |
|---|---|---|
| Unit tests (`./gradlew test`) | **505/505 pass** | 505/505 |
| Instrumented (`connectedDebugAndroidTest --continue`) | **268 tests, 0 failures, 2 skipped** | 268 tests, 0 failures, 2 skipped |
| `:core:database` | 34/34 | 34/34 |
| `:core:data` | 36/36 | 36/36 |
| `app` | 159 (2 skipped) | 159 (2 skipped) |
| Doze delivery (`ReminderDozeDeliveryTest`, opt-in) | **2/2 pass; awake 22 s, deep idle 45 s** | 2/2 pass; 22 s / 45 s |
| `lintDebug` | 0 errors | 0 errors |
| Post-`targetSdk=37` re-run | **268 tests, 0 failures, 2 skipped; Doze 22 s / 45 s** | — |
| Backup restore on emulator | Not possible — `bmgr` cannot open a session on the AVD (`Error: Could not get restore session for user 0`); `dumpsys backup` shows the app and agent register cleanly; **static review green** | Unverified static review |

The identical Doze latencies on API 37 (22 s / 45 s) are strong evidence the
reminder architecture behaves on Android 17 exactly as it did on Android 16.

## Release artifact (2.1.0)

| | |
|---|---|
| Path | `app/build/outputs/bundle/release/app-release.aab` |
| Size | 10,243,812 bytes |
| SHA-256 | `93901ccaf279d8a7e85af3b3627a413656077e12809f60ced59b48d979b4c555` (2026-11-10 rebuild after the `app/build.gradle.kts` script-compilation repair; the earlier build's hash was `1534f69c…`; AAB hashes are per-build) |
| Manifest | `compileSdkVersion="37"`, `versionCode="9"`, `versionName="2.1.0"`, `minSdk="26"`, `targetSdkVersion="37"`, `package="com.cyclecare.tracker"` |
| Signer | `CN=CycleCare Upload, OU=Mobile, O=CycleCare, L=Bengaluru, ST=Karnataka, C=IN` (same upload key as before) |
| Validation | `bundletool` 1.18.3 `validate` → PASS (run from the Gradle cache; the GitHub `bundletool-all` 1.18.3 does not exist — 404 — classpath assembled from the cache + `protobuf-java` 3.22.3 from Maven Central); `jarsigner -verify` → `jar verified.`; structural `scripts/validate-aab.sh` → PASS |
| `scripts/check-release-version.sh` | PASSED (versionCode 9, versionName 2.1.0) |

History: the 2.0 release candidate (versionCode 8, targetSdk 36) built and
verified on 2026-11-10 — SHA-256
`e681816ff71267e7b03a1625e936ef24816a4752805ce8f8e6c5050d4d241856`.

## Open items

1. **Exact-alarm (`SCHEDULE_EXACT_ALARM`) Play declaration** — still open
   (`play-console/data-safety-unresolved.md`); release dependency.
2. **Backup-restore on a physical device** — not exercisable on the AVD.
3. **Physical-device Doze sign-off** — `19-doze-verification.md` remains the
   execution sheet; emulator evidence is strong but is not a substitute.
4. **Play Console Policy status check** — API 36 is required today; API 37
   expected August 2027. Confirm in Play Console before upload.
5. **Onboarding flash on configuration change** (pre-existing) — the app
   renders `OnboardingRoute` for ~2–4 s on activity recreation while the
   Room profile flow re-emits. Not Android 17-specific, but aggravated by
   the Android 17 resize regime. Fix = tri-state profile with a neutral
   container; owner decision required.
6. **Android vitals** for API 37 devices after release; first-month review.

## Environment notes

- `cc37` ARM64 + swiftshader shows GPU instability under load (repeated
  `bad color buffer handle` errors); suites run reliably. Prefer x86_64
  (CI) or `aosp_atd` ARM64 for interactive sessions.
- The Pixel_10a 16 KB Play Store image self-rebooted once during rotation
  probing (system-side; the app never crashed; Room survived intact).
- `connectedDebugAndroidTest` uninstalls the app on completion (see
  runbook §10) — explains the empty `pm list packages` after a suite.
