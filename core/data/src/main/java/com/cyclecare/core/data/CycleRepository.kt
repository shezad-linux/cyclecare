package com.cyclecare.core.data

import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.model.CycleRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCycleRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : CycleRepository {
    override fun observeCycles(): Flow<List<CycleRecord>> =
        database.cycleRecordDao().observeAll().map { records -> records.map { it.toModel(logger) } }

    override suspend fun upsertCycle(record: CycleRecord) {
        database.cycleRecordDao().upsert(record.toEntity(System.currentTimeMillis()))
    }

    override suspend fun deleteCycle(id: String) {
        database.cycleRecordDao().softDelete(id, System.currentTimeMillis())
    }
}

