package com.cyclecare.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrivacyRulesTest {
    @Test
    fun pinValidation_requiresFourDigits() {
        assertThat(PrivacyRules.isPinValid("1234")).isTrue()
        assertThat(PrivacyRules.isPinValid("12")).isFalse()
        assertThat(PrivacyRules.isPinValid("12ab")).isFalse()
    }

    @Test
    fun unlockFreshness_expiresAfterGracePeriod() {
        assertThat(PrivacyRules.isUnlockFresh(lastUnlockEpochMillis = 1_000L, nowEpochMillis = 1_500L)).isTrue()
        assertThat(
            PrivacyRules.isUnlockFresh(
                lastUnlockEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + PrivacyRules.unlockGracePeriodMillis + 1,
            ),
        ).isFalse()
    }
}
