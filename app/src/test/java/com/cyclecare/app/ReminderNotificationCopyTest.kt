package com.cyclecare.app

import com.cyclecare.core.domain.ReminderDefaults
import com.cyclecare.core.domain.ReminderSchedulePlanner
import com.cyclecare.core.model.DateRange
import com.cyclecare.core.model.PredictionQuality
import com.cyclecare.core.model.PredictionSnapshot
import com.cyclecare.core.model.ReminderType
import com.cyclecare.core.model.TrackingGoal
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * P7-06, the part that is not an ambiguity: the notification copy.
 *
 * Spec `13` §17.12: no guilt, streak, or false-precision copy anywhere.
 * The scope here is the notification copy only — the words a notification
 * can carry, and the words the planner puts in a reminder. The Logging
 * feature's positive counters are a different question and are not
 * touched by this test.
 *
 * The banned list comes from `13` §15, `03` §2.5 and `05` §5: no streak
 * language, no guilt, no false precision, and no implication that the
 * app knows anything about pregnancy safety.
 */
class ReminderNotificationCopyTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val planner = ReminderSchedulePlanner(zone)
    private val now = ZonedDateTime.of(2026, 3, 10, 8, 0, 0, 0, zone)

    @Test
    fun `the copy every reminder carries is free of guilt streaks and false precision`() {
        val copy = plannerCopy()

        assertThat(copy).isNotEmpty()
        for ((source, text) in copy) {
            for (banned in BANNED_PHRASES) {
                assertThat(banned.containsMatchIn(text)).isFalse()
            }
            // The check above only proves the phrase is absent; the
            // source is named so a failure says which copy broke.
            assertThat(text).isNotEmpty()
            assertThat(source).isNotEmpty()
        }
    }

    @Test
    fun `the notification strings are free of guilt streaks and false precision`() {
        val strings = notificationStrings()

        assertThat(strings).isNotEmpty()
        for ((name, text) in strings) {
            for (banned in BANNED_PHRASES) {
                assertThat(banned.containsMatchIn(text)).isFalse()
            }
            assertThat(text).isNotEmpty()
            assertThat(name).isNotEmpty()
        }
    }

    @Test
    fun `the estimate copy hedges rather than asserting a date`() {
        val copy = plannerCopy().filter { it.first.contains("PeriodPrep") }

        // "Your period may start …", not "Your period starts …".
        assertThat(copy.map { it.second }).containsNoneOf(
            "Your period starts tomorrow.",
            "Your period will start tomorrow.",
        )
    }

    /**
     * The title and body of every reminder the planner produces.
     */
    private fun plannerCopy(): List<Pair<String, String>> {
        val schedules = planner.buildSchedules(
            prediction = prediction(),
            rules = ReminderDefaults.standardRules(
                enabled = true,
                trackingGoal = TrackingGoal.ConceptionAwareness,
            ),
            now = now,
        )
        assertThat(schedules).isNotEmpty()
        return schedules.flatMap { schedule ->
            listOf(
                "${schedule.type}.title" to schedule.title,
                "${schedule.type}.body" to schedule.body,
                "${schedule.type}.privateBody" to schedule.privateBody,
            )
        }
    }

    /**
     * The strings a notification can carry.
     *
     * Only the notification copy is in scope: the settings labels are
     * user-interface copy and are not what a lock screen shows.
     */
    private fun notificationStrings(): List<Pair<String, String>> {
        val resources = readStringResources()
        return NOTIFICATION_STRING_NAMES.map { name ->
            val value = resources[name]
            assertThat(value).isNotNull()
            name to value.orEmpty()
        }
    }

    private fun readStringResources(): Map<String, String> {
        val file = stringResourcesFile()
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val root = document.documentElement
        val values = mutableMapOf<String, String>()
        val nodes = root.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element) {
                val name = node.getAttribute("name")
                if (name.isNotEmpty()) {
                    values[name] = node.textContent.trim()
                }
            }
        }
        return values
    }

    private fun stringResourcesFile(): File {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("strings.xml not found from ${File(".").absolutePath}")
    }

    private fun prediction(): PredictionSnapshot = PredictionSnapshot(
        generatedAt = LocalDate.of(2026, 3, 10),
        cycleDay = 12,
        nextPeriodDate = LocalDate.of(2026, 3, 28),
        ovulationDate = LocalDate.of(2026, 3, 14),
        fertileWindow = DateRange(
            start = LocalDate.of(2026, 3, 10),
            endInclusive = LocalDate.of(2026, 3, 15),
        ),
        confidenceLabel = "test",
        daysUntilNextPeriod = 18,
        nextPeriodWindow = DateRange(
            start = LocalDate.of(2026, 3, 27),
            endInclusive = LocalDate.of(2026, 3, 29),
        ),
        nextPeriodHalfWidthDays = 1,
        quality = PredictionQuality.HIGH,
    )

    private companion object {
        /**
         * The phrases the specification bans.
         *
         * Each one is matched anywhere in the copy, so a copy that
         * contains the phrase is rejected whether it is at the start, in
         * the middle or at the end.
         */
        val BANNED_PHRASES = listOf(
            // Streaks and counters.
            Regex("""(?i)\bstreak\b"""),
            Regex("""(?i)\bday\s+\d+\s+of\b"""),
            // Guilt.
            Regex("""(?i)\bmiss(ed|ing)?\b"""),
            Regex("""(?i)\byou\s+forgot\b"""),
            Regex("""(?i)\bdon'?t\s+forget\b"""),
            Regex("""(?i)\byou\s+should\s+have\b"""),
            Regex("""(?i)\bwhy\s+haven'?t\b"""),
            Regex("""(?i)\bfailed?\b"""),
            Regex("""(?i)\bguilt\b"""),
            // False precision and accusations.
            Regex("""(?i)\bday(s)?\s+late\b"""),
            Regex("""(?i)\byour\s+period\s+starts\b"""),
            Regex("""(?i)\byour\s+period\s+will\b"""),
            Regex("""(?i)\byou\s+are\s+pregnant\b"""),
            Regex("""(?i)\bsafe\s+to\b"""),
            Regex("""(?i)\bmiscarriage\b"""),
        )

        /**
         * The string resources a notification can carry.
         */
        val NOTIFICATION_STRING_NAMES = listOf(
            "reminder_notification_title",
            "reminder_redacted_generic",
            "reminder_redacted_daily_log",
            "reminder_deferred_summary_title",
            "reminder_deferred_summary_body",
            "reminder_group_summary_title",
            "reminder_group_summary_body",
            "reminder_auto_disabled_title",
            "reminder_auto_disabled_body",
            "reminder_action_re_enable",
        )
    }
}
