package com.cyclecare.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmarks for the two journeys the specification budgets.
 *
 * Synchronisation. UiAutomator cannot read a Compose `testTag`, so the only
 * anchor it can see is the accessibility text a surface exposes. Two rules make
 * that dependable:
 *
 *  1. Every wait asserts. A bare `device.wait(Until.hasObject(...), …)` that
 *     times out still returns a value, so the measurement silently becomes "time
 *     to timeout" instead of the journey. Here an anchor that never arrives fails
 *     the benchmark outright, so a broken selector can never be reported as a
 *     good cold start.
 *
 *  2. The locale is pinned to English in setup. The anchors are localised
 *     strings; pinning the locale removes the device's language from the result.
 *
 * The tab labels were the previous anchors and they were two things fragile at
 * once: the destination is labelled "Today", not "Home", and the label is a
 * localised string that ellipsises at a large font scale. The app-bar Settings
 * action is used instead — it is on every top-level screen and is a stable
 * accessibility affordance rather than a moving label.
 */
@RunWith(AndroidJUnit4::class)
class CycleCareBenchmarkTest {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = "com.cyclecare.tracker"

    /** The app-bar Settings action. Present on every top-level screen. */
    private val settingsAnchor: BySelector = By.text("Settings")

    /** The Calendar tab label. Present on every top-level screen. */
    private val calendarTab: BySelector = By.text("Calendar")

    /** The Calendar screen title. Reached only after the Calendar tab is opened. */
    private val calendarScreen: BySelector = By.text("Cycle calendar")

    @Before
    fun pinLocale() {
        // The anchors are English accessibility strings; pin the locale so the
        // device's language cannot silently change which objects exist.
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "cmd locale set-app-locales $packageName --locales en",
        ).close()
    }

    @Test
    fun startup_homeColdStart() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
                assertReached(device, settingsAnchor, "startup_homeColdStart")
            },
        ) {
            // The activity is up; the setup block already asserted the anchor.
            device.waitForIdle()
        }
    }

    @Test
    fun calendar_openAndScroll() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.None(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
                assertReached(device, settingsAnchor, "calendar_openAndScroll")
            },
        ) {
            val tab = device.wait(Until.findObject(calendarTab), 5_000)
                ?: throw AssertionError("calendar_openAndScroll: the Calendar tab never appeared")
            tab.click()
            assertReached(device, calendarScreen, "calendar_openAndScroll after opening Calendar")
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, (height * 0.8f).toInt(), width / 2, (height * 0.25f).toInt(), 20)
        }
    }

    /**
     * Fails the benchmark if [anchor] never appears.
     *
     * A silent timeout would be measured and reported as the journey's cost, so
     * the anchor's absence must be an error rather than a slow result.
     */
    private fun assertReached(device: UiDevice, anchor: BySelector, journey: String) {
        val found = device.wait(Until.hasObject(anchor), 10_000)
        assertTrue(
            "$journey: expected to reach $anchor within 10s but it never appeared. " +
                "A missing anchor means the journey did not run and the timing is meaningless.",
            found,
        )
    }
}