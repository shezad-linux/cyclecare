package com.cyclecare.feature.privacy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.model.ConsentState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeController(override val capability: MutableStateFlow<SecureAuthCapability>) :
        SecureAuthController {
        override fun authenticate(
            preference: SecureAuthPreference,
            title: String,
            subtitle: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit,
            onCancel: () -> Unit,
        ) = onFailure("Not available in tests.")
    }

    private fun setContent(repository: PrivacyRepository, capability: SecureAuthCapability) {
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme {
                PrivacyRoute(
                    factory = PrivacyViewModelFactory(repository),
                    secureAuthController = FakeController(MutableStateFlow(capability)),
                )
            }
        }
    }

    @Test
    fun freshInstall_opensInPinSetupWithNoDigitsEntered() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        composeRule.onNodeWithTag(PrivacyUiTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Create a 4-digit PIN for sensitive screens.").assertIsDisplayed()
        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}0").assertDoesNotExist()
    }

    @Test
    fun typingDigits_revealsOneDotPerDigit() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}1").performClick()
        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}2").performClick()

        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}0").assertIsDisplayed()
        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}1").assertIsDisplayed()
        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}2").assertDoesNotExist()
    }

    @Test
    fun deleteKey_removesTheLastEnteredDigit() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}1").performClick()
        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}2").performClick()
        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}delete").performClick()

        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}1").assertDoesNotExist()
        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}0").assertIsDisplayed()
    }

    @Test
    fun submitWithIncompletePin_showsErrorAndStaysInSetup() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}1").performClick()
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).performClick()

        composeRule.onNodeWithText("PIN must be exactly 4 digits.").assertIsDisplayed()
    }

    @Test
    fun completingSetupTwice_reachesTheProtectionDashboard() {
        val repository = FakePrivacyRepository()
        setContent(repository, SecureAuthCapability.Both)

        enterPin("1234")
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Enter the same PIN again to confirm it.").assertIsDisplayed()

        enterPin("1234")
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()

        assertThat(repository.pin).isEqualTo("1234")
        assertThat(repository.consent.value.appLockEnabled).isTrue()
        composeRule.onNodeWithTag(PrivacyUiTags.APP_LOCK_SWITCH).assertIsDisplayed()
        composeRule.onNodeWithText("App lock on").assertIsDisplayed()
    }

    @Test
    fun mismatchedConfirmation_showsErrorAndStoresNothing() {
        val repository = FakePrivacyRepository()
        setContent(repository, SecureAuthCapability.None)

        enterPin("1234")
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()

        enterPin("9999")
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("PINs didn't match. Start again.").assertIsDisplayed()
        assertThat(repository.pin).isNull()
    }

    @Test
    fun appLockSwitch_isDisabledOnTheDashboardWhenNoHardwareBackedAuthExists() {
        setContent(FakePrivacyRepository(alreadyLocked = true), SecureAuthCapability.None)

        composeRule.onNodeWithTag(PrivacyUiTags.APP_LOCK_SWITCH).assertIsDisplayed()
    }

    @Test
    fun recoverAction_isReachableFromTheDashboard() {
        setContent(FakePrivacyRepository(alreadyLocked = true), SecureAuthCapability.None)

        composeRule.onNodeWithTag(PrivacyUiTags.RECOVER_BUTTON).assertIsDisplayed().assertIsEnabled()
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit ->
            composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}$digit").performClick()
        }
        composeRule.waitForIdle()
    }

    private class FakePrivacyRepository(alreadyLocked: Boolean = false) : PrivacyRepository {
        val consent = MutableStateFlow(
            ConsentState(
                hideNotificationPreview = true,
                localDataAcknowledged = true,
                appLockEnabled = alreadyLocked,
                biometricEnabled = false,
            ),
        )

        private val pinState = MutableStateFlow(if (alreadyLocked) "1234" else null)

        val pin: String?
            get() = pinState.value

        override fun observeConsent(): Flow<ConsentState> = consent
        override fun hasPinConfigured(): Flow<Boolean> = pinState.map { it != null }
        override fun observeLastUnlockEpochMillis(): Flow<Long?> = MutableStateFlow(null)
        override fun isBiometricEnabled(): Flow<Boolean> = consent.map { it.biometricEnabled }
        override suspend fun setAppLockEnabled(enabled: Boolean) {
            consent.value = consent.value.copy(appLockEnabled = enabled)
        }

        override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
            consent.value = consent.value.copy(hideNotificationPreview = hidden)
        }

        override suspend fun acknowledgeLocalData() = Unit
        override suspend fun setPin(pin: String) {
            pinState.value = pin
        }

        override suspend fun clearPin() {
            pinState.value = null
        }

        override suspend fun resetAppLock() {
            pinState.value = null
        }

        override suspend fun validatePin(pin: String): Boolean = pinState.value == pin
        override suspend fun setBiometricEnabled(enabled: Boolean) {
            consent.value = consent.value.copy(biometricEnabled = enabled)
        }

        override suspend fun markUnlocked(epochMillis: Long) = Unit
    }
}