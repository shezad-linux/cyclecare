package com.cyclecare.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Dark-pattern copy audit, spec `14` §12.13 / `03` §2.5 and §7.
 *
 * `03` §2.5: "No shame or pressure. Never use streak-loss language, guilt
 * notifications, fertility assumptions, or judgmental language for missing
 * logs or irregular cycles." `03` §7 lists copy to avoid, such as confident
 * exact-date language ("Your period will start on…") and unscoped privacy
 * claims ("Everything is 100% private").
 *
 * This test enumerates every user-facing string and asserts that none of
 * them contains banned dark-pattern language, so the review does not
 * silently regress. It is a complement to, not a replacement for, the
 * human copy review recorded in the workstream report.
 *
 * Strings that were reviewed and judged borderline (for example mild
 * encouragement such as "Keep up the great habit.") are not banned here;
 * they are documented in the workstream report instead of being blocked,
 * because no clear violation was found.
 */
class CopyAuditTest {

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").exists()) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root")
    }

    private fun stringResources(): List<Pair<String, Pair<String, String>>> {
        val root = repositoryRoot()
        return root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.name == "strings.xml" &&
                    file.path.contains("${File.separator}res${File.separator}")
            }
            .flatMap { file ->
                Regex("""<string name="([^"]+)">([^<]*)</string>""")
                    .findAll(file.readText())
                    .map { match ->
                        file.path to (match.groupValues[1] to match.groupValues[2])
                    }
            }
            .toList()
    }

    private val bannedPhrases = listOf(
        "streak lost", "streak broken", "you'll lose your streak",
        "missed your tracking streak", "streak-loss",
        "guilt", "guilty", "shame", "ashamed",
        "you should", "don't forget", "you must",
        "your period will start on",
        "you are definitely ovulating", "you are definitely",
        "you are safe from pregnancy", "safe from pregnancy",
        "your cycle is unhealthy",
        "everything is 100% private", "100% private",
        "you missed your tracking streak",
    )

    @Test
    fun noStringContainsBannedDarkPatternLanguage() {
        val offenders = stringResources()
            .filter { (_, pair) ->
                val (_, value) = pair
                bannedPhrases.any { phrase ->
                    value.lowercase().contains(phrase.lowercase())
                }
            }
            .map { (path, pair) -> "$path: ${pair.first} = ${pair.second}" }

        assertThat(offenders).isEmpty()
    }

}
