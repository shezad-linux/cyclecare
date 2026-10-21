package com.cyclecare.core.data

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.InsightSnapshot
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The exported report takes its cycle lengths from the derived interval.
 *
 * Spec `02` §9.4.5: application code never reads the stored
 * `cycleLength`. The report is where that matters most, because it is
 * the one place the app states a cycle length as a fact and a reader
 * takes it away.
 *
 * The report also carries an average cycle length of its own, so a
 * stored value that disagreed with the derived one would have put two
 * different figures in the same document.
 */
class ExportDerivedIntervalTest {

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

    private fun cycle(id: String, start: LocalDate, storedLength: Int) = CycleRecord(
        id = id,
        startDate = start,
        endDate = start.plusDays(4),
        cycleLength = storedLength,
        source = CycleSource.User,
        notes = null,
    )

    private fun report(cycles: List<CycleRecord>): String =
        buildReportContent(
            generatedAt = LocalDate.parse("2026-03-16"),
            profile = null,
            cycles = cycles,
            symptoms = emptyList(),
            insights = insights,
        ).markdown

    @Test
    fun consecutiveStartsGiveTheDerivedLength() {
        val markdown = report(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 28),
                cycle("newer", LocalDate.parse("2026-03-11"), 28),
            ),
        )

        assertThat(markdown).contains("28 day cycle")
    }

    @Test
    fun anInconsistentStoredValueDoesNotReachTheReport() {
        val honest = report(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 28),
                cycle("newer", LocalDate.parse("2026-03-11"), 28),
            ),
        )
        val inconsistent = report(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 91),
                cycle("newer", LocalDate.parse("2026-03-11"), 91),
            ),
        )

        // The same history produces the same report, whatever the
        // stored column says.
        assertThat(inconsistent).isEqualTo(honest)
        assertThat(inconsistent).doesNotContain("91 day cycle")
    }

    @Test
    fun theMostRecentCycleWithNoNextStartIsReportedAsUnknown() {
        val markdown = report(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 28),
                cycle("newer", LocalDate.parse("2026-03-11"), 28),
            ),
        )

        // The older cycle's interval is the real gap. The newer one has
        // no next start yet, so its length is not known, and the report
        // says so rather than printing an unvalidated number.
        assertThat(markdown).contains("cycle length not known yet")
        assertThat(markdown).doesNotContain("28 day cycle) \n")
    }

    @Test
    fun aHistoryWithOneCycleReportsNoLengthAtAll() {
        val markdown = report(listOf(cycle("only", LocalDate.parse("2026-03-11"), 28)))

        assertThat(markdown).contains("cycle length not known yet")
        assertThat(markdown).doesNotContain("28 day cycle")
    }
}
