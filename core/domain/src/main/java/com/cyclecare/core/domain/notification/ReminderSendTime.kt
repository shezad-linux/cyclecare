package com.cyclecare.core.domain.notification

import com.cyclecare.core.model.ReminderRule

/**
 * The hour a reminder goes out at.
 *
 * Spec `13` §8: a pinned time is the user's explicit choice and is never
 * overridden — not by a learned send time, not by a suggestion, not by a
 * rebuild of the schedule. A learned time is a suggestion the user
 * accepts or declines, so it only ever applies to a rule whose time the
 * user did not choose.
 *
 * Pure: the learned hour is handed in rather than fetched, so the domain
 * stays free of Android and of any database, and the rule is testable on
 * its own.
 */
fun resolveSendHour(
    userPinnedTime: Boolean,
    ruleHourOfDay: Int,
    learnedHourOfDay: Int?,
): Int = when {
    // An explicit choice always wins.
    userPinnedTime -> ruleHourOfDay
    // A learned hour is only ever a suggestion the user accepted.
    learnedHourOfDay != null -> learnedHourOfDay
    else -> ruleHourOfDay
}

/** The same rule, for a rule the domain already holds. */
fun resolveSendHour(rule: ReminderRule, learnedHourOfDay: Int?): Int =
    resolveSendHour(
        userPinnedTime = rule.userPinnedTime,
        ruleHourOfDay = rule.hourOfDay,
        learnedHourOfDay = learnedHourOfDay,
    )

/**
 * Whether this candidate is going out at the user's learned hour.
 *
 * The scorer's `learnedTimeBonus` never applies to a pinned candidate, so
 * this has to be false for one. The check lives here so that no caller can
 * award the bonus to a pinned time by accident: the flag is the only way
 * the bonus is reachable, and the flag is false whenever the time was the
 * user's own choice.
 */
fun sendsAtLearnedTime(
    userPinnedTime: Boolean,
    learnedHourOfDay: Int?,
    scheduledHourOfDay: Int,
): Boolean = !userPinnedTime && learnedHourOfDay != null && scheduledHourOfDay == learnedHourOfDay
