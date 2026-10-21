package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The save, validation and export states from spec `03` §6, at screen
 * level.
 *
 * `03` §6 asks for "save in progress and save success" and "validation
 * error with correction guidance". The ViewModels have always produced
 * those states, and their own module suites have always asserted them;
 * what was missing was the screen-level assertion, which is what the
 * state matrix is for.
 *
 * The export screen's working, success and failure states were not in the
 * matrix at all. The export screen is the only screen that reaches the
 * network, so it is the only one with a failure state worth asserting.
 *
 * Every state is produced by the real ViewModel reading from the fake
 * repositories. The export repository is told to hang or to fail, and the
 * symptom repository is told to hang, so the screen reaches the state
 * through its own code rather than through a state object handed to it.
 */
@RunWith(AndroidJUnit4::class)
class ScreenSaveStateMatrixTest {

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

    private fun openSettings() {
        composeRule.onNodeWithTag("app_bar_settings").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("settings_list")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openExport() {
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open export"))
        composeRule.onNodeWithText("Open export").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("export_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A matcher on a node's state description, which is what a screen reader reads. */
    private fun stateContains(value: String): SemanticsMatcher = SemanticsMatcher(
        description = "state description contains $value",
    ) { node ->
        node.config.getOrNull(SemanticsProperties.StateDescription)?.contains(value) ?: false
    }

    /** A matcher on the platform's error state. */
    private fun hasErrorState(): SemanticsMatcher = SemanticsMatcher(
        description = "is marked as an error",
    ) { node ->
        node.config.getOrNull(SemanticsProperties.Error) != null
    }

    /** The design system owns the typography, so its resource is the one to read. */
    private fun savingLabel(): String = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getString(com.cyclecare.core.designsystem.R.string.saving)

    // ── save in progress ──────────────────────────────────────────

    @Test
    fun theLogScreenShowsItsSaveInProgressState() {
        render(ScreenState.SAVING)
        composeRule.openTabAndAwait("log")

        composeRule.onNodeWithText("Save today's entry").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(savingLabel())).fetchSemanticsNodes().isNotEmpty()
        }

        // The repository never completes the write, so the screen stays
        // in its saving state for as long as the test needs it to.
        composeRule.onNodeWithText(savingLabel()).assertIsDisplayed()
        // The action is disabled while the write is in flight, so the
        // user cannot start a second one.
        composeRule.onNodeWithText(savingLabel()).assertIsNotEnabled()
    }

    // ── save success ──────────────────────────────────────────────

    @Test
    fun theLogScreenShowsItsSaveSuccessState() {
        render(ScreenState.SAVED)
        composeRule.openTabAndAwait("log")

        composeRule.onNodeWithText("Save today's entry").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Edit today's entry")).fetchSemanticsNodes().isNotEmpty()
        }

        // The success is more than a toast: the screen keeps saying so.
        // `03` §6 says a transient toast must not be the only evidence
        // that a health record was saved.
        composeRule.onNodeWithText("Edit today's entry").assertIsDisplayed()
        // The completed action is marked, so the state is visible in the
        // control itself and not only in the message beside it.
        composeRule.onNodeWithText("✓").assertIsDisplayed()
    }

    // ── validation error ──────────────────────────────────────────

    @Test
    fun theLogScreenShowsItsValidationErrorWithCorrectionGuidance() {
        render(ScreenState.VALIDATION_ERROR)
        composeRule.openTabAndAwait("log")

        composeRule.onNodeWithTag("logging_list")
            .performScrollToNode(hasText("Add medication or notes"))
        composeRule.onNodeWithText("Add medication or notes").performScrollTo().performClick()
        // The field is focused first: the supporting text, which is where
        // the message lives, is only on screen while the field has focus.
        composeRule.onNodeWithTag("logging_notes_field").performScrollTo().performClick()
        composeRule.onNodeWithTag("logging_notes_field").performTextReplacement("x".repeat(501))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(stateContains("Notes can be up to 500 characters."))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The message says what the limit is, so the user can correct it
        // without counting characters. It is on the field's state
        // description, which is what a screen reader reads out, and the
        // field is marked as an error.
        composeRule.onNodeWithTag("logging_notes_field")
            .assert(stateContains("Notes can be up to 500 characters."))
        composeRule.onNodeWithTag("logging_notes_field").assert(hasErrorState())

        // The supporting text sits below the field, so it can be
        // clipped by the bottom of the viewport depending on where the
        // list happens to be scrolled. It is scrolled into view rather
        // than asserted in whatever position the previous interaction
        // left it.
        composeRule.onNodeWithText("Notes can be up to 500 characters.").performScrollTo()
        composeRule.onNodeWithText("Notes can be up to 500 characters.").assertIsDisplayed()
    }

    @Test
    fun theDashboardEditorShowsItsValidationErrorWithCorrectionGuidance() {
        render(ScreenState.VALIDATION_ERROR)

        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_edit_cycle"))
        composeRule.onNodeWithTag("home_edit_cycle").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Edit details")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edit details").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("home_editor_cycle_length"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_editor_cycle_length").performScrollTo()
            .performTextReplacement("99")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Cycle length must stay between 20 and 45 days."))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The message says what the range is, rather than only that the
        // value is wrong.
        composeRule.onNodeWithText("Cycle length must stay between 20 and 45 days.")
            .assertIsDisplayed()
    }

    // ── export ────────────────────────────────────────────────────

    @Test
    fun theExportScreenShowsItsWorkingState() {
        render(ScreenState.EXPORT_WORKING)
        openExport()

        composeRule.onNodeWithText("Create markdown report").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Generating markdown report..."))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Generating markdown report...").assertIsDisplayed()
        // The action is disabled while the write is in flight, so the
        // user cannot start a second one.
        composeRule.onNodeWithText("Create markdown report").assertIsNotEnabled()
    }

    @Test
    fun theExportScreenShowsItsSuccessState() {
        render(ScreenState.EXPORT_SUCCEEDED)
        openExport()

        composeRule.onNodeWithText("Create markdown report").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Saved markdown report to test.markdown."))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Saved markdown report to test.markdown.").assertIsDisplayed()
        composeRule.onNodeWithText("Open latest export").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theExportScreenShowsItsFailureState() {
        render(ScreenState.EXPORT_FAILED)
        openExport()

        composeRule.onNodeWithText("Create markdown report").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Export failed locally. Please try again."))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The failure says what happened and the action is available
        // again. It is an inline message, not a transient one.
        composeRule.onNodeWithText("Export failed locally. Please try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Create markdown report").assertIsEnabled()
    }

    @Test
    fun theSaveStatesAreNotShownWhenTheScreenIsIdle() {
        render(ScreenState.CONTENT)
        composeRule.openTabAndAwait("log")

        // Nothing has been saved yet, so neither the saving state nor the
        // saved state is on the screen.
        assertThat(composeRule.onAllNodes(hasText(savingLabel())).fetchSemanticsNodes()).isEmpty()
        assertThat(
            composeRule.onAllNodes(hasText("Edit today's entry")).fetchSemanticsNodes(),
        ).isEmpty()
    }
}
