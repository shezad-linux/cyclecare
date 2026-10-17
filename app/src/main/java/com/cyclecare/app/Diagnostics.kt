package com.cyclecare.app

import android.util.Log
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.CrashReporter
import com.cyclecare.core.domain.NoOpCrashReporter

private const val breadcrumbKey = "breadcrumb"

// Attribute keys matching any of these are replaced before being logged.
// Note this scrubs by KEY name only. It cannot know that a caller put a
// symptom value into an innocuously-named key, so callers remain
// responsible for never passing health content as an attribute value.
private val sensitiveAttributeTokens = listOf(
    "note",
    "notes",
    "pin",
    "path",
    "body",
    "medication",
    "symptom",
)

class AndroidAppLogger(
    private val crashReporter: CrashReporter,
) : AppLogger {
    override fun debug(tag: String, event: String, attributes: Map<String, String>) {
        if (!BuildConfig.DEBUG) return
        Log.d(tag, formatDiagnosticLine(event, sanitizeAttributes(attributes)))
        crashReporter.logBreadcrumb("$tag:$event", sanitizeAttributes(attributes) + (breadcrumbKey to "debug"))
    }

    override fun info(tag: String, event: String, attributes: Map<String, String>) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, formatDiagnosticLine(event, sanitizeAttributes(attributes)))
        }
        crashReporter.logBreadcrumb("$tag:$event", sanitizeAttributes(attributes) + (breadcrumbKey to "info"))
    }

    override fun warn(
        tag: String,
        event: String,
        attributes: Map<String, String>,
        throwable: Throwable?,
    ) {
        val sanitizedAttributes = sanitizeAttributes(attributes)
        val message = formatDiagnosticLine(event, sanitizedAttributes)
        Log.w(tag, message, throwable)
        crashReporter.recordNonFatal(
            name = "$tag:$event",
            message = message,
            throwable = throwable,
            attributes = sanitizedAttributes + ("severity" to "warn"),
        )
    }

    override fun error(
        tag: String,
        event: String,
        attributes: Map<String, String>,
        throwable: Throwable?,
    ) {
        val sanitizedAttributes = sanitizeAttributes(attributes)
        val message = formatDiagnosticLine(event, sanitizedAttributes)
        Log.e(tag, message, throwable)
        crashReporter.recordNonFatal(
            name = "$tag:$event",
            message = message,
            throwable = throwable,
            attributes = sanitizedAttributes + ("severity" to "error"),
        )
    }
}

/**
 * No crash data leaves the device.
 *
 * Firebase Crashlytics was removed because the Gradle plugins are applied
 * conditionally on the presence of app/google-services.json, which is not
 * in version control — so the dependency shipped Firebase components in
 * the merged manifest while never initializing them.
 *
 * The CrashReporter interface is retained as the seam for a future
 * opt-in decision. Any replacement must be reviewed against
 * CycleCare-Redesign-Docs/04-security-and-privacy.md first: a
 * reproductive-health app should ship the minimum, and diagnostics must
 * never carry period dates, symptoms, notes, medication or free text.
 */
fun createCrashReporter(): CrashReporter = NoOpCrashReporter

private fun sanitizeAttributes(attributes: Map<String, String>): Map<String, String> =
    attributes.mapValues { (key, value) ->
        if (sensitiveAttributeTokens.any { token -> key.contains(token, ignoreCase = true) }) {
            "<redacted>"
        } else {
            value.take(120)
        }
    }

private fun formatDiagnosticLine(event: String, attributes: Map<String, String>): String {
    if (attributes.isEmpty()) return event
    val serializedAttributes = attributes.entries.joinToString(separator = ", ") { (key, value) -> "$key=$value" }
    return "$event [$serializedAttributes]"
}

