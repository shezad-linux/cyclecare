# CycleCare verification runbook

The reproducible operational guide. Every command here was run during the
audit of 2026-10-20 at commit `d689a7e`, and the results quoted are the
results that came back.

For what each check *means* and what is still outstanding, see
`22-phase-completion-plan.md`, which is the canonical ledger. This
document is the "how to run it" half.

## Contents

1. [Environment](#1-environment)
2. [Quick reference](#2-quick-reference)
3. [The build and test commands](#3-the-build-and-test-commands)
4. [The static guards](#4-the-static-guards)
5. [Screenshots and the visual sweep](#5-screenshots-and-the-visual-sweep)
6. [Font scale, RTL and landscape](#6-font-scale-rtl-and-landscape)
7. [Doze](#7-doze)
8. [Procedures that cannot be automated](#8-procedures-that-cannot-be-automated)
9. [Restoring the device](#9-restoring-the-device)
10. [Known harness limitations](#10-known-harness-limitations)

---

## 1. Environment

| | |
|---|---|
| **AVD** | `cc36` — Android 16, API 36, 320x640 at 160dpi |
| **Serial** | `emulator-5556` |
| **JDK** | As configured by Gradle; CI pins the version in `.github/workflows/ci.yml` |
| **Source of truth** | `ANDROID_SERIAL=emulator-5556` must be set for every `adb` and Gradle command below |

**Do not create another AVD.** The `cc36` image is the one the whole
verification set is calibrated against, and the numbers in
`19-doze-verification.md` and `21-phase-5-6-acceptance-matrix.md` were
taken on it.

**API 37 is broken for this suite.** A `Pixel_9_Pro` AVD on API 37 is
present at `emulator-5554` and it must not be used. Compose
instrumentation fails there with:

```
java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance
```

Always pass `ANDROID_SERIAL=emulator-5556`.

**Every command below is run from the repository root**, `/Users/shezad/period`.

---

## 2. Quick reference

| Check | Command | Expected |
|---|---|---|
| Unit tests | `./gradlew test` | 333 tests, 0 failures, 0 skipped |
| Instrumented tests | `ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest` | 166 tests, 0 failures, 2 skipped |
| Ad-targeting guard | `./scripts/guard-no-ad-targeting.sh` | `OK: no ad-targeting API found.` |
| Typography guard | `./scripts/guard-typography-roles.sh` | `All 15 typography roles are defined and referenced.` |
| Contrast | `python3 scripts/contrast-report.py` | `All pairs with a WCAG target meet it.` |
| Release build | `./gradlew assembleRelease` | `BUILD SUCCESSFUL` |
| Release lint | `./gradlew :app:lintRelease` | no errors |
| Corpus gates | `./gradlew :core:domain:test` | 8 of 13 gates exist; see §3.3 |
| Migration tests | `ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest` | 20 tests, 0 failures |
| Screenshots | see §5 | 97 images |
| Doze (opt-in) | see §7 | 2 tests, on a real device only for sign-off |

---

## 3. The build and test commands

### 3.1 Unit tests

```bash
./gradlew test
```

During the audit this reported **333 tests, 0 failures, 0 errors, 0
skipped**, spread as:

| Module | Tests |
|---|---|
| `app` | 34 |
| `core/data` | 3 |
| `core/designsystem` | 38 |
| `core/domain` | 129 |
| `core/security` | 25 |
| `feature/calendar` | 22 |
| `feature/export` | 13 |
| `feature/home` | 15 |
| `feature/insights` | 2 |
| `feature/logging` | 23 |
| `feature/onboarding` | 7 |
| `feature/privacy` | 18 |
| `feature/settings` | 4 |
| **Total** | **333** |

To read the results as XML:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
tot=f=e=s=0
for p in glob.glob('**/build/test-results/**/*.xml', recursive=True):
    try: r=ET.parse(p).getroot()
    except Exception: continue
    if r.tag!='testsuite': continue
    tot+=int(r.get('tests',0)); f+=int(r.get('failures',0))
    e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print(f"unit: tests={tot} failures={f} errors={e} skipped={s}")
PY
```

Run a single class or method with `--tests`:

```bash
./gradlew :core:domain:test --tests "*PredictionCorpusGateTest"
./gradlew :app:test --tests "*InterstitialCoordinatorTest"
```

### 3.2 Instrumented tests

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```

This took **3m 8s** and reported **166 tests, 0 failures, 2 skipped**:

| Module | Tests |
|---|---|
| `app` | 80 (2 skipped) |
| `core/data` | 27 |
| `core/database` | 20 |
| `feature/calendar` | 6 |
| `feature/home` | 12 |
| `feature/insights` | 2 |
| `feature/logging` | 2 |
| `feature/onboarding` | 7 |
| `feature/privacy` | 10 |
| **Total** | **166** |

The two skipped are `ReminderDozeDeliveryTest`. See §7.

Run one class:

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.ScreenStateMatrixTest
```

Results are written to
`<module>/build/outputs/androidTest-results/connected/debug/`. The root
element is `testsuites` and carries the aggregate counts:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
tot=f=e=s=0
for p in glob.glob('**/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    if '/testlog/' in p: continue
    r=ET.parse(p).getroot()
    tot+=int(r.get('tests',0)); f+=int(r.get('failures',0))
    e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print(f"instrumented: tests={tot} failures={f} errors={e} skipped={s}")
PY
```

### 3.3 The corpus gates

The gate harness is
`core/domain/src/test/java/com/cyclecare/core/domain/prediction/PredictionCorpusGateTest.kt`.

```bash
./gradlew :core:domain:test --tests "*PredictionCorpusGateTest"
```

**Eight of the thirteen gates in `12` §6 exist.** The other five —
G2, G3, G6, G10 and G12 — have no test anywhere in the repository, so
this command cannot pass or fail them. See `22-phase-completion-plan.md`
item **P4-01**.

To confirm which gates exist:

```bash
for g in G1 G2 G3 G4 G5 G6 G7 G8 G9 G10 G11 G12 G13; do
  printf "%-4s " "$g"
  grep -rl "$g" --include="*.kt" . 2>/dev/null \
    | grep -v "/build/\|/bin/" | head -1 || true
  echo
done
```

An empty line means the gate has no test.

### 3.4 The migration tests

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest
```

20 tests, 0 failures. These cover `02` §9.8 and §9.10 items 1, 2, 5, 7
and 8. The items that have no test are listed under **P3-02**, **P3-03**
and **P3-05**.

---

## 4. The static guards

All three run in CI and all three must pass before a release.

```bash
./scripts/guard-no-ad-targeting.sh
```

```
Checking app/src/main for ad-targeting APIs...
  OK: no ad-targeting API found.
  Ad requests are built with defaults only; no user content can reach the ad SDK.
```

```bash
./scripts/guard-typography-roles.sh
```

```
OK   displayLarge (reached via tabular variant numericHero)
OK   displayMedium (reached via tabular variant numericLarge)
All 15 typography roles are defined and referenced.
```

```bash
python3 scripts/contrast-report.py
```

Prints every token pair with its measured ratio and the WCAG target it
must meet, then:

```
All pairs with a WCAG target meet it.
INFO rows have no applicable requirement; shown for reference.
```

The `INFO` rows are decorative dividers with no WCAG requirement. They
are not failures.

### The release build

```bash
./gradlew assembleRelease
./gradlew :app:lintRelease
```

Both are clean. The release build applies the real
`app/proguard-rules.pro`, which is what `06` §5 asks for.

---

## 5. Screenshots and the visual sweep

### 5.1 Capturing

The harness is
`app/src/androidTest/java/com/cyclecare/app/ReThemeScreenshotTest.kt`.
It captures **twelve screens at four widths in two colour schemes**, plus
the privacy screen once, for **97 images**.

| Screen | Captures |
|---|---|
| `home` | 8 — 4 widths × light and dark |
| `calendar` | 8 |
| `log` | 8 |
| `insights` | 8 |
| `cycle_status` | 8 |
| `fertility_detail` | 8 |
| `edit_cycle_sheet` | 8 |
| `reminder_centre` | 8 |
| `settings` | 8 |
| `onboarding` | 8 |
| `notification_gate` | 8 |
| `app_lock` | 8 |
| `privacy` | 1 — 360dp, light only |

**The screenshots are deleted by the test task.** `connectedDebugAndroidTest`
uninstalls the app when it finishes, and the app's external files
directory goes with it. Pulling after the run finds nothing. Copy them
somewhere that survives the uninstall, while the run is in progress:

```bash
rm -rf /tmp/shots && mkdir -p /tmp/shots

ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.ReThemeScreenshotTest \
  --console=plain > /tmp/shots/gradle.log 2>&1 &

GRADLE_PID=$!
while kill -0 $GRADLE_PID 2>/dev/null; do
  adb -s emulator-5556 shell \
    'mkdir -p /sdcard/Download/cc_shots && cp -f /sdcard/Android/data/com.cyclecare.tracker/files/screenshots/*.png /sdcard/Download/cc_shots/ 2>/dev/null' >/dev/null 2>&1
  sleep 1
done

adb -s emulator-5556 pull /sdcard/Download/cc_shots /tmp/shots/
ls /tmp/shots/cc_shots | wc -l    # 97
```

The one-second copy interval matters. Polling less often loses the last
files, because the uninstall and the last writes race.

To check the set is complete:

```bash
ls /tmp/shots/cc_shots | sed -E 's/_(320|360|600|840)dp_(light|dark)\.png$//' | sort | uniq -c
```

Every screen should show 8, except `privacy`, which shows 1.

### 5.2 Reviewing

Read the images. A capture is evidence that a screen rendered, not
evidence that it is right. The three defects found at 200% are recorded
in `21-phase-5-6-acceptance-matrix.md`.

---

## 6. Font scale, RTL and landscape

### 6.1 Font scale at 200%

This uses the **real system setting**, not a density trick, so the
platform's own layout behaviour is what gets exercised.

```bash
adb -s emulator-5556 shell settings put system font_scale 2.0
```

Then capture with the procedure in §5.1, and afterwards **restore it**:

```bash
adb -s emulator-5556 shell settings put system font_scale 1.0
```

`NavLabelScalingTest` covers the in-app check at 1.5× by overriding
`LocalDensity` inside the test, which needs no device state change:

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.NavLabelScalingTest
```

### 6.2 RTL

`RtlLayoutTest` sets the locale through `LocaleManager`, asserts that
the layout direction and the tab order reverse, and restores the locale
in its own teardown. It leaves no device state behind.

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.RtlLayoutTest
```

To set the device locale by hand for a manual look:

```bash
adb -s emulator-5556 shell cmd locale set-app-locales com.cyclecare.tracker --locales ar
adb -s emulator-5556 shell cmd locale set-app-locales com.cyclecare.tracker --locales ""
```

### 6.3 Landscape

```bash
adb -s emulator-5556 shell settings put system accelerometer_rotation 0
adb -s emulator-5556 shell settings put system user_rotation 1
```

Capture, then restore with `user_rotation 0` and `accelerometer_rotation 1`
as in §9.

### 6.4 Touch targets and the 320dp case

The `cc36` AVD is already 320x640 at 160dpi, which is the 320dp case
natively. `DesignTokenTest.touchTargetMeetsTheAccessibilityMinimum`
covers the 48dp minimum.

**Do not set `wm size` to 1000x1800.** That combination breaks the
touch-target assertions and the results are meaningless.

---

## 7. Doze

### The mechanism, on an emulator

`ReminderDozeDeliveryTest` is **opt-in and must stay that way**. It
forces the whole device into idle, and when it is left enabled in the
default suite it breaks the other 28 instrumented tests.

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.ReminderDozeDeliveryTest \
  -Pandroid.testInstrumentationRunnerArguments.runDozeTest=true
```

The measurements already taken are in `19-doze-verification.md`: awake
delivery 22 seconds late, deep-idle delivery 45 seconds late, against a
WorkManager path that was held at `WAIT:DEV_NOT_DOZING` indefinitely.

### The demonstration, for sign-off

**The emulator cannot satisfy the exit criterion.** `13` §17's
requirement — and the roadmap's *"the Doze demonstration is recorded, not
assumed"* — needs a physical device. See **P7-05** in the ledger for
the definition of done and the expected evidence.

---

## 8. Procedures that cannot be automated

These need hardware or a person. They are listed so that they are not
mistaken for something the test suite covers.

### Real-device Doze

1. Install a release-signed build on a physical device.
2. Turn the screen off and leave the device idle for longer than the
   platform's Doze entry window.
3. Schedule a `PeriodPrep` and a `LatePeriod` reminder, and record the
   actual delivery latency for each.
4. Repeat at deep idle.
5. Record the device, the Android version, the idle duration and both
   latencies in `19-doze-verification.md`.

### Foldable

Needs a device with a hinge or a posture. The `cc36` emulator has
neither, and a resized display is not a foldable. The 600dp and 840dp
captures exercise the window size classes, which is the closest
available evidence.

### Manual screen reader pass

The semantics tree is what a screen reader consumes, and
`TalkBackSemanticsTest` asserts against it. That is not the same as a
person listening. Run TalkBack through the key screens and record what
was heard.

### Performance baseline

`06` §4 requires measurements on representative low and mid-range
physical devices, not an emulator, and explicitly says not to choose
targets to make the report look strong.

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

The module provides two cases, `startup_homeColdStart` and
`calendar_openAndScroll`. **Both are currently broken and neither will
produce a usable number.** `startup_homeColdStart` waits for
`By.text("Home")`, but the first tab is labelled **"Today"** since the
four-tab navigation was introduced, so the wait times out after five
seconds on every iteration and the measurement is of a timeout. See
**P9-05** in the ledger.

On an emulator, macrobenchmark refuses to run unless the error is
suppressed:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

`06` §4 asks for measurements on representative low and mid-range
**physical** devices, and explicitly says not to choose targets to make
the report look strong, so the emulator run is a smoke test rather than
a source of budgets. No baseline has been collected yet.

### Play Console and the store

Not in the repository. See the external actions table in
`22-phase-completion-plan.md`.

---

## 9. Restoring the device

Any procedure that changes device state must put it back. After a
session:

```bash
adb -s emulator-5556 shell settings put system font_scale 1.0
adb -s emulator-5556 shell settings put system accelerometer_rotation 1
adb -s emulator-5556 shell settings put system user_rotation 0
adb -s emulator-5556 shell wm size reset
adb -s emulator-5556 shell wm density reset
adb -s emulator-5556 shell cmd locale set-app-locales com.cyclecare.tracker --locales ""
```

`wm size reset` and `wm density reset` matter most. Setting a custom
size without resetting it breaks the touch-target tests, and the failure
looks like a product defect rather than a broken environment.

---

## 10. Known harness limitations

These are properties of the harness, not defects in the app. They are
recorded so that nobody reads them as findings.

| Limitation | Consequence |
|---|---|
| `connectedDebugAndroidTest` uninstalls the app when it finishes | The screenshot directory is deleted. Use the §5.1 copy procedure. |
| The privacy screen is captured once, at 360dp in light | Rendering, navigating and rendering again in one test leaves the previous composition registered and the next one fails — `ComposeRootRegistry.withRegistry`. |
| `ReminderDozeDeliveryTest` forces device-wide idle | It is opt-in. Left enabled, it breaks 28 other instrumented tests. |
| `PreviewCoverageTest` reads one source file | It asserts the widths and schemes against `ScreenDebugPreviews.kt` only, not against every screen. See **P5-05**. |
| `CycleCareShell.kt` is 620 lines | The `15` §14.14 criterion is **~600**. See **P6-01**. |
| The macrobenchmark module has no baseline | `06` §4 budgets cannot be set until measurements exist. See **P9-05**. |
