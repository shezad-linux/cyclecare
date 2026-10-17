package com.cyclecare.core.designsystem.token

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Test

class DesignTokenTest {

    // ── contrast ────────────────────────────────────────────────────

    private fun contrastRatio(a: Color, b: Color): Double {
        val lighter = max(a.luminance(), b.luminance())
        val darker = min(a.luminance(), b.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun everyTextRoleMeetsTheBodyContrastTargetInLight() {
        val scheme = lightColorScheme(
            primary = CycleCareColor.LightPrimary,
            onPrimary = CycleCareColor.LightOnPrimary,
            background = CycleCareColor.LightBackground,
            onBackground = CycleCareColor.LightOnBackground,
            surface = CycleCareColor.LightSurface,
            onSurface = CycleCareColor.LightOnSurface,
            surfaceVariant = CycleCareColor.LightSurfaceVariant,
            onSurfaceVariant = CycleCareColor.LightOnSurfaceVariant,
            error = CycleCareColor.LightError,
            onError = CycleCareColor.LightOnError,
        )

        listOf(
            scheme.onBackground to scheme.background,
            scheme.onSurface to scheme.surface,
            scheme.onSurfaceVariant to scheme.surfaceVariant,
            scheme.onPrimary to scheme.primary,
            scheme.onError to scheme.error,
        ).forEach { (foreground, background) ->
            val ratio = contrastRatio(foreground, background)
            assertThat(ratio).isAtLeast(CycleCareA11y.CONTRAST_BODY)
        }
    }

    @Test
    fun everyTextRoleMeetsTheBodyContrastTargetInDark() {
        val scheme = darkColorScheme(
            primary = CycleCareColor.DarkPrimary,
            onPrimary = CycleCareColor.DarkOnPrimary,
            background = CycleCareColor.DarkBackground,
            onBackground = CycleCareColor.DarkOnBackground,
            surface = CycleCareColor.DarkSurface,
            onSurface = CycleCareColor.DarkOnSurface,
            surfaceVariant = CycleCareColor.DarkSurfaceVariant,
            onSurfaceVariant = CycleCareColor.DarkOnSurfaceVariant,
            error = CycleCareColor.DarkError,
            onError = CycleCareColor.DarkOnError,
        )

        listOf(
            scheme.onBackground to scheme.background,
            scheme.onSurface to scheme.surface,
            scheme.onSurfaceVariant to scheme.surfaceVariant,
            scheme.onPrimary to scheme.primary,
            scheme.onError to scheme.error,
        ).forEach { (foreground, background) ->
            val ratio = contrastRatio(foreground, background)
            assertThat(ratio).isAtLeast(CycleCareA11y.CONTRAST_BODY)
        }
    }

    @Test
    fun outlinedBordersMeetTheLargeTextContrastTarget() {
        listOf(
            CycleCareColor.LightOutline to CycleCareColor.LightSurface,
            CycleCareColor.DarkOutline to CycleCareColor.DarkSurface,
        ).forEach { (border, surface) ->
            assertThat(contrastRatio(border, surface))
                .isAtLeast(CycleCareA11y.CONTRAST_LARGE)
        }
    }

    // ── greyscale distinguishability ────────────────────────────────

    @Test
    fun confirmedAndPredictedPeriodHaveDifferentLuminanceSoTheySurviveGreyscale() {
        // If the two fills collapse to the same grey, the dashed border
        // and differing icons become the only differentiator on a
        // greyscale or low-vision display, which is exactly the case the
        // three-channel rule exists for.
        val confirmedGrey = CycleCareColor.LightPrimaryContainer.luminance()
        val predictedGrey = CycleCareColor.LightSurfaceVariant.luminance()

        assertThat(abs(confirmedGrey - predictedGrey)).isAtLeast(0.02f)
    }

    @Test
    fun confirmedAndPredictedPeriodUseDifferentIconsAndBorders() {
        assertThat(CycleStateKind.CONFIRMED_PERIOD.icon)
            .isNotEqualTo(CycleStateKind.PREDICTED_PERIOD.icon)
        assertThat(CycleStateKind.CONFIRMED_PERIOD.border)
            .isNotEqualTo(CycleStateKind.PREDICTED_PERIOD.border)
    }

    @Test
    fun everyCycleStateIsIdentifiableWithoutColour() {
        CycleStateKind.entries.forEach { kind ->
            assertThat(kind.distinguishesFromColourAlone).isTrue()
        }
    }

    @Test
    fun onlyTheConfirmedStateIsNotAnEstimate() {
        assertThat(CycleStateKind.CONFIRMED_PERIOD.isEstimated).isFalse()
        assertThat(CycleStateKind.PREDICTED_PERIOD.isEstimated).isTrue()
        assertThat(CycleStateKind.ESTIMATED_FERTILE.isEstimated).isTrue()
        assertThat(CycleStateKind.ESTIMATED_OVULATION.isEstimated).isTrue()
    }

    @Test
    fun everyCycleStateHasADistinctAccessibilityLabel() {
        val labels = CycleStateKind.entries.map { it.accessibilityLabel }

        assertThat(labels).containsNoDuplicates()
        assertThat(labels).contains("Logged period")
        assertThat(labels).contains("Estimated period")
    }

    @Test
    fun everyCycleStateHasADistinctIcon() {
        val icons = CycleStateKind.entries.map { it.icon }

        assertThat(icons).containsNoDuplicates()
    }

    // ── spacing and radius ──────────────────────────────────────────

    @Test
    fun spacingFollowsTheEightDpGridWithHalfSteps() {
        val values = listOf(
            CycleCareSpace.space2.value,
            CycleCareSpace.space4.value,
            CycleCareSpace.space8.value,
            CycleCareSpace.space12.value,
            CycleCareSpace.space16.value,
            CycleCareSpace.space20.value,
            CycleCareSpace.space24.value,
            CycleCareSpace.space32.value,
            CycleCareSpace.space40.value,
            CycleCareSpace.space48.value,
        )

        // 8dp grid with half-steps: every token is a multiple of 2dp,
        // with space2 the deliberate hairline below the 4dp step.
        values.forEach { value ->
            val remainder = value % 2f
            assertThat(abs(remainder)).isAtMost(0.01f)
        }
        assertThat(values).isInOrder()
    }

    @Test
    fun touchTargetMeetsTheAccessibilityMinimum() {
        assertThat(CycleCareSpace.minTouchTarget.value).isAtLeast(48f)
        assertThat(CycleCareSpace.minListRowHeight.value).isAtLeast(48f)
    }

    @Test
    fun radiiIncreaseMonotonically() {
        val values = listOf(
            CycleCareRadius.xs.value,
            CycleCareRadius.sm.value,
            CycleCareRadius.md.value,
            CycleCareRadius.lg.value,
            CycleCareRadius.xl.value,
        )

        assertThat(values).isInOrder()
        assertThat(CycleCareRadius.full.value).isAtLeast(CycleCareRadius.xl.value)
    }
}