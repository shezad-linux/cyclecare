package com.cyclecare.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleCareBenchmarkTest {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = "com.cyclecare.tracker"

    @Test
    fun startup_homeColdStart() {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("Home")), 5_000)
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
                device.wait(Until.hasObject(By.text("Calendar")), 5_000)
            },
        ) {
            device.findObject(By.text("Calendar"))?.click()
            device.wait(Until.hasObject(By.text("Cycle calendar")), 5_000)
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, (height * 0.8f).toInt(), width / 2, (height * 0.25f).toInt(), 20)
        }
    }
}
