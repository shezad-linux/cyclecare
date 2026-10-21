package com.cyclecare.feature.home

import com.cyclecare.core.domain.prediction.DateWindow
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The framing the Today screen owes an estimate.
 *
 * `11` §8.11: "No screen renders a point estimate without visible
 * 'estimate' framing." A point estimate is a single date the user could
 * read as fact, so it must never reach the screen on its own: either it
 * arrives with the range it was derived from, or it arrives with an
 * honest statement that there is no range yet.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md section 8.11.
 */
class HomeEstimateFramingTest {

    @Test
    fun thePointDateIsShownWithTheRangeItWasDerivedFrom() {
        val state = HomeUiState(
            nextPeriodDateText = UiText.Raw("Apr 9"),
            supportsRange = true,
            nextPeriodRangeText = formatNextPeriodRange(
                range = com.cyclecare.core.domain.prediction.DateWindow(
                    start = java.time.LocalDate.parse("2026-04-07"),
                    endInclusive = java.time.LocalDate.parse("2026-04-11"),
                halfWidthDays = 3,
            ),
                supportsRange = true,
                halfWidthDays = 2,
            ),
        )

        // The range carries its own half-width, so the reader can see
        // how wide the estimate is rather than being handed a date.
        assertThat(state.nextPeriodRangeText).isEqualTo(
            uiText(R.string.date_range_with_halfwidth, "Apr 7", "Apr 11", 2),
        )
        assertThat(homeHeroSubtitle(state)).isEqualTo(
            uiText(R.string.expected_date, UiText.Raw("Apr 9")),
        )
    }

    @Test
    fun aPointDateWithoutARangeIsAnnouncedAsHavingNoRange() {
        val state = HomeUiState(
            nextPeriodDateText = UiText.Raw("Apr 9"),
            supportsRange = false,
        )

        assertThat(formatNextPeriodRange(range = null, supportsRange = false, halfWidthDays = null))
            .isEqualTo(uiText(R.string.not_enough_history_yet_for_a_range))
        assertThat(homeHeroSubtitle(state)).isEqualTo(
            uiText(R.string.date_not_enough_history_for_a_range, UiText.Raw("Apr 9")),
        )
    }

    @Test
    fun theFertileWindowIsUnavailableRatherThanInvented() {
        // The engine declines to emit a window when the history is too
        // thin. The screen must say so rather than draw a window of its
        // own.
        // The helper takes the flag as well as the window, because a
        // window the engine never produced must not be drawn even when
        // one is present on the snapshot.
        assertThat(formatFertileWindow(null, supportsRange = false))
            .isEqualTo(uiText(R.string.not_enough_history_for_fertile_days))
        assertThat(formatFertileWindow(null, supportsRange = true))
            .isEqualTo(uiText(R.string.not_enough_history_for_fertile_days))
    }
}
