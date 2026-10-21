package com.cyclecare.core.data

import com.cyclecare.core.database.MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE
import com.cyclecare.core.database.MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE
import com.cyclecare.core.database.MIGRATION_LOG_KIND_HISTORY_CARRIED_OVER
import com.cyclecare.core.database.MigrationLogEntity
import com.cyclecare.core.model.MigrationDivergenceKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spec `02` §9.10.4: divergences are logged and surfaced to the user,
 * never silently corrected.
 *
 * The migration writes the record; this is the half that carries it to
 * the user. The screen shows the wording the migration wrote, so
 * everything the user reads is decided by the mapping below, and the
 * mapping must not throw away the two values that make the record
 * meaningful: what the database held, and what it holds now.
 *
 * The kind is part of the record rather than something the screen has
 * to parse out of the wording, so a record written by a later build
 * still reaches the user: it is shown as an unknown kind rather than
 * dropped.
 */
class MigrationDivergenceMappingTest {

    @Test
    fun aCycleLengthDivergenceKeepsBothTheStoredAndTheObservedValue() {
        val model = log(
            kind = MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE,
            recordId = "jan",
            dateEpochDay = 128L,
            storedValue = 28L,
            observedValue = 40L,
        ).toModel()

        assertThat(model.kind).isEqualTo(MigrationDivergenceKind.CycleLengthDivergence)
        assertThat(model.tableName).isEqualTo("cycle_records")
        assertThat(model.recordId).isEqualTo("jan")
        assertThat(model.dateEpochDay).isEqualTo(128L)
        // The value the user's database held, and the value it holds
        // now. Neither is dropped, because a divergence that shows only
        // one of them is not a divergence the user can act on.
        assertThat(model.storedValue).isEqualTo(28L)
        assertThat(model.observedValue).isEqualTo(40L)
        assertThat(model.sourceVersion).isEqualTo(2)
        assertThat(model.targetVersion).isEqualTo(3)
    }

    @Test
    fun aCollapsedDuplicateKeepsTheCountThatWasCollapsed() {
        val model = log(
            kind = MIGRATION_LOG_KIND_DUPLICATE_SYMPTOM_DATE,
            recordId = null,
            dateEpochDay = 20456L,
            storedValue = 3L,
            observedValue = 1L,
        ).toModel()

        assertThat(model.kind).isEqualTo(MigrationDivergenceKind.DuplicateSymptomRowCollapsed)
        assertThat(model.tableName).isEqualTo("symptom_logs")
        // How many rows there were, and how many there are now.
        assertThat(model.storedValue).isEqualTo(3L)
        assertThat(model.observedValue).isEqualTo(1L)
    }

    @Test
    fun theCarriedOverSummaryIsItsOwnKind() {
        val model = log(
            kind = MIGRATION_LOG_KIND_HISTORY_CARRIED_OVER,
            recordId = null,
            dateEpochDay = null,
            storedValue = 2L,
            observedValue = 3L,
        ).toModel()

        assertThat(model.kind).isEqualTo(MigrationDivergenceKind.HistoryCarriedOver)
        assertThat(model.dateEpochDay).isNull()
    }

    @Test
    fun aRecordFromALaterBuildIsStillShown() {
        val model = log(
            kind = "SOMETHING_A_LATER_BUILD_WROTE",
            recordId = null,
            dateEpochDay = null,
            storedValue = null,
            observedValue = null,
            detail = "Something this build does not know about.",
        ).toModel()

        // An unrecognised kind is not an error and not a reason to
        // hide the record. The wording still reaches the user.
        assertThat(model.kind).isEqualTo(MigrationDivergenceKind.Unknown)
        assertThat(model.detail).isEqualTo("Something this build does not know about.")
    }

    @Test
    fun theWordingReachesTheUserUnchanged() {
        val detail = "Stored cycleLength 28 differs from the 40-day start-to-start gap " +
            "by 12 days. The gap is now authoritative; the original value is retained " +
            "in legacyCycleLength."

        val model = log(
            kind = MIGRATION_LOG_KIND_CYCLE_LENGTH_DIVERGENCE,
            recordId = "jan",
            dateEpochDay = 128L,
            storedValue = 28L,
            observedValue = 40L,
            detail = detail,
        ).toModel()

        // The screen shows what the migration wrote, so the wording
        // reaches the user exactly as it was written.
        assertThat(model.detail).isEqualTo(detail)
        // And it says what was kept, not that something was corrected.
        assertThat(model.detail).contains("legacyCycleLength")
        assertThat(model.detail.lowercase()).doesNotContain("corrected")
        assertThat(model.detail.lowercase()).doesNotContain("fixed")
    }

    private fun log(
        kind: String,
        recordId: String?,
        dateEpochDay: Long?,
        storedValue: Long?,
        observedValue: Long?,
        detail: String = "detail",
    ) = MigrationLogEntity(
        id = 1L,
        migrationFrom = 2,
        migrationTo = 3,
        kind = kind,
        recordId = recordId,
        dateEpochDay = dateEpochDay,
        storedValue = storedValue,
        observedValue = observedValue,
        detail = detail,
        createdAtEpochMillis = 1_700_000_000_000L,
        acknowledgedAtEpochMillis = null,
    )
}
