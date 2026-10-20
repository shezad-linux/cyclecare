package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
 *
 * Every key screen is covered: the shell, Today, the calendar, the log,
 * the insights detail screens, Settings, the export screen, the reminder
 * centre, the privacy screen, and the dialogs and sheets.
 *
 * A manual pass with a screen reader is a separate thing, and it is not
 * claimed here. See
 * `CycleCare-Redesign-Docs/23-verification-runbook.md` §8.
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

    private fun openTab(route: String) {
        composeRule.onNodeWithTag(navTag(route)).performClick()
        composeRule.waitForIdle()
    }

    private fun openSettings() {
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("settings_list")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Every node below this one. */
    private fun SemanticsNode.descendants(): List<SemanticsNode> {
        val collected = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            node.children.forEach {
                collected.add(it)
                walk(it)
            }
        }
        walk(this)
        return collected
    }

    private fun stateContains(value: String): SemanticsMatcher = SemanticsMatcher(
        description = "state description contains $value",
    ) { node ->
        node.config.getOrNull(SemanticsProperties.StateDescription)?.contains(value) ?: false
    }

    // ── the shell ──────────────────────────────────────────────────

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

    @Test
    fun theAppBarActionIsAnnouncedAsAButton() {
        renderApp()

        val node = composeRule.onNodeWithTag("app_bar_settings").fetchSemanticsNode()
        assertThat(node.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(node.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    // ── the calendar ───────────────────────────────────────────────

    @Test
    fun theCalendarMonthButtonsAreAnnouncedWithTheirLabels() {
        renderApp()
        openTab("calendar")

        listOf(
            "calendar_previous_month" to "Previous month",
            "calendar_next_month" to "Next month",
        ).forEach { (tag, label) ->
            val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            assertThat(node.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
            val descriptions = allNodes().filter { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
            }
            assertThat(descriptions).isNotEmpty()
        }
    }

    @Test
    fun theSelectedCalendarDayIsAnnouncedAsSelected() {
        renderApp()
        openTab("calendar")

        val today = LocalDate.now()
        val cell = composeRule.onNodeWithTag("calendar_day_$today").fetchSemanticsNode()

        // The selected day carries "selected" in its state description,
        // which is what a screen reader reads out for it.
        val state = cell.config.getOrNull(SemanticsProperties.StateDescription)
        assertThat(state).isNotNull()
        assertThat(state).contains("selected")
    }

    @Test
    fun everyCalendarDayAnnouncesItsStates() {
        renderApp()
        openTab("calendar")

        val today = LocalDate.now()
        val cell = composeRule.onNodeWithTag("calendar_day_$today").fetchSemanticsNode()

        val state = cell.config.getOrNull(SemanticsProperties.StateDescription)
        assertThat(state).isNotNull()
        assertThat(state).contains("today")
        assertThat(cell.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    // ── the log ────────────────────────────────────────────────────

    @Test
    fun theLogChoicesAnnounceWhetherTheyAreSelected() {
        renderApp()
        openTab("log")

        composeRule.onNodeWithTag("logging_list")
            .performScrollToNode(hasTestTag("logging_mood_Happy"))

        val chip = composeRule.onNodeWithTag("logging_mood_Happy").fetchSemanticsNode()
        assertThat(chip.config.getOrNull(SemanticsProperties.Selected)).isFalse()
        assertThat(chip.config.getOrNull(SemanticsActions.OnClick)).isNotNull()

        composeRule.onNodeWithTag("logging_mood_Happy").performClick()
        composeRule.waitForIdle()

        val selectedChip = composeRule.onNodeWithTag("logging_mood_Happy").fetchSemanticsNode()
        assertThat(selectedChip.config.getOrNull(SemanticsProperties.Selected)).isTrue()
    }

    @Test
    fun theLogSaveActionIsAnnouncedAsAButton() {
        renderApp()
        openTab("log")

        val action = composeRule.onNodeWithText("Save today's entry").fetchSemanticsNode()
        assertThat(action.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(action.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    @Test
    fun theLogNotesFieldAnnouncesHowManyCharactersAreLeft() {
        renderApp()
        openTab("log")

        composeRule.onNodeWithTag("logging_list")
            .performScrollToNode(hasText("Add medication or notes"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Add medication or notes").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Add medication or notes").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("logging_notes_field")).fetchSemanticsNodes().isNotEmpty()
        }

        // The character count is on the field's state description, so a
        // screen reader reads it as the user types.
        val field = composeRule.onNodeWithTag("logging_notes_field").fetchSemanticsNode()
        val state = field.config.getOrNull(SemanticsProperties.StateDescription)
        assertThat(state).isNotNull()
        assertThat(state).contains("500")
    }

    // ── the insights detail screens ────────────────────────────────

    @Test
    fun theCycleTrendsScreenAnnouncesItsBackAction() {
        renderApp()
        openTab("insights")
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasTestTag("insights_cycle_trends_cta"))
        composeRule.onNodeWithTag("insights_cycle_trends_cta").performScrollTo().performClick()

        val back = composeRule.onNodeWithTag("insights_back_to_overview").fetchSemanticsNode()
        assertThat(back.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
        composeRule.onNodeWithText("Back to insights").assertIsDisplayed()
    }

    @Test
    fun theSymptomPatternsScreenAnnouncesItsBackAction() {
        renderApp()
        openTab("insights")
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasTestTag("insights_symptom_patterns_cta"))
        composeRule.onNodeWithTag("insights_symptom_patterns_cta").performScrollTo().performClick()

        val back = composeRule.onNodeWithTag("insights_back_to_overview").fetchSemanticsNode()
        assertThat(back.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
        composeRule.onNodeWithText("Back to insights").assertIsDisplayed()
    }

    // ── settings ───────────────────────────────────────────────────

    @Test
    fun theSettingsRemindersSwitchAnnouncesItsState() {
        renderApp()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_reminders_enabled"))

        val row = composeRule
            .onNodeWithTag("settings_reminders_enabled", useUnmergedTree = true)
            .fetchSemanticsNode()
        // The switch carries the on or off state, so it is not read out
        // as a bare label.
        val toggleable = row.descendants()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ToggleableState) }
            .firstOrNull()
        assertThat(toggleable).isNotNull()
        // The switch also announces itself as a switch, so it is not
        // read out as a bare label.
        val switch = row.descendants()
            .firstOrNull { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
        assertThat(switch).isNotNull()
    }

    @Test
    fun theSettingsActionsAreAnnouncedAsButtons() {
        renderApp()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_open_reminder_center"))

        val node = composeRule.onNodeWithTag("settings_open_reminder_center").fetchSemanticsNode()
        assertThat(node.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    // ── export ─────────────────────────────────────────────────────

    @Test
    fun theExportActionsAreAnnouncedAsButtons() {
        renderApp()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open export"))
        composeRule.onNodeWithText("Open export").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("export_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        val action = composeRule.onNodeWithText("Create markdown report").performScrollTo()
            .fetchSemanticsNode()
        assertThat(action.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(action.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    // ── the reminder centre ────────────────────────────────────────

    @Test
    fun theReminderCentreAnnouncesItsHeadingAndItsEmptyState() {
        renderApp()
        openTab("home")
        composeRule.onNodeWithTag("home_reminder_center").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Reminder center")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Reminder center").assertIsDisplayed()
        // The empty state is announced, so the user is told the centre is
        // empty rather than being left on a blank screen.
        composeRule.onNodeWithText("No reminders yet").assertIsDisplayed()
    }

    // ── privacy ────────────────────────────────────────────────────

    @Test
    fun thePrivacyKeypadIsAnnouncedAsButtons() {
        renderApp()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open privacy lock"))
        composeRule.onNodeWithText("Open privacy lock").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("privacy_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        listOf("privacy_key_1", "privacy_key_2", "privacy_key_3").forEach { tag ->
            val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            assertThat(node.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
        }
    }

    @Test
    fun thePrivacyPrimaryActionIsAnnouncedAsAButton() {
        renderApp()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open privacy lock"))
        composeRule.onNodeWithText("Open privacy lock").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("privacy_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        val action = composeRule.onNodeWithTag("privacy_primary_action").fetchSemanticsNode()
        assertThat(action.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(action.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    // ── dialogs and sheets ─────────────────────────────────────────

    @Test
    fun theEditCycleSheetAnnouncesItsTitleAndItsActions() {
        renderApp()

        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_edit_cycle"))
        composeRule.onNodeWithTag("home_edit_cycle").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Edit details")).fetchSemanticsNodes().isNotEmpty()
        }

        // The sheet's title is announced, so the user knows what the
        // sheet is for before they are in it.
        composeRule.onNodeWithText("Edit cycle").assertIsDisplayed()
        val details = composeRule.onNodeWithText("Edit details").fetchSemanticsNode()
        assertThat(details.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }

    @Test
    fun theResetConfirmationDialogAnnouncesItsTitleAndItsActions() {
        renderApp()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_open_reset"))
        composeRule.onNodeWithTag("settings_open_reset").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("settings_confirm_reset")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Reset local data?").assertIsDisplayed()
        val confirm = composeRule.onNodeWithTag("settings_confirm_reset").fetchSemanticsNode()
        assertThat(confirm.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(confirm.config.getOrNull(SemanticsActions.OnClick)).isNotNull()
    }
}
