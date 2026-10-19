package com.cyclecare.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cyclecare.core.domain.PrivacyRules
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
import com.cyclecare.feature.onboarding.OnboardingRoute
import com.cyclecare.feature.onboarding.OnboardingViewModelFactory
import com.cyclecare.feature.privacy.PrivacyRoute
import com.cyclecare.feature.privacy.SecureAuthController
import com.cyclecare.feature.privacy.PrivacyViewModelFactory
import com.cyclecare.feature.privacy.rememberPlatformSecureAuthController
import com.cyclecare.feature.settings.SettingsRoute
import com.cyclecare.feature.settings.SettingsViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.launch

internal object NotificationPermissionTags {
    const val REQUIRED_GATE = "notification_permission_required_gate"
    const val REQUIRED_ACTION = "notification_permission_required_action"
}

private fun NotificationPermissionStatus.recoveryLabel(context: Context): String? =
    when (recoveryAction) {
        NotificationPermissionRecoveryAction.None -> null
        NotificationPermissionRecoveryAction.RequestRuntimePermission ->
            context.getString(R.string.allow_notifications)
        NotificationPermissionRecoveryAction.OpenSettings ->
            context.getString(R.string.open_notification_settings)
    }

@Composable
fun CycleCareApp(
    container: AppContainer,
    secureAuthController: SecureAuthController? = null,
    interstitialAdManager: InterstitialAdManager? = null,
) {
    val authController = secureAuthController ?: rememberPlatformSecureAuthController()
    val context = LocalContext.current
    val activity = context.findComponentActivity()
    val adsConsentManager = remember(context.applicationContext, container.appLogger) {
        GoogleMobileAdsConsentManager(
            context = context.applicationContext,
            logger = container.appLogger,
        )
    }
    val profile by container.profileRepository.observeProfile().collectAsStateWithLifecycle(initialValue = null)
    val reminderRules by container.reminderRepository.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val consent by container.privacyRepository.observeConsent().collectAsStateWithLifecycle(
        initialValue = com.cyclecare.core.model.ConsentState(
            hideNotificationPreview = true,
            localDataAcknowledged = false,
            appLockEnabled = false,
            biometricEnabled = false,
        ),
    )
    val hasPinConfigured by container.privacyRepository.hasPinConfigured().collectAsStateWithLifecycle(initialValue = false)
    val lastUnlockEpochMillis by container.privacyRepository.observeLastUnlockEpochMillis().collectAsStateWithLifecycle(initialValue = null)
    val canRequestAds by adsConsentManager.canRequestAds.collectAsStateWithLifecycle()
    val isAdPrivacyOptionsRequired by adsConsentManager.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    val activeInterstitialAdManager = interstitialAdManager ?: remember(context.applicationContext) {
        InterstitialAdManager(context = context.applicationContext)
    }

    LaunchedEffect(canRequestAds, activeInterstitialAdManager) {
        if (canRequestAds) {
            activeInterstitialAdManager.loadAd()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var unlockCheckEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notificationPermissionRefreshTick by remember { mutableIntStateOf(0) }
    val notificationPermissionStatus = remember(notificationPermissionRefreshTick, activity) {
        container.notificationPermissionController.status(activity)
    }
    var pendingPrivacyNavigation by rememberSaveable { mutableStateOf(false) }
    val remindersRequireNotifications = reminderRules.any { it.enabled }
    val notificationAccessRequired = profile != null &&
        remindersRequireNotifications &&
        !notificationPermissionStatus.canPostNotifications

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationPermissionRefreshTick += 1
        scope.launch {
            container.reminderScheduler.refreshSchedules()
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                unlockCheckEpochMillis = System.currentTimeMillis()
                notificationPermissionRefreshTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lastUnlockEpochMillis) {
        val lastUnlock = lastUnlockEpochMillis ?: return@LaunchedEffect
        if (lastUnlock > unlockCheckEpochMillis) {
            unlockCheckEpochMillis = lastUnlock
        }
    }

    val resolveNotificationAccess: () -> Unit = {
        when (notificationPermissionStatus.recoveryAction) {
            NotificationPermissionRecoveryAction.None -> Unit
            NotificationPermissionRecoveryAction.RequestRuntimePermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    container.notificationPermissionController.markRequestAttempted()
                    notificationPermissionRefreshTick += 1
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            NotificationPermissionRecoveryAction.OpenSettings -> {
                runCatching {
                    context.startActivity(container.notificationPermissionController.notificationSettingsIntent())
                }.recoverCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
        }
    }

    val currentLastUnlockEpochMillis = lastUnlockEpochMillis
    val hasUnlockedInCurrentProcess =
        currentLastUnlockEpochMillis != null &&
            currentLastUnlockEpochMillis >= container.processStartEpochMillis

    val shouldShowLock = consent.appLockEnabled &&
        hasPinConfigured &&
        (
            !hasUnlockedInCurrentProcess ||
                !PrivacyRules.isUnlockFresh(
                    lastUnlockEpochMillis = currentLastUnlockEpochMillis,
                    nowEpochMillis = unlockCheckEpochMillis,
                )
        )

    LaunchedEffect(activity, shouldShowLock) {
        val currentActivity = activity ?: return@LaunchedEffect
        if (!shouldShowLock) {
            adsConsentManager.requestConsentInfoUpdate(currentActivity)
        }
    }

    if (profile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            OnboardingRoute(
                factory = OnboardingViewModelFactory(
                    profileRepository = container.profileRepository,
                    cycleRepository = container.cycleRepository,
                    reminderRepository = container.reminderRepository,
                    privacyRepository = container.privacyRepository,
                    reminderScheduler = container.reminderScheduler,
                ),
                onComplete = { remindersEnabled ->
                    if (remindersEnabled) {
                        notificationPermissionRefreshTick += 1
                    }
                },
            )
        }
    } else if (notificationAccessRequired) {
        NotificationPermissionRequiredGate(
            actionLabel = notificationPermissionStatus.recoveryLabel(context)
                ?: context.getString(R.string.allow_notifications),
            onResolveNotificationAccess = resolveNotificationAccess,
        )
    } else {
        MainShell(
            container = container,
            notificationsCanPost = notificationPermissionStatus.canPostNotifications,
            notificationRecoveryActionLabel = notificationPermissionStatus.recoveryLabel(context),
            secureAuthController = authController,
            canRequestAds = canRequestAds,
            interstitialAdManager = activeInterstitialAdManager,
            isAdPrivacyOptionsRequired = isAdPrivacyOptionsRequired,
            onOpenAdPrivacyOptions = {
                activity?.let(adsConsentManager::showPrivacyOptionsForm)
            },
            openPrivacyOnNextFrame = pendingPrivacyNavigation,
            onPrivacyNavigationHandled = { pendingPrivacyNavigation = false },
            onResolveNotificationAccess = resolveNotificationAccess,
        )
    }

    if (profile != null && shouldShowLock && !notificationAccessRequired) {
        AppLockGate(
            privacyRepository = container.privacyRepository,
            appResetRepository = container.appResetRepository,
            secureAuthController = authController,
            onOpenPrivacyAfterRecovery = { pendingPrivacyNavigation = true },
        )
    }
}

