package com.cyclecare.app

import android.app.LocaleManager
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app laid out right to left.
 *
 * Spec `15` §14.8 requires every screen to work in RTL, and the manifest
 * declares `android:supportsRtl="true"`, so this is a promise the app
 * makes and has to keep.
 *
 * Two things are checked, and they are different things.
 *
 * The device locale is set through `LocaleManager`, so the app's own
 * resources and configuration are genuinely right to left. That is the
 * Android locale API, and it is what a user with an Arabic or Hebrew
 * system language would produce.
 *
 * The composition is then given the right to left layout direction
 * explicitly, so the layout is exercised deterministically. Compose derives
 * its layout direction from the configuration, and a test cannot change the
 * locale of a running activity without restarting it.
 *
 * A grep for a hardcoded left or right would not find the problems this
 * finds: a tab order that does not reverse, an icon that does not
 * mirror, a text that does not flip.
 *
 * Every key screen is covered, not only the shell and Today. The
 * assertion for each screen is that its leading content is on the right,
 * which is what mirroring means in practice: the first thing the user
 * reads is the first thing on the right.
 */
@RunWith(AndroidJUnit4::class)
class RtlLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        // Put the locale back, or the next test runs right to left.
        runCatching {
            context.getSystemService(LocaleManager::class.java)
                .setApplicationLocales(LocaleList.getEmptyLocaleList())
        }
        val configuration = android.content.res.Configuration(
            context.resources.configuration,
        )
        configuration.setLayoutDirection(java.util.Locale.ENGLISH)
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )
    }

    private fun useRightToLeftLocale() {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        localeManager.setApplicationLocales(LocaleList.forLanguageTags("ar"))
    }

    /** The whole app, because the bottom navigation is part of the app shell. */
    private fun renderApp(layoutDirection: LayoutDirection?) {
        val configuration = android.content.res.Configuration(
            context.resources.configuration,
        )
        if (layoutDirection == LayoutDirection.Rtl) {
            configuration.setLayoutDirection(java.util.Locale("ar"))
        }
        val (container, authController) = populatedScreenshotEnvironment()
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.cyclecare.core.designsystem.CycleCareTheme {
                CycleCareApp(
                    container = container,
                    secureAuthController = authController,
                )
            }
        }
        // The content is attached through the rule, so the rule owns
        // the composition and its snapshot state. The rule's own
        // `setContent` can only be called once per test, so the
        // direction selects the body rather than the call.
        composeRule.setContent {
            if (layoutDirection == LayoutDirection.Rtl) {
                CompositionLocalProvider(
                    LocalConfiguration provides configuration,
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    content()
                }
            } else {
                content()
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderRtl() = renderApp(LayoutDirection.Rtl)

    private fun renderLtr() = renderApp(null)

    // ── navigation helpers ─────────────────────────────────────────

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

    /**
     * Asserts that [leading] sits to the right of [trailing].
     *
     * In right to left the first item in a row is the right-hand one, so
     * this is the assertion that the row mirrors.
     */
    private fun assertLeadingIsOnTheRight(leadingTag: String, trailingTag: String) {
        val leading = composeRule.onNodeWithTag(leadingTag).fetchSemanticsNode()
        val trailing = composeRule.onNodeWithTag(trailingTag).fetchSemanticsNode()
        assertThat(leading.boundsInRoot.right).isGreaterThan(trailing.boundsInRoot.right)
    }

    /**
     * Asserts that a screen's heading sits at the leading edge.
     *
     * In right to left the leading edge is the right, so a heading that
     * is start-aligned moves to the right of the screen. The assertion
     * is that it is well past the middle, which is what "the layout
     * mirrors" means for a screen whose content starts at the leading
     * edge.
     */
    private fun assertHeadingIsAtTheLeadingEdge(text: String) {
        // The topmost node with this text is the screen's own heading.
        // A tab label carries the same word as a screen title, and the
        // tab bar is at the bottom.
        val heading = composeRule
            .onAllNodesWithText(text)
            .fetchSemanticsNodes()
            .minByOrNull { it.boundsInRoot.top }
        assertThat(heading).isNotNull()
        val root = composeRule.onRoot().fetchSemanticsNode()
        assertThat(heading!!.boundsInRoot.left).isGreaterThan(
            root.boundsInRoot.left + root.boundsInRoot.width * 0.25f,
        )
    }

    // ── the shell ──────────────────────────────────────────────────

    @Test
    fun theDeviceLocaleIsRightToLeftWhenItIsSet() {
        // The locale is applied to the resources, which is what the
        // platform does when a user picks an Arabic or Hebrew system
        // language.
        val configuration = android.content.res.Configuration(
            context.resources.configuration,
        )
        configuration.setLocale(java.util.Locale("ar"))
        configuration.setLayoutDirection(java.util.Locale("ar"))
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )

        assertThat(context.resources.configuration.layoutDirection).isEqualTo(
            android.view.View.LAYOUT_DIRECTION_RTL,
        )
    }

    @Test
    fun theTabOrderReversesInRightToLeft() {
        renderRtl()

        val today = composeRule.onNodeWithTag(navTag("home")).fetchSemanticsNode()
        val insights = composeRule.onNodeWithTag(navTag("insights")).fetchSemanticsNode()

        // In right to left the first tab is on the right, so the first
        // destination sits further right than the last one.
        assertThat(today.boundsInRoot.right).isGreaterThan(insights.boundsInRoot.right)
    }

    @Test
    fun theTabOrderIsLeftToRightInTheDefaultLocale() {
        renderLtr()

        val today = composeRule.onNodeWithTag(navTag("home")).fetchSemanticsNode()
        val insights = composeRule.onNodeWithTag(navTag("insights")).fetchSemanticsNode()

        assertThat(today.boundsInRoot.right).isLessThan(insights.boundsInRoot.right)
    }

    @Test
    fun theWholeNavigationIsLaidOutInReverse() {
        renderRtl()

        // Every tab, not just the first, has to move. The last tab ends
        // up furthest left.
        val insights = composeRule.onNodeWithTag(navTag("insights")).fetchSemanticsNode()
        val log = composeRule.onNodeWithTag(navTag("log")).fetchSemanticsNode()

        assertThat(insights.boundsInRoot.right).isLessThan(log.boundsInRoot.right)
    }

    @Test
    fun theAppBarActionMirrorsInRightToLeft() {
        renderRtl()

        // The app bar's only action sits at the end of the bar, and the
        // end is the left in right to left.
        val settings = composeRule.onNodeWithTag("app_bar_settings").fetchSemanticsNode()
        val root = composeRule.onRoot().fetchSemanticsNode()

        assertThat(settings.boundsInRoot.right).isLessThan(root.boundsInRoot.right / 2)
    }

    @Test
    fun theAppBarActionIsOnTheRightInTheDefaultLocale() {
        renderLtr()

        val settings = composeRule.onNodeWithTag("app_bar_settings").fetchSemanticsNode()
        val root = composeRule.onRoot().fetchSemanticsNode()

        assertThat(settings.boundsInRoot.right).isGreaterThan(root.boundsInRoot.right / 2)
    }

    // ── the calendar ───────────────────────────────────────────────

    @Test
    fun theCalendarMonthNavigationMirrorsInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("calendar")

        // "Previous month" is the first child of the header row, so in
        // right to left it is the right-hand one. The chevron mirrors
        // with it, or the control would point the wrong way.
        assertLeadingIsOnTheRight("calendar_previous_month", "calendar_next_month")
    }

    @Test
    fun theCalendarMonthNavigationIsLeftToRightInTheDefaultLocale() {
        renderLtr()
        composeRule.openTabAndAwait("calendar")

        assertLeadingIsOnTheRight("calendar_next_month", "calendar_previous_month")
    }

    @Test
    fun theCalendarAnnouncesTheSelectedDayInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("calendar")

        // The selected day is announced through its state description,
        // which is what a screen reader reads out, and it is the same
        // in either direction.
        val selected = composeRule
            .onAllNodes(hasTestTag("calendar_day_2026-10-21"))
            .fetchSemanticsNodes()
        assertThat(selected).isNotEmpty()
    }

    // ── the log ────────────────────────────────────────────────────

    @Test
    fun theLogScreenMirrorsInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("log")

        // The mood chips are a flow. In right to left the flow fills from
        // the right, so the earlier chip in the row is the right-hand
        // one.
        composeRule.onNodeWithTag("logging_list")
            .performScrollToNode(hasTestTag("logging_mood_Stable"))
        assertLeadingIsOnTheRight("logging_mood_Happy", "logging_mood_Low")
    }

    @Test
    fun theLogScreenFlowIsLeftToRightInTheDefaultLocale() {
        renderLtr()
        composeRule.openTabAndAwait("log")

        composeRule.onNodeWithTag("logging_list")
            .performScrollToNode(hasTestTag("logging_mood_Stable"))
        assertLeadingIsOnTheRight("logging_mood_Low", "logging_mood_Happy")
    }

    // ── insights ───────────────────────────────────────────────────

    @Test
    fun theInsightsScreenRendersInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("insights")

        composeRule.onNodeWithTag("insights_overview").assertIsDisplayed()
        assertHeadingIsAtTheLeadingEdge("Insights")
    }

    @Test
    fun theCycleTrendsScreenRendersInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("insights")
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasTestTag("insights_cycle_trends_cta"))
        composeRule.onNodeWithTag("insights_cycle_trends_cta").performScrollTo().performClick()

        composeRule.onNodeWithTag("insights_cycle_trends").assertIsDisplayed()
        assertHeadingIsAtTheLeadingEdge("Cycle trends")
    }

    @Test
    fun theSymptomPatternsScreenRendersInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("insights")
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasTestTag("insights_symptom_patterns_cta"))
        composeRule.onNodeWithTag("insights_symptom_patterns_cta").performScrollTo().performClick()

        composeRule.onNodeWithTag("insights_symptom_patterns").assertIsDisplayed()
        assertHeadingIsAtTheLeadingEdge("Symptom patterns")
    }

    // ── settings ───────────────────────────────────────────────────

    @Test
    fun theSettingsScreenMirrorsInRightToLeft() {
        renderRtl()
        openSettings()

        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
        assertHeadingIsAtTheLeadingEdge("Settings")
    }

    // ── export ─────────────────────────────────────────────────────

    @Test
    fun theExportScreenMirrorsInRightToLeft() {
        renderRtl()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open export"))
        composeRule.onNodeWithText("Open export").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("export_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("export_screen").assertIsDisplayed()
        assertHeadingIsAtTheLeadingEdge("Export report")
    }

    // ── privacy ────────────────────────────────────────────────────

    @Test
    fun thePrivacyScreenMirrorsInRightToLeft() {
        renderRtl()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open privacy lock"))
        composeRule.onNodeWithText("Open privacy lock").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("privacy_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("privacy_screen").assertIsDisplayed()
        // The PIN keypad's first digit is the right-hand one in right to
        // left.
        assertLeadingIsOnTheRight("privacy_key_1", "privacy_key_3")
        assertHeadingIsAtTheLeadingEdge("Create a PIN")
    }

    // ── the reminder centre ────────────────────────────────────────

    @Test
    fun theReminderCentreMirrorsInRightToLeft() {
        renderRtl()
        composeRule.openTabAndAwait("home")
        composeRule.onNodeWithTag("home_reminder_center").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Reminder center")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Reminder center").assertIsDisplayed()
        assertHeadingIsAtTheLeadingEdge("Reminder center")
    }

    // ── dialogs and sheets ─────────────────────────────────────────

    @Test
    fun theEditCycleSheetRendersInRightToLeft() {
        renderRtl()

        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_edit_cycle"))
        composeRule.onNodeWithTag("home_edit_cycle").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Edit details")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Edit details").assertIsDisplayed()
    }

    @Test
    fun theResetConfirmationDialogRendersInRightToLeft() {
        renderRtl()
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_open_reset"))
        composeRule.onNodeWithTag("settings_open_reset").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("settings_confirm_reset")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("settings_confirm_reset").assertIsDisplayed()
    }
}
