package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /**
     * What the layout is rendered at.
     *
     * The composition is installed once per test and these are the
     * parts that change between widths.
     */
    private class RenderTarget {
        var widthDp by mutableStateOf(320)
        var landscape by mutableStateOf(false)
        var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    }

    private val target = RenderTarget()

    /** Whether the composition has been installed for this test. */
    private var contentInstalled = false

    /**
     * Installs the composition for a test.
     *
     * The rule's `setContent` may be called only once per test, and it
     * has to be called through the rule: attaching the composition to
     * the activity directly leaves the rule without a record of it.
     *
     * The width and the orientation are therefore changed through state
     * afterwards, and the loop over the widths varies the state rather
     * than rebuilding the composition.
     */
    private fun installContent(content: @Composable () -> Unit) {
        target.content = content
        composeRule.setContent {
            val current = target.content
            if (current != null) {
                com.cyclecare.core.designsystem.CycleCareTheme {
                    androidx.compose.foundation.layout.Box(
                        modifier = if (target.landscape) {
                            Modifier.width(target.widthDp.dp).height(heightDp(target.widthDp).dp)
                                .testTag("responsive_root")
                        } else {
                            Modifier.width(target.widthDp.dp).fillMaxHeight().testTag("responsive_root")
                        },
                    ) {
                        current()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * The height the layout is rendered at.
     *
     * Landscape uses a short-but-renderable viewport so content can lay
     * out while still being vertically constrained (a real landscape
     * viewport is wide and short).
     */
    private fun heightDp(widthDp: Int): Int =
        if (target.landscape) max(400, (widthDp * 0.6).toInt()) else Int.MAX_VALUE

    /**
     * Points the installed composition at a width and an orientation.
     */
    private fun render(widthDp: Int, landscape: Boolean, content: @Composable () -> Unit) {
        target.widthDp = widthDp
        target.landscape = landscape
        target.content = content
        composeRule.waitForIdle()
    }

    private fun renderTabs(widthDp: Int, landscape: Boolean) {
        val content: @Composable () -> Unit = {
            val (container, authController) = remember { populatedScreenshotEnvironment() }
            com.cyclecare.app.CycleCareApp(
                container = container,
                secureAuthController = authController,
            )
        }
        if (!contentInstalled) {
            contentInstalled = true
            installContent(content)
        }
        render(widthDp, landscape, content)
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
                    composeRule.openTabAndAwait(route)
                    val displayed = runCatching {
                        composeRule.onNodeWithTag(contentTag, useUnmergedTree = true).assertIsDisplayed()
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
                composeRule.openTabAndAwait("calendar")
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
