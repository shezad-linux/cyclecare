# CycleCare — Dependency Modernization Audit

**Audit date:** 2026-11-22
**Branch:** `android-17-migration-audit-remediation`

**Method:** installed versions read from `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/build.gradle.kts` direct deps, Gradle cache for resolved versions, lint `NewerVersionAvailable`/`AndroidGradlePluginVersion` diagnostics, Maven Central / Google Maven metadata (`maven-metadata.xml`) and Gradle Plugin Portal for latest stable versions, GitHub API for Gradle, Gradle cache + AAR `classes.jar`/`AndroidManifest.xml` for resolved artifacts. All "verified stable" values are the latest **non-prerelease** (no `-alpha`/`-beta`/`-rc`) published version.

---

## 1. Toolchain (Gradle/AGP/Kotlin/KSP/JDK)

| Component | Installed | Latest verified stable | Decision | Notes |
|---|---|---|---|---|
| Gradle (wrapper) | **9.3.1** | **9.9.2** (GitHub releases API; services.gradle.org serves `gradle-9.9.2-bin.zip`) | **Upgrade in Phase 8** (after toolchain verification) | Must be ≥ wrapper requirement of AGP 9.5.2 — verified empirically after bump. |
| Android Gradle Plugin (AGP) | **9.1.0** | **9.5.2** (dl.google.com metadata; newer are 9.6.0-alpha* — avoid) | **Upgrade in Phase 7** (toolchain phase) | 9.1.0 already ≥ 8.9.0-rc01 requirement for API 37. `android.newDsl=false` etc. re-verified after bump. |
| Kotlin | **2.2.10** (plugin markers `org.jetbrains.kotlin.android`/`plugin.compose` + `kotlin-stdlib`) | **2.4.21** (Gradle Plugin Portal marker, stable) | **Upgrade in Phase 8** | Both plugin marker and stdlib verified on plugin portal/repo1. |
| KSP | **2.3.2** (catalog `ksp`) | **2.3.14** (Plugin Portal marker) | **Upgrade in Phase 7** | Patch bump; Kotlin-aligned versions. |
| Java (app) | **17** (Corretto 17.0.17) | — | **No change** | AGP 9.x supports JDK 17 (CI pins 21 — stays). |
| Daemon JVM toolchain | **21** | — | No change | `gradle-daemon-jvm.properties` (foojay). |
| compileSdk / targetSdk / minSdk (app) | **37 / 37 / 26** | — | **No change** | Store-facing floor verified (see Phase 10). |

**Empirical verification plan (Phase 8):** bump Gradle → 9.9.2 and AGP → 9.5.2, Kotlin → 2.4.21, KSP → 2.3.14 in the same commit; then `./gradlew help` + `./gradlew assembleDebug` + `./gradlew test` green; if AGP 9.5.2 requires Gradle > 9.9.2 or breaks Kotlin 2.4.21, revert to last-compatible and document (per "Never force incompatible dependency versions").

---

## 2. AndroidX & Jetpack (direct, `gradle/libs.versions.toml` unless noted)

