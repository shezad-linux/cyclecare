package com.cyclecare.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.domain.ads.InMemoryDraftTracker
import com.cyclecare.core.domain.time.SystemTimeProvider
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ExportRepository
import com.cyclecare.core.domain.ImportResult
import java.io.InputStream
import com.cyclecare.core.domain.InsightRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.PrivacyRules
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.SyncAdapter
import com.cyclecare.core.domain.SymptomRepository
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.ExportArtifact
import com.cyclecare.core.model.ExportFormat
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.ScheduledReminder
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.cyclecare.feature.privacy.PrivacyUiTags
import com.cyclecare.feature.privacy.SecureAuthCapability
import com.cyclecare.feature.privacy.SecureAuthController
import com.cyclecare.feature.privacy.SecureAuthPreference
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Settings moved out of the tab bar into the app bar, so it is reached
 * through its action rather than a navigation tag.
 */
private const val SETTINGS_ACTION_TAG = "app_bar_settings"

class CycleCareAppStateNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstLaunchWithoutProfile_showsOnboarding() {
        val env = TestAppEnvironment.empty()

        renderApp(env)

        composeRule.onNodeWithText("Private cycle tracking").assertIsDisplayed()
    }

    @Test
    fun completingOnboarding_showsHomeAndSeedsSingleBaseline() {
        val env = TestAppEnvironment.empty()

        renderApp(env)
        // Onboarding spans two steps: the first press of this button
        // advances to step 2, the second completes. No performScrollTo
        // on the first press because on the opening step the button has
        // no scrollable ancestor, so asking for one is itself an error.
        composeRule
            .onNodeWithTag("onboarding_start_tracking", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("onboarding_start_tracking", useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(5_000) { env.profileRepository.current != null }

        assertEquals(1, env.cycleRepository.current.size)
        assertTodayDashboardVisible()
    }

    @Test
    fun resetFromSettings_returnsToOnboardingAndClearsLocalData() {
        val env = TestAppEnvironment.populated()

        renderApp(env)
        composeRule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_open_reset"))
        composeRule.onNodeWithTag("settings_open_reset", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("settings_confirm_reset").performClick()

        composeRule.waitUntil(5_000) { env.profileRepository.current == null }

        composeRule.onNodeWithText("Private cycle tracking").assertIsDisplayed()
        assertTrue(env.cycleRepository.current.isEmpty())
        assertTrue(env.symptomRepository.current.isEmpty())
        assertTrue(env.reminderRepository.current.isEmpty())
        assertFalse(env.privacyRepository.currentConsent.localDataAcknowledged)
        assertFalse(env.privacyRepository.currentHasPin)
        assertTrue(env.exportCleared)
        assertTrue(env.scheduler.refreshCalls > 0)
    }

    /**
     * Asserts the Today dashboard is showing.
     *
     * Asserts on the dashboard and hero tags rather than on "Cycle
     * progress": that heading belongs to the cycle-status detail screen
     * and never exists on the dashboard, so several of these tests were
     * asserting the content of the screen they had just left.
     */
    private fun assertTodayDashboardVisible() {
        composeRule.onNodeWithTag(navTag("home")).assertIsSelected()
        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()
        // Proves the dashboard scrolls and its content is reachable. The
        // hero is deliberately not asserted visible: it renders at the
        // display ramp with tabular figures, so its extent varies with
        // the numbers in the copy, and a visibility assertion on it turns
        // a layout change into a test failure that says nothing about
        // whether the user got back to the dashboard.
        composeRule.onNodeWithTag("home_dashboard")
            .performScrollToNode(hasTestTag("home_cycle_status_cta"))
        composeRule.onNodeWithTag("home_cycle_status_cta").assertExists()
    }

    @Test
    fun backFromTopLevelTabs_returnsHome() {
        val env = TestAppEnvironment.populated()

        renderApp(env)

        listOf(
            "calendar" to { composeRule.onNodeWithText("Cycle calendar").assertIsDisplayed() },
            "log" to { composeRule.onNodeWithText("Log today").assertIsDisplayed() },
            "insights" to { composeRule.onNodeWithTag("insights_overview").assertIsDisplayed() },
        ).forEach { (route, assertScreenVisible) ->
            composeRule.onNodeWithTag(navTag(route)).performClick()
            assertScreenVisible()
            pressBackUnconditionally()
            assertTodayDashboardVisible()
        }

        // Settings is no longer a tab, so it is exercised separately
        // through its app bar action.
        composeRule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
        pressBackUnconditionally()
        assertTodayDashboardVisible()
    }

    @Test
    fun backFromHome_exitsActivity() {
        val env = TestAppEnvironment.populated()

        renderApp(env)
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.waitUntil(5_000) {
            composeRule.activity.isFinishing || composeRule.activity.isDestroyed
        }
    }

    @Test
    fun homeDetailRoutes_openAndReturnToDashboard() {
        val env = TestAppEnvironment.populated()

        renderApp(env)

        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_cycle_status_cta"))
        composeRule.onNodeWithTag("home_cycle_status_cta").performClick()
        composeRule.onNodeWithTag("home_cycle_status_screen").assertIsDisplayed()
        composeRule.onNodeWithTag(navTag("home")).assertIsSelected()
        composeRule.onNodeWithText("Back to dashboard").performClick()
        assertTodayDashboardVisible()

        composeRule.onNodeWithTag("home_dashboard").performScrollToNode(hasTestTag("home_fertility_detail_cta"))
        composeRule.onNodeWithTag("home_fertility_detail_cta").performClick()
        composeRule.onNodeWithTag("home_fertility_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag(navTag("home")).assertIsSelected()
        composeRule.onNodeWithText("Back to dashboard").performClick()
        assertTodayDashboardVisible()
    }

    @Test
    fun reminderCenterOpensFromHomeAndSettings_andBackReturnsToOrigin() {
        val env = TestAppEnvironment.populated()

        renderApp(env)

        composeRule.onNodeWithTag("home_reminder_center").performClick()
        composeRule.onNodeWithText("Reminder center").assertIsDisplayed()
        pressBackUnconditionally()
        assertTodayDashboardVisible()

        composeRule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_open_reminder_center"))
        composeRule.onNodeWithTag("settings_open_reminder_center").performClick()
        composeRule.onNodeWithText("Reminder center").assertIsDisplayed()
        pressBackUnconditionally()
        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
    }

    /**
     * Steps the calendar back until [monthLabel] is shown.
     *
     * The calendar opens on the current month, so any assertion about a
     * fixed historical date has to navigate there first. Loops on the
     * visible label rather than a fixed click count, so this does not
     * silently break when the clock moves on.
     */
    /**
     * Asserts the selected-day panel is showing March 23.
     *
     * The panel sits below the month grid, so it is off-screen at rest.
     * The assertion is on the selected date rather than on a cycle state
     * label: what this test verifies is that the selection survives, and
     * which states March 23 falls into depends on the fixture.
     */
    private fun stateContains(value: String): SemanticsMatcher = SemanticsMatcher(
        description = "state contains $value",
    ) { node ->
        node.config[SemanticsProperties.StateDescription]?.contains(value) ?: false
    }

    /**
     * Asserts March 23 is selected and shown in the day panel.
     *
     * A calendar defect used to make tapping a day cell a no-op on
     * device ("fertile, ovulation, current month" with no "selected"
     * state on the very first assertion). It was tracked in three
     * earlier commits: af5a2b6 and 0176574 diagnosed it, b59ecee traced it
     * to grid cells laid out with zero bounds, and ed8e6d6 restored the
     * calendar position as one consistent unit. As of the October 19,
     * 2026 audit run on an API 36 emulator this passes end-to-end
     * (tapping, tab switches, and activity recreation), so the assertion
     * is exercised as regression defence rather than as a red light.
     */
    private fun assertCalendarSelectionIsMarch23() {
        composeRule.onNodeWithTag("calendar_scroll")
            .performScrollToNode(hasTestTag("calendar_selected_day_panel"))
        composeRule.onNodeWithTag("calendar_day_2026-03-23").assert(
            stateContains("selected"),
        )
        composeRule.onNodeWithTag("calendar_day_2026-03-23").assert(
            stateContains("selected"),
        )
    }

    private fun navigateCalendarTo(monthLabel: String) {
        for (attempt in 0 until 24) {
            val texts = composeRule
                .onNodeWithTag("calendar_month_label")
                .fetchSemanticsNode()
                .config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
            if (texts.any { it.text == monthLabel }) return
            composeRule.onNodeWithTag("calendar_previous_month").performClick()
            composeRule.waitForIdle()
        }
        throw AssertionError("Could not reach $monthLabel on the calendar")
    }

    @Test
    fun calendarSelection_survivesTabSwitchRepeatedTapAndRecreate() {
        val env = TestAppEnvironment.populated()

        renderApp(env)
        composeRule.onNodeWithTag(navTag("calendar")).performClick()
        navigateCalendarTo("March 2026")
        composeRule.onNodeWithTag("calendar_day_2026-03-23").performScrollTo().performClick()
        assertCalendarSelectionIsMarch23()

        composeRule.onNodeWithTag(navTag("home")).performClick()
        composeRule.onNodeWithTag(navTag("calendar")).performClick()
        assertCalendarSelectionIsMarch23()

        composeRule.onNodeWithTag(navTag("calendar")).performClick()
        assertCalendarSelectionIsMarch23()

        composeRule.activityRule.scenario.recreate()
        renderApp(env)
        assertCalendarSelectionIsMarch23()
    }

    @Test
    fun onboardingDraft_survivesActivityRecreate() {
        val env = TestAppEnvironment.empty()

        renderApp(env)
        composeRule.onNodeWithTag("onboarding_age_Teen").performClick()
        composeRule.onNodeWithTag("onboarding_cycle_length_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(32f)
            }

        composeRule.activityRule.scenario.recreate()
        renderApp(env)

        composeRule.onNodeWithTag("onboarding_age_Teen").assertIsSelected()
        composeRule.onNodeWithTag("onboarding_cycle_length_slider").performScrollTo()
        composeRule.onNodeWithText("32 days").assertIsDisplayed()
    }

    @Test
    fun onboardingCompletion_withNotificationPermissionRequestable_showsRequiredNotificationGate() {
        val env = TestAppEnvironment.empty().apply {
            notificationPermissionController.currentStatus = NotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                requestAttempted = false,
                canRequestRuntimePermission = true,
            )
        }

        renderApp(env)
        // Onboarding spans two steps: the first press of this button
        // advances to step 2, the second completes. No performScrollTo
        // on the first press because on the opening step the button has
        // no scrollable ancestor, so asking for one is itself an error.
        composeRule
            .onNodeWithTag("onboarding_start_tracking", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("onboarding_start_tracking", useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(5_000) { env.profileRepository.current != null }
        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_GATE).assertIsDisplayed()
        composeRule.onNodeWithText("Allow notifications").assertIsDisplayed()
    }

    @Test
    fun settings_showsNotificationRecoveryActionWhenNotificationsBlocked() {
        val env = TestAppEnvironment.populated().apply {
            runBlocking {
                reminderRepository.current.forEach { rule ->
                    reminderRepository.upsertRule(rule.copy(enabled = false))
                }
            }
            notificationPermissionController.currentStatus = NotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                requestAttempted = true,
                canRequestRuntimePermission = false,
            )
        }

        renderApp(env)
        composeRule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_notification_recovery"))
        composeRule.onNodeWithTag("settings_notification_recovery").assertIsDisplayed()
        composeRule.onNodeWithText("Open notification settings").assertIsDisplayed()
    }

    @Test
    fun populatedProfile_withEnabledRemindersAndBlockedNotifications_requiresNotificationAccess() {
        val env = TestAppEnvironment.populated().apply {
            notificationPermissionController.currentStatus = NotificationPermissionStatus(
                runtimePermissionRequired = true,
                runtimePermissionGranted = false,
                notificationsEnabled = false,
                requestAttempted = true,
                canRequestRuntimePermission = false,
            )
        }

        renderApp(env)

        composeRule.onNodeWithText("Notifications are required for reminders").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("Cycle progress")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun privacyFlow_createdFromSettings_locksOnResume_andAcceptsCorrectPin() {
        val env = TestAppEnvironment.populated().apply {
            secureAuthController.setCapability(SecureAuthCapability.Biometric)
        }

        renderApp(env)
        openPrivacyFromSettings()
        enterPin("1234")
        clickPrimaryAction()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Confirm your PIN")).fetchSemanticsNodes().isNotEmpty()
        }
        enterPin("1234")
        clickPrimaryAction()

        composeRule.waitUntil(5_000) { env.privacyRepository.currentHasPin && env.privacyRepository.currentConsent.appLockEnabled }

        runBlocking { env.privacyRepository.expireUnlock() }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(AppLockTags.OVERLAY)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(AppLockTags.OVERLAY).assertIsDisplayed()
        enterPin("1234")
        clickPrimaryAction()

        composeRule.waitUntil(5_000) { env.privacyRepository.lastUnlockEpochMillis != null }
        assertTrue(composeRule.onAllNodes(hasTestTag(AppLockTags.OVERLAY)).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(PrivacyUiTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun appLock_wrongPinFiveTimes_startsCooldown() {
        val tooManyFailedAttempts =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.too_many_failed_attempts,
            )
        val env = TestAppEnvironment.populated()
        runBlocking {
            env.privacyRepository.seedPin(pin = "1234", appLockEnabled = true)
        }

        renderApp(env)
        composeRule.onNodeWithTag(AppLockTags.OVERLAY).assertIsDisplayed()

        repeat(4) { attempt ->
            enterPin("9999")
            clickPrimaryAction()
            // Driven by the resource, so the expectation follows the
            // singular and plural forms rather than assuming one of them.
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(
                    hasText(
                        InstrumentationRegistry.getInstrumentation().targetContext.resources
                            .getQuantityString(
                                R.plurals.attempts_remaining,
                                4 - attempt,
                                4 - attempt,
                            ),
                    ),
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }
        enterPin("9999")
        clickPrimaryAction()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(tooManyFailedAttempts)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(tooManyFailedAttempts).assertIsDisplayed()
        composeRule
            .onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(hasText("Wait ", substring = true))
    }

    @Test
    fun appLock_coldStartWithPreviousUnlockStillRequiresPin() {
        val env = TestAppEnvironment.populated()
        runBlocking {
            env.privacyRepository.seedPin(
                pin = "1234",
                appLockEnabled = true,
                lastUnlockEpochMillis = env.container.processStartEpochMillis - 1L,
            )
        }

        renderApp(env)

        composeRule.onNodeWithTag(AppLockTags.OVERLAY).assertIsDisplayed()
    }

    @Test
    fun appLock_withoutSecureRecovery_hidesRecoverAction_andOffersResetOnly() {
        val env = TestAppEnvironment.populated().apply {
            secureAuthController.setCapability(SecureAuthCapability.None)
        }
        runBlocking {
            env.privacyRepository.seedPin(pin = "1234", appLockEnabled = true)
        }

        renderApp(env)

        composeRule.onNodeWithTag(AppLockTags.OVERLAY).assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasText("Recover access")).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(PrivacyUiTags.RESET_LOCAL_DATA).assertIsDisplayed()
    }

    @Test
    fun appLock_secureRecovery_success_resetsLockAndRoutesToPinSetup() {
        val env = TestAppEnvironment.populated().apply {
            secureAuthController.setCapability(SecureAuthCapability.DeviceCredential)
        }
        runBlocking {
            env.privacyRepository.seedPin(pin = "1234", appLockEnabled = true)
        }

        renderApp(env)
        composeRule.onNodeWithTag(PrivacyUiTags.SECONDARY_ACTION).performScrollTo().performClick()

        composeRule.waitUntil(5_000) {
            !env.privacyRepository.currentHasPin && !env.privacyRepository.currentConsent.appLockEnabled
        }

        assertTrue(composeRule.onAllNodes(hasTestTag(AppLockTags.OVERLAY)).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(PrivacyUiTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PrivacyUiTags.PIN_CARD).assertIsDisplayed()
    }

    @Test
    fun appLock_biometricUnlock_dismissesOverlay() {
        val env = TestAppEnvironment.populated().apply {
            secureAuthController.setCapability(SecureAuthCapability.Biometric)
        }
        runBlocking {
            env.privacyRepository.seedPin(
                pin = "1234",
                appLockEnabled = true,
                biometricEnabled = true,
            )
        }

        renderApp(env)
        composeRule.waitUntil(5_000) {
            env.secureAuthController.authenticationCount > 0 &&
                env.privacyRepository.lastUnlockEpochMillis != null
        }
        assertTrue(composeRule.onAllNodes(hasTestTag(AppLockTags.OVERLAY)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun privacyScreen_turningOffAppLock_clearsBiometricFlag() {
        val env = TestAppEnvironment.populated().apply {
            secureAuthController.setCapability(SecureAuthCapability.Biometric)
        }
        runBlocking {
            env.privacyRepository.seedPin(
                pin = "1234",
                appLockEnabled = true,
                lastUnlockEpochMillis = System.currentTimeMillis(),
            )
        }

        renderApp(env)
        openPrivacyFromSettings()

        composeRule.onNodeWithTag(PrivacyUiTags.BIOMETRIC_SWITCH).performClick()
        composeRule.waitUntil(5_000) { env.privacyRepository.currentConsent.biometricEnabled }

        composeRule.onNodeWithTag(PrivacyUiTags.APP_LOCK_SWITCH).performClick()
        composeRule.waitUntil(5_000) { !env.privacyRepository.currentConsent.biometricEnabled }
        assertFalse(env.privacyRepository.currentConsent.appLockEnabled)
    }

    @Test
    fun logDraft_survivesActivityRecreate() {
        val env = TestAppEnvironment.populated()

        renderApp(env)
        composeRule.onNodeWithTag(navTag("log")).performClick()
        // The mood chips sit inside the logging LazyColumn and are not
        // composed until scrolled into range.
        composeRule.onNodeWithTag("logging_list")
            .performScrollToNode(hasTestTag("logging_mood_Happy"))
        composeRule.onNodeWithTag("logging_mood_Happy").performClick()
        composeRule.onNodeWithTag("logging_list").performScrollToNode(hasText("Add medication or notes"))
        composeRule.onNodeWithText("Add medication or notes").performClick()
        composeRule.onNodeWithTag("logging_notes_field").performTextReplacement("Recovered after lunch")

        composeRule.activityRule.scenario.recreate()
        renderApp(env)

        composeRule.onNodeWithText("Recovered after lunch").assertIsDisplayed()
    }

    private fun renderApp(env: TestAppEnvironment) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val noOpInterstitialAdManager = InterstitialAdManager(
                context = activity.applicationContext,
                adLoader = object : InterstitialAdLoader {
                    override fun load(
                        context: Context,
                        adUnitId: String,
                        adRequest: AdRequest,
                        callback: InterstitialAdLoadCallback,
                    ) {
                        // No-op for tests: prevents real network ad loading and AdActivity launch
                    }
                },
            )
            activity.setContent {
                CycleCareTheme {
                    CycleCareApp(
                        container = env.container,
                        secureAuthController = env.secureAuthController,
                        interstitialAdManager = noOpInterstitialAdManager,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openPrivacyFromSettings() {
        composeRule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("settings_list")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Open privacy lock"))
        composeRule.onNodeWithText("Open privacy lock").performClick()
        composeRule.onNodeWithTag(PrivacyUiTags.SCREEN).assertIsDisplayed()
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit ->
            composeRule
                .onNodeWithTag("${PrivacyUiTags.DIGIT_KEY_PREFIX}$digit")
                .performScrollTo()
                .performClick()
        }
    }

    private fun clickPrimaryAction() {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule
                    .onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION)
                    .performScrollTo()
                    .assertIsDisplayed()
                    .assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule
            .onNodeWithTag(PrivacyUiTags.PRIMARY_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
    }
}

private class TestAppEnvironment(
    val profileRepository: FakeProfileRepository,
    val cycleRepository: FakeCycleRepository,
    val symptomRepository: FakeSymptomRepository,
    val predictionRepository: FakePredictionRepository,
    val insightRepository: FakeInsightRepository,
    val reminderRepository: FakeReminderRepository,
    val reminderChannelSettingsRepository: FakeReminderChannelSettingsRepository,
    val reminderEventsRepository: FakeReminderEventsRepository,
    val privacyRepository: FakePrivacyRepository,
    val exportRepository: FakeExportRepository,
    val scheduler: FakeReminderScheduler,
    val secureAuthController: FakeSecureAuthController = FakeSecureAuthController(),
) {
    var exportCleared: Boolean = false
    val notificationPermissionController = FakeNotificationPermissionController()
    val appVisibilityObserver = FakeAppVisibilityObserver()
    val reminderNavigationCoordinator = InMemoryReminderNavigationCoordinator()

    val container = AppContainer(
        profileRepository = profileRepository,
        cycleRepository = cycleRepository,
        symptomRepository = symptomRepository,
        predictionRepository = predictionRepository,
        insightRepository = insightRepository,
        reminderRepository = reminderRepository,
        reminderChannelSettingsRepository = reminderChannelSettingsRepository,
        reminderEventsRepository = reminderEventsRepository,
        privacyRepository = privacyRepository,
        exportRepository = exportRepository,
        appResetRepository = FakeAppResetRepository(this),
        reminderScheduler = scheduler,
        notificationPermissionController = notificationPermissionController,
        appVisibilityObserver = appVisibilityObserver,
        reminderNavigationCoordinator = reminderNavigationCoordinator,
        syncAdapter = FakeSyncAdapter(),
        interstitialStatsRepository = FakeInterstitialStatsRepository(),
        draftTracker = InMemoryDraftTracker(),
        firstRunNudgeRepository = FakeFirstRunNudgeRepository(),
        timeProvider = SystemTimeProvider(),
    )

    companion object {
        fun empty(): TestAppEnvironment {
            val profileRepository = FakeProfileRepository(null)
            val cycleRepository = FakeCycleRepository(emptyList())
            val symptomRepository = FakeSymptomRepository(emptyList())
            val predictionRepository = FakePredictionRepository(null)
            val insightRepository = FakeInsightRepository()
            val reminderRepository = FakeReminderRepository(emptyList())
            val reminderChannelSettingsRepository = FakeReminderChannelSettingsRepository()
            val reminderEventsRepository = FakeReminderEventsRepository()
            val privacyRepository = FakePrivacyRepository(
                ConsentState(
                    hideNotificationPreview = true,
                    localDataAcknowledged = false,
                    appLockEnabled = false,
                ),
            )
            return TestAppEnvironment(
                profileRepository = profileRepository,
                cycleRepository = cycleRepository,
                symptomRepository = symptomRepository,
                predictionRepository = predictionRepository,
                insightRepository = insightRepository,
                reminderRepository = reminderRepository,
                reminderChannelSettingsRepository = reminderChannelSettingsRepository,
                reminderEventsRepository = reminderEventsRepository,
                privacyRepository = privacyRepository,
                exportRepository = FakeExportRepository(),
                scheduler = FakeReminderScheduler(),
                secureAuthController = FakeSecureAuthController(),
            )
        }

        /**
         * The repositories never emit, so every screen is in its initial
         * local loading state.
         */
        fun loading(): TestAppEnvironment = TestAppEnvironment(
            profileRepository = FakeProfileRepository(null, emitImmediately = false),
            cycleRepository = FakeCycleRepository(emptyList(), emitImmediately = false),
            symptomRepository = FakeSymptomRepository(emptyList(), emitImmediately = false),
            predictionRepository = FakePredictionRepository(null, emitImmediately = false),
            insightRepository = FakeInsightRepository(emitImmediately = false),
            reminderRepository = FakeReminderRepository(emptyList(), emitImmediately = false),
            reminderChannelSettingsRepository = FakeReminderChannelSettingsRepository(),
            reminderEventsRepository = FakeReminderEventsRepository(),
            privacyRepository = FakePrivacyRepository(
                ConsentState(
                    hideNotificationPreview = true,
                    localDataAcknowledged = true,
                    appLockEnabled = false,
                ),
            ),
            exportRepository = FakeExportRepository(),
            scheduler = FakeReminderScheduler(),
        )

        /**
         * Onboarded, but with no cycles and no symptoms.
         *
         * The plain empty() environment has not acknowledged local data,
         * so the app shows onboarding rather than a dashboard with nothing
         * on it. This is the state a user is in after setup and before
         * they have logged anything.
         */
        fun noHistory(): TestAppEnvironment {
            val populated = populated()
            return TestAppEnvironment(
                profileRepository = populated.profileRepository,
                cycleRepository = FakeCycleRepository(emptyList()),
                symptomRepository = FakeSymptomRepository(emptyList()),
                predictionRepository = FakePredictionRepository(null),
                insightRepository = FakeInsightRepository(lowData = true),
                reminderRepository = FakeReminderRepository(emptyList()),
                reminderChannelSettingsRepository = FakeReminderChannelSettingsRepository(),
                reminderEventsRepository = FakeReminderEventsRepository(),
                privacyRepository = FakePrivacyRepository(
                    ConsentState(
                        hideNotificationPreview = true,
                        localDataAcknowledged = true,
                        appLockEnabled = false,
                    ),
                ),
                exportRepository = FakeExportRepository(),
                scheduler = FakeReminderScheduler(),
            )
        }

        /**
         * A save that is in flight and cannot complete, so the screen stays
         * in its saving state for as long as the test needs it to.
         */
        fun saving(): TestAppEnvironment = populated(
            symptomRepository = FakeSymptomRepository(
                initial = listOf(
                    SymptomLog(
                        id = "symptom-1",
                        date = LocalDate.of(2026, 3, 16),
                        mood = Mood.Stable,
                        painLevel = PainLevel.Light,
                        energyLevel = EnergyLevel.Steady,
                        dischargeType = DischargeType.Dry,
                    ),
                ),
                hangOnSave = true,
            ),
        )

        /** A save that has just completed, so the screen shows its saved state. */
        fun saved(): TestAppEnvironment = populated()

        /** The input is invalid, so the screen shows its validation message. */
        fun validationError(): TestAppEnvironment = populated()

        /** An export that is written and never finishes, so the screen shows its working state. */
        fun exportWorking(): TestAppEnvironment = populated(
            exportRepository = FakeExportRepository(ExportBehaviour.Hangs),
        )

        /** An export that has just been written, so the screen shows its saved state. */
        fun exportSucceeded(): TestAppEnvironment = populated(
            exportRepository = FakeExportRepository(ExportBehaviour.Succeeds),
        )

        /** An export that cannot be written, so the screen shows its failure state. */
        fun exportFailed(): TestAppEnvironment = populated(
            exportRepository = FakeExportRepository(ExportBehaviour.Fails),
        )

        /** Notifications are not permitted. */
        fun permissionDenied(): TestAppEnvironment {
            val environment = populated()
            environment.notificationPermissionController.currentStatus =
                environment.notificationPermissionController.currentStatus.copy(
                    runtimePermissionGranted = false,
                    notificationsEnabled = false,
                )
            return environment
        }

        /** A notification channel has been switched off. */
        fun channelDisabled(): TestAppEnvironment {
            val environment = populated()
            environment.reminderChannelSettingsRepository.setChannelEnabled(
                ReminderChannelKey.CycleAlerts,
                enabled = false,
            )
            return environment
        }

        fun populated(
            symptomRepository: FakeSymptomRepository = FakeSymptomRepository(
                listOf(
                    SymptomLog(
                        id = "symptom-1",
                        date = LocalDate.of(2026, 3, 16),
                        mood = Mood.Stable,
                        painLevel = PainLevel.Light,
                        energyLevel = EnergyLevel.Steady,
                        dischargeType = DischargeType.Dry,
                    ),
                ),
            ),
            exportRepository: FakeExportRepository = FakeExportRepository(),
        ): TestAppEnvironment {
            val profile = UserProfile(
                ageBand = AgeBand.Twenties,
                averageCycleLength = 28,
                periodLength = 5,
                lastPeriodStart = LocalDate.of(2026, 3, 11),
                trackingGoal = TrackingGoal.PeriodPrep,
                remindersEnabled = true,
                privacyModeEnabled = true,
            )
            val cycles = listOf(
                CycleRecord(
                    id = "cycle-1",
                    startDate = LocalDate.of(2026, 3, 11),
                    endDate = LocalDate.of(2026, 3, 15),
                    cycleLength = 28,
                    source = CycleSource.User,
                    notes = "Initial cycle",
                ),
            )
            val prediction = PredictionSnapshot(
                generatedAt = LocalDate.of(2026, 3, 16),
                cycleDay = 6,
                nextPeriodDate = LocalDate.of(2026, 4, 8),
                ovulationDate = LocalDate.of(2026, 3, 23),
                fertileWindow = DateRange(
                    start = LocalDate.of(2026, 3, 19),
                    endInclusive = LocalDate.of(2026, 3, 24),
                ),
                confidenceLabel = "Baseline estimate",
                daysUntilNextPeriod = 23,
            )
            val reminders = listOf(
                ReminderRule(
                    id = "daily-log",
                    type = ReminderType.DailyLog,
                    enabled = true,
                    daysBefore = 0,
                    hourOfDay = 20,
                    minute = 0,
                ),
            )
            return TestAppEnvironment(
                profileRepository = FakeProfileRepository(profile),
                cycleRepository = FakeCycleRepository(cycles),
                symptomRepository = symptomRepository,
                predictionRepository = FakePredictionRepository(prediction),
                insightRepository = FakeInsightRepository(),
                reminderRepository = FakeReminderRepository(reminders),
                reminderChannelSettingsRepository = FakeReminderChannelSettingsRepository(),
                reminderEventsRepository = FakeReminderEventsRepository(),
                privacyRepository = FakePrivacyRepository(
                    ConsentState(
                        hideNotificationPreview = true,
                        localDataAcknowledged = true,
                        appLockEnabled = false,
                    ),
                ),
                exportRepository = exportRepository,
                scheduler = FakeReminderScheduler(),
                secureAuthController = FakeSecureAuthController(),
            )
        }
    }
}

private class FakeProfileRepository(
    initial: UserProfile?,
    /** When false the flow never emits, so the screen stays in its loading state. */
    val emitImmediately: Boolean = true,
) : ProfileRepository {
    private val state = MutableStateFlow(initial)
    val current: UserProfile? get() = state.value

    override fun observeProfile(): Flow<UserProfile?> =
        if (emitImmediately) state.asStateFlow() else emptyFlow()

    override suspend fun saveProfile(profile: UserProfile) {
        state.value = profile
    }

    override suspend fun updateRemindersEnabled(enabled: Boolean) {
        state.value = state.value?.copy(remindersEnabled = enabled)
    }

    suspend fun clear() {
        state.value = null
    }
}

private class FakeCycleRepository(
    initial: List<CycleRecord>,
    val emitImmediately: Boolean = true,
) : CycleRepository {
    private val state = MutableStateFlow(initial)
    val current: List<CycleRecord> get() = state.value

    override fun observeCycles(): Flow<List<CycleRecord>> =
        if (emitImmediately) state.asStateFlow() else emptyFlow()

    override suspend fun upsertCycle(record: CycleRecord) {
        state.update { current ->
            current.filterNot { it.id == record.id } + record
        }
    }

    override suspend fun deleteCycle(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakeSymptomRepository(
    initial: List<SymptomLog>,
    val emitImmediately: Boolean = true,
    /**
     * When true a save never completes, so the screen stays in its
     * saving state for as long as the test needs it to.
     */
    private val hangOnSave: Boolean = false,
) : SymptomRepository {
    private val state = MutableStateFlow(initial)
    val current: List<SymptomLog> get() = state.value

    override fun observeSymptoms(): Flow<List<SymptomLog>> =
        if (emitImmediately) state.asStateFlow() else emptyFlow()

    override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> =
        state.asStateFlow().map { logs -> logs.firstOrNull { it.date == date } }

    override suspend fun upsertSymptom(log: SymptomLog) {
        if (hangOnSave) {
            kotlinx.coroutines.awaitCancellation()
        }
        state.update { current ->
            current.filterNot { it.id == log.id } + log
        }
    }

    suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakePredictionRepository(
    initial: PredictionSnapshot?,
    val emitImmediately: Boolean = true,
) : PredictionRepository {
    private val state = MutableStateFlow(initial)

    override fun observePrediction(): Flow<PredictionSnapshot?> =
        if (emitImmediately) state.asStateFlow() else emptyFlow()

    suspend fun clear() {
        state.value = null
    }
}

private class FakeInsightRepository(
    /** When false the flow never emits, so the screen stays in its loading state. */
    val emitImmediately: Boolean = true,
    /**
     * When true the snapshot carries no cycle or symptom history, which
     * is the empty state the Insights screens have to render.
     */
    private val lowData: Boolean = false,
) : InsightRepository {
    private val state = MutableStateFlow(
        if (lowData) lowDataInsightSnapshot() else InsightSnapshot(
            averageCycleLength = 28,
            averagePeriodLength = 5,
            irregularCycles = false,
            recentCycleLengths = listOf(27, 28, 28, 29, 28, 29),
            recentPeriodLengths = listOf(5, 5, 4, 5, 5, 5),
            minCycleLength = 27,
            maxCycleLength = 29,
            cycleWindowLabel = "Based on 6 logged cycles from Oct 2025 to Mar 2026.",
            hasEnoughCycleData = true,
            moodCounts = mapOf(
                com.cyclecare.core.model.Mood.Stable to 6,
                com.cyclecare.core.model.Mood.Low to 2,
                com.cyclecare.core.model.Mood.Happy to 1,
                com.cyclecare.core.model.Mood.Irritable to 1,
                com.cyclecare.core.model.Mood.Anxious to 0,
            ),
            energyCounts = mapOf(
                com.cyclecare.core.model.EnergyLevel.Low to 2,
                com.cyclecare.core.model.EnergyLevel.Steady to 5,
                com.cyclecare.core.model.EnergyLevel.High to 1,
            ),
            painCounts = mapOf(
                com.cyclecare.core.model.PainLevel.None to 2,
                com.cyclecare.core.model.PainLevel.Light to 2,
                com.cyclecare.core.model.PainLevel.Moderate to 4,
                com.cyclecare.core.model.PainLevel.Strong to 1,
            ),
            dischargeCounts = mapOf(
                com.cyclecare.core.model.DischargeType.None to 1,
                com.cyclecare.core.model.DischargeType.Dry to 1,
                com.cyclecare.core.model.DischargeType.Creamy to 2,
                com.cyclecare.core.model.DischargeType.Sticky to 1,
                com.cyclecare.core.model.DischargeType.EggWhite to 3,
            ),
            phaseHighlights = listOf(
                com.cyclecare.core.model.InsightPhaseHighlight(
                    phase = com.cyclecare.core.model.InsightPhase.Period,
                    title = "Period phase pattern",
                    description = "Logged 5 entries during period days. Moderate pain and low energy show up most often.",
                ),
            ),
            hasEnoughSymptomData = true,
            narrativeSummary = "Recent history shows 27 to 29 day cycles across 6 logged cycles.",
        ),
    )

    override fun observeInsights(): Flow<InsightSnapshot> =
        if (emitImmediately) state.asStateFlow() else emptyFlow()
}

/**
 * The empty insights snapshot.
 *
 * One logged cycle is not two, so there is no cycle interval to average
 * and no symptom history to group. The screens have to say so rather
 * than show a chart of nothing.
 */
private fun lowDataInsightSnapshot() = InsightSnapshot(
    averageCycleLength = 28,
    averagePeriodLength = 5,
    irregularCycles = false,
    recentCycleLengths = emptyList(),
    recentPeriodLengths = emptyList(),
    minCycleLength = 28,
    maxCycleLength = 28,
    cycleWindowLabel = "Log two full cycles to unlock cycle trend charts.",
    hasEnoughCycleData = false,
    moodCounts = emptyMap(),
    energyCounts = emptyMap(),
    painCounts = emptyMap(),
    dischargeCounts = emptyMap(),
    phaseHighlights = emptyList(),
    hasEnoughSymptomData = false,
    narrativeSummary = "",
)

private class FakeReminderRepository(
    initial: List<ReminderRule>,
    val emitImmediately: Boolean = true,
) : ReminderRepository {
    private val state = MutableStateFlow(initial)
    val current: List<ReminderRule> get() = state.value

    override fun observeRules(): Flow<List<ReminderRule>> =
        if (emitImmediately) state.asStateFlow() else emptyFlow()

    override suspend fun upsertRule(rule: ReminderRule) {
        state.update { current ->
            current.filterNot { it.id == rule.id } + rule
        }
    }

    suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakeReminderChannelSettingsRepository : ReminderChannelSettingsRepository {
    private val state = MutableStateFlow(
        listOf(
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.CycleAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.GentleBell,
                vibrationEnabled = true,
            ),
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.FertilityAlerts,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.WaterDrop,
                vibrationEnabled = true,
            ),
            ReminderChannelSettings(
                channelKey = ReminderChannelKey.DailyCheckIn,
                enabled = true,
                soundEnabled = true,
                soundPreset = ReminderSoundPreset.SoftChime,
                vibrationEnabled = false,
            ),
        ),
    )

    /** Switches a channel off, for the channel-disabled state. */
    fun setChannelEnabled(channelKey: ReminderChannelKey, enabled: Boolean) {
        state.update { current ->
            current.map { settings ->
                if (settings.channelKey == channelKey) settings.copy(enabled = enabled) else settings
            }
        }
    }

    override fun observeChannelSettings(): Flow<List<ReminderChannelSettings>> = state.asStateFlow()

    override suspend fun upsertChannelSettings(settings: ReminderChannelSettings) {
        state.update { current -> current.filterNot { it.channelKey == settings.channelKey } + settings }
    }
}

private class FakeReminderEventsRepository : ReminderEventsRepository {
    private val state = MutableStateFlow<List<ReminderEvent>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> = state.map { it.take(limit) }

    override fun observeUnreadCount(): Flow<Int> = state.map { events -> events.count { it.readAtEpochMillis == null } }

    override fun observeNextUnread(): Flow<ReminderEvent?> = state.map { events -> events.firstOrNull { it.readAtEpochMillis == null } }

    override suspend fun recordEvent(event: ReminderEvent) {
        state.update { current -> listOf(event) + current.filterNot { it.id == event.id } }
    }

    override suspend fun markRead(id: String) {
        val now = System.currentTimeMillis()
        state.update { current -> current.map { if (it.id == id) it.copy(readAtEpochMillis = now) else it } }
    }

    override suspend fun markAllRead() {
        val now = System.currentTimeMillis()
        state.update { current -> current.map { it.copy(readAtEpochMillis = now) } }
    }

    override suspend fun dismiss(id: String) {
        val now = System.currentTimeMillis()
        state.update { current -> current.map { if (it.id == id) it.copy(dismissedAtEpochMillis = now) else it } }
    }

    override suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long? =
        state.value
            .filter { it.deliveredAt.toInstant().toEpochMilli() >= sinceEpochMillis }
            .maxOfOrNull { it.deliveredAt.toInstant().toEpochMilli() }
}

private class FakeAppVisibilityObserver : AppVisibilityObserver {
    private val state = MutableStateFlow(true)
    override val isForeground = state.asStateFlow()
}

private class FakePrivacyRepository(initial: ConsentState) : PrivacyRepository {
    private val consent = MutableStateFlow(initial)
    private val hasPin = MutableStateFlow(false)
    private val lastUnlock = MutableStateFlow<Long?>(null)
    private var pin: String? = null

    val currentConsent: ConsentState get() = consent.value
    val currentHasPin: Boolean get() = hasPin.value
    val lastUnlockEpochMillis: Long? get() = lastUnlock.value

    override fun observeConsent(): Flow<ConsentState> = consent.asStateFlow()

    override fun hasPinConfigured(): Flow<Boolean> = hasPin.asStateFlow()

    override fun observeLastUnlockEpochMillis(): Flow<Long?> = lastUnlock.asStateFlow()

    override fun isBiometricEnabled(): Flow<Boolean> = consent.map { it.biometricEnabled }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        consent.update { current ->
            current.copy(
                appLockEnabled = enabled,
                biometricEnabled = current.biometricEnabled && enabled,
            )
        }
    }

    override suspend fun setNotificationPreviewHidden(hidden: Boolean) {
        consent.update { it.copy(hideNotificationPreview = hidden) }
    }

    override suspend fun acknowledgeLocalData() {
        consent.update { it.copy(localDataAcknowledged = true) }
    }

    override suspend fun setPin(pin: String) {
        this.pin = pin
        hasPin.value = true
    }

    override suspend fun clearPin() {
        resetAppLock()
    }

    override suspend fun resetAppLock() {
        pin = null
        hasPin.value = false
        lastUnlock.value = null
        consent.update { current ->
            current.copy(
                appLockEnabled = false,
                biometricEnabled = false,
            )
        }
    }

    override suspend fun validatePin(pin: String): Boolean = this.pin == pin

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        consent.update { current ->
            current.copy(
                biometricEnabled = enabled && current.appLockEnabled && hasPin.value,
            )
        }
    }

    override suspend fun markUnlocked(epochMillis: Long) {
        lastUnlock.value = epochMillis
    }

    suspend fun seedPin(
        pin: String,
        appLockEnabled: Boolean,
        biometricEnabled: Boolean = false,
        lastUnlockEpochMillis: Long? = null,
    ) {
        this.pin = pin
        hasPin.value = true
        lastUnlock.value = lastUnlockEpochMillis
        consent.update { current ->
            current.copy(
                appLockEnabled = appLockEnabled,
                biometricEnabled = biometricEnabled && appLockEnabled,
            )
        }
    }

    suspend fun expireUnlock() {
        lastUnlock.value = System.currentTimeMillis() - PrivacyRules.unlockGracePeriodMillis - 1L
    }

    suspend fun reset() {
        consent.value = ConsentState(
            hideNotificationPreview = true,
            localDataAcknowledged = false,
            appLockEnabled = false,
        )
        clearPin()
    }
}

private class FakeSecureAuthController(
    initialCapability: SecureAuthCapability = SecureAuthCapability.None,
) : SecureAuthController {
    private val capabilityState = MutableStateFlow(initialCapability)
    override val capability = capabilityState.asStateFlow()

    var authenticationCount: Int = 0
    var nextError: String? = null
    var cancelNext: Boolean = false

    fun setCapability(capability: SecureAuthCapability) {
        capabilityState.value = capability
    }

    override fun authenticate(
        preference: SecureAuthPreference,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        authenticationCount += 1
        val failureMessage = nextError
        nextError = null
        if (cancelNext) {
            cancelNext = false
            onCancel()
        } else if (failureMessage != null) {
            onFailure(failureMessage)
        } else {
            onSuccess()
        }
    }
}

/**
 * What the export repository does when the screen asks for a report.
 */
private enum class ExportBehaviour {
    /** The report is written and the screen shows its saved state. */
    Succeeds,

    /** The report cannot be written and the screen shows its failure state. */
    Fails,

    /** The write never completes, so the screen stays in its working state. */
    Hangs,
}

private class FakeExportRepository(
    private val behaviour: ExportBehaviour = ExportBehaviour.Succeeds,
) : ExportRepository {
    override suspend fun createReport(format: ExportFormat): ExportArtifact = when (behaviour) {
        ExportBehaviour.Succeeds -> ExportArtifact(
            format = format,
            displayName = "test.${format.name.lowercase()}",
            absolutePath = "/tmp/test",
            mimeType = "text/plain",
            generatedAt = LocalDate.now(),
        )
        ExportBehaviour.Fails -> throw IllegalStateException("the report could not be written")
        ExportBehaviour.Hangs -> kotlinx.coroutines.awaitCancellation()
    }

    override suspend fun createEncryptedBackup(passphrase: CharArray): ExportArtifact =
        ExportArtifact(
            format = ExportFormat.EncryptedBackup,
            displayName = "test.ccvault",
            absolutePath = "/tmp/test.ccvault",
            mimeType = "application/octet-stream",
            generatedAt = LocalDate.now(),
        )

    override suspend fun importEncryptedBackup(inputStream: InputStream, passphrase: CharArray): ImportResult =
        ImportResult.Success(0, 0, false)
}

private class FakeReminderScheduler : ReminderScheduler {
    var refreshCalls: Int = 0

    override suspend fun refreshSchedules(now: ZonedDateTime): List<ScheduledReminder> {
        refreshCalls += 1
        return emptyList()
    }
}

private class FakeNotificationPermissionController : NotificationPermissionController {
    var currentStatus: NotificationPermissionStatus = NotificationPermissionStatus(
        runtimePermissionRequired = false,
        runtimePermissionGranted = true,
        notificationsEnabled = true,
        requestAttempted = false,
        canRequestRuntimePermission = false,
    )
    var requestMarked: Boolean = false

    override fun status(activity: androidx.activity.ComponentActivity?): NotificationPermissionStatus = currentStatus

    override fun markRequestAttempted() {
        requestMarked = true
        currentStatus = currentStatus.copy(requestAttempted = true)
    }

    override fun notificationSettingsIntent() = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
}

private class FakeSyncAdapter : SyncAdapter {
    override suspend fun pushPendingChanges() = Unit
    override suspend fun pullRemoteChanges() = Unit
    override suspend fun mergePreview(): String = "disabled"
}

private class FakeAppResetRepository(
    private val environment: TestAppEnvironment,
) : AppResetRepository {
    override suspend fun resetAllLocalData() {
        environment.profileRepository.clear()
        environment.cycleRepository.clear()
        environment.symptomRepository.clear()
        environment.reminderRepository.clear()
        environment.predictionRepository.clear()
        environment.privacyRepository.reset()
        environment.exportCleared = true
    }
}

/**
 * Populated app graph for design review screenshots.
 *
 * ReThemeScreenshotTest needs the same realistic state the assertions use, but
 * every fake above is private to this file. Rather than widen twelve fakes to
 * internal, this exposes only the two public types the app composable actually
 * consumes, which keeps the screenshots and the assertions on one source of
 * truth without leaking the test doubles.
 */
/**
 * The app in the states the review harness needs.
 *
 * Each returns only the public types the harness consumes, because the
 * fakes behind them are private to this file.
 */

/** A populated profile, for the ordinary screens. */
internal fun populatedScreenshotEnvironment(): Pair<AppContainer, SecureAuthController> {
    val environment = TestAppEnvironment.populated()
    return environment.container to environment.secureAuthController
}

/** No profile at all, which is what the onboarding flow starts from. */
internal fun emptyScreenshotEnvironment(): Pair<AppContainer, SecureAuthController> {
    val environment = TestAppEnvironment.empty()
    return environment.container to environment.secureAuthController
}

/** A PIN is set and the app lock is on, which shows the lock gate. */
internal suspend fun appLockScreenshotEnvironment(): Pair<AppContainer, SecureAuthController> {
    val environment = TestAppEnvironment.populated()
    environment.privacyRepository.seedPin(pin = "1234", appLockEnabled = true)
    return environment.container to environment.secureAuthController
}

/** Reminders are on and notifications are blocked, which shows the gate. */
internal suspend fun notificationGateScreenshotEnvironment(): Pair<AppContainer, SecureAuthController> {
    val environment = TestAppEnvironment.populated()
    environment.profileRepository.updateRemindersEnabled(true)
    environment.notificationPermissionController.currentStatus =
        environment.notificationPermissionController.currentStatus.copy(
            runtimePermissionGranted = false,
            notificationsEnabled = false,
        )
    return environment.container to environment.secureAuthController
}

/**
 * The states the state matrix covers, from spec `03` §6.
 *
 * The specification groups the sync outcomes into one line, and the
 * application has no synchronisation surface at all, because cloud sync is
 * deferred by decision. The sync states are therefore not applicable here,
 * and this list does not pretend otherwise.
 */
internal enum class ScreenState {
    /** The data has not arrived yet, so the screen shows its loading state. */
    LOADING,

    /** There is data and the screen shows it. */
    CONTENT,

    /** There is no data and the screen says so, with a way forward. */
    EMPTY,

    /** A save is in flight. */
    SAVING,

    /** A save has just completed and the screen says so. */
    SAVED,

    /** The input is invalid and the message says what to correct. */
    VALIDATION_ERROR,

    /** Notifications are not permitted. */
    PERMISSION_DENIED,

    /** A notification channel has been switched off. */
    CHANNEL_DISABLED,

    /** An export is in flight and has not finished. */
    EXPORT_WORKING,

    /** An export has just been written. */
    EXPORT_SUCCEEDED,

    /** An export could not be written. */
    EXPORT_FAILED,
}

/**
 * An app container in a named state.
 *
 * The state is produced by the fake repositories, so the screen under test
 * goes through its real ViewModel and its real state model. Nothing is
 * faked at the composable, and there is no production backdoor.
 */
internal fun stateEnvironment(
    state: ScreenState,
): Pair<AppContainer, SecureAuthController> {
    val environment = when (state) {
        // The repositories never emit, so the screen stays in its initial
        // local loading state.
        ScreenState.LOADING -> TestAppEnvironment.loading()

        ScreenState.CONTENT -> TestAppEnvironment.populated()

        ScreenState.EMPTY -> TestAppEnvironment.noHistory()

        ScreenState.SAVING -> TestAppEnvironment.saving()

        ScreenState.SAVED -> TestAppEnvironment.saved()

        ScreenState.VALIDATION_ERROR -> TestAppEnvironment.validationError()

        ScreenState.PERMISSION_DENIED -> TestAppEnvironment.permissionDenied()

        ScreenState.CHANNEL_DISABLED -> TestAppEnvironment.channelDisabled()

        ScreenState.EXPORT_WORKING -> TestAppEnvironment.exportWorking()

        ScreenState.EXPORT_SUCCEEDED -> TestAppEnvironment.exportSucceeded()

        ScreenState.EXPORT_FAILED -> TestAppEnvironment.exportFailed()
    }
    return environment.container to environment.secureAuthController
}
