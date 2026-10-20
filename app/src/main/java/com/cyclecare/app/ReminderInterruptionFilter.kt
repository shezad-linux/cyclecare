package com.cyclecare.app

import android.app.NotificationManager
import android.content.Context
import com.cyclecare.core.domain.notification.INTERRUPTION_FILTER_ALL

/**
 * The system's current interruption filter.
 *
 * Spec `13` §10: the delivery path has to know whether the device is in
 * do-not-disturb before it posts. Reading the filter needs the Android
 * framework, and `core:domain` is a pure JVM module, so the filter is
 * read here and handed to the domain as a number.
 *
 * Injectable so the decision can be driven from a test with no device:
 * a test says what the filter is, and the domain never finds out.
 */
internal fun interface InterruptionFilterSource {
    /** The platform's `NotificationManager.getCurrentInterruptionFilter()`. */
    fun currentInterruptionFilter(): Int
}

/** The real filter, read from the platform. */
internal class AndroidInterruptionFilterSource(
    private val context: Context,
) : InterruptionFilterSource {
    override fun currentInterruptionFilter(): Int =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            ?: INTERRUPTION_FILTER_ALL
}

/**
 * The interruption filter, translated outside the domain layer.
 *
 * Kept as a function for the call sites that have a context and no other
 * reason to hold a source object.
 */
internal fun interruptionFilterFor(context: Context): Int =
    AndroidInterruptionFilterSource(context).currentInterruptionFilter()
