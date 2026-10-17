package com.cyclecare.core.domain.time

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Explicit time source for domain logic.
 *
 * Domain engines must never read the system clock directly. The
 * prediction engine in particular recomputes on every Flow collection,
 * so an ambient clock would make production output non-deterministic
 * and would make rollover, stale-history and overdue behaviour
 * untestable.
 *
 * Every estimate therefore carries the date it was computed for, and
 * every test injects a fixed provider.
 */
interface TimeProvider {
    fun today(): LocalDate
    fun now(): ZonedDateTime
    fun nowEpochMillis(): Long
}

/**
 * Production implementation. The only place in the domain layer that is
 * allowed to observe wall-clock time.
 */
class SystemTimeProvider(
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : TimeProvider {
    override fun today(): LocalDate = LocalDate.now(zone())

    override fun now(): ZonedDateTime = ZonedDateTime.now(zone())

    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}