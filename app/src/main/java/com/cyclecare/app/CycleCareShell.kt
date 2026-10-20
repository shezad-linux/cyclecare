package com.cyclecare.app

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cyclecare.feature.calendar.CalendarRoute
import com.cyclecare.feature.calendar.CalendarViewModelFactory
import com.cyclecare.feature.export.ExportRoute
import com.cyclecare.feature.export.ExportViewModelFactory
import com.cyclecare.feature.home.HomeCycleStatusRoute
import com.cyclecare.feature.home.HomeFertilityDetailRoute
import com.cyclecare.feature.home.HomeRoute
import com.cyclecare.feature.home.HomeViewModelFactory
import com.cyclecare.feature.insights.InsightsCycleTrendsRoute
import com.cyclecare.feature.insights.InsightsRoute
import com.cyclecare.feature.insights.InsightsSymptomPatternsRoute
import com.cyclecare.feature.insights.InsightsViewModelFactory
import com.cyclecare.feature.logging.LoggingRoute
import com.cyclecare.feature.logging.LoggingViewModelFactory
import com.cyclecare.feature.privacy.PrivacyRoute
import com.cyclecare.feature.privacy.SecureAuthController
import com.cyclecare.feature.privacy.PrivacyViewModelFactory
import com.cyclecare.feature.settings.MigrationNotesRoute
import com.cyclecare.feature.settings.MigrationNotesViewModelFactory
import com.cyclecare.feature.settings.SettingsRoute
import com.cyclecare.feature.settings.SettingsViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                                    // The gate reads persisted state, so
                                    // this callback can arrive on whichever
                                    // dispatcher that read resumed on. A
                                    // navigation has to be driven from the
                                    // main thread, and the scope's
                                    // dispatcher is the main one, so the
                                    // navigation is posted back onto it.
                                    // Without this, tapping a tab can
                                    // throw "setCurrentState must be
                                    // called on the main thread" from
                                    // inside Navigation.
                                    onFinished = {
                                        scope.launch {
                                            // Explicitly the Android main
                                            // thread. The composition's
                                            // own dispatcher is not
                                            // guaranteed to be it, and
                                            // Navigation requires it.
                                            withContext(Dispatchers.Main.immediate) {
                                                navController.navigateToTopLevel(route)
                                            }
                                        }
                                    },
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

@Composable
internal fun CycleCareTopBar(
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = CycleCareSpace.space4, vertical = CycleCareSpace.space2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("app_bar_settings"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                )
            }
        }
    }
}

