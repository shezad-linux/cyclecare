package com.cyclecare.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cyclecare.feature.privacy.SecureAuthController
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
internal fun NotificationPermissionRequiredGate(
    actionLabel: String,
    onResolveNotificationAccess: () -> Unit,
) {
    // No back handler here on purpose.
    //
    // Spec 15 §1.3.4: the notification-permission gate "must not trap
    // back/switch-access users". A handler that swallowed back with an
    // empty body left someone who pressed back, or who used a switch
    // device to send the back gesture, with no way out of the app at all.
    //
    // Letting the system back through is the real exit: the activity
    // closes, and the gate is still waiting when the user comes back,
    // because reminders are enabled and notifications are not permitted.
    // The alternative, letting the user past the gate, would mean
    // reminders silently never arrive.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(NotificationPermissionTags.REQUIRED_GATE),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(CycleCareSpace.space24),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.notifications_required),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = CycleCareSpace.space16),
            )
            Text(
                text = stringResource(R.string.notifications_required_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CycleCareSpace.space8),
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CycleCareSpace.space24)
                    .testTag(NotificationPermissionTags.REQUIRED_ACTION),
                onClick = onResolveNotificationAccess,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun MainShell(
    container: AppContainer,
    notificationsCanPost: Boolean,
    notificationRecoveryActionLabel: String?,
    secureAuthController: SecureAuthController,
    canRequestAds: Boolean,
    interstitialAdManager: InterstitialAdManager,
    isAdPrivacyOptionsRequired: Boolean,
    onOpenAdPrivacyOptions: () -> Unit,
    openPrivacyOnNextFrame: Boolean,
    onPrivacyNavigationHandled: () -> Unit,
    onResolveNotificationAccess: () -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findComponentActivity()
    val interstitialCoordinator = remember(container, interstitialAdManager, scope) {
        InterstitialCoordinator(
            adManager = interstitialAdManager,
            statsRepository = container.interstitialStatsRepository,
            reminderEventsRepository = container.reminderEventsRepository,
            profileRepository = container.profileRepository,
            draftTracker = container.draftTracker,
            timeProvider = container.timeProvider,
            scope = scope,
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedTopLevelRoute = currentRoute.toSelectedTopLevelRoute()
    val unreadReminderCount by container.reminderEventsRepository.observeUnreadCount().collectAsStateWithLifecycle(initialValue = 0)
    val nextUnreadReminder by container.reminderEventsRepository.observeNextUnread().collectAsStateWithLifecycle(initialValue = null)
    val pendingReminderRequest by container.reminderNavigationCoordinator.requests.collectAsStateWithLifecycle(initialValue = null)
    var calendarSelectedDateOverrideEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val showBottomBar = selectedTopLevelRoute != null || currentRoute == null

    LaunchedEffect(openPrivacyOnNextFrame, currentRoute) {
        if (!openPrivacyOnNextFrame) return@LaunchedEffect
        if (currentRoute != "privacy") {
            navController.navigate("privacy")
        }
        onPrivacyNavigationHandled()
    }

    BackHandler(enabled = currentRoute != null && currentRoute in topLevelRoutes && currentRoute != "home") {
        navController.navigateToTopLevel("home")
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            container.appLogger.debug(
                tag = "CycleCareNav",
                event = "destination_changed",
                attributes = mapOf(
                    "route" to destination.route.orEmpty(),
                    "previousRoute" to controller.previousBackStackEntry?.destination?.route.orEmpty(),
                ),
            )
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(pendingReminderRequest) {
        val request = pendingReminderRequest ?: return@LaunchedEffect
        when (request.target) {
            com.cyclecare.core.model.ReminderDeepLinkTarget.Home -> navController.navigateToTopLevel("home")
            com.cyclecare.core.model.ReminderDeepLinkTarget.Calendar -> {
                calendarSelectedDateOverrideEpochDay = request.targetEpochDay
                navController.navigateToTopLevel("calendar")
            }
            com.cyclecare.core.model.ReminderDeepLinkTarget.Log -> navController.navigateToTopLevel("log")
            com.cyclecare.core.model.ReminderDeepLinkTarget.ReminderCenter -> navController.navigate("reminders")
        }
        request.eventId?.let { container.reminderEventsRepository.markRead(it) }
        container.reminderNavigationCoordinator.clear()
    }

    // The one-time nudge towards Settings. Spec 15 §1.3.4. It is shown
    // once, after onboarding, and it is a dialog rather than a badge.
    FirstRunNudge(
        repository = container.firstRunNudgeRepository,
        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // One shared app bar for the four tabs. Settings used to be
            // a fifth tab; it now lives here, where a destination that is
            // visited occasionally belongs. Detail routes keep their own
            // bars, so this is shown only on top-level routes.
            if (selectedTopLevelRoute != null) {
                CycleCareTopBar(
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                CycleCareBottomNavigation(
                    canRequestAds = canRequestAds,
                    selectedTopLevelRoute = selectedTopLevelRoute,
                    onNavigate = { route ->
                        val currentActivity = activity
                        if (canRequestAds && currentActivity != null) {
                            // Spec 15 §11.3. The gate decides; if a condition
                            // fails the interstitial is dropped and the
                            // navigation goes ahead immediately rather than
                            // the ad being deferred to a later screen.
                            scope.launch {
                                interstitialCoordinator.requestInterstitial(
                                    activity = currentActivity,
                                    // The raw route, not the selected tab:
                                    // the forbidden list contains detail
                                    // routes that map onto an allowed tab.
                                    currentRoute = currentRoute,
                                    adsConsentGranted = canRequestAds,
                                    onFinished = { navController.navigateToTopLevel(route) },
                                )
                            }
                        } else {
                            navController.navigateToTopLevel(route)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost(
                navController = navController,
                container = container,
                innerPadding = innerPadding,
                notificationsCanPost = notificationsCanPost,
                notificationRecoveryActionLabel = notificationRecoveryActionLabel,
                secureAuthController = secureAuthController,
                canRequestAds = canRequestAds,
                isAdPrivacyOptionsRequired = isAdPrivacyOptionsRequired,
                onOpenAdPrivacyOptions = onOpenAdPrivacyOptions,
                onResolveNotificationAccess = onResolveNotificationAccess,
                unreadReminderCount = unreadReminderCount,
                onOpenReminderCenter = { navController.navigate("reminders") },
                calendarSelectedDateOverride = calendarSelectedDateOverrideEpochDay?.let(LocalDate::ofEpochDay),
                onCalendarSelectedDateOverrideConsumed = { calendarSelectedDateOverrideEpochDay = null },
                onOpenReminderEvent = { event ->
                    when (event.deepLinkTarget) {
                        com.cyclecare.core.model.ReminderDeepLinkTarget.Home -> navController.navigateToTopLevel("home")
                        com.cyclecare.core.model.ReminderDeepLinkTarget.Calendar -> {
                            calendarSelectedDateOverrideEpochDay = event.targetEpochDay
                            navController.navigateToTopLevel("calendar")
                        }
                        com.cyclecare.core.model.ReminderDeepLinkTarget.Log -> navController.navigateToTopLevel("log")
                        com.cyclecare.core.model.ReminderDeepLinkTarget.ReminderCenter -> navController.navigate("reminders")
                    }
                },
            )
            ReminderBannerHost(
                event = nextUnreadReminder?.takeUnless { currentRoute == "reminders" },
                onOpen = { event ->
                    scope.launch { container.reminderEventsRepository.markRead(event.id) }
                    when (event.deepLinkTarget) {
                        com.cyclecare.core.model.ReminderDeepLinkTarget.Home -> navController.navigateToTopLevel("home")
                        com.cyclecare.core.model.ReminderDeepLinkTarget.Calendar -> {
                            calendarSelectedDateOverrideEpochDay = event.targetEpochDay
                            navController.navigateToTopLevel("calendar")
                        }
                        com.cyclecare.core.model.ReminderDeepLinkTarget.Log -> navController.navigateToTopLevel("log")
                        com.cyclecare.core.model.ReminderDeepLinkTarget.ReminderCenter -> navController.navigate("reminders")
                    }
                },
                onDismiss = { event ->
                    scope.launch { container.reminderEventsRepository.dismiss(event.id) }
                },
            )
        }
    }
}
