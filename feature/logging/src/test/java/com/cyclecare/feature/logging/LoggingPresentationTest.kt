package com.cyclecare.feature.logging

import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.DischargeType
import com.cyclecare.core.model.EnergyLevel
import com.cyclecare.core.model.Mood
import com.cyclecare.core.model.PainLevel
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.SymptomLog
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import com.cyclecare.core.designsystem.PeriodHeroMode
import com.cyclecare.core.designsystem.text.uiText
import com.cyclecare.core.designsystem.text.pluralUiText
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

class LoggingPresentationTest {
    private val today = LocalDate.of(2026, 3, 16)

    @Test
    fun `build logging ui state keeps blank entry compact`() {
        val editor = buildEditorState(entry = null, hydrationKey = "new:$today")

        val uiState = buildLoggingUiState(
            today = today,
            profile = null,
            prediction = null,
            todayEntry = null,
            editor = editor,
            saveMessage = null,
            isSaving = false,
        )

        assertThat(uiState.hasExistingEntry).isFalse()
        assertThat(uiState.saveCtaLabel).isEqualTo(uiText(R.string.save_cta_save))
        assertThat(uiState.isDetailsExpanded).isFalse()
        assertThat(buildLogDateLabel(today, Locale.US)).isEqualTo("Monday, Mar 16")
    }

    @Test
    fun `active period uses period check in copy`() {
        val uiState = LoggingUiState(
            heroMode = PeriodHeroMode.Active,
            cyclePhase = CyclePhase.PERIOD_DAYS,
        )

        assertThat(loggingHeroEyebrow(uiState)).isEqualTo(uiText(R.string.hero_eyebrow_period_check_in))
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo(uiText(R.string.hero_insight_active_period))
    }

    @Test
    fun `predicted period keeps daily check in copy`() {
        val uiState = LoggingUiState(
            heroMode = PeriodHeroMode.Predicted,
            // Control flow reads the phase value, not the English label.
            cyclePhase = CyclePhase.LATE,
        )

        assertThat(loggingHeroEyebrow(uiState)).isEqualTo(uiText(R.string.hero_eyebrow_daily_check_in))
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo(uiText(R.string.hero_insight_late_cycle))
    }

    @Test
    fun `current logged cycle drives active period check in copy`() {
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = today.minusDays(24),
            trackingGoal = TrackingGoal.SymptomAwareness,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )
        val prediction = PredictionSnapshot(
            generatedAt = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateRange(today.plusDays(7), today.plusDays(12)),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 22,
        )

        val uiState = buildLoggingUiState(
            today = today,
            profile = profile,
            prediction = prediction,
            todayEntry = null,
            editor = buildEditorState(entry = null, hydrationKey = "new:$today"),
            saveMessage = null,
            isSaving = false,
            cycles = listOf(
                CycleRecord(
                    id = "active-cycle",
                    startDate = today.minusDays(5),
                    endDate = today,
                    cycleLength = 28,
                    source = CycleSource.User,
                ),
            ),
        )