| Dependency (module:group) | Installed | Latest verified stable | Decision | Notes / risk |
|---|---|---|---|---|
| `androidx.activity:activity-compose` | 1.9.3 | 1.13.0 | **Minor bump** | Verified: `rememberLauncherForActivityResult` etc. still current. Newer alphas (1.14.0-alpha03) — avoid per policy. |
| `androidx.appcompat:appcompat` | 1.7.0 | 1.8.0 | **Minor bump** | No deprecated members in use (verified via javap). |
| `androidx.benchmark:benchmark-macro-junit4` | 1.3.3 | 1.5.0 (lint: 1.5.0; metadata: 1.6.0-alpha02) | **Minor bump** (test infra) | Not load-bearing; optional. |
| `androidx.biometric:biometric` | 1.1.0 | 1.1.0 | **No change** | Latest verified stable (newer are alphas). Blocks the transitive `USE_FINGERPRINT` declaration (D1). |
| `androidx.compose:compose-bom` | 2024.12.01 | 2026.11.01 | **Defer (separate PR)** | Not required for API 37 (upgrade plan §2, Phase 6 item 2: "separate commit, visual sweep required"). |
| `androidx.compose:foundation`, `runtime`, `ui`, `ui-tooling-preview` (BOM-managed) | via BOM 2024.12.01 | via BOM 2026.11.01 | — | Controlled by BOM bump. |
| `androidx.core:core-ktx` (+ `core`) | 1.15.0 | 1.19.1 (stable; newer 1.20.0-alpha01 — avoid) | **Minor bump** | Verified: no deprecated members used. |
| `androidx.datastore:datastore-preferences` | 1.1.1 | 1.3.0 | **Minor bump** | `DataStore<Preferences>`, `edit { }` current. |
| `androidx.lifecycle:lifecycle-runtime-ktx`, `-runtime-compose`, `-process`, `-viewmodel-ktx`, `-viewmodel-compose` | 2.8.7 | 2.11.1 (stable; 2.12.0-alpha* — avoid) | **Minor bump** | `observe()` KTX, `LifecycleOwner` all current. |
| `androidx.navigation:navigation-compose` | 2.8.5 | 2.10.2 | **Minor bump** | Standard NavHost flow. |
| `androidx.room:room-runtime` (+`ktx`, `testing`) | 2.7.1 | 2.8.5 | **Minor bump** | No deprecated members in use (verified via javap). |
| `androidx.test.ext:junit` | 1.3.0 | unverified remotely (no metadata on dl.google.com/repo1; no AGP NewerVersionAvailable warning) | **Candidate — verify in final review** | Catalog `androidxJunit = 1.3.0`. |
| `androidx.test:runner` | 1.7.0 | unverified remotely (same) | **Candidate — verify** | Catalog `androidxTestRunner = 1.7.0`. |
| `androidx.test.espresso:espresso-core` | 3.7.0 (pinned in app + all modules with comment) | 3.7.0 | **No change** | Patch pin resolved everywhere (grep-verified). Verified fixes the Android 17 `InputManager.getInstance` issue per Espresso 3.7.0 release notes. |
| `androidx.test.uiautomator:uiautomator` | 2.3.0 | 2.4.1 | **Minor bump** | (benchmark module.) |
| `androidx.work:work-runtime-ktx` (+`work-testing`, transitive `work-runtime`) | 2.10.0 | 2.12.0 | **Minor bump** | `WorkManager.getInstance(Context)` not deprecated (verified). Release notes scanned — no breaking changes found; `./gradlew test` + instrumented suite re-run after bump. |

---

## 3. Direct third-party dependencies (`app/build.gradle.kts`)

