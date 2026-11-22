# CycleCare — Baseline Audit (Phase 1)

**Audit date:** 2026-11-22
**Branch:** `android-17-migration-audit-remediation` (created from `android-17-api37` @ `bb9deb3`)
**Scope:** Complete Android 17 (API 37) migration, API deprecation audit, dependency modernization, functional audit, privacy/security audit, and production readiness.

> Note: This app is a Kotlin/Compose Android application (CycleCare, `com.cyclecare.tracker`). It is **not** a Flutter app. All "Dart/Flutter" audit items are recorded as N/A.

---

## 1. Repository Discovery

### 1.1 Modules (settings.gradle.kts)

| Module | Purpose |
|---|---|
| `:app` | Application shell + navigation |
| `:benchmark` | Macrobenchmark (Android Test) |
| `:core:model` | Domain models & enums |
| `:core:domain` | Domain logic (prediction engine, insights, notification intelligence, ads gating) |
| `:core:data` | Repositories (Room, DataStore, Export) |
| `:core:database` | Room database + migrations (v3) |
| `:core:designsystem` | Design system (M3 + tokens) |
| `:core:security` | Crypto contracts (vault cipher, passphrase policy) |
| `:core:testing` | Test utilities (FixedTimeProvider, fixtures) |
| `:feature:onboarding` | Onboarding flow |
| `:feature:home` | Home dashboard, period status, fertility detail |
| `:feature:calendar` | Calendar + cycle history editor |
| `:feature:logging` | Symptom logging |
| `:feature:insights` | Insights (trends, symptom patterns) |
| `:feature:settings` | Settings, reminders, channels, exact-alarm access, reset, migration notes |
| `:feature:privacy` | App lock, PIN/biometric, Data Safety |
| `:feature:export` | Export & encrypted backup |

Source inventory: **316 Kotlin/Java files** (app: 41+21+14, features: 48, core: ~120), plus resource files. `feature/billing` exists on disk with `build/`+`src/` but is **not referenced by `settings.gradle.kts` and not referenced by any other module or build script** — recorded as an orphan module candidate for Phase 2 disposal review.

### 1.2 Application identity

| Item | Value | Source |
|---|---|---|
| Application ID | `com.cyclecare.tracker` | `app/build.gradle.kts` (namespace `com.cyclecare.app`) |
| versionName | `2.1.0` | `app/build.gradle.kts` (`defaultVersionName`) |
| versionCode | `10` | `app/build.gradle.kts` (`defaultVersionCode`) |
| Merged manifest (built APK) | `versionCode="10" versionName="2.1.0" targetSdkVersion="37" compileSdkVersion="37" minSdkVersion="26"` | `aapt dump badging` (verified on built `app-debug.apk`) |

### 1.3 Toolchain (environment: macOS arm64)

| Component | Installed version |
|---|---|
| Java/JDK | OpenJDK 17.0.17 (Corretto) |
| Gradle (wrapper) | 9.3.1 (`gradle/wrapper/gradle-wrapper.properties`) |
| Android Gradle Plugin (AGP) | 9.1.0 |
| Kotlin | 2.2.10 |
| KSP | 2.3.2 |
| Gradle Daemon JVM toolchain | 21 (file: `gradle/gradle-daemon-jvm.properties`) |
| Android SDK | `/Users/shezad/Library/Android/sdk` |
| Android platforms | `android-31`, `android-33`, `android-34`, `android-35`, `android-36`, `android-36.1`, **`android-37.0`** |
| Build tools | 34.0.0 … 37.0.0 (`aapt` 37.0.0 available) |
| AVDs | `Pixel_10a` (Android 17, API 37.0, Play Store, 16 KB page size) |

**Flutter/Dart:** N/A — this is a Kotlin/Compose app. No `pubspec.yaml`, no Flutter modules. (Audit items about "Flutter", "Dart APIs", "pubspec.yaml", "Flutter widgets", etc. are recorded N/A.)

### 1.4 Build features

