@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.ScreenDebugPreviews
import com.cyclecare.core.designsystem.CycleCareTheme
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val onboardingDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

private object OnboardingStateKeys {
    const val AgeBand = "age_band"
    const val CycleLength = "cycle_length"
    const val PeriodLength = "period_length"
    const val LastPeriodStartEpochDay = "last_period_start_epoch_day"
    const val Goal = "goal"
    const val RemindersEnabled = "reminders_enabled"
    const val PrivacyModeEnabled = "privacy_mode_enabled"
}

data class OnboardingUiState(
    val ageBand: AgeBand = AgeBand.Twenties,
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lastPeriodStart: LocalDate = LocalDate.now().minusDays(5),
    val lastPeriodStartLabel: String = LocalDate.now().minusDays(5).formatOnboardingDate(),
    val goal: TrackingGoal = TrackingGoal.PeriodPrep,
    val remindersEnabled: Boolean = false,
    val privacyModeEnabled: Boolean = true,
    val showDatePicker: Boolean = false,
    val saving: Boolean = false,
) {
    val cycleLengthLabel: String get() = "$cycleLength days"
    val periodLengthLabel: String get() = "$periodLength days"
    val cycleLengthError: String?
        get() = if (cycleLength in 20..45) null else "Cycle length must stay between 20 and 45 days."
    val periodLengthError: String?
        get() = if (periodLength in 2..10) null else "Period length must stay between 2 and 10 days."
    val lastPeriodStartError: String?
        get() = if (!lastPeriodStart.isAfter(LocalDate.now())) null else "Last period start cannot be in the future."
    val isSubmitEnabled: Boolean
        get() = !saving &&
            cycleLengthError == null &&
            periodLengthError == null &&
            lastPeriodStartError == null
}

internal object OnboardingTags {
    const val CYCLE_LENGTH_VALUE = "onboarding_cycle_length_value"
    const val CYCLE_LENGTH_SLIDER = "onboarding_cycle_length_slider"
    const val LAST_PERIOD_ROW = "onboarding_last_period_row"
    const val DATE_PICKER = "onboarding_date_picker"
    const val DATE_PICKER_CONFIRM = "onboarding_date_picker_confirm"
    const val START_TRACKING_BUTTON = "onboarding_start_tracking"
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

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onAgeBandSelected: (AgeBand) -> Unit,
    onCycleLengthIncrement: (Int) -> Unit,
    onPeriodLengthChanged: (Int) -> Unit,
    onGoalSelected: (TrackingGoal) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onPrivacyChanged: (Boolean) -> Unit,
    onOpenDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onContinue: () -> Unit,
) {
    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val progress by animateFloatAsState(targetValue = if (currentStep == 1) 0.5f else 1f, label = "progress")

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentStep = currentStep,
                saving = uiState.saving,
                enabled = uiState.isSubmitEnabled,
                onStep1Continue = { currentStep = 2 },
                onBack = { currentStep = 1 },
                onContinue = onContinue,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OnboardingProgressHeader(currentStep = currentStep, progress = progress)

            GradientHeaderCard(
                title = "Private cycle tracking",
                subtitle = "Set your baseline once and keep everything on-device by default.",
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                supportingContent = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OnboardingPill(text = "Local profile")
                        OnboardingPill(text = "Editable later")
                    }
                },
            )

            if (currentStep == 1) {
                OnboardingBaselineStep(
                    uiState = uiState,
                    onAgeBandSelected = onAgeBandSelected,
                    onCycleLengthIncrement = onCycleLengthIncrement,
                    onPeriodLengthChanged = onPeriodLengthChanged,
                    onOpenDatePicker = onOpenDatePicker,
                )
            } else {
                OnboardingPreferencesStep(
                    uiState = uiState,
                    onGoalSelected = onGoalSelected,
                    onRemindersChanged = onRemindersChanged,
                    onPrivacyChanged = onPrivacyChanged,
                )
            }
        }
    }

    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.lastPeriodStart.toEpochMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !utcTimeMillis.toLocalDate().isAfter(LocalDate.now())
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                Button(
                    modifier = Modifier.testTag(OnboardingTags.DATE_PICKER_CONFIRM),
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            onDateSelected(selectedMillis.toLocalDate())
                        } ?: onDismissDatePicker()
                    },
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                modifier = Modifier.testTag(OnboardingTags.DATE_PICKER),
            )
        }
    }
}

