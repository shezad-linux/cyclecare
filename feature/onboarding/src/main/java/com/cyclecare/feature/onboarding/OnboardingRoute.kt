@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.domain.CycleRepository
import com.cyclecare.core.domain.PrivacyRepository
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderDefaults
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal val onboardingDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

internal object OnboardingStateKeys {
    const val AgeBand = "age_band"
    const val CycleLength = "cycle_length"
    const val PeriodLength = "period_length"
    const val LastPeriodStartEpochDay = "last_period_start_epoch_day"
    const val Goal = "goal"
    const val RemindersEnabled = "reminders_enabled"
    const val PrivacyModeEnabled = "privacy_mode_enabled"
}

class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val reminderRepository: ReminderRepository,
    private val privacyRepository: PrivacyRepository,
    private val reminderScheduler: ReminderScheduler,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(savedStateHandle.toOnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private fun updateState(transform: (OnboardingUiState) -> OnboardingUiState) {
        mutableState.update { current ->
            transform(current).also(savedStateHandle::persistDraft)
        }
    }

    fun updateAgeBand(ageBand: AgeBand) = updateState { it.copy(ageBand = ageBand) }

    fun incrementCycleLength(delta: Int) {
        updateState { state ->
            state.copy(cycleLength = (state.cycleLength + delta).coerceIn(20, 45))
        }
    }

    fun incrementPeriodLength(delta: Int) {
        updateState { state ->
            state.copy(periodLength = (state.periodLength + delta).coerceIn(2, 10))
        }
    }

    fun updateGoal(goal: TrackingGoal) = updateState { it.copy(goal = goal) }
    fun updateReminders(enabled: Boolean) = updateState { it.copy(remindersEnabled = enabled) }
    fun updatePrivacy(enabled: Boolean) = updateState { it.copy(privacyModeEnabled = enabled) }
    fun showDatePicker() = mutableState.update { it.copy(showDatePicker = true) }
    fun dismissDatePicker() = mutableState.update { it.copy(showDatePicker = false) }

    fun updateLastPeriodStart(date: LocalDate) {
        updateState {
            it.copy(
                lastPeriodStart = date,
                lastPeriodStartLabel = date.formatOnboardingDate(),
                showDatePicker = false,
            )
        }
    }

    fun submit(onComplete: (Boolean) -> Unit) {
        val state = uiState.value
        if (!state.isSubmitEnabled || state.saving) return

        mutableState.update { it.copy(saving = true) }

        viewModelScope.launch {
            val existingProfile = profileRepository.observeProfile().first()
            if (existingProfile == null) {
                profileRepository.saveProfile(
                    UserProfile(
                        ageBand = state.ageBand,
                        averageCycleLength = state.cycleLength,
                        periodLength = state.periodLength,
                        lastPeriodStart = state.lastPeriodStart,
                        trackingGoal = state.goal,
                        remindersEnabled = state.remindersEnabled,
                        privacyModeEnabled = state.privacyModeEnabled,
                    ),
                )
            }

            val existingUserCycle = cycleRepository.observeCycles().first()
                .any { cycle -> cycle.source == CycleSource.User }
            if (!existingUserCycle) {
                cycleRepository.upsertCycle(
                    CycleRecord(
                        id = UUID.randomUUID().toString(),
                        startDate = state.lastPeriodStart,
                        endDate = state.lastPeriodStart.plusDays(state.periodLength.toLong() - 1),
                        cycleLength = state.cycleLength,
                        source = CycleSource.User,
                    ),
                )
            }

            if (reminderRepository.observeRules().first().isEmpty()) {
                ReminderDefaults.standardRules(state.remindersEnabled, state.goal).forEach { rule ->
                    reminderRepository.upsertRule(rule)
                }
            }
            privacyRepository.acknowledgeLocalData()
            privacyRepository.setNotificationPreviewHidden(state.privacyModeEnabled)
            reminderScheduler.refreshSchedules()
            mutableState.update { it.copy(saving = false) }
            onComplete(state.remindersEnabled)
        }
    }
}

class OnboardingViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val cycleRepository: CycleRepository,
    private val reminderRepository: ReminderRepository,
    private val privacyRepository: PrivacyRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        OnboardingViewModel(
            profileRepository = profileRepository,
            cycleRepository = cycleRepository,
            reminderRepository = reminderRepository,
            privacyRepository = privacyRepository,
            reminderScheduler = reminderScheduler,
            savedStateHandle = extras.createSavedStateHandle(),
        ) as T
}

@Composable
fun OnboardingRoute(
    factory: OnboardingViewModelFactory,
    onComplete: (Boolean) -> Unit,
) {
    val viewModel: OnboardingViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        uiState = uiState,
        onAgeBandSelected = viewModel::updateAgeBand,
        onCycleLengthIncrement = viewModel::incrementCycleLength,
        onPeriodLengthChanged = viewModel::incrementPeriodLength,
        onGoalSelected = viewModel::updateGoal,
        onRemindersChanged = viewModel::updateReminders,
        onPrivacyChanged = viewModel::updatePrivacy,
        onOpenDatePicker = viewModel::showDatePicker,
        onDismissDatePicker = viewModel::dismissDatePicker,
        onDateSelected = viewModel::updateLastPeriodStart,
        onContinue = { viewModel.submit(onComplete) },
    )
}