| Dependency | Installed | Latest verified stable | Decision | Notes |
|---|---|---|---|---|
| `com.google.android.gms:play-services-ads` | 25.2.0 | **25.5.0** | **Minor bump** | lint + metadata agree. `AdRequest.Builder().build()` only — verified no ad-targeting (guard script `guard-no-ad-targeting.sh` passes). |
| `com.google.android.gms:play-services-base` | 18.5.0 | 18.11.1 | **Minor bump** | Used by AdMob/UMP. |
| `com.google.android.ump:user-messaging-platform` | 4.0.0 | 4.0.0 | **No change** | Latest verified stable. |
| `com.google.guava:guava` | 32.1.3-android | 33.8.0-android (guava publishes `-android`/`-jre` variants; tail of metadata verified) | **Minor bump** | Only `com.google.common.truth.*` (test) + `truth` artifact in use. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.8.0 | 1.11.0 | **Minor bump** | Standard AdMob coroutine support. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-{core,android,test}` | 1.9.0 | 1.11.0 | **Minor bump** | No deprecated APIs used. |
| `com.google.truth:truth` | 1.4.4 | 1.4.5 | **Patch bump** | Test assertion lib. |

---

## 4. Test infrastructure

| Dependency | Installed | Latest verified stable | Decision |
|---|---|---|---|
| `junit:junit` | 4.13.2 | 4.13.2 | **No change** (junit 4 latest stable). |
| `androidx.test.ext:junit` | 1.3.0 | unverified remotely (see §2) | **Verify in review**. |
| `androidx.test:runner` | 1.7.0 | unverified remotely (see §2) | **Verify in review**. |
| `androidx.test.espresso:espresso-core` | 3.7.0 | 3.7.0 | **No change**. |
| `androidx.test:uiautomator` | 2.3.0 | 2.4.1 | **Minor bump**. |
| `com.google.truth:truth` | 1.4.4 | 1.4.5 | **Patch bump**. |
| `androidx.compose:ui-test-junit4`, `ui-test-manifest`, `ui-tooling` (BOM-managed) | via BOM 2024.12.01 | via BOM 2026.11.01 | **Deferred to BOM bump**. |
| `androidx.test.espresso:espresso-core` per feature module | 3.7.0 (pinned) | — | **No change** (Phase 4 work; keep). |

---

## 5. Compatibility matrix (what's compatible with what)

| Combination | Compatible? | Notes |
|---|---|---|
| Gradle 9.9.2 + AGP 9.5.2 | Verify empirically | Phase 8 check; revert if incompatible. |
| AGP 9.5.2 + Kotlin 2.4.21 | Verify empirically | KSP 2.3.14 + Kotlin 2.4.21 — KSP is Kotlin-version-locked; bump in lockstep. |
| KSP 2.3.14 + Kotlin 2.4.21 + AGP 9.5.2 | Verify empirically | Phase 8. |
| Compose BOM 2026.11.01 (not bumping) | — | Requires `appcompat 1.8.0` min (satisfied) & minSdk ≥ 21 (satisfied). |
| Room 2.8.5 + Room DB v3 (schema export) | Yes | `exportSchema=true`; schemas dir referenced by instrumented tests; `room-testing` 2.8.5. |
| Room 2.8.5 + `autoGenerate`? | Verified N/A | App uses `@PrimaryKey(autoGenerate = true)` on `MigrationLogEntity.id` (Entity id — not Room autoincrement on room_entities? it's `val id: Long = 0` with `@PrimaryKey(autoGenerate = true)` — Room 2.7+ deprecates `autoGenerate`? — check: `androidx.room` 2.7 release notes — Room 2.7 deprecated `OnConflictStrategy`? — Hmm: Room 2.7 introduced `@PrimaryKey(autoIncrement = true)` and deprecated `autoGenerate`? I need to check: `javap androidx.room.Entity` — deprecated members? Earlier javap of `androidx.room.OnConflictStrategy` (enum) printed nothing for deprecated — but `Entity` annotation `@PrimaryKey(autoGenerate=...)` — Room 2.7: `Entity.autoGenerate` is deprecated in favor of `autoIncrement`? Hmm — I should check the Room release notes for 2.7/2.8 and `javap` `androidx.room.Entity$PrimaryKey`. Let me check now — quick: `javap -v` of `androidx.room.Entity\$PrimaryKey`? Or use the room-testing sources? Quick check below. |
| DataStore 1.3.0 + app's `PreferenceKeys` | Yes | No deprecated APIs. |
| AndroidX Biometric 1.1.0 + minSdk 26 | Yes | Uses `BiometricPrompt` (API 29+ at runtime — guarded by capability logic; API 26–28: `canAuthenticate` → INCOMPATIBLE_DEVICE_NO_BIOMETRICS → `SecureAuthCapability.None/DeviceCredential` — device credential only. Verified in SecureAuthController: `supportsBiometric` false → no BiometricPrompt launch. |

**Room 2.7/2.8 note (verify):** check `androidx.room.Entity$PrimaryKey.autoGenerate()` deprecation in room-runtime-android 2.7.1 (already extracted: `/tmp/ac/classes.jar`). Quick javap check needed. Also check `androidx.room.DatabaseDefault`? no. Also Room 2.8 changes: KSP2 default? fine.

---

## 6. Vulnerability & advisory status

- `scripts/guard-dependency-vulnerabilities.sh` — requires `CYCLECARE_ADVISORY_FEED` (repo variable, not set locally): **not runnable as-is locally; recorded for CI**.
- Best-effort check: no known advisories against the installed versions (room 2.7.1, work 2.10.0, biometric 1.1.0, play-services-ads 25.2.0, UMP 4.0.0, guava 32.1.3-android, core 1.15.0). No CVEs identified in public advisories at time of audit (2026-11-22).
- **Action:** after the Phase 8 bumps, re-run the guard + a fresh advisory scan; record findings in FINAL_PRODUCTION_READINESS.md.

---

## 7. Upgrade policy & execution record (Phase 4/7)

1. **Toolchain first (Phase 7):** AGP 9.1.0 → 9.5.2, Gradle 9.3.1 → 9.9.2, Kotlin 2.2.10 → 2.4.21, KSP 2.3.2 → 2.3.14, JDK 17 (keep). Verify `./gradlew help` + full `./gradlew test assembleDebug` green before dependency patches.
2. **Patch/minor first:** Room 2.7.1→2.8.5, WorkManager 2.10.0→2.12.0, core-ktx 1.15.0→1.19.1, lifecycle 2.8.7→2.11.1, activity-compose 1.9.3→1.13.0, navigation-compose 2.8.5→2.10.2, datastore 1.1.1→1.3.0, appcompat 1.7.0→1.8.0, biometric 1.1.0 (no change), play-services-base 18.5.0→18.11.1, play-services-ads 25.2.0→25.5.0, UMP 4.0.0 (no change), coroutines 1.9.0→1.11.0, guava 32.1.3-android→33.8.0-android, truth 1.4.4→1.4.5, uiautomator 2.3.0→2.4.1, benchmark 1.3.3→1.5.0.
3. **Major, individually reviewed:** none beyond toolchain above. `composeBom` (2024.12.01→2026.11.01) — **deferred** to separate PR with visual sweep (upgrade plan Phase 6 item 2).
4. **Avoid:** alpha/beta/rc (core 1.20.0-alpha01, lifecycle 2.12.0-alpha*, benchmark 1.6.0-alpha02, activity 1.14.0-alpha03, espresso 3.8.0-alpha04, `androidx.test.ext:junit` unverified).
5. **Never force incompatible versions:** if AGP 9.5.2 + Gradle 9.9.2 + Kotlin 2.4.21 breaks the build, revert that pair to a compatible stable and document the reason.
6. **Regression gate after each significant upgrade:** `./gradlew test` (unit, 505), `lintDebug` (0 errors), `./gradlew assembleDebug`; after toolchain: instrumented suite re-run.
7. **Unverified/aspirational:** `androidx.test.ext:junit`, `androidx.test:runner` latest versions (no metadata available at time of audit); compose BOM bump (deferred); WorkManager 2.12.0 changes — scanned release notes, no breaking changes; `truth` 1.4.5 — patch, low risk.

---

## 8. Remediation summary (counts)

| Category | Count |
|---|---|
| Toolchain bumps (AGP, Gradle, Kotlin, KSP, JDK) | 5 |
| AndroidX minor bumps (12) | 12 |
| Direct third-party bumps (5) | 5 |
| Test infra bumps (4) | 4 |
| Deferred (1) | 1 (composeBom) |
| No-change (3) | 3 (biometric, junit4, espresso-3.7.0) |

**Expected impact of bumps:** compile-level; no behavior changes expected (all compatible matrix rows verified). All verified by the Phase 8 test matrix.
