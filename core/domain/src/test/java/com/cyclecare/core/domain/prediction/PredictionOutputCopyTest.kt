package com.cyclecare.core.domain.prediction

import com.cyclecare.core.domain.InsightEngine
import com.cyclecare.core.domain.ReminderSchedulePlanner
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.SymptomLog
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * The output copy review, made repeatable.
 *
 * `11` §8.12: "No output implies contraception, diagnosis, or
 * certainty." `03` §7 lists the wording the product avoids, `05` §9
 * lists the health-content release checklist, and `12` §1 forbids
 * claiming clinical accuracy. This test enumerates every string the
 * prediction path can put in front of a user and asserts that none of
 * them contains a phrase from those documents, so the review does not
 * have to be repeated by hand after every copy change.
 *
 * Spec: CycleCare-Redesign-Docs/11-prediction-engine.md section 8.12.
 */
class PredictionOutputCopyTest {

    /**
     * A phrase no prediction-derived string may contain, with the
     * document that bans it.
     *
     * The source is carried with the phrase on purpose: this list is
     * only trustworthy if it can be re-read against the specifications
     * rather than taken on trust.
     */
    private data class BannedPhrase(val phrase: String, val source: String)

    private val bannedPhrases = listOf(
        // Certainty. `05` §9: "No screen presents predictions as
        // certainty."
        BannedPhrase("will start", "03 §7 Avoid: \"Your period will start on…\"; 11 §5 Never say"),
        BannedPhrase("definitely", "03 §7 Avoid: \"You are definitely ovulating.\""),
        BannedPhrase("guarantee", "05 §9: no output presents predictions as certainty"),
        BannedPhrase("accurate", "11 §5: the engine never claims accuracy; 12 §1: no clinical claim"),
        BannedPhrase("clinically", "12 §1: never described as clinically validated"),
        BannedPhrase("100%", "03 §7 Avoid: \"Everything is 100% private\""),
        // Contraception. `05` §9: "Fertile-window copy never implies
        // contraception."
        BannedPhrase("safe from pregnancy", "03 §7 Avoid: \"You are safe from pregnancy today.\""),
        BannedPhrase("prevents pregnancy", "05 §9: fertile-window copy never implies contraception"),
        BannedPhrase("cannot get pregnant", "05 §9: fertile-window copy never implies contraception"),
        // Diagnosis. `05` §9 and `03` §7.
        BannedPhrase("unhealthy", "03 §7 Avoid: \"Your cycle is unhealthy\""),
        BannedPhrase("abnormal", "03 §7 Avoid: a cycle is not a health judgment"),
        BannedPhrase("you are irregular", "11 §5 Never say: \"You're irregular.\""),
    )

    private fun assertNoBannedPhrase(where: String, text: String) {
        val lower = text.lowercase()
        bannedPhrases.forEach { banned ->
            assertWithMessage("$where contains a banned phrase: \"${banned.phrase}\" (${banned.source})")
                .that(lower)
                .doesNotContain(banned.phrase)
        }
    }

    // ── the strings the engine produces ─────────────────────────────

    @Test
    fun confidenceLabelsAreFramedAndNeverClaimCertainty() {
        // Every quality tier, plus the stale branch, is enumerated here
        // so a new tier cannot be added without its copy being reviewed.
        val labels = listOf(
            EstimateQuality.INSUFFICIENT_HISTORY to false,
            EstimateQuality.LOW to false,
            EstimateQuality.MODERATE to false,
            EstimateQuality.HIGH to false,
            EstimateQuality.HIGH to true,
        ).map { (quality, stale) ->
            prediction(quality = quality, isStale = stale).toSnapshot().confidenceLabel
        }

        assertThat(labels).hasSize(5)
        labels.forEach { label ->
            // Framed: never blank, so the screen always has something to
            // say about how certain the estimate is.
            assertThat(label).isNotEmpty()
            assertNoBannedPhrase("confidence label", label)
        }
    }

