package com.cyclecare.core.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Spec 02 §9.10.6: backfilled timestamps are never presented as real
 * creation dates.
 *
 * v2 had no audit columns at all, so the v3 migration had to invent
 * them. The values it wrote are the migration time, which is not when
 * the user recorded anything, and nothing may show them as if it were.
 *
 * That guarantee used to rest on nothing reading the field. This test
 * makes it enforced: a main-source file outside the persistence layer
 * that reads an audit timestamp fails the build, so the guarantee
 * cannot lapse quietly the next time somebody wires the field into a
 * screen.
 */
class AuditTimestampReadGuardTest {

    @Test
    fun noMainSourceFileOutsideThePersistenceLayerReadsAnAuditTimestamp() {
        val repositoryRoot = findRepositoryRoot()
        val offenders = mutableListOf<String>()

        mainSourceRoots(repositoryRoot)
            .filterNot { it.isInside(repositoryRoot, PERSISTENCE_LAYER_PATHS) }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val text = file.readText()
                        AUDIT_TIMESTAMP_COLUMNS.forEach { column ->
                            if (text.contains(column)) {
                                offenders += "${file.relativeTo(repositoryRoot)} reads $column"
                            }
                        }
                    }
            }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

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

    private fun File.isInside(repositoryRoot: File, relativePaths: List<String>): Boolean =
        relativePaths.any { relative ->
            val candidate = File(repositoryRoot, relative)
            this.path == candidate.path || this.path.startsWith(candidate.path + File.separator)
        }

    private companion object {
        /**
         * The persistence layer, where the audit columns are read and
         * written. Everywhere else they must not appear.
         */
        val PERSISTENCE_LAYER_PATHS = listOf(
            "core/database/src/main",
            "core/data/src/main",
        )

        val AUDIT_TIMESTAMP_COLUMNS = listOf(
            "createdAtEpochMillis",
            "updatedAtEpochMillis",
            "deletedAtEpochMillis",
        )
    }
}
