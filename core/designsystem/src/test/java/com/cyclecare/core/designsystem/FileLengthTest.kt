package com.cyclecare.core.designsystem

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Spec `15` §14.14: no file in a main source set exceeds ~600 lines.
 *
 * Walks every main source set and fails when a file exceeds the limit,
 * naming the file and the count, so the criterion is enforceable rather
 * than a matter of memory.
 */
class FileLengthTest {

    private val maxLines = 600

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").exists()) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root")
    }

    private fun mainSourceFiles(): List<File> {
        val root = repositoryRoot()
        return root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file.path.contains("${File.separator}src${File.separator}main${File.separator}")
            }
            .toList()
    }

    @Test
    fun noMainSourceFileExceedsTheLineLimit() {
        val offenders = mainSourceFiles()
            .mapNotNull { file ->
                val lines = file.readLines().size
                if (lines > maxLines) "$file ($lines lines)" else null
            }

        assertThat(offenders).isEmpty()
    }
}
