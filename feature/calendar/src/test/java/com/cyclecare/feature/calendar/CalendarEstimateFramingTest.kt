package com.cyclecare.feature.calendar

import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionSnapshot
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The framing the Calendar screen owes an estimate.
 *
 * `11` §8.11: "No screen renders a point estimate without visible
 * 'estimate' framing." Every predicted day the calendar marks has to
 * say that it is an estimate, not a fact.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md section 8.11.
 */
class CalendarEstimateFramingTest {

    private val prediction = PredictionSnapshot(
        generatedAt = LocalDate.of(2026, 3, 16),
        cycleDay = 6,
        nextPeriodDate = LocalDate.of(2026, 4, 8),
        ovulationDate = LocalDate.of(2026, 3, 23),
        fertileWindow = DateRange(
            start = LocalDate.of(2026, 3, 19),
            endInclusive = LocalDate.of(2026, 3, 24),
        ),
        // The calendar gates the fertile marking on `supportsRange`,
        // which is derived from the next-period window. Without one the
        // engine declined to estimate a range, and the calendar must not
        // mark fertile days it never estimated.
        nextPeriodWindow = DateRange(
            start = LocalDate.of(2026, 4, 6),
            endInclusive = LocalDate.of(2026, 4, 10),
        ),
        confidenceLabel = "Narrow range",
        daysUntilNextPeriod = 23,
    )

    @Test
    fun aFutureFertileDaySaysItIsAnEstimate() {
        val summary = buildSelectedDaySummary(
            selectedDate = LocalDate.of(2026, 3, 20),
            selectedCycle = null,
            prediction = prediction,
            today = LocalDate.of(2026, 3, 16),
        )

        assertThat(summary.statusLabel).isEqualTo(UiText.Resource(R.string.summary_status_fertile_window))
        assertThat(summary.detail).isEqualTo(
            UiText.Resource(R.string.summary_fertile_detail_future, listOf(2)),
        )
    }

    @Test
    fun aFutureDateNearTheEstimateIsHedgedRatherThanAsserted() {
        val summary = buildSelectedDaySummary(
            selectedDate = LocalDate.of(2026, 4, 8),
            selectedCycle = null,
            prediction = prediction,
            today = LocalDate.of(2026, 3, 16),
        )

        // "Your period is expected around …", not "Your period starts
        // on …".
        assertThat(summary.detail).isEqualTo(
            UiText.Resource(R.string.summary_future_detail_expected, listOf("Apr 8, 2026")),
        )
    }
}
