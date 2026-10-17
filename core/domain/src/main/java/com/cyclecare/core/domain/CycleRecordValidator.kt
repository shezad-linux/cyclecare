package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CycleValidationResult(
    val isValid: Boolean,
    val message: String? = null,
)

class CycleRecordValidator(
    private val validCycleLengthRange: IntRange = 20..45,
    private val validPeriodLengthRange: IntRange = 2..10,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) {
    fun validate(
        candidate: CycleRecord,
        existingRecords: List<CycleRecord>,
    ): CycleValidationResult {
        val today = todayProvider()

        if (candidate.startDate.isAfter(today)) {
            return CycleValidationResult(false, "Start date cannot be in the future.")
        }

        // endDate is null while a period is still in progress, which is a
        // valid state rather than an error. End-date and period-length
        // rules only apply once an end has actually been recorded.
        val endDate = candidate.endDate
        if (endDate != null) {
            if (endDate.isAfter(today)) {
                return CycleValidationResult(false, "End date cannot be in the future.")
            }
            if (endDate < candidate.startDate) {
                return CycleValidationResult(false, "End date must be on or after the start date.")
            }
            val periodLength = ChronoUnit.DAYS.between(candidate.startDate, endDate).toInt() + 1
            if (periodLength !in validPeriodLengthRange) {
                return CycleValidationResult(
                    false,
                    "Period length must stay between ${validPeriodLengthRange.first} and ${validPeriodLengthRange.last} days.",
                )
            }
        }

        // cycleLength is deprecated and is no longer range-checked: it is
        // never read as a cycle length now that the domain derives it
        // from consecutive starts, and stored values can legitimately
        // disagree with the true gap.

        val overlaps = existingRecords
            .asSequence()
            .filterNot { it.id == candidate.id }
            .any { existing ->
                val existingEnd = existing.endDate
                // An in-progress record runs to the present, so it
                // overlaps anything starting on or after its start.
                val overlapsEnded = existingEnd != null &&
                    candidate.startDate <= existingEnd &&
                    existing.startDate <= candidate.endDate
                val overlapsInProgress = existingEnd == null &&
                    candidate.startDate >= existing.startDate
                overlapsEnded || overlapsInProgress
            }
        if (overlaps) {
            return CycleValidationResult(false, "This period overlaps an existing cycle entry.")
        }

        return CycleValidationResult(true)
    }
}
