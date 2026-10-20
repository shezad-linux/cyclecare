package com.cyclecare.app

import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderDeepLinkTarget
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Spec `13` §12: a retried delivery never produces a second
 * notification.
 *
 * The behaviour used to hold by coincidence — a deterministic event id,
 * an upsert, and a stable notification id. It now holds because of one
 * explicit mechanism: the event row is inserted first, and the insert is
 * refused when the row is already there. A retry finds the row and stops
 * before the notification is posted.
 *
 * The post is a closure in the seam, so the test can count the posts
 * without building an Android notification.
 */
class ReminderDeliveryRetryTest {

    private val deliveredAt = ZonedDateTime.parse("2026-03-17T09:00:00Z")
    private val scheduledAt = ZonedDateTime.parse("2026-03-17T09:00:00Z")

    @Test
    fun `delivering the same payload twice records one event row and posts once`() = runBlocking {
        val events = RecordingReminderEventsRepository()
        val event = event()
        var posts = 0

        val first = publishOnce(events, event) {
            posts += 1
            ReminderNotificationPostResult.Posted
        }
        val second = publishOnce(events, event) {
            posts += 1
            ReminderNotificationPostResult.Posted
        }

        assertThat(first).isEqualTo(DeliveryPublishOutcome.Posted)
        assertThat(second).isEqualTo(DeliveryPublishOutcome.Deduplicated)
        assertThat(events.rows).hasSize(1)
        assertThat(posts).isEqualTo(1)
    }

    @Test
    fun `the retry after a lost post does not post again`() = runBlocking {
        val events = RecordingReminderEventsRepository()
        val event = event()
        var posts = 0

        // The first attempt recorded the row and then lost the post.
        publishOnce(events, event) {
            posts += 1
            ReminderNotificationPostResult.Failed(IllegalStateException("notify failed"))
        }
        // The retry finds the row and stops.
        val retry = publishOnce(events, event) {
            posts += 1
            ReminderNotificationPostResult.Posted
        }

        assertThat(retry).isEqualTo(DeliveryPublishOutcome.Deduplicated)
        assertThat(events.rows).hasSize(1)
        assertThat(posts).isEqualTo(1)
    }

    @Test
    fun `two different deliveries of the same rule are two different rows`() = runBlocking {
        val events = RecordingReminderEventsRepository()
        var posts = 0

        publishOnce(events, event(scheduledAt = scheduledAt)) { posts += 1; ReminderNotificationPostResult.Posted }
        publishOnce(events, event(scheduledAt = scheduledAt.plusDays(1))) { posts += 1; ReminderNotificationPostResult.Posted }

        assertThat(events.rows).hasSize(2)
        assertThat(posts).isEqualTo(2)
    }

    private fun event(
        scheduledAt: ZonedDateTime = this.scheduledAt,
    ): ReminderEvent = ReminderEvent(
        id = "period-${scheduledAt.toInstant().toEpochMilli()}",
        ruleId = "period",
        type = ReminderType.PeriodPrep,
        channelKey = ReminderChannelKey.CycleAlerts,
        scheduledAt = scheduledAt,
        deliveredAt = deliveredAt,
        title = "Period reminder",
        body = "Your period may start soon.",
        privateBody = "Cycle reminder available. Open the app to review it.",
        deepLinkTarget = ReminderDeepLinkTarget.Home,
        targetEpochDay = LocalDate.of(2026, 3, 20).toEpochDay(),
    )
}

/**
 * An event history that refuses a duplicate row.
 *
 * The same rule the Room DAO implements: the insert is refused when the
 * id is already there.
 */
private class RecordingReminderEventsRepository : ReminderEventsRepository {
    val rows = mutableListOf<ReminderEvent>()
    private val state = MutableStateFlow<List<ReminderEvent>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> = state.map { it.take(limit) }

    override fun observeUnreadCount(): Flow<Int> = state.map { events -> events.count { it.readAtEpochMillis == null } }

    override fun observeNextUnread(): Flow<ReminderEvent?> = state.map { events ->
        events.firstOrNull { it.readAtEpochMillis == null && it.dismissedAtEpochMillis == null }
    }

    override suspend fun recordEvent(event: ReminderEvent) {
        rows.removeAll { it.id == event.id }
        rows += event
        state.value = rows.toList()
    }

    override suspend fun markRead(id: String) = Unit

    override suspend fun markAllRead() = Unit

    override suspend fun dismiss(id: String) = Unit

    override suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long? =
        rows.maxOfOrNull { it.deliveredAt.toInstant().toEpochMilli() }

    override suspend fun insertIfAbsent(event: ReminderEvent): Boolean {
        if (rows.any { it.id == event.id }) return false
        rows += event
        state.value = rows.toList()
        return true
    }

    override suspend fun countDeliveredBetween(fromEpochMillis: Long, toEpochMillis: Long): Int = 0

    override suspend fun countDeliveredForType(type: ReminderType): Int = rows.count { it.type == type }

    override suspend fun countDismissedForType(type: ReminderType): Int = 0

    override suspend fun countOpenedForType(type: ReminderType): Int = 0
}
