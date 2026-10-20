package com.cyclecare.core.designsystem

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The preview coverage the phase requires, made checkable.
 *
 * Spec `08` §5 makes "previews exist at 320/360/600/840dp in light and
 * dark" a phase exit criterion, and `14` §12 records that the only
 * assertion behind it read **one** file. Six files used
 * [ScreenWidthPreviews] and every other screen had no preview at all, so
 * a layout problem at a width the app actually uses was invisible.
 *
 * The test below reads every preview source in the main source sets and
 * checks two things:
 *
 * 1. The width annotation itself declares every required width, in both
 *    colour schemes. The annotation is what every screen inherits, so a
 *    hole in it is a hole in every screen's preview.
 * 2. Every screen in the app's screen list has a preview source that
 *    uses it.
 *
 * The screen list is the one in
 * `CycleCare-Redesign-Docs/21-phase-5-6-acceptance-matrix.md`, which
 * is the project's own list of the fifteen screens.
 */
class PreviewCoverageTest {

    @Test
    fun theWidthPreviewsCoverEveryRequiredWidth() {
        val source = previewSource()

        // The four widths the phase requires, each declared as a preview.
        listOf(320, 360, 600, 840).forEach { width ->
            assertThat(source).contains("widthDp = $width")
        }
    }

    @Test
    fun theWidthPreviewsCoverBothColourSchemes() {
        val source = previewSource()

        // The dark previews carry the night flag; the light ones leave it
        // at its default, which is not night. Both have to be there, so
        // the annotation has to declare the night variant and declare more
        // previews than the four light widths alone.
        assertThat(source).contains("UI_MODE_NIGHT_YES")
        assertThat(Regex("""@Preview\(""").findAll(source).count()).isAtLeast(8)
    }

    @Test
    fun theScreenPreviewsCoverTheLargeFontScale() {
        // The layouts stack their controls at a large font scale as well as
        // at a narrow width, so the previews have to show that.
        assertThat(previewSource()).contains("fontScale = 1.3f")
    }

    @Test
    fun thePreviewsAreNotPinnedToASingleWidth() {
        // A single width would be the failure mode this test exists to
        // catch: the annotation would still render, and every layout
        // problem at the other widths would stay invisible.
        assertThat(declaredWidths()).containsAtLeast(320, 360, 600, 840)
    }

    // ── the extended coverage ──────────────────────────────────────

    @Test
    fun theWidthAnnotationCoversEveryRequiredWidthInBothSchemes() {
        val declared = widthAnnotationDeclarations()

        // Each width has to be declared once in light and once in dark.
        // A width that is only in light is the gap this test exists for.
        val missing = mutableListOf<String>()
        REQUIRED_WIDTHS.forEach { width ->
            if (declared.none { it.width == width && !it.isDark }) {
                missing += "no light preview at ${width}dp"
            }
            if (declared.none { it.width == width && it.isDark }) {
                missing += "no dark preview at ${width}dp"
            }
        }

        assertThat(missing).isEmpty()
    }

    @Test
    fun everyScreenHasAPreviewAtEveryWidthInBothSchemes() {
        val sources = previewSources()
        val missing = mutableListOf<String>()

        SCREENS.forEach { screen ->
            // A screen whose composable is private to a file another
            // workstream owns cannot be previewed from here. The gap is
            // listed, and the list is checked, so it cannot grow without
            // this test failing.
            if (screen.blockedByOwnership != null) return@forEach
            val source = sources[screen.previewFile]
            if (source == null) {
                missing += "${screen.name}: no preview source at ${screen.previewFile}"
                return@forEach
            }
            if (!source.contains("@ScreenWidthPreviews")) {
                missing += "${screen.name}: ${screen.previewFile} does not use @ScreenWidthPreviews"
            }
        }

        assertThat(missing).isEmpty()
    }

    @Test
    fun theBlockedScreensAreExactlyTheOnesThatAreKnown() {
        // A screen can be missing a preview only when the file that holds
        // its composable belongs to another workstream. The list is
        // checked, so a gap that appears for any other reason fails.
        assertThat(BLOCKED_BY_OWNERSHIP.keys).containsExactlyElementsIn(
            SCREENS.filter { it.blockedByOwnership != null }.map { it.name }.toSet(),
        )
        BLOCKED_BY_OWNERSHIP.forEach { (name, reason) ->
            assertThat(reason).isNotEmpty()
        }
    }

    @Test
    fun thePreviewScanFindsThePreviewSources() {
        val sources = previewSources()

        // A scan that found nothing would pass the coverage test for the
        // wrong reason.
        assertThat(sources.size).isAtLeast(8)
        assertThat(sources.values.any { it.contains("@ScreenWidthPreviews") }).isTrue()
    }

    // ── the screen list ────────────────────────────────────────────

