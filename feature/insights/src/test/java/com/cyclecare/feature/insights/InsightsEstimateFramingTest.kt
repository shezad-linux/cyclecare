package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.domain.prediction.averageCycleLengthOf
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * What the Insights screen is allowed to show.
 *
 * Two things are pinned here.
 *
 * First, the "Avg cycle" figure: it is the canonical domain figure, so
 * the same label cannot mean two different numbers on two screens.
 *
 * Second, the screen renders no point estimate at all. Every figure it
 * shows comes from observed history, so there is nothing on it that
 * needs an estimate frame.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md sections 8.9
 * and 8.11.
 */
class InsightsEstimateFramingTest {

    /**
     * The stored cycle length contradicts the observed gaps on purpose:
     * 50, 51 and 52 against an observed gap of 28 days throughout.
     */
    private val cycles = listOf(
        CycleRecord(
            id = "c1",
            startDate = LocalDate.parse("2026-01-01"),
            endDate = LocalDate.parse("2026-01-05"),
            cycleLength = 50,
            source = CycleSource.User,
        ),
        CycleRecord(
            id = "c2",
            startDate = LocalDate.parse("2026-01-29"),
            endDate = LocalDate.parse("2026-02-02"),
            cycleLength = 51,
            source = CycleSource.User,
        ),
        CycleRecord(
            id = "c3",
            startDate = LocalDate.parse("2026-02-26"),
            endDate = LocalDate.parse("2026-03-02"),
            cycleLength = 52,
            source = CycleSource.User,
        ),
    )

    @Test
    fun theAverageCycleFigureIsTheCanonicalDomainFigure() {
        val snapshot = InsightEngine().build(cycles, emptyList())
        val uiState = buildInsightsUiState(snapshot)

        assertThat(averageCycleLengthOf(cycles)).isEqualTo(28)
        assertThat(uiState.averageCycleText).isEqualTo(pluralUiText(R.plurals.n_days, 28, 28))
    }

    @Test
    fun everyFigureOnThisScreenComesFromObservedHistory() {
        val snapshot = InsightEngine().build(cycles, emptyList())
        val uiState = buildInsightsUiState(snapshot)

        // The trend bars are the observed start-to-start intervals, not
        // a prediction and not the stored column.
        assertThat(uiState.cycleTrendBars.map { it.value }).isEqualTo(listOf(28, 28))
        assertThat(uiState.cycleTrendBars.map { it.value }).doesNotContain(50)
    }
}
