package com.cyclecare.core.designsystem.token

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.cyclecare.core.designsystem.metricCardAccents
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Test

/**
 * The dashboard metric cards.
 *
 * The four cards are siblings, so they share one surface. Only "Avg period"
 * keeps the warm period tint, because that is the one distinction a reader
 * can act on. The previous mix of mint, blush, sage-grey and near-white
 * had no rule behind it, and three of the four were arbitrary.
 *
 * The cards draw their label in onSurfaceVariant and their value in
 * onSurface on whichever accent they are given, so every combination the
 * app actually uses is checked here against the body text target.
 */
class MetricCardAccentTest {

    private fun contrastRatio(a: Color, b: Color): Double {
        val lighter = max(a.luminance(), b.luminance())
        val darker = min(a.luminance(), b.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** The colour a translucent accent composites to over the card behind it. */
    private fun composite(accent: Color, over: Color): Color {
        val alpha = accent.alpha
        return Color(
            red = accent.red * alpha + over.red * (1 - alpha),
            green = accent.green * alpha + over.green * (1 - alpha),
            blue = accent.blue * alpha + over.blue * (1 - alpha),
            alpha = 1f,
        )
    }

    private fun lightScheme(): ColorScheme = lightColorScheme(
        primary = CycleCareColor.LightPrimary,
        onPrimary = CycleCareColor.LightOnPrimary,
        primaryContainer = CycleCareColor.LightPrimaryContainer,
        onPrimaryContainer = CycleCareColor.LightOnPrimaryContainer,
        secondary = CycleCareColor.LightSecondary,
        onSecondary = CycleCareColor.LightOnSecondary,
        secondaryContainer = CycleCareColor.LightSecondaryContainer,
        onSecondaryContainer = CycleCareColor.LightOnSecondaryContainer,
        tertiary = CycleCareColor.LightTertiary,
        onTertiary = CycleCareColor.LightOnTertiary,
        tertiaryContainer = CycleCareColor.LightTertiaryContainer,
        onTertiaryContainer = CycleCareColor.LightOnTertiaryContainer,
        background = CycleCareColor.LightBackground,
        onBackground = CycleCareColor.LightOnBackground,
        surface = CycleCareColor.LightSurface,
        onSurface = CycleCareColor.LightOnSurface,
        surfaceVariant = CycleCareColor.LightSurfaceVariant,
        onSurfaceVariant = CycleCareColor.LightOnSurfaceVariant,
    )

    private fun darkScheme(): ColorScheme = darkColorScheme(
        primary = CycleCareColor.DarkPrimary,
        onPrimary = CycleCareColor.DarkOnPrimary,
        primaryContainer = CycleCareColor.DarkPrimaryContainer,
        onPrimaryContainer = CycleCareColor.DarkOnPrimaryContainer,
        secondary = CycleCareColor.DarkSecondary,
        onSecondary = CycleCareColor.DarkOnSecondary,
        secondaryContainer = CycleCareColor.DarkSecondaryContainer,
        onSecondaryContainer = CycleCareColor.DarkOnSecondaryContainer,
        tertiary = CycleCareColor.DarkTertiary,
        onTertiary = CycleCareColor.DarkOnTertiary,
        tertiaryContainer = CycleCareColor.DarkTertiaryContainer,
        onTertiaryContainer = CycleCareColor.DarkOnTertiaryContainer,
        background = CycleCareColor.DarkBackground,
        onBackground = CycleCareColor.DarkOnBackground,
        surface = CycleCareColor.DarkSurface,
        onSurface = CycleCareColor.DarkOnSurface,
        surfaceVariant = CycleCareColor.DarkSurfaceVariant,
        onSurfaceVariant = CycleCareColor.DarkOnSurfaceVariant,
    )

    @Test
    fun theSiblingMetricsShareOneNeutralSurface() {
        val accents = metricCardAccents(lightScheme())

        // Three of the four cards are the neutral one, and one is the
        // period one. There is no third surface.
        assertThat(accents.neutral).isNotEqualTo(accents.period)
        assertThat(accents.neutral).isEqualTo(
            lightScheme().surfaceVariant.copy(alpha = 0.82f),
        )
    }

    @Test
    fun theNeutralSurfaceCarriesTheLabelAndValueInLight() {
        val scheme = lightScheme()
        val accents = metricCardAccents(scheme)
        val surface = composite(accents.neutral, scheme.surface)

        assertThat(contrastRatio(scheme.onSurface, surface)).isAtLeast(4.5)
        assertThat(contrastRatio(scheme.onSurfaceVariant, surface)).isAtLeast(4.5)
    }

    @Test
    fun theNeutralSurfaceCarriesTheLabelAndValueInDark() {
        val scheme = darkScheme()
        val accents = metricCardAccents(scheme)
        val surface = composite(accents.neutral, scheme.surface)

        assertThat(contrastRatio(scheme.onSurface, surface)).isAtLeast(4.5)
        assertThat(contrastRatio(scheme.onSurfaceVariant, surface)).isAtLeast(4.5)
    }

    @Test
    fun thePeriodSurfaceCarriesTheLabelAndValueInLight() {
        val scheme = lightScheme()
        val accents = metricCardAccents(scheme)
        val surface = composite(accents.period, scheme.surface)

        assertThat(contrastRatio(scheme.onSurface, surface)).isAtLeast(4.5)
        assertThat(contrastRatio(scheme.onSurfaceVariant, surface)).isAtLeast(4.5)
    }

    @Test
    fun thePeriodSurfaceCarriesTheLabelAndValueInDark() {
        val scheme = darkScheme()
        val accents = metricCardAccents(scheme)
        val surface = composite(accents.period, scheme.surface)

        assertThat(contrastRatio(scheme.onSurface, surface)).isAtLeast(4.5)
        assertThat(contrastRatio(scheme.onSurfaceVariant, surface)).isAtLeast(4.5)
    }

    @Test
    fun theNeutralIsTheLighterOfTheTwoSurfacesInLight() {
        // The label is onSurfaceVariant in every case, so the lighter
        // surface is the safer one for it.
        val scheme = lightScheme()
        val accents = metricCardAccents(scheme)
        val neutral = composite(accents.neutral, scheme.surface)
        val period = composite(accents.period, scheme.surface)

        assertThat(neutral.luminance()).isGreaterThan(period.luminance())
    }
}
