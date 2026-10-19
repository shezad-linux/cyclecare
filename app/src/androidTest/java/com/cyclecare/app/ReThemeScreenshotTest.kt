package com.cyclecare.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures design review screenshots of the four top-level screens across
 * the widths the redesign requires and in both colour schemes.
 *
 * Phase 5 exit criteria require previews at 320/360/600/840dp in light and
 * dark. There is no debug seed facility in the app, so the screenshots are
 * rendered from the same populated [TestAppEnvironment] the assertion tests
 * use, which is also why that class was widened from private to internal.
 *
 * Widths are applied as a fixed-width container rather than by resizing the
 * emulator, so every width is captured in a single test run.
 *
 * Ad requests are stubbed out: a real banner would inject an unrelated
 * third-party creative into a design review.
 */
@RunWith(AndroidJUnit4::class)
class ReThemeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val widths = listOf(320, 360, 600, 840)

    private fun render(widthDp: Int, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val noOpInterstitial = InterstitialAdManager(
                context = activity.applicationContext,
                adLoader = object : InterstitialAdLoader {
                    override fun load(
                        context: android.content.Context,
                        adUnitId: String,
                        adRequest: AdRequest,
                        callback: InterstitialAdLoadCallback,
                    ) = Unit
                },
            )
            activity.setContent {
                CycleCareTheme(darkTheme = dark) {
                    Box(
                        modifier = Modifier
                            .width(widthDp.dp)
                            .fillMaxHeight()
                            .testTag(CAPTURE_TAG),
                    ) {
                        content()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(screen: String, widthDp: Int, dark: Boolean) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap()
        val scheme = if (dark) "dark" else "light"
        val name = "${screen}_${widthDp}dp_$scheme.png"
        val dir = File(
            composeRule.activity.applicationContext.getExternalFilesDir(null),
            "screenshots",
        )
        dir.mkdirs()
        File(dir, name).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("screenshot was not written", File(dir, name).length() > 0)
    }

    private fun screenContent(): @Composable () -> Unit = {
        val (container, authController) = remember { populatedScreenshotEnvironment() }
        val appContext = LocalContext.current.applicationContext
        CycleCareApp(
            container = container,
            secureAuthController = authController,
            interstitialAdManager = remember(appContext) {
                InterstitialAdManager(
                    context = appContext,
                    adLoader = NoOpAdLoader,
                )
            },
        )
    }

    private object NoOpAdLoader : InterstitialAdLoader {
        override fun load(
            context: Context,
            adUnitId: String,
            adRequest: AdRequest,
            callback: InterstitialAdLoadCallback,
        ) = Unit
    }

    @Test
    fun captureAllTopLevelScreensAtEveryWidthAndScheme() {
        val routes = listOf("home", "calendar", "log", "insights")
        widths.forEach { widthDp ->
            listOf(false, true).forEach { dark ->
                // setContent replaces the composition but the activity retains
                // its saved navigation state, so the previous iteration's tab
                // survives. Every capture therefore navigates to its own tab
                // explicitly rather than assuming a fresh start.
                render(widthDp, dark, screenContent())
                routes.forEach { route ->
                    composeRule.onNodeWithTag(navTag(route)).performClick()
                    composeRule.waitForIdle()
                    capture(route, widthDp, dark)
                }
            }
        }
    }

    private companion object {
        const val CAPTURE_TAG = "screenshot_capture_root"
    }
}