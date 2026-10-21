package com.cyclecare.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The dark-pattern copy audit, made repeatable.
 *
 * Spec `14` §12.13 says "No dark-pattern copy anywhere in the UI". That
 * is a copy review, and a copy review that lives only in a document
 * silently stops being true: the next string someone adds is never
 * reviewed against it.
 *
 * This test walks every user-facing string in the repository and fails
 * when one contains a phrase from the banned list. The list is the
 * union of the phrases `03` §7 names as "avoid", the certainty and
 * privacy overclaims `03` §2.4 and `05` §9 rule out, and the guilt and
 * streak-loss language `03` §2.5 bans.
 *
 * **Scope.** The list bans *streak-loss* language, not the word
 * "streak". The Logging feature carries a positive streak counter —
 * "%1$d day streak!" and "Keep up the great habit." — and whether
 * `13` §17.12's "anywhere" reaches a positive counter is an open
 * decision that the ledger records as row A2. That copy is reviewed
 * and reported, and left alone. The test therefore bans losing a streak
 * and never bans having one.
 *
 * The recorded review is in
 * `CycleCare-Redesign-Docs/24-dark-pattern-copy-review.md`.
 */
class DarkPatternCopyTest {

    @Test
    fun noUserFacingStringContainsABannedPhrase() {
        val offenders = userFacingStrings()
            .filter { (value, _, _) ->
                BANNED_PHRASES.any { phrase -> value.contains(phrase, ignoreCase = true) }
            }
            .map { (value, source, name) -> "$source:$name -> \"$value\"" }
            .sorted()

        assertThat(offenders).isEmpty()
    }

    @Test
    fun theScanActuallyReadsTheUserFacingStrings() {
        val strings = userFacingStrings()

        // A scan that found nothing would pass the test above for the
        // wrong reason.
        assertThat(strings.size).isGreaterThan(500)
        assertThat(strings.map { triple -> triple.second }.distinct().size).isAtLeast(8)
    }

    @Test
    fun theMatcherActuallyRejectsABannedPhrase() {
        // The matcher is the thing that makes the audit repeatable, so
        // it is proved against a phrase that is on the list rather than
        // assumed to work.
        val sample = "You missed your tracking streak."

        assertThat(BANNED_PHRASES.any { sample.contains(it, ignoreCase = true) }).isTrue()
    }

    @Test
    fun theBannedListCoversEveryCategoryTheReviewNames() {
        // Each category `03` §2.5 and §7 names has to have at least one
        // phrase, or the audit has a hole in it.
        listOf(
            "missed your tracking streak",
            "will start on",
            "safe from pregnancy",
            "cycle is unhealthy",
            "100% private",
            "accurately",
        ).forEach { phrase ->
            assertThat(BANNED_PHRASES).contains(phrase)
        }
    }

    /** Every string and plural item in every main source set's resources. */
    private fun userFacingStrings(): List<Triple<String, String, String>> {
        val root = repositoryRoot()
        val files = root.walkTopDown()
            .filter { it.isFile }
            .filter { it.name == "strings.xml" }
            .filter { it.parentFile?.name == "values" }
            .filter { it.parentFile?.parentFile?.parentFile?.name == "main" }
            .filter { file -> file.toPath().none { it.toString() == "build" } }
            .sortedBy { it.path }
            .toList()

        return files.flatMap { file ->
            val source = file.relativeTo(root).path
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val nodes = document.getElementsByTagName("string")
            val values = (0 until nodes.length).mapNotNull { index ->
                val element = nodes.item(index) as? org.w3c.dom.Element ?: return@mapNotNull null
                val name = element.getAttribute("name")
                val text = element.textContent
                if (name.isBlank() || text.isBlank()) null else Triple(text, source, name)
            }
            val pluralNodes = document.getElementsByTagName("item")
            val plurals = (0 until pluralNodes.length).mapNotNull { index ->
                val element = pluralNodes.item(index) as? org.w3c.dom.Element ?: return@mapNotNull null
                val text = element.textContent
                if (text.isBlank()) null else Triple(text, source, element.getAttribute("quantity"))
            }
            values + plurals
        }
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root from ${File(".").absolutePath}")
    }

    private companion object {
        /**
         * Phrases that must not appear in a user-facing string.
         *
         * Each entry names the rule it comes from, so a future change
         * can be traced back to the specification rather than to taste.
         */
        val BANNED_PHRASES = listOf(
            // `03` §7 and §2.5: guilt, and shame about a missing log.
            "missed your tracking streak",
            "you missed",
            "streak is over",
            "lost your streak",
            "streak reset",
            "broke your streak",
            "break your streak",
            "keep your streak alive",
            "don't forget to log",
            "don't forget",
            "we miss you",
            "log every day to",

            // `03` §7: a prediction presented as certainty.
            "will start on",
            "definitely",
            "guaranteed",
            "exactly on",
            "you are sure",

            // `03` §7 and `05` §9: contraception and fertility claims.
            "safe from pregnancy",
            "cannot get pregnant",
            "won't get pregnant",

            // `03` §7: clinical judgement without a clinical basis.
            "cycle is unhealthy",
            "unhealthy cycle",
            "abnormal cycle",
            "you're irregular",
            "you are irregular",

            // `03` §7: privacy claims that are not provable and scoped.
            "100% private",
            "completely private",
            "totally private",
            "fully private",
            "end-to-end encrypted",
            "anonymous and untraceable",

            // `11` §5 and `05` §9: false accuracy and false precision.
            "accurately",
            "100% accurate",
            "precisely predict",

            // `11` §5: the stale case says this instead.
            "you are overdue",
        )
    }
}
