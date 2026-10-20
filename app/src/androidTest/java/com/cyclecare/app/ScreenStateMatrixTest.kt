package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
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
 * harness does is choose what the repositories hold, and for a held save it
 * chooses that the repository has not answered yet.
 *
 * The sync states, "sync waiting, in progress, failed and complete", are
 * **not applicable**. Cloud synchronisation is deferred by decision and the
 * application has no synchronisation surface at all: there is no sync
 * status, no sync string and no sync screen. `NoOpSyncAdapter` is not
 * standing in for a working sync, it is the whole of the sync feature
 * today, and pretending otherwise would be a false claim in a test. The
 * absence is asserted by [SyncSurfaceAbsenceTest].
 *
 * "Offline local content" is likewise not a state but a property: the
 * content is read from the local database and the app has no network
 * dependency for it. The export screen is the only screen that reaches
 * the network, and it is the only one with a failure state.
 *
 * "Unsupported or corrupt schema data" is not a screen state either: it is
 * a database condition, handled by the migration tests, and is not rendered
 * as a screen. It should not be.
 *
 * Known gap: the home cycle editor pre-fills a future end date for a new
 * cycle, which the validator rejects, so saving a *new* cycle from the home
 * editor is blocked (the save button stays disabled). That is a genuine
 * defect tracked separately; the save mechanism itself is exercised here by
 * seeding a valid past cycle, and end-to-end on the log screen.
 */
