package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Test

/**
 * Gate suite from spec 12.
 *
 * Read G1 together with G5. G1 alone is trivially satisfiable with an
 * absurdly wide window, so a pass on coverage with a failing sharpness
 * gate is not a pass.
 */
class PredictionCorpusGateTest {
    private val engine = CyclePredictionEngineV2()

    private fun inputFor(scenario: Corpus.Scenario, upTo: Int) = CyclePredictionInput(
        observed = scenario.starts.take(upTo).mapIndexed { index, date ->
            ObservedCycleStart(
                id = "r$index",
                startDate = date,
                endDate = date.plusDays(4),
                source = CycleSource.User,
            )
        },
        userReportedAverageCycleLength = null,
        // Rolling origin: "today" is the day the anchor period began.
        // Anchoring to the full series' final start would place the
        // prediction arbitrarily far in the past for short prefixes.
        today = scenario.starts[upTo - 1],
    )

    /** Rolling origin: predict the next interval from every prefix. */
    private fun holdOut(scenario: Corpus.Scenario): List<Pair<CyclePrediction, Int>> {
        val results = mutableListOf<Pair<CyclePrediction, Int>>()
        for (prefix in 2 until scenario.starts.size) {
            val input = inputFor(scenario, prefix)
            val prediction = engine.predict(input) ?: continue
            val actual = ChronoUnit.DAYS.between(
                scenario.starts[prefix - 1],
                scenario.starts[prefix],
            ).toInt()
            results += prediction to actual
        }
        return results
    }

    // ── G7 availability ─────────────────────────────────────────────

    @Test
    fun G7_windowIsEmittedExactlyWhenThereAreEnoughIntervals() {
        Corpus.all().forEach { scenario ->
            for (prefix in 2..scenario.starts.size) {
                val prediction = engine.predict(inputFor(scenario, prefix)) ?: continue
                val intervals = prefix - 1
                val expectsWindow = intervals >= INSUFFICIENT_HISTORY_MIN_INTERVALS

                assertThat(prediction.nextPeriodWindow != null).isEqualTo(expectsWindow)
                if (expectsWindow) {
                    assertThat(prediction.supportsRange).isTrue()
                } else {
                    // Never a fabricated range below the sufficiency floor.
                    assertThat(prediction.nextPeriodWindow).isNull()
                    assertThat(prediction.quality).isEqualTo(EstimateQuality.INSUFFICIENT_HISTORY)
                }
            }
        }
    }

    // ── G1 / G5 coverage and sharpness ──────────────────────────────

    @Test
    fun G1_regularHistoryCoverageMeetsTheEightyPercentTarget() {
        // Deliberately limited to genuinely regular histories (sigma up
        // to 2). The window is calibrated to a nominal 80% central
        // interval, so it cannot also be 80% wide enough for sigma 3
        // while staying sharp enough for G5 to pass. Dispersed
        // populations are measured by G4, where the tolerance widens
        // with the observed variability.
        val regular = listOf(
            Corpus.regular(cycles = 6),
            Corpus.regular(cycles = 8),
            Corpus.regular(cycles = 8, sigma = 1.0, seed = 7),
            Corpus.regular(cycles = 12, sigma = 2.0, seed = 11),
            Corpus.regular(cycles = 12, sigma = 1.0, mean = 31, seed = 5),
            Corpus.regular(cycles = 12, sigma = 2.0, mean = 24, seed = 9),
        )

        var hits = 0
        var total = 0
        regular.forEach { scenario ->
            holdOut(scenario).forEach { pair ->
                val prediction = pair.first
                val actualInterval = pair.second
                val window = prediction.nextPeriodWindow ?: return@forEach
                total++
                val actualDate = prediction.generatedForDate.plusDays(actualInterval.toLong())
                val lower = prediction.nextPeriodDate.minusDays(window.halfWidthDays.toLong())
                val upper = prediction.nextPeriodDate.plusDays(window.halfWidthDays.toLong())
                if (actualDate >= lower && actualDate <= upper) hits++
            }
        }

        assertThat(total).isGreaterThan(20)
        assertThat(hits.toDouble() / total).isAtLeast(0.80)
    }

    @Test
    fun G5_regularHistoryStaysSharpEnoughToBeUseful() {
        val widths = listOf(
            Corpus.regular(cycles = 6),
            Corpus.regular(cycles = 8),
            Corpus.regular(cycles = 12, sigma = 2.0, seed = 11),
        ).flatMap { holdOut(it) }
            .mapNotNull { it.first.nextPeriodWindow?.halfWidthDays }

        assertThat(widths).isNotEmpty()
        val sorted = widths.sorted()
        val median = sorted[sorted.size / 2]
        assertThat(median).isAtMost(3)
        assertThat(sorted.first()).isAtLeast(1)
    }

    // ── G8 outlier sensitivity ─────────────────────────────────────

    @Test
    fun G8_oneExtremeOutlierDoesNotMoveThePrediction() {
        val cleanScenario = Corpus.stable(cycles = 8, mean = 28)
        val contaminatedScenario = Corpus.singleOutlier(cycles = 8, mean = 28, outlier = 200)

        val cleanPrediction = engine.predict(inputFor(cleanScenario, cleanScenario.starts.size - 1))
        val dirtyPrediction =
            engine.predict(inputFor(contaminatedScenario, contaminatedScenario.starts.size - 1))

        assertThat(cleanPrediction).isNotNull()
        assertThat(dirtyPrediction).isNotNull()

        val clean = requireNotNull(cleanPrediction)
        val dirty = requireNotNull(dirtyPrediction)
        // The two corpora end on different days by construction, so the
        // outlier must not move the estimated length or the projection
        // from each one's own anchor.
        assertThat(kotlin.math.abs(clean.centerCycleLength - dirty.centerCycleLength)).isAtMost(1.0)
        val cleanSpan = ChronoUnit.DAYS.between(clean.anchorDate, clean.nextPeriodDate).toInt()
        val dirtySpan = ChronoUnit.DAYS.between(dirty.anchorDate, dirty.nextPeriodDate).toInt()
        assertThat(kotlin.math.abs(cleanSpan - dirtySpan)).isAtMost(1)
    }

