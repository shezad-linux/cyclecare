package com.cyclecare.app

import com.cyclecare.core.model.ReminderChannelKey
import com.cyclecare.core.model.ReminderType
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The delivery path's diagnostic log, checked where it is written.
 *
 * `ReminderDeliveryLogTest` asks the log's builders for their maps and
 * looks at what comes out. That is necessary and it is not enough: a
 * call site that builds its own map, or that passes an instant into a
 * builder, never reaches those builders, so nothing would fail.
 *
 * The defect this guards against is concrete. The delivery log used to
 * carry `scheduledAtEpochMillis` — the instant a reminder was scheduled
 * for, which for a `PeriodPrep` reminder is the predicted period date
 * minus the lead time, so a derived cycle date in a log that is
 * documented to carry no dates (`Diagnostics.kt:87`).
 *
 * So this test reads the delivery path's sources, finds the actual
 * logging call sites, and checks that no instant is passed to one, that
 * every attribute name is on a reviewed list, and that the values are
 * coarse buckets rather than measurements.
 *
 * It checks the other side of the rule too, which is easy to get wrong
 * in the other direction: the scheduled instant is still persisted, in
 * the event, the database column, the alarm payload and the deferral
 * store, because those are the app's records and not its diagnostics. A
 * guard that banned the word would have had to delete them as well, and
 * the correct fix is to move the instant out of the log, not out of the
 * app. So the test asserts both: no instant reaches a log call site,
 * and every persisted instant is still there.
 */
class ReminderDeliveryLoggingSourceTest {

    @Test
    fun `the scan finds the delivery path's log call sites`() {
        val sites = allLogCallSites()

        // A scan that found nothing would pass every other test here for
        // the wrong reason.
        assertThat(sites).isNotEmpty()
        assertThat(sites.map { it.file }.distinct())
            .containsAtLeast(
                "ReminderDelivery.kt",
                "ReminderDeferredSummaryReceiver.kt",
                "ReminderAutoDisableActionReceiver.kt",
                "ReminderAlarmScheduler.kt",
                "ReminderChannels.kt",
            )
        assertThat(sites.count { it.file == "ReminderDelivery.kt" }).isAtLeast(10)

        // A site that carries no attributes has to be carrying a
        // throwable instead, so a site the scan failed to parse cannot
        // pass for a site that logs nothing.
        val withoutAttributes = sites.filter { it.attributes == null }
        assertThat(withoutAttributes.all { it.hasThrowable }).isTrue()

        // A site whose attributes could not be read is a scan failure,
        // not a site with no attributes.
        assertThat(sites.filter { it.attributes == "" }).isEmpty()
    }

