package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.domain.prediction.IRREGULAR_MIN_OUTLIERS
import com.cyclecare.core.domain.prediction.MAD_TO_SIGMA_FACTOR
import com.cyclecare.core.domain.prediction.extractIntervals
import com.cyclecare.core.domain.prediction.isOutlier
import com.cyclecare.core.domain.prediction.observedStartsOf
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.InsightPhase
import com.cyclecare.core.model.InsightPhaseHighlight
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.SymptomLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class InsightEngine {
    fun build(cycles: List<CycleRecord>, symptoms: List<SymptomLog>): InsightSnapshot {
        val historicalCycles = cycles
            .filter { it.source != CycleSource.Predicted }
            .sortedBy { it.startDate }
            .takeLast(HISTORY_LIMIT)

        // Cycle lengths come from consecutive period STARTS via the same
        // extractor the prediction engine uses. Reading the stored
        // cycleLength column instead let this screen contradict the
        // prediction screen on the same day.
        // N cycles describe N-1 intervals, so the interval series needs
        // one extra cycle to fill the same window. Sourcing it from the
        // already-truncated list would silently shorten the trend series
        // relative to every other figure on the screen.
        val intervalSource = observedStartsOf(cycles).takeLast(HISTORY_LIMIT + 1)
        val recentCycleLengths = extractIntervals(intervalSource)
            .filter { it.usable }
            .takeLast(HISTORY_LIMIT)
            .map { it.lengthDays }
        val recentPeriodLengths = historicalCycles.mapNotNull(::periodLength)
        val averageCycleLength = recentCycleLengths.ifEmpty { listOf(DEFAULT_CYCLE_LENGTH) }.average().roundToInt()
        val averagePeriodLength = recentPeriodLengths.ifEmpty { listOf(DEFAULT_PERIOD_LENGTH) }.average().roundToInt()

        // Irregularity uses the same sigma-relative rule as the engine,
        // so a genuinely variable user is not labelled irregular merely
        // for varying.
        val centre = recentCycleLengths.average().takeIf { recentCycleLengths.isNotEmpty() }
        val spread = recentCycleLengths
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                values.map { abs(it - centre!!) }.average() * MAD_TO_SIGMA_FACTOR
            }
            ?: 0.0
        val irregularCycles = recentCycleLengths.count {
            isOutlier(abs(it - centre!!).roundToInt(), spread)
        } >= IRREGULAR_MIN_OUTLIERS
        val minCycleLength = recentCycleLengths.minOrNull() ?: averageCycleLength
        val maxCycleLength = recentCycleLengths.maxOrNull() ?: averageCycleLength
        val relevantSymptoms = relevantSymptomsForWindow(historicalCycles, symptoms)
        val hasEnoughCycleData = historicalCycles.size >= 2
        val hasEnoughSymptomData = relevantSymptoms.size >= 5

        return InsightSnapshot(
            averageCycleLength = averageCycleLength,
            averagePeriodLength = averagePeriodLength,
            irregularCycles = irregularCycles,
            recentCycleLengths = recentCycleLengths,
            recentPeriodLengths = recentPeriodLengths,
            minCycleLength = minCycleLength,
            maxCycleLength = maxCycleLength,
            cycleWindowLabel = buildCycleWindowLabel(historicalCycles),
            hasEnoughCycleData = hasEnoughCycleData,
            moodCounts = enumCounts(relevantSymptoms) { it.mood },
            energyCounts = enumCounts(relevantSymptoms) { it.energyLevel },
            painCounts = enumCounts(relevantSymptoms) { it.painLevel },
            dischargeCounts = enumCounts(relevantSymptoms) { it.dischargeType },
            phaseHighlights = buildPhaseHighlights(
                historicalCycles = historicalCycles,
                relevantSymptoms = relevantSymptoms,
                hasEnoughSymptomData = hasEnoughSymptomData,
            ),
            hasEnoughSymptomData = hasEnoughSymptomData,
            narrativeSummary = buildNarrativeSummary(
                historicalCycles = historicalCycles,
                relevantSymptoms = relevantSymptoms,
                averageCycleLength = averageCycleLength,
                averagePeriodLength = averagePeriodLength,
                minCycleLength = minCycleLength,
                maxCycleLength = maxCycleLength,
                hasEnoughCycleData = hasEnoughCycleData,
                hasEnoughSymptomData = hasEnoughSymptomData,
            ),
        )
    }

    private fun relevantSymptomsForWindow(
        historicalCycles: List<CycleRecord>,
        symptoms: List<SymptomLog>,
    ): List<SymptomLog> {
        if (historicalCycles.isEmpty()) return symptoms.sortedBy { it.date }

        val start = historicalCycles.first().startDate
        val end = historicalCycles.last().startDate.plusDays(historicalCycles.last().cycleLength.toLong() - 1L)
        return symptoms
            .filter { symptom -> !symptom.date.isBefore(start) && !symptom.date.isAfter(end) }
            .sortedBy { it.date }
    }

    private fun buildCycleWindowLabel(historicalCycles: List<CycleRecord>): String = when {
        historicalCycles.isEmpty() -> "Log two full cycles to unlock cycle trend charts."
        historicalCycles.size == 1 -> "Based on 1 logged cycle from ${monthYear(historicalCycles.single().startDate)}."
        else -> {
            val first = monthYear(historicalCycles.first().startDate)
            val last = monthYear(historicalCycles.last().startDate)
            "Based on ${historicalCycles.size} logged cycles from $first to $last."
        }
    }

    private fun buildPhaseHighlights(
        historicalCycles: List<CycleRecord>,
        relevantSymptoms: List<SymptomLog>,
        hasEnoughSymptomData: Boolean,
    ): List<InsightPhaseHighlight> {
        if (!hasEnoughSymptomData || historicalCycles.isEmpty()) return emptyList()

        return listOfNotNull(
            buildPeriodHighlight(historicalCycles, relevantSymptoms),
            buildFertileWindowHighlight(historicalCycles, relevantSymptoms),
        )
    }

    private fun buildPeriodHighlight(
        historicalCycles: List<CycleRecord>,
        relevantSymptoms: List<SymptomLog>,
    ): InsightPhaseHighlight? {
        val periodSymptoms = relevantSymptoms.filter { symptom ->
            historicalCycles.any { cycle ->
                !symptom.date.isBefore(cycle.startDate) && !symptom.date.isAfter(cycle.endDate)
            }
        }
        if (periodSymptoms.isEmpty()) return null

        val strongestPain = periodSymptoms
            .groupingBy { it.painLevel }
            .eachCount()
            .filterKeys { it != PainLevel.None }
            .maxByOrNull { it.value }
            ?.key
        val lowestEnergy = periodSymptoms
            .groupingBy { it.energyLevel }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val mood = periodSymptoms
            .groupingBy { it.mood }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return InsightPhaseHighlight(
            phase = InsightPhase.Period,
            title = "Period phase pattern",
            description = buildString {
                append("Logged ${periodSymptoms.size} entries during period days. ")
                when {
                    strongestPain != null && lowestEnergy != null -> {
                        append("${strongestPain.label()} pain and ${lowestEnergy.label().lowercase(Locale.US)} energy show up most often.")
                    }
                    strongestPain != null -> append("${strongestPain.label()} pain shows up most often.")
                    lowestEnergy != null -> append("${lowestEnergy.label()} energy appears most often.")
                    mood != null -> append("${mood.label()} mood appears most often.")
                }
            },
        )
    }

    private fun buildFertileWindowHighlight(
        historicalCycles: List<CycleRecord>,
        relevantSymptoms: List<SymptomLog>,
    ): InsightPhaseHighlight? {
        val fertileSymptoms = relevantSymptoms.filter { symptom ->
            historicalCycles.any { cycle ->
                val fertileWindow = fertileWindowFor(cycle)
                !symptom.date.isBefore(fertileWindow.first) && !symptom.date.isAfter(fertileWindow.second)
            }
        }
        if (fertileSymptoms.isEmpty()) return null

        val discharge = fertileSymptoms
            .groupingBy { it.dischargeType }
            .eachCount()
            .filterKeys { it != DischargeType.None }
            .maxByOrNull { it.value }
            ?.key
        val mood = fertileSymptoms
            .groupingBy { it.mood }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val energy = fertileSymptoms
            .groupingBy { it.energyLevel }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return InsightPhaseHighlight(
            phase = InsightPhase.FertileWindow,
            title = "Fertile window pattern",
            description = buildString {
                append("Logged ${fertileSymptoms.size} entries near fertile-window days. ")
                when {
                    discharge != null && energy != null -> {
                        append("${discharge.label()} discharge and ${energy.label().lowercase(Locale.US)} energy appear most often.")
                    }
                    discharge != null -> append("${discharge.label()} discharge appears most often.")
                    mood != null -> append("${mood.label()} mood appears most often.")
                    energy != null -> append("${energy.label()} energy appears most often.")
                }
            },
        )
    }

    private fun buildNarrativeSummary(
        historicalCycles: List<CycleRecord>,
        relevantSymptoms: List<SymptomLog>,
        averageCycleLength: Int,
        averagePeriodLength: Int,
        minCycleLength: Int,
        maxCycleLength: Int,
        hasEnoughCycleData: Boolean,
        hasEnoughSymptomData: Boolean,
    ): String {
        if (historicalCycles.isEmpty() && relevantSymptoms.isEmpty()) {
            return "Log cycles and symptoms to unlock trend charts, phase-aware callouts, and stronger pattern summaries."
        }
        if (!hasEnoughCycleData && !hasEnoughSymptomData) {
            return "Your local timeline is still building. Keep logging cycles and symptoms to unlock trend charts and phase-based pattern insights."
        }
        if (!hasEnoughCycleData) {
            return "Symptom logging is building useful pattern depth. Log at least two full cycles to unlock cycle trend charts and variability tracking."
        }
        if (!hasEnoughSymptomData) {
            return "Recent cycles average $averageCycleLength days with about $averagePeriodLength days of bleeding. Keep logging symptoms to reveal phase-based pattern callouts."
        }

        val cycleRangeText = if (minCycleLength == maxCycleLength) {
            "$averageCycleLength-day cycles"
        } else {
            "$minCycleLength to $maxCycleLength day cycles"
        }
        val mostCommonMood = enumCounts(relevantSymptoms) { it.mood }
            .maxByOrNull { it.value }
            ?.key
            ?: Mood.Stable
        val strongestPain = enumCounts(relevantSymptoms) { it.painLevel }
            .filterKeys { it != PainLevel.None }
            .maxByOrNull { it.value }
            ?.key

        return buildString {
            append("Recent history shows $cycleRangeText across ${historicalCycles.size} logged cycles.")
            append(" ${mostCommonMood.label()} mood shows up most often")
            strongestPain?.let { pain ->
                append(", with ${pain.label().lowercase(Locale.US)} pain appearing most frequently in symptom logs")
            }
            append(".")
        }
    }

    /** Null while a period is still in progress. */
    private fun periodLength(cycle: CycleRecord): Int? = cycle.periodLengthDays

    private fun fertileWindowFor(cycle: CycleRecord): Pair<LocalDate, LocalDate> {
        val ovulationDate = cycle.startDate.plusDays(cycle.cycleLength.toLong() - 14L)
        return ovulationDate.minusDays(4) to ovulationDate.plusDays(1)
    }

    private fun monthYear(date: LocalDate): String = date.format(monthFormatter)

    /**
     * Counts how often each tracked value appears.
     *
     * Untracked entries (null) are excluded rather than defaulted.
     * Defaulting would count a day the user never rated as though they
     * had reported that value.
     */
    private inline fun <reified T : Enum<T>> enumCounts(
        symptoms: List<SymptomLog>,
        selector: (SymptomLog) -> T?,
    ): Map<T, Int> = enumValues<T>().associateWith { value ->
        symptoms.count { selector(it) == value }
    }

    private fun Mood.label(): String = when (this) {
        Mood.Stable -> "Stable"
        Mood.Happy -> "Happy"
        Mood.Low -> "Low"
        Mood.Irritable -> "Irritable"
        Mood.Anxious -> "Anxious"
    }

    private fun PainLevel.label(): String = when (this) {
        PainLevel.None -> "No"
        PainLevel.Light -> "Light"
        PainLevel.Moderate -> "Moderate"
        PainLevel.Strong -> "Strong"
    }

    private fun EnergyLevel.label(): String = when (this) {
        EnergyLevel.Low -> "Low"
        EnergyLevel.Steady -> "Steady"
        EnergyLevel.High -> "High"
    }

    private fun DischargeType.label(): String = when (this) {
        DischargeType.None -> "No"
        DischargeType.Dry -> "Dry"
        DischargeType.Creamy -> "Creamy"
        DischargeType.Sticky -> "Sticky"
        DischargeType.EggWhite -> "Egg white"
    }

    private companion object {
        const val HISTORY_LIMIT = 6
        const val DEFAULT_CYCLE_LENGTH = 28
        const val DEFAULT_PERIOD_LENGTH = 5
        val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM uuuu", Locale.US)
    }
}