- Compose + buildConfig enabled (`buildFeatures { compose = true; buildConfig = true }`).
- Java 17 source/target compatibility; `kotlin { jvmToolchain(17) }`.
- R8 minify enabled for `release`; resource shrinking explicitly disabled (`isShrinkResources = false`).
- Signing: `keystore.properties` (git-ignored) points at `signing/upload-keystore.jks`; `releaseSigningConfigured` resolved at configuration time. **Do not modify** per task constraint.
- `android.newDsl=false`, `android.builtInKotlin=false`, etc. in `gradle.properties`.
- `android:appComponentFactory="androidx.core.app.CoreComponentFactory"` (legacy but valid).

---

## 2. Baseline Build & Test Evidence (BEFORE any modifications)

All commands executed on this branch before any changes (working tree clean, `git status` verified).

| Command | Result |
|---|---|
| `./gradlew test :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintVitalRelease --continue` | **BUILD SUCCESSFUL in 1m 52s** (542 executed, 540 up-to-date tasks) |
| `./gradlew test` (unit tests, all modules) | **505 tests, 0 failures, 0 errors, 0 skipped** (70 result files across app/core/feature) |
| `:app:assembleDebug` | Built: `app/build/outputs/apk/debug/app-debug.apk` — `versionCode=10 versionName=2.1.0 targetSdk=37 compileSdk=37 minSdk=26` (aapt) |
| `:app:assembleRelease` | Built: `app/build/outputs/apk/release/app-release.apk`; AAB from earlier release build: `app/build/outputs/bundle/release/app-release.aab` |
| `:app:lintDebug` | **0 errors, 55 warnings** (all NewerVersionAvailable/ObsoleteSdkInt/UnusedResources class; one pre-existing `InlinedApi` warning at `ReminderRescheduleReceiver.kt:20`) |
| `:app:lintVitalRelease` | **No issues found.** |
| `apksigner verify` (planned, Phase 8) | Pending |
| Instrumented tests on API 37 emulator | Pending (Phase 8 — `connectedDebugAndroidTest`) |

Artifacts exist: `app-debug.apk`, `app-release.apk`, `app-release.aab` (v2.1.0, targetSdk 37).

**Baseline status: green.** The "do not assume compatible merely because it builds" requirement is honored: the audit below independently verifies platform compatibility, deprecations, and privacy, not just build success.

---

## 3. Git & Repository State

- Branch: `android-17-migration-audit-remediation` (new, from `android-17-api37` @ `bb9deb3`).
- Working tree: **clean** at start (`git status` empty).
- Commits so far (`android-17-api37` line):
  - `bb9deb3` Rebuild the 2.1.0 release candidate as versionCode 10
  - `fb33750` Record the Android 17 (API 37) verification evidence
  - `14816f4` Add large-window containment and fix the wide-window defects it exposed
  - `4243d70` Ship 2.1.0: target Android 17 (API 37)
  - `ae1baaa` Compile against and verify Android 17 (API 37)
- Per task constraint, **no uncommitted changes were discarded**; all subsequent changes go into new commits on the new branch.

---

## 4. Audited Surface Summary (Phase 1 findings feeding later phases)

- **App structure:** Compose MVVM with modular features; 20+ receivers/services in the app manifest; Room schema v3 with full migration chain; AdMob + UMP integration; BiometricPrompt app-lock; Export/Backup via encrypted file.
- **Platform surface:** alarms (exact + inexact while idle), notifications with public versions, WorkManager, DataStore, Room, BiometricPrompt, FileProvider, AndroidX Startup.
- **Notable pre-existing work:** The repo has extensive Android-17 migration evidence from the `android-17-api37` branch (instrumented suite on API 37 AVD green per `CycleCare-Redesign-Docs/26-android-17-verification.md`, Phase 2–4/5 execution records). This audit independently re-verifies.

---

## 5. Reporting Notes

- Distinguish: **Automated tests** (unit: 505 pass), **Emulator tests** (to run Phase 8), **Physical-device tests** (none — see Phase 8), **Instrumented** (268 expected).
- All claims in later phases are evidence-based and cite commands/files; no test results or dependency versions are invented.