    @Test
    fun `no log call site is passed an instant`() {
        val offenders = allLogCallSites()
            .filter { site ->
                site.attributes != null &&
                    instantIdentifiers.any { identifier -> site.attributes.contains(identifier) }
            }
            .map { site -> "${site.file}:${site.line}" }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun `no log call site turns a time into a string`() {
        val offenders = allLogCallSites()
            .filter { site ->
                site.attributes != null &&
                    timeConversions.any { conversion -> site.attributes.contains(conversion) }
            }
            .map { site -> "${site.file}:${site.line} ${site.attributes}" }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun `every logged attribute name is on the reviewed list`() {
        val used = loggedAttributeNames()

        assertThat(used).isNotEmpty()
        assertThat(used).containsNoDuplicates()
        val unreviewed = used.filter { name -> name !in REVIEWED_ATTRIBUTE_NAMES }
        assertThat(unreviewed).isEmpty()

        // The list itself is not a loophole: a name that looks like an
        // instant does not get in by being listed.
        val instantShaped = REVIEWED_ATTRIBUTE_NAMES.filter { name -> instantShapedName.matches(name) }
        assertThat(instantShaped).isEmpty()
    }

    @Test
    fun `no logged attribute is named like a timestamp`() {
        val used = loggedAttributeNames()

        val instantShaped = used.filter { name -> instantShapedName.matches(name) }
        assertThat(instantShaped).isEmpty()

        // A minute-precision duration is a measurement, and a
        // measurement of a delay against a scheduled instant is the
        // subtraction that recovers it.
        val finelyTimed = used.filter { name -> name.endsWith("Minutes") || name.endsWith("Millis") }
        assertThat(finelyTimed).isEmpty()
    }

    @Test
    fun `the lateness the log carries is a class and not a measurement`() {
        val attributes = ReminderDeliveryLog.success(
            ruleId = "period",
            type = ReminderType.PeriodPrep,
            channelKey = ReminderChannelKey.CycleAlerts,
            leadDays = 2,
            deliveryLatency = deliveryLatencyOf(0L),
            score = 70,
            threshold = 40,
        )

        // A class, so there is nothing to subtract from the time the
        // log line carries.
        assertThat(attributes["deliveryLatency"]).isEqualTo("OnTime")
        assertThat(attributes["deliveryLatency"]?.toLongOrNull()).isNull()

        // And no minute-precision figure anywhere in the map.
        assertThat(attributes.keys.filter { name -> name.endsWith("Minutes") }).isEmpty()
    }

    @Test
    fun `the buckets the log carries are coarse`() {
        val deferred = ReminderDeliveryLog.deferred(
            ruleId = "period",
            type = ReminderType.PeriodPrep,
            reason = "quiet hours",
            deferredCount = 2,
            deferUntilHour = 8,
        )

        // An hour of the day is a bucket of twenty-four, not a moment.
        val hour = deferred["deferUntilHour"]?.toIntOrNull()
        assertThat(hour).isNotNull()
        assertThat(hour!!).isAtLeast(0)
        assertThat(hour).isAtMost(23)
    }

    @Test
    fun `the scheduled instant is still persisted`() {
        val root = repositoryRoot()

        // The event identity and the event record keep the instant: it is
        // the app's own record, not a diagnostic.
        val model = File(root, "core/model/src/main/java/com/cyclecare/core/model/CycleModels.kt").readText()
        assertThat(model).contains("scheduledAtEpochMillis: Long")
        assertThat(model).contains("scheduledAt: ZonedDateTime")

        // The database keeps the column, and the migration keeps it too.
        val database = File(root, "core/database/src/main/java/com/cyclecare/core/database/CycleCareDatabase.kt")
            .readText()
        assertThat(database).contains("scheduledAtEpochMillis")

        // The alarm payload carries it from the scheduler to the
        // delivery.
        val payload = File(root, "app/src/main/java/com/cyclecare/app/ReminderAlarmFacade.kt").readText()
        assertThat(payload).contains("keyScheduledAtEpochMillis")

        // The deferral store serialises it, so a deferred candidate can
        // still be delivered at its own time.
        val codec = File(
            root,
            "core/domain/src/main/java/com/cyclecare/core/domain/notification/ReminderIntelligenceCodec.kt",
        ).readText()
        assertThat(codec).contains("scheduledAtEpochMillis")
    }

    @Test
    fun `no persisted instant is passed to a log call site`() {
        val offenders = allLogCallSites()
            .filter { site ->
                site.attributes != null &&
                    persistedIdentifiers.any { identifier -> site.attributes.contains(identifier) }
            }
            .map { site -> "${site.file}:${site.line}" }

        assertThat(offenders).isEmpty()
    }

    // ── The scan ──────────────────────────────────────────────────────

    /** Every logging call site in the delivery path. */
    private fun allLogCallSites(): List<LogCallSite> =
        deliveryPathFiles.flatMap { file -> logCallSites(file) }

    private fun logCallSites(file: File): List<LogCallSite> {
        val text = file.readText()
        val sites = mutableListOf<LogCallSite>()
        var searchFrom = 0
        while (true) {
            val match = LOG_CALL.find(text, searchFrom) ?: break
            val open = text.indexOf('(', match.range.first)
            val close = matchingParen(text, open)
            val arguments = text.substring(open + 1, close)
            sites += LogCallSite(
                file = file.name,
                line = text.substring(0, match.range.first).count { it == '\n' } + 1,
                attributes = attributesArgument(arguments),
                hasThrowable = arguments.contains("throwable"),
            )
            searchFrom = close + 1
        }
        return sites
    }

    /**
     * The `attributes` argument of a logging call.
     *
     * Only the attributes are read: the tag, the event name and the
     * throwable are not attribute values, and the throwable is the
     * platform's own message rather than something the app composed.
     *
     * Null when the call has no attributes at all; the empty string when
     * it has one that could not be read, which is a scan failure.
     */
    private fun attributesArgument(arguments: String): String? {
        val marker = arguments.indexOf("attributes")
        if (marker < 0) return null
        val rest = arguments.substring(marker + "attributes".length).trimStart()
        if (!rest.startsWith("=")) return ""
        return topLevelArgument(rest.removePrefix("=").trimStart())
    }

    /** The value of an argument, up to the comma that ends it. */
    private fun topLevelArgument(value: String): String {
        var depth = 0
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '"' -> index = endOfStringLiteral(value, index)
                character == '/' && value.startsWith("//", index) -> {
                    val end = value.indexOf('\n', index)
                    index = if (end < 0) value.length else end
                }
                character == '/' && value.startsWith("/*", index) -> {
                    val end = value.indexOf("*/", index + 2)
                    index = if (end < 0) value.length else end + 2
                }
                character == '(' || character == '[' || character == '{' -> {
                    depth++
                    index++
                }
                character == ')' || character == ']' || character == '}' -> {
                    depth--
                    index++
                }
                character == ',' && depth == 0 -> return value.substring(0, index)
                else -> index++
            }
        }
        return value
    }

