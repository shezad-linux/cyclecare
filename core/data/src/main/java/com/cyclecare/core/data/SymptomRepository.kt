package com.cyclecare.core.data

import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.SymptomLog
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSymptomRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : SymptomRepository {
    override fun observeSymptoms(): Flow<List<SymptomLog>> =
        database.symptomLogDao().observeAll().map { logs -> logs.map { it.toModel(logger) } }

    override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> =
        database.symptomLogDao()
            .observeLatestForDate(date.toEpochDay())
            .map { it?.toModel(logger) }

    override suspend fun upsertSymptom(log: SymptomLog) {
        database.symptomLogDao().upsert(log.toEntity(System.currentTimeMillis()))
    }
}

