package com.cyclecare.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That a reminder alarm is actually delivered while the device is idle.
 *
 * Spec `13` §11 and the Phase 7 exit criterion are about delivery, not
 * about scheduling. An alarm that is armed and never fires satisfies
 * nothing, so this test arms a real alarm, puts the device into idle, and
 * waits for the broadcast to arrive.
 *
 * This test is opt-in, because it changes the state of the whole device
 * and every other test on that device would be affected. It is run
 * deliberately:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.cyclecare.app.ReminderDozeDeliveryTest \
 *   -Pandroid.testInstrumentationRunnerArguments.runDozeTest=true
 * ```
 *
 * Without that argument the test is skipped, so the shared suite is not
 * disturbed.
 *
 * Deep idle is used, because that is the harder case and the one the
 * specification names. Light idle does not engage on the emulator at all,
 * and in deep idle the platform defers an alarm to a maintenance window
 * rather than delivering it immediately. A delivery that survives deep
 * idle therefore also survives light idle, and the deferral is real
 * behaviour rather than a defect.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDozeDeliveryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    private val action = "${context.packageName}.DOZE_DELIVERY_TEST"
    private val fireTimes = AtomicLong(0)
    private val latch = CountDownLatch(1)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            fireTimes.set(System.currentTimeMillis())
            latch.countDown()
        }
    }

    @Before
    fun setUp() {
        // The device state this test changes is global, so it only runs
        // when it is asked for by name.
        assumeTrue(
            "the Doze delivery test is opt-in; pass runDozeTest=true to run it",
            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("runDozeTest") == "true",
        )
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    @After
    fun tearDown() {
        runCatching { context.unregisterReceiver(receiver) }
        // The device must not be left idle, or the next test would run
        // against a device that is not behaving normally. The screen is
        // turned on as well, because a device with the screen off does not
        // bring an activity to the foreground.
        shell("dumpsys deviceidle unforce")
        shell("dumpsys deviceidle reset")
        shell("dumpsys battery reset")
        shell("input keyevent 26")
        repeat(20) {
            if (!shell("dumpsys deviceidle get deep").contains("IDLE")) return@repeat
            Thread.sleep(500)
        }
    }

    private fun shell(command: String): String {
        val descriptor = uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    }

    /**
     * Puts the device into deep idle.
     *
     * Light idle does not engage on the emulator, so deep idle is forced
     * instead. That is the harder case: in deep idle the platform defers
     * alarms to a maintenance window, whereas in light idle they are
     * delivered promptly. A delivery that survives deep idle therefore
     * also survives light idle.
     */
    private fun enterDeepIdle(): Boolean {
        shell("dumpsys deviceidle enable")
        shell("dumpsys deviceidle force-idle")
        repeat(20) {
            if (shell("dumpsys deviceidle get deep").contains("IDLE")) return true
            Thread.sleep(1_000)
        }
        return false
    }

    private fun armAlarm(triggerAtMillis: Long) {
        val intent = Intent(action).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4242,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Tier 2: inexact, but delivered through idle, and it needs no
        // permission. This is the API the specification calls the important
        // upgrade.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    @Test
    fun anAlarmFiresWhileTheDeviceIsIdle() {
        val triggerAt = System.currentTimeMillis() + 60_000L
        armAlarm(triggerAt)

        val idled = enterDeepIdle()
        assertThat(idled).isTrue()

        val delivered = latch.await(6, TimeUnit.MINUTES)

        // Whatever happened, wake the device so the platform can settle.
        shell("input keyevent 26")

        assertThat(delivered).isTrue()
        val firedAt = fireTimes.get()
        val lateness = Duration.ofMillis(firedAt - triggerAt)
        android.util.Log.i(
            "ReminderDozeDelivery",
            "deep idle delivery was ${lateness.seconds}s after the armed time",
        )
        // The platform may deliver a little late, and in light idle an
        // alarm can be deferred by up to a minute. What matters is that it
        // was delivered at all, while the device was idle, rather than
        // being held until the device woke up.
        assertThat(lateness.toMinutes()).isAtMost(5)
    }

    @Test
    fun anAlarmFiresPromptlyWhenTheDeviceIsAwake() {
        // The counterpart to the idle case.
        //
        // The bound is a window rather than a moment, because
        // setAndAllowWhileIdle is an inexact API: the platform may deliver
        // it anywhere inside its window, and on this emulator that window
        // is tens of seconds rather than the one second it is on a current
        // device. What the old deferred-work path could not do at all is
        // deliver it while the device was idle, which is the case above.
        //
        // The measured lateness is logged, so the evidence is visible
        // rather than only bounded.
        val triggerAt = System.currentTimeMillis() + 30_000L
        armAlarm(triggerAt)

        val delivered = latch.await(3, TimeUnit.MINUTES)

        assertThat(delivered).isTrue()
        val lateness = Duration.ofMillis(fireTimes.get() - triggerAt)
        android.util.Log.i(
            "ReminderDozeDelivery",
            "awake delivery was ${lateness.seconds}s after the armed time",
        )
        assertThat(lateness.seconds).isAtMost(120)
    }
}