    /** The index just past the end of the string literal at [start]. */
    private fun endOfStringLiteral(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> return index + 1
                else -> index++
            }
        }
        return text.length
    }

    /** The index of the parenthesis that closes the one at [open]. */
    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        var index = open
        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' -> index = endOfStringLiteral(text, index)
                character == '/' && text.startsWith("//", index) -> {
                    val end = text.indexOf('\n', index)
                    index = if (end < 0) text.length else end
                }
                character == '/' && text.startsWith("/*", index) -> {
                    val end = text.indexOf("*/", index + 2)
                    index = if (end < 0) text.length else end + 2
                }
                character == '(' -> {
                    depth++
                    index++
                }
                character == ')' -> {
                    depth--
                    if (depth == 0) return index
                    index++
                }
                else -> index++
            }
        }
        return text.length - 1
    }

    /**
     * Every attribute name the delivery path logs.
     *
     * Two sources: the maps the log's builders declare, and the maps a
     * call site declares for itself.
     */
    private fun loggedAttributeNames(): Set<String> {
        val names = linkedSetOf<String>()
        // The builder file: every `"name" to` in it is an attribute name.
        names += attributeNamesIn(stripComments(logBuildersFile().readText()))
        // The call sites: every `"name" to` in a site's attributes.
        for (file in deliveryPathFiles) {
            for (site in logCallSites(file)) {
                names += attributeNamesIn(stripComments(site.attributes.orEmpty()))
            }
        }
        return names
    }

    /** The `"name" to` keys in a piece of source. */
    private fun attributeNamesIn(text: String): Set<String> =
        ATTRIBUTE_KEY.findAll(text).map { it.groupValues[1] }.toSet()

    private fun stripComments(text: String): String =
        text.replace(COMMENT_BLOCK, " ").replace(COMMENT_LINE, " ")

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root from ${File(".").absolutePath}")
    }

    // ── The lists ─────────────────────────────────────────────────────

    /** The sources that make up the delivery path. */
    private val deliveryPathFiles: List<File>
        get() = DELIVERY_PATH_FILE_NAMES.map { name ->
            File(repositoryRoot(), "app/src/main/java/com/cyclecare/app/$name")
        }

    /** A logging call: `logger.info(...)`, `appLogger.warn(...)` and so on. */
    private data class LogCallSite(
        val file: String,
        val line: Int,
        /** Null when the call has no attributes; empty when it could not be read. */
        val attributes: String?,
        val hasThrowable: Boolean,
    )

    /** The file that holds the delivery path's attribute builders. */
    private fun logBuildersFile(): File =
        File(repositoryRoot(), "app/src/main/java/com/cyclecare/app/ReminderDeliveryLog.kt")

    private companion object {

        /** The sources of the delivery path and its collaborators. */
        val DELIVERY_PATH_FILE_NAMES = listOf(
            "ReminderDelivery.kt",
            "ReminderDeliveryLog.kt",
            "ReminderDeferredSummaryReceiver.kt",
            "ReminderAutoDisableActionReceiver.kt",
            "ReminderAlarmScheduler.kt",
            "ReminderChannels.kt",
        )

        val LOG_CALL = Regex("""\b(?:appLogger|logger)\.(?:info|warn|error|debug)\s*\(""")

        /** A `"name" to` pair in a map. */
        val ATTRIBUTE_KEY = Regex(""""([^"\n]+)"\s*to\b""")

        val COMMENT_BLOCK = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val COMMENT_LINE = Regex("""//[^\n]*""")

        /**
         * Identifiers that carry an instant.
         *
         * None of them may appear in the attributes of a logging call.
         * The scheduled and delivered instants are the two the delivery
         * path has to hand around, and the rest are the shapes a
         * date would take if it were formatted.
         */
        val instantIdentifiers = listOf(
            "scheduledAt",
            "deliveredAt",
            "deferredUntilEpochMillis",
            "EpochMilli",
            "EpochDay",
            "Instant",
            "ZonedDateTime",
            "OffsetDateTime",
            "LocalDate",
            "timestamp",
            "Timestamp",
        )

        /** Calls that turn a time into something loggable. */
        val timeConversions = listOf(
            "toEpochMilli",
            "toEpochDay",
            "toLocalDate",
            "toLocalDateTime",
            "toInstant",
            "atZone",
            "DateTimeFormatter",
            "SimpleDateFormat",
            "Calendar",
        )

        /**
         * The identifiers the app persists the instant under.
         *
         * They are the ones that must not reach a log call site, and
         * they must still be there afterwards.
         */
        val persistedIdentifiers = listOf(
            "scheduledAtEpochMillis",
            "deliveredAtEpochMillis",
            "targetEpochDay",
        )

        /** An attribute name that looks like an instant rather than a bucket. */
        val instantShapedName = Regex("(?i)^(.*(epoch|milli|timestamp|instant|date).*)$")

        /**
         * Every attribute name the delivery path is allowed to log.
         *
         * Adding a name here is a deliberate act: the list is the record
         * of what the diagnostics are known to carry. The identifiers
         * are the rule, the type, the channel, the reason from a closed
         * set, the counts, the scorer's own numbers and the buckets.
         */
        val REVIEWED_ATTRIBUTE_NAMES = setOf(
            "ruleId",
            "type",
            "channelKey",
            "reason",
            "outcome",
            "deferredCount",
            "deferUntilHour",
            "leadDays",
            "deliveryLatency",
            "score",
            "threshold",
            "impressions",
            "dismissed",
            "opened",
            "count",
            "types",
            "tier",
            "zone",
            "exactAlarms",
            "preset",
            "what",
            "extra",
        )
    }
}
