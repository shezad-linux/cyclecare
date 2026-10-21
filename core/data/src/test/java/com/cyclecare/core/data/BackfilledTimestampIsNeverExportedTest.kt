package com.cyclecare.core.data

import com.cyclecare.core.database.SymptomLogEntity
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.SymptomLog
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * Spec `02` §9.10.6: backfilled timestamps are never presented as real
 * creation dates.
 *
 * v2 had no audit columns, so the migration had to invent them. The
 * value it wrote is the migration time, which is not when the user
 * recorded anything.
 *
 * There are two ways for the invented value to reach a user: through
 * the model the screens read, and through the exported report. Both are
 * checked here. The other half of the guarantee — that no module
 * outside the persistence layer reads the audit columns at all — is
 * enforced by `AuditTimestampReadGuardTest`.
 */
class BackfilledTimestampIsNeverExportedTest {

    @Test
    fun theBackfilledTimestampDoesNotReachTheModelTheScreensRead() {
        val backfilled = 1_700_000_000_000L
        val entity = SymptomLogEntity(
            id = "log-a",
            dateEpochDay = LocalDate.parse("2026-03-11").toEpochDay(),
            mood = "Stable",
            painLevel = "None",
            energyLevel = "Steady",
            dischargeType = "None",
            medication = null,
            notes = null,
            createdAtEpochMillis = backfilled,
            updatedAtEpochMillis = backfilled,
            deletedAtEpochMillis = null,
            createdAtIsBackfilled = true,
        )

        val model = entity.toModel(NoOpAppLogger)

        // The model the screens read has no creation date in it at all,
        // so there is nothing there to show as one.
        assertThat(model.toString()).doesNotContain(backfilled.toString())
        assertThat(model.date).isEqualTo(LocalDate.parse("2026-03-11"))
    }

    @Test
    fun theReportShowsThePeriodTheUserRecordedAndNoCreationDate() {
        val markdown = buildReportContent(
            generatedAt = LocalDate.parse("2026-03-16"),
            profile = null,
            cycles = listOf(
                CycleRecord(
                    id = "older",
                    startDate = LocalDate.parse("2026-02-11"),
                    endDate = LocalDate.parse("2026-02-15"),
                    cycleLength = 28,
                    source = CycleSource.User,
                    notes = null,
                ),
                CycleRecord(
                    id = "newer",
                    startDate = LocalDate.parse("2026-03-11"),
                    endDate = LocalDate.parse("2026-03-15"),
                    cycleLength = 28,
                    source = CycleSource.User,
                    notes = null,
                ),
            ),
            symptoms = listOf(
                SymptomLog(
                    id = "log-a",
                    date = LocalDate.parse("2026-03-11"),
                    mood = com.cyclecare.core.model.Mood.Stable,
                    painLevel = com.cyclecare.core.model.PainLevel.None,
                    energyLevel = com.cyclecare.core.model.EnergyLevel.Steady,
                    dischargeType = com.cyclecare.core.model.DischargeType.None,
                    medication = null,
                    notes = null,
                ),
            ),
            insights = insights,
        ).markdown

        // The period the user recorded is in the report.
        assertThat(markdown).contains("2026-03-11 to 2026-03-15")
        // And nothing in it claims to know when the record was created.
        val lower = markdown.lowercase()
        assertThat(lower).doesNotContain("logged on")
        assertThat(lower).doesNotContain("recorded on")
        assertThat(lower).doesNotContain("created on")
        assertThat(lower).doesNotContain("created at")
    }

    private val insights = InsightSnapshot(
        averageCycleLength = 28,
        averagePeriodLength = 5,
        irregularCycles = false,
        recentCycleLengths = listOf(28, 28),
        recentPeriodLengths = listOf(5, 5),
        minCycleLength = 28,
        maxCycleLength = 28,
        cycleWindowLabel = "Based on 2 logged cycles.",
        hasEnoughCycleData = true,
        moodCounts = emptyMap(),
        energyCounts = emptyMap(),
        painCounts = emptyMap(),
        dischargeCounts = emptyMap(),
        phaseHighlights = emptyList(),
        hasEnoughSymptomData = false,
        narrativeSummary = "",
    )
}
