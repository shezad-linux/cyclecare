package com.cyclecare.core.domain.prediction

import java.time.LocalDate
import java.util.Random

/**
 * Synthetic corpus for validating the prediction engine.
 *
 * Generated, never drawn from real users: shipping reproductive-health
 * data into a test fixture would breach the product's own privacy
 * posture.
 *
 * The cost is real and worth stating. A synthetic corpus cannot capture
 * genuine cycle physiology. It verifies internal consistency, numerical
 * behaviour, robustness and edge-case handling. It does not establish
 * biological accuracy, and passing these gates is not clinical
 * validation.
 *
 * Spec: CycleCare-Redesign-Docs/12-algorithm-validation.md
 */
object Corpus {

    const val DEFAULT_MEAN = 28.0
    const val DEFAULT_SIGMA = 2.0
    const val SEED = 20261017L

    data class Scenario(
        val name: String,
        val starts: List<LocalDate>,
        val expectedIntervals: Int,
        /**
         * True when an injected anomaly can land on the interval being
         * forecast. Such scenarios measure robustness, never accuracy:
         * no algorithm can predict a 300-day gap, and folding them into
         * an error-bound gate would only measure the size of the planted
         * anomaly.
         */
        val stress: Boolean = false,
    )

    private fun start(dayOffset: Int): LocalDate = LocalDate.parse("2026-01-05").plusDays(dayOffset.toLong())

    private fun build(lengths: List<Int>): List<LocalDate> {
        val result = mutableListOf(start(0))
        var day = 0
        lengths.forEach {
            day += it
            result += start(day)
        }
        return result
    }

    /** Perfectly constant cycles. */
    fun stable(cycles: Int = 8, mean: Int = 28): Scenario =
        Scenario("stable_${mean}_$cycles", build(List(cycles) { mean }), cycles)

    /**
     * Constant cycle length with controlled dispersion.
     *
     * sigma is approximate: the generated values are integers, and the
     * resulting spread depends on the seed as well as the parameter.
     */
    fun regular(
        cycles: Int = 8,
        mean: Int = 28,
        sigma: Double = DEFAULT_SIGMA,
        seed: Long = SEED,
    ): Scenario {
        val random = Random(seed + cycles * 31L + mean.toLong())
        val lengths = List(cycles) {
            val noise = random.nextGaussian() * sigma
            (mean + noise).toInt().coerceIn(15, 60)
        }
        return Scenario("regular_${mean}_s${sigma}_$cycles", build(lengths), cycles)
    }

    /**
     * Cycles trending steadily longer or shorter.
     *
     * [noise] is the standard deviation of the jitter added to each
     * interval. Zero produces a perfectly linear series, whose
     * regression slope is exactly [slopePerCycle] rather than
     * approximately so, which is what the drift gate needs to assert
     * against a known drift.
     */
    fun drifting(
        cycles: Int = 8,
        mean: Int = 28,
        slopePerCycle: Double = 1.2,
        seed: Long = SEED,
        noise: Double = 0.6,
    ): Scenario {
        val random = Random(seed + 977L)
        val lengths = List(cycles) { index ->
            val target = mean + slopePerCycle * index
            (target + random.nextGaussian() * noise).toInt().coerceIn(15, 60)
        }
        return Scenario("drift_${mean}_${slopePerCycle}_$cycles", build(lengths), cycles)
    }

    /**
     * Drift with integer lengths, so the weighted least-squares slope is
     * exactly [slopePerCycle] rather than approximately the parameter.
     *
     * G10 needs the projected drift to be known independently of the
     * estimator; noisy generator output cannot establish the activation
     * boundary or the `tanh` correction.
     */
    fun linearDrift(
        cycles: Int = 9,
        startLength: Int = 28,
        slopePerCycle: Int = 2,
    ): Scenario {
        val lengths = List(cycles) { startLength + slopePerCycle * it }
        return Scenario("linear_drift_${slopePerCycle}_$cycles", build(lengths), cycles)
    }

    /** A retroactive correction to one recorded start, as a user edit would make. */
    fun corrected(base: Scenario, index: Int, deltaDays: Int): Scenario {
        require(index in base.starts.indices) { "index $index is outside ${base.starts.indices}" }
        val starts = base.starts.toMutableList()
        starts[index] = starts[index].plusDays(deltaDays.toLong())
        return base.copy(name = "${base.name}_edited_${index}_$deltaDays", starts = starts)
    }

    /** A deletion of one recorded start, as a user correction would make. */
    fun deleted(base: Scenario, index: Int): Scenario {
        require(index in base.starts.indices) { "index $index is outside ${base.starts.indices}" }
        val starts = base.starts.filterIndexed { position, _ -> position != index }
        return base.copy(
            name = "${base.name}_deleted_$index",
            starts = starts,
            expectedIntervals = starts.size - 1,
        )
    }

    /** A few deliberately extreme intervals among plausible ones. */
    fun irregular(
        cycles: Int = 10,
        mean: Int = 28,
        outlierCount: Int = 2,
        magnitude: Int = 45,
    ): Scenario {
        val base = List(cycles) { mean }
        val withOutliers = base.toMutableList()
        repeat(outlierCount) { index ->
            val position = (index * 3 + 1).coerceAtMost(cycles - 1)
            withOutliers[position] = if (index % 2 == 0) magnitude else 60 - magnitude
        }
        return Scenario("irregular_${outlierCount}_${magnitude}_$cycles", build(withOutliers), cycles)
    }

