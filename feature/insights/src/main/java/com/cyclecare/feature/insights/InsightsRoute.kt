@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.domain.InsightRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class InsightsViewModel(
    insightRepository: InsightRepository,
) : ViewModel() {
    val uiState: StateFlow<InsightsUiState> = insightRepository.observeInsights()
        .map(::buildInsightsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())
}

class InsightsViewModelFactory(
    private val insightRepository: InsightRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InsightsViewModel(insightRepository) as T
}

@Composable
fun InsightsRoute(
    factory: InsightsViewModelFactory,
    onOpenCycleTrends: () -> Unit,
    onOpenSymptomPatterns: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsOverviewScreen(
        uiState = uiState,
        onOpenCycleTrends = onOpenCycleTrends,
        onOpenSymptomPatterns = onOpenSymptomPatterns,
    )
}

@Composable
fun InsightsCycleTrendsRoute(
    factory: InsightsViewModelFactory,
    onBackToOverview: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CycleTrendsScreen(
        uiState = uiState,
        onBackToOverview = onBackToOverview,
    )
}

@Composable
fun InsightsSymptomPatternsRoute(
    factory: InsightsViewModelFactory,
    onBackToOverview: () -> Unit,
) {
    val viewModel: InsightsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SymptomPatternsScreen(
        uiState = uiState,
        onBackToOverview = onBackToOverview,
    )
}

