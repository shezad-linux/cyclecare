package com.cyclecare.core.model

/**
 * What kind of divergence a migration recorded.
 *
 * Spec `02` §9.10.4 requires divergences to be logged and surfaced to
 * the user rather than silently corrected, so the kind is part of the
 * record rather than something the wording has to be parsed out of.
 */
enum class MigrationDivergenceKind {
    /**
     * Two entries were recorded for the same date and only the one
     * added later was kept.
     */
    DuplicateSymptomRowCollapsed,

    /**
     * The stored cycle length disagrees with the gap between the
     * recorded period starts.
     */
    CycleLengthDivergence,

    /**
     * The history was carried over from an earlier version of the app.
     */
    HistoryCarriedOver,

    /**
     * A record written by a version this build does not know about.
     * Its detail is still shown.
     */
    Unknown,
}

/**
 * One durable record of what a migration did.
 *
 * The record has no user-facing timestamp. The time on the stored row
 * is when the migration wrote the record, not when the user did
 * anything, and spec `02` §9.6 forbids presenting a synthetic time as
 * a real one, so the run is identified by the version pair instead.
 */
data class MigrationDivergence(
    val kind: MigrationDivergenceKind,
    /** The table the divergence was found in. */
    val tableName: String,
    /** The record the divergence belongs to, when it belongs to one. */
    val recordId: String?,
    /**
     * The day the record is about, when it is about a day.
     */
    val dateEpochDay: Long?,
    /** The value the source database held, before reconciliation. */
    val storedValue: Long?,
    /** The value the record carries now. */
    val observedValue: Long?,
    /**
     * Plain, factual wording. It never claims the data was corrected,
     * because it was not.
     */
    val detail: String,
    /** The schema version the database was migrated from. */
    val sourceVersion: Int,
    /** The schema version the database was migrated to. */
    val targetVersion: Int,
)
