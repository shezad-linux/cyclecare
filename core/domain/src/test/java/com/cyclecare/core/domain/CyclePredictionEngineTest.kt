package com.cyclecare.core.domain

import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class CyclePredictionEngineTest {
    private val engine = CyclePredictionEngine()

    @Test
    fun predict_noHistory_usesProfileBaseline() {
        val today = date("2026-03-16")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-03-01",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 16,
            nextPeriodDate = date("2026-03-29"),
            ovulationDate = date("2026-03-15"),
            fertileWindow = DateRange(
                start = date("2026-03-11"),
                endInclusive = date("2026-03-16"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 13,
        )
    }

    @Test
    fun predict_singleLoggedCycle_usesProfileAverageFromLatestAnchor() {
        val today = date("2026-03-10")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-01-01",
                averageCycleLength = 28,
            ),
            cycles = listOf(cycle(id = "cycle-1", startDate = "2026-03-05")),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 6,
            nextPeriodDate = date("2026-04-02"),
            ovulationDate = date("2026-03-19"),
            fertileWindow = DateRange(
                start = date("2026-03-15"),
                endInclusive = date("2026-03-20"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 23,
        )
    }

    @Test
    fun predict_singleInterval_usesThatInterval() {
        val today = date("2026-02-10")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-01-15",
                averageCycleLength = 35,
            ),
            cycles = listOf(
                cycle(id = "cycle-1", startDate = "2026-01-01"),
                cycle(id = "cycle-2", startDate = "2026-01-31"),
            ),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 11,
            nextPeriodDate = date("2026-03-02"),
            ovulationDate = date("2026-02-16"),
            fertileWindow = DateRange(
                start = date("2026-02-12"),
                endInclusive = date("2026-02-17"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 20,
        )
    }

    @Test
    fun predict_multipleCycles_usesLastSixIntervalsOnly() {
        val today = date("2026-07-20")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-01-01",
                averageCycleLength = 33,
            ),
            cycles = listOf(
                cycle(id = "cycle-1", startDate = "2026-01-01"),
                cycle(id = "cycle-2", startDate = "2026-01-26"),
                cycle(id = "cycle-3", startDate = "2026-02-21"),
                cycle(id = "cycle-4", startDate = "2026-03-20"),
                cycle(id = "cycle-5", startDate = "2026-04-17"),
                cycle(id = "cycle-6", startDate = "2026-05-16"),
                cycle(id = "cycle-7", startDate = "2026-06-15"),
                cycle(id = "cycle-8", startDate = "2026-07-16"),
            ),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 5,
            nextPeriodDate = date("2026-08-14"),
            ovulationDate = date("2026-07-31"),
            fertileWindow = DateRange(
                start = date("2026-07-27"),
                endInclusive = date("2026-08-01"),
            ),
            confidenceLabel = "High confidence estimate",
            daysUntilNextPeriod = 25,
        )
    }

    @Test
    fun predict_ignoresPredictedCycleRecordsForAnchorAndAverage() {
        val today = date("2026-03-20")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-01-01",
                averageCycleLength = 28,
            ),
            cycles = listOf(
                cycle(id = "logged-1", startDate = "2026-01-01"),
                cycle(id = "logged-2", startDate = "2026-01-29"),
                cycle(
                    id = "predicted-future",
                    startDate = "2026-04-20",
                    source = CycleSource.Predicted,
                ),
            ),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 51,
            nextPeriodDate = date("2026-02-26"),
            ovulationDate = date("2026-02-12"),
            fertileWindow = DateRange(
                start = date("2026-02-08"),
                endInclusive = date("2026-02-13"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = -22,
        )
    }

    @Test
    fun predict_leapYear_rollsAcrossFeb29Correctly() {
        val today = date("2024-03-01")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2024-02-29",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 2,
            nextPeriodDate = date("2024-03-28"),
            ovulationDate = date("2024-03-14"),
            fertileWindow = DateRange(
                start = date("2024-03-10"),
                endInclusive = date("2024-03-15"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 27,
        )
    }

    @Test
    fun predict_monthEnd_rollsIntoNextMonthCorrectly() {
        val today = date("2026-02-02")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-01-31",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 3,
            nextPeriodDate = date("2026-02-28"),
            ovulationDate = date("2026-02-14"),
            fertileWindow = DateRange(
                start = date("2026-02-10"),
                endInclusive = date("2026-02-15"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 26,
        )
    }

    @Test
    fun predict_yearBoundary_rollsIntoNextYearCorrectly() {
        val today = date("2026-12-31")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-12-20",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 12,
            nextPeriodDate = date("2027-01-17"),
            ovulationDate = date("2027-01-03"),
            fertileWindow = DateRange(
                start = date("2026-12-30"),
                endInclusive = date("2027-01-04"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 17,
        )
    }

    @Test
    fun predict_shortCycle_keepsExpectedOvulationAndFertileDates() {
        val today = date("2026-03-05")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-03-01",
                averageCycleLength = 20,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 5,
            nextPeriodDate = date("2026-03-21"),
            ovulationDate = date("2026-03-07"),
            fertileWindow = DateRange(
                start = date("2026-03-03"),
                endInclusive = date("2026-03-08"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 16,
        )
    }

    @Test
    fun predict_longCycle_keepsExpectedOvulationAndFertileDates() {
        val today = date("2026-03-20")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-03-01",
                averageCycleLength = 45,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = today,
            cycleDay = 20,
            nextPeriodDate = date("2026-04-15"),
            ovulationDate = date("2026-04-01"),
            fertileWindow = DateRange(
                start = date("2026-03-28"),
                endInclusive = date("2026-04-02"),
            ),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 26,
        )
    }

    @Test
    fun predict_cycleDay_startsAtOneOnAnchorDate() {
        val today = date("2026-03-11")

        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-03-11",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = today,
        )

        assertThat(prediction.generatedAt).isEqualTo(today)
        assertThat(prediction.cycleDay).isEqualTo(1)
        assertThat(prediction.daysUntilNextPeriod).isEqualTo(28)
    }

    @Test
    fun predict_cycleDay_continuesPastAverageWhenLate() {
        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-03-01",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = date("2026-04-05"),
        )

        assertThat(prediction.cycleDay).isEqualTo(36)
        assertThat(prediction.cycleDay).isGreaterThan(28)
    }

    @Test
    fun predict_daysUntilNextPeriod_goesNegativeWhenOverdue() {
        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-03-01",
                averageCycleLength = 28,
            ),
            cycles = emptyList(),
            today = date("2026-04-05"),
        )

        assertThat(prediction.daysUntilNextPeriod).isEqualTo(-7)
    }

    @Test
    fun predict_confidence_isBaselineWithFewerThanTwoIntervals() {
        val prediction = engine.predict(
            profile = profile(
                lastPeriodStart = "2026-01-15",
                averageCycleLength = 35,
            ),
            cycles = listOf(
                cycle(id = "cycle-1", startDate = "2026-01-01"),
                cycle(id = "cycle-2", startDate = "2026-01-31"),
            ),
            today = date("2026-02-10"),
        )

        assertThat(prediction.confidenceLabel).isEqualTo("Baseline estimate")
    }

    @Test
    fun predict_confidence_isHighForLowVariance() {
        val prediction = engine.predict(
            profile = profile(averageCycleLength = 31),
            cycles = listOf(
                cycle(id = "cycle-1", startDate = "2026-01-01"),
                cycle(id = "cycle-2", startDate = "2026-01-29"),
                cycle(id = "cycle-3", startDate = "2026-02-26"),
            ),
            today = date("2026-03-01"),
        )

        assertThat(prediction.nextPeriodDate).isEqualTo(date("2026-03-26"))
        assertThat(prediction.confidenceLabel).isEqualTo("High confidence estimate")
    }

    @Test
    fun predict_confidence_isModerateForMidVariance() {
        val prediction = engine.predict(
            profile = profile(averageCycleLength = 29),
            cycles = listOf(
                cycle(id = "cycle-1", startDate = "2026-01-01"),
                cycle(id = "cycle-2", startDate = "2026-01-25"),
                cycle(id = "cycle-3", startDate = "2026-02-24"),
            ),
            today = date("2026-03-01"),
        )

        assertThat(prediction.nextPeriodDate).isEqualTo(date("2026-03-23"))
        assertThat(prediction.confidenceLabel).isEqualTo("Moderate confidence estimate")
    }

    @Test
    fun predict_irregularHistory_keepsSimpleAverageAndUsesLowerConfidence() {
        val prediction = engine.predict(
            profile = profile(averageCycleLength = 30),
            cycles = listOf(
                cycle(id = "cycle-1", startDate = "2026-01-01"),
                cycle(id = "cycle-2", startDate = "2026-01-21"),
                cycle(id = "cycle-3", startDate = "2026-02-26"),
            ),
            today = date("2026-03-01"),
        )

        assertPrediction(
            prediction = prediction,
            generatedAt = date("2026-03-01"),
            cycleDay = 4,
            nextPeriodDate = date("2026-03-26"),
            ovulationDate = date("2026-03-12"),
            fertileWindow = DateRange(
                start = date("2026-03-08"),
                endInclusive = date("2026-03-13"),
            ),
            confidenceLabel = "Lower confidence estimate",
            daysUntilNextPeriod = 25,
        )
    }

    private fun assertPrediction(
        prediction: PredictionSnapshot,
        generatedAt: LocalDate,
        cycleDay: Int,
        nextPeriodDate: LocalDate,
        ovulationDate: LocalDate,
        fertileWindow: DateRange,
        confidenceLabel: String,
        daysUntilNextPeriod: Int,
    ) {
        assertThat(prediction.generatedAt).isEqualTo(generatedAt)
        assertThat(prediction.cycleDay).isEqualTo(cycleDay)
        assertThat(prediction.nextPeriodDate).isEqualTo(nextPeriodDate)
        assertThat(prediction.ovulationDate).isEqualTo(ovulationDate)
        assertThat(prediction.fertileWindow).isEqualTo(fertileWindow)
        assertThat(prediction.confidenceLabel).isEqualTo(confidenceLabel)
        assertThat(prediction.daysUntilNextPeriod).isEqualTo(daysUntilNextPeriod)
    }

    private fun profile(
        lastPeriodStart: String = "2026-03-01",
        averageCycleLength: Int = 28,
        periodLength: Int = 5,
    ) = UserProfile(
        ageBand = AgeBand.Twenties,
        averageCycleLength = averageCycleLength,
        periodLength = periodLength,
        lastPeriodStart = date(lastPeriodStart),
        trackingGoal = TrackingGoal.PeriodPrep,
        remindersEnabled = true,
        privacyModeEnabled = false,
    )

    private fun cycle(
        id: String,
        startDate: String,
        cycleLength: Int = 28,
        periodLength: Int = 5,
        source: CycleSource = CycleSource.User,
    ) = CycleRecord(
        id = id,
        startDate = date(startDate),
        endDate = date(startDate).plusDays(periodLength.toLong() - 1),
        cycleLength = cycleLength,
        source = source,
    )

    private fun date(value: String): LocalDate = LocalDate.parse(value)
}
