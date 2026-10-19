package com.cyclecare.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.database.CycleCareDatabase
import com.cyclecare.core.database.ReminderEventEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 15 §11.3 condition 5 asks whether a reminder was delivered in the
 * last 30 minutes.
 *
 * The existing `observeRecent` query filters out dismissed events, which
 * would hide a reminder that was delivered and then dismissed. A dismissed
 * reminder was still delivered, and dismissing it is not the user asking
 * for another interruption, so this query deliberately does not filter.
 */
@RunWith(AndroidJUnit4::class)
class ReminderDeliveryLookupTest {

    private lateinit var database: CycleCareDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun event(
        id: String,
        deliveredAtEpochMillis: Long,
        dismissedAtEpochMillis: Long? = null,
    ) = ReminderEventEntity(
        id = id,
        ruleId = "rule-1",
        type = "PeriodPrep",
        channelKey = "CycleAlerts",
        scheduledAtEpochMillis = deliveredAtEpochMillis,
        deliveredAtEpochMillis = deliveredAtEpochMillis,
        title = "title",
        body = "body",
        privateBody = "private",
        deepLinkTarget = "Today",
        targetEpochDay = null,
        readAtEpochMillis = null,
        dismissedAtEpochMillis = dismissedAtEpochMillis,
        deliveryState = "DELIVERED",
    )

    @Test
    fun returnsNull_whenNoReminderHasBeenDelivered() = runBlocking {
        val result = database.reminderEventDao().latestDeliveryAtSince(0L)
        assertThat(result).isNull()
    }

    @Test
    fun returnsTheDeliveryTime_whenAReminderWasDeliveredInTheWindow() = runBlocking {
        database.reminderEventDao().upsert(event("event-1", deliveredAtEpochMillis = 5_000L))

        val result = database.reminderEventDao().latestDeliveryAtSince(1_000L)

        assertThat(result).isEqualTo(5_000L)
    }

    @Test
    fun returnsNull_whenTheOnlyReminderWasDeliveredBeforeTheWindow() = runBlocking {
        database.reminderEventDao().upsert(event("event-1", deliveredAtEpochMillis = 500L))

        val result = database.reminderEventDao().latestDeliveryAtSince(1_000L)

        assertThat(result).isNull()
    }

    @Test
    fun returnsTheMostRecentDelivery_whenSeveralAreInTheWindow() = runBlocking {
        database.reminderEventDao().upsert(event("event-1", deliveredAtEpochMillis = 1_000L))
        database.reminderEventDao().upsert(event("event-2", deliveredAtEpochMillis = 3_000L))
        database.reminderEventDao().upsert(event("event-3", deliveredAtEpochMillis = 2_000L))

        val result = database.reminderEventDao().latestDeliveryAtSince(0L)

        assertThat(result).isEqualTo(3_000L)
    }

    @Test
    fun aDismissedReminderStillCountsAsDelivered() = runBlocking {
        database.reminderEventDao().upsert(
            event("event-1", deliveredAtEpochMillis = 5_000L, dismissedAtEpochMillis = 6_000L),
        )

        val result = database.reminderEventDao().latestDeliveryAtSince(1_000L)

        assertThat(result).isEqualTo(5_000L)
    }
}