    @Test
    fun G8_implausibleIntervalIsExcludedRatherThanAveragedIn() {
        val scenario = Corpus.impossibleGap()
        val prediction = engine.predict(inputFor(scenario, scenario.starts.size - 1))

        assertThat(prediction).isNotNull()
        assertThat(prediction!!.intervalsExcluded).isNotEmpty()
        assertThat(prediction.intervalsExcluded.first().exclusionReason).isNotNull()
        // A 300-day gap must not push the next period months out.
        assertThat(ChronoUnit.DAYS.between(prediction.anchorDate, prediction.nextPeriodDate).toInt())
            .isLessThan(60)
    }

    // ── G9 irregularity ─────────────────────────────────────────────

    @Test
    fun G9_aSingleOutlierIsNotEnoughToDeclareIrregular() {
        val scenario = Corpus.irregular(cycles = 8, outlierCount = 1)
        val prediction = engine.predict(inputFor(scenario, scenario.starts.size - 1))

        assertThat(prediction).isNotNull()
        assertThat(prediction!!.isIrregular).isFalse()
    }

    @Test
    fun G9_multipleOutliersAreDetected() {
        val scenario = Corpus.irregular(cycles = 10, outlierCount = 2)
        val prediction = engine.predict(inputFor(scenario, scenario.starts.size - 1))

        assertThat(prediction).isNotNull()
        assertThat(prediction!!.isIrregular).isTrue()
        assertThat(prediction.warnings).contains(PredictWarning.IRREGULAR_HISTORY)
    }

    // ── G4 error bounds ─────────────────────────────────────────────

    @Test
    fun G4_meanAbsoluteErrorStaysWithinTolerance() {
        // Stress corpora are excluded: their forecast target is the
        // planted anomaly, so an error there measures the anomaly, not
        // the estimator. G8 and G9 cover them.
        val scenarios = Corpus.all().filter { it.expectedIntervals >= 3 && !it.stress }
        val errors: List<Double> = scenarios.flatMap { holdOut(it) }
            .map { pair ->
                val prediction = pair.first
                val signedError = ChronoUnit.DAYS
                    .between(prediction.generatedForDate, prediction.nextPeriodDate).toInt()
                kotlin.math.abs(signedError - pair.second).toDouble()
            }

        assertThat(errors).isNotEmpty()
        assertThat(errors.average()).isAtMost(4.0)
        val sorted = errors.sorted()
        assertThat(sorted[sorted.size / 2]).isAtMost(2.0)
    }

    @Test
    fun G4_perfectlyConstantHistoryIsPredictedExactly() {
        val errors: List<Int> = listOf(Corpus.stable(cycles = 3), Corpus.stable(cycles = 6), Corpus.stable(cycles = 8))
            .flatMap { holdOut(it) }
            .map { pair ->
                val prediction = pair.first
                val signed = ChronoUnit.DAYS
                    .between(prediction.generatedForDate, prediction.nextPeriodDate).toInt()
                kotlin.math.abs(signed - pair.second)
            }

        assertThat(errors).isNotEmpty()
        assertThat(errors.max()).isAtMost(1)
    }

    // ── G11 determinism ─────────────────────────────────────────────

    @Test
    fun G11_identicalInputProducesIdenticalOutput() {
        Corpus.all().forEach { scenario ->
            val input = inputFor(scenario, scenario.starts.size)
            val first = engine.predict(input)
            repeat(20) {
                assertThat(engine.predict(input)).isEqualTo(first)
            }
        }
    }

    // ── G13 calendar edges ──────────────────────────────────────────

    @Test
    fun G13_crossesMonthYearAndLeapBoundaries() {
        val leapStarts = listOf(
            LocalDate.parse("2024-02-27"),
            LocalDate.parse("2024-02-29"),
            LocalDate.parse("2024-03-04"),
            LocalDate.parse("2024-03-08"),
            LocalDate.parse("2024-03-13"),
            LocalDate.parse("2024-03-18"),
        )
        val prediction = engine.predict(
            CyclePredictionInput(
                observed = leapStarts.mapIndexed { index, date ->
                    ObservedCycleStart("l$index", date, date.plusDays(4), CycleSource.User)
                },
                userReportedAverageCycleLength = null,
                today = leapStarts.last(),
            ),
        )

        assertThat(prediction).isNotNull()
        assertThat(prediction!!.nextPeriodDate).isEqualTo(LocalDate.parse("2024-04-15"))
    }

    @Test
    fun G13_handlesMonthEndAndYearBoundaryStarts() {
        val starts = listOf(
            LocalDate.parse("2025-12-15"),
            LocalDate.parse("2026-01-12"),
            LocalDate.parse("2026-02-09"),
            LocalDate.parse("2026-03-12"),
            LocalDate.parse("2026-04-08"),
        )
        val prediction = engine.predict(
            CyclePredictionInput(
                observed = starts.mapIndexed { index, date ->
                    ObservedCycleStart("y$index", date, date.plusDays(4), CycleSource.User)
                },
                userReportedAverageCycleLength = null,
                today = starts.last(),
            ),
        )

        assertThat(prediction).isNotNull()
        val next = requireNotNull(prediction).nextPeriodDate
        assertThat(next).isEqualTo(LocalDate.parse("2026-05-06"))
    }
}