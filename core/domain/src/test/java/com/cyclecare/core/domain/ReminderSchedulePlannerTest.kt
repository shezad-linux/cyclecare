package com.cyclecare.core.domain

import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class ReminderSchedulePlannerTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val planner = ReminderSchedulePlanner(kolkata)

    @Test
    fun buildSchedules_returnsPeriodFertileAndDailyWhenFutureDatesExist() {
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-28"),
                ovulationDate = LocalDate.parse("2026-03-14"),
                fertileStart = LocalDate.parse("2026-03-10"),
                fertileEnd = LocalDate.parse("2026-03-15"),
                daysUntilNextPeriod = 18,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
                ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).hasSize(3)
        assertThat(schedules.map { it.type }).containsExactly(
            ReminderType.FertileWindowStart,
            ReminderType.DailyLog,
            ReminderType.PeriodPrep,
        ).inOrder()
        assertThat(schedules[0].scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, kolkata))
        assertThat(schedules[0].title).isEqualTo("Fertile window reminder")
        assertThat(schedules[1].scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 10, 20, 0, 0, 0, kolkata))
        // Two days before the earliest plausible start (28th minus the
        // one-day half-width), not two days before the centre estimate.
        // Anchoring to the centre would assert a precision the app does
        // not have. Spec: 13-notification-intelligence.md section 6.
        assertThat(schedules[2].scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 25, 9, 0, 0, 0, kolkata))
        assertThat(schedules[2].privateBody).contains("Open the app")
    }

    @Test
    fun buildSchedules_dailyLogUsesTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = null,
            rules = listOf(
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).hasSize(1)
        val dailyReminder = schedules.single()
        assertThat(dailyReminder.scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 10, 20, 0, 0, 0, kolkata))
        assertThat(dailyReminder.channelKey).isEqualTo(ReminderChannelKey.DailyCheckIn)
        assertThat(dailyReminder.body).isEqualTo("Take a moment to log today's symptoms.")
        assertThat(dailyReminder.deepLinkTarget).isEqualTo(ReminderDeepLinkTarget.Log)
    }

    @Test
    fun buildSchedules_dailyLogRollsToTomorrowWhenTodaysTimePassed() {
        val now = ZonedDateTime.of(2026, 3, 10, 21, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = null,
            rules = listOf(
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).hasSize(1)
        assertThat(schedules.single().scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 11, 20, 0, 0, 0, kolkata))
    }

    @Test
    fun buildSchedules_skipsPastDuePeriodAndFertileReminders() {
        val now = ZonedDateTime.of(2026, 3, 10, 10, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-12"),
                ovulationDate = LocalDate.parse("2026-03-10"),
                fertileStart = LocalDate.parse("2026-03-10"),
                fertileEnd = LocalDate.parse("2026-03-15"),
                daysUntilNextPeriod = 2,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
                ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).isEmpty()
    }

    @Test
    fun buildSchedules_handlesLeapYearMonthEndAndYearTransitions() {
        val leapNow = ZonedDateTime.of(2028, 2, 28, 8, 0, 0, 0, kolkata)
        val leapSchedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2028-03-02"),
                ovulationDate = LocalDate.parse("2028-02-17"),
                fertileStart = LocalDate.parse("2028-02-13"),
                fertileEnd = LocalDate.parse("2028-02-18"),
                daysUntilNextPeriod = 3,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
            ),
            now = leapNow,
        )
        // 2028-03-02 minus the one-day half-width is 03-01, and two
        // days before that is 02-28. Still a leap-year boundary, so the
        // transition stays covered.
        assertThat(leapSchedules.single().scheduledAt.toLocalDate()).isEqualTo(LocalDate.parse("2028-02-28"))

        val monthEndNow = ZonedDateTime.of(2026, 1, 30, 8, 0, 0, 0, kolkata)
        val monthEndSchedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-02-01"),
                ovulationDate = LocalDate.parse("2026-01-18"),
                fertileStart = LocalDate.parse("2026-01-14"),
                fertileEnd = LocalDate.parse("2026-01-19"),
                daysUntilNextPeriod = 2,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -1, hourOfDay = 9, minute = 0),
            ),
            now = monthEndNow,
        )
        // 2026-02-01 minus the half-width is 01-31, and one day before
        // that is 01-30. The month-end crossing is still exercised.
        assertThat(monthEndSchedules.single().scheduledAt.toLocalDate()).isEqualTo(LocalDate.parse("2026-01-30"))

        val yearBoundaryNow = ZonedDateTime.of(2026, 12, 29, 8, 0, 0, 0, kolkata)
        val yearBoundarySchedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2027-01-02"),
                ovulationDate = LocalDate.parse("2026-12-19"),
                fertileStart = LocalDate.parse("2026-12-15"),
                fertileEnd = LocalDate.parse("2026-12-20"),
                daysUntilNextPeriod = 4,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
            ),
            now = yearBoundaryNow,
        )
        // Shifted by the half-width, as above; the year boundary is still
        // exercised.
        assertThat(yearBoundarySchedules.single().scheduledAt.toLocalDate()).isEqualTo(LocalDate.parse("2026-12-30"))
    }

    @Test
    fun buildSchedules_usesNowZoneForLocalScheduling() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, newYork)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-28"),
                ovulationDate = LocalDate.parse("2026-03-14"),
                fertileStart = LocalDate.parse("2026-03-12"),
                fertileEnd = LocalDate.parse("2026-03-17"),
                daysUntilNextPeriod = 18,
            ),
            rules = listOf(
                ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).hasSize(1)
        assertThat(schedules.single().scheduledAt.zone).isEqualTo(newYork)
        assertThat(schedules.single().scheduledAt.hour).isEqualTo(9)
    }

    @Test
    fun buildSchedules_deduplicatesRepeatedRuleIds() {
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(),
            rules = listOf(
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 22, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).hasSize(1)
        assertThat(schedules.single().scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 10, 20, 0, 0, 0, kolkata))
        assertThat(schedules.single().uniqueWorkName).isEqualTo(reminderWorkName("daily"))
    }

    @Test
    fun buildSchedules_returnsLatePeriodReminderWhenPredictionPassedAndNoNewCycleExists() {
        val now = ZonedDateTime.of(2026, 3, 20, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-17"),
                ovulationDate = LocalDate.parse("2026-03-03"),
                fertileStart = LocalDate.parse("2026-02-27"),
                fertileEnd = LocalDate.parse("2026-03-04"),
                daysUntilNextPeriod = -3,
            ),
            rules = listOf(
                ReminderRule("late", ReminderType.LatePeriod, enabled = true, daysBefore = 3, hourOfDay = 9, minute = 0),
            ),
            now = now,
        )

        assertThat(schedules).hasSize(1)
        assertThat(schedules.single().type).isEqualTo(ReminderType.LatePeriod)
        assertThat(schedules.single().scheduledAt).isEqualTo(ZonedDateTime.of(2026, 3, 20, 9, 0, 0, 0, kolkata))
    }

    @Test
    fun buildSchedules_skipsLatePeriodWhenUserCycleAlreadyLoggedAfterPrediction() {
        val now = ZonedDateTime.of(2026, 3, 20, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-17"),
                ovulationDate = LocalDate.parse("2026-03-03"),
                fertileStart = LocalDate.parse("2026-02-27"),
                fertileEnd = LocalDate.parse("2026-03-04"),
                daysUntilNextPeriod = -3,
            ),
            rules = listOf(
                ReminderRule("late", ReminderType.LatePeriod, enabled = true, daysBefore = 3, hourOfDay = 9, minute = 0),
            ),
            cycles = listOf(
                CycleRecord(
                    id = "cycle",
                    startDate = LocalDate.parse("2026-03-18"),
                    endDate = LocalDate.parse("2026-03-22"),
                    cycleLength = 28,
                    source = CycleSource.User,
                ),
            ),
            now = now,
        )

        assertThat(schedules).isEmpty()
    }

    private fun predictionSnapshot(
        nextPeriodDate: LocalDate = LocalDate.parse("2026-03-20"),
        ovulationDate: LocalDate = LocalDate.parse("2026-03-06"),
        fertileStart: LocalDate = LocalDate.parse("2026-03-02"),
        fertileEnd: LocalDate = LocalDate.parse("2026-03-07"),
        daysUntilNextPeriod: Int = 10,
        // A supportable estimate carries a real range. Tests that mean to
        // exercise suppression pass a narrower snapshot explicitly.
        halfWidthDays: Int? = 1,
    ) = PredictionSnapshot(
        generatedAt = LocalDate.parse("2026-03-10"),
        cycleDay = 10,
        nextPeriodDate = nextPeriodDate,
        ovulationDate = ovulationDate,
        fertileWindow = DateRange(
            start = fertileStart,
            endInclusive = fertileEnd,
        ),
        confidenceLabel = "Stable",
        daysUntilNextPeriod = daysUntilNextPeriod,
        nextPeriodWindow = halfWidthDays?.let {
            DateRange(nextPeriodDate.minusDays(it.toLong()), nextPeriodDate.plusDays(it.toLong()))
        },
        nextPeriodHalfWidthDays = halfWidthDays,
    )

    @Test
    fun buildSchedules_withholdsPeriodReminderWhenHistoryIsInsufficient() {
        // With fewer than three intervals there is nothing worth
        // interrupting anyone about. Spec section 6 treats this as
        // "no period reminder at all".
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-28"),
                daysUntilNextPeriod = 18,
                halfWidthDays = null,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
                ReminderRule("fertile", ReminderType.FertileWindowStart, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
                ReminderRule("daily", ReminderType.DailyLog, enabled = true, daysBefore = 0, hourOfDay = 20, minute = 0),
            ),
            now = now,
        )

        // The daily nudge survives; the estimate-derived reminders do not.
        assertThat(schedules.map { it.type }).containsExactly(ReminderType.DailyLog)
    }

    @Test
    fun buildSchedules_anchorsPeriodPrepToEarliestPlausibleStart() {
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-28"),
                daysUntilNextPeriod = 18,
                halfWidthDays = 3,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0),
            ),
            now = now,
        )

        // Earliest plausible start is the 25th (28th minus three days),
        // and the reminder is two days ahead of that.
        assertThat(schedules.single().scheduledAt.toLocalDate())
            .isEqualTo(LocalDate.parse("2026-03-23"))
    }

    @Test
    fun buildSchedules_widerRangePushesPeriodPrepEarlier() {
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)
        val rule = ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = -2, hourOfDay = 9, minute = 0)

        val narrow = planner.buildSchedules(
            prediction = predictionSnapshot(nextPeriodDate = LocalDate.parse("2026-03-28"), halfWidthDays = 1),
            rules = listOf(rule),
            now = now,
        )
        val wide = planner.buildSchedules(
            prediction = predictionSnapshot(nextPeriodDate = LocalDate.parse("2026-03-28"), halfWidthDays = 5),
            rules = listOf(rule),
            now = now,
        )

        assertThat(wide.single().scheduledAt.toLocalDate()).isLessThan(narrow.single().scheduledAt.toLocalDate())
    }

    @Test
    fun buildSchedules_wideRangePeriodCopyStatesTheRangeNotAPoint() {
        val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, kolkata)

        val schedules = planner.buildSchedules(
            prediction = predictionSnapshot(
                nextPeriodDate = LocalDate.parse("2026-03-28"),
                halfWidthDays = 3,
            ),
            rules = listOf(
                ReminderRule("period", ReminderType.PeriodPrep, enabled = true, daysBefore = 0, hourOfDay = 9, minute = 0),
            ),
            now = now,
        )

        // Copy must be written against the range. Claiming a single date
        // is the overstatement this whole change exists to remove.
        val body = schedules.single().body
        assertThat(body).doesNotContain("due today")
        assertThat(body).contains("2026-03-25")
        assertThat(body).contains("2026-03-28")
    }
}
