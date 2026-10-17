package com.cyclecare.core.domain.prediction

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The single definition of a cycle interval, shared by the prediction
 * engine and the insight engine.
 *
 * These two previously derived interval lengths independently, from
 * different sources: the predictor used the gap between consecutive
 * period starts, while insights used the stored `cycleLength` column.
 * Phase 3 deprecated that column because it is written at logging time
 * from whatever the user last reported and can drift arbitrarily far
 * from reality. The result was a single screen stating two different
 * average cycle lengths for the same user on the same day.
 *
 * Both now read the same gaps through this function.
 */

/** Observed starts in chronological order, excluding engine predictions. */
fun observedStartsOf(cycles: List<CycleRecord>): List<ObservedCycleStart> = cycles
    .filter { it.source != CycleSource.Predicted }
    .sortedBy { it.startDate }
    .map { record ->
        ObservedCycleStart(
            id = record.id,
            startDate = record.startDate,
            endDate = record.endDate,
            source = record.source,
        )
    }

/**
 * Start-to-start gaps between consecutive observed records.
 *
 * Cycle length is always the distance between two period STARTS. A
 * period's own span (`endDate - startDate`) is period length and must
 * never be substituted for it.
 *
 * Input is assumed sorted; callers should use [observedStartsOf] or sort
 * explicitly.
 */
fun extractIntervals(observed: List<ObservedCycleStart>): List<IntervalEstimate> =
    observed.sortedBy { it.startDate }.zipWithNext { from, to ->
        val length = ChronoUnit.DAYS.between(from.startDate, to.startDate).toInt()
        val reason = when {
            length < MIN_PLAUSIBLE_INTERVAL_DAYS -> "shorter than $MIN_PLAUSIBLE_INTERVAL_DAYS days"
            length > MAX_PLAUSIBLE_INTERVAL_DAYS -> "longer than $MAX_PLAUSIBLE_INTERVAL_DAYS days"
            else -> null
        }
        IntervalEstimate(
            fromRecordId = from.id,
            toRecordId = to.id,
            startDate = from.startDate,
            lengthDays = length,
            usable = reason == null,
            exclusionReason = reason,
        )
    }

/** Convenience for callers that only want the plausible interval lengths. */
fun usableIntervalLengths(cycles: List<CycleRecord>, limit: Int = MAX_INTERVALS): List<Int> =
    extractIntervals(observedStartsOf(cycles))
        .filter { it.usable }
        .takeLast(limit)
        .map { it.lengthDays }

/** Days between two dates, used where a plain integer gap is wanted. */
fun daysBetween(from: LocalDate, to: LocalDate): Int =
    ChronoUnit.DAYS.between(from, to).toInt()