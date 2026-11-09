# Android 17 (API 37) upgrade plan — CycleCare

Status: **proposed, not started.** Prepared 2026-11-09 against commit `8e3b69e`.
Owner actions are marked **[OWNER]**; everything else is repo work.

---

## 0. Why, and why now

- Android 17 (API 37, "Cinnamon Bun") shipped 2026-06-16 (Pixel first). It is
  stable; API 37.1 (QPR1) and 37.2 add no target-side work.
- Google Play policy, as of today: new apps and updates must target **API 36**
  (since 2026-08-31; extension to 2026-11-01 requestable). Per Google's
  Android 17 announcement (Feb / Jun 2026), Play is expected to require
  **API 37 in August 2027**. The formal Play Help text is not yet published
  (verified 2026-11-09), so **verify in Play Console → Policy status before
  shipping** **[OWNER]**.
- Related Play policy dated 2026-08-25: apps targeting API 37+ that use
  `ACCESS_LOCAL_NETWORK` must file a Play declaration. **Not applicable —
  this app makes no LAN connections.**
- Doing the upgrade now, rather than August 2027, leaves the full alpha →
  pre-launch report → staged rollout runway.

Recommended target: ship a targetSdk 37 build to production by **June 2027**,
with July as buffer.

---

## 1. Current state (verified 2026-11-09)

### Versions

