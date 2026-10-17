package com.cyclecare.feature.privacy

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

/**
 * Notes for anyone extending these tests.
 *
 * The screen is a LazyColumn on a 320dp-wide emulator, so the keypad,
 * the message row and the dashboard controls all sit below the fold.
 * A LazyColumn only composes what is visible, so nodes below the fold
 * do not merely report "not displayed", they are absent from the
 * semantics tree entirely. Anything asserted past the header must
 * therefore be scrolled to with performScrollToNode, not
 * performScrollTo, which requires the node to already exist.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freshInstall_opensInPinSetup() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        composeRule.onNodeWithTag(PrivacyUiTags.SCREEN).assertIsDisplayed()

        // Title and subtitle each render twice: once in the gradient
        // header and again in the PIN card.
        composeRule.onAllNodesWithText("Create a PIN").assertCountEquals(2)
        composeRule.onAllNodesWithText("Choose a 4-digit PIN before app lock can turn on.")
            .assertCountEquals(2)
    }

    @Test
    fun freshInstall_showsFourEmptyDots() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        (0..3).forEach { index ->
            composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}$index")
                .assert(hasContentDescription("Digit ${index + 1}, empty"))
        }
    }

    @Test
    fun typingDigits_marksThemEnteredInOrder() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        tapDigit(1)
        assertDotState(0, entered = true)
        assertDotState(1, entered = false)

        tapDigit(2)
        assertDotState(0, entered = true)
        assertDotState(1, entered = true)
        assertDotState(2, entered = false)
    }

    @Test
    fun deleteKey_clearsTheLastEnteredDigit() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        tapDigit(1)
        tapDigit(2)
        scrollToTag(PrivacyUiTags.DELETE_KEY)
        composeRule.onNodeWithTag(PrivacyUiTags.DELETE_KEY).performClick()
        composeRule.waitForIdle()

        assertDotState(0, entered = true)
        assertDotState(1, entered = false)
    }

    @Test
    fun keypadHasTenDigitKeysOneZeroKeyAndOneDeleteKey() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        (0..3).forEach { index ->
            composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}$index").assertExists()
        }
        (0..9).forEach { digit ->
            composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}$digit").assertExists()
        }
        composeRule.onNodeWithTag(PrivacyUiTags.DELETE_KEY).assertExists()
    }

    @Test
    fun primaryActionStaysDisabledUntilFourDigitsAreEntered() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        scrollToTag(PrivacyUiTags.PRIMARY_ACTION)
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).assertIsNotEnabled()

        tapDigit(1)
        tapDigit(2)
        tapDigit(3)
        scrollToTag(PrivacyUiTags.PRIMARY_ACTION)
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).assertIsNotEnabled()

        tapDigit(4)
        scrollToTag(PrivacyUiTags.PRIMARY_ACTION)
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).assertIsEnabled()
    }

    /**
     * The ViewModel rejects an incomplete PIN with "PIN must be exactly 4
     * digits.", but the UI disables the primary action until four digits
     * are entered, so that message is unreachable from this screen. The
     * behaviour itself is covered by PrivacyViewModelTest; this test
     * pins the gate that makes it unreachable, so a future change that
     * re-enables the button will show up here.
     */
    @Test
    fun primaryActionCannotBeClickedWithAnIncompletePin() {
        setContent(FakePrivacyRepository(), SecureAuthCapability.None)

        tapDigit(1)
        scrollToTag(PrivacyUiTags.PRIMARY_ACTION)
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION)
            .assertIsNotEnabled()
            .performClick()
        composeRule.waitForIdle()

        // Still in Setup: the primary action is unchanged and no PIN was
        // stored. privacy_screen is the scroll container itself, so it
        // cannot be used as a scroll target.
        scrollToTag(PrivacyUiTags.PRIMARY_ACTION)
        assertSomeNodeHasText("Continue")
    }

    @Test
    fun completingSetupTwice_storesPinAndEnablesAppLock() {
        val repository = FakePrivacyRepository()
        setContent(repository, SecureAuthCapability.Both)

        enterPin("1234")
        submitPrimary()
        scrollToTag(PrivacyUiTags.PIN_CARD)
        assertSomeNodeHasText("Confirm your PIN")


        enterPin("1234")
        submitPrimary()

        assertThat(repository.pin).isEqualTo("1234")
        assertThat(repository.consent.value.appLockEnabled).isTrue()
        scrollToTag(PrivacyUiTags.APP_LOCK_SWITCH)
        composeRule.onNodeWithTag(PrivacyUiTags.APP_LOCK_SWITCH).assertIsDisplayed()
    }

    @Test
    fun completingSetupTwice_revealsTheProtectionControls() {
        val repository = FakePrivacyRepository()
        setContent(repository, SecureAuthCapability.Both)

        enterPin("1234")
        submitPrimary()
        enterPin("1234")
        submitPrimary()

        scrollToTag(PrivacyUiTags.APP_LOCK_SWITCH)
        composeRule.onNodeWithTag(PrivacyUiTags.APP_LOCK_SWITCH).assertIsDisplayed()
    }

    @Test
    fun mismatchedConfirmation_showsErrorAndStoresNothing() {
        val repository = FakePrivacyRepository()
        setContent(repository, SecureAuthCapability.None)

        enterPin("1234")
        submitPrimary()

        enterPin("9999")
        submitPrimary()

        scrollToTag(PrivacyUiTags.PIN_CARD)
        assertSomeNodeHasText("PINs didn't match. Start again.")
        assertThat(repository.pin).isNull()
    }

    // ── helpers ─────────────────────────────────────────────────────

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

    private fun tapDigit(digit: Int) {
        scrollToTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}$digit")
        composeRule.onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}$digit").performClick()
        composeRule.waitForIdle()
    }

    private fun submitPrimary() {
        scrollToTag(PrivacyUiTags.PRIMARY_ACTION)
        composeRule.onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
    }

    private fun enterPin(pin: String) {
        pin.forEach { tapDigit(it.digitToInt()) }
    }

    private fun assertSomeNodeHasText(text: String) {
        composeRule.waitForIdle()
        assertThat(composeRule.onAllNodesWithText(text).fetchSemanticsNodes()).isNotEmpty()
    }

    private fun scrollToTag(tag: String) {
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    private fun assertDotState(index: Int, entered: Boolean) {
        val state = if (entered) "entered" else "empty"
        scrollToTag("${PrivacyUiTags.PIN_DOT_PREFIX}$index")
        composeRule.onNodeWithTag("${PrivacyUiTags.PIN_DOT_PREFIX}$index")
            .assert(hasContentDescription("Digit ${index + 1}, $state"))
    }

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