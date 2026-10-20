package com.cyclecare.app

import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.PredictionRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ReminderRepository
import com.cyclecare.core.domain.ReminderScheduler
import com.cyclecare.core.domain.InMemoryReminderIntelligenceRepository
import com.cyclecare.core.domain.notification.INTERRUPTION_FILTER_ALL
import com.cyclecare.core.domain.notification.INTERRUPTION_FILTER_PRIORITY
import com.cyclecare.core.domain.notification.MAX_SENDS_PER_DAY
import com.cyclecare.core.domain.notification.MAX_SENDS_PER_WEEK
import com.cyclecare.core.domain.notification.DeliveryGateOutcome
import com.cyclecare.core.model.ReminderEvent
import com.cyclecare.core.model.ReminderRule
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The delivery decision, on the JVM.
 *
 * Spec `13` §17 criteria 1, 2, 3 and 11 are about what the delivery path
 * does *not* do: it does not post a candidate that lost its score, it
 * does not exceed the daily or the weekly budget, it does not keep
 * sending a type the user has stopped opening, and it does not interrupt
 * a quiet hour or a do-not-disturb.
 *
 * Each of those is a decision about data the domain cannot fetch for
 * itself, so the app layer resolves the data and hands it over. These
 * tests drive that hand-over with fake repositories, which is why they
 * need neither a device nor an emulator.
 */
class ReminderDeliveryPolicyTest {

    private val zone = ZoneId.of("UTC")

    /** Noon, so quiet hours are never accidentally in play. */
    private val now = ZonedDateTime.of(2026, 3, 17, 12, 0, 0, 0, zone)

    @Test
    fun `a candidate that clears the threshold is delivered`() = runBlocking {
        val events = FakeReminderEventsRepository()

        val outcome = decide(events = events, type = ReminderType.PeriodPrep)

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `a candidate below the send threshold is not delivered`() = runBlocking {
        val events = FakeReminderEventsRepository()
        // A week out, with a low-quality estimate: the score falls under
        // SEND_THRESHOLD, so the candidate is not worth an interruption.
        val payload = payload(leadDays = 7)

        val outcome = decide(
            events = events,
            type = ReminderType.PeriodPrep,
            payload = payload,
            quality = com.cyclecare.core.model.PredictionQuality.LOW,
        )

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("below the delivery threshold")
    }

    @Test
    fun `the daily budget is enforced against the event history`() = runBlocking {
        val events = FakeReminderEventsRepository(sendsInLast24h = MAX_SENDS_PER_DAY)

        val outcome = decide(events = events, type = ReminderType.PeriodPrep)

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("daily budget exhausted")
    }

    @Test
    fun `the weekly budget is enforced against the event history`() = runBlocking {
        val events = FakeReminderEventsRepository(sendsInLast7d = MAX_SENDS_PER_WEEK)

        val outcome = decide(events = events, type = ReminderType.PeriodPrep)

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("weekly budget exhausted")
    }

    @Test
    fun `a type that crossed the auto-disable threshold is not sent`() = runBlocking {
        val events = FakeReminderEventsRepository()
        val intelligence = InMemoryReminderIntelligenceRepository()
        intelligence.disable(
            type = ReminderType.PeriodPrep,
            dismissedCount = 8,
            openedCount = 0,
            disabledAtEpochMillis = now.toInstant().toEpochMilli(),
        )

        val outcome = decide(
            events = events,
            type = ReminderType.PeriodPrep,
            intelligence = intelligence,
        )

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Suppress::class.java)
        assertThat((outcome as DeliveryGateOutcome.Suppress).reason)
            .isEqualTo("type auto-disabled")
    }

    @Test
    fun `the auto-disable threshold needs both the impressions and the dismiss rate`() {
        val tooFewImpressions = evaluateAutoDisableDecision(
            impressions = 9,
            dismissed = 9,
            opened = 0,
        )
        val notDismissedEnough = evaluateAutoDisableDecision(
            impressions = 10,
            dismissed = 5,
            opened = 5,
        )
        val crossed = evaluateAutoDisableDecision(
            impressions = 10,
            dismissed = 7,
            opened = 1,
        )

        assertThat(tooFewImpressions.shouldDisable).isFalse()
        assertThat(notDismissedEnough.shouldDisable).isFalse()
        assertThat(crossed.shouldDisable).isTrue()
    }

    @Test
    fun `a paused type can be re-enabled with one tap`() = runBlocking {
        val intelligence = InMemoryReminderIntelligenceRepository()
        intelligence.disable(
            type = ReminderType.PeriodPrep,
            dismissedCount = 8,
            openedCount = 0,
            disabledAtEpochMillis = now.toInstant().toEpochMilli(),
        )
        val rules = FakeReminderRepository()
        val scheduler = RecordingReminderScheduler()

        reEnableReminderType(
            reminderRepository = rules,
            intelligence = intelligence,
            scheduler = scheduler,
            type = ReminderType.PeriodPrep,
        )

        assertThat(rules.enabledTypes).containsExactly(ReminderType.PeriodPrep)
        assertThat(intelligence.isAutoDisabled(ReminderType.PeriodPrep)).isFalse()
        assertThat(scheduler.refreshCount).isEqualTo(1)
    }

