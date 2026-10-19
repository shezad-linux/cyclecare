package com.cyclecare.core.data

import com.cyclecare.core.domain.SyncAdapter

class NoOpSyncAdapter : SyncAdapter {
    override suspend fun pushPendingChanges() = Unit

    override suspend fun pullRemoteChanges() = Unit

    override suspend fun mergePreview(): String = "Sync is not enabled in v1."
}

