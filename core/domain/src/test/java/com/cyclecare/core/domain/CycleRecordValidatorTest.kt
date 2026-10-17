package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class CycleRecordValidatorTest {
    private val validator = CycleRecordValidator()

    @Test
    fun validate_rejectsOverlappingCycles() {
        val existing = listOf(
            CycleRecord(
                id = "existing",
                startDate = LocalDate.parse("2026-03-01"),
                endDate = LocalDate.parse("2026-03-05"),
                cycleLength = 28,
                source = CycleSource.User,
            ),
        )

        val result = validator.validate(
            candidate = CycleRecord(
                id = "candidate",
                startDate = LocalDate.parse("2026-03-04"),
                endDate = LocalDate.parse("2026-03-08"),
                cycleLength = 28,
                source = CycleSource.User,
            ),
            existingRecords = existing,
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("overlaps")
    }

    @Test
    fun validate_rejectsPeriodLengthsOutsideExpectedRange() {
        val result = validator.validate(
            candidate = CycleRecord(
                id = "candidate",
                startDate = LocalDate.parse("2026-03-01"),
                endDate = LocalDate.parse("2026-03-14"),
                cycleLength = 28,
                source = CycleSource.User,
            ),
            existingRecords = emptyList(),
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("Period length")
    }

    @Test
    fun validate_rejectsCycleLengthsOutsideExpectedRange() {
        val result = validator.validate(
            candidate = CycleRecord(
                id = "candidate",
                startDate = LocalDate.parse("2026-03-01"),
                endDate = LocalDate.parse("2026-03-05"),
                cycleLength = 19,
                source = CycleSource.User,
            ),
            existingRecords = emptyList(),
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("Cycle length")
    }

    @Test
    fun validate_rejectsFutureDates() {
        val validator = CycleRecordValidator(todayProvider = { LocalDate.parse("2026-03-16") })

        val result = validator.validate(
            candidate = CycleRecord(
                id = "candidate",
                startDate = LocalDate.parse("2026-03-17"),
                endDate = LocalDate.parse("2026-03-20"),
                cycleLength = 28,
                source = CycleSource.User,
            ),
            existingRecords = emptyList(),
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.message).contains("future")
    }

    @Test
    fun validate_allowsEditingExistingRecordWithoutSelfOverlap() {
        val existing = listOf(
            CycleRecord(
                id = "same-id",
                startDate = LocalDate.parse("2026-03-01"),
                endDate = LocalDate.parse("2026-03-05"),
                cycleLength = 28,
                source = CycleSource.User,
            ),
        )

        val result = validator.validate(
            candidate = existing.single().copy(endDate = LocalDate.parse("2026-03-06")),
            existingRecords = existing,
        )

        assertThat(result.isValid).isTrue()
        assertThat(result.message).isNull()
    }
}