    @Test
    fun `a candidate inside quiet hours is deferred rather than delivered`() = runBlocking {
        val events = FakeReminderEventsRepository()
        val atNight = now.withHour(23)

        val outcome = decide(
            events = events,
            type = ReminderType.PeriodPrep,
            deliveredAt = atNight,
        )

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Defer::class.java)
        val defer = outcome as DeliveryGateOutcome.Defer
        assertThat(defer.reason).isEqualTo("quiet hours")
        // Deferred to the open end of the quiet window, not dropped.
        assertThat(defer.deferredUntilEpochMillis)
            .isGreaterThan(atNight.toInstant().toEpochMilli())
    }

    @Test
    fun `a candidate under do not disturb is deferred rather than delivered`() = runBlocking {
        val events = FakeReminderEventsRepository()

        val outcome = decide(
            events = events,
            type = ReminderType.PeriodPrep,
            interruptionFilter = INTERRUPTION_FILTER_PRIORITY,
        )

        assertThat(outcome).isInstanceOf(DeliveryGateOutcome.Defer::class.java)
        assertThat((outcome as DeliveryGateOutcome.Defer).reason).isEqualTo("do not disturb")
    }

    @Test
    fun `the late period reminder is the documented exception to quiet hours and dnd`() = runBlocking {
        val events = FakeReminderEventsRepository()

        val quietHoursOutcome = decide(
            events = events,
            type = ReminderType.LatePeriod,
            deliveredAt = now.withHour(23),
        )
        val dndOutcome = decide(
            events = events,
            type = ReminderType.LatePeriod,
            interruptionFilter = INTERRUPTION_FILTER_PRIORITY,
        )

        assertThat(quietHoursOutcome).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
        assertThat(dndOutcome).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    @Test
    fun `the interruption filter is data the domain receives, not state it reads`() = runBlocking {
        val events = FakeReminderEventsRepository()

        val underDnd = decide(
            events = events,
            type = ReminderType.PeriodPrep,
            interruptionFilter = INTERRUPTION_FILTER_PRIORITY,
        )
        val withoutDnd = decide(
            events = events,
            type = ReminderType.PeriodPrep,
            interruptionFilter = INTERRUPTION_FILTER_ALL,
        )

        assertThat(underDnd).isInstanceOf(DeliveryGateOutcome.Defer::class.java)
        assertThat(withoutDnd).isInstanceOf(DeliveryGateOutcome.Deliver::class.java)
    }

    private suspend fun decide(
        events: FakeReminderEventsRepository,
        type: ReminderType,
        payload: ReminderAlarmPayload = payload(),
        intelligence: InMemoryReminderIntelligenceRepository = InMemoryReminderIntelligenceRepository(),
        deliveredAt: ZonedDateTime = now,
        interruptionFilter: Int = INTERRUPTION_FILTER_ALL,
        quality: com.cyclecare.core.model.PredictionQuality =
            com.cyclecare.core.model.PredictionQuality.HIGH,
    ): DeliveryGateOutcome {
        val input = ReminderDeliveryPolicy.gateInput(
            events = events,
            profile = FakeProfileRepository(),
            prediction = FakePredictionRepository(quality),
            type = type,
            payload = payload,
            deliveredAtEpochMillis = deliveredAt.toInstant().toEpochMilli(),
            interruptionFilter = interruptionFilter,
        )
        return ReminderDeliveryPolicy.decide(
            intelligence = intelligence,
            type = type,
            input = input,
        )
    }

    private fun payload(
        leadDays: Int = 2,
        userPinnedTime: Boolean = false,
    ): ReminderAlarmPayload = ReminderAlarmPayload(
        notificationId = 42,
        title = "Period reminder",
        body = "Your period may start soon.",
        privateBody = "Cycle reminder available. Open the app to review it.",
        ruleId = "period",
        reminderType = ReminderType.PeriodPrep,
        channelKey = com.cyclecare.core.model.ReminderChannelKey.CycleAlerts,
        deepLinkTarget = com.cyclecare.core.model.ReminderDeepLinkTarget.Home,
        targetEpochDay = LocalDateOf(2026, 3, 20),
        scheduledAtEpochMillis = now.toInstant().toEpochMilli(),
        leadDays = leadDays,
        windowHalfWidthDays = 1,
        userPinnedTime = userPinnedTime,
    )

    private fun LocalDateOf(year: Int, month: Int, day: Int): Long =
        java.time.LocalDate.of(year, month, day).toEpochDay()
}

/**
 * An event history the delivery gates read.
 *
 * The counts are what the budgets and the fatigue term are computed
 * from, so they are set per test rather than derived from rows.
 */
