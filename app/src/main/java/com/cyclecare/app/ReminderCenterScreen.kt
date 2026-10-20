@file:OptIn(ExperimentalLayoutApi::class)

package com.cyclecare.app

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardPill
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.MetricCard
import com.cyclecare.core.designsystem.metricCardAccents
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderIntelligenceRepository
import com.cyclecare.core.model.ReminderAutoDisabled
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderCenterUiState(
    val unreadCount: Int = 0,
    val groups: List<ReminderEventGroup> = emptyList(),
    /** Types the app paused on its own, with the counts that explain it. */
    val autoDisabled: List<ReminderAutoDisabled> = emptyList(),
)

data class ReminderEventGroup(
    val date: LocalDate,
    val label: String,
    val items: List<ReminderEvent>,
)

private val reminderDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val reminderTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

class ReminderCenterViewModel(
    private val reminderEventsRepository: ReminderEventsRepository,
    private val reminderIntelligenceRepository: ReminderIntelligenceRepository,
    private val reminderRepository: com.cyclecare.core.domain.ReminderRepository,
    private val reminderScheduler: com.cyclecare.core.domain.ReminderScheduler,
) : ViewModel() {
    val uiState: StateFlow<ReminderCenterUiState> = combine(
        reminderEventsRepository.observeRecent(limit = 50),
        reminderEventsRepository.observeUnreadCount(),
        reminderIntelligenceRepository.observeAutoDisabled(),
    ) { events, unreadCount, autoDisabled ->
        ReminderCenterUiState(
            unreadCount = unreadCount,
            groups = events.groupBy { it.deliveredAt.toLocalDate() }
                .map { (date, items) ->
                    ReminderEventGroup(
                        date = date,
                        label = date.format(reminderDateFormatter),
                        items = items,
                    )
                },
            autoDisabled = autoDisabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderCenterUiState())

    fun markAllRead() {
        viewModelScope.launch { reminderEventsRepository.markAllRead() }
    }

    fun markRead(id: String) {
        viewModelScope.launch { reminderEventsRepository.markRead(id) }
    }

    fun dismiss(id: String) {
        viewModelScope.launch { reminderEventsRepository.dismiss(id) }
    }

    fun reEnable(type: com.cyclecare.core.model.ReminderType) {
        viewModelScope.launch {
            // The rules were switched off when the type was paused, so
            // re-enabling has to switch them back on, clear the pause
            // record, and re-arm what was cancelled.
            reEnableReminderType(
                reminderRepository = reminderRepository,
                intelligence = reminderIntelligenceRepository,
                scheduler = reminderScheduler,
                type = type,
            )
        }
    }
}

class ReminderCenterViewModelFactory(
    /**
     * The event history the centre lists.
     *
     * Public because the route reads it back when it builds the factory
     * it actually uses: the navigation shell owns this call site, and the
     * intelligence state the auto-disable explanation needs comes from
     * the app container rather than from the shell.
     */
    val reminderEventsRepository: ReminderEventsRepository,
    private val reminderIntelligenceRepository: ReminderIntelligenceRepository =
        com.cyclecare.core.domain.NoOpReminderIntelligenceRepository,
    private val reminderRepository: com.cyclecare.core.domain.ReminderRepository =
        NoOpReminderRepository,
    private val reminderScheduler: com.cyclecare.core.domain.ReminderScheduler =
        NoOpReminderScheduler,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReminderCenterViewModel(
            reminderEventsRepository = reminderEventsRepository,
            reminderIntelligenceRepository = reminderIntelligenceRepository,
            reminderRepository = reminderRepository,
            reminderScheduler = reminderScheduler,
        ) as T
}

/**
 * A reminder repository that changes nothing.
 *
 * The default for a centre built without the app container: the pause
 * record still shows, but there is nothing to re-enable.
 */
private object NoOpReminderRepository : com.cyclecare.core.domain.ReminderRepository {
    override fun observeRules(): Flow<List<com.cyclecare.core.model.ReminderRule>> =
        MutableStateFlow<List<com.cyclecare.core.model.ReminderRule>>(emptyList()).asStateFlow()

    override suspend fun upsertRule(rule: com.cyclecare.core.model.ReminderRule) = Unit

    override suspend fun setRuleTypeEnabled(
        type: com.cyclecare.core.model.ReminderType,
        enabled: Boolean,
    ) = Unit
}

/** A scheduler that re-arms nothing. See [NoOpReminderRepository]. */
private object NoOpReminderScheduler : com.cyclecare.core.domain.ReminderScheduler {
    override suspend fun refreshSchedules(
        now: java.time.ZonedDateTime,
    ): List<com.cyclecare.core.model.ScheduledReminder> = emptyList()
}

@Composable
fun ReminderCenterRoute(
    factory: ReminderCenterViewModelFactory,
    onOpenEvent: (ReminderEvent) -> Unit,
) {
    val context = LocalContext.current
    // The pause record and the re-enable need the app container. The
    // navigation shell owns the call site of this route, so the container
    // is resolved here rather than threaded through it.
    val container = remember(context) {
        (context.applicationContext as CycleCareApplication).appContainer
    }
    val viewModel: ReminderCenterViewModel = viewModel(
        factory = ReminderCenterViewModelFactory(
            reminderEventsRepository = factory.reminderEventsRepository,
            reminderIntelligenceRepository = container.reminderIntelligenceRepository,
            reminderRepository = container.reminderRepository,
            reminderScheduler = container.reminderScheduler,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReminderCenterScreen(
        uiState = uiState,
        onMarkAllRead = viewModel::markAllRead,
        onOpenEvent = {
            viewModel.markRead(it.id)
            onOpenEvent(it)
        },
        onDismiss = viewModel::dismiss,
        onReEnable = viewModel::reEnable,
    )
}

@Composable
private fun ReminderCenterScreen(
    uiState: ReminderCenterUiState,
    onMarkAllRead: () -> Unit,
    onOpenEvent: (ReminderEvent) -> Unit,
    onDismiss: (String) -> Unit,
    onReEnable: (com.cyclecare.core.model.ReminderType) -> Unit,
) {
    val totalEvents = uiState.groups.sumOf { it.items.size }
    LazyColumn(
        modifier = Modifier.padding(CycleCareSpace.space16),
        contentPadding = PaddingValues(bottom = CycleCareSpace.space24),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            GradientHeaderCard(
                title = stringResource(R.string.reminder_center_title),
                subtitle = if (uiState.unreadCount == 0) {
                    stringResource(R.string.all_caught_up)
                } else {
                    pluralStringResource(
                        R.plurals.n_unread_reminders,
                        uiState.unreadCount,
                        uiState.unreadCount,
                    )
                },
                supportingContent = {
                    if (uiState.unreadCount > 0) {
                        FilledTonalButton(onClick = onMarkAllRead) {
                            Text(stringResource(R.string.mark_all_read))
                        }
                    }
                },
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MetricCard(
                    label = uiText(R.string.unread),
                    value = uiState.unreadCount.toString(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = metricCardAccents(MaterialTheme.colorScheme).neutral,
                )
                MetricCard(
                    label = uiText(R.string.active_days),
                    value = uiState.groups.size.toString(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = metricCardAccents(MaterialTheme.colorScheme).neutral,
                )
                MetricCard(
                    label = uiText(R.string.stored_events),
                    value = totalEvents.toString(),
                    modifier = Modifier.widthIn(min = 150.dp),
                    accent = metricCardAccents(MaterialTheme.colorScheme).neutral,
                )
            }
        }

        if (uiState.autoDisabled.isNotEmpty()) {
            item {
                DashboardSectionCard(
                    title = stringResource(R.string.reminder_auto_disabled_card_title),
                    eyebrow = stringResource(R.string.reminder_auto_disabled_title),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                    ) {
                        uiState.autoDisabled.forEach { paused ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
                                ) {
                                    Text(
                                        text = reminderTypeDisplayName(paused.type),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.reminder_auto_disabled_card_counts,
                                            paused.dismissedCount,
                                            paused.openedCount,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { onReEnable(paused.type) }) {
                                    Text(stringResource(R.string.reminder_action_re_enable))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (uiState.groups.isEmpty()) {
            item {
                DashboardSectionCard(
                    title = stringResource(R.string.no_reminders_yet),
                    subtitle = stringResource(R.string.no_reminders_yet_subtitle),
                    eyebrow = stringResource(R.string.local_history_eyebrow),
                )
            }
        } else {
            uiState.groups.forEach { group ->
                item {
                    DashboardSectionCard(
                        title = group.label,
                        subtitle = pluralStringResource(
                            R.plurals.reminders_captured_on_day,
                            group.items.size,
                            group.items.size,
                        ),
                        eyebrow = stringResource(R.string.delivered_reminders_eyebrow),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
                        ) {
                            group.items.forEachIndexed { index, item ->
                                ReminderEventCard(
                                    event = item,
                                    onOpen = { onOpenEvent(item) },
                                    onDismiss = { onDismiss(item.id) },
                                )
                                if (index != group.items.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderEventCard(
    event: ReminderEvent,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = CycleCareSpace.space16),
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space4),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
            ) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                if (event.readAtEpochMillis == null) {
                    DashboardPill(text = stringResource(R.string.unread), emphasized = true)
                }
                DashboardPill(
                    text = when (event.deepLinkTarget) {
                        ReminderDeepLinkTarget.Home -> stringResource(R.string.reminder_target_home)
                        ReminderDeepLinkTarget.Calendar -> stringResource(R.string.reminder_target_calendar)
                        ReminderDeepLinkTarget.Log -> stringResource(R.string.reminder_target_log)
                        ReminderDeepLinkTarget.ReminderCenter ->
                            stringResource(R.string.reminder_target_reminder_center)
                    },
                )
            }
            Text(
                event.deliveredAt.format(reminderTimeFormatter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                event.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.reminder_linked_surface_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space8)) {
            FilledTonalButton(onClick = onOpen) {
                Text(stringResource(R.string.reminder_action_open))
            }
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.reminder_action_dismiss))
            }
        }
    }
}

@Composable
private fun reminderTypeDisplayName(type: com.cyclecare.core.model.ReminderType): String =
    when (type) {
        com.cyclecare.core.model.ReminderType.PeriodPrep ->
            stringResource(R.string.reminder_type_label_period_prep)
        com.cyclecare.core.model.ReminderType.FertileWindowStart ->
            stringResource(R.string.reminder_type_label_fertile_window)
        com.cyclecare.core.model.ReminderType.OvulationEstimate ->
            stringResource(R.string.reminder_type_label_ovulation)
        com.cyclecare.core.model.ReminderType.DailyLog ->
            stringResource(R.string.reminder_type_label_daily_log)
        com.cyclecare.core.model.ReminderType.LatePeriod ->
            stringResource(R.string.reminder_type_label_late_period)
        // Never generated; the label keeps the compiler exhaustive.
        com.cyclecare.core.model.ReminderType.Hydration ->
            stringResource(R.string.reminder_type_label_daily_log)
        com.cyclecare.core.model.ReminderType.Pill ->
            stringResource(R.string.reminder_type_label_daily_log)
    }
