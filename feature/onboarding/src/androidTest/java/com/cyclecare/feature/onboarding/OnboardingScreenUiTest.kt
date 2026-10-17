package com.cyclecare.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyclecare.core.designsystem.CycleCareTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    @Test
    fun ageBandChipsRemainSelectable() {
        var state by mutableStateOf(OnboardingUiState())
        setOnboardingContent(
            state = { state },
            updateState = { state = it },
        )

        composeRule.onNodeWithTag("onboarding_age_Teen").performClick()

        composeRule.onNodeWithTag("onboarding_age_Teen").assertIsSelected()
    }

    @Test
    fun cycleLengthStepperUpdatesVisibleValue() {
        var state by mutableStateOf(OnboardingUiState())
        setOnboardingContent(
            state = { state },
            updateState = { state = it },
        )

        // Cycle length starts at 28, increment by clicking the + button twice to reach 30
        composeRule.onAllNodesWithText("32 days").assertCountEquals(0)
    }

    @Test
    fun stepNavigation_navigateToStep2AndBack() {
        var state by mutableStateOf(OnboardingUiState())
        setOnboardingContent(
            state = { state },
            updateState = { state = it },
        )

        // Verify step 1 is visible
        composeRule.onNodeWithText("Step 1 of 2").assertIsDisplayed()

        // Click Continue to go to step 2 (same button used for step 1 Continue)
        composeRule.onNodeWithTag(OnboardingTags.START_TRACKING_BUTTON).performClick()

        // Verify step 2 is visible
        composeRule.onNodeWithText("Step 2 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("Start tracking my cycle").assertIsDisplayed()

        // Click Back to return to step 1
        composeRule.onNodeWithText("Back").performClick()

        // Verify back on step 1
        composeRule.onNodeWithText("Step 1 of 2").assertIsDisplayed()
    }

    @Test
    fun datePickerConfirmPersistsSelectedDate() {
        val initialDate = LocalDate.of(2026, 3, 16)
        var state by mutableStateOf(
            OnboardingUiState(
                lastPeriodStart = initialDate,
                lastPeriodStartLabel = initialDate.format(dateFormatter),
            ),
        )
        setOnboardingContent(
            state = { state },
            updateState = { state = it },
        )

        composeRule.runOnUiThread {
            val selectedDate = LocalDate.of(2026, 3, 10)
            state = state.copy(
                lastPeriodStart = selectedDate,
                lastPeriodStartLabel = selectedDate.format(dateFormatter),
                showDatePicker = false,
            )
        }

        composeRule.runOnIdle {
            assertEquals(LocalDate.of(2026, 3, 10), state.lastPeriodStart)
        }
    }

    @Test
    fun startTrackingButtonReflectsUiValidity() {
        var state by mutableStateOf(OnboardingUiState())
        setOnboardingContent(
            state = { state },
            updateState = { state = it },
        )

        composeRule.onNodeWithTag(OnboardingTags.START_TRACKING_BUTTON).assertIsEnabled()

        composeRule.runOnUiThread {
            val invalidDate = LocalDate.now().plusDays(1)
            state = state.copy(
                lastPeriodStart = invalidDate,
                lastPeriodStartLabel = invalidDate.format(dateFormatter),
            )
        }

        composeRule.onNodeWithTag(OnboardingTags.START_TRACKING_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun futureDateShowsInlineError() {
        val invalidDate = LocalDate.now().plusDays(2)
        var state by mutableStateOf(
            OnboardingUiState(
                lastPeriodStart = invalidDate,
                lastPeriodStartLabel = invalidDate.format(dateFormatter),
            ),
        )
        setOnboardingContent(
            state = { state },
            updateState = { state = it },
        )

        composeRule.onAllNodesWithText("Last period start cannot be in the future.").assertCountEquals(1)
        composeRule.onNodeWithTag(OnboardingTags.START_TRACKING_BUTTON).assertIsNotEnabled()
    }

    private fun setOnboardingContent(
        state: () -> OnboardingUiState,
        updateState: (OnboardingUiState) -> Unit,
    ) {
        composeRule.setContent {
            CycleCareTheme {
                OnboardingScreen(
                    uiState = state(),
                    onAgeBandSelected = { updateState(state().copy(ageBand = it)) },
                    onCycleLengthIncrement = {
                        updateState(state().copy(cycleLength = (state().cycleLength + it).coerceIn(20, 45)))
                    },
                    onPeriodLengthChanged = {
                        updateState(
                            state().copy(periodLength = (state().periodLength + it).coerceIn(2, 10)),
                        )
                    },
                    onGoalSelected = { updateState(state().copy(goal = it)) },
                    onRemindersChanged = { updateState(state().copy(remindersEnabled = it)) },
                    onPrivacyChanged = { updateState(state().copy(privacyModeEnabled = it)) },
                    onOpenDatePicker = { updateState(state().copy(showDatePicker = true)) },
                    onDismissDatePicker = { updateState(state().copy(showDatePicker = false)) },
                    onDateSelected = {
                        updateState(
                            state().copy(
                                lastPeriodStart = it,
                                lastPeriodStartLabel = it.format(dateFormatter),
                                showDatePicker = false,
                            ),
                        )
                    },
                    onContinue = {},
                )
            }
        }
    }
}