    /** A single catastrophic outlier among otherwise regular cycles. */
    fun singleOutlier(cycles: Int = 8, mean: Int = 28, outlier: Int = 200): Scenario {
        val lengths = MutableList(cycles) { mean }
        lengths[cycles / 2] = outlier
        return Scenario("single_outlier_${outlier}_$cycles", build(lengths), cycles, stress = true)
    }

    /** Too few intervals to emit a range. */
    fun sparse(cycles: Int = 2): Scenario =
        Scenario("sparse_$cycles", build(List(cycles) { 28 }), cycles)

    /** One recorded period with nothing to compare it against. */
    fun singleRecord(): Scenario = Scenario("single_record", listOf(start(0)), 0)

    /**
     * An implausible gap that must be excluded, not averaged in.
     *
     * The gap is real: a run of ordinary cycles, then a jump of [gap]
     * days, then ordinary cycles again. The jump is longer than
     * `MAX_PLAUSIBLE_INTERVAL_DAYS`, so the extractor excludes it and
     * reports it, and the centre is fitted on the ordinary cycles either
     * side.
     *
     * An earlier version of this generator built the gap and then failed
     * to put it in the scenario, so the history it produced held two
     * duplicated dates and no gap at all. The exclusion it appeared to
     * prove was the short end of the plausibility band, not the long one.
     */
    fun impossibleGap(mean: Int = 28, gap: Int = 300): Scenario {
        val before = build(List(3) { mean })
        val afterGap = before.last().plusDays(gap.toLong())
        val tail = listOf(
            afterGap,
            afterGap.plusDays(mean.toLong()),
            afterGap.plusDays(2L * mean.toLong()),
        )
        return Scenario(
            "impossible_gap_$gap",
            before + tail,
            expectedIntervals = 6,
            stress = true,
        )
    }

    /**
     * One interval that is a data-entry error rather than a cycle.
     *
     * Spec `12` §3 names this generator: "Impossible intervals must not
     * influence the centre". The planted interval is longer than
     * `MAX_PLAUSIBLE_INTERVAL_DAYS`, so the extractor excludes it, and
     * the estimate must be the one the same history would produce
     * without it.
     */
    fun dataEntryError(
        cycles: Int = 8,
        errorIndex: Int = 3,
        magnitude: Int = 200,
        mean: Int = 28,
    ): Scenario {
        require(errorIndex in 0 until cycles) { "errorIndex $errorIndex is outside 0 until $cycles" }
        val lengths = List(cycles) { mean }.toMutableList()
        lengths[errorIndex] = magnitude
        return Scenario(
            "data_entry_error_${errorIndex}_${magnitude}_$cycles",
            build(lengths),
            cycles,
            stress = true,
        )
    }

    /**
     * Calendar boundaries, with plausible cycle lengths.
     *
     * Spec `12` §3 names this generator: "Leap day, month end, year
     * boundary, DST-adjacent dates". The intervals are ordinary 28-day
     * cycles, so the estimate is a real estimate rather than a fallback,
     * and the history spans a year boundary (15 December to 12 January)
     * and a leap day (9 February to 8 March 2024, which contains 29
     * February).
     */
    fun calendarEdge(): Scenario = Scenario(
        "calendar_edge",
        listOf(
            LocalDate.parse("2023-12-15"),
            LocalDate.parse("2024-01-12"),
            LocalDate.parse("2024-02-09"),
            LocalDate.parse("2024-03-08"),
            LocalDate.parse("2024-04-05"),
            LocalDate.parse("2024-05-03"),
        ),
        expectedIntervals = 5,
    )

    /** A skipped period, which is a long but plausible interval. */
    fun skipped(cycles: Int = 8, mean: Int = 28, skipAt: Int = 3): Scenario {
        val lengths = MutableList(cycles) { mean }
        if (skipAt < cycles) lengths[skipAt] = mean * 2
        return Scenario("skipped_$skipAt", build(lengths), cycles)
    }

    /**
     * The full validation matrix, stratified by history length.
     *
     * Results must always be read per stratum. A single aggregate number
     * hides exactly the behaviour that matters, such as good results for
     * long histories alongside useless predictions for new users.
     */
    fun all(): List<Scenario> = listOf(
        singleRecord(),
        sparse(1),
        sparse(2),
        stable(cycles = 3),
        stable(cycles = 6),
        stable(cycles = 8),
        regular(cycles = 3),
        regular(cycles = 6),
        regular(cycles = 8, sigma = 1.0),
        regular(cycles = 12, sigma = 4.0),
        drifting(cycles = 8, slopePerCycle = 1.2),
        drifting(cycles = 8, slopePerCycle = -1.0),
        drifting(cycles = 6, slopePerCycle = 0.2),
        drifting(cycles = 10, slopePerCycle = 2.5),
        drifting(cycles = 10, slopePerCycle = -2.0),
        irregular(cycles = 10),
        irregular(cycles = 8, outlierCount = 1),
        singleOutlier(cycles = 8),
        singleOutlier(cycles = 6, outlier = 120),
        skipped(cycles = 8),
        impossibleGap(),
    )

    /**
     * The p-th percentile of a sample, by nearest rank.
     *
     * Nearest rank rather than an interpolated percentile: the corpus
     * samples are small, and an interpolated value would be a number no
     * hold-out case ever produced.
     */
    fun percentile(values: List<Double>, percentile: Double): Double {
        require(values.isNotEmpty()) { "the percentile of an empty sample is undefined" }
        val sorted = values.sorted()
        val rank = kotlin.math.ceil(percentile / 100.0 * sorted.size).toInt()
        return sorted[(rank - 1).coerceIn(0, sorted.size - 1)]
    }
}