@Composable
private fun OnboardingBaselineStep(
    uiState: OnboardingUiState,
    onAgeBandSelected: (AgeBand) -> Unit,
    onCycleLengthIncrement: (Int) -> Unit,
    onPeriodLengthChanged: (Int) -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    OnboardingPanel(
        title = "Baseline setup",
        subtitle = "These details create the first prediction and keep the rest of the app useful from day one.",
    ) {
        OnboardingInputBlock(
            icon = Icons.Rounded.PersonOutline,
            title = "Age band",
            description = "Used to tailor cycle predictions and insights.",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AgeBand.entries.forEach { ageBand ->
                    FilterChip(
                        selected = uiState.ageBand == ageBand,
                        onClick = { onAgeBandSelected(ageBand) },
                        modifier = Modifier.testTag("onboarding_age_${ageBand.name}"),
                        label = { Text(ageBand.displayLabel()) },
                        colors = onboardingChipColors(),
                    )
                }
            }
        }

        SoftDivider()

        OnboardingInputBlock(
            icon = Icons.Rounded.Sync,
            title = "Typical cycle length",
        ) {
            StepperField(
                valueLabel = uiState.cycleLengthLabel,
                onDecrement = { onCycleLengthIncrement(-1) },
                onIncrement = { onCycleLengthIncrement(1) },
                decrementEnabled = uiState.cycleLength > 20,
                incrementEnabled = uiState.cycleLength < 45,
                contentDescriptionBase = "cycle length",
                valueTestTag = OnboardingTags.CYCLE_LENGTH_VALUE,
                modifier = Modifier
                    .testTag(OnboardingTags.CYCLE_LENGTH_SLIDER)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = uiState.cycleLength.toFloat(),
                            range = 20f..45f,
                            steps = 24,
                        )
                        setProgress { target ->
                            val targetLength = target.roundToInt().coerceIn(20, 45)
                            onCycleLengthIncrement(targetLength - uiState.cycleLength)
                            true
                        }
                    },
            )
            uiState.cycleLengthError?.let { error ->
                InlineError(text = error)
            }
        }

        SoftDivider()

        OnboardingInputBlock(
            icon = Icons.Rounded.FavoriteBorder,
            title = "Typical period length",
        ) {
            StepperField(
                valueLabel = uiState.periodLengthLabel,
                onDecrement = { onPeriodLengthChanged(-1) },
                onIncrement = { onPeriodLengthChanged(1) },
                decrementEnabled = uiState.periodLength > 2,
                incrementEnabled = uiState.periodLength < 10,
            )
            uiState.periodLengthError?.let { error ->
                InlineError(text = error)
            }
        }

        SoftDivider()

        OnboardingInputBlock(
            icon = Icons.Rounded.CalendarMonth,
            title = "Last period start",
            description = "This helps predict your next cycle accurately.",
        ) {
            DateSelectionField(
                label = uiState.lastPeriodStartLabel,
                onClick = onOpenDatePicker,
            )
            uiState.lastPeriodStartError?.let { error ->
                InlineError(text = error)
            }
        }
    }
}

@Composable
private fun OnboardingPreferencesStep(
    uiState: OnboardingUiState,
    onGoalSelected: (TrackingGoal) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onPrivacyChanged: (Boolean) -> Unit,
) {
    OnboardingPanel(
        title = "Preferences",
        subtitle = "Choose the support you want active when your dashboard opens.",
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        OnboardingInputBlock(
            icon = Icons.Rounded.FavoriteBorder,
            title = "Wellness goal",
            description = "Choose your primary goal",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackingGoal.entries.forEach { goal ->
                    FilterChip(
                        selected = uiState.goal == goal,
                        onClick = { onGoalSelected(goal) },
                        modifier = Modifier.testTag("onboarding_goal_${goal.name}"),
                        label = { Text(goal.displayLabel()) },
                        colors = onboardingChipColors(),
                    )
                }
            }
            GoalSummary(text = uiState.goal.description())
        }

        SoftDivider()

        ToggleSettingRow(
            icon = Icons.Rounded.NotificationsNone,
            title = "Smart reminders",
            description = "Get period prep, daily check-ins, and conception-aware fertile-window or ovulation reminders. Late-period reminders can be enabled later in Settings.",
            checked = uiState.remindersEnabled,
            onCheckedChange = onRemindersChanged,
        )
        ToggleSettingRow(
            icon = Icons.Rounded.Lock,
            title = "Hide sensitive details in notifications",
            description = "Notifications won't show words like 'period' or 'fertile'.",
            checked = uiState.privacyModeEnabled,
            onCheckedChange = onPrivacyChanged,
        )
    }
}

@Composable
private fun OnboardingProgressHeader(
    currentStep: Int,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Step $currentStep of 2",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (currentStep == 1) "Baseline" else "Preferences",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun OnboardingPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun OnboardingPanel(
    title: String,
    subtitle: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun OnboardingInputBlock(
    icon: ImageVector,
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(icon = icon, title = title)
        description?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun SoftDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
}

@Composable
private fun InlineError(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun DateSelectionField(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTags.LAST_PERIOD_ROW)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap to choose a date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GoalSummary(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    saving: Boolean,
    enabled: Boolean,
    onStep1Continue: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentStep == 1) {
                Button(
                    onClick = onStep1Continue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTags.START_TRACKING_BUTTON),
                    enabled = enabled,
                ) {
                    Text("Continue")
                }
            } else {
                FinalStepActions(
                    saving = saving,
                    enabled = enabled,
                    onBack = onBack,
                    onContinue = onContinue,
                )
            }
            FooterPrivacyNote()
        }
    }
}

