package com.cyclecare.feature.home

import com.cyclecare.core.domain.VALID_CYCLE_LENGTH_RANGE
import com.cyclecare.core.domain.prediction.EstimateQuality
import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.domain.prediction.DateWindow
import com.cyclecare.core.domain.CycleRecordValidator
import com.cyclecare.core.model.InsightSnapshot
import com.cyclecare.core.domain.prediction.SamplePrediction
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.cyclecare.core.designsystem.text.resolve
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class HomePresentationTest {
    @Test
    fun fertileWindow_isFramedAsAnEstimate() {
        val range = DateWindow(
            start = LocalDate.of(2026, 3, 21),
            endInclusive = LocalDate.of(2026, 3, 26),
            halfWidthDays = 3,
        )

        // Spec 11 §5: a fertile window always reads as an estimate.
        assertThat(formatFertileWindow(range, supportsRange = true)).isEqualTo(
            uiText(R.string.estimated_fertile_days, "Mar 21", "Mar 26"),
        )
    }

    @Test
    fun fertileWindow_isAbsentWhenTheEngineProducedNone() {
        // The snapshot always carries a range: when the engine declined,
        // the mapper fills the field in with a presentation fallback.
        // Showing that would put a fertile range on the screen that the
        // engine never generated.
        val range = DateWindow(
            start = LocalDate.of(2026, 3, 19),
            endInclusive = LocalDate.of(2026, 3, 24),
            halfWidthDays = 3,
        )

        assertThat(formatFertileWindow(range, supportsRange = false)).isEqualTo(
            uiText(R.string.not_enough_history_for_fertile_days),
        )
        assertThat(formatFertileWindow(null, supportsRange = false)).isEqualTo(
            uiText(R.string.not_enough_history_for_fertile_days),
        )
    }

    @Test
    fun averageCycleLength_isTheCanonicalObservedAverage() {
        // Insights renders `insights.averageCycleLength`. Today used to
        // render the onboarding baseline, so the two screens could
        // disagree about the user's cycle on the same day.
        val insights = InsightSnapshot(
            averageCycleLength = 31,
            averagePeriodLength = 5,
            irregularCycles = false,
            recentCycleLengths = listOf(30, 31, 32),
            recentPeriodLengths = listOf(5),
            minCycleLength = 30,
            maxCycleLength = 32,
            cycleWindowLabel = "Based on 3 logged cycles.",
            hasEnoughCycleData = true,
            moodCounts = emptyMap(),
            energyCounts = emptyMap(),
            painCounts = emptyMap(),
            dischargeCounts = emptyMap(),
            phaseHighlights = emptyList(),
            hasEnoughSymptomData = false,
            narrativeSummary = "Recent history shows 30 to 32 day cycles.",
        )
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = LocalDate.of(2026, 3, 11),
            trackingGoal = TrackingGoal.PeriodPrep,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )

        assertThat(resolveAverageCycleLength(insights, profile)).isEqualTo(31)
    }

    @Test
    fun averageCycleLength_fallsBackToTheProfileThenToTwentyEight() {
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = LocalDate.of(2026, 3, 11),
            trackingGoal = TrackingGoal.PeriodPrep,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )

        assertThat(resolveAverageCycleLength(insights = null, profile = profile)).isEqualTo(28)
        assertThat(resolveAverageCycleLength(insights = null, profile = null)).isEqualTo(28)
    }

    @Test
    fun cyclePhaseLabel_takesTheFertileWindowFromTheEngine() {
        // The engine derives the window from the ovulation estimate and
        // the dispersion. A fraction of the cycle cannot reproduce it,
        // so the screen reads the engine's own window.
        val today = LocalDate.of(2026, 3, 16)
        val prediction = SamplePrediction.of(
            generatedForDate = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateWindow(start = today.minusDays(1), endInclusive = today.plusDays(3), halfWidthDays = 3),
            quality = EstimateQuality.MODERATE,
            daysUntilNextPeriod = 22,
            nextPeriodWindow = DateWindow(start = today.plusDays(20), endInclusive = today.plusDays(24), halfWidthDays = 3),
        )

        assertThat(
            buildCyclePhaseLabel(
                cycleDay = 6,
                cycleLength = 28,
                prediction = prediction,
                today = today,
            ),
        ).isEqualTo(uiText(R.string.fertile_window))
    }

    @Test
    fun cyclePhaseLabel_doesNotClaimAFertileWindowTheEngineNeverProduced() {
        // Cycle day 14 of 28 is inside the old 0.40-0.60 band, so this
        // used to read "Fertile window". The engine declined to produce
        // a window here, so the screen must not invent one.
        val today = LocalDate.of(2026, 3, 16)
        val prediction = SamplePrediction.of(
            generatedForDate = today,
            cycleDay = 14,
            nextPeriodDate = today.plusDays(14),
            ovulationDate = today.plusDays(1),
            fertileWindow = DateWindow(start = today.minusDays(4), endInclusive = today.plusDays(1), halfWidthDays = 3),
            quality = EstimateQuality.MODERATE,
            daysUntilNextPeriod = 14,
            // The engine declined to produce a range, so there is no
            // next-period window and `supportsRange` is false. That is
            // what the screen must respect.
            nextPeriodWindow = null,
        )

        assertThat(
            buildCyclePhaseLabel(
                cycleDay = 14,
                cycleLength = 28,
                prediction = prediction,
                today = today,
            ),
        ).isNotEqualTo(uiText(R.string.fertile_window))
    }

    @Test
    fun fertileWindow_usesNaturalDashboardDateFormatting() {
        val range = DateWindow(
            start = LocalDate.of(2026, 3, 21),
            endInclusive = LocalDate.of(2026, 3, 26),
            halfWidthDays = 3,
        )

        assertThat(formatFertileWindow(range, supportsRange = true)).isEqualTo(
            uiText(R.string.estimated_fertile_days, "Mar 21", "Mar 26"),
        )
    }

    @Test
    fun cycleProgressLabel_keepsDayContextAndCarriesNoConfidence() {
        // The confidence is its own element with its own resource. It
        // used to be an argument of this sentence, which meant a
        // translator had to carry a fragment of the engine's English
        // prose inside a translated string.
        assertThat(
            formatCycleProgressLabel(
                cycleDay = 6,
                cycleLength = 28,
            ),
        ).isEqualTo(uiText(R.string.day_slash_total, "6", 28))
    }

    @Test
    fun theCycleLengthRangeIsTheDomainsAndTheBoundariesAreAccepted() {
        // The range is the domain's rule, in one place. The editor uses
        // it, so the two cannot disagree.
        assertThat(VALID_CYCLE_LENGTH_RANGE).isEqualTo(20..45)

        val validator = com.cyclecare.core.domain.CycleRecordValidator(
            todayProvider = { LocalDate.of(2026, 3, 16) },
        )
        fun errorFor(length: Int) = validateHomeEditorState(
            editor = HomeEditorState(
                startDateText = "2026-03-01",
                endDateText = "2026-03-05",
                cycleLengthText = length.toString(),
            ),
            cycles = emptyList(),
            validator = validator,
            today = LocalDate.of(2026, 3, 16),
        ).cycleLengthError

        listOf(20, 45).forEach { length ->
            assertThat(errorFor(length)).isNull()
        }
        listOf(19, 46).forEach { length ->
            assertThat(errorFor(length)).isNotNull()
        }
    }

    @Test
    fun everyQualityTierHasItsOwnConfidenceResource() {
        val resources = EstimateQuality.entries.map { quality ->
            confidenceLabelResource(quality = quality, isStale = false)
        }
        assertThat(resources).containsNoDuplicates()
        assertThat(resources).hasSize(EstimateQuality.entries.size)
    }

    @Test
    fun aStalePredictionSaysSoRatherThanClaimingANarrowRange() {
        val stale = confidenceLabelResource(quality = EstimateQuality.HIGH, isStale = true)
        val fresh = confidenceLabelResource(quality = EstimateQuality.HIGH, isStale = false)
        assertThat(stale).isNotEqualTo(fresh)
    }

    @Test
    fun heroCopy_usesPeriodStartedCopyDuringCurrentCycle() {
        val state = HomeUiState(
            hasCurrentCycle = true,
            daysUntilNextPeriod = 21,
            nextPeriodDateText = UiText.Raw("Apr 6"),
        )

        assertThat(homeHeroTitle(state)).isEqualTo(uiText(R.string.period_started))
        assertThat(homeHeroSubtitle(state)).isEqualTo(uiText(R.string.log_how_today_feels))
    }

    @Test
    fun heroCopy_keepsPredictionCopyOutsideCurrentCycle() {
        val state = HomeUiState(
            hasCurrentCycle = false,
            daysUntilNextPeriod = 3,
            nextPeriodDateText = UiText.Raw("Mar 19"),
            // Declared explicitly because supportsRange defaults to
            // false: a caller that omits it gets the honest "no range"
            // copy rather than a bare date presented as expected.
            supportsRange = true,
        )

        assertThat(homeHeroTitle(state)).isEqualTo(pluralUiText(R.plurals.next_period_in_n_days, 3, 3))
        assertThat(homeHeroSubtitle(state)).isEqualTo(uiText(R.string.expected_date, UiText.Raw("Mar 19")))
    }

    @Test
    fun cyclePhaseLabel_usesPeriodDaysDuringCurrentCycle() {
        val today = LocalDate.of(2026, 3, 16)
        val prediction = SamplePrediction.of(
            generatedForDate = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateWindow(start = today.plusDays(7), endInclusive = today.plusDays(12), halfWidthDays = 3),
            quality = EstimateQuality.MODERATE,
            daysUntilNextPeriod = 22,
        )
        val currentCycle = CycleRecord(
            id = "active-cycle",
            startDate = today.minusDays(5),
            endDate = today,
            cycleLength = 28,
            source = CycleSource.User,
        )

        assertThat(
            buildHomeCyclePhaseLabel(
                currentCycle = currentCycle,
                prediction = prediction,
                cycleLength = 28,
            ),
        ).isEqualTo(uiText(R.string.period_days))
    }

    @Test
    fun progressStateDescription_isUserFacing() {
        assertThat(progressStateDescription(cycleDay = 6, cycleLength = 28)).isEqualTo(uiText(R.string.day_x_of_y, 6, 28))
        assertThat(progressStateDescription(cycleDay = null, cycleLength = 28)).isEqualTo(uiText(R.string.cycle_progress_unavailable))
    }

    @Test
    fun defaultEditorState_usesSavedProfileBaseline() {
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = LocalDate.of(2026, 3, 11),
            trackingGoal = TrackingGoal.PeriodPrep,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )

        val editor = defaultEditorState(profile, LocalDate.of(2026, 3, 16))

        assertThat(editor.isVisible).isTrue()
        assertThat(editor.isExpanded).isFalse()
        assertThat(editor.startDateText).isEqualTo("2026-03-16")
        assertThat(editor.endDateText).isEqualTo("2026-03-20")
        assertThat(editor.cycleLengthText).isEqualTo("28")
    }

    @Test
    fun validateHomeEditorState_rejectsFutureStartDate() {
        val validated = validateHomeEditorState(
            editor = HomeEditorState(
                isVisible = true,
                isExpanded = true,
                startDateText = LocalDate.now().plusDays(1).toString(),
                endDateText = LocalDate.now().toString(),
                cycleLengthText = "28",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        assertThat(validated.startDateError).isEqualTo(uiText(R.string.start_date_cannot_be_future))
        assertThat(validated.isSaveEnabled).isFalse()
    }

    @Test
    fun validateHomeEditorState_rejectsBlankCycleLength() {
        val validated = validateHomeEditorState(
            editor = HomeEditorState(
                isVisible = true,
                isExpanded = true,
                startDateText = "2026-03-10",
                endDateText = "2026-03-14",
                cycleLengthText = "",
            ),
            cycles = emptyList(),
            validator = CycleRecordValidator(),
        )

        assertThat(validated.cycleLengthError).isEqualTo(uiText(R.string.cycle_length_required))
        assertThat(validated.isSaveEnabled).isFalse()
    }

    @Test
    fun nextPeriodRange_rendersBothEndsAndTheHalfWidth() {
        val text = formatNextPeriodRange(
            range = DateWindow(
                start = LocalDate.of(2026, 4, 7),
                endInclusive = LocalDate.of(2026, 4, 11),
                halfWidthDays = 2,
            ),
            supportsRange = true,
            halfWidthDays = 2,
        )

        assertThat(text).isNotNull()
        assertThat(text).isEqualTo(uiText(R.string.date_range_with_halfwidth, "Apr 7", "Apr 11", 2))
    }

    @Test
    fun nextPeriodRange_isNullWhenTheEngineSupportsNoRange() {
        // Distinguishing "no range" from "empty range" is the whole
        // point: a caller that conflates them prints a bare date and the
        // overstatement returns.
        val text = formatNextPeriodRange(range = null, supportsRange = false, halfWidthDays = null)

        assertThat(text).isNotNull()
        assertThat(text).isEqualTo(uiText(R.string.not_enough_history_yet_for_a_range))
    }

    @Test
    fun heroSubtitle_doesNotPresentAPointEstimateAsExpectedWithoutARange() {
        val state = HomeUiState(
            nextPeriodDateText = UiText.Raw("Apr 9"),
            supportsRange = false,
            hasCurrentCycle = false,
        )

        assertThat(homeHeroSubtitle(state)).isNotEqualTo(uiText(R.string.expected_date, UiText.Raw("Apr 9")))
        assertThat(homeHeroSubtitle(state)).isEqualTo(uiText(R.string.date_not_enough_history_for_a_range, UiText.Raw("Apr 9")))
    }

    @Test
    fun heroSubtitle_surfacesOverdueDays() {
        val state = HomeUiState(
            nextPeriodDateText = UiText.Raw("Apr 9"),
            supportsRange = true,
            overdueDays = 3,
            hasCurrentCycle = false,
        )

        assertThat(homeHeroSubtitle(state)).isEqualTo(uiText(R.string.expected_date_with_overdue, UiText.Raw("Apr 9"), pluralUiText(R.plurals.n_days_past_the_expected_date, 3, 3)))
    }

    @Test
    fun overdueFormatting_isSingularAtOneDay() {
        assertThat(formatOverdue(1)).isEqualTo(uiText(R.string.one_day_past_the_expected_date))
        assertThat(formatOverdue(0)).isEqualTo(UiText.Raw(""))
        assertThat(formatOverdue(5)).isEqualTo(pluralUiText(R.plurals.n_days_past_the_expected_date, 5, 5))
    }
}
