package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The shape of the fertile window.
 *
 * Spec `11` §4.5: the population-typical fertile window is
 * `[ovulation - 4, ovulation + 1]`, widened by the `§4.6` uncertainty
 * and by the irreducible luteal sigma floor, because ovulation is a
 * population prior rather than something the app measures.
 *
 * The window used to be symmetric around ovulation and eight days wide
 * on each side, which is a different window from the one the
 * specification describes, and it omitted the luteal floor entirely.
 */
class PredictionFertileWindowTest {

    private val engine = CyclePredictionEngineV2()

    @Test
    fun theWindowIsThePopulationTypicalOneWidenedByTheUncertainty() {
        val prediction = requireNotNull(engine.predict(inputFor(cycles = 12, mean = 28)))

        val window = requireNotNull(prediction.fertileWindow)
        val halfWidth = requireNotNull(prediction.nextPeriodWindow).halfWidthDays
        val widen = halfWidth + LUTEAL_PHASE_SIGMA_DAYS.toInt()

        assertThat(window.start)
            .isEqualTo(prediction.ovulationDate.minusDays((FERTILE_WINDOW_DAYS_BEFORE_OVULATION + widen).toLong()))
        assertThat(window.endInclusive)
            .isEqualTo(prediction.ovulationDate.plusDays((FERTILE_WINDOW_DAYS_AFTER_OVULATION + widen).toLong()))
    }

    @Test
    fun theWindowIsAsymmetricAroundOvulation() {
        val prediction = requireNotNull(engine.predict(inputFor(cycles = 12, mean = 28)))
        val window = requireNotNull(prediction.fertileWindow)

        val before = java.time.temporal.ChronoUnit.DAYS.between(window.start, prediction.ovulationDate)
        val after = java.time.temporal.ChronoUnit.DAYS.between(prediction.ovulationDate, window.endInclusive)

        // The app is guessing at the days before ovulation and is
        // confident about the day after, so the window reaches further
        // back than it reaches forward.
        assertThat(before).isGreaterThan(after)
    }

    @Test
    fun theLutealSigmaFloorIsApplied() {
        val prediction = requireNotNull(engine.predict(inputFor(cycles = 12, mean = 28)))
        val window = requireNotNull(prediction.fertileWindow)
        val halfWidth = requireNotNull(prediction.nextPeriodWindow).halfWidthDays

        val withoutFloor = java.time.temporal.ChronoUnit.DAYS.between(
            window.start,
            prediction.ovulationDate,
        )
        assertThat(withoutFloor)
            .isEqualTo((FERTILE_WINDOW_DAYS_BEFORE_OVULATION + halfWidth + LUTEAL_PHASE_SIGMA_DAYS.toInt()).toLong())
    }

    @Test
    fun thereIsNoWindowWithoutASupportableEstimate() {
        // Two intervals is below the sufficiency floor, so the engine
        // does not support a range and must not produce one.
        val prediction = requireNotNull(engine.predict(inputFor(cycles = 2, mean = 28)))

        assertThat(prediction.nextPeriodWindow).isNull()
        assertThat(prediction.fertileWindow).isNull()
    }

    private fun inputFor(cycles: Int, mean: Int) = CyclePredictionInput(
        observed = (0 until cycles).map { index ->
            ObservedCycleStart(
                id = "r$index",
                startDate = LocalDate.of(2026, 1, 1).plusDays((index * mean).toLong()),
                endDate = LocalDate.of(2026, 1, 1).plusDays((index * mean).toLong()).plusDays(4),
                source = CycleSource.User,
            )
        },
        userReportedAverageCycleLength = null,
        today = LocalDate.of(2026, 1, 1).plusDays(((cycles - 1) * mean).toLong()),
    )
}
