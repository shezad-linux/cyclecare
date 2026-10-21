package com.cyclecare.core.domain

import com.cyclecare.core.model.CycleRecord
import com.cyclecare.core.model.CycleSource
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import org.junit.Test

/**
 * The cycle-length range is the domain's rule, and the screens use it.
 *
 * The range used to be written out as a literal `20..45` in the
 * validator and again in three feature modules, which is four places
 * for one rule to drift. It now lives in one place,
 * [VALID_CYCLE_LENGTH_RANGE], and this test holds the two together.
 */
class CycleLengthRangeTest {

    @Test
    fun theRangeIsTheOneTheValidatorUses() {
        assertThat(VALID_CYCLE_LENGTH_RANGE).isEqualTo(20..45)
    }

    @Test
    fun theValidatorDoesNotRangeCheckTheStoredLength() {
        // The domain no longer reads the stored length, so it does not
        // range-check it either: `CycleRecordValidator` says so
        // explicitly. The range is enforced where a length is *entered*,
        // in the three editors, and they all use this constant.
        val validator = CycleRecordValidator(todayProvider = { LocalDate.parse("2026-03-16") })

        listOf(20, 45, 19, 46).forEach { length ->
            val result = validator.validate(
                candidate = candidate(length),
                existingRecords = emptyList(),
            )
            assertThat(result.isValid).isTrue()
        }
    }

    @Test
    fun noFeatureModuleRestatesTheCycleLengthRange() {
        // The screens use the shared constant. A literal here would be
        // a second copy of the rule, and the two could disagree.
        //
        // The check is narrow on purpose: it looks for a line that
        // talks about the cycle length *and* writes a range out. A
        // feature module has other ranges — the period length, a window
        // of days around the expected date — and those are their own
        // rules, so matching every range literal would be a broad
        // search that flags correct code.
        val offenders = mutableListOf<String>()

        featureSourceRoots().forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val code = line.substringBefore("//")
                        val talksAboutCycleLength = code.contains("cycleLength")
                        val writesARangeOut = RANGE_LITERAL.containsMatchIn(code)
                        val usesTheSharedConstant = code.contains("VALID_CYCLE_LENGTH_RANGE")
                        if (talksAboutCycleLength && writesARangeOut && !usesTheSharedConstant) {
                            offenders += "${file.name}:${index + 1} restates the cycle-length range"
                        }
                    }
                }
        }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun noFeatureStringWritesTheRangeOutInWords() {
        // The wording must not restate the numbers either. A message
        // that says "between 20 and 45 days" is a copy of the rule, and
        // a copy does not move when the rule does.
        val offenders = mutableListOf<String>()

        featureResourceFiles().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (RANGE_IN_WORDS.containsMatchIn(line)) {
                    offenders += "${file.name}:$index writes the range out in words"
                }
            }
        }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    /** The range written out as words, the way a message would. */
    private val RANGE_IN_WORDS = Regex(
        """\b\d+\s+and\s+\d+\s+days\b"""",
        RegexOption.IGNORE_CASE,
    )

    /** A range written out as a literal rather than referenced. */
    private val RANGE_LITERAL = Regex("""\b\d+\s*\.\.\s*\d+\b""")

    private fun candidate(length: Int): CycleRecord = CycleRecord(
        id = "candidate",
        startDate = LocalDate.parse("2026-03-01"),
        endDate = LocalDate.parse("2026-03-05"),
        cycleLength = length,
        source = CycleSource.User,
        notes = null,
    )

    private fun featureSourceRoots(): List<File> =
        findRepositoryRoot()
            .walkTopDown()
            .filter { it.isDirectory && it.name == "main" && it.parentFile?.name == "src" }
            .filter { it.path.contains(File.separator + "feature" + File.separator) }
            .filterNot { it.path.contains(File.separator + "build" + File.separator) }
            .toList()

    /** Every `strings.xml` in the feature modules. */
    private fun featureResourceFiles(): List<File> =
        findRepositoryRoot()
            .walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" }
            .filter { it.path.contains(File.separator + "feature" + File.separator) }
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
