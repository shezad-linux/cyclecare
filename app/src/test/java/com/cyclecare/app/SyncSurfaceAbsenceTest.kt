package com.cyclecare.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloud synchronisation is deferred by decision, so the app must not
 * present a synchronisation surface it does not have.
 *
 * Spec `03` §6 lists "sync waiting, in progress, failed and complete (where
 * relevant)". The relevance condition is the whole point: the app has no
 * sync status, no sync string and no sync screen, so those states are not
 * applicable. A state-matrix test that manufactured them would be claiming
 * a feature that does not exist, and `NoOpSyncAdapter` is the whole of the
 * sync feature today rather than a working engine standing in for one.
 *
 * This test makes that absence enforceable rather than a matter of memory.
 * It reads the resource files from source because `@string` values are the
 * user-facing copy, and it is the copy a sync surface would have to carry.
 */
class SyncSurfaceAbsenceTest {

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").exists()) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root")
    }

    private fun stringResources(): List<File> {
        val root = repositoryRoot()
        return root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.name == "strings.xml" &&
                    file.path.contains("${File.separator}res${File.separator}")
            }
            .toList()
    }

    @Test
    fun noUserFacingStringIsAboutSynchronisation() {
        val offenders = stringResources()
            .flatMap { file ->
                Regex("""<string name="([^"]+)">([^<]*)</string>""")
                    .findAll(file.readText())
                    .map { match ->
                        file.path to (match.groupValues[1] to match.groupValues[2])
                    }
                    .filter { (_, pair) ->
                        val (name, value) = pair
                        name.contains("sync", ignoreCase = true) ||
                            value.contains("sync", ignoreCase = true)
                    }
                    .map { (path, pair) -> "$path: ${pair.first} = ${pair.second}" }
            }.toList()

        assertTrue("expected no synchronisation strings, found: $offenders", offenders.isEmpty())
    }

    @Test
    fun noPreviewOrRouteDeclaresASyncSurface() {
        val root = repositoryRoot()
        val offenders = root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file.path.contains("${File.separator}src${File.separator}main${File.separator}")
            }
            .flatMap { file ->
                file.readLines()
                    .mapIndexed { index, line -> Triple(file, index + 1, line) }
                    .filter { (_, _, line) ->
                        line.contains("testTag(\"sync", ignoreCase = true) ||
                            line.contains("route = \"sync", ignoreCase = true) ||
                            line.contains("composable(\"sync", ignoreCase = true)
                    }
                    .map { (file, line, text) -> "${file.path}:$line $text" }
            }.toList()

        assertTrue("expected no sync surface declarations, found: $offenders", offenders.isEmpty())
    }
}
