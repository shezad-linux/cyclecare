@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.cyclecare.core.designsystem.ScreenWidthPreviews
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.model.Mood

@ScreenWidthPreviews
@Composable
private fun InsightsOverviewPreview() {
    CycleCareTheme {
        InsightsOverviewScreen(
            uiState = previewInsightsUiState(),
            onOpenCycleTrends = {},
            onOpenSymptomPatterns = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun InsightsLowDataPreview() {
    CycleCareTheme {
        InsightsOverviewScreen(
            uiState = previewLowDataInsightsUiState(),
            onOpenCycleTrends = {},
            onOpenSymptomPatterns = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun CycleTrendsPreview() {
    CycleCareTheme {
        CycleTrendsScreen(
            uiState = previewInsightsUiState(),
            onBackToOverview = {},
        )
    }
}

@ScreenDebugPreviews
@Composable
private fun SymptomPatternsPreview() {
    CycleCareTheme {
        SymptomPatternsScreen(
            uiState = previewInsightsUiState(),
            onBackToOverview = {},
        )
    }
}

internal fun previewInsightsUiState(): InsightsUiState = InsightsUiState(
    averageCycleText = UiText.Raw("29 days"),
    averagePeriodText = UiText.Raw("5 days"),
    variabilityLabel = UiText.Raw("Steady lately"),
    variabilityText = UiText.Raw("27-31 day range"),
    variabilityDetail = UiText.Raw("Your cycle is consistent"),
    variabilityInterpretation = UiText.Raw("Recent cycles stay close to your average"),
    cycleWindowLabel = UiText.Raw("Based on 6 logged cycles"),
    hasEnoughCycleData = true,
    hasEnoughSymptomData = true,
    cyclesLoggedCount = 6,
    cyclesNeededForInsights = 2,
    daysLoggedThisCycle = 7,
    daysNeededForSymptomInsights = 5,
    cycleTrendBars = listOf(28, 30, 27, 29, 31, 29).mapIndexed { index, value ->
        InsightBarPointUi(UiText.Raw("C${index + 1}"), value, UiText.Raw("$value days"))
    },
    periodTrendItems = listOf(5, 5, 4, 5, 6, 5).mapIndexed { index, value ->
        InsightStripItemUi(UiText.Raw("P${index + 1}"), UiText.Raw("${value}d"))
    },
    cycleTrendSummary = UiText.Raw("Your last cycle was 29 days, within typical range"),
    symptomSummary = UiText.Raw("Your symptoms show patterns related to your cycle phases"),
    symptomSections = listOf(
        breakdownSection(UiText.Raw("Mood"), mapOf(UiText.Raw("Stable") to 6, UiText.Raw("Low") to 3, UiText.Raw("Happy") to 2, UiText.Raw("Irritable") to 1, UiText.Raw("Anxious") to 1)),
        breakdownSection(UiText.Raw("Energy"), mapOf(UiText.Raw("Steady") to 5, UiText.Raw("Low") to 4, UiText.Raw("High") to 2)),
        breakdownSection(UiText.Raw("Pain"), mapOf(UiText.Raw("None") to 4, UiText.Raw("Light") to 3, UiText.Raw("Moderate") to 4, UiText.Raw("Strong") to 1)),
        breakdownSection(UiText.Raw("Body cues"), mapOf(UiText.Raw("Dry") to 2, UiText.Raw("Creamy") to 3, UiText.Raw("Egg white") to 4)),
    ),
    phaseHighlights = listOf(
        InsightPhaseHighlightUi(
            badgeLabel = UiText.Raw("Period"),
            title = UiText.Raw("Period phase pattern"),
            detail = UiText.Raw("Moderate pain and low energy show up most often during your period."),
        ),
        InsightPhaseHighlightUi(
            badgeLabel = UiText.Raw("Fertile window"),
            title = UiText.Raw("Fertile window pattern"),
            detail = UiText.Raw("Egg white discharge and steady energy appear most often."),
        ),
    ),
    narrativeTitle = UiText.Raw("What we know"),
    narrativeSummary = UiText.Raw("Your cycle looks regular. Recent history shows 27 to 31 day cycles. Stable mood shows up most often."),
    cycleProgressLabel = UiText.Raw("6 of 2 cycles logged"),
    symptomProgressLabel = UiText.Raw("7 of 5 days logged this cycle"),
)

internal fun previewLowDataInsightsUiState(): InsightsUiState = InsightsUiState(
    averageCycleText = UiText.Raw("28 days"),
    averagePeriodText = UiText.Raw("5 days"),
    variabilityLabel = UiText.Raw("Getting started"),
    variabilityText = UiText.Raw("Early baseline"),
    variabilityDetail = UiText.Raw("Your cycle is consistent"),
    variabilityInterpretation = UiText.Raw("Recent cycles stay close to your average"),
    cycleWindowLabel = UiText.Raw("Based on your setup"),
    hasEnoughCycleData = false,
    hasEnoughSymptomData = false,
    cyclesLoggedCount = 1,
    cyclesNeededForInsights = 2,
    daysLoggedThisCycle = 3,
    daysNeededForSymptomInsights = 5,
    cycleTrendSummary = TrendChartsBuildingSummary,
    symptomSummary = SymptomPatternsLockedSummary,
    narrativeTitle = UiText.Raw("Build stronger patterns"),
    narrativeSummary = BuildStrongerPatternsSummary,
    cycleProgressLabel = UiText.Raw("1 of 2 cycles logged"),
    symptomProgressLabel = UiText.Raw("Log a few more days to see patterns"),
)
