package com.cyclecare.core.domain

object PrivacyRules {
    const val unlockGracePeriodMillis: Long = 5 * 60 * 1000

    fun isPinValid(pin: String): Boolean = pin.length == 4 && pin.all(Char::isDigit)

    fun isUnlockFresh(
        lastUnlockEpochMillis: Long?,
        nowEpochMillis: Long,
        gracePeriodMillis: Long = unlockGracePeriodMillis,
    ): Boolean {
        if (lastUnlockEpochMillis == null) return false
        return nowEpochMillis - lastUnlockEpochMillis in 0..gracePeriodMillis
    }
}
