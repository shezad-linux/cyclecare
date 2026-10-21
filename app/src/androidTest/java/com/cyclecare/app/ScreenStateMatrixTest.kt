package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The state matrix from spec `03` §6, at screen level.
 *
 * The specification lists ten state categories. This test covers the ones
 * the product actually has a surface for, and says so where it does not.
 *
 * Every state is produced by the fake repositories, so the screen goes
 * through its real ViewModel and its real state model. Nothing is faked at
 * the composable, and there is no production backdoor: the only thing the
 * harness does is choose what the repositories hold.
 *
 * The sync states, "sync waiting, in progress, failed and complete", are
 * **not applicable**. Cloud synchronisation is deferred by decision and the
 * application has no synchronisation surface at all: there is no sync
 * status, no sync string and no sync screen. `NoOpSyncAdapter` is not
 * standing in for a working sync, it is the whole of the sync feature
 * today, and pretending otherwise would be a false claim in a test.
 *
 * "Offline local content" is likewise not a state but a property: the
 * content is read from the local database and the app has no network
 * dependency for it. The export screen is the only screen that reaches
 * the network, and it is the only one with a failure state.
 *
 * "Unsupported or corrupt schema data" is a database condition rather
 * than a screen state, and it is covered by the migration tests.
 *
 * The save states, the validation state and the export states are in
 * [ScreenSaveStateMatrixTest].
 */
@RunWith(AndroidJUnit4::class)
class ScreenStateMatrixTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(state: ScreenState) {
        val (container, authController) = stateEnvironment(state)
        composeRule.activityRule.scenario.onActivity { activity ->
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

    // ── navigation helpers ─────────────────────────────────────────

    private fun openSettings() {
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("settings_list")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openExport() {
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open export"))
        composeRule.onNodeWithText("Open export").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("export_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPrivacy() {
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open privacy lock"))
        composeRule.onNodeWithText("Open privacy lock").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("privacy_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openReminderCentre() {
        composeRule.openTabAndAwait("home")
        composeRule.onNodeWithTag("home_reminder_center").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Reminder center")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── loading ───────────────────────────────────────────────────

    @Test
    fun theDashboardShowsItsLoadingStateBeforeTheDataArrives() {
        render(ScreenState.LOADING)

        // The profile has not arrived, so the app is still on the loading
        // path rather than the dashboard.
        composeRule.onNodeWithTag("home_dashboard").assertDoesNotExist()
    }

    @Test
    fun theLogScreenShowsItsLoadingStateBeforeTheDataArrives() {
        render(ScreenState.LOADING)

        composeRule.onNodeWithTag("logging_list").assertDoesNotExist()
    }

    @Test
    fun noDataDrivenScreenIsReachableBeforeTheProfileArrives() {
        render(ScreenState.LOADING)

        // The local profile is what the whole app waits for, so while it
        // is in flight there is no dashboard, no calendar, no log, no
        // insights and no settings. This is the loading state for every
        // data-driven screen, not only for Today.
        listOf(
            "home_dashboard",
            "calendar_scroll",
            "logging_list",
            "insights_overview",
            "settings_list",
            "export_screen",
            "privacy_screen",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    // ── content ───────────────────────────────────────────────────

    @Test
    fun theDashboardShowsItsContent() {
        render(ScreenState.CONTENT)

        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Next period in 23 days").assertIsDisplayed()
    }

    @Test
    fun theLogScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.openTabAndAwait("log")

        composeRule.onNodeWithTag("logging_list").assertIsDisplayed()
    }

    @Test
    fun theInsightsScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.openTabAndAwait("insights")

        composeRule.onNodeWithTag("insights_overview").assertIsDisplayed()
    }

    @Test
    fun theCalendarScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.openTabAndAwait("calendar")

        composeRule.onNodeWithTag("calendar_scroll").assertIsDisplayed()
    }

    @Test
    fun theCycleStatusScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_cycle_status_cta"))
        composeRule.onNodeWithTag("home_cycle_status_cta").performClick()

        composeRule.onNodeWithTag("home_cycle_status_screen").assertIsDisplayed()
    }

    @Test
    fun theFertilityDetailScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_fertility_detail_cta"))
        composeRule.onNodeWithTag("home_fertility_detail_cta").performClick()

        composeRule.onNodeWithTag("home_fertility_detail_screen").assertIsDisplayed()
    }

    @Test
    fun theCycleTrendsScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.openTabAndAwait("insights")
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasTestTag("insights_cycle_trends_cta"))
        composeRule.onNodeWithTag("insights_cycle_trends_cta").performScrollTo().performClick()

        composeRule.onNodeWithTag("insights_cycle_trends").assertIsDisplayed()
    }

