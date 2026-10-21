package com.cyclecare.feature.calendar

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

/**
 * The history list takes its cycle lengths from the derived interval.
 *
 * Spec `02` §9.4.5: application code never reads the stored
 * `cycleLength`. The history list is a place where a user reads a
 * cycle length as a fact about their own body, so an unvalidated stored
 * value there is the worst place for one to appear.
 *
 * An interval is the gap from a cycle's start to the next one. The most
 * recent cycle has no next start yet, so its length is not known, and
 * the list says so rather than guessing.
 */
class CalendarHistoryIntervalTest {

    private fun cycle(id: String, start: LocalDate, storedLength: Int) = CycleRecord(
        id = id,
        startDate = start,
        endDate = start.plusDays(4),
        cycleLength = storedLength,
        source = CycleSource.User,
        notes = null,
    )

    @Test
    fun consecutiveStartsGiveTheDerivedLength() {
        val items = buildHistoryItems(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 28),
                cycle("newer", LocalDate.parse("2026-03-11"), 28),
            ),
            Locale.US,
        )

        val older = items.first { it.id == "older" }
        assertThat(older.cycleLengthLabel).isEqualTo(
            com.cyclecare.core.designsystem.text.UiText.Plural(
                R.plurals.history_cycle_length,
                28,
                listOf(28),
            ),
        )
    }

    @Test
    fun aMissingStartMeansNoDerivedLength() {
        // Only one cycle: there is no next start, so there is no gap to
        // measure.
        val items = buildHistoryItems(
            listOf(cycle("only", LocalDate.parse("2026-03-11"), 28)),
            Locale.US,
        )

        assertThat(items.single().cycleLengthLabel).isEqualTo(
            com.cyclecare.core.designsystem.text.UiText.Resource(
                R.string.history_cycle_length_unknown,
            ),
        )
    }

    @Test
    fun anInconsistentStoredValueDoesNotChangeTheLabel() {
        val honest = buildHistoryItems(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 28),
                cycle("newer", LocalDate.parse("2026-03-11"), 28),
            ),
            Locale.US,
        )
        val inconsistent = buildHistoryItems(
            listOf(
                cycle("older", LocalDate.parse("2026-02-11"), 91),
                cycle("newer", LocalDate.parse("2026-03-11"), 91),
            ),
            Locale.US,
        )

        assertThat(inconsistent.map { it.cycleLengthLabel })
            .isEqualTo(honest.map { it.cycleLengthLabel })
        assertThat(inconsistent.first { it.id == "older" }.cycleLengthLabel.toString())
            .contains("28")
    }

    @Test
    fun aGapThatIsNotAPlausibleCycleIsNotOfferedAsALength() {
        // A 200-day gap is not a cycle length. The extractor excludes
        // it, so the list says the length is not known rather than
        // printing 200.
        val items = buildHistoryItems(
            listOf(
                cycle("older", LocalDate.parse("2025-09-01"), 28),
                cycle("newer", LocalDate.parse("2026-03-11"), 28),
            ),
            Locale.US,
        )

        assertThat(items.first { it.id == "older" }.cycleLengthLabel).isEqualTo(
            com.cyclecare.core.designsystem.text.UiText.Resource(
                R.string.history_cycle_length_unknown,
            ),
        )
    }
}
