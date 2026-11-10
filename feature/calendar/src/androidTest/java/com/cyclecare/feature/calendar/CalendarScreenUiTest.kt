package com.cyclecare.feature.calendar

import com.cyclecare.core.domain.prediction.EstimateQuality
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.domain.prediction.DateWindow
import com.cyclecare.core.domain.prediction.SamplePrediction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class CalendarScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarDayTagsExposePeriodAndOvulationStates() {
        setCalendarContent(initialState = previewCalendarState())

        composeRule.onNodeWithTag(CalendarTags.day(LocalDate.of(2026, 3, 11))).assert(
            stateContains(localizedString(R.string.day_state_period)),
        )
        composeRule.onNodeWithTag(CalendarTags.day(LocalDate.of(2026, 3, 19))).assert(
            stateContains(localizedString(R.string.day_state_fertile)),
        )
        composeRule.onNodeWithTag(CalendarTags.day(LocalDate.of(2026, 3, 23))).assert(
            stateContains(localizedString(R.string.day_state_ovulation)),
        )
    }

    @Test
    fun selectedDayPanelUpdatesWhenDayChanges() {
        setCalendarContent(initialState = previewCalendarState())

        composeRule.onNodeWithTag(CalendarTags.day(LocalDate.of(2026, 3, 23))).performClick()

        composeRule.onNodeWithTag(CalendarTags.SELECTED_DAY_PANEL).assert(
            hasAnyDescendant(hasText(localizedString(R.string.summary_status_ovulation_estimate))),
        )
        composeRule.onNodeWithTag(CalendarTags.day(LocalDate.of(2026, 3, 23))).assert(
            stateContains(localizedString(R.string.day_state_selected)),
        )
    }

    @Test
    fun adjacentMonthDaySelection_updatesMonthAndMarksOutsideMonthStatesCorrectly() {
        setCalendarContent(initialState = previewCalendarState())

        val firstDayOfWeek = WeekFields.of(deviceLocale()).firstDayOfWeek
        val outsideDate = if (firstDayOfWeek == DayOfWeek.MONDAY) {
            LocalDate.of(2026, 2, 23)
        } else {
            LocalDate.of(2026, 4, 1)
        }
        val targetMonth = YearMonth.from(outsideDate)

        composeRule.onNodeWithTag(CalendarTags.day(outsideDate)).assert(
            stateContains(localizedString(R.string.day_state_outside_month)),
        )
        composeRule.onNodeWithTag(CalendarTags.day(outsideDate)).performScrollTo().performClick()

        // The label's text is the point of this test; its visibility
        // depends on where the viewport happens to be scrolled (on wide
        // windows the outside day sits near the fold). Scroll it into view
        // first so the assertion is about the label, not the scroll.
        composeRule.onNodeWithText(monthLabelText(targetMonth))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CalendarTags.day(outsideDate)).assert(
            stateContains(localizedString(R.string.day_state_selected)),
        )
    }

    @Test
    fun monthNavigationUpdatesHeaderWithoutSkippingLabel() {
        var state by mutableStateOf(previewCalendarState())
        composeRule.setContent {
            CycleCareTheme {
                CalendarScreen(
                    uiState = state,
                    onPreviousMonth = {
                        val targetMonth = state.month.minusMonths(1)
                        state = state.copy(
                            month = targetMonth,
                            selectedDate = targetMonth.withAnchoredDay(state.selectedDate.dayOfMonth),
                        )
                    },
                    onNextMonth = {
                        val targetMonth = state.month.plusMonths(1)
                        state = state.copy(
                            month = targetMonth,
                            selectedDate = targetMonth.withAnchoredDay(state.selectedDate.dayOfMonth),
                        )
                    },
                    onSelectDate = { state = state.withSelectedDate(it) },
                    onOpenCreate = {},
                    onOpenEdit = {},
                    onOpenHistoryEdit = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                    onDismissEditor = {},
                    onShowDeleteConfirmation = {},
                    onDismissDeleteConfirmation = {},
                    onDeleteCycle = {},
                )
            }
        }

        composeRule.onNodeWithTag(CalendarTags.MONTH_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(CalendarTags.NEXT_MONTH).performClick()
        composeRule.onNodeWithText(monthLabelText(YearMonth.of(2026, 4))).assertIsDisplayed()
        composeRule.onNodeWithTag(CalendarTags.PREVIOUS_MONTH).performClick()
        composeRule.onNodeWithText(monthLabelText(YearMonth.of(2026, 3))).assertIsDisplayed()
    }

    @Test
    fun invalidEditorShowsInlineErrorsAndDisablesSave() {
        val invalidEditor = validateCycleEditorState(
            editor = CycleEditorState(
                isVisible = true,
                startDateText = LocalDate.now().plusDays(1).toString(),
                endDateText = "bad-date",
                cycleLengthText = "",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        composeRule.setContent {
            CycleCareTheme {
                CalendarScreen(
                    uiState = previewCalendarState().copy(editor = invalidEditor),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDate = {},
                    onOpenCreate = {},
                    onOpenEdit = {},
                    onOpenHistoryEdit = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                    onDismissEditor = {},
                    onShowDeleteConfirmation = {},
                    onDismissDeleteConfirmation = {},
                    onDeleteCycle = {},
                )
            }
        }

        composeRule.onNodeWithText(localizedString(R.string.editor_start_date_future)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.editor_end_date_format, "YYYY-MM-DD")).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.editor_cycle_length_required)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.save_locally)).assertIsNotEnabled()
    }

    @Test
    fun futurePredictionHidesCreateEntryButton() {
        val prediction = SamplePrediction.of(
            generatedForDate = LocalDate.of(2026, 3, 16),
            cycleDay = 6,
            nextPeriodDate = LocalDate.of(2026, 4, 8),
            ovulationDate = LocalDate.of(2026, 3, 23),
            quality = EstimateQuality.MODERATE,
            daysUntilNextPeriod = 23,
            // The calendar marks fertile days from the engine's own
            // window, and the engine only produces one when it supports
            // a range. `supportsRange` is `nextPeriodWindow != null`, so
            // the fixture has to carry a range for the fertile marker to
            // exist at all.
            nextPeriodWindow = DateWindow(
                start = LocalDate.of(2026, 4, 6),
                endInclusive = LocalDate.of(2026, 4, 10),
                halfWidthDays = 2,
            ),
            fertileWindow = DateWindow(
                start = LocalDate.of(2026, 3, 19),
                endInclusive = LocalDate.of(2026, 3, 24),
                halfWidthDays = 3,
            ),
        )

        composeRule.setContent {
            CycleCareTheme {
                CalendarScreen(
                    uiState = CalendarUiState(
                        month = YearMonth.of(2026, 3),
                        selectedDate = LocalDate.of(2026, 3, 23),
                        selectedDaySummary = buildSelectedDaySummary(
                            selectedDate = LocalDate.of(2026, 3, 23),
                            selectedCycle = null,
                            prediction = prediction,
                            today = LocalDate.of(2026, 3, 16),
                        ),
                        prediction = prediction,
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDate = {},
                    onOpenCreate = {},
                    onOpenEdit = {},
                    onOpenHistoryEdit = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                    onDismissEditor = {},
                    onShowDeleteConfirmation = {},
                    onDismissDeleteConfirmation = {},
                    onDeleteCycle = {},
                )
            }
        }

        composeRule.onNodeWithTag(CalendarTags.SELECTED_DAY_PANEL).assert(
            hasAnyDescendant(hasText(localizedString(R.string.summary_status_ovulation_estimate))),
        )
        composeRule.onAllNodesWithText(localizedString(R.string.summary_action_log_period)).assertCountEquals(0)
    }

    private fun setCalendarContent(initialState: CalendarUiState) {
        var state by mutableStateOf(initialState)
        composeRule.setContent {
            CycleCareTheme {
                // The app caps content at 840dp on wide windows
                // (CenterConstrainedBox in the app shell); these tests render
                // the screen without the shell, so the same cap is applied
                // here to keep the test window realistic on tablets and
                // foldables. Modifier order matters: widthIn outer.
                Box(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                ) {
                    CalendarScreen(
                    uiState = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onSelectDate = { state = state.withSelectedDate(it) },
                    onOpenCreate = {},
                    onOpenEdit = {},
                    onOpenHistoryEdit = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onCycleLengthChanged = {},
                    onNotesChanged = {},
                    onSaveCycle = {},
                    onDismissEditor = {},
                    onShowDeleteConfirmation = {},
                    onDismissDeleteConfirmation = {},
                    onDeleteCycle = {},
                )
                }
            }
        }
    }

    private fun previewCalendarState(): CalendarUiState {
        val cycle = CycleRecord(
            id = "cycle-1",
            startDate = LocalDate.of(2026, 3, 11),
            endDate = LocalDate.of(2026, 3, 15),
            cycleLength = 28,
            source = CycleSource.User,
            notes = "Cycle felt regular and symptoms stayed light.",
        )
        val prediction = SamplePrediction.of(
            generatedForDate = LocalDate.of(2026, 3, 16),
            cycleDay = 6,
            nextPeriodDate = LocalDate.of(2026, 4, 8),
            ovulationDate = LocalDate.of(2026, 3, 23),
            quality = EstimateQuality.MODERATE,
            daysUntilNextPeriod = 23,
            // The calendar marks fertile days from the engine's own
            // window, and the engine only produces one when it supports
            // a range. `supportsRange` is `nextPeriodWindow != null`, so
            // the fixture has to carry a range for the fertile marker to
            // exist at all.
            nextPeriodWindow = DateWindow(
                start = LocalDate.of(2026, 4, 6),
                endInclusive = LocalDate.of(2026, 4, 10),
                halfWidthDays = 2,
            ),
            fertileWindow = DateWindow(
                start = LocalDate.of(2026, 3, 19),
                endInclusive = LocalDate.of(2026, 3, 24),
                halfWidthDays = 3,
            ),
        )
        val month = YearMonth.of(2026, 3)
        return CalendarUiState(
            month = month,
            selectedDate = cycle.startDate,
            selectedDaySummary = buildSelectedDaySummary(cycle.startDate, cycle, prediction),
            selectedCycle = cycle,
            cycles = listOf(cycle),
            prediction = prediction,
            historyItems = buildHistoryItems(listOf(cycle)),
        )
    }

    private fun CalendarUiState.withSelectedDate(date: LocalDate): CalendarUiState {
        val selectedCycle = cycles.firstOrNull { date >= it.startDate && date <= it.endDate }
        return copy(
            month = YearMonth.from(date),
            selectedDate = date,
            selectedCycle = selectedCycle,
            selectedDaySummary = buildSelectedDaySummary(date, selectedCycle, prediction),
        )
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

    private fun localizedString(resId: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *args)

    private fun monthLabelText(month: YearMonth): String =
        formatMonthLabel(month, deviceLocale())

    private fun deviceLocale(): Locale =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]
}
