package com.cyclecare.core.data

import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.InsightSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class LocalInsightRepository(
    private val cycleRepository: CycleRepository,
    private val symptomRepository: SymptomRepository,
    private val engine: InsightEngine,
) : InsightRepository {
    override fun observeInsights(): Flow<InsightSnapshot> =
        combine(cycleRepository.observeCycles(), symptomRepository.observeSymptoms()) { cycles, symptoms ->
            engine.build(cycles, symptoms)
        }.flowOn(Dispatchers.Default)
}

