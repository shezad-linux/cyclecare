package com.cyclecare.app

import android.content.Context
import android.util.Log
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.CrashReporter
import com.cyclecare.core.domain.NoOpCrashReporter
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

private const val breadcrumbKey = "breadcrumb"
private val sensitiveAttributeTokens = listOf(
    "note",
    "notes",
    "pin",
    "path",
    "body",
    "medication",
    "symptom",
)

class FirebaseBackedCrashReporter private constructor(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {
    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    override fun logBreadcrumb(name: String, attributes: Map<String, String>) {
        crashlytics.log(formatDiagnosticLine(name, sanitizeAttributes(attributes)))
    }

    override fun recordNonFatal(
        name: String,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, String>,
    ) {
        val sanitizedAttributes = sanitizeAttributes(attributes)
        crashlytics.setCustomKey("event", name)
        crashlytics.setCustomKey("message", message)
        sanitizedAttributes.forEach { (key, value) -> crashlytics.setCustomKey(key, value) }
        crashlytics.log(formatDiagnosticLine(name, sanitizedAttributes))
        crashlytics.recordException(
            DiagnosticException(name = name, message = message, cause = throwable),
        )
    }

    companion object {
        fun createOrNull(context: Context): CrashReporter? {
            val app = runCatching {
                FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            }.getOrNull() ?: return null
            return runCatching {
                FirebaseCrashlytics.getInstance().apply {
                    setCrashlyticsCollectionEnabled(true)
                    setCustomKey("firebase_app", app.name)
                }
            }.getOrNull()?.let(::FirebaseBackedCrashReporter)
        }
    }
}

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

fun createCrashReporter(context: Context): CrashReporter =
    FirebaseBackedCrashReporter.createOrNull(context) ?: NoOpCrashReporter

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

private class DiagnosticException(
    name: String,
    message: String,
    cause: Throwable?,
) : RuntimeException("$name: $message", cause)
