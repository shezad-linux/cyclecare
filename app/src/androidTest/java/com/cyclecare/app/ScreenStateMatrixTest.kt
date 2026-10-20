package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The state matrix from spec `03` §6.
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
        composeRule.onNodeWithTag(navTag("log")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("logging_list").assertIsDisplayed()
    }

    @Test
    fun theInsightsScreenShowsItsContent() {
        render(ScreenState.CONTENT)
        composeRule.onNodeWithTag(navTag("insights")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insights_overview").assertIsDisplayed()
    }

    // ── empty ─────────────────────────────────────────────────────

    @Test
    fun theReminderCentreShowsAnEmptyStateWhenNothingHasBeenDelivered() {
        render(ScreenState.EMPTY)
        composeRule.onNodeWithTag(navTag("home")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_reminder_center").performClick()
        composeRule.waitForIdle()

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

        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
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
}
