package com.cyclecare.core.data

import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProfileRepository(
    private val database: CycleCareDatabase,
    private val logger: AppLogger = NoOpAppLogger,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> =
        database.userProfileDao().observeProfile().map { it?.toModel(logger) }

    override suspend fun saveProfile(profile: UserProfile) {
        database.userProfileDao().upsert(profile.toEntity())
    }

    override suspend fun updateRemindersEnabled(enabled: Boolean) {
        database.userProfileDao().updateRemindersEnabled(enabled)
    }
}

