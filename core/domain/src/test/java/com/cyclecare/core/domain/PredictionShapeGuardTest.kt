package com.cyclecare.core.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The second prediction shape is gone.
 *
 * Spec `11` §7: "`PredictionSnapshot` is replaced by `CyclePrediction`.
 * `confidenceLabel: String` is removed; `quality: EstimateQuality`
 * replaces it. Migrate consumers in one change, never both shapes at
 * once."
 *
 * A migration that leaves the old shape behind is not a migration: there
 * would be two ways to say the same thing, and one of them would
 * quietly keep the old English prose. This guard makes the removal
 * stick.
 */
class PredictionShapeGuardTest {

    @Test
    fun theRetiredPredictionShapeIsNotDeclaredAnywhere() {
        val offenders = mutableListOf<String>()

        sourceRoots().forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val code = line.substringBefore("//").trim()
                        if (code.startsWith("data class PredictionSnapshot") ||
                            code.startsWith("class PredictionSnapshot") ||
                            code.startsWith("enum class PredictionQuality") ||
                            code.startsWith("enum class PredictionPhase") ||
                            code.startsWith("enum class PredictionWarning")
                        ) {
                            offenders += "${file.relativeTo(findRepositoryRoot())}:${index + 1} declares $code"
                        }
                    }
                }
        }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun noProductionCodeReferencesTheRetiredShape() {
        val offenders = mutableListOf<String>()

        productionSourceRoots().forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    var inDocComment = false
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        // A reference in a doc comment is a reference to
                        // the contract, not to the code, and the
                        // contract names the retired shape when it
                        // explains why the shape is gone.
                        if (trimmed.startsWith("/*")) inDocComment = true
                        if (inDocComment) {
                            if (trimmed.endsWith("*/")) inDocComment = false
                            return@forEachIndexed
                        }
                        val code = line.substringBefore("//")
                        if (RETIRED_REFERENCE.containsMatchIn(code)) {
                            offenders += "${file.relativeTo(findRepositoryRoot())}:${index + 1} references the retired shape"
                        }
                    }
                }
        }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    /**
     * A reference to the retired shape.
     *
     * The prose the domain used to produce is gone with it, so the
     * confidence is a tier and a resource rather than an English
     * string.
     */
    private val RETIRED_REFERENCE = Regex(
        """\b(PredictionSnapshot|PredictionQuality|PredictionPhase|PredictionWarning|confidenceLabel)\b""",
    )

    private fun findRepositoryRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").exists()) return candidate
            candidate = candidate.parentFile
        }
        throw AssertionError("Could not find the repository root from ${File("").absolutePath}")
    }

    /** Every source set, so the shape cannot be reintroduced anywhere. */
    private fun sourceRoots(): List<File> =
        findRepositoryRoot()
            .walkTopDown()
            .filter { it.isDirectory && it.name == "main" && it.parentFile?.name == "src" }
            .filterNot { it.path.contains(File.separator + "build" + File.separator) }
            .toList()

    /** Only the main source sets, where a reference would be a defect. */
    private fun productionSourceRoots(): List<File> =
        findRepositoryRoot()
            .walkTopDown()
            .filter { it.isDirectory && it.name == "main" && it.parentFile?.name == "src" }
            .filterNot { it.path.contains(File.separator + "build" + File.separator) }
            .filterNot { it.path.contains(File.separator + "test" + File.separator) }
            .filterNot { it.path.contains(File.separator + "androidTest" + File.separator) }
            .toList()
}
