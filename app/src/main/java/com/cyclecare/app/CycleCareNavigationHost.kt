package com.cyclecare.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.cyclecare.feature.settings.SettingsRoute
import com.cyclecare.feature.settings.SettingsViewModelFactory
import java.time.LocalDate

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
                ),
                onOpenPrivacy = { navController.navigate("privacy") },
                onOpenExport = { navController.navigate("export") },
                onOpenReminderCenter = onOpenReminderCenter,
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
    }
}

