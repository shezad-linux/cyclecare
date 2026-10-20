package com.cyclecare.feature.settings

import com.cyclecare.core.model.MigrationDivergence
import com.cyclecare.core.model.MigrationDivergenceKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The migration record screen has to say what the migration did, and
 * nothing else. It never claims that anything was corrected, because
 * nothing was.
 */
class MigrationNotesUiStateTest {

    @Test
    fun noRecords_produceAnEmptyState() {
        val state = buildMigrationNotesUiState(emptyList())

        assertThat(state.hasRecords).isFalse()
        assertThat(state.duplicateEntries).isEmpty()
        assertThat(state.cycleLengthDifferences).isEmpty()
        assertThat(state.carriedOverSummary).isNull()
    }

    @Test
    fun recordsAreGroupedByKind() {
        val state = buildMigrationNotesUiState(
            listOf(
                divergence(
                    kind = MigrationDivergenceKind.DuplicateSymptomRowCollapsed,
                    detail = "Two entries were recorded for 2026-03-14.",
                ),
                divergence(
                    kind = MigrationDivergenceKind.CycleLengthDivergence,
                    detail = "The period starting 2026-03-12 was recorded with a 21 day cycle length.",
                ),
                divergence(
                    kind = MigrationDivergenceKind.HistoryCarriedOver,
                    detail = "12 cycle records were carried over from an earlier version of CycleCare.",
                ),
            ),
        )

        assertThat(state.hasRecords).isTrue()
        assertThat(state.duplicateEntries).containsExactly(
            "Two entries were recorded for 2026-03-14.",
        )
        assertThat(state.cycleLengthDifferences).containsExactly(
            "The period starting 2026-03-12 was recorded with a 21 day cycle length.",
        )
        assertThat(state.carriedOverSummary).isEqualTo(
            "12 cycle records were carried over from an earlier version of CycleCare.",
        )
    }

    @Test
    fun aRecordOfAnUnknownKindIsStillSurfaced() {
        val state = buildMigrationNotesUiState(
            listOf(
                divergence(
                    kind = MigrationDivergenceKind.Unknown,
                    detail = "Something this build does not recognise.",
                ),
            ),
        )

        // It is counted, so the entry into the record is offered, and
        // the wording is passed through rather than dropped.
        assertThat(state.hasRecords).isTrue()
    }

    private fun divergence(
        kind: MigrationDivergenceKind,
        detail: String,
    ) = MigrationDivergence(
        kind = kind,
        tableName = "cycle_records",
        recordId = "record-1",
        dateEpochDay = null,
        storedValue = null,
        observedValue = null,
        detail = detail,
        sourceVersion = 2,
        targetVersion = 3,
    )
}
