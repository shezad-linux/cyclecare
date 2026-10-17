package com.cyclecare.core.testing

import com.cyclecare.core.domain.time.TimeProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic time source for tests.
 *
 * The prediction engine, the reminder planner and the notification
 * scoring model all take their current date from a TimeProvider rather
 * than reading the system clock, so that midnight rollover, stale
 * history, overdue windows and quiet hours are all reachable in a unit
 * test instead of only being observable in production.
 *
 * The instant is stored rather than a bare date so that notification
 * tests can pin an hour of the day, which is what the learned send-time
 * and quiet-hours logic operates on.
 */
class FixedTimeProvider(
    private val instant: ZonedDateTime,
) : TimeProvider {

    constructor(
        date: LocalDate,
        zone: ZoneId = ZoneId.of("UTC"),
    ) : this(date.atStartOfDay(zone))

    override fun today(): LocalDate = instant.toLocalDate()

    override fun now(): ZonedDateTime = instant

    override fun nowEpochMillis(): Long = instant.toInstant().toEpochMilli()

    fun advanceDays(days: Long): FixedTimeProvider = FixedTimeProvider(instant.plusDays(days))

    fun advanceHours(hours: Long): FixedTimeProvider = FixedTimeProvider(instant.plusHours(hours))

    companion object {
        fun at(
            date: String,
            hour: Int = 0,
            minute: Int = 0,
            second: Int = 0,
            zone: ZoneId = ZoneId.of("UTC"),
        ): FixedTimeProvider = FixedTimeProvider(
            LocalDate.parse(date).atTime(hour, minute, second).atZone(zone),
        )
    }
}

/** Readable call site for the common midnight case. */
fun fixedDate(date: String, zone: ZoneId = ZoneId.of("UTC")): FixedTimeProvider =
    FixedTimeProvider.at(date, zone = zone)