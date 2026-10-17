package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class CyclePredictionEngine {
    fun predict(
        profile: UserProfile,
        cycles: List<CycleRecord>,
        today: LocalDate = LocalDate.now(),
    ): PredictionSnapshot {
        val sortedCycles = cycles
            .filter { it.source != CycleSource.Predicted }
            .sortedBy { it.startDate }
        val anchorDate = sortedCycles.lastOrNull()?.startDate ?: profile.lastPeriodStart
        val rollingAverage = recentCycleAverage(sortedCycles) ?: profile.averageCycleLength
        val nextPeriodDate = anchorDate.plusDays(rollingAverage.toLong())
        val ovulationDate = nextPeriodDate.minusDays(14)
        val fertileWindow = DateRange(
            start = ovulationDate.minusDays(4),
            endInclusive = ovulationDate.plusDays(1),
        )
        val cycleDay = (today.toEpochDay() - anchorDate.toEpochDay()).toInt().coerceAtLeast(0) + 1
        val daysUntilNextPeriod = (nextPeriodDate.toEpochDay() - today.toEpochDay()).toInt()
        val confidenceLabel = confidenceLabel(sortedCycles, rollingAverage)

        return PredictionSnapshot(
            generatedAt = today,
            cycleDay = cycleDay,
            nextPeriodDate = nextPeriodDate,
            ovulationDate = ovulationDate,
            fertileWindow = fertileWindow,
            confidenceLabel = confidenceLabel,
            daysUntilNextPeriod = daysUntilNextPeriod,
        )
    }

    private fun recentCycleAverage(cycles: List<CycleRecord>): Int? {
        val lengths = cycles.zipWithNext { current, next ->
            (next.startDate.toEpochDay() - current.startDate.toEpochDay()).toInt()
        }.takeLast(6)

        if (lengths.isEmpty()) return null
        return lengths.average().roundToInt()
    }

    private fun confidenceLabel(cycles: List<CycleRecord>, cycleLength: Int): String {
        val sampleLengths = cycles.zipWithNext { current, next ->
            (next.startDate.toEpochDay() - current.startDate.toEpochDay()).toInt()
        }.takeLast(6)

        if (sampleLengths.size < 2) return "Baseline estimate"

        val averageVariance = sampleLengths
            .map { (it - cycleLength).absoluteValue }
            .average()

        return when {
            averageVariance <= 2.0 -> "High confidence estimate"
            averageVariance <= 5.0 -> "Moderate confidence estimate"
            else -> "Lower confidence estimate"
        }
    }
}
