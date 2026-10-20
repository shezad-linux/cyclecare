package com.cyclecare.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.reminderWorkName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAppResetRepository(
    private val context: Context,
    private val database: CycleCareDatabase,
    /**
     * Cancels whatever alarms are armed before the tables are cleared.
     *
     * Alarms are addressed by rule id, which lives in the very table this
     * reset is about to wipe. The names have to be snapshotted before the
     * clear and passed out to the app layer, which is the only layer that
     * knows which receiver class to address, so a stale reminder cannot
     * fire after a wipe — which is also the state an alarm would then
     * have to recover from on its own, with no rule to consult.
     */
    private val cancelArmedReminders: suspend (ruleAlarmNames: List<String>) -> Unit = {},
) : AppResetRepository {
    override suspend fun resetAllLocalData() {
        withContext(Dispatchers.IO) {
            val armedRules = database.reminderRuleDao().getAllForBackup()
            runCatching {
                cancelArmedReminders(armedRules.map { reminderWorkName(it.id) })
            }
            database.clearAllTables()
            File(context.filesDir, "exports").deleteRecursively()
            context.dataStore.edit { preferences -> preferences.clear() }
            context.getSharedPreferences(notificationPermissionPrefsName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}
