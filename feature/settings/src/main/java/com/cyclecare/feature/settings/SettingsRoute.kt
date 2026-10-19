@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.domain.AppResetRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderChannelSettingsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderSoundPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
                    Text(stringResource(R.string.reset_local_data))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.reset_local_data_2)) },
            text = {
                Text(
                    stringResource(
                        R.string.this_clears_your_profile_cycle_history_symptoms_reminders_p,
                    ),
                )
            },
        )
    }
}