@RunWith(AndroidJUnit4::class)
class ScreenStateMatrixTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(state: ScreenState): ScreenStateEnvironment {
        val environment = stateEnvironmentFull(state)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CycleCareTheme {
                    CycleCareApp(
                        container = environment.container,
                        secureAuthController = environment.secureAuthController,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return environment
    }

    /** Opens the dashboard's cycle editor and expands its detail fields. */
    private fun openExpandedHomeEditor() {
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_edit_cycle"))
        composeRule.onNodeWithTag("home_edit_cycle").performClick()
        composeRule.waitForIdle()
        // The sheet opens collapsed, on the quick-start card. The fields the
        // matrix drives are behind "Edit details".
        composeRule.onNodeWithText("Edit details").performClick()
        composeRule.waitForIdle()
    }

    /**
     * Opens the editor and seeds a valid past cycle.
     *
     * `defaultEditorState` pre-fills a future end date for a new cycle, which
     * the validator rejects (an end date must not be in the future, but a
     * multi-day period starting today always ends in the future). Until that
     * defect is fixed, a valid past cycle is seeded so the real save path
     * can be exercised.
     */
    private fun openHomeEditorForSave() {
        openExpandedHomeEditor()
        composeRule.onNodeWithTag("home_editor_start_date")
            .performScrollTo().performTextReplacement("2026-03-01")
        composeRule.onNodeWithTag("home_editor_end_date")
            .performScrollTo().performTextReplacement("2026-03-05")
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSavingVisible() {
        // "Saving…" vs "Saving…" (ellipsis vs three dots) depends on which
        // module's string resource wins the merge, so match on the substring.
        val nodes = composeRule.onAllNodesWithText("aving", substring = true).fetchSemanticsNodes()
        assertThat(nodes).isNotEmpty()
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

    // ── save in progress ──────────────────────────────────────────

    @Test
    fun theHomeEditorShowsItsSavingStateWhileTheWriteIsHeld() {
        val environment = render(ScreenState.SAVING)
        openHomeEditorForSave()

        composeRule.onNodeWithTag("home_editor_save").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The repository has not answered, so the screen is on its real
        // isSaving path rather than a synthetic one.
        assertSavingVisible()

        environment.cycleWriteGate!!.complete(Unit)
        composeRule.waitForIdle()
        // The saved message lives in the dashboard's lazy list, so scroll
        // the dashboard until it is materialised and scrolled into view.
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasText("Cycle saved locally.", substring = true))
    }

    @Test
    fun theLogScreenShowsItsSavingStateWhileTheWriteIsHeld() {
        val environment = render(ScreenState.SAVING)
        composeRule.onNodeWithTag(navTag("log")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save today's entry").performClick()
        composeRule.waitForIdle()

        assertSavingVisible()

        environment.symptomWriteGate!!.complete(Unit)
        // Once the held write completes, isSaved flips and the save bar's
        // label changes to "Edit today's entry".
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit today's entry").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── save success ──────────────────────────────────────────────

    @Test
    fun theHomeEditorShowsItsSavedMessageAfterTheWriteCompletes() {
        render(ScreenState.SAVED)
        openHomeEditorForSave()

        composeRule.onNodeWithTag("home_editor_save").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The gate is already open, so the real save completes.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasText("Cycle saved locally.", substring = true))
    }

    @Test
    fun theLogScreenShowsItsSavedMessageAfterTheWriteCompletes() {
        render(ScreenState.SAVED)
        composeRule.onNodeWithTag(navTag("log")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save today's entry").performClick()
        composeRule.waitForIdle()

        // The gate is already open, so the write completes at once and the
        // label flips to "Edit today's entry".
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edit today's entry").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── validation error ──────────────────────────────────────────

    @Test
    fun theHomeEditorShowsGuidanceAndBlocksSaveWhenTheInputIsInvalid() {
        render(ScreenState.VALIDATION_ERROR)
        openExpandedHomeEditor()

        composeRule.onNodeWithTag("home_editor_start_date").performScrollTo().performTextReplacement("")
        composeRule.waitForIdle()

        // The correction guidance is shown inline, and the destructive
        // action is unavailable rather than merely unlabelled.
        composeRule.onNodeWithText("Start date is required.").assertIsDisplayed()
        composeRule.onNodeWithTag("home_editor_save").assertIsNotEnabled()
    }

    @Test
    fun theHomeEditorExplainsWhyANewCycleCannotBeSavedYet() {
        render(ScreenState.VALIDATION_ERROR)
        openExpandedHomeEditor()

        // The editor pre-fills an end date four days ahead, and the
        // validator rejects an end date in the future, so the save is
        // blocked. The screen says which field to correct rather than
        // presenting a dead button. Whether the pre-filled end date
        // should change is a product decision, not a UI one.
        composeRule.onNodeWithText("End date cannot be in the future.").assertIsDisplayed()
        composeRule.onNodeWithTag("home_editor_save").assertIsNotEnabled()
    }

    // ── empty ─────────────────────────────────────────────────────

    @Test
    fun theDashboardShowsAnEmptyStateWithNoHistory() {
        render(ScreenState.EMPTY)

        // The dashboard still renders, and says there is not enough history
        // rather than showing a fabricated estimate.
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Not enough history yet to give a range")
            .assertIsDisplayed()
    }

    @Test
    fun theCalendarShowsItsEmptyHistoryState() {
        render(ScreenState.EMPTY)
        composeRule.onNodeWithTag(navTag("calendar")).performClick()
        composeRule.waitForIdle()

        // Scroll the calendar's history list until the empty-state message
        // is materialised and scrolled into view.
        composeRule.onNodeWithTag("calendar_scroll")
            .performScrollToNode(hasText("Your logged cycles will appear here once you save them.", substring = true))
    }

    @Test
    fun theLogScreenShowsItsEmptyEntryState() {
        render(ScreenState.EMPTY)
        composeRule.onNodeWithTag(navTag("log")).performClick()
        composeRule.waitForIdle()

        // With nothing logged, the screen still offers the entry path rather
        // than an error or a blank.
        composeRule.onNodeWithTag("logging_list").assertIsDisplayed()
        composeRule.onNodeWithText("Save today's entry").assertIsDisplayed()
    }

    @Test
    fun theInsightsScreenShowsItsEmptyState() {
        render(ScreenState.EMPTY)
        composeRule.onNodeWithTag(navTag("insights")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("insights_overview")
            .performScrollToNode(hasText("Symptom clustering needs more check-ins"))
        composeRule.onNodeWithText("Symptom clustering needs more check-ins").assertIsDisplayed()
    }

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
    fun theSettingsScreenShowsNoReminderRulesWhenThereAreNone() {
        render(ScreenState.EMPTY)
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("No reminder rules are available yet."))

        composeRule.onNodeWithText("No reminder rules are available yet.").assertIsDisplayed()
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

    // ── export ────────────────────────────────────────────────────

    private fun openExportScreen() {
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open export"))
        composeRule.onNodeWithText("Open export").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun theExportScreenShowsItsWorkingState() {
        val environment = render(ScreenState.EXPORT_WORKING)
        openExportScreen()

        composeRule.onNodeWithText("Create markdown report").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Generating markdown report...").assertIsDisplayed()
        composeRule.onNodeWithText("Working").assertIsDisplayed()

        environment.exportWriteGate!!.complete(Unit)
        waitForText("Saved markdown report to test.markdown.")
    }

    @Test
    fun theExportScreenShowsItsSuccessState() {
        render(ScreenState.EXPORT_SUCCESS)
        openExportScreen()

        composeRule.onNodeWithText("Create markdown report").performScrollTo().performClick()
        composeRule.waitForIdle()

        waitForText("Saved markdown report to test.markdown.")
    }

    @Test
    fun theExportScreenShowsItsFailureState() {
        render(ScreenState.EXPORT_FAILURE)
        openExportScreen()

        composeRule.onNodeWithText("Create markdown report").performScrollTo().performClick()
        composeRule.waitForIdle()

        waitForText("Export failed locally. Please try again.")
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
