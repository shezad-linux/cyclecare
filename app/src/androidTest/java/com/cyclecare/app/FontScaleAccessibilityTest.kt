package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Key screens at 200% font scale.
 *
 * Renders each screen with a doubled [Density.fontScale] (the same mechanism
 * [NavLabelScalingTest] uses) so the platform's own large-text layout
 * behaviour is exercised, without changing device state.
 *
 * Spec `15` §14.8 and `14` §12.8 require every screen to work at 200% font
 * scale. This is a controlled configuration: it covers the in-app layout at
 * 2.0x, but it is not a substitute for a real system font-scale sweep
 * (font_scale = 2.0) on a device, which remains a manual step.
 *
 * Assertions:
 * - the screen's primary content is displayed,
 * - primary actions remain reachable,
 * - no content overflows horizontally (no clipped or off-screen essentials).
 */
@RunWith(AndroidJUnit4::class)
class FontScaleAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderApp(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        val (container, authController) = populatedScreenshotEnvironment()
        composeRule.setContent {

            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                com.cyclecare.core.designsystem.CycleCareTheme {
                    content()
                }
            }
        
}
        composeRule.waitForIdle()
    }

    private fun renderHome(fontScale: Float) {
        val (container, authController) = populatedScreenshotEnvironment()
        renderApp(fontScale) {
            CycleCareApp(container = container, secureAuthController = authController)
        }
    }

    private fun assertNoHorizontalOverflow() {
        val root = composeRule.onRoot().fetchSemanticsNode()
        val rootWidth = root.boundsInRoot.right - root.boundsInRoot.left
        collect(root, mutableListOf()).forEach { node ->
            val right = node.boundsInRoot.right
            if (right > rootWidth + 1f) {
                throw AssertionError("horizontal overflow at 200%: node extends to ${right.toInt()}px beyond ${rootWidth.toInt()}px root")
            }
        }
    }

    private fun collect(node: androidx.compose.ui.semantics.SemanticsNode, acc: MutableList<androidx.compose.ui.semantics.SemanticsNode>): MutableList<androidx.compose.ui.semantics.SemanticsNode> {
        acc.add(node)
        node.children.forEach { collect(it, acc) }
        return acc
    }

    @Test
    fun homeRendersAt200PercentFontScale() {
        renderHome(2.0f)
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Next period in 23 days").assertIsDisplayed()
        assertNoHorizontalOverflow()
    }

    @Test
    fun calendarRendersAt200PercentFontScale() {
        renderHome(2.0f)
        composeRule.openTabAndAwait("calendar")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("calendar_scroll").assertIsDisplayed()
        // At 200% the header card and the legend chips fill the first
        // viewport, so the grid itself is below the fold. It must still be
        // reachable by scrolling, or the screen's primary content is lost.
        composeRule.onNodeWithTag("calendar_scroll")
            .performScrollToNode(hasTestTag("calendar_month_label"))
        composeRule.onNodeWithTag("calendar_month_label").assertIsDisplayed()
        assertNoHorizontalOverflow()
    }

    @Test
    fun logRendersAt200PercentFontScale() {
        renderHome(2.0f)
        composeRule.openTabAndAwait("log")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("logging_list").assertIsDisplayed()
        composeRule.onNodeWithText("Save today's entry").assertIsDisplayed()
        assertNoHorizontalOverflow()
    }

    @Test
    fun insightsRendersAt200PercentFontScale() {
        renderHome(2.0f)
        composeRule.openTabAndAwait("insights")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insights_overview").assertIsDisplayed()
        assertNoHorizontalOverflow()
    }

    @Test
    fun settingsRendersAt200PercentFontScale() {
        renderHome(2.0f)
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
        assertNoHorizontalOverflow()
    }

    @Test
    fun reminderCentreRendersAt200PercentFontScale() {
        renderHome(2.0f)
        composeRule.onNodeWithTag("home_reminder_center").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reminder center").assertIsDisplayed()
        assertNoHorizontalOverflow()
    }
}
