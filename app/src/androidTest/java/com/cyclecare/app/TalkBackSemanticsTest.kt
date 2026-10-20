package com.cyclecare.app

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader is given.
 *
 * Spec `15` §14.8 requires every screen to work under TalkBack, and
 * §1.4 requires the bottom navigation to carry proper content
 * descriptions on its icons.
 *
 * These are assertions about the semantics tree rather than about the
 * pixels, because the semantics tree is what a screen reader actually
 * consumes. A screenshot cannot tell you whether a control is focusable,
 * whether it announces itself as a tab, or whether the current one
 * announces itself as selected.
 */
@RunWith(AndroidJUnit4::class)
class TalkBackSemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderApp() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val (container, authController) = populatedScreenshotEnvironment()
            activity.setContent {
                CycleCareTheme {
                    CycleCareApp(
                        container = container,
                        secureAuthController = authController,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Every node in the tree, in traversal order. */
    private fun allNodes(): List<SemanticsNode> {
        val root = composeRule.onRoot().fetchSemanticsNode()
        val collected = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            collected.add(node)
            node.children.forEach(::walk)
        }
        walk(root)
        return collected
    }

    @Test
    fun everyBottomNavigationItemAnnouncesItselfAsATab() {
        renderApp()

        listOf("home", "calendar", "log", "insights").forEach { route ->
            val node = composeRule.onNodeWithTag(navTag(route)).fetchSemanticsNode()
            val role = node.config.getOrNull(SemanticsProperties.Role)
            assertThat("tab $route has no role").isNotNull()
            assertThat(role).isEqualTo(Role.Tab)
        }
    }

    @Test
    fun exactlyOneBottomNavigationItemIsAnnouncedAsSelected() {
        renderApp()

        val selected = listOf("home", "calendar", "log", "insights").filter { route ->
            composeRule.onNodeWithTag(navTag(route)).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Selected) == true
        }

        assertThat(selected).containsExactly("home")
    }

    @Test
    fun theSelectionFollowsTheTabThatWasOpened() {
        renderApp()

        composeRule.onNodeWithTag(navTag("insights")).performClick()
        composeRule.waitForIdle()

        val selected = listOf("home", "calendar", "log", "insights").filter { route ->
            composeRule.onNodeWithTag(navTag(route)).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Selected) == true
        }

        assertThat(selected).containsExactly("insights")
    }

    @Test
    fun everyBottomNavigationItemIsActionable() {
        renderApp()

        listOf("home", "calendar", "log", "insights").forEach { route ->
            val node = composeRule.onNodeWithTag(navTag(route)).fetchSemanticsNode()
            // The action is what makes the item focusable and activatable.
            val action = node.config.getOrNull(SemanticsActions.OnClick)
            assertThat("tab $route is not clickable").isNotNull()
            assertThat(action).isNotNull()
        }
    }

    @Test
    fun theBottomNavigationIsAnnouncedInTabOrder() {
        renderApp()

        // The traversal order is the order a screen reader walks the tabs in,
        // and it has to match the visual order.
        val traversal = allNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)
            }
            .filter { it.startsWith("nav_") && it != "nav_icon_home" && it != "nav_icon_calendar" && it != "nav_icon_log" && it != "nav_icon_insights" }

        assertThat(traversal).containsExactly(
            "nav_home",
            "nav_calendar",
            "nav_log",
            "nav_insights",
        ).inOrder()
    }

    @Test
    fun theCycleProgressIsAnnouncedWithItsState() {
        renderApp()

        // The progress bar carries the day of the cycle in its state
        // description, which is what a screen reader reads out.
        val withState = allNodes().filter { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription) != null
        }

        assertThat(withState).isNotEmpty()
    }
}
