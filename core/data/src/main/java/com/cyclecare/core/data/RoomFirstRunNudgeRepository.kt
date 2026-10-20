package com.cyclecare.core.data

import com.cyclecare.core.database.AppSettingEntity
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.FirstRunNudgeRepository

/**
 * The first-run nudge flag, held in the app settings table.
 *
 * The table was declared with the rest of the schema and never used, and
 * it is the right shape for this: a durable key and value that survives a
 * process restart and is cleared with the rest of the local data when the
 * user resets it.
 */
class RoomFirstRunNudgeRepository(
    private val database: CycleCareDatabase,
) : FirstRunNudgeRepository {

    override suspend fun shouldShowFirstRunNudge(): Boolean =
        database.appSettingDao().get(FIRST_RUN_NUDGE_SHOWN_KEY) != "true"

    override suspend fun markFirstRunNudgeShown() {
        database.appSettingDao().put(
            AppSettingEntity(key = FIRST_RUN_NUDGE_SHOWN_KEY, value = "true"),
        )
    }

    private companion object {
        const val FIRST_RUN_NUDGE_SHOWN_KEY = "first_run_nudge_shown"
    }
}
