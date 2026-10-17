@file:OptIn(ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.ConsentState
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import com.cyclecare.core.model.ReminderType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val consentState: ConsentState = ConsentState(
        hideNotificationPreview = true,
        localDataAcknowledged = false,
        appLockEnabled = false,
    ),
    val remindersEnabled: Boolean = true,
    val reminderItems: List<SettingsReminderItem> = emptyList(),
    val channelItems: List<SettingsChannelItem> = emptyList(),
)

data class SettingsReminderItem(
    val rule: ReminderRule,
    val title: String,
    val scheduleLabel: String,
    val detail: String,
)

data class SettingsChannelItem(
    val settings: ReminderChannelSettings,
    val title: String,
    val detail: String,
)

private val reminderTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal object SettingsTags {
    const val LIST = "settings_list"
    const val OPEN_RESET = "settings_open_reset"
    const val CONFIRM_RESET = "settings_confirm_reset"
    const val REMINDERS_ENABLED = "settings_reminders_enabled"
    const val NOTIFICATION_RECOVERY = "settings_notification_recovery"
    const val OPEN_REMINDER_CENTER = "settings_open_reminder_center"
    const val OPEN_AD_PRIVACY = "settings_open_ad_privacy"
}

internal fun ReminderType.displayTitle(): String = when (this) {
    ReminderType.PeriodPrep -> "Period preparation"
    ReminderType.FertileWindowStart -> "Fertile window"
    ReminderType.OvulationEstimate -> "Ovulation estimate"
    ReminderType.DailyLog -> "Daily check-in"
    ReminderType.LatePeriod -> "Late-period check"
    ReminderType.Hydration -> "Hydration reminder"
    ReminderType.Pill -> "Pill reminder"
}

internal fun buildReminderScheduleLabel(rule: ReminderRule): String {
    val timeLabel = LocalTime.of(rule.hourOfDay, rule.minute).format(reminderTimeFormatter)
    return when (rule.type) {
        ReminderType.PeriodPrep -> {
            val offset = rule.daysBefore.absoluteValue
            val dayLabel = if (offset == 1) "1 day before" else "$offset days before"
            "$dayLabel • $timeLabel"
        }
        ReminderType.FertileWindowStart -> "Window start • $timeLabel"
        ReminderType.OvulationEstimate -> "Ovulation estimate • $timeLabel"
        ReminderType.DailyLog -> "Daily • $timeLabel"
        ReminderType.LatePeriod -> {
            val offset = rule.daysBefore.absoluteValue
            val dayLabel = if (offset == 1) "1 day late" else "$offset days late"
            "$dayLabel • $timeLabel"
        }
        else -> timeLabel
    }
}

internal fun ReminderType.detailCopy(): String = when (this) {
    ReminderType.PeriodPrep -> "Heads-up before your predicted start date."
    ReminderType.FertileWindowStart -> "Heads-up when your fertile window estimate begins."
    ReminderType.OvulationEstimate -> "Heads-up on your ovulation estimate day."
    ReminderType.DailyLog -> "Prompt to capture mood, pain, energy, and notes."
    ReminderType.LatePeriod -> "Optional check-in if your predicted start date has already passed."
    ReminderType.Hydration -> "Gentle nudge to stay hydrated."
    ReminderType.Pill -> "Daily birth control reminder."
}

private val reminderDisplayOrder = listOf(
    ReminderType.PeriodPrep,
    ReminderType.FertileWindowStart,
    ReminderType.OvulationEstimate,
    ReminderType.DailyLog,
    ReminderType.LatePeriod,
)

internal fun buildReminderItems(rules: List<ReminderRule>): List<SettingsReminderItem> =
    rules
        .filter { it.type in reminderDisplayOrder }
        .sortedWith(compareBy({ reminderDisplayOrder.indexOf(it.type).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it.id }))
        .map { rule ->
            SettingsReminderItem(
                rule = rule,
                title = rule.type.displayTitle(),
                scheduleLabel = buildReminderScheduleLabel(rule),
                detail = rule.type.detailCopy(),
            )
        }

private val channelDisplayOrder = listOf(
    ReminderChannelKey.CycleAlerts,
    ReminderChannelKey.FertilityAlerts,
    ReminderChannelKey.DailyCheckIn,
)

internal fun ReminderChannelKey.displayTitle(): String = when (this) {
    ReminderChannelKey.CycleAlerts -> "Cycle alerts"
    ReminderChannelKey.FertilityAlerts -> "Fertility alerts"
    ReminderChannelKey.DailyCheckIn -> "Daily check-in"
}

