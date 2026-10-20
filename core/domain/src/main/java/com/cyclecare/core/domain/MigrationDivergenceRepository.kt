package com.cyclecare.core.domain

import com.cyclecare.core.model.MigrationDivergence
import kotlinx.coroutines.flow.Flow

/**
 * The durable record of what a schema migration did.
 *
 * Spec `02` §9.10.4 requires divergences to be logged and surfaced to
 * the user. The record lives in the database rather than in a log
 * buffer, because it has to outlive the process that performed the
 * migration and be readable by a later build.
 */
interface MigrationDivergenceRepository {
    fun observeDivergences(): Flow<List<MigrationDivergence>>
}
