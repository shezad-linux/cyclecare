package com.cyclecare.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppResetRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAppResetRepository(
    private val context: Context,
    private val database: CycleCareDatabase,
) : AppResetRepository {
    override suspend fun resetAllLocalData() {
        withContext(Dispatchers.IO) {
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

