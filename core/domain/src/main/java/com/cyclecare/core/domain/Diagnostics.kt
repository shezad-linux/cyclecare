package com.cyclecare.core.domain

interface CrashReporter {
    fun setCustomKey(key: String, value: String)
    fun logBreadcrumb(name: String, attributes: Map<String, String> = emptyMap())
    fun recordNonFatal(
        name: String,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, String> = emptyMap(),
    )
}

object NoOpCrashReporter : CrashReporter {
    override fun setCustomKey(key: String, value: String) = Unit

    override fun logBreadcrumb(name: String, attributes: Map<String, String>) = Unit

    override fun recordNonFatal(
        name: String,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, String>,
    ) = Unit
}

interface AppLogger {
    fun debug(tag: String, event: String, attributes: Map<String, String> = emptyMap())
    fun info(tag: String, event: String, attributes: Map<String, String> = emptyMap())
    fun warn(
        tag: String,
        event: String,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    )

    fun error(
        tag: String,
        event: String,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null,
    )
}

object NoOpAppLogger : AppLogger {
    override fun debug(tag: String, event: String, attributes: Map<String, String>) = Unit

    override fun info(tag: String, event: String, attributes: Map<String, String>) = Unit

    override fun warn(
        tag: String,
        event: String,
        attributes: Map<String, String>,
        throwable: Throwable?,
    ) = Unit

    override fun error(
        tag: String,
        event: String,
        attributes: Map<String, String>,
        throwable: Throwable?,
    ) = Unit
}
