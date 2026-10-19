@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import androidx.compose.runtime.getValue
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.model.InsightPhase
import com.cyclecare.core.model.InsightSnapshot
import kotlinx.coroutines.flow.map

internal object InsightsTags {
    const val OverviewScreen = "insights_overview"
    const val CycleTrendsScreen = "insights_cycle_trends"
    const val SymptomPatternsScreen = "insights_symptom_patterns"
    const val CycleTrendsCta = "insights_cycle_trends_cta"
    const val SymptomPatternsCta = "insights_symptom_patterns_cta"
    const val BackToOverview = "insights_back_to_overview"
}

internal val TrendChartsBuildingSummary = uiText(R.string.trend_charts_building_summary)
internal val SymptomPatternsLockedSummary = uiText(R.string.symptom_patterns_locked_summary)
internal val BuildStrongerPatternsSummary = uiText(R.string.build_stronger_patterns_summary)

data class InsightsUiState(
    val averageCycleText: UiText = pluralUiText(R.plurals.n_days, 28, 28),
    val averagePeriodText: UiText = pluralUiText(R.plurals.n_days, 5, 5),
    val variabilityLabel: UiText = uiText(R.string.variability_steady_lately),
    val variabilityText: UiText = pluralUiText(R.plurals.n_day_rhythm, 28, 28),
    val variabilityDetail: UiText = uiText(R.string.variability_consistent),
    val variabilityInterpretation: UiText = uiText(R.string.variability_close_to_average),
    val cycleWindowLabel: UiText = uiText(R.string.cycle_window_recent_cycles),
    val hasEnoughCycleData: Boolean = false,
    val hasEnoughSymptomData: Boolean = false,
    val cyclesLoggedCount: Int = 0,
    val cyclesNeededForInsights: Int = 2,
    val daysLoggedThisCycle: Int = 0,
    val daysNeededForSymptomInsights: Int = 5,
    val cycleTrendBars: List<InsightBarPointUi> = emptyList(),
    val periodTrendItems: List<InsightStripItemUi> = emptyList(),
    val cycleTrendSummary: UiText = TrendChartsBuildingSummary,
    val symptomSummary: UiText = SymptomPatternsLockedSummary,
    val symptomSections: List<InsightBreakdownSectionUi> = emptyList(),
    val phaseHighlights: List<InsightPhaseHighlightUi> = emptyList(),
    val narrativeTitle: UiText = uiText(R.string.narrative_build_stronger_patterns),
    val narrativeSummary: UiText = UiText.Raw(""),
    val cycleProgressLabel: UiText = pluralUiText(R.plurals.n_of_m_cycles_logged, 1, 1, 2),
    val symptomProgressLabel: UiText = uiText(R.string.keep_logging_to_unlock_patterns),
)

data class InsightBarPointUi(
    val label: UiText,
    val value: Int,
    val valueLabel: UiText,
)

data class InsightStripItemUi(
    val label: UiText,
    val valueLabel: UiText,
)

data class InsightBreakdownSectionUi(
    val title: UiText,
    val topLabel: UiText?,
    val items: List<InsightBreakdownItemUi>,
)

data class InsightBreakdownItemUi(
    val label: UiText,
    val count: Int,
    val fraction: Float,
    val valueLabel: UiText,
)

data class InsightPhaseHighlightUi(
    val badgeLabel: UiText,
    val title: UiText,
    val detail: UiText,
)

