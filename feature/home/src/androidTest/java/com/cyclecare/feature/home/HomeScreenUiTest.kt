package com.cyclecare.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.domain.CycleRecordValidator
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsPredictionAndFertileWindowText() {
        setHomeContent()

        composeRule.onNodeWithText("Next period in 23 days").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD).performScrollToNode(hasTestTag(HomeTags.FERTILE_CARD))
        composeRule.onNodeWithTag(HomeTags.FERTILE_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("Mar 21 - Mar 26").assertIsDisplayed()
    }

    @Test
    fun progressBarExposesCycleProgressSemantics() {
        setHomeContent()

        composeRule.onNodeWithTag(HomeTags.PROGRESS_BAR).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Day 6 of 28",
            ),
        )
    }

    @Test
    fun heroShowsPredictionSourceContext() {
        setHomeContent()

        composeRule.onNodeWithText(localizedString(R.string.predictions_combine_latest_cycle_with_baseline))
            .assertIsDisplayed()
    }

    @Test
    fun cycleStatusDetailShowsAnimatedHeroAndActions() {
        composeRule.setContent {
            CycleCareTheme {
                HomeCycleStatusScreen(
                    uiState = previewHomeState(),
                    onBack = {},
                    onOpenEditCycle = {},
                    onOpenCalendar = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTags.CYCLE_STATUS_HERO).assertIsDisplayed()
        composeRule.onNodeWithText("Edit cycle").assertIsDisplayed()
        composeRule.onNodeWithText("Open calendar").assertIsDisplayed()
    }

    @Test
    fun quickActionsInvokeNavigationCallbacks() {
        var openedLog = false
        var openedCalendar = false
        var openedInsights = false
        var openedCycleStatus = false
        var openedFertilityDetail = false

        composeRule.setContent {
            CycleCareTheme {
                HomeScreen(
                    uiState = previewHomeState(),
                    onOpenLog = { openedLog = true },
                    onOpenCalendar = { openedCalendar = true },
                    onOpenInsights = { openedInsights = true },
                    onOpenCycleStatus = { openedCycleStatus = true },
                    onOpenFertilityDetail = { openedFertilityDetail = true },
                    reminderUnreadCount = 0,
                    onOpenReminderCenter = {},
                    onOpenEditCycle = {},
                    onExpandEditor = {},
                    onDismissEditor = {},
                    onQuickStartPeriod = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.actionTag(HomeQuickActionType.LogSymptoms)))
        composeRule.onNodeWithTag(HomeTags.actionTag(HomeQuickActionType.LogSymptoms)).performClick()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.actionTag(HomeQuickActionType.OpenCalendar)))
        composeRule.onNodeWithTag(HomeTags.actionTag(HomeQuickActionType.OpenCalendar)).performClick()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.actionTag(HomeQuickActionType.ViewInsights)))
        composeRule.onNodeWithTag(HomeTags.actionTag(HomeQuickActionType.ViewInsights)).performClick()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.CYCLE_STATUS_CTA))
        composeRule.onNodeWithTag(HomeTags.CYCLE_STATUS_CTA).performClick()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.FERTILITY_DETAIL_CTA))
        composeRule.onNodeWithTag(HomeTags.FERTILITY_DETAIL_CTA).performClick()

        assertTrue(openedLog)
        assertTrue(openedCalendar)
        assertTrue(openedInsights)
        assertTrue(openedCycleStatus)
        assertTrue(openedFertilityDetail)
    }

    @Test
    fun invalidEditorStateShowsInlineErrorsAndDisablesSave() {
        val invalidEditor = validateHomeEditorState(
            editor = HomeEditorState(
                isVisible = true,
                isExpanded = true,
                startDateText = LocalDate.now().plusDays(1).toString(),
                endDateText = "bad-date",
                cycleLengthText = "",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        composeRule.setContent {
            CycleCareTheme {
                HomeScreen(
                    uiState = previewHomeState().copy(editorState = invalidEditor),
                    onOpenLog = {},
                    onOpenCalendar = {},
                    onOpenInsights = {},
                    onOpenCycleStatus = {},
                    onOpenFertilityDetail = {},
                    reminderUnreadCount = 0,
                    onOpenReminderCenter = {},
                    onOpenEditCycle = {},
                    onExpandEditor = {},
                    onDismissEditor = {},
                    onQuickStartPeriod = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                )
            }
        }

        composeRule.onNodeWithText("Start date cannot be in the future.").assertIsDisplayed()
        composeRule.onNodeWithText("Use YYYY-MM-DD for the end date.").assertIsDisplayed()
        composeRule.onNodeWithText("Cycle length is required.").assertIsDisplayed()
        composeRule.onNodeWithText("Save cycle").assertIsNotEnabled()
    }

    private fun setHomeContent() {
        composeRule.setContent {
            CycleCareTheme {
                HomeScreen(
                    uiState = previewHomeState(),
                    onOpenLog = {},
                    onOpenCalendar = {},
                    onOpenInsights = {},
                    onOpenCycleStatus = {},
                    onOpenFertilityDetail = {},
                    reminderUnreadCount = 0,
                    onOpenReminderCenter = {},
                    onOpenEditCycle = {},
                    onExpandEditor = {},
                    onDismissEditor = {},
                    onQuickStartPeriod = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                )
            }
        }
    }

    private fun localizedString(resId: Int, vararg args: Any): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *args)

    private fun previewHomeState() = com.cyclecare.feature.home.previewHomeState()
}
