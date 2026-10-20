package com.cyclecare.core.designsystem

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The preview widths the design system has to cover.
 *
 * The layouts have breakpoints at 340dp, 360dp, 420dp and 430dp, so the
 * previews have to span below the first of those and well above the last.
 * A preview list that only had one width would compile and would still
 * miss every layout problem the breakpoints exist to catch.
 *
 * `@Preview` has source retention, because only the IDE's rendering tools
 * read it, so it is not visible at runtime and the declared widths can only
 * be read from the source. That is also the thing being asserted: the
 * widths are a property of the declaration, not of any runtime object.
 */
class PreviewCoverageTest {

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
}
