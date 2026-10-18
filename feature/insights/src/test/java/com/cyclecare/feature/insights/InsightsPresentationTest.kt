package com.cyclecare.feature.insights

import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.uiText
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

        assertThat(uiState.averageCycleText).isEqualTo(pluralUiText(R.plurals.n_days, 29, 29))
        assertThat(uiState.variabilityLabel).isEqualTo(uiText(R.string.variability_steady_lately))
        assertThat(uiState.variabilityText).isEqualTo(pluralUiText(R.plurals.min_max_day_range, 31, 27, 31))
        assertThat(uiState.cycleTrendBars).hasSize(6)
        assertThat(uiState.periodTrendItems).hasSize(6)
        assertThat(uiState.phaseHighlights.single().badgeLabel).isEqualTo(uiText(R.string.phase_period))
        assertThat(uiState.symptomSections.map { it.title }).containsExactly(
            uiText(R.string.section_mood),
            uiText(R.string.section_energy),
            uiText(R.string.section_pain),
            uiText(R.string.section_body_cues),
        ).inOrder()

        assertThat(uiState.averagePeriodText).isEqualTo(pluralUiText(R.plurals.n_days, 5, 5))
        assertThat(uiState.cycleTrendBars.first().valueLabel).isEqualTo(pluralUiText(R.plurals.n_days, 28, 28))
        assertThat(uiState.periodTrendItems.first().valueLabel).isEqualTo(uiText(R.string.day_compact, 5))
        assertThat(uiState.cycleProgressLabel).isEqualTo(pluralUiText(R.plurals.n_of_m_cycles_logged, 6, 6, 2))
        assertThat(uiState.symptomProgressLabel).isEqualTo(pluralUiText(R.plurals.n_of_m_days_logged, 13, 13, 5))
        assertThat(uiState.cycleTrendSummary).isEqualTo(
            pluralUiText(R.plurals.your_last_cycle_was_n_days_within_typical_range, 29, 29),
        )
        assertThat(uiState.symptomSections.first().topLabel).isEqualTo(uiText(R.string.mood_stable))
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
        assertThat(uiState.cycleTrendSummary).isEqualTo(uiText(R.string.trend_charts_building_summary))
        assertThat(uiState.symptomSummary).isEqualTo(uiText(R.string.symptom_patterns_locked_summary))
        assertThat(uiState.narrativeTitle).isEqualTo(uiText(R.string.narrative_build_stronger_patterns))
        assertThat(uiState.cycleProgressLabel).isEqualTo(pluralUiText(R.plurals.n_of_m_cycles_logged, 1, 1, 2))
        assertThat(uiState.symptomProgressLabel).isEqualTo(pluralUiText(R.plurals.n_of_m_days_logged, 1, 1, 5))
    }
}