@Composable
private fun FinalStepActions(
    saving: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier.testTag("onboarding_final_step"),
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTags.START_TRACKING_BUTTON),
                        enabled = enabled,
                    ) {
                        Text(if (saving) "Saving..." else "Start tracking my cycle")
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Back")
                }
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("onboarding_final_step"),
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTags.START_TRACKING_BUTTON),
                        enabled = enabled,
                    ) {
                        Text(if (saving) "Saving..." else "Start tracking my cycle")
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterPrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Your data stays private on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun onboardingChipColors() =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
    )

@Composable
private fun SectionHeading(
    icon: ImageVector,
    title: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StepperField(
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    contentDescriptionBase: String = "period length",
    valueTestTag: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onDecrement, enabled = decrementEnabled) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease $contentDescriptionBase")
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.headlineSmall,
                modifier = if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier
            )
            FilledTonalIconButton(onClick = onIncrement, enabled = incrementEnabled) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase $contentDescriptionBase")
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

internal fun AgeBand.displayLabel(): String = when (this) {
    AgeBand.Teen -> "Teen (13-19)"
    AgeBand.Twenties -> "20s"
    AgeBand.Thirties -> "30s"
    AgeBand.FortiesPlus -> "40+"
}

internal fun TrackingGoal.displayLabel(): String = when (this) {
    TrackingGoal.PeriodPrep -> "Period Preparation"
    TrackingGoal.SymptomAwareness -> "Symptom Tracking"
    TrackingGoal.ConceptionAwareness -> "Pregnancy Planning"
}

internal fun TrackingGoal.description(): String = when (this) {
    TrackingGoal.PeriodPrep -> "Get reminders before your period"
    TrackingGoal.SymptomAwareness -> "Track mood, pain, and trends"
    TrackingGoal.ConceptionAwareness -> "Focus on fertile window insights"
}

private fun LocalDate.formatOnboardingDate(): String = onboardingDateFormatter.format(this)

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun SavedStateHandle.toOnboardingUiState(): OnboardingUiState {
    val defaultDate = LocalDate.now().minusDays(5)
    val lastPeriodStart = get<Long>(OnboardingStateKeys.LastPeriodStartEpochDay)
        ?.let(LocalDate::ofEpochDay)
        ?: defaultDate
    return OnboardingUiState(
        ageBand = get<String>(OnboardingStateKeys.AgeBand)
            ?.let { value -> runCatching { AgeBand.valueOf(value) }.getOrNull() }
            ?: AgeBand.Twenties,
        cycleLength = get<Int>(OnboardingStateKeys.CycleLength) ?: 28,
        periodLength = get<Int>(OnboardingStateKeys.PeriodLength) ?: 5,
        lastPeriodStart = lastPeriodStart,
        lastPeriodStartLabel = lastPeriodStart.formatOnboardingDate(),
        goal = get<String>(OnboardingStateKeys.Goal)
            ?.let { value -> runCatching { TrackingGoal.valueOf(value) }.getOrNull() }
            ?: TrackingGoal.PeriodPrep,
        remindersEnabled = get<Boolean>(OnboardingStateKeys.RemindersEnabled) ?: true,
        privacyModeEnabled = get<Boolean>(OnboardingStateKeys.PrivacyModeEnabled) ?: true,
    )
}

internal fun SavedStateHandle.persistDraft(state: OnboardingUiState) {
    this[OnboardingStateKeys.AgeBand] = state.ageBand.name
    this[OnboardingStateKeys.CycleLength] = state.cycleLength
    this[OnboardingStateKeys.PeriodLength] = state.periodLength
    this[OnboardingStateKeys.LastPeriodStartEpochDay] = state.lastPeriodStart.toEpochDay()
    this[OnboardingStateKeys.Goal] = state.goal.name
    this[OnboardingStateKeys.RemindersEnabled] = state.remindersEnabled
    this[OnboardingStateKeys.PrivacyModeEnabled] = state.privacyModeEnabled
}

@ScreenDebugPreviews
@Composable
private fun OnboardingScreenPreview() {
    CycleCareTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(),
            onAgeBandSelected = {},
            onCycleLengthIncrement = {},
            onPeriodLengthChanged = {},
            onGoalSelected = {},
            onRemindersChanged = {},
            onPrivacyChanged = {},
            onOpenDatePicker = {},
            onDismissDatePicker = {},
            onDateSelected = {},
            onContinue = {},
        )
    }
}