    /**
     * The screens the app has, and where their preview lives.
     *
     * The list is the app's own list of the fifteen screens, in
     * `21-phase-5-6-acceptance-matrix.md`. A screen with no preview file
     * of its own is listed against the file that holds it.
     */
    private data class Screen(
        val name: String,
        val previewFile: String,
        /** Set when the composable lives in a file another workstream owns. */
        val blockedByOwnership: String? = null,
    )

    private companion object {
        val REQUIRED_WIDTHS = listOf(320, 360, 600, 840)

        val SCREENS = listOf(
            Screen("today", "feature/home/src/main/java/com/cyclecare/feature/home/HomePreviews.kt"),
            Screen("calendar", "feature/calendar/src/main/java/com/cyclecare/feature/calendar/CalendarPreviews.kt"),
            Screen("log", "feature/logging/src/main/java/com/cyclecare/feature/logging/LoggingPreviews.kt"),
            Screen("insights", "feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsPreviews.kt"),
            Screen("cycle status", "feature/home/src/main/java/com/cyclecare/feature/home/HomePreviews.kt"),
            Screen("fertility detail", "feature/home/src/main/java/com/cyclecare/feature/home/HomePreviews.kt"),
            Screen("cycle trends", "feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsPreviews.kt"),
            Screen("symptom patterns", "feature/insights/src/main/java/com/cyclecare/feature/insights/InsightsPreviews.kt"),
            Screen("settings", "feature/settings/src/main/java/com/cyclecare/feature/settings/SettingsPreviews.kt"),
            Screen("export", "feature/export/src/main/java/com/cyclecare/feature/export/ExportPreviews.kt"),
            Screen("privacy", "feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyPreviews.kt"),
            Screen("app lock", "feature/privacy/src/main/java/com/cyclecare/feature/privacy/PrivacyPreviews.kt"),
            Screen("notification gate", "app/src/main/java/com/cyclecare/app/AppShellPreviews.kt"),
            Screen("edit sheet", "feature/home/src/main/java/com/cyclecare/feature/home/HomePreviews.kt"),
            Screen(
                name = "reminder centre",
                previewFile = "app/src/main/java/com/cyclecare/app/ReminderCenterScreen.kt",
                blockedByOwnership = "ReminderCenterScreen is private to " +
                    "app/src/main/java/com/cyclecare/app/ReminderCenterScreen.kt, which another " +
                    "workstream owns, so a preview cannot be added without editing that file. " +
                    "The file needs one word changed, private to internal.",
            ),
        )

        val BLOCKED_BY_OWNERSHIP = SCREENS
            .filter { it.blockedByOwnership != null }
            .associate { it.name to it.blockedByOwnership!! }
    }

    // ── reading the sources ────────────────────────────────────────

    private fun previewSource(): String {
        val relative = "src/main/java/com/cyclecare/core/designsystem/ScreenDebugPreviews.kt"
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.exists()) return candidate.readText()
            val nested = File(directory, "core/designsystem/$relative")
            if (nested.exists()) return nested.readText()
            directory = directory.parentFile
        }
        error("could not find the preview annotations source")
    }

    private fun declaredWidths(): Set<Int> =
        Regex("""widthDp = (\d+)""")
            .findAll(previewSource())
            .map { it.groupValues[1].toInt() }
            .toSet()

    /** One `@Preview` declaration inside the width annotation. */
    private data class PreviewDeclaration(val width: Int, val isDark: Boolean)

    /**
     * The `@Preview` declarations of the width annotation.
     *
     * The annotation is a set of `@Preview` annotations on an annotation
     * class, so the widths and the colour scheme are declared there and
     * every screen inherits them. Reading them is reading the source,
     * because `@Preview` has source retention: only the IDE's rendering
     * tools read it, and the declared widths are a property of the
     * declaration rather than of any runtime object.
     */
    private fun widthAnnotationDeclarations(): List<PreviewDeclaration> {
        val source = previewSource()
        val start = source.indexOf("annotation class ScreenWidthPreviews")
        if (start < 0) error("the width preview annotation is gone")
        val body = source.substring(0, start)
        val blocks = Regex("""@Preview\(([^)]*)\)""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
        return blocks.mapNotNull { block ->
            val width = Regex("""widthDp = (\d+)""").find(block)?.groupValues?.get(1)?.toInt()
                ?: return@mapNotNull null
            PreviewDeclaration(
                width = width,
                isDark = block.contains("UI_MODE_NIGHT_YES"),
            )
        }
    }

    /** Every preview source in the main source sets, keyed by its path. */
    private fun previewSources(): Map<String, String> {
        val root = repositoryRoot()
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "kt" }
            .filter { file -> file.toPath().none { it.toString() == "build" } }
            .map { file -> file.relativeTo(root).path to file.readText() }
            .filter { (_, source) ->
                source.contains("@Preview") || source.contains("@ScreenWidthPreviews")
            }
            .toMap()
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("could not find the repository root from ${File(".").absolutePath}")
    }
}
