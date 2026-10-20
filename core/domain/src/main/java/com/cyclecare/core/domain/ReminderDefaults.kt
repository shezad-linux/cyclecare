package com.cyclecare.core.domain

import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType

object ReminderDefaults {
    fun standardRules(
        enabled: Boolean,
        trackingGoal: TrackingGoal,
    ): List<ReminderRule> = listOf(
        ReminderRule(
            id = ReminderType.PeriodPrep.name,
            type = ReminderType.PeriodPrep,
            enabled = enabled,
            daysBefore = -2,
            hourOfDay = 9,
            minute = 0,
            // Seeded from an assumption, never from a user choice, so none
            // of these are pinned.
            userPinnedTime = false,
        ),
        ReminderRule(
            id = ReminderType.FertileWindowStart.name,
            type = ReminderType.FertileWindowStart,
            enabled = enabled && trackingGoal == TrackingGoal.ConceptionAwareness,
            daysBefore = 0,
            hourOfDay = 9,
            minute = 0,
            userPinnedTime = false,
        ),
        ReminderRule(
            id = ReminderType.OvulationEstimate.name,
            type = ReminderType.OvulationEstimate,
            enabled = enabled && trackingGoal == TrackingGoal.ConceptionAwareness,
            daysBefore = 0,
            hourOfDay = 9,
            minute = 0,
            userPinnedTime = false,
        ),
        ReminderRule(
            id = ReminderType.DailyLog.name,
            type = ReminderType.DailyLog,
            enabled = enabled,
            daysBefore = 0,
            hourOfDay = 20,
            minute = 0,
            userPinnedTime = false,
        ),
        ReminderRule(
            id = ReminderType.LatePeriod.name,
            type = ReminderType.LatePeriod,
            enabled = false,
            daysBefore = 3,
            hourOfDay = 9,
            minute = 0,
            userPinnedTime = false,
        ),
    )
}
