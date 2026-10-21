package com.cyclecare.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.data.LocalAppResetRepository
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.ReminderRuleEntity
import com.cyclecare.core.domain.reminderWorkName
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ledger item P9-11: prove that a reset cancels the reminders.
 *
 * Spec `02` §9.7 and the scenario "cancels scheduled reminders where
 * possible" both require it, and the hook is in place: the reset
 * snapshots the armed rules, and the app wires the snapshot to the
 * alarm facade. What was missing was the evidence.
 *
 * The evidence has to come from the device, because the alarm is a
 * device resource and nothing below the platform can say whether one is
 * pending. So the test does three things on the device:
 *
 *  1. arms a real alarm through the real facade, addressed exactly as
 *     the scheduler addresses it,
 *  2. asks the platform whether that alarm is pending, before and after
 *     the reset, by reading the alarm queue the platform itself keeps,
 *  3. posts a notification under one of the app's own reminder ids, and
 *     checks the reset takes it down as well.
 *
 * The first measurement is the control. If the alarm is not in the
 * queue before the reset, the second measurement would prove nothing,
 * so the test fails rather than passing quietly.
 *
 * The test wipes the app's own preferences and its notification
 * permission record, because that is what a reset does. On a shared
 * test device that is the intended effect of the operation under test.
 */
@RunWith(AndroidJUnit4::class)
class AppResetCancelsRemindersTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    private lateinit var database: CycleCareDatabase
    private lateinit var alarmFacade: AndroidReminderAlarmFacade

    /**
     * A rule id nothing else on the device uses.
     *
     * The alarm is addressed by a request code derived from the rule
     * id, so an id nothing else uses means the request code in the
     * alarm queue belongs to this test and to nothing else.
     */
    private lateinit var ruleId: String
    private lateinit var uniqueName: String

    @Before
    fun setUp() {
        ruleId = "reset-probe-${UUID.randomUUID()}"
        uniqueName = reminderWorkName(ruleId)

        database = Room.inMemoryDatabaseBuilder(context, CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            database.reminderRuleDao().upsert(
                ReminderRuleEntity(
                    id = ruleId,
                    type = ReminderType.PeriodPrep.name,
                    enabled = true,
                    offsetDays = -2,
                    hourOfDay = 9,
                    minute = 0,
                    userPinnedTime = false,
                ),
            )
        }

        alarmFacade = AndroidReminderAlarmFacade(context)
        armTheReminder()
    }

    @After
    fun tearDown() {
        // The device must not be left with a pending alarm, whatever
        // the test did.
        runCatching { alarmFacade.cancel(uniqueName) }
        runCatching { database.close() }
    }

    @Test
    fun resetAllLocalData_cancelsTheArmedReminderAlarm() {
        // The control: the alarm really is in the platform's queue, so
        // its absence afterwards means the reset took it out.
        val before = pendingAlarmLinesForThisReminder()
        assertThat(before).isNotEmpty()

        runReset()

        val after = pendingAlarmLinesForThisReminder()
        assertThat(after).isEmpty()
    }

    @Test
    fun resetAllLocalData_leavesTheRuleTableEmptySoNothingCanFireAfterwards() = runBlocking {
        runReset()

        // The rule the alarm was addressed by is gone as well, so even
        // an alarm that survived would have nothing to deliver.
        assertThat(database.reminderRuleDao().count()).isEqualTo(0)
    }

    @Test
    fun resetAllLocalData_takesDownTheReminderNotificationsItOwns() {
        assumeTrue(
            "the test needs the app to be allowed to post notifications",
            notificationsCanBePosted(),
        )

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                TEST_CHANNEL_ID,
                "Reset test",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        // A notification under one of the ids the reset is responsible
        // for. This is the control: the notification is really posted
        // before the reset, so its absence afterwards is a result and
        // not a posting that never happened.
        manager.notify(
            ReminderChannels.groupedSummaryNotificationId,
            NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle("Reset test")
                .setContentText("Posted before the reset")
                .build(),
        )
        assertThat(activeNotificationIds()).contains(ReminderChannels.groupedSummaryNotificationId)

        runReset()

        val remaining = activeNotificationIds()
        assertThat(remaining).doesNotContain(ReminderChannels.groupedSummaryNotificationId)
        assertThat(remaining).doesNotContain(ReminderChannels.deferredSummaryNotificationId)
        assertThat(remaining).doesNotContain(ReminderDelivery.autoDisableNoticeNotificationId)
        // The id the reminder itself would have posted under.
        assertThat(remaining).doesNotContain(uniqueName.hashCode())
    }

    /**
     * The reset, wired exactly as the application wires it.
     */
    private fun runReset() = runBlocking {
        LocalAppResetRepository(
            context = context,
            database = database,
            cancelArmedReminders = { ruleAlarmNames ->
                ReminderResetCancellation.cancel(context, ruleAlarmNames)
            },
        ).resetAllLocalData()
    }

    /**
     * Arms the alarm the way the scheduler arms it.
     *
     * The inexact tier is used because it needs no permission. The
     * trigger is far enough away that the alarm cannot fire while the
     * test runs: what is being measured is whether it is *pending*.
     */
    private fun armTheReminder() {
        val request = ReminderAlarmRequest(
            ruleId = ruleId,
            uniqueName = uniqueName,
            payload = ReminderAlarmPayload(
                notificationId = uniqueName.hashCode(),
                title = "Reset test",
                body = "Reset test",
                privateBody = "Reset test",
                ruleId = ruleId,
                reminderType = ReminderType.PeriodPrep,
                channelKey = ReminderChannelKey.CycleAlerts,
                deepLinkTarget = ReminderDeepLinkTarget.Home,
                targetEpochDay = null,
                scheduledAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        alarmFacade.setAndAllowWhileIdle(
            System.currentTimeMillis() + TEN_MINUTES_MILLIS,
            request,
        )
    }

    /**
     * The pending alarms in the platform's queue that belong to this
     * reminder.
     *
     * The platform keeps the queue and `dumpsys` is the only way to
     * read it: there is no API that says "is this alarm still
     * scheduled". The line for an alarm carries the request code it was
     * addressed by, which is derived from the rule id, so the request
     * code identifies this reminder's alarm and nothing else.
     */
    private fun pendingAlarmLinesForThisReminder(): List<String> {
        val requestCode = uniqueName.hashCode()
        val codePattern = Regex("(?<![0-9-])$requestCode(?![0-9])")
        val lines = shell("dumpsys alarm")
            .lineSequence()
            .filter { line ->
                val lower = line.lowercase()
                lower.contains("pending alarm") && line.contains(context.packageName)
            }
            .filter { line -> codePattern.containsMatchIn(line) || line.contains(RECEIVER_NAME) }
            .toList()
        android.util.Log.i(TAG, "pending alarms for $ruleId: $lines")
        return lines
    }

    private fun activeNotificationIds(): List<Int> =
        NotificationManagerCompat.from(context).activeNotifications.map { it.id }

    private fun notificationsCanBePosted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                uiAutomation.grantRuntimePermission(
                    context.packageName,
                    "android.permission.POST_NOTIFICATIONS",
                )
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun shell(command: String): String {
        val descriptor = uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    }

    private companion object {
        const val TAG = "AppResetCancelsReminders"
        const val RECEIVER_NAME = "ReminderAlarmReceiver"
        const val TEST_CHANNEL_ID = "cyclecare-reset-test-channel"
        const val TEN_MINUTES_MILLIS = 10 * 60 * 1000L
    }
}
