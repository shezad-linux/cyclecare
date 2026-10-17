package com.cyclecare.core.domain.prediction

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class PredictionMathTest {
    // ── sigma ───────────────────────────────────────────────────────

    @Test
    fun sigmaFromMad_appliesTheNormalConsistencyConstant() {
        assertThat(sigmaFromMad(1.0)).isWithin(1e-9).of(MAD_TO_SIGMA_FACTOR)
    }

    @Test
    fun inflateForSampleSize_decaysAsEvidenceAccumulates() {
        val sigma = 2.0

        val atThree = inflateForSampleSize(sigma, 3)
        val atSix = inflateForSampleSize(sigma, 6)
        val atTwelve = inflateForSampleSize(sigma, 12)

        assertThat(atThree).isGreaterThan(atSix)
        assertThat(atSix).isGreaterThan(atTwelve)
        assertThat(atThree).isWithin(1e-9).of(3.0)
        assertThat(atSix).isWithin(1e-9).of(2.5)
        assertThat(atTwelve).isWithin(1e-9).of(2.25)
    }

    @Test
    fun totalSigma_doublesForIrregularHistory() {
        // 2.0 * (1 + 1.5/8) = 2.375
        assertThat(totalSigma(2.0, 8, isIrregular = false)).isWithin(1e-9).of(2.375)
        assertThat(totalSigma(2.0, 8, isIrregular = true)).isWithin(1e-9).of(4.75)
    }

    // ── half width ──────────────────────────────────────────────────

    @Test
    fun halfWidth_neverGoesBelowOneDay() {
        assertThat(halfWidthFor(0.0)).isEqualTo(MIN_HALF_WIDTH_DAYS)
        assertThat(halfWidthFor(0.01)).isEqualTo(MIN_HALF_WIDTH_DAYS)
    }

    @Test
    fun halfWidth_neverExceedsSevenDays() {
        assertThat(halfWidthFor(1000.0)).isEqualTo(MAX_HALF_WIDTH_DAYS)
    }

    @Test
    fun halfWidth_isAlwaysWithinTheDocumentedBounds() {
        listOf(0.0, 0.5, 1.0, 2.0, 5.0, 12.0, 500.0).forEach { sigma ->
            val width = halfWidthFor(sigma)
            assertThat(width).isAtLeast(MIN_HALF_WIDTH_DAYS)
            assertThat(width).isAtMost(MAX_HALF_WIDTH_DAYS)
        }
    }

    @Test
    fun halfWidth_ofPerfectlyRegularHistoryCollapsesToTheFloor() {
        assertThat(halfWidthFor(0.0)).isEqualTo(1)
    }

    // ── outlier ─────────────────────────────────────────────────────

    @Test
    fun outlier_requiresBothAnAbsoluteFloorAndTheSigmaThreshold() {
        assertThat(isOutlier(deviationDays = 6, sigma = 1.0)).isFalse()
        assertThat(isOutlier(deviationDays = 9, sigma = 1.0)).isTrue()
    }

    @Test
    fun outlier_adaptsToWideUserVariability() {
        assertThat(isOutlier(deviationDays = 12, sigma = 6.0)).isFalse()
        assertThat(isOutlier(deviationDays = 25, sigma = 6.0)).isTrue()
    }

    // ── irregularity ────────────────────────────────────────────────

    @Test
    fun oneOutlierAloneIsNotIrregular() {
        assertThat(isIrregular(outlierCount = 1, usableIntervalCount = 6, hasImpossibleInterval = false)).isFalse()
    }

    @Test
    fun twoOutliersAmongSixMakeItIrregular() {
        assertThat(isIrregular(outlierCount = 2, usableIntervalCount = 6, hasImpossibleInterval = false)).isTrue()
    }

    @Test
    fun twoOutliersDoNotCountWhenThereAreTooFewIntervals() {
        assertThat(isIrregular(outlierCount = 2, usableIntervalCount = 3, hasImpossibleInterval = false)).isFalse()
    }

    @Test
    fun anImpossibleIntervalForcesIrregularRegardlessOfCount() {
        assertThat(isIrregular(outlierCount = 0, usableIntervalCount = 6, hasImpossibleInterval = true)).isTrue()
    }

    // ── staleness ───────────────────────────────────────────────────

    @Test
    fun historyIsStaleBeyondOneAndAHalfCycles() {
        val anchor = LocalDate.parse("2026-01-01")

        // threshold is 1.5 * 28 = 42 days
        assertThat(isStale(anchor, LocalDate.parse("2026-02-01"), 28.0)).isFalse()
        assertThat(isStale(anchor, LocalDate.parse("2026-02-12"), 28.0)).isFalse()
        assertThat(isStale(anchor, LocalDate.parse("2026-02-13"), 28.0)).isTrue()
    }

    // ── quality ─────────────────────────────────────────────────────

    @Test
    fun fewerThanThreeIntervalsIsInsufficientHistory() {
        assertThat(qualityFor(0, 0.0, false, false)).isEqualTo(EstimateQuality.INSUFFICIENT_HISTORY)
        assertThat(qualityFor(2, 0.1, false, false)).isEqualTo(EstimateQuality.INSUFFICIENT_HISTORY)
    }

    @Test
    fun deepRegularHistoryIsHighQuality() {
        assertThat(qualityFor(8, 1.5, false, false)).isEqualTo(EstimateQuality.HIGH)
    }

    @Test
    fun enoughIntervalsButWideDispersionIsLow() {
        assertThat(qualityFor(8, 5.0, false, false)).isEqualTo(EstimateQuality.LOW)
    }

    @Test
    fun shallowHistoryIsNeverModerateOrHigh() {
        assertThat(qualityFor(3, 1.0, false, false)).isEqualTo(EstimateQuality.LOW)
        assertThat(qualityFor(4, 1.0, false, false)).isEqualTo(EstimateQuality.LOW)
    }

    @Test
    fun midDepthRegularHistoryIsModerate() {
        assertThat(qualityFor(6, 2.0, false, false)).isEqualTo(EstimateQuality.MODERATE)
    }

    @Test
    fun irregularOrStaleHistoryIsDowngradedRegardlessOfDispersion() {
        assertThat(qualityFor(10, 1.0, isIrregular = true, isStale = false)).isEqualTo(EstimateQuality.LOW)
        assertThat(qualityFor(10, 1.0, isIrregular = false, isStale = true)).isEqualTo(EstimateQuality.LOW)
    }

    // ── dates ───────────────────────────────────────────────────────

    @Test
    fun ovulationIsDerivedFromTheLutealPrior() {
        assertThat(ovulationFrom(LocalDate.parse("2026-10-24"))).isEqualTo(LocalDate.parse("2026-10-10"))
    }

    @Test
    fun cycleDayIsOneOnTheAnchorDate() {
        val anchor = LocalDate.parse("2026-03-01")
        assertThat(cycleDayFor(anchor, anchor)).isEqualTo(1)
        assertThat(cycleDayFor(anchor, LocalDate.parse("2026-03-08"))).isEqualTo(8)
    }

    @Test
    fun cycleDayNeverDropsBelowOneForAFutureDatedAnchor() {
        assertThat(cycleDayFor(LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-01"))).isEqualTo(1)
    }

    @Test
    fun daysUntilGoesNegativeWhenOverdueAndOverdueDaysIsBounded() {
        val predicted = LocalDate.parse("2026-03-01")

        assertThat(daysUntilFor(LocalDate.parse("2026-03-05"), predicted)).isEqualTo(-4)
        assertThat(overdueDaysFor(LocalDate.parse("2026-03-05"), predicted)).isEqualTo(4)
    }

    @Test
    fun overdueDaysIsZeroWhenNotYetDue() {
        assertThat(overdueDaysFor(LocalDate.parse("2026-02-27"), LocalDate.parse("2026-03-01"))).isEqualTo(0)
    }
}