internal fun ReminderChannelKey.detailCopy(): String = when (this) {
    ReminderChannelKey.CycleAlerts -> "Period preparation and optional late-period reminders."
    ReminderChannelKey.FertilityAlerts -> "Fertile-window start and ovulation reminders."
    ReminderChannelKey.DailyCheckIn -> "Daily symptom logging prompts."
}

internal fun ReminderSoundPreset.displayTitle(): String = when (this) {
    ReminderSoundPreset.GentleBell -> "Gentle bell"
    ReminderSoundPreset.SoftChime -> "Soft chime"
    ReminderSoundPreset.WaterDrop -> "Water drop"
}

internal fun buildChannelItems(settings: List<ReminderChannelSettings>): List<SettingsChannelItem> =
    settings
        .sortedBy { channelDisplayOrder.indexOf(it.channelKey).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        .map { item ->
            SettingsChannelItem(
                settings = item,
                title = item.channelKey.displayTitle(),
                detail = item.channelKey.detailCopy(),
            )
        }

class SettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val privacyRepository: PrivacyRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderChannelSettingsRepository: ReminderChannelSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appResetRepository: AppResetRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        profileRepository.observeProfile(),
        privacyRepository.observeConsent(),
        reminderRepository.observeRules(),
        reminderChannelSettingsRepository.observeChannelSettings(),
    ) { profile, consent, reminders, channelSettings ->
        SettingsUiState(
            consentState = consent,
            remindersEnabled = profile?.remindersEnabled ?: false,
            reminderItems = buildReminderItems(reminders),
            channelItems = buildChannelItems(channelSettings),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun toggleRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            profileRepository.updateRemindersEnabled(enabled)
            reminderScheduler.refreshSchedules()
        }
    }

    fun toggleNotificationPreview(hidden: Boolean) {
        viewModelScope.launch {
            privacyRepository.setNotificationPreviewHidden(hidden)
        }
    }

    fun toggleReminderRule(rule: ReminderRule, enabled: Boolean) {
        viewModelScope.launch {
            reminderRepository.upsertRule(rule.copy(enabled = enabled))
            reminderScheduler.refreshSchedules()
        }
    }

    fun updateChannelSettings(settings: ReminderChannelSettings) {
        viewModelScope.launch {
            reminderChannelSettingsRepository.upsertChannelSettings(settings)
            reminderScheduler.refreshSchedules()
        }
    }

    fun resetLocalData() {
        viewModelScope.launch {
            appResetRepository.resetAllLocalData()
            reminderScheduler.refreshSchedules()
        }
    }

    fun refreshReminderSchedules() {
        viewModelScope.launch {
            reminderScheduler.refreshSchedules()
        }
    }
}

class SettingsViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val privacyRepository: PrivacyRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderChannelSettingsRepository: ReminderChannelSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appResetRepository: AppResetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(
            profileRepository = profileRepository,
            privacyRepository = privacyRepository,
            reminderRepository = reminderRepository,
            reminderChannelSettingsRepository = reminderChannelSettingsRepository,
            reminderScheduler = reminderScheduler,
            appResetRepository = appResetRepository,
        ) as T
}

