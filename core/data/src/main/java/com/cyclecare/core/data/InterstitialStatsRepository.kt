package com.cyclecare.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.edit

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cyclecare.core.domain.ads.InterstitialStatsRepository
import kotlinx.coroutines.flow.first

private object InterstitialPreferenceKeys {
    val completedSessions = intPreferencesKey("interstitial_completed_sessions")
    val lastShownAtEpochMillis = longPreferencesKey("interstitial_last_shown_at_epoch_millis")
    val shownOnDayKey = stringPreferencesKey("interstitial_shown_day_key")
    val shownOnDayCount = intPreferencesKey("interstitial_shown_day_count")
}

/**
 * DataStore-backed interstitial counters.
 *
 * The day key is a local date string, so the daily counter resets at local
 * midnight rather than at a UTC boundary that would put the reset at an
 * inconvenient hour for the user.
 *
 * The store is injected rather than taken from a fixed name so that a test
 * can use an isolated one. The shared store is process-wide, so a test
 * using it would see counters left behind by another test.
 */
class DataStoreInterstitialStatsRepository(
    private val dataStore: DataStore<Preferences>,
) : InterstitialStatsRepository {

    constructor(context: Context) : this(context.dataStore)

    override suspend fun completedSessions(): Int =
        dataStore.data.first()[InterstitialPreferenceKeys.completedSessions] ?: 0

    override suspend fun recordSession() {
        dataStore.edit { preferences ->
            val current = preferences[InterstitialPreferenceKeys.completedSessions] ?: 0
            preferences[InterstitialPreferenceKeys.completedSessions] = current + 1
        }
    }

    override suspend fun lastInterstitialShownAtEpochMillis(): Long? =
        dataStore.data.first()[InterstitialPreferenceKeys.lastShownAtEpochMillis]

    override suspend fun interstitialsShownOn(dayKey: String): Int {
        val preferences = dataStore.data.first()
        val storedDay = preferences[InterstitialPreferenceKeys.shownOnDayKey]
        // A stored count from a previous day is not a count for today.
        if (storedDay != dayKey) return 0
        return preferences[InterstitialPreferenceKeys.shownOnDayCount] ?: 0
    }

    override suspend fun recordInterstitialShown(epochMillis: Long, dayKey: String) {
        dataStore.edit { preferences ->
            preferences[InterstitialPreferenceKeys.lastShownAtEpochMillis] = epochMillis
            val storedDay = preferences[InterstitialPreferenceKeys.shownOnDayKey]
            val storedCount = preferences[InterstitialPreferenceKeys.shownOnDayCount] ?: 0
            if (storedDay == dayKey) {
                preferences[InterstitialPreferenceKeys.shownOnDayCount] = storedCount + 1
            } else {
                preferences[InterstitialPreferenceKeys.shownOnDayKey] = dayKey
                preferences[InterstitialPreferenceKeys.shownOnDayCount] = 1
            }
        }
    }

    companion object {
        /**
         * A repository backed by its own store file, for tests.
         *
         * The production store is process-wide, so a test using it would
         * inherit counters written by another test and could not assert on
         * a known starting state.
         */
        fun isolated(context: Context, name: String): DataStoreInterstitialStatsRepository {
            val store = PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile(name)
            }
            return DataStoreInterstitialStatsRepository(store)
        }
    }
}
