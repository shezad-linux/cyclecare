package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.CycleSource
import java.time.LocalDate

/**
 * A representative prediction, for previews and for tests.
 *
 * It runs the real engine over a regular history rather than hand-building
 * the output. A preview then shows what the app would really show, and a
 * test asserts against the engine's own numbers rather than against a
 * fixture that could drift away from the contract.
 *
 * Spec `11` §7 retired the second prediction shape, so there is now only
 * one way to make a prediction and this is it.
 */
object SamplePrediction {

    /**
     * A prediction over [cycles] regular cycles of [meanLength] days.
     *
     * The history ends on [today], and the engine is asked to predict
     * from that point, so the result is deterministic: the same inputs
     * always produce the same output.
     */
    fun from(
        cycles: Int = 6,
        meanLength: Int = 28,
        today: LocalDate = LocalDate.parse("2026-03-16"),
    ): CyclePrediction {
        val engine = CyclePredictionEngineV2()
        val firstStart = today.minusDays((cycles * meanLength).toLong())
        val observed = (0 until cycles).map { index ->
            ObservedCycleStart(
                id = "sample-$index",
                startDate = firstStart.plusDays((index * meanLength).toLong()),
                endDate = firstStart.plusDays((index * meanLength).toLong()).plusDays(4),
                source = CycleSource.User,
            )
        }
        return requireNotNull(
            engine.predict(
                CyclePredictionInput(
                    observed = observed,
                    userReportedAverageCycleLength = null,
                    today = today,
                ),
            ),
        ) { "the engine produced no prediction for a regular history" }
    }

    /**
     * A prediction with the given values and sensible defaults elsewhere.
     *
     * For a test that asserts on one particular field and does not care
     * about the rest. Every parameter of [CyclePrediction] has a
     * default, so a test names only what it is actually about.
     */
    @Suppress("LongParameterList")
    fun of(
        generatedForDate: LocalDate = LocalDate.parse("2026-03-16"),
        cycleDay: Int = 6,
        nextPeriodDate: LocalDate = LocalDate.parse("2026-04-07"),
        ovulationDate: LocalDate = LocalDate.parse("2026-03-24"),
        fertileWindow: DateWindow? = DateWindow(
            start = LocalDate.parse("2026-03-19"),
            endInclusive = LocalDate.parse("2026-03-29"),
            halfWidthDays = 3,
        ),
        daysUntilNextPeriod: Int = 22,
        nextPeriodWindow: DateWindow? = DateWindow(
            start = LocalDate.parse("2026-04-04"),
            endInclusive = LocalDate.parse("2026-04-10"),
            halfWidthDays = 3,
        ),
        quality: EstimateQuality = EstimateQuality.MODERATE,
        isStale: Boolean = false,
        isIrregular: Boolean = false,
        overdueDays: Int = 0,
        phase: PhaseEstimate = PhaseEstimate(CyclePhase.FOLLICULAR, isEstimated = true),
        warnings: List<PredictWarning> = emptyList(),
        algorithmVersion: String = "sample",
    ): CyclePrediction = CyclePrediction(
        algorithmVersion = algorithmVersion,
        generatedForDate = generatedForDate,
        anchorRecordId = null,
        anchorDate = null,
        recordIdsUsed = emptyList(),
        intervalsUsed = emptyList(),
        intervalsExcluded = emptyList(),
        centerCycleLength = 28.0,
        dispersionSigma = 0.0,
        trendAdjustmentDays = 0.0,
        isIrregular = isIrregular,
        isStale = isStale,
        quality = quality,
        nextPeriodDate = nextPeriodDate,
        nextPeriodWindow = nextPeriodWindow,
        ovulationDate = ovulationDate,
        fertileWindow = fertileWindow,
        cycleDay = cycleDay,
        daysUntilNextPeriod = daysUntilNextPeriod,
        overdueDays = overdueDays,
        phase = phase,
        warnings = warnings,
    )
}
