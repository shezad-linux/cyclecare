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

        if (candidate.endDate.isAfter(today)) {
            return CycleValidationResult(false, "End date cannot be in the future.")
        }

        if (candidate.endDate < candidate.startDate) {
            return CycleValidationResult(false, "End date must be on or after the start date.")
        }

        val periodLength = ChronoUnit.DAYS.between(candidate.startDate, candidate.endDate).toInt() + 1
        if (periodLength !in validPeriodLengthRange) {
            return CycleValidationResult(
                false,
                "Period length must stay between ${validPeriodLengthRange.first} and ${validPeriodLengthRange.last} days.",
            )
        }

        if (candidate.cycleLength !in validCycleLengthRange) {
            return CycleValidationResult(
                false,
                "Cycle length must stay between ${validCycleLengthRange.first} and ${validCycleLengthRange.last} days.",
            )
        }

        val overlaps = existingRecords
            .asSequence()
            .filterNot { it.id == candidate.id }
            .any { existing ->
                candidate.startDate <= existing.endDate && existing.startDate <= candidate.endDate
            }
        if (overlaps) {
            return CycleValidationResult(false, "This period overlaps an existing cycle entry.")
        }

        return CycleValidationResult(true)
    }
}
