package com.cyclecare.core.domain

import com.cyclecare.core.domain.prediction.CyclePredictionEngineV2
import com.cyclecare.core.domain.prediction.CyclePredictionInput
import com.cyclecare.core.domain.prediction.FERTILE_WINDOW_LEAD_DAYS
import com.cyclecare.core.domain.prediction.FERTILE_WINDOW_TRAIL_DAYS
import com.cyclecare.core.domain.prediction.ObservedCycleStart
import com.cyclecare.core.domain.prediction.averageCycleLengthOf
import com.cyclecare.core.domain.prediction.ovulationFrom
import com.cyclecare.core.domain.prediction.typicalFertileWindow
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.SymptomLog
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * One extractor, one fertile-window rule, one average-cycle figure.
 *
 * `11` §8.9 asks that the insight engine and the prediction engine
 * "cannot disagree for the same input, because they share one
 * extractor". These tests pin the shared behaviour, so a consumer that
 * goes back to the stored `cycleLength` column fails here rather than
 * on a user's screen.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md section 8.9.
 */
class SharedExtractorTest {

    @Test
    fun insightEngineAndPredictionEngineReadTheSameIntervals() {
        val cycles = listOf(
            cycle("c1", "2026-01-01", storedCycleLength = 50),
            cycle("c2", "2026-01-29", storedCycleLength = 51),
            cycle("c3", "2026-02-26", storedCycleLength = 52),
        )

        val insight = InsightEngine().build(cycles, emptyList())
        val prediction = requireNotNull(
            CyclePredictionEngineV2().predict(
                CyclePredictionInput(
                    observed = cycles.map { record ->
                        ObservedCycleStart(
                            id = record.id,
                            startDate = record.startDate,
                            endDate = record.endDate,
                            source = record.source,
                        )
                    },
                    userReportedAverageCycleLength = null,
                    today = LocalDate.parse("2026-02-26"),
                ),
            ),
        )

        // The observed gaps, not the stored column, in both engines.
        assertThat(insight.recentCycleLengths).containsExactly(28, 28).inOrder()
        assertThat(prediction.intervalsUsed.map { it.lengthDays }).containsExactly(28, 28).inOrder()
    }

    @Test
    fun theAverageCycleLengthFigureHasOneDefinition() {
        // The stored column contradicts the observed gaps on purpose.
        // A surface that labels a number "Avg cycle" and reads the
        // column would show 50 here; the canonical figure is 28.
        val cycles = listOf(
            cycle("c1", "2026-01-01", storedCycleLength = 50),
            cycle("c2", "2026-01-29", storedCycleLength = 51),
            cycle("c3", "2026-02-26", storedCycleLength = 52),
        )

        assertThat(averageCycleLengthOf(cycles)).isEqualTo(28)
        assertThat(InsightEngine().build(cycles, emptyList()).averageCycleLength).isEqualTo(28)
    }

    @Test
    fun theAverageCycleLengthFigureIsNullWithoutUsableHistory() {
        // Null is the cue to show an onboarding baseline instead of a
        // derived figure, rather than inventing an average.
        assertThat(averageCycleLengthOf(emptyList())).isNull()
        assertThat(averageCycleLengthOf(listOf(cycle("c1", "2026-01-01", storedCycleLength = 28)))).isNull()
    }

    @Test
    fun retrospectiveFertileWindowUsesTheObservedNextStart() {
        // The stored column says the cycle ran 45 days; the observed gap
        // says 28. Ovulation is dated backwards from the next period, so
        // the observed gap is what decides the window. The symptom on
        // 2026-01-15 sits inside the observed window (ovulation on
        // 2026-01-15, window 2026-01-11 to 2026-01-16) and the one on
        // 2026-02-01 sits inside the window the stored column would
        // have produced (ovulation on 2026-02-01).
        val cycles = listOf(
            cycle("c1", "2026-01-01", storedCycleLength = 45),
            cycle("c2", "2026-01-29", storedCycleLength = 45),
        )

        val snapshot = InsightEngine().build(
            cycles = cycles,
            symptoms = listOf(
                symptom("s1", "2026-01-13"),
                symptom("s2", "2026-01-15"),
                symptom("s3", "2026-02-01"),
                symptom("s4", "2026-02-02"),
                symptom("s5", "2026-02-03"),
            ),
        )

        val fertile = snapshot.phaseHighlights.single { it.phase == com.cyclecare.core.model.InsightPhase.FertileWindow }
        // Two of the five entries fall in the window derived from the
        // observed gap. The other three fall in the window the stored
        // column would have produced.
        assertThat(fertile.description).contains("Logged 2 entries")
    }

    @Test
    fun thePredictionEngineWidensTheTypicalFertileWindowRatherThanRedefiningIt() {
        val prediction = requireNotNull(
            CyclePredictionEngineV2().predict(
                CyclePredictionInput(
                    observed = listOf(
                        observed("r0", "2026-01-01", "2026-01-05"),
                        observed("r1", "2026-01-29", "2026-02-02"),
                        observed("r2", "2026-02-26", "2026-03-02"),
                        observed("r3", "2026-03-26", "2026-03-30"),
                    ),
                    userReportedAverageCycleLength = null,
                    today = LocalDate.parse("2026-03-26"),
                ),
            ),
        )

        val typical = typicalFertileWindow(prediction.ovulationDate)
        val window = requireNotNull(prediction.fertileWindow)

        // The population-typical window is inside the emitted window,
        // because the emitted one carries the estimate's own
        // uncertainty. The rule itself is shared, not redefined.
        assertThat(window.start).isAtMost(typical.start)
        assertThat(window.endInclusive).isAtLeast(typical.endInclusive)
        assertThat(typical.start).isEqualTo(prediction.ovulationDate.minusDays(FERTILE_WINDOW_LEAD_DAYS.toLong()))
        assertThat(typical.endInclusive).isEqualTo(prediction.ovulationDate.plusDays(FERTILE_WINDOW_TRAIL_DAYS.toLong()))
        // The retrospective rule is the same function of the same
        // ovulation date.
        assertThat(typical).isEqualTo(
            typicalFertileWindow(ovulationFrom(prediction.nextPeriodDate)),
        )
    }

    private fun observed(id: String, start: String, end: String) = ObservedCycleStart(
        id = id,
        startDate = LocalDate.parse(start),
        endDate = LocalDate.parse(end),
        source = CycleSource.User,
    )

    private fun cycle(
        id: String,
        startDate: String,
        storedCycleLength: Int,
        endDate: String = LocalDate.parse(startDate).plusDays(4).toString(),
    ): CycleRecord = CycleRecord(
        id = id,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate),
        cycleLength = storedCycleLength,
        source = CycleSource.User,
    )

    private fun symptom(id: String, date: String): SymptomLog = SymptomLog(
        id = id,
        date = LocalDate.parse(date),
        mood = Mood.Stable,
        painLevel = PainLevel.None,
        energyLevel = EnergyLevel.Steady,
        dischargeType = DischargeType.None,
    )
}
