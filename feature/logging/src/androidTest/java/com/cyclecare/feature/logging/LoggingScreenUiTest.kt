package com.cyclecare.feature.logging

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.cyclecare.core.designsystem.CycleCareTheme
import org.junit.Rule
import org.junit.Test

class LoggingScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notesOverflowShowsInlineErrorAndDisablesSave() {
        val notes = "a".repeat(MAX_SYMPTOM_NOTE_LENGTH + 1)
        composeRule.setContent {
            CycleCareTheme {
                LoggingScreen(
                    uiState = buildLoggingUiState(
                        today = java.time.LocalDate.of(2026, 3, 16),
                        profile = null,
                        prediction = null,
                        todayEntry = null,
                        editor = LoggingEditorState(
                            notes = notes,
                            isDetailsExpanded = true,
                        ),
                        saveMessage = null,
                        isSaving = false,
                    ),
                    onMoodSelected = {},
                    onPainSelected = {},
                    onEnergySelected = {},
                    onDischargeSelected = {},
                    onMedicationChanged = {},
                    onNotesChanged = {},
                    onToggleDetailsExpanded = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag(LoggingTags.LIST)
            .performScrollToNode(hasTestTag(LoggingTags.NotesField))
        composeRule.onNodeWithTag(LoggingTags.NotesField).assert(
            stateContains("Notes can be up to 500 characters."),
        )
        composeRule.onNodeWithText("Save today's entry").assertIsNotEnabled()
    }

    @Test
    fun polishedHeaderAndSaveRailRenderForExistingEntry() {
        composeRule.setContent {
            CycleCareTheme {
                LoggingScreen(
                    uiState = previewLoggingUiState(hasExistingEntry = true),
                    onMoodSelected = {},
                    onPainSelected = {},
                    onEnergySelected = {},
                    onDischargeSelected = {},
                    onMedicationChanged = {},
                    onNotesChanged = {},
                    onToggleDetailsExpanded = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag(LoggingTags.HeroCard).assertExists()
        composeRule.onNodeWithText("Editing today's saved check-in").assertExists()
        composeRule.onNodeWithTag(LoggingTags.SaveBar).assertExists()
        composeRule.onNodeWithText("Update today's entry").assertExists()
    }

    private fun stateContains(value: String): SemanticsMatcher = SemanticsMatcher(
        description = "state contains $value",
    ) { semanticsNode ->
        if (SemanticsProperties.StateDescription in semanticsNode.config) {
            semanticsNode.config[SemanticsProperties.StateDescription].contains(value)
        } else {
            false
        }
    }
}
