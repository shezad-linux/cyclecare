package com.cyclecare.core.data

import com.cyclecare.core.domain.reminderWorkName
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Spec 02 §9.7 and the ledger item P9-11: a reset must not leave a
 * reminder armed.
 *
 * The instrumented proof of this needs a device, because the alarm
 * itself is a device resource. What can be proved without one is the
 * part the device would otherwise hide: the order the reset performs
 * its steps in, and the names it addresses the alarms by.
 *
 * The order is the contract. The rules that address the alarms live in
 * the very table the reset clears, so the snapshot of the armed rules
 * and the cancellation both have to happen before the clear. A reset
 * that cleared first would leave an alarm armed with no rule to
 * consult, and the user would get a reminder about a period the app no
 * longer knows anything about.
 *
 * The names are the other half. An alarm is addressed by a name, and
 * the cancellation only finds the alarm that was actually armed if it
 * uses the same name the scheduler armed it under.
 */
class AppResetReminderCancellationTest {

    @Test
    fun theArmedRemindersAreCancelledBeforeTheTablesAreCleared() = runBlocking {
        val order = mutableListOf<String>()

        AppResetSequence(
            cancelArmedReminders = { order += "cancel" },
            clearTables = { order += "clear" },
            deleteExports = { order += "exports" },
            clearPreferences = { order += "preferences" },
            clearNotificationPermission = { order += "notificationPermission" },
        ).run()

        // The cancellation is first, and the clear is second. Everything
        // else the reset does is after both, because it is all part of
        // the same wipe.
        assertThat(order).containsExactly(
            "cancel",
            "clear",
            "exports",
            "preferences",
            "notificationPermission",
        ).inOrder()
    }

    @Test
    fun theCancellationIsAddressedByTheNamesTheSchedulerArmsTheAlarmsUnder() {
        val names = reminderAlarmNames(listOf("PeriodPrep", "DailyLog", "LatePeriod"))

        // Exactly the names `ReminderSchedulePlanner` arms them under,
        // so the cancellation finds the alarm that was armed rather than
        // a similar one.
        assertThat(names).containsExactly(
            reminderWorkName("PeriodPrep"),
            reminderWorkName("DailyLog"),
            reminderWorkName("LatePeriod"),
        ).inOrder()
        assertThat(names).containsExactly(
            "cyclecare-reminder-rule-periodprep",
            "cyclecare-reminder-rule-dailylog",
            "cyclecare-reminder-rule-lateperiod",
        ).inOrder()
    }

    @Test
    fun theRuleIdIsLowerCasedSoTheNameIsTheSameWhicheverCaseItWasStoredWith() {
        assertThat(reminderAlarmNames(listOf("PeriodPrep")))
            .isEqualTo(reminderAlarmNames(listOf("periodprep")))
    }

    @Test
    fun aResetWithNoArmedRulesStillCancels() = runBlocking {
        var cancelledWith: List<String>? = null

        AppResetSequence(
            cancelArmedReminders = { cancelledWith = emptyList() },
            clearTables = {},
            deleteExports = {},
            clearPreferences = {},
            clearNotificationPermission = {},
        ).run()

        // The app layer cancels more than the rule alarms — the coalesced
        // summary and the auto-disable notice have alarms of their own,
        // addressed by no rule at all. So the hook runs even when there
        // is no rule to name.
        assertThat(cancelledWith).isEmpty()
    }

    @Test
    fun aFailedCancellationDoesNotStopTheReset() = runBlocking {
        var cleared = false

        AppResetSequence(
            cancelArmedReminders = { throw IllegalStateException("the alarm service refused") },
            clearTables = { cleared = true },
            deleteExports = {},
            clearPreferences = {},
            clearNotificationPermission = {},
        ).run()

        // The user asked for their data to go. A reminder that could not
        // be cancelled is a problem worth reporting, but it is not a
        // reason to leave the health records the user asked to delete
        // sitting on the device.
        assertThat(cleared).isTrue()
    }
}