internal fun buildInsightsUiState(insights: InsightSnapshot): InsightsUiState {
    val cycleTrendBars = insights.recentCycleLengths.mapIndexed { index, value ->
        InsightBarPointUi(
            label = uiText(R.string.cycle_point_label, index + 1),
            value = value,
            valueLabel = pluralUiText(R.plurals.n_days, value, value),
        )
    }
    val periodTrendItems = insights.recentPeriodLengths.mapIndexed { index, value ->
        InsightStripItemUi(
            label = uiText(R.string.period_point_label, index + 1),
            valueLabel = uiText(R.string.day_compact, value),
        )
    }
    val symptomSections = listOf(
        breakdownSection(
            title = uiText(R.string.section_mood),
            counts = insights.moodCounts.mapKeys { (mood, _) -> mood.label() },
        ),
        breakdownSection(
            title = uiText(R.string.section_energy),
            counts = insights.energyCounts.mapKeys { (energy, _) -> energy.label() },
        ),
        breakdownSection(
            title = uiText(R.string.section_pain),
            counts = insights.painCounts.mapKeys { (pain, _) -> pain.label() },
        ),
        breakdownSection(
            title = uiText(R.string.section_body_cues),
            counts = insights.dischargeCounts.mapKeys { (discharge, _) -> discharge.label() },
        ),
    ).filter { section -> section.items.isNotEmpty() }

    val cyclesLogged = insights.recentCycleLengths.size
    val cyclesNeeded = 2
    val daysLoggedThisCycle = insights.moodCounts.values.sum()
    val daysNeeded = 5

    return InsightsUiState(
        averageCycleText = pluralUiText(R.plurals.n_days, insights.averageCycleLength, insights.averageCycleLength),
        averagePeriodText = pluralUiText(R.plurals.n_days, insights.averagePeriodLength, insights.averagePeriodLength),
        variabilityLabel = if (insights.irregularCycles) {
            uiText(R.string.variability_more_variation)
        } else {
            uiText(R.string.variability_steady_lately)
        },
        variabilityText = if (insights.minCycleLength == insights.maxCycleLength) {
            pluralUiText(R.plurals.n_day_rhythm, insights.averageCycleLength, insights.averageCycleLength)
        } else {
            pluralUiText(
                R.plurals.min_max_day_range,
                insights.maxCycleLength,
                insights.minCycleLength,
                insights.maxCycleLength,
            )
        },
        variabilityDetail = when {
            !insights.hasEnoughCycleData -> uiText(R.string.variability_consistent)
            insights.irregularCycles -> uiText(R.string.variability_more_variation_than_usual)
            else -> uiText(R.string.variability_consistent)
        },
        variabilityInterpretation = when {
            !insights.hasEnoughCycleData -> uiText(R.string.variability_close_to_average)
            insights.irregularCycles -> uiText(R.string.variability_above_baseline)
            else -> uiText(R.string.variability_close_to_average)
        },
        cycleWindowLabel = UiText.Raw(insights.cycleWindowLabel),
        hasEnoughCycleData = insights.hasEnoughCycleData,
        hasEnoughSymptomData = insights.hasEnoughSymptomData,
        cyclesLoggedCount = cyclesLogged,
        cyclesNeededForInsights = cyclesNeeded,
        daysLoggedThisCycle = daysLoggedThisCycle,
        daysNeededForSymptomInsights = daysNeeded,
        cycleTrendBars = cycleTrendBars,
        periodTrendItems = periodTrendItems,
        cycleTrendSummary = when {
            !insights.hasEnoughCycleData -> TrendChartsBuildingSummary
            else -> pluralUiText(
                R.plurals.your_last_cycle_was_n_days_within_typical_range,
                insights.averageCycleLength,
                insights.averageCycleLength,
            )
        },
        symptomSummary = when {
            !insights.hasEnoughSymptomData -> SymptomPatternsLockedSummary
            else -> uiText(R.string.symptoms_show_patterns_related_to_cycle_phases)
        },
        symptomSections = symptomSections,
        phaseHighlights = insights.phaseHighlights.map { highlight ->
            InsightPhaseHighlightUi(
                badgeLabel = when (highlight.phase) {
                    InsightPhase.Period -> uiText(R.string.phase_period)
                    InsightPhase.FertileWindow -> uiText(R.string.phase_fertile_window)
                },
                title = UiText.Raw(highlight.title),
                detail = UiText.Raw(highlight.description),
            )
        },
        narrativeTitle = if (insights.hasEnoughCycleData || insights.hasEnoughSymptomData) {
            uiText(R.string.narrative_what_we_know)
        } else {
            uiText(R.string.narrative_build_stronger_patterns)
        },
        narrativeSummary = when {
            !insights.hasEnoughCycleData && !insights.hasEnoughSymptomData -> BuildStrongerPatternsSummary
            insights.narrativeSummary.isBlank() && cyclesLogged > 0 -> {
                pluralUiText(
                    R.plurals.your_last_cycle_was_n_days_keep_logging_to_refine_your_insights,
                    insights.averageCycleLength,
                    insights.averageCycleLength,
                )
            }
            insights.narrativeSummary.isBlank() -> {
                uiText(R.string.start_logging_to_build_insights)
            }
            else -> UiText.Raw(insights.narrativeSummary)
        },
        cycleProgressLabel = pluralUiText(R.plurals.n_of_m_cycles_logged, cyclesLogged, cyclesLogged, cyclesNeeded),
        symptomProgressLabel = if (daysLoggedThisCycle > 0) {
            pluralUiText(R.plurals.n_of_m_days_logged, daysLoggedThisCycle, daysLoggedThisCycle, daysNeeded)
        } else {
            uiText(R.string.log_a_few_days_to_see_patterns)
        },
    )
}

