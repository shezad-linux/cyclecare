package com.cyclecare.core.domain.notification

import com.cyclecare.core.domain.prediction.CyclePrediction
import com.cyclecare.core.domain.prediction.CyclePhase
import com.cyclecare.core.domain.prediction.EstimateQuality
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class NotificationTimingTest {
    private fun prediction(
        nextPeriod: String,
        halfWidth: Int?,
        generatedFor: String = "2026-10-01",
        quality: EstimateQuality =
            if (halfWidth == null) EstimateQuality.INSUFFICIENT_HISTORY else EstimateQuality.MODERATE,
    ) = CyclePrediction(
        algorithmVersion = "2.0.0",
        generatedForDate = LocalDate.parse(generatedFor),
        anchorRecordId = "anchor",
        anchorDate = LocalDate.parse("2026-09-25"),
        recordIdsUsed = listOf("a", "b", "c", "d"),
        intervalsUsed = emptyList(),
        intervalsExcluded = emptyList(),
        centerCycleLength = 28.0,
        dispersionSigma = 2.0,
        trendAdjustmentDays = 0.0,
        isIrregular = false,
        isStale = false,
        quality = quality,
        nextPeriodDate = LocalDate.parse(nextPeriod),
        nextPeriodWindow = halfWidth?.let {
            com.cyclecare.core.domain.prediction.DateWindow(
                start = LocalDate.parse(nextPeriod).minusDays(it.toLong()),
                endInclusive = LocalDate.parse(nextPeriod).plusDays(it.toLong()),
                halfWidthDays = it,
            )
        },
        ovulationDate = LocalDate.parse(nextPeriod).minusDays(14),
        fertileWindow = halfWidth?.let {
            com.cyclecare.core.domain.prediction.DateWindow(
                start = LocalDate.parse(nextPeriod).minusDays(18),
                endInclusive = LocalDate.parse(nextPeriod).minusDays(13),
                halfWidthDays = it,
            )
        },
        cycleDay = 7,
        daysUntilNextPeriod = 23,
        overdueDays = 0,
        phase = com.cyclecare.core.domain.prediction.PhaseEstimate(CyclePhase.FOLLICULAR, true),
        warnings = emptyList(),
    )

    @Test
    fun timingAnchorsToTheEarliestPlausibleStartNotTheCentre() {
        val p = prediction(nextPeriod = "2026-10-24", halfWidth = 3)

        assertThat(earliestPlausibleStart(p)).isEqualTo(LocalDate.parse("2026-10-21"))
    }

    @Test
    fun aNarrowRangeAnchorMatchesTheCentreDate() {
        val p = prediction(nextPeriod = "2026-10-24", halfWidth = 1)

        assertThat(earliestPlausibleStart(p)).isEqualTo(LocalDate.parse("2026-10-23"))
    }

    @Test
    fun aWideRangeFiresEarlierBecauseTheEstimateIsLessCertain() {
        val certain = prediction(nextPeriod = "2026-10-24", halfWidth = 1)
        val uncertain = prediction(nextPeriod = "2026-10-24", halfWidth = 6)

        assertThat(earliestPlausibleStart(uncertain) < earliestPlausibleStart(certain)).isTrue()
    }

    @Test
    fun leadDaysAreMeasuredFromTheGeneratedDate() {
        val p = prediction(nextPeriod = "2026-10-24", halfWidth = 3, generatedFor = "2026-10-01")

        // earliest plausible start is Oct 21; a 2-day lead targets Oct 19
        assertThat(leadDaysFor(p, requestedLeadDays = 2)).isEqualTo(18)
    }

    @Test
    fun noWindowMeansNoReminderRatherThanAFabricatedDate() {
        val p = prediction(nextPeriod = "2026-10-24", halfWidth = null)

        assertThat(leadDaysFor(p, requestedLeadDays = 2)).isNull()
    }

    @Test
    fun latePeriodDoesNotFireBeforeTheGracePeriod() {
        val p = prediction(nextPeriod = "2026-10-01", halfWidth = 2, generatedFor = "2026-10-04")

        // 3 days after Oct 1, grace is max(3, 2) = 3, so Oct 4 is not after Oct 4
        assertThat(isLatePeriodCandidate(p, loggedStartAfterAnchor = false)).isFalse()
    }

    @Test
    fun latePeriodFiresOnceTheGracePeriodPasses() {
        val p = prediction(nextPeriod = "2026-10-01", halfWidth = 2, generatedFor = "2026-10-06")

        assertThat(isLatePeriodCandidate(p, loggedStartAfterAnchor = false)).isTrue()
    }

    @Test
    fun aWideEstimateDelaysTheLatePeriodAccusation() {
        // grace is max(3, halfWidth), so the certain case escalates from
        // Oct 5 and the uncertain case from Oct 8. Oct 6 sits between them.
        val certain = prediction(nextPeriod = "2026-10-01", halfWidth = 1, generatedFor = "2026-10-06")
        val uncertain = prediction(nextPeriod = "2026-10-01", halfWidth = 6, generatedFor = "2026-10-06")

        assertThat(isLatePeriodCandidate(certain, loggedStartAfterAnchor = false)).isTrue()
        assertThat(isLatePeriodCandidate(uncertain, loggedStartAfterAnchor = false)).isFalse()
    }

    @Test
    fun loggingAStartCancelsTheLatePeriodReminderImmediately() {
        val p = prediction(nextPeriod = "2026-10-01", halfWidth = 2, generatedFor = "2026-10-06")

        assertThat(isLatePeriodCandidate(p, loggedStartAfterAnchor = false)).isTrue()
        assertThat(isLatePeriodCandidate(p, loggedStartAfterAnchor = true)).isFalse()
    }

    @Test
    fun latePeriodNeverFiresWithoutAnEstimate() {
        val p = prediction(nextPeriod = "2026-10-01", halfWidth = null, generatedFor = "2026-10-20")

        assertThat(isLatePeriodCandidate(p, loggedStartAfterAnchor = false)).isFalse()
    }
}