        assertThat(uiState.heroMode).isEqualTo(PeriodHeroMode.Active)
        assertThat(uiState.cyclePhase).isEqualTo(CyclePhase.PERIOD_DAYS)
        assertThat(uiState.cyclePhase?.displayKey()).isEqualTo(uiText(R.string.cycle_phase_period_days))
        assertThat(loggingHeroEyebrow(uiState)).isEqualTo(uiText(R.string.hero_eyebrow_period_check_in))
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo(uiText(R.string.hero_insight_active_period))
    }

    @Test
    fun `existing entry reuses update wording and expands details`() {
        val entry = SymptomLog(
            id = "today-entry",
            date = today,
            mood = Mood.Happy,
            painLevel = PainLevel.Light,
            energyLevel = EnergyLevel.High,
            dischargeType = DischargeType.EggWhite,
            medication = "Ibuprofen",
            notes = "Symptoms eased by evening.",
        )
        val profile = UserProfile(
            ageBand = AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = today.minusDays(5),
            trackingGoal = TrackingGoal.SymptomAwareness,
            remindersEnabled = true,
            privacyModeEnabled = true,
        )
        val prediction = PredictionSnapshot(
            generatedAt = today,
            cycleDay = 6,
            nextPeriodDate = today.plusDays(22),
            ovulationDate = today.plusDays(9),
            fertileWindow = DateRange(today.plusDays(7), today.plusDays(12)),
            confidenceLabel = "Baseline estimate",
            daysUntilNextPeriod = 22,
        )

        val uiState = buildLoggingUiState(
            today = today,
            profile = profile,
            prediction = prediction,
            todayEntry = entry,
            editor = buildEditorState(entry = entry, hydrationKey = entry.id),
            saveMessage = uiText(R.string.saved_to_local_timeline),
            isSaving = false,
        )

        assertThat(uiState.hasExistingEntry).isTrue()
        assertThat(uiState.saveCtaLabel).isEqualTo(uiText(R.string.save_cta_update))
        assertThat(uiState.entryStatusLabel).isEqualTo(uiText(R.string.editing_todays_saved_check_in))
        assertThat(uiState.cycleProgressLabel).isEqualTo(uiText(R.string.cycle_progress_day, 6, 28))
        assertThat(uiState.isDetailsExpanded).isTrue()
        assertThat(uiState.dischargeType?.displayLabel()).isEqualTo(uiText(R.string.discharge_egg_white))
    }

    @Test
    fun `build cycle progress label degrades safely without complete context`() {
        assertThat(buildCycleProgressLabel(profile = null, prediction = null)).isNull()
        assertThat(
            buildCycleProgressLabel(
                profile = UserProfile(
                    ageBand = AgeBand.Twenties,
                    averageCycleLength = 28,
                    periodLength = 5,
                    lastPeriodStart = today.minusDays(5),
                    trackingGoal = TrackingGoal.PeriodPrep,
                    remindersEnabled = true,
                    privacyModeEnabled = true,
                ),
                prediction = null,
            ),
        ).isNull()
    }

    @Test
    fun `the fertile window phase comes from the engine window`() {
        // The engine derives the window from the ovulation estimate and
        // the dispersion of the observed history. A fixed fraction of the
        // cycle cannot reproduce it, and the Log screen and the Today
        // screen used to disagree about which days it covered.
        val prediction = predictionWith(
            cycleDay = 6,
            fertileWindow = DateRange(today.minusDays(1), today.plusDays(3)),
            supportsRange = true,
        )

        val uiState = buildLoggingUiState(
            today = today,
            profile = profile(),
            prediction = prediction,
            todayEntry = null,
            editor = buildEditorState(entry = null, hydrationKey = "new:$today"),
            saveMessage = null,
            isSaving = false,
        )

        assertThat(uiState.cyclePhase).isEqualTo(CyclePhase.FERTILE_WINDOW)
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo(uiText(R.string.hero_insight_fertile_window))
    }

    @Test
    fun `no fertile window is claimed when the engine produced none`() {
        // The snapshot always carries a range: the mapper fills it in
        // when the engine declined. Showing that would put a fertile
        // window on the screen that the engine never generated.
        val prediction = predictionWith(
            cycleDay = 14,
            fertileWindow = DateRange(today.minusDays(4), today.plusDays(1)),
            supportsRange = false,
        )

        val uiState = buildLoggingUiState(
            today = today,
            profile = profile(),
            prediction = prediction,
            todayEntry = null,
            editor = buildEditorState(entry = null, hydrationKey = "new:$today"),
            saveMessage = null,
            isSaving = false,
        )

        assertThat(uiState.cyclePhase).isNotEqualTo(CyclePhase.FERTILE_WINDOW)
        assertThat(uiState.fertileWindowStartsInDays).isNull()
    }

    @Test
    fun `the fertile window countdown comes from the engine window`() {
        // The countdown used to be a hardcoded cycle day 12, which was a
        // third derivation of the fertile window and had nothing to do
        // with the engine's.
        val prediction = predictionWith(
            cycleDay = 4,
            fertileWindow = DateRange(today.plusDays(4), today.plusDays(9)),
            supportsRange = true,
        )

        // A short period length, so the hero is in the predicted mode at
        // cycle day 4 rather than the active-period one.
        val uiState = buildLoggingUiState(
            today = today,
            profile = profile(periodLength = 3),
            prediction = prediction,
            todayEntry = null,
            editor = buildEditorState(entry = null, hydrationKey = "new:$today"),
            saveMessage = null,
            isSaving = false,
        )

        assertThat(uiState.fertileWindowStartsInDays).isEqualTo(4)
        assertThat(buildSmartLoggingInsight(uiState)).isEqualTo(
            pluralUiText(R.plurals.fertile_window_starts_in_days, 4, 4),
        )
    }

    private fun profile(periodLength: Int = 5) = UserProfile(
        ageBand = AgeBand.Twenties,
        averageCycleLength = 28,
        periodLength = periodLength,
        lastPeriodStart = today.minusDays(5),
        trackingGoal = TrackingGoal.SymptomAwareness,
        remindersEnabled = true,
        privacyModeEnabled = true,
    )

    private fun predictionWith(
        cycleDay: Int,
        fertileWindow: DateRange,
        supportsRange: Boolean,
    ) = PredictionSnapshot(
        generatedAt = today,
        cycleDay = cycleDay,
        nextPeriodDate = today.plusDays((28 - cycleDay).toLong()),
        ovulationDate = today.plusDays((14 - cycleDay).toLong()),
        fertileWindow = fertileWindow,
        confidenceLabel = if (supportsRange) "Moderate range" else "Baseline estimate",
        daysUntilNextPeriod = 28 - cycleDay,
        nextPeriodWindow = if (supportsRange) {
            DateRange(today.plusDays((26 - cycleDay).toLong()), today.plusDays((30 - cycleDay).toLong()))
        } else {
            null
        },
    )
}
