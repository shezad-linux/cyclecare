package com.cyclecare.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyclecare.core.designsystem.DashboardSectionCard
import com.cyclecare.core.designsystem.GradientHeaderCard
import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.domain.MigrationDivergenceRepository
import com.cyclecare.core.model.MigrationDivergence
import com.cyclecare.core.model.MigrationDivergenceKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The record of what the last schema migration did.
 *
 * Spec 02 §9.10.4 requires a divergence to be surfaced to the user,
 * not silently corrected. The wording here is deliberately plain: it
 * says what happened and what was kept, and never says that anything
 * was fixed.
 */
data class MigrationNotesUiState(
    val hasRecords: Boolean = false,
    val duplicateEntries: List<String> = emptyList(),
    val cycleLengthDifferences: List<String> = emptyList(),
    val carriedOverSummary: String? = null,
)

internal object MigrationNotesTags {
    const val LIST = "migration_notes_list"
    const val EMPTY = "migration_notes_empty"
    const val DUPLICATES = "migration_notes_duplicates"
    const val CYCLE_LENGTHS = "migration_notes_cycle_lengths"
    const val CARRIED_OVER = "migration_notes_carried_over"
}

/**
 * Groups the records by kind.
 *
 * The wording of each record is written by the migration, because the
 * migration is the only thing that knows what it did. The screen does
 * not reinterpret it.
 */
internal fun buildMigrationNotesUiState(divergences: List<MigrationDivergence>): MigrationNotesUiState {
    val duplicateEntries = divergences
        .filter { it.kind == MigrationDivergenceKind.DuplicateSymptomRowCollapsed }
        .map { it.detail }
    val cycleLengthDifferences = divergences
        .filter { it.kind == MigrationDivergenceKind.CycleLengthDivergence }
        .map { it.detail }
    val carriedOverSummary = divergences
        .firstOrNull { it.kind == MigrationDivergenceKind.HistoryCarriedOver }
        ?.detail
    return MigrationNotesUiState(
        hasRecords = divergences.isNotEmpty(),
        duplicateEntries = duplicateEntries,
        cycleLengthDifferences = cycleLengthDifferences,
        carriedOverSummary = carriedOverSummary,
    )
}

class MigrationNotesViewModel(
    private val migrationDivergenceRepository: MigrationDivergenceRepository,
) : ViewModel() {
    val uiState: StateFlow<MigrationNotesUiState> = migrationDivergenceRepository.observeDivergences()
        .map { divergences -> buildMigrationNotesUiState(divergences) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MigrationNotesUiState())
}

class MigrationNotesViewModelFactory(
    private val migrationDivergenceRepository: MigrationDivergenceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MigrationNotesViewModel(migrationDivergenceRepository) as T
}

@Composable
fun MigrationNotesRoute(
    factory: MigrationNotesViewModelFactory,
) {
    val viewModel: MigrationNotesViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MigrationNotesScreen(uiState = uiState)
}

@Composable
internal fun MigrationNotesScreen(
    uiState: MigrationNotesUiState,
) {
    LazyColumn(
        modifier = Modifier
            .padding(CycleCareSpace.space16)
            .testTag(MigrationNotesTags.LIST),
        contentPadding = PaddingValues(bottom = CycleCareSpace.space24),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space16),
    ) {
        item {
            GradientHeaderCard(
                title = stringResource(R.string.migration_record_title),
                subtitle = stringResource(R.string.migration_record_subtitle),
            )
        }

        item {
            Text(
                text = stringResource(R.string.migration_record_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!uiState.hasRecords) {
            item {
                DashboardSectionCard(
                    title = stringResource(R.string.migration_record_empty_title),
                    subtitle = stringResource(R.string.migration_record_empty_body),
                    eyebrow = stringResource(R.string.migration_record_eyebrow),
                    modifier = Modifier.testTag(MigrationNotesTags.EMPTY),
                )
            }
        }

        if (uiState.duplicateEntries.isNotEmpty()) {
            item {
                DashboardSectionCard(
                    title = stringResource(R.string.migration_record_duplicates_title),
                    subtitle = pluralStringResource(
                        R.plurals.n_duplicate_entries,
                        uiState.duplicateEntries.size,
                        uiState.duplicateEntries.size,
                    ),
                    eyebrow = stringResource(R.string.migration_record_eyebrow),
                    modifier = Modifier.testTag(MigrationNotesTags.DUPLICATES),
                ) {
                    MigrationRecordList(uiState.duplicateEntries)
                }
            }
        }

        if (uiState.cycleLengthDifferences.isNotEmpty()) {
            item {
                DashboardSectionCard(
                    title = stringResource(R.string.migration_record_cycle_length_title),
                    subtitle = pluralStringResource(
                        R.plurals.n_cycle_length_differences,
                        uiState.cycleLengthDifferences.size,
                        uiState.cycleLengthDifferences.size,
                    ),
                    eyebrow = stringResource(R.string.migration_record_eyebrow),
                    modifier = Modifier.testTag(MigrationNotesTags.CYCLE_LENGTHS),
                ) {
                    MigrationRecordList(uiState.cycleLengthDifferences)
                }
            }
        }

        uiState.carriedOverSummary?.let { summary ->
            item {
                DashboardSectionCard(
                    title = stringResource(R.string.migration_record_carried_over_title),
                    subtitle = summary,
                    eyebrow = stringResource(R.string.migration_record_eyebrow),
                    modifier = Modifier.testTag(MigrationNotesTags.CARRIED_OVER),
                )
            }
        }
    }
}

@Composable
private fun MigrationRecordList(entries: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12),
    ) {
        entries.forEachIndexed { index, entry ->
            Text(
                text = entry,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (index != entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
