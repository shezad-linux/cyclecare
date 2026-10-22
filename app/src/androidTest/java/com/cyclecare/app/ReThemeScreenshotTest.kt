package com.cyclecare.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.cyclecare.app.AppContainer
import com.cyclecare.feature.privacy.SecureAuthController
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.File
import kotlinx.coroutines.runBlocking
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

    /**
     * What the capture renders.
     *
     * The composition is installed once per test and these are the
     * parts that change between captures.
     */
    private class RenderTarget {
        var widthDp by mutableStateOf(320)
        var dark by mutableStateOf(false)
        var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    }

    private val target = RenderTarget()

    /**
     * Installs the composition for a test.
     *
     * The rule's `setContent` may be called only once per test, and it
     * has to be called through the rule: attaching the composition to
     * the activity directly leaves the rule without a record of it, and
     * a class that does that in a shared process intermittently leaves
     * the framework reading snapshot state across threads.
     *
     * So the content is installed once and the width, the colour scheme
     * and the environment are changed through state afterwards. The
     * capture loop then varies the state rather than rebuilding the
     * composition, which is also why a capture can no longer be
     * preceded by a stale one.
     */
    private fun installContent(content: @Composable () -> Unit) {
        target.content = content
        composeRule.setContent {
            val current = target.content
            if (current != null) {
                CycleCareTheme(darkTheme = target.dark) {
                    Box(
                        modifier = Modifier
                            .width(target.widthDp.dp)
                            .fillMaxHeight()
                            .testTag(CAPTURE_TAG),
                    ) {
                        current()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Points the installed composition at a width and a colour scheme.
     */
    private fun render(widthDp: Int, dark: Boolean, content: @Composable () -> Unit) {
        target.widthDp = widthDp
        target.dark = dark
        target.content = content
        composeRule.waitForIdle()
    }

    /**
     * Switches tab without the touch feedback.
     *
     * Material draws a ripple on the tab that was tapped, and it lingers
     * over the selection indicator. A screenshot taken while it is still
     * fading shows a grey pill behind every tab that was tapped, which
     * reads as a design defect but is only a transient touch effect.
     * Invoking the click semantics navigates without the touch input, so
     * the review images show the resting state.
     */
    /**
     * Switches tab and waits for the destination to be on screen.
     *
     * The wait matters. Tapping a tab asks the interstitial gate whether
     * an ad may be shown, and the gate reads persisted state, so the
     * navigation does not happen inside the click: it happens when that
     * read returns, on a background thread, and it is then posted to
     * the main thread. `waitForIdle` waits for the composition to be
     * quiet, which it already is while the read is outstanding, so
     * without this the capture can be taken of the screen the app was
     * on before the tab switch.
     */
    private fun switchTab(route: String) {
        composeRule.onNodeWithTag(navTag(route)).performSemanticsAction(SemanticsActions.OnClick) {
            it()
        }
        composeRule.waitForIdle()
        val destination = TAB_CONTENT_TAG[route] ?: return
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(destination)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /**
     * Captures the screens that are reachable from the seeded state.
     *
     * The four tabs are covered by the main loop. These are the detail
     * screens a person can reach from them, which the review also needs to
     * see: the settings, the reminder centre, the cycle status and the
     * fertility detail, and the edit sheet over the dashboard.
     */
    /**
     * Scrolls a dashboard action fully into view and taps it.
     *
     * `performScrollToNode` only guarantees the node exists in the tree; at a
     * large font scale the hero card is taller than the viewport, so the
     * action at its bottom can still be off-screen and the tap would land
     * somewhere else. `performScrollTo` brings the node itself into view.
     */
    private fun scrollToAndClick(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun captureDetailScreens(widthDp: Int, dark: Boolean) {
        render(widthDp, dark, screenContent())
        // The route loop ends on Insights, so the dashboard is put back
        // before its detail screens are captured.
        switchTab("home")

        // The edit sheet, over the dashboard. The button is inside the
        // hero, which is at the top of a scrolling dashboard, so it is
        // scrolled to before it is tapped.
        //
        // The dashboard root is located in the unmerged tree. Its tag is
        // applied to a `LazyColumn`, and a LazyColumn merges its
        // descendants, so whether the tag survives as a node of its own
        // depends on what else the composition currently contains. That
        // is why this failed intermittently only when the capture ran
        // alongside the other classes in the same process: the platform
        // reported the node as present in the unmerged tree and absent
        // from the merged one. Searching the unmerged tree is correct in
        // either arrangement.
        //
        // The assertion is unchanged. `home_edit_cycle` still has to be
        // found by scrolling the dashboard to it, so a dashboard that is
        // missing or not scrollable still fails.
        composeRule.onNodeWithTag("home_dashboard", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("home_edit_cycle"))
        scrollToAndClick("home_edit_cycle")
        capture("edit_cycle_sheet", widthDp, dark)
        // The sheet is a modal with no dismiss control of its own, so it is
        // closed the way a person closes it.
        pressBackUnconditionally()
        composeRule.waitForIdle()

        // The cycle status detail.
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_cycle_status_cta"))
        scrollToAndClick("home_cycle_status_cta")
        capture("cycle_status", widthDp, dark)
        composeRule.onNodeWithText("Back to dashboard").performClick()
        composeRule.waitForIdle()

        // The fertility detail.
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_fertility_detail_cta"))
        scrollToAndClick("home_fertility_detail_cta")
        capture("fertility_detail", widthDp, dark)
        composeRule.onNodeWithText("Back to dashboard").performClick()
        composeRule.waitForIdle()

        // The reminder centre, from the dashboard.
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_reminder_center"))
        scrollToAndClick("home_reminder_center")
        capture("reminder_centre", widthDp, dark)
        pressBackUnconditionally()
        composeRule.waitForIdle()

        // Settings, from the app bar on the dashboard.
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        capture("settings", widthDp, dark)

        // Back to the dashboard, so the next pass starts on a screen that
        // has the bottom navigation on it.
        pressBackUnconditionally()
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

    /**
     * The screens that need their own setup.
     *
     * One test per screen, because each one needs a different container and
     * a fresh activity. Setting the content repeatedly in a single test
     * leaves the previous composition registered and the second one fails.
     */
    @Test
    fun captureOnboarding() = runBlocking {
        val environment = emptyScreenshotEnvironment()
        installContent {
            val (container, authController) = environment
            CycleCareApp(container = container, secureAuthController = authController)
        }
        widths.forEach { widthDp ->
            listOf(false, true).forEach { dark ->
                render(widthDp, dark) {
                    val (container, authController) = environment
                    CycleCareApp(container = container, secureAuthController = authController)
                }
                capture("onboarding", widthDp, dark)
            }
        }
    }

    @Test
    fun captureAppLock() = runBlocking {
        val environment = appLockScreenshotEnvironment()
        installContent {
            val (container, authController) = environment
            CycleCareApp(container = container, secureAuthController = authController)
        }
        widths.forEach { widthDp ->
            listOf(false, true).forEach { dark ->
                render(widthDp, dark) {
                    val (container, authController) = environment
                    CycleCareApp(container = container, secureAuthController = authController)
                }
                capture("app_lock", widthDp, dark)
            }
        }
    }

    @Test
    fun captureNotificationGate() = runBlocking {
        val environment = notificationGateScreenshotEnvironment()
        installContent {
            val (container, authController) = environment
            CycleCareApp(container = container, secureAuthController = authController)
        }
        widths.forEach { widthDp ->
            listOf(false, true).forEach { dark ->
                render(widthDp, dark) {
                    val (container, authController) = environment
                    CycleCareApp(container = container, secureAuthController = authController)
                }
                capture("notification_gate", widthDp, dark)
            }
        }
    }

    @Test
    fun capturePrivacy() = runBlocking {
        // Once, at a representative size.
        //
        // Rendering, navigating and rendering again in the same test
        // leaves the previous composition registered and the next one
        // fails, so the privacy screen is captured in a single pass rather
        // than across every width. The other screens have no navigation in
        // the loop and are captured at all four widths.
        val environment = populatedScreenshotEnvironment()
        installContent {
            val (container, authController) = environment
            CycleCareApp(container = container, secureAuthController = authController)
        }
        render(360, false) {
            val (container, authController) = environment
            CycleCareApp(container = container, secureAuthController = authController)
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("app_bar_settings")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_open_privacy"))
        composeRule.onNodeWithTag("settings_open_privacy").performClick()
        composeRule.waitForIdle()
        capture("privacy", 360, false)
    }

    @Test
    fun captureAllTopLevelScreensAtEveryWidthAndScheme() {
        val routes = listOf("home", "calendar", "log", "insights")
        // The composition is installed once for the whole test. The
        // capture loop varies the width and the colour scheme through
        // the state the composition reads, so each capture is a fresh
        // render of the same composition rather than a new one stacked
        // on the last.
        installContent(screenContent())
        widths.forEach { widthDp ->
            listOf(false, true).forEach { dark ->
                // setContent replaces the composition but the activity retains
                // its saved navigation state, so the previous iteration's tab
                // survives. Every capture therefore navigates to its own tab
                // explicitly rather than assuming a fresh start.
                render(widthDp, dark, screenContent())
                routes.forEach { route ->
                    switchTab(route)
                    capture(route, widthDp, dark)
                }
            }
        }

        // The detail screens get their own pass. They replace the bottom
        // navigation with their own bar, so leaving one of them open would
        // break the tab loop above.
        widths.forEach { widthDp ->
            listOf(false, true).forEach { dark ->
                captureDetailScreens(widthDp, dark)
            }
        }
    }

    private companion object {
        const val CAPTURE_TAG = "screenshot_capture_root"

        /**
         * The content each tab is known by.
         *
         * The capture waits for the tab's own content before taking the
         * image, so a capture can never be of the previous screen.
         */
        val TAB_CONTENT_TAG = mapOf(
            "home" to "home_dashboard",
            "calendar" to "calendar_scroll",
            "log" to "logging_list",
            "insights" to "insights_overview",
        )
    }
}