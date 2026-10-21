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
            AppResetSequence(
                cancelArmedReminders = {
                    val armedRules = database.reminderRuleDao().getAllForBackup()
                    cancelArmedReminders(reminderAlarmNames(armedRules.map { it.id }))
                },
                clearTables = { database.clearAllTables() },
                deleteExports = { File(context.filesDir, "exports").deleteRecursively() },
                clearPreferences = { context.dataStore.edit { preferences -> preferences.clear() } },
                clearNotificationPermission = {
                    context.getSharedPreferences(notificationPermissionPrefsName, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                },
            ).run()
        }
    }
}

/**
 * The order a reset has to follow.
 *
 * The order is the whole contract, and it is the part a test can check
 * without a device: the armed reminders are cancelled first, while the
 * rules that address them still exist, and only then are the tables
 * cleared. A reset that cleared first would leave an alarm armed with
 * nothing to fire about, and the user would get a reminder for a period
 * the app no longer knows anything about.
 */
internal class AppResetSequence(
    private val cancelArmedReminders: suspend () -> Unit,
    private val clearTables: suspend () -> Unit,
    private val deleteExports: suspend () -> Unit,
    private val clearPreferences: suspend () -> Unit,
    private val clearNotificationPermission: suspend () -> Unit,
) {
    suspend fun run() {
        // A reminder that could not be cancelled is a problem worth
        // reporting, but it is not a reason to leave the health records
        // the user asked to delete sitting on the device. The reset
        // completes either way.
        runCatching { cancelArmedReminders() }
        clearTables()
        deleteExports()
        clearPreferences()
        clearNotificationPermission()
    }
}

/**
 * The names the armed reminders are addressed by.
 *
 * The same names the scheduler arms them under, so the cancellation
 * finds the alarm that was actually armed rather than a similar one.
 * The rule id is lower-cased by [reminderWorkName], which is what makes
 * the name stable across the case the rule was stored with.
 */
internal fun reminderAlarmNames(ruleIds: List<String>): List<String> =
    ruleIds.map { reminderWorkName(it) }