private class FakeReminderEventsRepository(
    private val sendsInLast24h: Int = 0,
    private val sendsInLast7d: Int = 0,
    private val sendsInArbitrationWindow: Int = 0,
) : ReminderEventsRepository {
    private val rows = mutableListOf<ReminderEvent>()

    override fun observeRecent(limit: Int): Flow<List<ReminderEvent>> =
        MutableStateFlow(rows.toList()).asStateFlow().map { it.take(limit) }

    override fun observeUnreadCount(): Flow<Int> = MutableStateFlow(0).asStateFlow()

    override fun observeNextUnread(): Flow<ReminderEvent?> = MutableStateFlow<ReminderEvent?>(null).asStateFlow()

    override suspend fun recordEvent(event: ReminderEvent) {
        rows.removeAll { it.id == event.id }
        rows += event
    }

    override suspend fun markRead(id: String) = Unit

    override suspend fun markAllRead() = Unit

    override suspend fun dismiss(id: String) = Unit

    override suspend fun latestDeliveryAtSince(sinceEpochMillis: Long): Long? = null

    override suspend fun insertIfAbsent(event: ReminderEvent): Boolean {
        if (rows.any { it.id == event.id }) return false
        rows += event
        return true
    }

    override suspend fun countDeliveredBetween(fromEpochMillis: Long, toEpochMillis: Long): Int {
        // The three windows the gates ask about are a week, a day and the
        // arbitration window, so the width of the window is what picks
        // the answer.
        val windowMillis = toEpochMillis - fromEpochMillis
        return when {
            windowMillis >= 6 * MILLIS_PER_DAY -> sendsInLast7d
            windowMillis >= 12 * MILLIS_PER_HOUR -> sendsInLast24h
            else -> sendsInArbitrationWindow
        }
    }

    override suspend fun countDeliveredForType(type: ReminderType): Int = 0

    override suspend fun countDismissedForType(type: ReminderType): Int = 0

    override suspend fun countOpenedForType(type: ReminderType): Int = 0

    private companion object {
        const val MILLIS_PER_HOUR = 60L * 60_000L
        const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
    }
}

private class FakeProfileRepository : ProfileRepository {
    private val profile = MutableStateFlow<com.cyclecare.core.model.UserProfile?>(
        com.cyclecare.core.model.UserProfile(
            ageBand = com.cyclecare.core.model.AgeBand.Twenties,
            averageCycleLength = 28,
            periodLength = 5,
            lastPeriodStart = java.time.LocalDate.of(2026, 2, 20),
            trackingGoal = com.cyclecare.core.model.TrackingGoal.PeriodPrep,
            remindersEnabled = true,
            privacyModeEnabled = false,
        ),
    )

    override fun observeProfile(): Flow<com.cyclecare.core.model.UserProfile?> = profile.asStateFlow()

    override suspend fun saveProfile(profile: com.cyclecare.core.model.UserProfile) = Unit

    override suspend fun updateRemindersEnabled(enabled: Boolean) = Unit
}

private class FakePredictionRepository(
    private val quality: com.cyclecare.core.model.PredictionQuality,
) : PredictionRepository {
    private val snapshot = MutableStateFlow<com.cyclecare.core.model.PredictionSnapshot?>(
        com.cyclecare.core.model.PredictionSnapshot(
            generatedAt = java.time.LocalDate.of(2026, 3, 17),
            cycleDay = 15,
            nextPeriodDate = java.time.LocalDate.of(2026, 3, 24),
            ovulationDate = java.time.LocalDate.of(2026, 3, 19),
            fertileWindow = com.cyclecare.core.model.DateRange(
                start = java.time.LocalDate.of(2026, 3, 17),
                endInclusive = java.time.LocalDate.of(2026, 3, 22),
            ),
            confidenceLabel = "test",
            daysUntilNextPeriod = 7,
            nextPeriodWindow = com.cyclecare.core.model.DateRange(
                start = java.time.LocalDate.of(2026, 3, 23),
                endInclusive = java.time.LocalDate.of(2026, 3, 25),
            ),
            nextPeriodHalfWidthDays = 1,
            quality = quality,
        ),
    )

    override fun observePrediction(): Flow<com.cyclecare.core.model.PredictionSnapshot?> =
        snapshot.asStateFlow()
}

private class FakeReminderRepository : ReminderRepository {
    val enabledTypes = mutableListOf<ReminderType>()

    override fun observeRules(): Flow<List<ReminderRule>> = MutableStateFlow(emptyList<ReminderRule>()).asStateFlow()

    override suspend fun upsertRule(rule: ReminderRule) = Unit

    override suspend fun setRuleTypeEnabled(type: ReminderType, enabled: Boolean) {
        if (enabled) enabledTypes += type
    }
}

private class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount: Int = 0
        private set

    override suspend fun refreshSchedules(now: ZonedDateTime): List<com.cyclecare.core.model.ScheduledReminder> {
        refreshCount += 1
        return emptyList()
    }
}