@Composable
internal fun CycleCareBottomNavigation(
    canRequestAds: Boolean,
    selectedTopLevelRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val largeFontScale = LocalDensity.current.fontScale > 1.2f
    Column {
        // Spec 15 §11.2: Today and Insights only.
        if (canRequestAds && routeAllowsBanner(selectedTopLevelRoute)) {
            AdBanner(canRequestAds = true)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    thickness = 1.dp,
                )
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedTopLevelRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            modifier = Modifier.testTag(navTag(destination.route)),
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.testTag(navIconTag(destination.route)),
                                )
                            },
                            label = {
                                // A four-tab bar gives each label a quarter
                                // of the width, and at a large font scale a
                                // single word like "Calendar" no longer
                                // fits, so it broke mid-word into "Calen
                                // dar". That reads as a rendering fault
                                // rather than as a label.
                                //
                                // The label drops to the smaller role at
                                // large font scales, the way the metric grid
                                // stacks, and is held to a single line so
                                // it can never break mid-word again. The
                                // full text stays in the semantics, so a
                                // screen reader still announces the whole
                                // label.
                                Text(
                                    text = stringResource(destination.labelRes),
                                    style = if (largeFontScale) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        LocalTextStyle.current
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    innerPadding: PaddingValues,
    notificationsCanPost: Boolean,
    notificationRecoveryActionLabel: String?,
    secureAuthController: SecureAuthController,
    canRequestAds: Boolean,
    isAdPrivacyOptionsRequired: Boolean,
    onOpenAdPrivacyOptions: () -> Unit,
    onResolveNotificationAccess: () -> Unit,
    unreadReminderCount: Int,
    onOpenReminderCenter: () -> Unit,
    calendarSelectedDateOverride: LocalDate?,
    onCalendarSelectedDateOverrideConsumed: () -> Unit,
    onOpenReminderEvent: (com.cyclecare.core.model.ReminderEvent) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding),
    ) {
        composable("home") {
            HomeRoute(
                factory = HomeViewModelFactory(
                    profileRepository = container.profileRepository,
                    cycleRepository = container.cycleRepository,
                    predictionRepository = container.predictionRepository,
                    insightRepository = container.insightRepository,
                    reminderRepository = container.reminderRepository,
                    reminderScheduler = container.reminderScheduler,
                ),
                onOpenLog = { navController.navigateToTopLevel("log") },
                onOpenCalendar = { navController.navigateToTopLevel("calendar") },
                onOpenInsights = { navController.navigateToTopLevel("insights") },
                onOpenCycleStatus = { navController.navigate("home/cycle-status") },
                onOpenFertilityDetail = { navController.navigate("home/fertility-detail") },
                reminderUnreadCount = unreadReminderCount,
                onOpenReminderCenter = onOpenReminderCenter,
                inlineAd = {
                    NativeAdvancedAd(canRequestAds = canRequestAds)
                },
            )
        }
        composable("home/cycle-status") {
            HomeCycleStatusRoute(
                factory = HomeViewModelFactory(
                    profileRepository = container.profileRepository,
                    cycleRepository = container.cycleRepository,
                    predictionRepository = container.predictionRepository,
                    insightRepository = container.insightRepository,
                    reminderRepository = container.reminderRepository,
                    reminderScheduler = container.reminderScheduler,
                ),
                onBackToDashboard = { navController.popBackStack() },
                onOpenCalendar = { navController.navigateToTopLevel("calendar") },
            )
        }
        composable("home/fertility-detail") {
            HomeFertilityDetailRoute(
                factory = HomeViewModelFactory(
                    profileRepository = container.profileRepository,
                    cycleRepository = container.cycleRepository,
                    predictionRepository = container.predictionRepository,
                    insightRepository = container.insightRepository,
                    reminderRepository = container.reminderRepository,
                    reminderScheduler = container.reminderScheduler,
                ),
                onBackToDashboard = { navController.popBackStack() },
                onOpenCalendar = { navController.navigateToTopLevel("calendar") },
                onOpenInsights = { navController.navigateToTopLevel("insights") },
            )
        }
        composable("calendar") {
            CalendarRoute(
                factory = CalendarViewModelFactory(
                    cycleRepository = container.cycleRepository,
                    predictionRepository = container.predictionRepository,
                    reminderScheduler = container.reminderScheduler,
                ),
                externalSelectedDate = calendarSelectedDateOverride,
                onExternalSelectedDateConsumed = onCalendarSelectedDateOverrideConsumed,
            )
        }
        composable("log") {
            LoggingRoute(
                factory = LoggingViewModelFactory(
                    symptomRepository = container.symptomRepository,
                    profileRepository = container.profileRepository,
                    cycleRepository = container.cycleRepository,
                    predictionRepository = container.predictionRepository,
                    draftTracker = container.draftTracker,
                ),
            )
        }
        composable("insights") {
            InsightsRoute(
                factory = InsightsViewModelFactory(
                    insightRepository = container.insightRepository,
                ),
                onOpenCycleTrends = { navController.navigate("insights/cycle-trends") },
                onOpenSymptomPatterns = { navController.navigate("insights/symptom-patterns") },
            )
        }
        composable("insights/cycle-trends") {
            InsightsCycleTrendsRoute(
                factory = InsightsViewModelFactory(
                    insightRepository = container.insightRepository,
                ),
                onBackToOverview = { navController.popBackStack() },
            )
        }
        composable("insights/symptom-patterns") {
            InsightsSymptomPatternsRoute(
                factory = InsightsViewModelFactory(
                    insightRepository = container.insightRepository,
                ),
                onBackToOverview = { navController.popBackStack() },
            )
        }
        composable("settings") {
            val context = LocalContext.current
            SettingsRoute(
                factory = SettingsViewModelFactory(
                    profileRepository = container.profileRepository,
                    privacyRepository = container.privacyRepository,
                    reminderRepository = container.reminderRepository,
                    reminderChannelSettingsRepository = container.reminderChannelSettingsRepository,
                    appResetRepository = container.appResetRepository,
                    reminderScheduler = container.reminderScheduler,
                    migrationDivergenceRepository = container.migrationDivergenceRepository,
                ),
                onOpenPrivacy = { navController.navigate("privacy") },
                onOpenExport = { navController.navigate("export") },
                onOpenReminderCenter = onOpenReminderCenter,
                onOpenMigrationRecord = { navController.navigate("migration-notes") },
                notificationsCanPost = notificationsCanPost,
                notificationRecoveryActionLabel = notificationRecoveryActionLabel,
                unreadReminderCount = unreadReminderCount,
                onPreviewReminderSound = { preset ->
                    ReminderChannels.playPreview(context, preset, container.appLogger)
                },
                showAdPrivacyOptions = isAdPrivacyOptionsRequired,
                onOpenAdPrivacyOptions = onOpenAdPrivacyOptions,
                onResolveNotificationAccess = onResolveNotificationAccess,
            )
        }
        composable("reminders") {
            ReminderCenterRoute(
                factory = ReminderCenterViewModelFactory(container.reminderEventsRepository),
                onOpenEvent = onOpenReminderEvent,
            )
        }
        composable("privacy") {
            PrivacyRoute(
                factory = PrivacyViewModelFactory(container.privacyRepository),
                secureAuthController = secureAuthController,
            )
        }
        composable("export") {
            ExportRoute(factory = ExportViewModelFactory(container.exportRepository))
        }
        composable("migration-notes") {
            MigrationNotesRoute(
                factory = MigrationNotesViewModelFactory(container.migrationDivergenceRepository),
            )
        }
    }
}

@Composable
internal fun ReminderBell(
    unreadReminderCount: Int,
) {
    BadgedBox(
        badge = {
            if (unreadReminderCount > 0) {
                Badge {
                    Text(unreadReminderCount.coerceAtMost(99).toString())
                }
            }
        },
    ) {
        Icon(Icons.Rounded.Notifications, contentDescription = null)
    }
}
