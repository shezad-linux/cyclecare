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

    private data class HoldOut(
        val prediction: CyclePrediction,
        val actualIntervalDays: Int,
        val historyIntervals: Int,
    )

    /** Rolling origin: predict the next interval from every prefix. */
    private fun holdOutDetailed(scenario: Corpus.Scenario): List<HoldOut> {
        val results = mutableListOf<HoldOut>()
        for (prefix in 2 until scenario.starts.size) {
            val input = inputFor(scenario, prefix)
            val prediction = engine.predict(input) ?: continue
            val actual = ChronoUnit.DAYS.between(
                scenario.starts[prefix - 1],
                scenario.starts[prefix],
            ).toInt()
            results += HoldOut(prediction, actual, prefix - 1)
        }
        return results
    }

    private fun holdOut(scenario: Corpus.Scenario): List<Pair<CyclePrediction, Int>> =
        holdOutDetailed(scenario).map { it.prediction to it.actualIntervalDays }

    private fun regularGateScenarios(): List<Corpus.Scenario> = listOf(
        Corpus.regular(cycles = 8),
        Corpus.regular(cycles = 8, sigma = 1.0, seed = 7),
        Corpus.regular(cycles = 12, sigma = 2.0, seed = 11),
        Corpus.regular(cycles = 12, sigma = 4.0),
        Corpus.regular(cycles = 12, sigma = 1.0, mean = 31, seed = 5),
        Corpus.regular(cycles = 12, sigma = 2.0, mean = 24, seed = 9),
    )

    private fun regularHoldOutErrors(): List<Int> =
        regularGateScenarios()
            .flatMap { holdOutDetailed(it) }
            .filter { it.historyIntervals >= 6 }
            .map {
                kotlin.math.abs(
                    ChronoUnit.DAYS
                        .between(it.prediction.generatedForDate, it.prediction.nextPeriodDate)
                        .toInt() - it.actualIntervalDays,
                )
            }

    /** Nearest-rank P90: the smallest value at or above the 90th percentile. */
    private fun percentile90(values: List<Int>): Int {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val rank = Math.ceil(sorted.size * 0.9).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
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
    fun G2_irregularHistoryCoverageMeetsTheNinetyPercentTarget() {
        val scenarios = listOf(
            Corpus.irregular(cycles = 8, outlierCount = 2),
            Corpus.irregular(cycles = 10, outlierCount = 2),
            Corpus.irregular(cycles = 12, outlierCount = 2),
            Corpus.irregular(cycles = 14, outlierCount = 2),
            Corpus.irregular(cycles = 12, outlierCount = 2, magnitude = 42),
        )
        val holds = scenarios
            .flatMap { holdOutDetailed(it) }
            .filter { it.historyIntervals >= 6 }

        assertThat(holds.size).isAtLeast(10)
        val hits = holds.count { hold ->
            val window = hold.prediction.nextPeriodWindow ?: return@count false
            val actualDate = hold.prediction.generatedForDate
                .plusDays(hold.actualIntervalDays.toLong())
            actualDate >= window.start && actualDate <= window.endInclusive
        }
        assertThat(hits.toDouble() / holds.size).isAtLeast(0.90)
    }

@Test
    fun G2_irregularHistoryWidensTheWindowRatherThanNarrowingIt() {
        // The mechanism the gate names: the irregularity flag inflates
        // sigma, so an irregular history produces a wider window than the
        // same history without the outliers. A window that narrowed would
        // be the opposite of over-covering.
        val withOutliers = engine.predict(inputFor(Corpus.irregular(cycles = 12), 13))
        val withoutOutliers = engine.predict(inputFor(Corpus.stable(cycles = 12, mean = 28), 13))

        assertThat(withOutliers).isNotNull()
        assertThat(withoutOutliers).isNotNull()
        val irregular = requireNotNull(withOutliers)
        val stable = requireNotNull(withoutOutliers)

        assertThat(irregular.isIrregular).isTrue()
        assertThat(irregular.dispersionSigma).isGreaterThan(stable.dispersionSigma)
        assertThat(irregular.nextPeriodWindow!!.halfWidthDays)
            .isAtLeast(stable.nextPeriodWindow!!.halfWidthDays)
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

    @Test
    fun G3_regularHistoryMeanAbsoluteErrorStaysWithinTwoDays() {
        val errors = regularHoldOutErrors()

        assertThat(errors.size).isAtLeast(20)
        assertThat(errors.average()).isAtMost(2.0)
    }

    @Test
    fun G6_regularHistoryNinetiethPercentileErrorStaysWithinFiveDays() {
        val errors = regularHoldOutErrors()

        assertThat(errors.size).isAtLeast(20)
        assertThat(percentile90(errors)).isAtMost(5)
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

    // ── G10 drift activation, cap and monotonicity ──────────────────

    @Test
    fun G10_trendCorrectionActivatesAtTheThresholdAndStaysCappedAndMonotone() {
        val cases = listOf(
            1 to Corpus.linearDrift(cycles = 9, startLength = 30, slopePerCycle = 1),
            -1 to Corpus.linearDrift(cycles = 9, startLength = 35, slopePerCycle = -1),
            2 to Corpus.linearDrift(cycles = 9, startLength = 20, slopePerCycle = 2),
            -2 to Corpus.linearDrift(cycles = 9, startLength = 50, slopePerCycle = -2),
            3 to Corpus.linearDrift(cycles = 9, startLength = 15, slopePerCycle = 3),
            -3 to Corpus.linearDrift(cycles = 9, startLength = 55, slopePerCycle = -3),
            4 to Corpus.linearDrift(cycles = 9, startLength = 15, slopePerCycle = 4),
            -4 to Corpus.linearDrift(cycles = 9, startLength = 60, slopePerCycle = -4),
            5 to Corpus.linearDrift(cycles = 9, startLength = 15, slopePerCycle = 5),
            -5 to Corpus.linearDrift(cycles = 9, startLength = 60, slopePerCycle = -5),
        )

        val corrections = cases.associate { (slope, scenario) ->
            val prediction = requireNotNull(engine.predict(inputFor(scenario, scenario.starts.size)))
            val projectedDrift = slope.toDouble()
            val expected = if (kotlin.math.abs(projectedDrift) < TREND_ACTIVATION_DAYS) {
                0.0
            } else {
                PROJECTED_DRIFT_CAP * kotlin.math.tanh(projectedDrift / PROJECTED_DRIFT_CAP)
            }

            assertThat(prediction.trendAdjustmentDays).isWithin(1e-9).of(expected)
            assertThat(kotlin.math.abs(prediction.trendAdjustmentDays))
                .isAtMost(PROJECTED_DRIFT_CAP)
            if (expected == 0.0) {
                assertThat(prediction.warnings).doesNotContain(PredictWarning.TREND_DETECTED)
            } else {
                assertThat(prediction.warnings).contains(PredictWarning.TREND_DETECTED)
            }
            slope to prediction.trendAdjustmentDays
        }

        val positiveMagnitudes = listOf(2, 3, 4, 5)
            .map { kotlin.math.abs(requireNotNull(corrections[it])) }
        positiveMagnitudes.zipWithNext().forEach { (smaller, larger) ->
            assertThat(larger).isGreaterThan(smaller)
        }

        cases.forEach { (slope, _) ->
            assertThat(kotlin.math.abs(requireNotNull(corrections[-slope])))
                .isWithin(1e-9)
                .of(kotlin.math.abs(requireNotNull(corrections[slope])))
        }
    }

@Test
    fun G10_totalDriftCorrectionStaysWithinThreeDaysAndIsMonotone() {
        val corrections = mutableListOf<Pair<Double, CyclePrediction>>()
        listOf(0.0, 1.0, 2.0, 3.0).forEach { step ->
            val scenario = Corpus.drifting(cycles = 6, mean = 30, slopePerCycle = step, noise = 0.0)
            corrections += step to requireNotNull(engine.predict(inputFor(scenario, scenario.starts.size)))
        }
        listOf(-1.0, -2.0, -3.0).forEach { step ->
            val scenario = Corpus.drifting(cycles = 6, mean = 30, slopePerCycle = step, noise = 0.0)
            corrections += step to requireNotNull(engine.predict(inputFor(scenario, scenario.starts.size)))
        }

        corrections.forEach { (step, prediction) ->
            // The correction itself is capped, so a runaway trend cannot
            // move the estimate by weeks.
            assertThat(kotlin.math.abs(prediction.trendAdjustmentDays)).isAtMost(PROJECTED_DRIFT_CAP)
            // The total correction, in days of predicted date, is capped
            // by the same constant.
            val centreOnly = prediction.anchorDate!!
                .plusDays(Math.round(prediction.centerCycleLength).toLong())
            val movement = ChronoUnit.DAYS.between(centreOnly, prediction.nextPeriodDate).toInt()
            assertThat(kotlin.math.abs(movement)).isAtMost(PROJECTED_DRIFT_CAP.toInt())
            // The direction of the correction follows the direction of
            // the drift.
            if (step != 0.0 && kotlin.math.abs(step) >= TREND_ACTIVATION_DAYS) {
                assertThat(Math.signum(prediction.trendAdjustmentDays)).isEqualTo(Math.signum(step))
            }
        }

        // Monotone in the magnitude of the drift, in both directions.
        val lengthening = corrections.filter { it.first > 0.0 }.map { it.second.trendAdjustmentDays }
        lengthening.zipWithNext { earlier, later ->
            assertThat(later).isGreaterThan(earlier)
        }
        val shortening = corrections.filter { it.first < 0.0 }.map { it.second.trendAdjustmentDays }
        shortening.zipWithNext { earlier, later ->
            assertThat(later).isLessThan(earlier)
        }
    }


    // ── G12 stability ───────────────────────────────────────────────

    @Test
    fun G12_editingOneHistoricalDateStaysWithinThePriorWindowHalfWidth() {
        val scenarios = listOf(
            Corpus.regular(cycles = 8),
            Corpus.regular(cycles = 12, sigma = 2.0, seed = 11),
            Corpus.regular(cycles = 12, sigma = 1.0, mean = 31, seed = 5),
            Corpus.regular(cycles = 12, sigma = 4.0),
        )

        scenarios.forEach { scenario ->
            val baseline = requireNotNull(engine.predict(inputFor(scenario, scenario.starts.size)))
            val priorHalfWidth = requireNotNull(baseline.nextPeriodWindow).halfWidthDays

            for (index in 0 until scenario.starts.size - 1) {
                for (delta in listOf(-7, -5, -3, -1, 1, 3, 5, 7)) {
                    val edited = Corpus.corrected(scenario, index, delta)
                    val input = inputFor(edited, edited.starts.size)
                    val after = requireNotNull(engine.predict(input))

                    val movement = kotlin.math.abs(
                        ChronoUnit.DAYS
                            .between(baseline.nextPeriodDate, after.nextPeriodDate)
                            .toInt(),
                    )
                    assertThat(movement).isAtMost(priorHalfWidth)
                    assertThat(engine.predict(input)).isEqualTo(after)
                }
            }
        }
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
            repeat(1000) {
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

    // ── 11 §8 acceptance criteria 8, 10 and 12 ──────────────────────

    @Test
    fun criterion8_everyOutputCarriesAlgorithmVersionAndRecordIdsUsed() {
        val scenario = Corpus.regular(cycles = 8)
        val predicted = ObservedCycleStart(
            id = "predicted",
            startDate = scenario.starts.last().plusDays(28),
            endDate = null,
            source = CycleSource.Predicted,
        )
        val input = CyclePredictionInput(
            observed = scenario.starts.mapIndexed { index, date ->
                ObservedCycleStart("r$index", date, date.plusDays(4), CycleSource.User)
            } + predicted,
            userReportedAverageCycleLength = null,
            today = scenario.starts.last(),
        )

        val prediction = requireNotNull(engine.predict(input))
        assertThat(prediction.algorithmVersion).isEqualTo(PREDICTION_ALGORITHM_VERSION)
        assertThat(prediction.recordIdsUsed)
            .containsExactlyElementsIn(scenario.starts.indices.map { "r$it" })
            .inOrder()
        assertThat(prediction.toSnapshot().algorithmVersion)
            .isEqualTo(PREDICTION_ALGORITHM_VERSION)
    }

    @Test
    fun criterion10_deletingAnOldRecordRecomputesWithinThePriorWindow() {
        val scenarios = listOf(
            Corpus.regular(cycles = 8),
            Corpus.regular(cycles = 12, sigma = 2.0, seed = 11),
            Corpus.regular(cycles = 12, sigma = 1.0, mean = 31, seed = 5),
            Corpus.regular(cycles = 12, sigma = 4.0),
        )

        scenarios.forEach { scenario ->
            val baseline = requireNotNull(engine.predict(inputFor(scenario, scenario.starts.size)))
            val priorHalfWidth = requireNotNull(baseline.nextPeriodWindow).halfWidthDays

            // Keep the anchor and the interval immediately before it: this
            // is an old-record deletion, not a change to the current cycle.
            for (index in 0 until scenario.starts.size - 2) {
                val deleted = Corpus.deleted(scenario, index)
                val input = inputFor(deleted, deleted.starts.size)
                val after = requireNotNull(engine.predict(input))

                val movement = kotlin.math.abs(
                    ChronoUnit.DAYS
                        .between(baseline.nextPeriodDate, after.nextPeriodDate)
                        .toInt(),
                )
                assertThat(movement).isAtMost(priorHalfWidth)
                assertThat(engine.predict(input)).isEqualTo(after)
            }
        }
    }

    @Test
    fun criterion12_predictionLabelsDoNotImplyContraceptionDiagnosisOrCertainty() {
        val baseline = requireNotNull(
            engine.predict(inputFor(Corpus.stable(cycles = 8), 9)),
        )
        val labels = EstimateQuality.values().flatMap { quality ->
            listOf(false, true).map { stale ->
                baseline.copy(quality = quality, isStale = stale)
                    .toSnapshot()
                    .confidenceLabel
            }
        }.distinct()

        assertThat(labels).isNotEmpty()
        val bannedPhrases = listOf(
            "safe",
            "contracept",
            "diagnos",
            "certain",
            "guarantee",
            "will start",
            "definitely",
            "clinically accurate",
            "unhealthy",
            "abnormal",
            "100%",
        )
        labels.forEach { label ->
            bannedPhrases.forEach { phrase ->
                assertThat(label.lowercase()).doesNotContain(phrase)
            }
        }
    }

    @Test
    fun dispersionSigmaAppliesTheMadConsistencyConstantBeforeInflation() {
        val scenario = Corpus.regular(cycles = 12, sigma = 2.0, seed = 11)
        val prediction = requireNotNull(
            engine.predict(inputFor(scenario, scenario.starts.size)),
        )
        val lengths = prediction.intervalsUsed.map { it.lengthDays }
        val weights = lengths.indices.map {
            Math.pow(RECENCY_DECAY, (lengths.size - 1 - it).toDouble())
        }
        val weightedMad = lengths.indices.sumOf { index ->
            kotlin.math.abs(lengths[index] - prediction.centerCycleLength) * weights[index]
        } / weights.sum()
        val expected = totalSigma(
            sigmaFromMad(weightedMad),
            lengths.size,
            prediction.isIrregular,
        )

        assertThat(prediction.dispersionSigma).isWithin(1e-9).of(expected)
    }
}
