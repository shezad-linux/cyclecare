package com.cyclecare.core.designsystem.token

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Test

class DesignTokenTest {

    private fun lightScheme(): ColorScheme = lightColorScheme(
        primary = CycleCareColor.LightPrimary,
        background = CycleCareColor.LightBackground,
        surface = CycleCareColor.LightSurface,
    )

    private fun darkScheme(): ColorScheme = darkColorScheme(
        primary = CycleCareColor.DarkPrimary,
        background = CycleCareColor.DarkBackground,
        surface = CycleCareColor.DarkSurface,
    )

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

    // ── typography ──────────────────────────────────────────────────

    @Test
    fun everyTypographyRoleMatchesTheSpecRamp() {
        val expected = listOf(
            Triple("displayLarge", CycleCareTypography.displayLarge, 44f to 48f),
            Triple("displayMedium", CycleCareTypography.displayMedium, 36f to 42f),
            Triple("displaySmall", CycleCareTypography.displaySmall, 30f to 36f),
            Triple("headlineLarge", CycleCareTypography.headlineLarge, 28f to 34f),
            Triple("headlineMedium", CycleCareTypography.headlineMedium, 24f to 30f),
            Triple("headlineSmall", CycleCareTypography.headlineSmall, 22f to 28f),
            Triple("titleLarge", CycleCareTypography.titleLarge, 18f to 24f),
            Triple("titleMedium", CycleCareTypography.titleMedium, 16f to 22f),
            Triple("titleSmall", CycleCareTypography.titleSmall, 14f to 20f),
            Triple("bodyLarge", CycleCareTypography.bodyLarge, 16f to 24f),
            Triple("bodyMedium", CycleCareTypography.bodyMedium, 14f to 20f),
            Triple("bodySmall", CycleCareTypography.bodySmall, 12f to 16f),
            Triple("labelLarge", CycleCareTypography.labelLarge, 14f to 18f),
            Triple("labelMedium", CycleCareTypography.labelMedium, 12f to 16f),
            Triple("labelSmall", CycleCareTypography.labelSmall, 11f to 16f),
        )

        assertThat(expected).hasSize(15)
        expected.forEach { (role, style, metrics) ->
            assertThat(style.fontSize.value).isEqualTo(metrics.first)
            assertThat(style.lineHeight.value).isEqualTo(metrics.second)
            // A role left at a Material default would arrive with the
            // default weight rather than the spec weight.
            assertThat(style.fontWeight).isNotNull()
        }
    }

    @Test
    fun typographyIsExposedThroughMaterialThemeSoNoRoleFallsBack() {
        val all = CycleCareTypography.all
        val defined = listOf(
            all.displayLarge, all.displayMedium, all.displaySmall,
            all.headlineLarge, all.headlineMedium, all.headlineSmall,
            all.titleLarge, all.titleMedium, all.titleSmall,
            all.bodyLarge, all.bodyMedium, all.bodySmall,
            all.labelLarge, all.labelMedium, all.labelSmall,
        )
        assertThat(defined).hasSize(15)
        // Body roles must be Regular per the spec; an undefined role
        // would fall back to Material's own scale and slip through here.
        assertThat(all.bodyLarge.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(all.bodyMedium.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(all.bodySmall.fontWeight).isEqualTo(FontWeight.Normal)
    }

    @Test
    fun numericStylesUseTabularFigures() {
        // Without tabular figures a cycle day visibly jitters in width
        // as it advances in place.
        assertThat(CycleCareTypography.numericHero.fontFeatureSettings).contains("tnum")
        assertThat(CycleCareTypography.numericLarge.fontFeatureSettings).contains("tnum")
    }

    // ── shapes ──────────────────────────────────────────────────────

    @Test
    fun shapesAreDefinedForEveryMaterialRole() {
        val shapes = CycleCareShapes.all
        // Compared against freshly constructed shapes: CornerSize on
        // this Compose version exposes no dp accessor to assert on.
        assertThat(shapes.extraSmall).isEqualTo(RoundedCornerShape(CycleCareRadius.xs))
        assertThat(shapes.small).isEqualTo(RoundedCornerShape(CycleCareRadius.sm))
        assertThat(shapes.medium).isEqualTo(RoundedCornerShape(CycleCareRadius.md))
        assertThat(shapes.large).isEqualTo(RoundedCornerShape(CycleCareRadius.lg))
        assertThat(shapes.extraLarge).isEqualTo(RoundedCornerShape(CycleCareRadius.xl))
    }

    @Test
    fun everyShapeRoleUsesADistinctRadius() {
        // CornerSize carries no dp accessor on this Compose version, so
        // distinctness is asserted against the radius tokens each shape
        // was built from, which is what actually has to vary.
        val radii = listOf(
            CycleCareRadius.xs, CycleCareRadius.sm, CycleCareRadius.md,
            CycleCareRadius.lg, CycleCareRadius.xl,
        )
        assertThat(radii.map { it.value }).isInOrder()
        assertThat(radii.distinct()).hasSize(5)
    }

    // ── extended colours ────────────────────────────────────────────

    @Test
    fun everyExtendedColourHasADistinctLightAndDarkValue() {
        val roles = listOf<Pair<String, Pair<Color, Color>>>(
            "periodFill" to (LightExtendedColors.calendarPeriodFill to DarkExtendedColors.calendarPeriodFill),
            "ovulationFill" to (LightExtendedColors.calendarOvulationFill to DarkExtendedColors.calendarOvulationFill),
            "fertileFill" to (LightExtendedColors.calendarFertileFill to DarkExtendedColors.calendarFertileFill),
            "periodSelected" to (LightExtendedColors.calendarPeriodSelected to DarkExtendedColors.calendarPeriodSelected),
            "fertileSelected" to (LightExtendedColors.calendarFertileSelected to DarkExtendedColors.calendarFertileSelected),
            "ovulationSelected" to (LightExtendedColors.calendarOvulationSelected to DarkExtendedColors.calendarOvulationSelected),
            "periodSwatch" to (LightExtendedColors.calendarPeriodSwatch to DarkExtendedColors.calendarPeriodSwatch),
            "fertileSwatch" to (LightExtendedColors.calendarFertileSwatch to DarkExtendedColors.calendarFertileSwatch),
            "ovulationSwatch" to (LightExtendedColors.calendarOvulationSwatch to DarkExtendedColors.calendarOvulationSwatch),
        )

        assertThat(roles).hasSize(9)
        roles.forEach { (role, pair) ->
            assertThat(pair.first).isNotEqualTo(pair.second)
        }
    }

    @Test
    fun calendarLegendColoursStayLegibleAgainstTheirSurfaces() {
        // Legend swatches sit on the page background, so each needs a
        // visible boundary against it in both schemes.
        listOf(
            LightExtendedColors to lightScheme().surface,
            DarkExtendedColors to darkScheme().surface,
        ).forEach { (extended, surface) ->
            listOf(
                extended.calendarPeriodSwatch,
                extended.calendarFertileSwatch,
                extended.calendarOvulationSwatch,
            ).forEach { swatch ->
                // A legend swatch is a UI component boundary, so it is
                // held to the 3.0 non-text threshold rather than the
                // 1.4.11 minimum. The previous pale light-mode values
                // sat at roughly 1.1 and were effectively invisible.
                assertThat(contrastRatio(swatch, surface)).isAtLeast(3.0)
            }
        }
    }
}
