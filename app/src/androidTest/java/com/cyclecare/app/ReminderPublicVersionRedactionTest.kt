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
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Criterion 7, spec `13` §17.7, posted through the real system.
 *
 * The member notification is built exactly the way `ReminderDelivery`
 * builds it and read back from `NotificationManager`, because that is
 * the only place the "lock screen" of the specification resolves to
 * something observable. The summary the templates build proves the
 * group-summary requirement on the coalesced arc, which is where the
 * group's summary lives once the A7 resolution is honoured.
 */
@RunWith(AndroidJUnit4::class)
class ReminderPublicVersionRedactionTest {

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
    private val cycleBody = "Your period may start tomorrow toward 09:00."

    @Before
    fun setUp() = runBlocking {
        // The system silently drops posts without this on API 33+, and
        // the runner reinstalls the app under test for every run.
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
        manager.cancel(9_001)
        manager.cancel(9_002)
        manager.cancel(9_004)
        manager.cancel(ReminderChannels.deferredSummaryNotificationId)
    }

    /** The member the delivery path builds, verbatim. */
    private fun memberNotification(): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(cycleBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cycleBody))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                ReminderNotificationTemplates.redactedContent(
                    context = context,
                    channelId = channelId,
                    type = ReminderType.PeriodPrep,
                ),
            )
            .setGroup(ReminderChannels.remindersGroupKey)
            .build()

    private fun posted(id: Int): Notification? {
        // The shade list updates asynchronously to notify(); a miss can
        // simply be premature. Poll briefly rather than assume the post
        // landed instantly.
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
    fun aGroupedMemberCarriesAPublicVersionWhoseTextIsNeverTheCycleBody() {
        NotificationManagerCompat.from(context).notify(9_001, memberNotification())

        val posted = posted(9_001)

        assertThat(posted).isNotNull()
        assertThat(posted!!.publicVersion).isNotNull()
        val publicTitle =
            posted.publicVersion!!.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val publicText =
            posted.publicVersion!!.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertThat(publicTitle).isEqualTo(context.getString(R.string.reminder_notification_title))
        assertThat(publicText).isEqualTo(context.getString(R.string.reminder_redacted_generic))
        assertThat(publicText).isNotEqualTo(cycleBody)
        // The private variant keeps the cycle body where the lock screen
        // will not show it.
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            .isEqualTo(cycleBody)
        assertThat(posted.group).isEqualTo(ReminderChannels.remindersGroupKey)
    }

    /**
     * The summary the templates build is a group summary carrying a
     * redacted public copy. The member test proves the public version
     * survives the posting round trip; the summary's own existence in
     * the shade is decided by the platform's group manager (it can
     * synthesise, re-key or hide flagged summaries in single-member
     * groups), so this test asserts the contract the app owes.
     */
    @Test
    fun theCoalescedSummaryIsAGroupSummaryWithItsOwnRedaction() {
        val summary = ReminderNotificationTemplates.redactedGroupSummary(
            context = context,
            channelId = channelId,
            title = context.getString(R.string.reminder_deferred_summary_title),
            body = context.resources.getQuantityString(
                R.plurals.reminder_deferred_summary_body,
                3,
                3,
            ),
        )

        assertThat(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0).isTrue()
        assertThat(summary.group).isEqualTo(ReminderChannels.remindersGroupKey)
        assertThat(summary.publicVersion).isNotNull()
        // The summary's own public copy carries no text at all: nothing
        // cycles-derived can leak through a summarised lock surface.
        assertThat(
            summary.publicVersion!!.extras.getCharSequence(Notification.EXTRA_TEXT),
        ).isNull()
    }

    /** The deferred summary arrives alone, so it must survive alone. */
    @Test
    fun theDeferredSummaryIsAStandaloneGroupedNotificationThatPostsAlone() {
        val summary = ReminderNotificationTemplates.redactedStandalone(
            context = context,
            channelId = channelId,
            title = context.getString(R.string.reminder_deferred_summary_title),
            body = context.resources.getQuantityString(
                R.plurals.reminder_deferred_summary_body,
                2,
                2,
            ),
        )

        assertThat(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0).isFalse()

        NotificationManagerCompat.from(context)
            .notify(9_004, summary)

        val posted = posted(9_004)
        assertThat(posted).isNotNull()
        assertThat(posted!!.group).isEqualTo(ReminderChannels.remindersGroupKey)
        // The public copy carries nothing cycle-derived at all.
        assertThat(
            posted.publicVersion!!.extras.getCharSequence(Notification.EXTRA_TEXT),
        ).isNull()
    }

    @Test
    fun everyReminderTypeHasRedactedCopyAndNoneIsTheCycleBody() {
        val cycleLike = setOf(cycleBody, "Your period is likely between 09:00 and 10:00.", "09:00")

        for (type in ReminderType.entries) {
            val redacted = ReminderNotificationTemplates.redactedContent(
                context = context,
                channelId = channelId,
                type = type,
            )
            val text = redacted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

            assertThat(text).isNotEmpty()
            for (value in cycleLike) {
                assertThat(text).doesNotContain(value)
            }
        }
        // The daily-log label is its own copy, not the generic one.
        val daily = ReminderNotificationTemplates.redactedContent(
            context = context,
            channelId = channelId,
            type = ReminderType.DailyLog,
        )
        assertThat(daily.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            .isEqualTo(context.getString(R.string.reminder_redacted_daily_log))
    }
}
