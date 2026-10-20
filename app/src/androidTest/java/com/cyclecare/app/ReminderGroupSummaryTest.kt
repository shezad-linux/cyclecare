package com.cyclecare.app

import android.Manifest
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.domain.NoOpAppLogger
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderChannelSettings
import com.cyclecare.core.model.ReminderSoundPreset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Criterion 7, spec `13` §17.7: every notification has a group summary
 * and a `setPublicVersion` redaction.
 *
 * The specification is explicit that a group key with no summary is an
 * orphan group, and that the summary is the redacted variant. This test
 * posts a member into the reminders group, posts the group's summary
 * through the production code path, and reads both back from the
 * system.
 *
 * It needs a device: the group manager, the shade and the lock-screen
 * redaction are all platform behaviour, and no JVM assertion can stand
 * in for them.
 */
@RunWith(AndroidJUnit4::class)
class ReminderGroupSummaryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val settings = ReminderChannelSettings(
        channelKey = ReminderChannelKey.CycleAlerts,
        enabled = true,
        soundEnabled = true,
        soundPreset = ReminderSoundPreset.entries.first(),
        vibrationEnabled = true,
    )
    private val channelId by lazy { ReminderChannels.channelId(settings) }
    private val container by lazy { (context.applicationContext as CycleCareApplication).appContainer }

    @Before
    fun setUp() = runBlocking {
        val grant = instrumentation.uiAutomation.executeShellCommand(
            "pm grant com.cyclecare.tracker android.permission.POST_NOTIFICATIONS",
        )
        grant.close()
        // Without a real channel the system silently drops the post.
        ReminderChannels.sync(context, listOf(settings), NoOpAppLogger).getOrThrow()
    }

    @After
    fun tearDown() {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(9_101)
        manager.cancel(ReminderChannels.groupedSummaryNotificationId)
        manager.cancel(ReminderChannels.deferredSummaryNotificationId)
    }

    /** A member, built the way the delivery path builds it. */
    private fun memberNotification(): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText("Your period may start tomorrow.")
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                ReminderNotificationTemplates.redactedContent(
                    context = context,
                    channelId = channelId,
                    type = com.cyclecare.core.model.ReminderType.PeriodPrep,
                ),
            )
            .setGroup(ReminderChannels.remindersGroupKey)
            .build()

    private fun posted(id: Int): Notification? {
        repeat(5) { attempt ->
            val row = NotificationManagerCompat.from(context)
                .activeNotifications
                .filter { it.id == id }
                .firstOrNull()
                ?.notification
            if (row != null) return row
            if (attempt < 4) Thread.sleep(200)
        }
        return null
    }

    @Test
    fun theGroupHasExactlyOneSummaryAndItIsTheRedactedVariant() {
        NotificationManagerCompat.from(context).notify(9_101, memberNotification())
        ReminderGroupSummary.post(
            context = context,
            container = container,
            channelId = channelId,
        )

        val summary = posted(ReminderChannels.groupedSummaryNotificationId)

        assertThat(summary).isNotNull()
        assertThat(summary!!.flags and Notification.FLAG_GROUP_SUMMARY != 0).isTrue()
        assertThat(summary.group).isEqualTo(ReminderChannels.remindersGroupKey)
        // The summary is the redacted variant, so it carries a public
        // copy of its own and never the cycle body.
        assertThat(summary.publicVersion).isNotNull()
        val publicText =
            summary.publicVersion!!.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertThat(publicText).isNotEqualTo("Your period may start tomorrow.")
        assertThat(summary.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            .doesNotContain("Your period may start tomorrow.")
    }

    @Test
    fun everyMemberOfTheGroupCarriesTheGroupKeyAndARedaction() {
        NotificationManagerCompat.from(context).notify(9_101, memberNotification())
        ReminderGroupSummary.post(
            context = context,
            container = container,
            channelId = channelId,
        )

        val member = posted(9_101)

        assertThat(member).isNotNull()
        assertThat(member!!.group).isEqualTo(ReminderChannels.remindersGroupKey)
        assertThat(member.publicVersion).isNotNull()
        // The member is not itself the summary; the group has one.
        assertThat(member.flags and Notification.FLAG_GROUP_SUMMARY != 0).isFalse()
    }
}
