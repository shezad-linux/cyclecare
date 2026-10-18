package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.ExportRepository
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
     * KNOWN DEFECT, and it is not a recreation problem: this fails on
     * the very first assertion, before any recreation. Tapping a day
     * cell does not select it, so the cell reports "fertile, ovulation,
     * current month" with no "selected" state, and the selected-day
     * panel is not even composed afterwards.
     *
     * Earlier readings suggested the selection was lost only across
     * activity recreation. That was wrong: the original failure was that
     * the March day node was never found, because the calendar opens on
     * the current month. Navigating to March exposed that the click
     * itself never takes effect.
     *
     * The assertion is kept at its correct value deliberately so the
     * defect stays visible rather than being written around.
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
        composeRule.onNodeWithTag("calendar_day_2026-03-23").performClick()
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
        val env = TestAppEnvironment.populated()
        runBlocking {
            env.privacyRepository.seedPin(pin = "1234", appLockEnabled = true)
        }

        renderApp(env)
        composeRule.onNodeWithTag(AppLockTags.OVERLAY).assertIsDisplayed()

        repeat(4) { attempt ->
            enterPin("9999")
            clickPrimaryAction()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(
                    hasText("Incorrect PIN. ${4 - attempt} attempts remaining."),
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }
        enterPin("9999")
        clickPrimaryAction()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Too many failed attempts. Try again in 30 seconds.")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Too many failed attempts. Try again in 30 seconds.").assertIsDisplayed()
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
            activity.setContent {
                CycleCareTheme {
                    CycleCareApp(
                        container = env.container,
                        secureAuthController = env.secureAuthController,
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

        fun populated(): TestAppEnvironment {
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
            val symptoms = listOf(
                SymptomLog(
                    id = "symptom-1",
                    date = LocalDate.of(2026, 3, 16),
                    mood = Mood.Stable,
                    painLevel = PainLevel.Light,
                    energyLevel = EnergyLevel.Steady,
                    dischargeType = DischargeType.Dry,
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
                symptomRepository = FakeSymptomRepository(symptoms),
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
                exportRepository = FakeExportRepository(),
                scheduler = FakeReminderScheduler(),
                secureAuthController = FakeSecureAuthController(),
            )
        }
    }
}

private class FakeProfileRepository(initial: UserProfile?) : ProfileRepository {
    private val state = MutableStateFlow(initial)
    val current: UserProfile? get() = state.value

    override fun observeProfile(): Flow<UserProfile?> = state.asStateFlow()

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

private class FakeCycleRepository(initial: List<CycleRecord>) : CycleRepository {
    private val state = MutableStateFlow(initial)
    val current: List<CycleRecord> get() = state.value

    override fun observeCycles(): Flow<List<CycleRecord>> = state.asStateFlow()

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

private class FakeSymptomRepository(initial: List<SymptomLog>) : SymptomRepository {
    private val state = MutableStateFlow(initial)
    val current: List<SymptomLog> get() = state.value

    override fun observeSymptoms(): Flow<List<SymptomLog>> = state.asStateFlow()

    override fun observeSymptomForDate(date: LocalDate): Flow<SymptomLog?> =
        state.asStateFlow().map { logs -> logs.firstOrNull { it.date == date } }

    override suspend fun upsertSymptom(log: SymptomLog) {
        state.update { current ->
            current.filterNot { it.id == log.id } + log
        }
    }

    suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakePredictionRepository(initial: PredictionSnapshot?) : PredictionRepository {
    private val state = MutableStateFlow(initial)

    override fun observePrediction(): Flow<PredictionSnapshot?> = state.asStateFlow()

    suspend fun clear() {
        state.value = null
    }
}

private class FakeInsightRepository : InsightRepository {
    private val state = MutableStateFlow(
        InsightSnapshot(
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

    override fun observeInsights(): Flow<InsightSnapshot> = state.asStateFlow()
}

private class FakeReminderRepository(initial: List<ReminderRule>) : ReminderRepository {
    private val state = MutableStateFlow(initial)
    val current: List<ReminderRule> get() = state.value

    override fun observeRules(): Flow<List<ReminderRule>> = state.asStateFlow()

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

private class FakeExportRepository : ExportRepository {
    override suspend fun createReport(format: ExportFormat): ExportArtifact =
        ExportArtifact(
            format = format,
            displayName = "test.${format.name.lowercase()}",
            absolutePath = "/tmp/test",
            mimeType = "text/plain",
            generatedAt = LocalDate.now(),
        )
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