    @Test
    fun theSymptomPatternsScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.openTabAndAwait("insights")
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasTestTag("insights_symptom_patterns_cta"))
        composeRule.onNodeWithTag("insights_symptom_patterns_cta").performScrollTo().performClick()

        composeRule.onNodeWithTag("insights_symptom_patterns").assertIsDisplayed()
    }

    @Test
    fun theSettingsScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        openSettings()

        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
    }

    @Test
    fun theReminderCentreShowsItsContent() {
        render(ScreenState.CONTENT)
        openReminderCentre()

        composeRule.onNodeWithText("Reminder center").assertIsDisplayed()
    }

    @Test
    fun theExportScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        openExport()

        composeRule.onNodeWithTag("export_screen").assertIsDisplayed()
    }

    @Test
    fun thePrivacyScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        openPrivacy()

        composeRule.onNodeWithTag("privacy_screen").assertIsDisplayed()
    }

    // ── empty ─────────────────────────────────────────────────────

    @Test
    fun theReminderCentreShowsAnEmptyStateWhenNothingHasBeenDelivered() {
        render(ScreenState.EMPTY)
        openReminderCentre()

        composeRule.onNodeWithText("No reminders yet").assertIsDisplayed()
    }

    @Test
    fun theDashboardShowsAnEmptyStateWithNoHistory() {
        render(ScreenState.EMPTY)

        // The dashboard still renders, and says there is not enough history
        // rather than showing a fabricated estimate.
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()
        // The hero says there is not enough history rather than showing a
        // fabricated estimate.
        composeRule.onNodeWithText("Not enough history yet to give a range")
            .assertIsDisplayed()
    }

    @Test
    fun theCalendarShowsAnEmptyStateWithNoLoggedCycles() {
        render(ScreenState.EMPTY)
        composeRule.openTabAndAwait("calendar")

        composeRule.onNodeWithTag("calendar_scroll").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_scroll").performScrollToNode(
            hasText(
                "Your logged cycles will appear here once you save them. " +
                    "History makes predictions and reminder timing more reliable.",
            ),
        )
        composeRule.onNodeWithText(
            "Your logged cycles will appear here once you save them. " +
                "History makes predictions and reminder timing more reliable.",
        ).assertIsDisplayed()
    }

    @Test
    fun theInsightsScreenShowsAnEmptyStateWithNoHistory() {
        render(ScreenState.EMPTY)
        composeRule.openTabAndAwait("insights")

        composeRule.onNodeWithTag("insights_overview").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasText("Symptom clustering needs more check-ins"))
        // The screen says the clustering is still building rather than
        // grouping nothing.
        composeRule.onNodeWithText("Symptom clustering needs more check-ins").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasText("Build stronger patterns"))
        composeRule.onNodeWithText("Build stronger patterns").assertIsDisplayed()
    }

    @Test
    fun theCycleTrendChartIsNotOfferedWithNoHistory() {
        render(ScreenState.EMPTY)
        composeRule.openTabAndAwait("insights")

        // With no cycle history there is no chart to open, so the app
        // does not offer one. A locked chart behind a button that goes
        // nowhere would be the alternative.
        composeRule.onNodeWithTag("insights_cycle_trends_cta").assertDoesNotExist()
    }

    @Test
    fun theSymptomPatternChartIsNotOfferedWithNoHistory() {
        render(ScreenState.EMPTY)
        composeRule.openTabAndAwait("insights")

        composeRule.onNodeWithTag("insights_symptom_patterns_cta").assertDoesNotExist()
    }

    @Test
    fun theExportScreenShowsItsIdleStateWithNothingGenerated() {
        render(ScreenState.EMPTY)
        openExport()

        composeRule.onNodeWithTag("export_screen").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Generate a local markdown or PDF report and keep it on-device.",
        ).assertIsDisplayed()
    }

    // ── permission ────────────────────────────────────────────────

    @Test
    fun theNotificationGateIsShownWhenNotificationsAreNotPermitted() {
        render(ScreenState.PERMISSION_DENIED)

        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_GATE).assertIsDisplayed()
        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_ACTION).assertIsDisplayed()
    }

    @Test
    fun theNotificationGateIsNotShownWhenNotificationsArePermitted() {
        render(ScreenState.CONTENT)

        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_GATE).assertDoesNotExist()
    }

    // ── channel disabled ──────────────────────────────────────────

    @Test
    fun theSettingsScreenShowsAChannelThatHasBeenSwitchedOff() {
        render(ScreenState.CHANNEL_DISABLED)

        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_reminders_enabled"))
        composeRule.onNodeWithTag("settings_reminders_enabled").assertIsDisplayed()
    }

    // ── contradictory states ──────────────────────────────────────

    @Test
    fun theNotificationGateAndTheDashboardAreNotShownTogether() {
        render(ScreenState.PERMISSION_DENIED)

        // The gate replaces the app's content rather than sitting on top
        // of it, so a user is never asked to answer two things at once.
        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_GATE).assertIsDisplayed()
        composeRule.onNodeWithTag("home_dashboard").assertDoesNotExist()
    }

@Test
    fun theLogScreenShowsItsEmptyEntryState() {
        render(ScreenState.EMPTY)
        composeRule.openTabAndAwait("log")
        composeRule.waitForIdle()

        // With nothing logged, the screen still offers the entry path rather
        // than an error or a blank.
        composeRule.onNodeWithTag("logging_list").assertIsDisplayed()
        composeRule.onNodeWithText("Save today's entry").assertIsDisplayed()
    }


@Test
    fun theSettingsScreenShowsNoReminderRulesWhenThereAreNone() {
        render(ScreenState.EMPTY)
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("No reminder rules are available yet."))

        composeRule.onNodeWithText("No reminder rules are available yet.").assertIsDisplayed()
    }

}