| Component | Value |
|---|---|
| `compileSdk` — `:app` | 36 |
| `compileSdk` — 13 Android modules at 35: `benchmark`, `core:{database,designsystem,security,data}`, `feature:{onboarding,home,calendar,logging,insights,settings,privacy,export}` | **35 ← inconsistent, must all become 37** |
| `targetSdk` (app) | 36 |
| `minSdk` | 26 (keep) |
| AGP | 9.1.0 (Android 17 SDK needs ≥ 8.9.0-rc01 — satisfied) |
| Kotlin | 2.2.10, KSP 2.3.2 |
| Gradle | 9.3.1 (wrapper), CI pins JDK 21, module toolchain JDK 17 |
| Compose BOM | 2024.12.01 (old; not an API 37 blocker) |
| Espresso / androidx.test | **3.7.0 / 1.7.0–1.3.0** (bumped 2026-10-19, *after* the "API 37 breaks Compose tests" note was written) |
| `versionCode` / `versionName` | working tree has uncommitted bump 7→8 / 1.0→2.0 ("release prep for 2.0", user's work — untouched by this plan) |
| CI | `ci.yml` instrumented job = API 36, advisory (`continue-on-error`); `release.yml` unchanged |

### Tooling already in place locally

- Platform **android-37.0** installed; build-tools **37.0.0** installed.
  platform-tools update to 37.0.1 available (install it).
- API 37 emulator images available in the SDK repository:
  `system-images;android-37.0;google_apis;x86_64` (for CI runners), plus
  `aosp_atd` and `google_apis_playstore_ps16k` x86_64; ARM64 images installed
  locally (Apple Silicon).
- **Only AVD present locally is `Pixel_10a`** — the `cc36` AVD referenced by
  the runbook no longer exists. The runbook must be updated as part of this
  work, not just its comments.

### App-specific behavior surface (grep-verified)

| Area | Finding | API 37 action |
|---|---|---|
| Orientation / resizability locks (`screenOrientation`, `resizeableActivity`, aspect ratios, `setRequestedOrientation`) | **None anywhere** — the Android 17 "opt-out removal" on sw ≥ 600 dp is formally a no-op for this app | Large-screen *experience* still untested — Phase 3 |
| App widgets / `RemoteViews` custom content views | None — reminders post standard `BigTextStyle` notifications | Notification memory limit: **N/A** |
| WebView | None | WebView UA reduction: **N/A** |
| LAN / local-network access | None (only AdMob / UMP over HTTPS) | `ACCESS_LOCAL_NETWORK`: **N/A** |
| SMS permissions | None | OTP delay changes: **N/A** |
| Contacts provider | None | CP2 PII / SQL enforcement: **N/A** |
| Bluetooth | None | RFCOMM `read()` → `-1`: **N/A** |
| Android Keystore keys | **Zero** — vault uses PBKDF2-with-HMAC-SHA-256 derived keys held in memory (`core:security`) | 50 000-key per-app limit: **N/A** |
| Reflection on `MessageQueue` / `static final` / `setAccessible` | **None found** in src | MessageQueue lock-free + unmodifiable `static final`: verify by test, not by edit |
| Background audio | `MediaPlayer` previews are user-initiated and foreground-only; reminders use notification channel sound, never a service playing media | Background-audio hardening: keep as-is; do **not** add background playback without a while-in-use FGS |
| TLS | `network_security_config.xml`: `cleartextTrafficPermitted="false"`, system CAs only, **no pinning** (deliberate) | Certificate Transparency now on by default — smoke-test consent + ad load; no pinning = nothing to break |
| `usesCleartextTraffic` | Not set (already on the end state Android 18 will mandate) | Keep the `network_security_config.xml` as the single source of truth |
| Implicit URI grants | `ExportScreen.kt` uses `FileProvider` + explicit `FLAG_GRANT_READ_URI_PERMISSION` | Compliant already (Android 18 will restrict implicit grants — good hygiene) |
| Export / share | `ACTION_SEND` / `ACTION_VIEW` with explicit read grants | Keep explicit flags — do not regress |
| Exact alarms | `SCHEDULE_EXACT_ALARM` + `setExactAndAllowWhileIdle` for PeriodPrep / LatePeriod tiers; `ReminderRescheduleReceiver` re-arms on `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | No Android 17 change; re-verify the **open** Play exact-alarm declaration (`play-console/data-safety-unresolved.md`) |
| `setContentCaptureEnabled` | Not used | **N/A** (pass on app-lock screens: if ever hidden from capture, use `FLAG_SECURE`, not this deprecated API) |
| Native code / `System.load` | None — pure Kotlin/Java | 16 KB pages: **N/A**; Safer-DCL: **N/A** |

### The one real unknown — Compose UI tests on API 37

`ci.yml` ("WHY API 36 AND NOT 37") and `CycleCare-Redesign-Docs/23-verification-runbook.md`
say every Compose UI test fails on API 37 with
`NoSuchMethodException: android.hardware.input.InputManager.getInstance()`.
That note dates to 2026-10-17 and was written against **Espresso 3.6.1**.
The 2026-10-19 bump moved this repo to **Espresso 3.7.0**, whose release notes
say exactly this bug is fixed ("Use `getSystemService` instead of reflective
`InputManager.getInstance`"). Gradle resolution also picks this repo's
direct 3.7.0 over `ui-test`'s transitive 3.5.x. The documented claim is
almost certainly **stale but unproven** — and "the docs didn't get updated"
cuts both ways. Phase 4 re-verifies it on a real API 37 AVD before anything
is flipped.

---

## 2. Open questions for the owner (resolve before starting) **[OWNER]**

1. **Ship as part of 2.0, or a 2.1 follow-up?** The working tree already bumps
   `versionCode 8 / versionName 2.0`. If 2.0 ships without API 37 support that
   is fine (Play requires 36 now); the API 37 build then becomes 2.1. Decide
   before Phase 1 so version bumps don't interleave with the uncommitted
   release-prep change.
2. **Track strategy**: internal/alpha first, or straight to production staged
   rollout? Recommendation: internal early access → production staged
   20% → 50% → 100%.
3. **Scope of large-screen work**: (a) verify-no-breakage only, or
   (b) adopt Material 3 adaptive patterns (`NavigationSuiteScaffold` /
   list-detail) for calendar/insights/home/settings. Recommendation: (a) now,
   (b) as a fast-follow — see Phase 3.
4. **Compose BOM**: keep 2024.12.01, or bump to current (e.g. 2026.06.01, which
   pulls `ui-test` 1.11.4) in the same change? Recommendation: separate commit,
   visual sweep required; not required for API 37.

---

## 3. Phases

### Phase 0 — Guardrails (½ day)

- Branch `android-17-api37` off `main`; record baseline on `8e3b69e` (+ the
  owner's version bump commit if it lands first).
- `./gradlew test` — expect 505 pass.
- `./gradlew connectedDebugAndroidTest` on API 36 — expect 268, 2 skipped
  (needs an API 36 AVD; recreate `cc36` or use any API 36 image; numbers from
  the 2026-10-20 audit are the baseline, but do not trust them blindly).
- `./gradlew assembleRelease` — confirm the R8 build is green *before* any
  change, so Phase 1 regressions are attributable.
- `sdkmanager` installs: `platforms;android-37.0` (have), `build-tools;37.0.0`
  (have), `platform-tools` (upgrade to 37.0.1), and — for CI only —
  `system-images;android-37.0;google_apis;x86_64` is installed by the emulator
  action automatically.
- Local AVD creation is part of Phase 4 (need it there anyway).

### Phase 1 — Build configuration (½–1 day)

Edit, in one commit unless review says otherwise:

1. `app/build.gradle.kts`: `compileSdk = 37`, `targetSdk = 37`, keep `minSdk = 26`.
2. Same edit in the 13 modules listed in §1 that say `compileSdk = 35` → `37`.
   `benchmark/build.gradle.kts` too (its `minSdk = 29` stays; it is an
   `com.android.test` module).
3. `gradle/libs.versions.toml`:
   - leave `composeBom` as-is for this change (optional follow-up bump);
   - add an explicit `espresso-core = 3.7.0` constraint if we ever drop the
     direct `androidTestImplementation` in a module — keep one place doing the
     pinning, comment why, referencing this file and the runbook;
   - remove or adopt the unused `material3Adaptive = "1.0.0"` entry depending
     on the Phase 3 decision (question 3).
4. Do **not** change: `minSdk`, the `jvmToolchain(17)` /
   `JavaVersion.VERSION_17`, `android.sdk.defaultTargetSdkToCompileSdkIfUnset=false`,
   R8 config, or `isShrinkResources=false`.
5. Add explicit CI SDK installation to `.github/workflows/ci.yml` and
   `.github/workflows/release.yml` (the runner image's platform set moves;
   `compileSdk 37` must not depend on what the image happens to ship):

   ```yaml
   - name: Install Android 17 platform
     run: |
       yes | sdkmanager --licenses >/dev/null || true
       sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
   ```

6. Verify: `./gradlew assembleDebug assembleRelease` — release build runs
   `lintVitalRelease`; any new 37-related lint error is a Phase 1 blocker.
7. Expected outcomes: zero behavior change; the app installs on API 36 devices
   exactly as before; `targetSdk=37` changes nothing at runtime until the app
   runs on an Android 17 device.

**Commit boundary:** "Compile against API 37, still target 36" is a valid
incremental step if you want to de-risk: land Phase 1 with `targetSdk = 37`
*held back*, soak, then a one-line commit flips `targetSdk`. Only do the
held-back variant if Play timing demands it — otherwise flip both in Phase 1
and verify in Phase 4 before merge.

### Phase 2 — Behavior-change audit closure (½ day)

Walk the two official pages (linked below) against §1's table and close each
row: either "verified not applicable (grep evidence)" or a code change.

- Apps targeting 37:
  <https://developer.android.com/about/versions/17/behavior-changes-17>
- All apps on 17:
  <https://developer.android.com/about/versions/17/behavior-changes-all>

Rows needing action beyond verification, on this codebase:

1. **Lock-free `MessageQueue`** — no reflection found. No code change;
   note the escape hatch for debugging only:
   `adb am compat disable USE_NEW_MESSAGEQUEUE <pkg>` (do not ship reliance
   on this). Reminder: `mMessages` is always null under the new implementation —
   any future Espresso-like tooling that peeks at it will lie silently.
2. **Certificate Transparency on by default** — `ConnectivityManager` /
   `HttpEngine` not used; TLS is AdMob + UMP only. Action: on an API 37
   emulator with Google Play, complete the UMP consent flow, confirm an ad
   request succeeds, and confirm failure modes are handled gracefully (app
   must not crash when offline/consent-denied). No cert pinning exists to
   update.
3. **Background audio (37 tier is stricter)** — keep the reminder preview
   path foreground-only (it is); add a short comment in `ReminderChannels.kt`
   noting playback must never move into a service without a while-in-use
   foreground service, per the Android 17 restriction.
4. **RAM-based app memory limits (all apps)** — establish a baseline: cold
   start RSS and time via `benchmark/` macrobenchmark on API 37; capture
   `adb shell dumpsys meminfo com.cyclecare.tracker` after a reminder-heavy
   session; store numbers in the dated evidence note (Phase 5). If the app is
   anywhere near the ceiling, `low`-importance cached processes are killed to
   enforce it — the app should show a clean restart, not corruption (Room +
   DataStore already make this safe; re-run the migration suite to be sure).
5. **IME default visibility after rotation** (all apps) — manual check on an
   API 37 device/emulator: logging screen, keyboard open, rotate — IME and
   focus must behave predictably.
6. **Global keyboard shortcuts** (`Meta+Back`, all apps) — app defines no
   shortcuts; verify none of the Compose shortcut handling collides. N/A but
   spot-check on a desktop/ChromeOS-style window if available.
7. **Everything else** — N/A per §1 with grep evidence; re-run the greps at
   review time (`screenOrientation`, `MessageQueue`, `setAccessible`,
   `RemoteViews`, `WebView`, `KeyStore`, `ACTION_SEND` flags).

### Phase 3 — Large screens (2–4 days, the only user-visible work)

Formally: this app declares **no** orientation/resizability/aspect-ratio
restrictions, so the Android 17 opt-out removal changes nothing mechanically.
Practically: the app has never been validated at sw ≥ 600 dp, and Play's
pre-launch report and tablet users will judge it there.

Minimum bar (this upgrade):

- **Resize + rotate matrix on API 37**: sw 600 dp (7" tablet), sw 672+ (foldable),
  sw 800 dp (10" tablet), landscape and portrait, plus freeform multi-window
  if the emulator supports it. Screens × themes × font scale 1.3 — at least
  onboarding, home, calendar, insights, logging, settings, privacy, export.
- **State survival**: rotation and window resize must not lose in-progress
  entry (logging form), app-lock gate state, PIN dots. Fix with savedState /
  rememberSaveable where broken.
- **Containment**: stretch the phone-width layouts with a max content width
  (e.g. 600–840 dp) and center; ensure charts and calendar grid are not
  edge-stretched absurdly; keep touch targets ≥ 48 dp.
- **`navigation rail`/`suite` decision**: if any layout is obviously broken on
  tablets, adopt `NavigationSuiteScaffold` (Material 3 adaptive) behind the
  unused catalog version instead of one-off scaffolding. If everything passes
  modest containment, defer Phase 3b (two-pane calendar → day detail) as a
  fast-follow and record it in the doc that tracks this plan.
- Do **not** add `screenOrientation` / portrait locks as a "fix" — they are
  ignored on sw ≥ 600 dp under API 37 anyway, and Play treats locks as a
  negative quality signal for large screens.

Verification: Compose screenshots are manual/visual in this repo (no
Roborazzi); check on the tablet AVDs, capture evidence, and get the
Play pre-launch report on a physical/tablet track before rollout.

### Phase 4 — Test infrastructure (1–2 days)

**Unblock API 37 verification.** Steps in order:

1. Create AVD `cc37` — Android 17, API 37.0, Google APIs image, x86_64 on CI /
   ARM64 locally; 320×640 @ 160 dpi if it keeps sizes comparable with the
   `cc36` data, plus one tablet AVD for Phase 3. Keep `ANDROID_SERIAL` usage
   from the runbook.
2. `ANDROID_SERIAL=<cc37> ./gradlew connectedDebugAndroidTest --continue`.
   Split results into (a) Compose UI suites and (b) non-Compose suites
   (core:database migration, core:data, feature:onboarding).
3. **If all green (expected, per Espresso 3.7.0 release notes):**
   - Update `ci.yml`: `api-level: 37` in the instrumented job (keep
     `continue-on-error: true` and x86_64 `google_apis`; install
     `platform-tools` too), and **replace the stale "WHY API 36 AND NOT 37"
     comment** with the actual outcome, date, commit and links to the
     Espresso 3.7.0 note.
   - Update `docs/release-checklist.md` ("API 37 emulator automation is
     non-gating until the AndroidX test stack supports it" — no longer true)
     and `23-verification-runbook.md` (cc37 procedure, serial handling;
     **do not delete** cc36 references — annotate as the historical baseline;
     the runbook is explicitly the calibration record).
   - Keep the job advisory until two consecutive green CI runs, then consider
     gating. Do not gate in this change.
4. **If Compose suites still fail:** keep `connectedDebugAndroidTest` split —
   non-UI modules on API 37, Compose modules on API 36 (documented exception,
   with the failure filtered to the Espresso/InputManager signature), file at
   android-test/compose, and pin `espresso-core:3.7.0` explicitly in every
   module's androidTest deps. Revisit on the next Compose BOM bump.
5. Re-run the **Doze** verification (`19-doze-verification.md`) on `cc37`:
   Tier 1 `setExactAndAllowWhileIdle` delivery, Tier 2
   `setAndAllowWhileIdle`, reboot reschedule, timezone change — Doze behavior
   is the one platform-interaction most likely to shift beneath the reminder
   design. Record results as a dated evidence section; do not edit the
   original October evidence, supersede it.
6. Confirm backup / restore (`dataExtractionRules.xml` excludes storage
   domains) still behaves on API 37.
7. Unit tests (`./gradlew test`) must stay 505/505 on the new toolchain.

### Phase 5 — Release & store readiness (1 day + owner actions)

- Versioning: implement the owner decision (Q1). If targeting 37 is part of
  2.0: bump the working-tree `defaultVersionCode`/`defaultVersionName` work
  into that release; otherwise `versionCode 9 / versionName 2.1.0`.
- `./gradlew assembleRelease` + `scripts/validate-aab.sh` +
  `scripts/check-release-version.sh` as usual; verify the AAB against
  bundletool structural validation exactly as the 2.0 flow does.
- Docs to update (this upgrade, same PR or follow-ups):
  - `docs/release-prep/owner-actions.md` §6: "Target API 37, min 26".
  - `docs/release-checklist.md`: API 36/37 wording (see Phase 4).
  - `docs/release-readiness.md` / `CycleCare-Redesign-Docs/20|22-*.md`: add a
    **dated amendment note** for the 37 verification rather than rewriting the
    historical ledger — the repo's convention is dated ledger entries.
  - New evidence note, e.g. `CycleCare-Redesign-Docs/26-android-17-verification.md`:
    environment (AVD/image), command transcripts summary, test counts, Doze
    results, large-screen sweep results, screenshots.
- Play Console **[OWNER]**:
  - Policy status page: confirm no API 37 requirement date is set for August
    2027 yet; note the confirmation date in the evidence note.
  - Exact-alarm declaration: currently **open**
    (`play-console/data-safety-unresolved.md` §unresolved) — submit alongside
    this release; unchanged by API 37.
  - Re-submit Data Safety only if the manifest changes — it should not; if it
    does, re-check `data-safety-audit.md` line references (they point at
    manifest lines by number).
  - Health declaration (cycle tracker): unchanged.
  - Closed/open testing → pre-launch report **must include API 37 devices**;
    check for new ANR/crash classes under the RAM-based memory limits.
- Post-release: watch **Android vitals** on API 17 devices weekly for the
  first month (ANR, lmkd kills, notification failures); feed findings back
  into the evidence note.

### Phase 6 — Post-release / tidy-up (½ day)

- Delete the `material3Adaptive` catalog entry if unused, else wire it.
- Consider the Compose BOM bump as its own PR with a screenshot sweep.
- Decide whether `cc36` remains in the local AVD set (yes, until the 37 lane
  is boring).
- Remove/annotate the "API 36 is also the current Play target" sentence in
  `ci.yml` — after this ships it describes a superseded requirement.

---

## 4. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Compose UI tests still red on API 37 | Low–medium | Verification evidence weak; job stays advisory | Phase 4 split-lane approach; upstream report; Compose BOM bump PR |
| Large-screen stretch/overlap complaints | Medium | Pre-launch report flags; tablet users bounce | Phase 3 matrix + containment; tablet track in Play |
| UMP/AdMob CT or ECH edge failure on some carriers | Low | Consent screen or ad fill failure for a slice of users | Staging smoke test on API 37; keep UMP current; no pinning to break |
| `targetSdk=37` breaks something no test covers (e.g. Doze subtlety) | Low | Reminder delivery regressions in prod | Doze re-verification + alpha track first |
| Release build lint failure on new 37 deprecations | Medium | CI red at Phase 1 | Fix forward; do not suppress new lint checks |
| Play "API 37 required" arrives with less runway than expected | Low (Aug 2027 announced) | Rushed Android 17 work | Ship by June 2027; check Policy status monthly |
| 16 KB page-size requirement interaction | None (no native code) | — | Confirmed N/A |
| Uncommitted version-bump (7→8) conflicts with upgrade branch | Medium | Rebase pain, accidental version regression | Owner decides Q1 first; land that change or stash it before branching |

---

## 5. Suggested commit sequence

1. `Compile against compileSdk 37, hold targetSdk` *(optional de-risk step)*
2. `Unify all modules on compileSdk 37`
3. `Install platform 37 explicitly in CI`
4. `Flip targetSdk to 37` + Phase 2 audit doc reference
5. `Add large-screen containment and resize-safe state`
6. `Verify on API 37: instrumented suite + Doze + update runbook`
7. `Enable API 37 lane in CI (advisory), rewrite stale comment`
8. `Bump version for the API 37 release; update store docs`
9. Evidence note + ledger amendment

---

## 6. Estimation

| Phase | Effort |
|---|---|
| 0 Guardrails | ½ day |
| 1 Build config | ½–1 day |
| 2 Behavior audit closure | ½ day |
| 3 Large screens | 2–4 days (containment only; +2–4 for two-pane) |
| 4 Test infrastructure | 1–2 days |
| 5 Release & store | 1 day |
| 6 Tidy-up | ½ day |

Total: roughly **5–9 engineering days**, well inside the runway to an August 2027
Play requirement if it holds to the announced schedule.

---

## 7. Phase 0 / Phase 1 execution record — 2026-11-09

Executed on branch `android-17-api37` at `8e3b69e`. **No phase 2 work was done.**
`targetSdk` remains 36; `versionCode`/`versionName` remain 8 / 2.0 (the owner's
uncommitted 7 → 8 / 1.0 → 2.0 bump is preserved, untouched). `minSdk` unchanged (26; 29 in `:benchmark`).

### Phase 0 verification results

- **Baseline:** `./gradlew test` — 505 tests, 0 failures/errors/skipped
  (302 `testDebugUnitTest` + 203 pure-JVM). `./gradlew assembleDebug` — BUILD SUCCESSFUL.
- **SDK:** platform `android-37.0` and build-tools `37.0.0` installed;
  platform-tools 37.0.0 installed (37.0.1 available, not applied — cosmetic, out of scope).
  `android-37.0` emulator images: ARM64 Google APIs + Google Play 16 KB ARM64 installed
  locally; x86_64 `google_apis` / `aosp_atd` / `google_apis_playstore_ps16k` present in the
  SDK repository for CI runners (not installed locally; ARM-host machine).
  API 37.1 / 37.2 platform artifacts exist but are **not** required — API 37.0 is the level to build/test.
- **Espresso 3.7.0 / API 37 investigation (clarified, not resolved):**
  the "Compose UI tests fail on Android 37" notes in `.github/workflows/ci.yml` and
  `23-verification-runbook.md` date from 2026-10-17 and were written against Espresso 3.6.1.
  This repo moved to Espresso **3.7.0** / androidx.test runner 1.7.0 / ext-junit 1.3.0 on
  2026-10-19 (commit `37a87fc`); Espresso 3.7.0 replaced the reflective
  `InputManager.getInstance()` call with `getSystemService`. Gradle resolves the direct
  3.7.0 over `ui-test`'s transitive 3.5.x/3.6.x. **Nothing here re-runs the instrumented
  suite on API 37 — Compose UI tests are still unverified on Android 17.** That is Phase 4.
- **AVD inventory changed:** the `cc36` AVD referenced by the runbook no longer exists on
  this machine (`emulator -list-avds` shows only `Pixel_10a`). Phase 4 must create `cc37`.
- **Exact-alarm Play Console declaration is a separate, still-open release dependency**
  (`play-console/data-safety-unresolved.md`): submit the `SCHEDULE_EXACT_ALARM` justification
  alongside the 2.0 release — it is independent of the API 37 upgrade and must not block it.
  It remains open as of this record.

### Phase 1 changes (compileSdk 37 only)

- `gradle/libs.versions.toml`: new `androidCompileSdk = "37"` — the single source of truth
  for all 14 Android modules (`:app`, `:benchmark`, `:core:{data,database,designsystem,security}`,
  `:feature:{calendar,export,home,insights,logging,onboarding,privacy,settings}`).
  Prevents SDK drift between modules.
- All 14 modules: `compileSdk = libs.versions.androidCompileSdk.get().toInt()` (was 35 × 13, 36 × 1).
- No other dependency, plugin, or configuration changes. AGP 9.1.0 / Kotlin 2.2.10 / KSP 2.3.2 /
  Gradle 9.3.1 unchanged. `benchmark` keeps `minSdk = 29`.
- Verified post-change: `./gradlew help`, `./gradlew assembleDebug assembleBenchmark compileDebugAndroidTestKotlin`,
  `./gradlew test` (505/505 green), `./gradlew lintDebug` (0 errors; 4 pre-existing warning themes,
  plus one expected new `OldTargetApi` warning on `targetSdk = 36`, which is deliberate).
- Library AAR metadata reports `minCompileSdk=37` after the change; app merged manifest still shows
  `versionCode="8" versionName="2.0" minSdk="26" targetSdk="36"`.

### Still outstanding before Phase 2

- `targetSdk = 37` flip (decision: **ships in 2.1**, not 2.0).
- Phase 2: Android 17 behavior-change audit closure (see §3 Phase 2).
- Phase 4 must re-verify Compose UI tests on `cc37` before any 37 emulator lane is trusted —
  do not reuse the pre-Espresso-3.7.0 conclusion either way.

---

## 8. Phase 2 execution record — 2026-11-09 (late) / 2026-11-10

Branch `android-17-api37`, off `8e3b69e` (+ uncommitted 2.0 version bump, untouched).
`targetSdk` = 36 throughout. One source file changed (`ReminderChannels.kt`, comment only).

### Environment used for runtime verification

| | |
|---|---|
| AVD | `Pixel_10a` (`Pixel_10a_3.avd`) — **Android 17, API 37.0, Google APIs Play Store, 16 KB page size** |
| Architecture | arm64-v8a; 1080 × 2424; 420 dpi; 2048 MB RAM; hw.keyboard=yes |
| SDK packages | platforms/android-37.0 ✓, build-tools 37.0.0 ✓, ARM64 `google_apis_playstore_ps16k` image |
| Network | Validated Wi-Fi + LTE; 8.8.8.8 reachable |
| App | debug build of this branch installed via `:app:installDebug` |

**The API 37 emulator rebooted itself once during rotation testing** (guest OS restarted at
00:10:44; uptime reset; `BootAnimation` re-logged; "bad color buffer handle" GPU errors in the
emulator log). This looks like an image/driver stability issue on the 16 KB-page ARM64 Play
Store image, not an app defect: the process was killed by the reboot, never by the app; the two
`FATAL EXCEPTIONs` in logcat at 00:10:46/00:10:59 are from the `uiautomator dump` helper itself
(`UiAutomationShellWrapper`, `registerUiTestAutomationService` on a dying accessibility manager).
**Room durability across the reboot was verified**: after cold relaunch the app went straight to
Today with the profile intact ("Next period in 23 days", Avg cycle 28 days). Treat this emulator
image as fragile; Phase 4 should prefer x86_64 `google_apis` on Intel/AMD runners or the ARM64
`aosp_atd` image and repeat the rotation probes.

AVD global settings were changed for probing and approximately restored afterwards
(`show_ime_with_hard_keyboard=1` for display of the soft keyboard on a hw.keyboard AVD, back to 0;
`accelerometer_rotation` 1; `user_rotation` 0 in parentheses — original values were not recorded,
do not treat as exact).

### Compatibility findings (verified)

| Area | Verdict | Evidence |
|---|---|---|
| MessageQueue lock-free | **Verified safe** (no reflection anywhere in `src/main`; only `BackHandler` grep hit) | §1 greps re-run at audit time |
| `static final` unmodifiable | **Verified safe** — `setAccessible` = 0 occurrences repo-wide | grep |
| Reflection on Espresso (instrumented only) | **Confirmed root cause for API 37 instrumented failures** — see below | dependency graphs |
| Orientation / resizability locks | None declared anywhere; **no opt-out can exist** (nothing to opt out) | manifests + grep |
| Notification custom views / RemoteViews / widgets | None — std `BigTextStyle` only; VISIBILITY_PRIVATE + publicVersion set | source + manifests |
| Local network access | None; no LAN code, no OkHttp/Ktor; only AdMob/UMP HTTPS | deps, source |
| Android Keystore | **Zero keys created**; vault is PBKDF2-HMAC-SHA256 in memory | `core:security` |
| Background execution / FGS | No FGS; WorkManager's `SystemForegroundService` non-exported; reminders are alarms + notifications, not audio sessions | merged manifest, source |
| Exact alarms & notification perms | All correct: immutable PendingIntents, allowlist-filtered exported receiver, `canScheduleExactAlarms()` checked before Tier 1, grant/revoke re-arms via `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`; POST_NOTIFICATIONS requested and granted correctly on API 17 | source; runtime flow |
| Exported components & URI grants | Release merged manifest free of debug-only `PreviewActivity`/`ComponentActivity`; only launcher activity, allowlisted `ReminderRescheduleReceiver`, signature-protected WorkManager/diagnostics receivers; export uses explicit `FLAG_GRANT_READ_URI_PERMISSION`; FileProvider non-exported, paths narrowed to `exports/` | manifests, source |
| Cleartext traffic | `network_security_config.xml` denies cleartext everywhere, system CAs only, no pinning (deliberate) | unchanged |
| Certificate Transparency (default on 37) | **Runtime-verified**: UMP consent flow completed, MobileAds initialized on IO (`CycleCareAdsConsent: mobile_ads_initializing` in logcat), and a Google test adaptive banner **loaded and rendered** on the Today screen — an actual HTTPS ad-request succeeded under CT | emulator + screenshots |
| ECH | N/A — no `HttpEngine`/OkHttp; system-default TLS only; no pinning | deps |
| RAM-based memory limits | Footprint measured far below any ceiling (see below) | runtime |
| IME default visibility after rotation | Matches documented behavior: keyboard hides on rotation; focus retained; no crash; re-tap reopens | runtime probes |
| Global keyboard shortcuts | App defines none | source |
| Self-broadcasts / loopback / keystore limits / SMS OTP / contacts / BT RFCOMM / WebView / NPU / Native-DCL / CP2 | All **N/A** (no such code paths) | greps + deps |
| `usesCleartextTraffic` | Not set (already the Android 18 end-state) | manifest |
| Exact-alarm Play declaration | **Open dependency, separate release track** — unchanged by this phase | `data-safety-unresolved.md` |
| Background audio (37 tier) | Preview path foreground-only; comment added to `ReminderChannels.kt` guarding it; audio session self-terminated (~1.6 s measured), 0 lingering sessions after force-stop | source + `dumpsys media.audio_flinger` |
| Onboarding flash on configuration change | **Confirmed defect (pre-existing, NOT fixed here): on activity recreation the app renders `OnboardingRoute` for ~2–4 s before the Room flow emits the profile, on every rotation. Data is never lost; the Log draft state survived one rotation.** With Android 17's resize regime this flicker will be frequent on large screens. Fix = tri-state profile + neutral container; needs owner approval (touches onboarding/test surface). | two rotations observed |
| `OldTargetApi` lint warning | Expected — `targetSdk` deliberately 36 | lint |

### Code changes made

- `app/src/main/java/com/cyclecare/app/ReminderChannels.kt` — added a background-audio
  restriction comment at `playPreview` (the single source-code change; mandated by Phase 2
  plan item 3). No behavior change.

### Build / test / lint after the change

- `./gradlew :app:installDebug` — installed on Pixel_10a (API 37).
- Cold start: 1285 ms (first), 5.4 s (relaunch after reboot, post-reboot ART jitter).
- `./gradlew test` — **505/505 pass** (4 tasks executed, rest up-to-date).
- `./gradlew lintDebug` — **0 errors** (same warning set as Phase 1 + expected `OldTargetApi`).
- Instrumented, on API 37 AVD (not a CI-lane change; local runs only):
  - `:core:database:connectedDebugAndroidTest` — **34/34 pass** (migration suite intact on 37)
  - `:core:data:connectedDebugAndroidTest` — **36/36 pass**
  - `:feature:onboarding` — persistence test **1/1 pass**; `OnboardingScreenUiTest` **6/6 FAIL**

### Root-caused instrumented-test failure (was unexplained; not yet Phase 4's fix)

`OnboardingScreenUiTest` fails with exactly the documented signature:
`NoSuchMethodException: android.hardware.input.InputManager.getInstance()`
(`androidx.test.espresso.Espresso.onIdle`). Unlike `:app` (which pins
`androidTestImplementation(libs.androidx.espresso.core)` = **3.7.0**), the feature modules
declare **no direct espresso dependency**, so `ui-test-junit4:1.7.6` (Compose BOM 2024.12.01)
pulls **espresso-core 3.5.0** transitively via `ui-test-junit4-android:1.7.6`. Gradle takes the
highest available version only when a direct/constraint pin exists — it does not here.

**Phase 4 fix (do not implement before Phase 4 approval):** add
`androidTestImplementation(libs.androidx.espresso.core)` (3.7.0, already in catalog) to the
13 Android modules' androidTest dependencies — or bump Compose BOM; then re-run all Compose UI
suites on API 37. Do **not** trust either the old "every Compose test fails on 37" note or a
naive "it works" claim until this re-run happens; it is the deciding experiment.

### Memory measurements (API 37 emulator, debug build — conditions differ from production; do not treat as release baselines)

| State | TOTAL PSS | TOTAL RSS | Notes |
|---|---|---|---|
| Cold start idle (onboarding) | 193,285 KB | 370,432 KB | Views=13, 1 ViewRootImpl |
| Today (banner ad loaded) | 209,972 KB | 393,840 KB | banner adds ~17 MB PSS |
| After rotation cycles (Log screen) | 198,835 KB | 392,720 KB | no growth trend |
| Relaunch after reboot (Today) | 199,015 KB | 388,128 KB | — |
| During/after sound preview | 208,650 KB | 404,240 KB | Native heap 31.5 MB (≈29 MB pre-preview) |

Sound preview audio session: `dumpsys media.audio_flinger` shows one session from the app, process
duration ~1.6 s, and zero sessions after force-stop — preview releases cleanly.

No memory ceiling pressure observed under debug allocations. Release (R8) footprints will be lower.

### Network / privacy verification

- UMP consent: `requestConsentInfoUpdate` completed without crash; state machine logged
  `mobile_ads_initializing` (IO thread) — ads initialize only after `canRequestAds()`.
- Test banner rendered end-to-end on Today (Today is one of only two banner-eligible routes);
  consent deny / failure paths were **not** walked at runtime (no runtime failure injection
  performed — failure handlers inspected statically, they exist and log without crashing).
- Nothing weakens TLS; no pinning; no validation bypass. No exceptions added.
- Debug-only ad IDs in source are the Google test units; release uses prod IDs (unchanged).
- `dataExtractionRules.xml`/`backup_rules.xml`: cloud backup fully excluded; device transfer
  includes only database + datastore; matches privacy claims.

### IME and rotation

- Soft keyboard opens on field focus (with `show_ime_with_hard_keyboard=1` for this AVD's
  hw.keyboard setting); layout above the IME correct; Save entry stays reachable.
- Rotation: app recreates activity; **keyboard hides** (documented Android 17 default-visibility
  behavior); **no crash, no data loss, log drafts survive one rotation**; ~2–4 s onboarding
  flash (defect above); landscape layout functional (large-screen evaluation is Phase 3).
- IME dismissal via back gesture not explicitly exercised; not blocked either.

### Outstanding risks / deferred items

1. **Onboarding flash on recreation** — confirmed defect; owner decision for the tri-state fix.
2. **Compose UI tests on API 37** — empirically reproduce the reported failure for feature
   modules; root-caused; **Phase 4 experiment pending** (do not claim green yet).
3. **16 KB ARM64 Play Store image instability** (self-reboot during rotation) — environment
   risk for Phase 4; prefer x86_64 `google_apis` on CI.
4. UMP consent UI (form) never displayed on this image (region/config: no EEA message for this
   test setup) — form path + CT edge cases untested at runtime; static paths reviewed.
5. Release R8 + `lintVitalRelease` not run locally this phase (unchanged from Phase 1 note).
6. Export open/share (FileProvider) not exercised at runtime; static verification only.
7. Reminder end-to-end delivery on 37 (Doze session) — deferred to Phase 4 as planned.
8. `AppOps: attributionTag not declared` system warnings during audio playback (benign system
   log; attribution tags exist for privileged apps; playback worked).

### Recommendation

- **Phase 4 is unblocked and should proceed next**, with the espresso resolution fix as its
  first step; after it, the CI API 37 lane decision becomes evidence-based.
- Do **not** start Phase 3 implementation before the owner decides on the onboarding-flash fix
  (it affects the resize-heavy large-screen experience) and on Phase 3 scope (containment-only).
- Phase 5 (targetSdk=37 flip) must wait for Phase 4's instrumented suite outcome; the 6 failing
  UI tests are expected to pass with the espresso pin, but that is unverified.

---

## 9. Phase 4 execution record — 2026-11-09/10

Branch `android-17-api37`. Phase 4 goal: re-verify the instrumented suite on API 37, fix
test-harness incompatibilities, re-run Doze, confirm backup rules, record the evidence.

### What was done

1. **`cc37` AVD created** — `system-images;android-37.0;google_apis;arm64-v8a` (installed via
   sdkmanager; ARM64 host), `small_phone` profile with display forced to the calibrated
   **320x640 at 160 dpi** (same geometry as the historical `cc36`), `google_apis` tag, API 37.0.
   `disk.dataPartition.size` = 4 GB. `ANDROID_SERIAL=emulator-5554`.
   Note: x86_64 equivalent exists in the SDK repository for CI
   (`system-images;android-37.0;google_apis;x86_64`) and is what the CI job installs.
2. **Test-dependency fix (the Phase 2-root-caused blocker):** Compose-test modules
   `feature:{home,calendar,insights,logging,onboarding,privacy}` previously resolved
   `espresso-core:3.5.0` transitively (via `ui-test-junit4:1.7.6` → `ui-test-junit4-android`),
   which fails every Compose UI test on Android 17 with
   `NoSuchMethodException: android.hardware.input.InputManager.getInstance()`.
   Added `androidTestImplementation(libs.androidx.espresso.core)` (3.7.0, already in catalog)
   with an explanatory comment to each module; resolution now upgrades to 3.7.0.
   `:app` already pinned 3.7.0 directly.
3. **`RtlLayoutTest` had a stale hardcoded date** (`calendar_day_2026-10-21`; the selected day
   defaults to `LocalDate.now()`, so the test could only pass during October 2026).
   Now derives the tag from `LocalDate.now()`.
4. **Real layout defect found and fixed** (`feature/calendar/CalendarScreen.kt`): the calendar
   header `Row(spaceBetween)` let the month label (`displaySmall`) measure wider than the
   available width at small widths, clipping the **next-month button entirely out of
   composition** (zero bounds; unreachable). Fixed by giving the text column
   `Modifier.weight(1f)`. Fixed-size buttons always render; the label wraps inside the
   remaining width. Consistent with the codebase's own precedent for narrow grids.
5. **Full instrumented verification on `cc37`** (2026-11-09, after fixes):
   - `./gradlew connectedDebugAndroidTest --continue` — **BUILD SUCCESSFUL**
     **268 tests, 0 failures, 0 errors, 2 skipped** — identical totals to the API 36 calibration:

     | Module | Tests | | Module | Tests |
     |---|---|---|---|---|
     | app | 159 (2 skipped) | | feature/logging | 2 |
     | core:data | 36 | | feature:onboarding | 7 |
     | core:database | 34 | | feature:privacy | 10 |
     | feature:calendar | 6 | | feature:home | 12 |
     | feature:insights | 2 | | **Total** | **268** |

   - The two skipped are the opt-in `ReminderDozeDeliveryTest`, then run explicitly:
     **2/2 pass on API 37** — awake delivery 22s late, deep-idle delivery 45s late —
     identical latencies to the 2026-10-20 API 36 measurement.
   - Unit tests still **505/505**; `lintDebug` **0 errors**.
6. **Backup/restore on API 37 — runtime check not possible on the emulator:** `bmgr` switching
   to the local transport works, but `bmgr backup` fails with
   `Error: Could not get restore session for user 0` (no backup account/transport session on
   the AVD; the Google transport has no account either). `dumpsys backup` shows the framework
   and the app's backup agent register cleanly; nothing app-side misbehaves.
   **Static review remains green** (`dataExtractionRules.xml` excludes all cloud-backup
   domains; device transfer includes only `database/` and `file/datastore/`; API ≤30
   `backup_rules.xml` excludes everything; merged manifest carries
   `android:dataExtractionRules`/`fullBackupContent`). A real restore check still needs a
   physical device or the Play pre-launch report; tracked as a release dependency.
7. **Docs updated:** `ci.yml` instrumented lane now `api-level: 37` (advisory, x86_64
   `google_apis`, same libpulse/KVM notes; stale "WHY API 36 AND NOT 37" comment replaced with
   the actual root cause, fix, and verification record); `docs/release-checklist.md` API 37
   wording; runbook §1 (cc37 environment + historical cc36 calibration note), §3 (both-run
   timings), §7 (Doze measurements table, API 36 vs API 37 — identical latencies).

### Confirmed / not-confirmed

- ✅ Compose UI tests pass on API 37 (all 40) — empirically, in two full suite runs.
- ✅ Migration suite intact on API 37 (34/34).
- ✅ Reminder Doze behavior identical on API 37 (22s/45s).
- ✅ Room durability across reboot verified (app advanced from onboarding to Today with
  profile intact after an unclean emulator reboot — separate earlier finding).
- ❌ Restore path not exercised (see 6).
- ❌ Release build (R8 + lintVitalRelease) not re-run locally this phase (CI covers).
- ❌ Backup restore on real device pending (release dependency).

### Known issues discovered during Phase 4 (not Android 17 defects)

- Calendar month label clipped the next-month button at narrow widths — **fixed**
  (CalendarScreen.kt). If Compose behaviour changes here, watch this in large-screen sweeps.
- `connectedDebugAndroidTest` **uninstalls the app** on completion (runbook §10) — explains why
  the package is absent after a run; re-install before interactive probes.
- `cc37` ARM64+swiftshader instance shows GPU instability under load (repeated
  `bad color buffer handle` errors, `VkInstance` churn). The separate Pixel_10a 16 KB Play
  Store image definitely guest-rebooted once during rotation probing. Prefer x86_64/CI or
  `aosp_atd` ARM64 for repeated interactive sessions; suites themselves run reliably.

### Phase 4 conclusion

**The AndroidX test stack supports API 37** (with the espresso pin). The CI instrumented lane is
API 37, advisory; keep it non-gating until two consecutive green CI runs, then gate.

### What remains for Phase 5 (2.1)

- `targetSdk = 37` flip (decision: ships in 2.1), version bump `9 / 2.1.0`.
- AAB validation + Play Console Policy status check for the expected August 2027 API 37
  requirement; exact-alarm declaration submission (tracked separately, `data-safety-unresolved.md`).
- Physical-device Doze sign-off and backup-restore validation before the 2.1 release candidate.
- Reminder channel audio under background restrictions — re-verify no service-based audio path
  is introduced (the one Phase-2-adjacent note).
