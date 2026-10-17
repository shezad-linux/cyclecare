package com.cyclecare.feature.insights

import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.InsightPhase
import com.cyclecare.core.model.InsightPhaseHighlight
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InsightsPresentationTest {
    @Test
    fun `build insights ui state formats advanced overview`() {
        val uiState = buildInsightsUiState(
            InsightSnapshot(
                averageCycleLength = 29,
                averagePeriodLength = 5,
                irregularCycles = false,
                recentCycleLengths = listOf(28, 30, 27, 29, 31, 29),
                recentPeriodLengths = listOf(5, 5, 4, 5, 6, 5),
                minCycleLength = 27,
                maxCycleLength = 31,
                cycleWindowLabel = "Based on 6 logged cycles from Oct 2025 to Mar 2026.",
                hasEnoughCycleData = true,
                moodCounts = mapOf(
                    Mood.Stable to 6,
                    Mood.Low to 3,
                    Mood.Happy to 2,
                    Mood.Irritable to 1,
                    Mood.Anxious to 1,
                ),
                energyCounts = mapOf(
                    EnergyLevel.Low to 3,
                    EnergyLevel.Steady to 5,
                    EnergyLevel.High to 2,
                ),
                painCounts = mapOf(
                    PainLevel.None to 3,
                    PainLevel.Light to 2,
                    PainLevel.Moderate to 4,
                    PainLevel.Strong to 1,
                ),
                dischargeCounts = mapOf(
                    DischargeType.None to 1,
                    DischargeType.Dry to 1,
                    DischargeType.Creamy to 2,
                    DischargeType.Sticky to 1,
                    DischargeType.EggWhite to 4,
                ),
                phaseHighlights = listOf(
                    InsightPhaseHighlight(
                        phase = InsightPhase.Period,
                        title = "Period phase pattern",
                        description = "Logged 7 entries during period days. Moderate pain and low energy show up most often.",
                    ),
                ),
                hasEnoughSymptomData = true,
                narrativeSummary = "Recent history shows 27 to 31 day cycles across 6 logged cycles.",
            ),
        )

        assertThat(uiState.averageCycleText).isEqualTo("29 days")
        assertThat(uiState.variabilityLabel).isEqualTo("Steady lately")
        assertThat(uiState.variabilityText).isEqualTo("27-31 day range")
        assertThat(uiState.cycleTrendBars).hasSize(6)
        assertThat(uiState.periodTrendItems).hasSize(6)
        assertThat(uiState.phaseHighlights.single().badgeLabel).isEqualTo("Period")
        assertThat(uiState.symptomSections.map { it.title }).containsExactly("Mood", "Energy", "Pain", "Body cues").inOrder()
    }

    @Test
    fun `build insights ui state surfaces low data guidance`() {
        val uiState = buildInsightsUiState(
            InsightSnapshot(
                averageCycleLength = 28,
                averagePeriodLength = 5,
                irregularCycles = true,
                recentCycleLengths = listOf(28),
                recentPeriodLengths = listOf(5),
                minCycleLength = 28,
                maxCycleLength = 28,
                cycleWindowLabel = "Log two full cycles to unlock cycle trend charts.",
                hasEnoughCycleData = false,
                moodCounts = mapOf(
                    Mood.Stable to 1,
                    Mood.Low to 0,
                    Mood.Happy to 0,
                    Mood.Irritable to 0,
                    Mood.Anxious to 0,
                ),
                energyCounts = mapOf(
                    EnergyLevel.Low to 0,
                    EnergyLevel.Steady to 1,
                    EnergyLevel.High to 0,
                ),
                painCounts = mapOf(
                    PainLevel.None to 1,
                    PainLevel.Light to 0,
                    PainLevel.Moderate to 0,
                    PainLevel.Strong to 0,
                ),
                dischargeCounts = mapOf(
                    DischargeType.None to 1,
                    DischargeType.Dry to 0,
                    DischargeType.Creamy to 0,
                    DischargeType.Sticky to 0,
                    DischargeType.EggWhite to 0,
                ),
                phaseHighlights = emptyList(),
                hasEnoughSymptomData = false,
                narrativeSummary = "Your local timeline is still building. Keep logging cycles and symptoms to unlock trend charts and phase-based pattern insights.",
            ),
        )

        assertThat(uiState.hasEnoughCycleData).isFalse()
        assertThat(uiState.hasEnoughSymptomData).isFalse()
        assertThat(uiState.cycleTrendSummary).contains("two logged cycles")
        assertThat(uiState.symptomSummary).contains("Keep logging symptoms")
        assertThat(uiState.narrativeTitle).isEqualTo("Build stronger patterns")
    }
}
