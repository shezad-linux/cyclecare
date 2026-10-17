package com.cyclecare.feature.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.cyclecare.core.designsystem.CycleCareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InsightsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overviewShowsAdvancedCardsAndInvokesDetailCtas() {
        var openedCycleTrends = false
        var openedSymptomPatterns = false

        composeRule.setContent {
            CycleCareTheme {
                InsightsOverviewScreen(
                    uiState = previewInsightsUiState(),
                    onOpenCycleTrends = { openedCycleTrends = true },
                    onOpenSymptomPatterns = { openedSymptomPatterns = true },
                )
            }
        }

        composeRule.onNodeWithTag(InsightsTags.OverviewScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("Cycle variability"))
        composeRule.onNodeWithText("Cycle variability").assertIsDisplayed()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("Recent cycle trends"))
        composeRule.onNodeWithText("Recent cycle trends").assertIsDisplayed()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("Symptom patterns"))
        composeRule.onNodeWithText("Symptom patterns").assertIsDisplayed()

        composeRule.onNodeWithTag(InsightsTags.CycleTrendsCta).performClick()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("View symptom patterns"))
        composeRule.onNodeWithTag(InsightsTags.SymptomPatternsCta).performClick()

        assertTrue(openedCycleTrends)
        assertTrue(openedSymptomPatterns)
    }

    @Test
    fun lowDataOverviewShowsGuidanceStates() {
        composeRule.setContent {
            CycleCareTheme {
                InsightsOverviewScreen(
                    uiState = previewLowDataInsightsUiState(),
                    onOpenCycleTrends = {},
                    onOpenSymptomPatterns = {},
                )
            }
        }

        composeRule.onNodeWithTag(InsightsTags.OverviewScreen).assertIsDisplayed()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("Trend charts are still building"))
        composeRule.onNodeWithText("Trend charts are still building").assertIsDisplayed()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("Symptom clustering needs more check-ins"))
        composeRule.onNodeWithText("Symptom clustering needs more check-ins").assertIsDisplayed()
        composeRule.onNodeWithTag(InsightsTags.OverviewScreen)
            .performScrollToNode(hasText("Build stronger patterns"))
        composeRule.onNodeWithText("Build stronger patterns").assertIsDisplayed()
    }
}