@Composable
fun SettingsRoute(
    factory: SettingsViewModelFactory,
    onOpenPrivacy: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenReminderCenter: () -> Unit,
    notificationsCanPost: Boolean,
    notificationRecoveryActionLabel: String?,
    unreadReminderCount: Int,
    onPreviewReminderSound: (ReminderSoundPreset) -> Unit,
    showAdPrivacyOptions: Boolean,
    onOpenAdPrivacyOptions: () -> Unit,
    onResolveNotificationAccess: () -> Unit,
) {
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        notificationsCanPost = notificationsCanPost,
        notificationRecoveryActionLabel = notificationRecoveryActionLabel,
        onResolveNotificationAccess = {
            onResolveNotificationAccess()
            viewModel.refreshReminderSchedules()
        },
        onToggleRemindersEnabled = viewModel::toggleRemindersEnabled,
        onToggleNotificationPreview = viewModel::toggleNotificationPreview,
        onToggleReminderRule = viewModel::toggleReminderRule,
        onUpdateChannelSettings = viewModel::updateChannelSettings,
        onOpenResetConfirmation = { showResetConfirmation = true },
        onOpenPrivacy = onOpenPrivacy,
        onOpenExport = onOpenExport,
        onOpenReminderCenter = onOpenReminderCenter,
        unreadReminderCount = unreadReminderCount,
        onPreviewReminderSound = onPreviewReminderSound,
        showAdPrivacyOptions = showAdPrivacyOptions,
        onOpenAdPrivacyOptions = onOpenAdPrivacyOptions,
    )

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag(SettingsTags.CONFIRM_RESET),
                    onClick = {
                        showResetConfirmation = false
                        viewModel.resetLocalData()
                    },
                ) {
                    Text("Reset local data")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Reset local data?") },
            text = {
                Text("This clears your profile, cycle history, symptoms, reminders, privacy settings, PIN, and local exports from this device.")
            },
        )
    }
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    notificationsCanPost: Boolean,
    notificationRecoveryActionLabel: String?,
    onResolveNotificationAccess: () -> Unit,
    onToggleRemindersEnabled: (Boolean) -> Unit,
    onToggleNotificationPreview: (Boolean) -> Unit,
    onToggleReminderRule: (ReminderRule, Boolean) -> Unit,
    onUpdateChannelSettings: (ReminderChannelSettings) -> Unit,
    onOpenResetConfirmation: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenReminderCenter: () -> Unit,
    unreadReminderCount: Int,
    onPreviewReminderSound: (ReminderSoundPreset) -> Unit,
    showAdPrivacyOptions: Boolean,
    onOpenAdPrivacyOptions: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(16.dp)
            .testTag(SettingsTags.LIST),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeaderCard(
                title = "Settings",
                subtitle = "Local-first controls for reminders, privacy, and export.",
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsBadge(text = "Stored locally")
                        SettingsBadge(
                            text = if (notificationsCanPost) "Notifications on" else "Notifications blocked",
                            emphasized = !notificationsCanPost,
                        )
                    }
                },
            )
        }

        item {
            SettingsMetricGrid(
                remindersEnabled = uiState.remindersEnabled,
                unreadReminderCount = unreadReminderCount,
                previewMode = if (uiState.consentState.hideNotificationPreview) "Private" else "Detailed",
            )
        }

        item {
            ActionCard(
                title = "Reminder center",
                body = if (unreadReminderCount == 0) {
                    "Reopen recent reminders and in-app alerts even if Android blocked the notification."
                } else {
                    "$unreadReminderCount unread reminder${if (unreadReminderCount == 1) "" else "s"} waiting in your local reminder center."
                },
                actionLabel = "Open reminder center",
                onAction = onOpenReminderCenter,
                actionModifier = Modifier.testTag(SettingsTags.OPEN_REMINDER_CENTER),
            )
        }

        if (!notificationsCanPost) {
            item {
                SectionCard(
                    title = "Notifications are blocked",
                    body = "Period, fertile-window, and daily check-in reminders stay local, but Android will not show them until notification access is restored.",
                ) {
                    notificationRecoveryActionLabel?.let { actionLabel ->
                        OutlinedButton(
                            onClick = onResolveNotificationAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SettingsTags.NOTIFICATION_RECOVERY),
                        ) {
                            Text(actionLabel)
                        }
                    }
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Reminders", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keep local reminder timing active across baseline edits, reboot, and timezone changes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitchRow(
                        modifier = Modifier.testTag(SettingsTags.REMINDERS_ENABLED),
                        title = "Reminders enabled",
                        detail = "Cancel or restore all future reminder work at once.",
                        checked = uiState.remindersEnabled,
                        onCheckedChange = onToggleRemindersEnabled,
                    )
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Alert channels", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Choose calm bundled sounds and vibration behavior for cycle, fertility, and daily check-in reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    uiState.channelItems.forEachIndexed { index, item ->
                        ReminderChannelCard(
                            item = item,
                            remindersEnabled = uiState.remindersEnabled,
                            onToggleChannelEnabled = { enabled ->
                                onUpdateChannelSettings(item.settings.copy(enabled = enabled))
                            },
                            onToggleSoundEnabled = { enabled ->
                                onUpdateChannelSettings(item.settings.copy(soundEnabled = enabled))
                            },
                            onToggleVibrationEnabled = { enabled ->
                                onUpdateChannelSettings(item.settings.copy(vibrationEnabled = enabled))
                            },
                            onSelectSoundPreset = { preset ->
                                onUpdateChannelSettings(item.settings.copy(soundPreset = preset))
                            },
                            onPreviewSound = { onPreviewReminderSound(item.settings.soundPreset) },
                        )
                        if (index != uiState.channelItems.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Notification privacy", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Keep sensitive details out of visible lock-screen reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingSwitchRow(
                        title = "Hide notification previews",
                        detail = "Show a generic reminder body until the app is unlocked.",
                        checked = uiState.consentState.hideNotificationPreview,
                        onCheckedChange = onToggleNotificationPreview,
                    )
                }
            }
        }

        item {
            CycleCarePanel(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Reminder rules", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Control which local reminders stay active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.reminderItems.isEmpty()) {
                        Text(
                            "No reminder rules are available yet.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        uiState.reminderItems.forEachIndexed { index, item ->
                            ReminderRuleCard(
                                item = item,
                                enabled = uiState.remindersEnabled,
                                onCheckedChange = { enabled -> onToggleReminderRule(item.rule, enabled) },
                            )
                            if (index != uiState.reminderItems.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }

        item {
            ActionCard(
                title = "Privacy lock",
                body = "Set or update your PIN before sensitive screens open.",
                actionLabel = "Open privacy lock",
                onAction = onOpenPrivacy,
            )
        }

        if (showAdPrivacyOptions) {
            item {
                ActionCard(
                    title = "Ad privacy choices",
                    body = "Review or update the ad consent options required for your region.",
                    actionLabel = "Manage ad privacy",
                    onAction = onOpenAdPrivacyOptions,
                    actionModifier = Modifier.testTag(SettingsTags.OPEN_AD_PRIVACY),
                )
            }
        }

        item {
            ActionCard(
                title = "Export and share",
                body = "Create local markdown or PDF reports only when you choose to share them.",
                actionLabel = "Open export",
                onAction = onOpenExport,
            )
        }

        item {
            ActionCard(
                title = "Reset local data",
                body = "Clear local health data, reminder settings, privacy state, and return to onboarding.",
                actionLabel = "Reset local data",
                onAction = onOpenResetConfirmation,
                actionModifier = Modifier.testTag(SettingsTags.OPEN_RESET),
            )
        }
    }
}

@Composable
private fun SettingsMetricGrid(
    remindersEnabled: Boolean,
    unreadReminderCount: Int,
    previewMode: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackMetrics = maxWidth < 430.dp || LocalDensity.current.fontScale > 1.2f
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsMetricCard(
                    label = "Reminders",
                    value = if (remindersEnabled) "On" else "Off",
                    accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                )
                SettingsMetricCard(
                    label = "Unread center",
                    value = unreadReminderCount.toString(),
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
                )
                SettingsMetricCard(
                    label = "Preview mode",
                    value = previewMode,
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsMetricCard(
                        label = "Reminders",
                        value = if (remindersEnabled) "On" else "Off",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                    )
                    SettingsMetricCard(
                        label = "Unread center",
                        value = unreadReminderCount.toString(),
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
                    )
                }
                SettingsMetricCard(
                    label = "Preview mode",
                    value = previewMode,
                    accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun SettingsMetricCard(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    MetricCard(
        label = label,
        value = value,
        modifier = modifier.fillMaxWidth(),
        accent = accent,
    )
}

@Composable
private fun SectionCard(
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    DashboardSectionCard(
        title = title,
        subtitle = body,
        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        eyebrow = "Notification access",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            action()
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionModifier: Modifier = Modifier,
) {
    DashboardSectionCard(
        title = title,
        subtitle = body,
        accent = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        eyebrow = "Action",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onAction, modifier = actionModifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ReminderRuleCard(
    item: SettingsReminderItem,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                item.scheduleLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = item.rule.enabled,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ReminderChannelCard(
    item: SettingsChannelItem,
    remindersEnabled: Boolean,
    onToggleChannelEnabled: (Boolean) -> Unit,
    onToggleSoundEnabled: (Boolean) -> Unit,
    onToggleVibrationEnabled: (Boolean) -> Unit,
    onSelectSoundPreset: (ReminderSoundPreset) -> Unit,
    onPreviewSound: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingSwitchRow(
            title = item.title,
            detail = item.detail,
            checked = item.settings.enabled,
            onCheckedChange = onToggleChannelEnabled,
        )
        SettingSwitchRow(
            title = "Sound",
            detail = "Play a calm bundled sound for this channel.",
            checked = item.settings.soundEnabled,
            onCheckedChange = onToggleSoundEnabled,
        )
        SettingSwitchRow(
            title = "Vibration",
            detail = "Use vibration for this channel when Android allows it.",
            checked = item.settings.vibrationEnabled,
            onCheckedChange = onToggleVibrationEnabled,
        )
        Text("Sound preset", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderSoundPreset.entries.forEach { preset ->
                val selected = preset == item.settings.soundPreset
                if (selected) {
                    FilledTonalButton(
                        onClick = { onSelectSoundPreset(preset) },
                        enabled = remindersEnabled && item.settings.enabled && item.settings.soundEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(preset.displayTitle())
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelectSoundPreset(preset) },
                        enabled = remindersEnabled && item.settings.enabled && item.settings.soundEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(preset.displayTitle())
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onPreviewSound,
            enabled = remindersEnabled && item.settings.enabled && item.settings.soundEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Preview sound")
        }
    }
}

@Composable
private fun SettingSwitchRow(
    modifier: Modifier = Modifier,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsBadge(
    text: String,
    emphasized: Boolean = false,
) {
    DashboardPill(text = text, emphasized = emphasized)
}
