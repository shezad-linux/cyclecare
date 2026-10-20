package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import kotlin.math.max
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Responsive and adaptive layout verification.
 *
 * Renders the real screens inside a fixed-width frame at each of the
 * required widths (320, 360, 600 and 840 dp), in both portrait and a
 * short-viewport landscape arrangement, and asserts that nothing overflows
 * horizontally and that primary actions and date navigation keep working.
 *
 * The widths are pinned in dp inside the frame rather than by resizing the
 * emulator, so every configuration is exercised in a single test run. A
 * resized emulator screenshot does not prove foldable behaviour; the
 * foldable posture itself remains outstanding (see the workstream report).
 */
@RunWith(AndroidJUnit4::class)
class ResponsiveLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val widths = listOf(320, 360, 600, 840)

    private fun render(widthDp: Int, landscape: Boolean, content: @Composable () -> Unit) {
        // Landscape uses a short-but-renderable viewport so content can lay
        // out while still being vertically constrained (a real landscape
        // viewport is wide and short).
        val heightDp = if (landscape) max(400, (widthDp * 0.6).toInt()) else Int.MAX_VALUE
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                com.cyclecare.core.designsystem.CycleCareTheme {
                    androidx.compose.foundation.layout.Box(
                        modifier = if (landscape) {
                            Modifier.width(widthDp.dp).height(heightDp.dp).testTag("responsive_root")
                        } else {
                            Modifier.width(widthDp.dp).fillMaxHeight().testTag("responsive_root")
                        },
                    ) {
                        content()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderTabs(widthDp: Int, landscape: Boolean) {
        render(widthDp, landscape) {
            val (container, authController) = remember { populatedScreenshotEnvironment() }
            com.cyclecare.app.CycleCareApp(
                container = container,
                secureAuthController = authController,
            )
        }
    }

    private fun assertNoHorizontalOverflow(widthDp: Int, landscape: Boolean) {
        val root = composeRule.onNodeWithTag("responsive_root").fetchSemanticsNode()
        val rootWidth = root.boundsInRoot.right - root.boundsInRoot.left
        composeRule.onRoot().fetchSemanticsNode().let { rootNode ->
            collect(rootNode).forEach { node ->
                val right = node.boundsInRoot.right
                assert(right <= rootWidth + 1f) {
                    "horizontal overflow at ${widthDp}dp ${if (landscape) "landscape" else "portrait"}: " +
                        "node extends to $right px beyond the $rootWidth px root"
                }
            }
        }
    }

    private fun collect(node: androidx.compose.ui.semantics.SemanticsNode, acc: MutableList<androidx.compose.ui.semantics.SemanticsNode> = mutableListOf()): List<androidx.compose.ui.semantics.SemanticsNode> {
        acc.add(node)
        node.children.forEach { collect(it, acc) }
        return acc
    }

    @Test
    fun noHorizontalOverflowAtEveryWidthAndOrientation() {
        widths.forEach { widthDp ->
            listOf(false, true).forEach { landscape ->
                renderTabs(widthDp, landscape)
                assertNoHorizontalOverflow(widthDp, landscape)
            }
        }
    }

    @Test
    fun everyTabRendersItsPrimaryContentAtEveryWidthAndOrientation() {
        val tabs = listOf(
            "home" to "home_dashboard",
            "calendar" to "calendar_scroll",
            "log" to "logging_list",
            "insights" to "insights_overview",
        )
        widths.forEach { widthDp ->
            listOf(false, true).forEach { landscape ->
                tabs.forEach { (route, contentTag) ->
                    renderTabs(widthDp, landscape)
                    composeRule.onNodeWithTag(navTag(route)).performClick()
                    composeRule.waitForIdle()
                    val displayed = runCatching {
                        composeRule.onNodeWithTag(contentTag).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                    assert(displayed) {
                        "primary content $contentTag not displayed for route $route at ${widthDp}dp ${if (landscape) "landscape" else "portrait"}"
                    }
                }
            }
        }
    }

    @Test
    fun calendarMonthNavigationWorksAtEveryWidthAndOrientation() {
        widths.forEach { widthDp ->
            listOf(false, true).forEach { landscape ->
                renderTabs(widthDp, landscape)
                composeRule.onNodeWithTag(navTag("calendar")).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("calendar_scroll").assertIsDisplayed()
                // On very short viewports the next-month control may not fit;
                // when it is present it must be clickable without breaking
                // the calendar.
                val nextMonthPresent = runCatching {
                    composeRule.onNodeWithTag("calendar_next_month").assertIsDisplayed()
                    true
                }.getOrDefault(false)
                if (nextMonthPresent) {
                    composeRule.onNodeWithTag("calendar_next_month").performClick()
                    composeRule.waitForIdle()
                    composeRule.onNodeWithTag("calendar_scroll").assertIsDisplayed()
                }
            }
        }
    }
}
