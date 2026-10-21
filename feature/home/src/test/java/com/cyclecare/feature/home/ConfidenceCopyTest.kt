package com.cyclecare.feature.home

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The confidence wording, and the rule that keeps it out of sentences.
 *
 * The engine used to produce the confidence as English prose, which the
 * UI then passed to a translatable string as an argument. That made a
 * translator responsible for a sentence containing a fragment they could
 * not translate, and it made the *shape* of the UI depend on whether
 * that fragment was blank.
 *
 * Spec `11` §7 replaced the prose with `quality: EstimateQuality`, and
 * the wording is now a whole resource per tier. These tests hold both
 * halves of that: the wording never claims more than the engine knows,
 * and no translatable sentence takes it as an argument.
 */
class ConfidenceCopyTest {

    @Test
    fun everyConfidenceStringIsFreeOfThePhrasesTheSpecificationBans() {
        val banned = listOf(
            "safe",
            "contracept",
            "diagnos",
            "certain",
            "guarantee",
            "will start",
            "definitely",
            "clinically accurate",
            "unhealthy",
            "abnormal",
            "100%",
            "accurate",
        )
        val strings = confidenceStrings()

        assertThat(strings).isNotEmpty()
        strings.forEach { (name, value) ->
            assertThat(value).isNotEmpty()
            banned.forEach { phrase ->
                assertThat(value.lowercase()).doesNotContain(phrase)
            }
            // The name is only there to make a failure readable.
            assertThat(name).isNotEmpty()
        }
    }

    @Test
    fun everyConfidenceResourceIsAWholeString() {
        // The confidence is rendered as its own element, so its resource
        // must not take an argument. If it ever does, the wording has
        // been folded back into a sentence.
        val offenders = mutableListOf<String>()
        confidenceStrings().forEach { (name, value) ->
            if (value.contains("%")) {
                offenders += "$name takes a format argument: $value"
            }
        }
        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun noResourceCombinesTheConfidenceWithASentence() {
        // "day_slash_total_with_confidence" was the sentence that took
        // the prose. The naming is what a reintroduction would look
        // like, and it is the same rule the retired-shape guard checks
        // on the code side.
        val offenders = resourceFiles().flatMap { file ->
            Regex("""name="([^"]*with_confidence[^"]*)"""")
                .findAll(file.readText())
                .map { match -> "${file.name}: ${match.groupValues[1]}" }
                .toList()
        }
        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun theRetiredCombinedSentenceIsGone() {
        // "Day %1$s / %2$d - %3$s" was the sentence that took the
        // prose. It must not come back.
        val names = resourceFiles().flatMap { file ->
            Regex("""name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }
        }.toList()

        assertThat(names).doesNotContain("day_slash_total_with_confidence")
    }

    /** The confidence resources and their text. */
    private fun confidenceStrings(): List<Pair<String, String>> {
        val values = mutableMapOf<String, String>()
        resourceFiles().forEach { file ->
            file.readLines().forEach { line ->
                val match = Regex("""<string name="(confidence_[^"]+)">(.*)</string>""").find(line)
                if (match != null) {
                    values[match.groupValues[1]] = match.groupValues[2]
                }
            }
        }
        return values.toList()
    }

    /** Every `strings.xml` in the repository, so the check covers them all. */
    private fun resourceFiles(): List<File> =
        findRepositoryRoot()
            .walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" }
            .filterNot { it.path.contains(File.separator + "build" + File.separator) }
            .toList()

    private fun findRepositoryRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").exists()) return candidate
            candidate = candidate.parentFile
        }
        throw AssertionError("Could not find the repository root from ${File("").absolutePath}")
    }
}
