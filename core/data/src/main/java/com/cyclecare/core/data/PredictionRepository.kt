package com.cyclecare.core.data

import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.prediction.CyclePredictionEngineV2
import com.cyclecare.core.domain.prediction.CyclePredictionInput
import com.cyclecare.core.domain.prediction.ObservedCycleStart
import com.cyclecare.core.domain.prediction.toSnapshot
import com.cyclecare.core.domain.time.TimeProvider
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class LocalPredictionRepository(
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val engine: CyclePredictionEngineV2,
    private val timeProvider: TimeProvider,
) : PredictionRepository {
    override fun observePrediction(): Flow<PredictionSnapshot?> =
        combine(profileRepository.observeProfile(), cycleRepository.observeCycles()) { profile, cycles ->
            val today = timeProvider.today()

            // Predicted rows are the v1 engine's own output written back
            // into the cycle table. Feeding them back as history would
            // let an estimate reinforce itself.
            val observed = cycles
                .filter { it.source != CycleSource.Predicted }
                .map { record ->
                    ObservedCycleStart(
                        id = record.id,
                        startDate = record.startDate,
                        endDate = record.endDate,
                        source = record.source,
                    )
                }

            val reported = profile?.averageCycleLength

            engine.predict(
                CyclePredictionInput(
                    observed = observed,
                    userReportedAverageCycleLength = reported,
                    today = today,
                ),
            )?.toSnapshot()
        }.flowOn(Dispatchers.Default)
}

