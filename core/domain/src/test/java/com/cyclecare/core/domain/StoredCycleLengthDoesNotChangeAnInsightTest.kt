package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.domain.prediction.derivedIntervalsByRecordId
import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The stored cycle length cannot change an interval-derived insight.
 *
 * Spec `02` §9.4.5: "Application code reads intervals through the shared
 * extractor in `11` §7 and **never** reads `cycleLength`." The stored
 * column is written at logging time from whatever the user last
 * reported, so a record can carry a number that has nothing to do with
 * the real gap between period starts.
 *
 * These tests hold that promise at the level it matters: the same
 * history, with the stored column changed to an arbitrary value, must
 * produce exactly the same insight.
 */
class StoredCycleLengthDoesNotChangeAnInsightTest {

    private val engine = InsightEngine()

    private fun cycle(id: String, start: LocalDate, storedLength: Int): CycleRecord =
        CycleRecord(
            id = id,
            startDate = start,
            endDate = start.plusDays(4),
            cycleLength = storedLength,
            source = CycleSource.User,
            notes = null,
        )

    private fun history(storedLength: Int): List<CycleRecord> = listOf(
        cycle("c1", LocalDate.parse("2026-01-05"), storedLength),
        cycle("c2", LocalDate.parse("2026-02-02"), storedLength),
        cycle("c3", LocalDate.parse("2026-03-02"), storedLength),
    )

    @Test
    fun anArbitraryStoredLengthDoesNotChangeTheAverageCycleLength() {
        val honest = engine.build(history(28), emptyList())
        val absurd = engine.build(history(365), emptyList())
        val wrong = engine.build(history(3), emptyList())

        assertThat(absurd.averageCycleLength).isEqualTo(honest.averageCycleLength)
        assertThat(wrong.averageCycleLength).isEqualTo(honest.averageCycleLength)
        // The derived figure is the real gap: 28 days.
        assertThat(honest.averageCycleLength).isEqualTo(28)
    }

    @Test
    fun anArbitraryStoredLengthDoesNotChangeTheRecentCycleLengths() {
        val honest = engine.build(history(28), emptyList())
        val absurd = engine.build(history(365), emptyList())

        assertThat(absurd.recentCycleLengths).isEqualTo(honest.recentCycleLengths)
        assertThat(absurd.minCycleLength).isEqualTo(honest.minCycleLength)
        assertThat(absurd.maxCycleLength).isEqualTo(honest.maxCycleLength)
    }

    @Test
    fun anArbitraryStoredLengthDoesNotChangeTheIrregularityVerdict() {
        val honest = engine.build(history(28), emptyList())
        val absurd = engine.build(history(365), emptyList())

        assertThat(absurd.irregularCycles).isEqualTo(honest.irregularCycles)
    }

    @Test
    fun theDerivedIntervalComesFromConsecutiveStarts() {
        val intervals = derivedIntervalsByRecordId(history(999))

        // The real gaps are 28 and 28 days. The stored value of 999
        // is not among them.
        assertThat(intervals).containsExactly("c1", 28, "c2", 28)
    }

    @Test
    fun aRecordWithNoSuccessorHasNoDerivedInterval() {
        val intervals = derivedIntervalsByRecordId(history(28))

        // The most recent cycle has no next start yet, so its interval
        // is not known. The shared extractor says so rather than
        // guessing.
        assertThat(intervals).doesNotContainKey("c3")
    }
}
