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
 * The persisted half of criterion 6, spec `13` §12: a retried delivery
 * must never post a second time.
 *
 * `upsert` overwrites every column, so a retry that carried stale counts
 * would silently rewrite history. The posting path instead calls
 * `insertIfAbsent`, whose IGNORE conflict strategy returns 0 when the
 * event id — deterministic, `"$ruleId-$scheduledAtEpochMillis"` — is
 * already present. The id is the lock, and this test proves the lock
 * holds.
 */
@RunWith(AndroidJUnit4::class)
class ReminderInsertIfAbsentTest {

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

    private fun event(id: String, deliveredAtEpochMillis: Long) = ReminderEventEntity(
        id = id,
        ruleId = "rule-1",
        type = "PeriodPrep",
        channelKey = "CycleAlerts",
        scheduledAtEpochMillis = deliveredAtEpochMillis,
        deliveredAtEpochMillis = deliveredAtEpochMillis,
        title = "original title",
        body = "original body",
        privateBody = "original private",
        deepLinkTarget = "Today",
        targetEpochDay = null,
        readAtEpochMillis = null,
        dismissedAtEpochMillis = null,
        deliveryState = "DELIVERED",
    )

    @Test
    fun insertsAnEventThatIsNotThereYet() = runBlocking {
        val inserted = database.reminderEventDao().insertIfAbsent(
            event("rule-1-1000", deliveredAtEpochMillis = 1_000L),
        )

        // Room returns the new row id, not a constant.
        assertThat(inserted).isEqualTo(1L)
        assertThat(database.reminderEventDao().countDeliveredForType("PeriodPrep")).isEqualTo(1)
    }

    @Test
    fun refusesToOverwriteAnEventWithTheSameId() = runBlocking {
        database.reminderEventDao().insertIfAbsent(
            event("rule-1-1000", deliveredAtEpochMillis = 1_000L),
        )

        val retry = event("rule-1-1000", deliveredAtEpochMillis = 9_000L)
        val inserted = database.reminderEventDao().insertIfAbsent(retry)

        // -1 is Room's "ignored" answer on conflict: the retry returned
        // "not inserted", and the original row won — the stored delivery
        // time is the first post's, not the retry's.
        assertThat(inserted).isEqualTo(-1L)
        assertThat(database.reminderEventDao().latestDeliveryAtSince(0L)).isEqualTo(1_000L)
    }

    @Test
    fun allowsADifferentScheduleTimeOfTheSameRule() = runBlocking {
        database.reminderEventDao().insertIfAbsent(
            event("rule-1-1000", deliveredAtEpochMillis = 1_000L),
        )

        val next = event("rule-1-2000", deliveredAtEpochMillis = 2_000L)
        val inserted = database.reminderEventDao().insertIfAbsent(next)

        // A second row's id is 2, not 1 — the return is the row id.
        assertThat(inserted).isEqualTo(2L)
        assertThat(database.reminderEventDao().countDeliveredForType("PeriodPrep")).isEqualTo(2)
    }
}
