package com.cyclecare.core.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Spec `02` §9.4.5: "Application code reads intervals through the
 * shared extractor in `11` §7 and **never** reads `cycleLength`."
 *
 * The column is written at logging time from whatever the user last
 * reported, and `CycleRecordValidator` says so: the value "is never
 * read as a cycle length now that the domain derives it from
 * consecutive starts, and stored values can legitimately disagree with
 * the true gap." A single read of it in a screen is enough for the
 * Insights screen and the prediction screen to disagree about the same
 * user on the same day, which is the defect the shared extractor
 * exists to prevent.
 *
 * The guard is exact rather than a broad text search. The identifier
 * `cycleLength` is unambiguous in main source, because the user-facing
 * fields that share the name were renamed to `cycleLengthDays` when
 * this rule was introduced, and `UserProfile.averageCycleLength` was
 * never called `cycleLength`. What remains is the stored field, and a
 * member access to it is the only way to read it.
 *
 * Persistence, serialization and the migration are allowed, and they
 * are allowed by file rather than by module, so a new read in the
 * export repository or in a feature module still fails.
 */
class StoredCycleLengthGuardTest {

    @Test
    fun noApplicationCodeReadsTheStoredCycleLength() {
        val repositoryRoot = findRepositoryRoot()
        val offenders = mutableListOf<String>()

        mainSourceRoots(repositoryRoot)
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filterNot { it.isAllowed(repositoryRoot) }
                    .forEach { file ->
                        file.readLines().forEachIndexed { index, line ->
                            val code = line.substringBefore("//")
                            if (STORED_FIELD_ACCESS.containsMatchIn(code)) {
                                offenders += "${file.relativeTo(repositoryRoot)}:${index + 1} reads the stored cycleLength"
                            }
                        }
                    }
            }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    /**
     * The stored field is read as a member access.
     *
     * A declaration (`val cycleLength: Int`) and a named argument
     * (`cycleLength = ...`) are not reads of the field, and neither is
     * the `averageCycleLength` on the profile, which is a different
     * field with a different name.
     */
    private val STORED_FIELD_ACCESS = Regex("""\.\s*cycleLength\b""")

    private fun findRepositoryRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").exists()) return candidate
            candidate = candidate.parentFile
        }
        throw AssertionError("Could not find the repository root from ${File("").absolutePath}")
    }

    /**
     * Every `src/main` directory in the repository.
     *
     * The module layout is discovered rather than listed, so a new
     * module is covered by the guard without this file being edited.
     */
    private fun mainSourceRoots(repositoryRoot: File): List<File> =
        repositoryRoot.walkTopDown()
            .filter { it.isDirectory && it.name == "main" && it.parentFile?.name == "src" }
            .filterNot { it.path.contains(File.separator + "build" + File.separator) }
            .toList()

    /**
     * Whether this file is one of the places the column legitimately
     * lives.
     *
     * The allowlist is by path, and a directory entry covers everything
     * under it, so the migration module is covered as a whole while
     * the two data-layer files are named individually.
     */
    private fun File.isAllowed(repositoryRoot: File): Boolean =
        ALLOWED_PATHS.any { relative ->
            val candidate = File(repositoryRoot, relative)
            this.path == candidate.path || this.path.startsWith(candidate.path + File.separator)
        }

    private companion object {
        /**
         * Where the stored column is legitimately handled.
         *
         * The migration reconciles the column and verifies it. The
         * mapper carries it between the entity and the model. The
         * serializer writes it into a backup and reads it back. All
         * three are the column's own home.
         */
        val ALLOWED_PATHS = listOf(
            "core/database/src/main",
            "core/data/src/main/java/com/cyclecare/core/data/EntityMappers.kt",
            "core/data/src/main/java/com/cyclecare/core/data/BackupPayloadSerializer.kt",
        )
    }
}
