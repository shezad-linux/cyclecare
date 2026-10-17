package com.cyclecare.core.domain

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

class InsightEngineTest {
    private val engine = InsightEngine()

    @Test
    fun build_usesOnlyRecentNonPredictedCyclesForTrendSeries() {
        val snapshot = engine.build(
            cycles = listOf(
                cycle("predicted-old", "2025-08-01", cycleLength = 35, source = CycleSource.Predicted),
                cycle("c1", "2025-09-01", cycleLength = 26),
                cycle("c2", "2025-10-01", cycleLength = 27),
                cycle("c3", "2025-11-01", cycleLength = 28),
                cycle("c4", "2025-12-01", cycleLength = 29),
                cycle("c5", "2026-01-01", cycleLength = 30),
                cycle("c6", "2026-02-01", cycleLength = 31),
                cycle("c7", "2026-03-01", cycleLength = 32),
                cycle("predicted-future", "2026-04-01", cycleLength = 33, source = CycleSource.Predicted),
            ),
            symptoms = emptyList(),
        )

        assertThat(snapshot.recentCycleLengths).containsExactly(27, 28, 29, 30, 31, 32).inOrder()
        assertThat(snapshot.recentPeriodLengths).hasSize(6)
        assertThat(snapshot.averageCycleLength).isEqualTo(30)
        assertThat(snapshot.hasEnoughCycleData).isTrue()
        assertThat(snapshot.cycleWindowLabel).contains("6 logged cycles")
    }

    @Test
    fun build_marksLowDataWhenCycleAndSymptomDepthAreInsufficient() {
        val snapshot = engine.build(
            cycles = listOf(cycle("c1", "2026-03-01", cycleLength = 28)),
            symptoms = listOf(
                symptom("s1", "2026-03-01"),
                symptom("s2", "2026-03-02"),
                symptom("s3", "2026-03-03"),
                symptom("s4", "2026-03-04"),
            ),
        )

        assertThat(snapshot.hasEnoughCycleData).isFalse()
        assertThat(snapshot.hasEnoughSymptomData).isFalse()
        assertThat(snapshot.phaseHighlights).isEmpty()
        assertThat(snapshot.narrativeSummary).contains("still building")
    }

    @Test
    fun build_countsSymptomsAndCreatesPhaseHighlightsFromRecentWindow() {
        val snapshot = engine.build(
            cycles = listOf(
                cycle("jan", "2026-01-01", cycleLength = 29, endDate = "2026-01-05"),
                cycle("feb", "2026-01-30", cycleLength = 30, endDate = "2026-02-03"),
            ),
            symptoms = listOf(
                symptom("p1", "2026-01-01", pain = PainLevel.Moderate, energy = EnergyLevel.Low, mood = Mood.Low),
                symptom("p2", "2026-01-02", pain = PainLevel.Moderate, energy = EnergyLevel.Low, mood = Mood.Low),
                symptom("p3", "2026-02-01", pain = PainLevel.Strong, energy = EnergyLevel.Low, mood = Mood.Irritable),
                symptom("p4", "2026-02-02", pain = PainLevel.Moderate, energy = EnergyLevel.Low, mood = Mood.Irritable),
                symptom("f1", "2026-01-15", discharge = DischargeType.EggWhite, energy = EnergyLevel.High, mood = Mood.Happy),
                symptom("f2", "2026-01-16", discharge = DischargeType.EggWhite, energy = EnergyLevel.High, mood = Mood.Happy),
                symptom("f3", "2026-02-15", discharge = DischargeType.Creamy, energy = EnergyLevel.Steady, mood = Mood.Stable),
            ),
        )

        assertThat(snapshot.hasEnoughSymptomData).isTrue()
        assertThat(snapshot.moodCounts[Mood.Low]).isEqualTo(2)
        assertThat(snapshot.painCounts[PainLevel.Moderate]).isEqualTo(3)
        assertThat(snapshot.dischargeCounts[DischargeType.EggWhite]).isEqualTo(2)
        assertThat(snapshot.phaseHighlights.map { it.title }).containsExactly(
            "Period phase pattern",
            "Fertile window pattern",
        ).inOrder()
    }

    private fun cycle(
        id: String,
        startDate: String,
        cycleLength: Int,
        endDate: String = LocalDate.parse(startDate).plusDays(4).toString(),
        source: CycleSource = CycleSource.User,
    ): CycleRecord = CycleRecord(
        id = id,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate),
        cycleLength = cycleLength,
        source = source,
    )

    private fun symptom(
        id: String,
        date: String,
        mood: Mood = Mood.Stable,
        pain: PainLevel = PainLevel.None,
        energy: EnergyLevel = EnergyLevel.Steady,
        discharge: DischargeType = DischargeType.None,
    ): SymptomLog = SymptomLog(
        id = id,
        date = LocalDate.parse(date),
        mood = mood,
        painLevel = pain,
        energyLevel = energy,
        dischargeType = discharge,
    )
}