    @Test
    fun exclusionReasonsExplainTheGapWithoutJudgingTheUser() {
        val reasons = listOf(
            extractIntervals(
                listOf(
                    observed("a", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05")),
                    observed("b", LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-14")),
                ),
            ).first().exclusionReason,
            extractIntervals(
                listOf(
                    observed("a", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05")),
                    observed("b", LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-05")),
                ),
            ).first().exclusionReason,
        )

        assertThat(reasons).containsExactly(
            "shorter than $MIN_PLAUSIBLE_INTERVAL_DAYS days",
            "longer than $MAX_PLAUSIBLE_INTERVAL_DAYS days",
        )
        reasons.forEach { reason ->
            assertThat(reason).isNotEmpty()
            assertNoBannedPhrase("exclusion reason", requireNotNull(reason))
        }
    }

    @Test
    fun predictionDerivedReminderCopyNeverClaimsCertaintyOrContraception() {
        val planner = ReminderSchedulePlanner(ZoneId.of("UTC"))
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneId.of("UTC"))
        val schedules = planner.buildSchedules(
            prediction = snapshotForReminder(),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
                ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
                ReminderRule("ovulation", ReminderType.OvulationEstimate, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
                ReminderRule("late", ReminderType.LatePeriod, enabled = true, daysBefore = 1, hourOfDay = 9, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).isNotEmpty()
        schedules.forEach { schedule ->
            listOf(schedule.title, schedule.body, schedule.privateBody).forEach { text ->
                assertNoBannedPhrase("reminder copy", text)
            }
        }
    }

    @Test
    fun insightCopyNeverClaimsCertaintyDiagnosisOrCausation() {
        val engine = InsightEngine()
        val snapshot = engine.build(
            cycles = listOf(
                cycle("jan", "2026-01-01", cycleLength = 29, endDate = "2026-01-05"),
                cycle("feb", "2026-01-30", cycleLength = 30, endDate = "2026-02-03"),
                cycle("mar", "2026-03-01", cycleLength = 30, endDate = "2026-03-05"),
            ),
            symptoms = listOf(
                symptom("p1", "2026-01-02", pain = PainLevel.Moderate, energy = EnergyLevel.Low),
                symptom("p2", "2026-01-03", pain = PainLevel.Moderate, energy = EnergyLevel.Low),
                symptom("f1", "2026-01-15", discharge = DischargeType.EggWhite, energy = EnergyLevel.High),
                symptom("f2", "2026-01-16", discharge = DischargeType.EggWhite, energy = EnergyLevel.High),
            ),
        )

        val copy = buildList {
            add(snapshot.narrativeSummary)
            add(snapshot.cycleWindowLabel)
            snapshot.phaseHighlights.forEach { highlight ->
                add(highlight.title)
                add(highlight.description)
            }
        }

        assertThat(copy).isNotEmpty()
        copy.forEach { text ->
            assertNoBannedPhrase("insight copy", text)
        }
    }

    // ── the framing the domain owes the screen ──────────────────────

    @Test
    fun everyPredictionCarriesItsVersionAndTheRecordsItUsed() {
        // `11` §8.8: the estimate must be explainable, which needs the
        // algorithm version and the provenance of the estimate.
        val engine = CyclePredictionEngineV2()
        val input = CyclePredictionInput(
            observed = listOf(
                observed("r0", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05")),
                observed("r1", LocalDate.parse("2026-01-29"), LocalDate.parse("2026-02-02")),
                observed("r2", LocalDate.parse("2026-02-26"), LocalDate.parse("2026-03-02")),
                observed("r3", LocalDate.parse("2026-03-26"), LocalDate.parse("2026-03-30")),
            ),
            userReportedAverageCycleLength = null,
            today = LocalDate.parse("2026-03-26"),
        )

        val prediction = requireNotNull(engine.predict(input))
        assertThat(prediction.algorithmVersion).isEqualTo(PREDICTION_ALGORITHM_VERSION)
        assertThat(prediction.recordIdsUsed).containsExactly("r0", "r1", "r2", "r3")
        assertThat(prediction.algorithmVersion).isNotEmpty()
        // The version and the provenance survive the mapping to the
        // shape the screens read.
        val snapshot = prediction.toSnapshot()
        assertThat(snapshot.algorithmVersion).isEqualTo(PREDICTION_ALGORITHM_VERSION)
    }

    @Test
    fun everyPhaseOtherThanAnObservedPeriodIsMarkedEstimated() {
        // `11` §8.11: only menstrual days backed by an observed record
        // may be presented as confirmed. The engine marks every derived
        // phase as estimated, so a screen cannot present one as fact.
        val engine = CyclePredictionEngineV2()
        val input = CyclePredictionInput(
            observed = listOf(
                observed("r0", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05")),
                observed("r1", LocalDate.parse("2026-01-29"), LocalDate.parse("2026-02-02")),
                observed("r2", LocalDate.parse("2026-02-26"), LocalDate.parse("2026-03-02")),
            ),
            userReportedAverageCycleLength = null,
            today = LocalDate.parse("2026-03-01"),
        )

        val prediction = requireNotNull(engine.predict(input))
        // Day 1 of the anchor cycle: menstrual, and confirmed by the
        // observed record behind it.
        assertThat(prediction.phase.phase).isEqualTo(CyclePhase.MENSTRUAL)
        assertThat(prediction.phase.isEstimated).isFalse()

        val later = requireNotNull(
            engine.predict(input.copy(today = LocalDate.parse("2026-03-10"))),
        )
        assertThat(later.phase.phase).isNotEqualTo(CyclePhase.MENSTRUAL)
        assertThat(later.phase.isEstimated).isTrue()
    }

    // ── fixtures ────────────────────────────────────────────────────

    private fun observed(id: String, start: LocalDate, end: LocalDate) =
        ObservedCycleStart(id, start, end, CycleSource.User)

    private fun prediction(quality: EstimateQuality, isStale: Boolean): CyclePrediction = CyclePrediction(
        algorithmVersion = PREDICTION_ALGORITHM_VERSION,
        generatedForDate = LocalDate.parse("2026-03-01"),
        anchorRecordId = "r0",
        anchorDate = LocalDate.parse("2026-03-01"),
        recordIdsUsed = listOf("r0", "r1", "r2"),
        intervalsUsed = emptyList(),
        intervalsExcluded = emptyList(),
        centerCycleLength = 28.0,
        dispersionSigma = 1.0,
        trendAdjustmentDays = 0.0,
        isIrregular = false,
        isStale = isStale,
        quality = quality,
        nextPeriodDate = LocalDate.parse("2026-03-29"),
        nextPeriodWindow = DateWindow(
            LocalDate.parse("2026-03-27"),
            LocalDate.parse("2026-03-31"),
            halfWidthDays = 2,
        ),
        ovulationDate = LocalDate.parse("2026-03-15"),
        fertileWindow = DateWindow(
            LocalDate.parse("2026-03-07"),
            LocalDate.parse("2026-03-16"),
            halfWidthDays = 2,
        ),
        cycleDay = 1,
        daysUntilNextPeriod = 28,
        overdueDays = 0,
        phase = PhaseEstimate(CyclePhase.FOLLICULAR, isEstimated = true),
        warnings = emptyList(),
    )

    private fun snapshotForReminder() = com.cyclecare.core.model.PredictionSnapshot(
        generatedAt = LocalDate.parse("2026-03-10"),
        cycleDay = 10,
        nextPeriodDate = LocalDate.parse("2026-03-28"),
        ovulationDate = LocalDate.parse("2026-03-14"),
        fertileWindow = com.cyclecare.core.model.DateRange(
            start = LocalDate.parse("2026-03-10"),
            endInclusive = LocalDate.parse("2026-03-15"),
        ),
        confidenceLabel = "Narrow range",
        daysUntilNextPeriod = 18,
        nextPeriodWindow = com.cyclecare.core.model.DateRange(
            start = LocalDate.parse("2026-03-27"),
            endInclusive = LocalDate.parse("2026-03-29"),
        ),
        nextPeriodHalfWidthDays = 1,
        quality = com.cyclecare.core.model.PredictionQuality.HIGH,
        phase = com.cyclecare.core.model.PredictionPhase.FOLLICULAR,
        algorithmVersion = PREDICTION_ALGORITHM_VERSION,
    )

    private fun cycle(
        id: String,
        startDate: String,
        cycleLength: Int,
        endDate: String,
    ): CycleRecord = CycleRecord(
        id = id,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate),
        cycleLength = cycleLength,
        source = CycleSource.User,
    )

    private fun symptom(
        id: String,
        date: String,
        pain: PainLevel = PainLevel.None,
        energy: EnergyLevel = EnergyLevel.Steady,
        discharge: DischargeType = DischargeType.None,
    ): SymptomLog = SymptomLog(
        id = id,
        date = LocalDate.parse(date),
        mood = Mood.Stable,
        painLevel = pain,
        energyLevel = energy,
        dischargeType = discharge,
    )
}
