package com.cyclecare.core.data

import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE
import com.cyclecare.core.database.MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE
import com.cyclecare.core.database.MIGRATION_LOG_KIND_HISTORY_CARRIED_OVER
import com.cyclecare.core.database.MigrationLogEntity
import com.cyclecare.core.domain.MigrationDivergenceRepository
import com.cyclecare.core.model.MigrationDivergence
import com.cyclecare.core.model.MigrationDivergenceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Reads the durable record of what a migration did.
 *
 * The record is written by the migration itself, so it survives the
 * process that performed it and is still there the next time the app
 * is opened.
 */
class RoomMigrationDivergenceRepository(
    private val database: CycleCareDatabase,
) : MigrationDivergenceRepository {
    override fun observeDivergences(): Flow<List<MigrationDivergence>> =
        database.migrationLogDao().observeAll().map { records -> records.map { it.toModel() } }
}

/**
 * The repository used where there is no database, such as a preview or
 * a test that does not care about the migration record.
 */
class EmptyMigrationDivergenceRepository : MigrationDivergenceRepository {
    override fun observeDivergences(): Flow<List<MigrationDivergence>> = flowOf(emptyList())
}

internal fun MigrationLogEntity.toModel() = MigrationDivergence(
    kind = kind.toKind(),
    tableName = kind.toTableName(),
    recordId = recordId,
    dateEpochDay = dateEpochDay,
    storedValue = storedValue,
    observedValue = observedValue,
    detail = detail.orEmpty(),
    sourceVersion = migrationFrom,
    targetVersion = migrationTo,
)

/**
 * Maps the stored kind onto the known kinds.
 *
 * An unrecognised kind becomes [MigrationDivergenceKind.Unknown]
 * rather than an error: a record written by a later build still has to
 * be readable by an earlier one.
 */
internal fun String.toKind(): MigrationDivergenceKind = when (this) {
    MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE -> MigrationDivergenceKind.DuplicateSymptomRowCollapsed
    MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE -> MigrationDivergenceKind.CycleLengthDivergence
    MIGRATION_LOG_KIND_HISTORY_CARRIED_OVER -> MigrationDivergenceKind.HistoryCarriedOver
    else -> MigrationDivergenceKind.Unknown
}

/**
 * The table a kind was found in.
 *
 * The stored row does not name its table, because the kind already
 * says where it came from. The mapping is here so that a consumer
 * never has to know the storage detail.
 */
internal fun String.toTableName(): String = when (toKind()) {
    MigrationDivergenceKind.DuplicateSymptomRowCollapsed -> "symptom_logs"
    MigrationDivergenceKind.CycleLengthDivergence -> "cycle_records"
    MigrationDivergenceKind.HistoryCarriedOver -> "cycle_records"
    MigrationDivergenceKind.Unknown -> ""
}
