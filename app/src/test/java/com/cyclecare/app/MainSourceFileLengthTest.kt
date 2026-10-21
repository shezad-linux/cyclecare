package com.cyclecare.app

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The file-length criterion, made enforceable.
 *
 * Spec `15` §14.14 says "No file exceeds ~600 lines". That clause was
 * recorded as met in the acceptance matrix while
 * `CycleCareShell.kt` was 620 lines, which shows the problem with a
 * criterion that lives only in prose: nothing fails when it stops being
 * true, and the only way to find out is to go looking.
 *
 * This test walks every main source set in the repository and fails when
 * a file is over the limit, naming the file and the count. It is the
 * difference between a guideline that is checked and a guideline that is
 * remembered.
 *
 * The limit is 600 rather than "about 600" because a tilde cannot be
 * tested. `CycleCareShell.kt` at 620 was the case in point: close
 * enough for a human to call it approximately 600, and twenty lines
 * over a number that the specification states.
 */
class MainSourceFileLengthTest {

    @Test
    fun noMainSourceFileExceedsTheLineLimit() {
        val root = repositoryRoot()
        val offenders = mainSourceFiles(root)
            .map { file -> file to lineCount(file) }
            .filter { (_, lines) -> lines > MAX_LINES }
            .sortedByDescending { (_, lines) -> lines }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun theWalkActuallyFindsTheMainSourceSets() {
        val root = repositoryRoot()

        // A walk that found nothing would pass the test above for the
        // wrong reason. These are the modules the repository is made of,
        // and each one has a main source set.
        val modules = listOf(
            "app",
            "core/designsystem",
            "core/domain",
            "core/model",
            "core/data",
            "core/database",
            "core/security",
            "feature/calendar",
            "feature/export",
            "feature/home",
            "feature/insights",
            "feature/logging",
            "feature/onboarding",
            "feature/privacy",
            "feature/settings",
        )

        modules.forEach { module ->
            val mainSourceSet = File(root, "$module/src/main")
            assertThat(mainSourceSet.isDirectory).isTrue()
        }
    }

    @Test
    fun theWalkFindsTheFilesItIsMeantToMeasure() {
        val root = repositoryRoot()
        val files = mainSourceFiles(root)

        // The largest file before the split, and the file this criterion
        // was written for.
        assertThat(files.map { it.name }).contains("CycleCareShell.kt")
        assertThat(files.map { it.name }).contains("HomeRoute.kt")
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root from ${File(".").absolutePath}")
    }

    /** Every file under a `src/main` directory, excluding build output. */
    private fun mainSourceFiles(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isDirectory }
            .filter { it.name == "main" && it.parentFile?.name == "src" }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile }
            .filter { file -> file.extension !in BINARY_EXTENSIONS }
            .filter { file -> file.readBytes().let { bytes -> !bytes.contains(0) } }
            .toList()

    /**
     * The number of lines in a file.
     *
     * Counted as the number of line terminators, so a file whose last
     * line has no trailing newline is not counted as having an extra
     * empty line.
     */
    private fun lineCount(file: File): Int {
        val text = file.readText()
        if (text.isEmpty()) return 0
        val newlines = text.count { it == '\n' }
        return if (text.endsWith("\n")) newlines else newlines + 1
    }

    private companion object {
        /** Spec `15` §14.14. */
        const val MAX_LINES = 600

        /** Binary assets have no lines, and counting bytes would be noise. */
        val BINARY_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "ogg", "mp3", "wav", "jar", "so", "zip")
    }
}
