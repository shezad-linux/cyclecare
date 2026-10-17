package com.cyclecare.core.testing

import com.cyclecare.core.model.AgeBand
import com.cyclecare.core.model.TrackingGoal
import com.cyclecare.core.model.UserProfile
import java.time.LocalDate

object TestFixtures {
    val profile = UserProfile(
        ageBand = AgeBand.Twenties,
        averageCycleLength = 28,
        periodLength = 5,
        lastPeriodStart = LocalDate.parse("2026-03-01"),
        trackingGoal = TrackingGoal.PeriodPrep,
        remindersEnabled = true,
        privacyModeEnabled = true,
    )
}
