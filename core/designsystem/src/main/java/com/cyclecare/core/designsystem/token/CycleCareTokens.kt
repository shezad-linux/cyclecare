package com.cyclecare.core.designsystem.token

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens.
 *
 * Every colour, size and duration the product uses is declared here, so
 * a palette change propagates rather than needing a hunt for literals.
 *
 * The existing CycleCareTheme hardcodes period-accent colours outside
 * the theme (CycleCareTheme.kt lines 153-176) and its spacing object
 * has zero consumers while screens use raw 16.dp and 24.dp literals.
 * Those are the gaps this file closes.
 *
 * Spec: CycleCare-Redesign-Docs/14-design-system.md
 */

// ── Colour ────────────────────────────────────────────────────────
//
// The existing palette is carried forward; it is cohesive and already
// dark-theme complete. What changes is that each role is named and
// reachable from one place.

object CycleCareColor {
    // Light scheme: Botanical teal, warm neutral surfaces, restrained terracotta
    val LightPrimary = Color(0xFF076364)           // Botanical deep cadet / teal
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFCCE8E5)  // Soft sage teal container
    val LightOnPrimaryContainer = Color(0xFF003738) // Deep teal
    val LightSecondary = Color(0xFF9C4F40)         // Restrained high-contrast terracotta / period accent
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFFBECE8)// Soft warm clay container
    val LightOnSecondaryContainer = Color(0xFF4A1F18)
    val LightTertiary = Color(0xFF39604C)          // Botanical eucalyptus / forest sage
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFD5EADB) // Soft eucalyptus container
    val LightOnTertiaryContainer = Color(0xFF0F3822)
    val LightBackground = Color(0xFFF5F4EF)        // Warm neutral limestone / alabaster
    val LightOnBackground = Color(0xFF202724)      // Deep pine charcoal
    val LightSurface = Color(0xFFFFFFFF)           // Crisp white main card surface
    val LightOnSurface = Color(0xFF202724)
    val LightSurfaceVariant = Color(0xFFEAF0EE)    // Subtle botanical surface wash
    val LightOnSurfaceVariant = Color(0xFF50605A)  // Sage slate secondary text
    val LightOutline = Color(0xFF6F807A)           // Border / outline
    val LightOutlineVariant = Color(0xFFD2DDD8)    // Subtle hairline divider
    val LightInverseSurface = Color(0xFF2B3330)
    val LightInverseOnSurface = Color(0xFFEFF3F1)
    val LightInversePrimary = Color(0xFF62C9C4)
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)

    // Dark scheme: Botanical obsidian, elevated pine surfaces, luminous seafoam & terracotta
    val DarkPrimary = Color(0xFF5CC4BF)            // Luminous botanical seafoam/teal
    val DarkOnPrimary = Color(0xFF003737)
    val DarkPrimaryContainer = Color(0xFF0E4B4B)
    val DarkOnPrimaryContainer = Color(0xFFB0F4F0)
    val DarkSecondary = Color(0xFFEFA594)          // Luminous warm terracotta / clay
    val DarkOnSecondary = Color(0xFF4C1D15)
    val DarkSecondaryContainer = Color(0xFF6E3025)
    val DarkOnSecondaryContainer = Color(0xFFFFDBD1)
    val DarkTertiary = Color(0xFFA2CFB7)           // Luminous sage / eucalyptus
    val DarkOnTertiary = Color(0xFF113824)
    val DarkTertiaryContainer = Color(0xFF2B533E)
    val DarkOnTertiaryContainer = Color(0xFFC6EED7)
    val DarkBackground = Color(0xFF131917)         // Deep botanical obsidian
    val DarkOnBackground = Color(0xFFE6ECE9)
    val DarkSurface = Color(0xFF1A2220)            // Elevated botanical dark surface
    val DarkOnSurface = Color(0xFFE6ECE9)
    val DarkSurfaceVariant = Color(0xFF26322E)     // Subtle botanical dark surface
    val DarkOnSurfaceVariant = Color(0xFFB2C2BC)   // Luminous sage slate
    val DarkOutline = Color(0xFF788A83)            // Outline
    val DarkOutlineVariant = Color(0xFF3D4C46)
    val DarkInverseSurface = Color(0xFFE6ECE9)
    val DarkInverseOnSurface = Color(0xFF1A2220)
    val DarkInversePrimary = Color(0xFF076364)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    // Surface elevation, tint-derived rather than shadow-derived.
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFF9F8F4)
    val LightSurfaceContainer = Color(0xFFF5F4EF)
    val LightSurfaceContainerHigh = Color(0xFFEFECE5)
    val LightSurfaceContainerHighest = Color(0xFFE8E5DD)
    val DarkSurfaceContainerLowest = Color(0xFF0F1413)
    val DarkSurfaceContainerLow = Color(0xFF161D1B)
    val DarkSurfaceContainer = Color(0xFF1A2220)
    val DarkSurfaceContainerHigh = Color(0xFF222B28)
    val DarkSurfaceContainerHighest = Color(0xFF2B3532)

    // Brand / Menstrual-state accents
    val LightPeriodActiveStart = Color(0xFF8A3326) // Deep rust terracotta
    val LightPeriodActiveEnd = Color(0xFF9C4F40)   // High-contrast period accent
    val LightPeriodActiveContainer = Color(0xFFFBECE8)
    val LightPeriodActiveOnContainer = Color(0xFF4A1F18)
    val LightPeriodActiveAccent = Color(0xFF9C4F40)
    val DarkPeriodActiveStart = Color(0xFFBA5748)
    val DarkPeriodActiveEnd = Color(0xFFD87262)
    val DarkPeriodActiveContainer = Color(0xFF4D231C)
    val DarkPeriodActiveOnContainer = Color(0xFFFFD9D1)
    val DarkPeriodActiveAccent = Color(0xFFE88675)

    // ── Large-surface fills ────────────────────────────────────────────
    //
    // The saturated primary is reserved for actions, for selection and for
    // navigation. A full-bleed card or a field of chart bars in that
    // colour dominates the screen and flattens the hierarchy, because
    // everything ends up shouting at the same volume.
    //
    // These are the same hues at a lower saturation, so the palette stays
    // recognisably the same. The text contrast improves rather than
    // degrades: the desaturated hero teal is a little lighter, so white
    // text on it measures higher than on the saturated primary.
    //
    // Measured, with the text colour each surface actually uses:
    //   Light hero start  #155556  white title 8.52:1, subtitle 7.04:1
    //   Light hero end    #3F5A4C  white title 7.56:1, subtitle 6.30:1
    //   Dark hero start   #124A48  title 8.36:1, subtitle 6.21:1
    //   Dark hero end     #2B4A38  title 8.20:1, subtitle 6.13:1
    //   Light chart fill  #1D7677  5.37:1 against a white card
    //   Dark chart fill   #64B2AE  6.59:1 against a dark card
    val LightHeroGradientStart = Color(0xFF155556)
    val LightHeroGradientEnd = Color(0xFF3F5A4C)
    val DarkHeroGradientStart = Color(0xFF124A48)
    val DarkHeroGradientEnd = Color(0xFF2B4A38)
    val LightChartFill = Color(0xFF1D7677)
    val DarkChartFill = Color(0xFF64B2AE)
}

// ── Shape ─────────────────────────────────────────────────────────
//
// The project previously had no Shapes() at all and fell back to
// Material defaults on every card.

object CycleCareRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val full: Dp = 999.dp
}

// ── Spacing ───────────────────────────────────────────────────────
//
// 8dp grid with 4dp half-steps. Replaces the declared-but-unused
// CycleCareSpacing object and raw per-screen literals.

object CycleCareSpace {
    val space2: Dp = 2.dp
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space20: Dp = 20.dp
    val space24: Dp = 24.dp
    val space32: Dp = 32.dp
    val space40: Dp = 40.dp
    val space48: Dp = 48.dp

    /** Default card padding. */
    val cardPadding: Dp = space16

    /** Horizontal screen gutter. */
    val screenGutter: Dp = space20

    /** Vertical gap between sections. */
    val sectionGap: Dp = space24

    // Half-steps used by the existing screens. Added so those call sites
    // can be expressed in tokens rather than raw literals.
    val space6: Dp = 6.dp
    val space10: Dp = 10.dp
    val space14: Dp = 14.dp
    val space18: Dp = 18.dp
    val space28: Dp = 28.dp

    /** Minimum touch target, per Android accessibility guidance. */
    val minTouchTarget: Dp = 48.dp

    /** Minimum list row height without a subtitle. */
    val minListRowHeight: Dp = 56.dp
}

// ── Elevation ─────────────────────────────────────────────────────
//
// Reserved for genuinely floating surfaces: dialogs, sheets, drag
// targets. Cards separate via surface-container steps, not shadows.

object CycleCareElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
}

// ── Motion ────────────────────────────────────────────────────────

object CycleCareMotion {
    const val DURATION_SHORT = 100
    const val DURATION_MEDIUM = 200
    const val DURATION_LONG = 350

    fun standardEasing() = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun emphasizedEasing() = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    fun exitEasing() = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

// ── Accessibility targets ─────────────────────────────────────────
//
// Numeric, so they can be checked rather than intended.

object CycleCareA11y {
    /** Body text against its background. */
    const val CONTRAST_BODY = 4.5

    /** Large text and UI component boundaries. */
    const val CONTRAST_LARGE = 3.0

    /** Font scale the UI must remain usable at. */
    const val MAX_FONT_SCALE = 2.0

    /** Previews must be produced at this scale as well as 1.0. */
    const val PREVIEW_FONT_SCALE = 1.3
}
// ── Typography ───────────────────────────────────────────────────
//
// All fifteen Material roles are defined. Only four were, so the
// remaining eleven silently inherited Material defaults and the app
// had no consistent type ramp.
//
// No FontFamily is set anywhere: the platform default is used
// throughout. Bundling a typeface would narrow localisation coverage
// and grow the APK for no functional gain. Brand character comes from
// weight, size and rhythm instead.
//
// Spec table: 14-design-system.md §4.

object CycleCareTypography {
    val displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp,
    )
    val displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.25).sp,
    )
    val displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
    )
    val headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp,
    )
    val headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp,
    )
    val headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
    )
    val titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    )
    val titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
    )
    val titleSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    )
    val bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    )
    val bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    )
    val bodySmall = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    )
    val labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    )
    val labelMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    )
    val labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    )

    /**
     * Numeric readouts use tabular figures so digits do not jitter as a
     * cycle day advances in place. Without this, the hero number visibly
     * shifts width once per second-level update.
     */
    val numericHero = displayLarge.copy(fontFeatureSettings = "tnum")
    val numericLarge = displayMedium.copy(fontFeatureSettings = "tnum")

    val all: Typography = Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
    )
}

// ── Shapes ───────────────────────────────────────────────────────
//
// The app previously supplied no Shapes(), so every card fell back to
// Material's defaults and the radius scale above was decorative.

object CycleCareShapes {
    val pill = CircleShape
    val extraSmall = RoundedCornerShape(CycleCareRadius.xs)
    val small = RoundedCornerShape(CycleCareRadius.sm)
    val medium = RoundedCornerShape(CycleCareRadius.md)
    val large = RoundedCornerShape(CycleCareRadius.lg)
    val extraLarge = RoundedCornerShape(CycleCareRadius.xl)

    val all: Shapes = Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge,
    )
}

// ── Calendar legend colours ──────────────────────────────────────
//
// These lived as inline `if (darkTheme) Color(0x...) else Color(0x...)`
// expressions in CalendarScreen, which meant hardcoded colours outside
// the theme file and three direct isSystemInDarkTheme() reads. Both are
// spec violations, and the latter also ignored any explicitly forced
// theme, so previews and screenshots rendered the light branch.
//
// Exposed as theme-provided extended colours instead: the values are
// chosen once, here, and consumers read the active scheme.

object CalendarLegendColors {
    val LightPeriodFill = Color(0xFFF5DCD5)
    val LightOvulationFill = Color(0xFFD0E8E6)
    val LightFertileFill = Color(0xFFD5EADB)
    val LightPeriodSelected = Color(0xFFFCEEEA)
    val LightFertileSelected = Color(0xFFE9F6EE)
    val LightOvulationSelected = Color(0xFFE4F4F3)
    // Swatches sit on the bare page background, not behind text, so
    // they use the deep end of each hue. Tested to satisfy >= 3.0:1
    // non-text contrast against the page surface.
    val LightPeriodSwatch = Color(0xFF9C4F40)
    val LightFertileSwatch = Color(0xFF2E684C)
    val LightOvulationSwatch = Color(0xFF076364)

    val DarkPeriodFill = Color(0xFF4A2B25)
    val DarkOvulationFill = Color(0xFF163E3F)
    val DarkFertileFill = Color(0xFF203F30)
    val DarkPeriodSelected = Color(0xFF5D332B)
    val DarkFertileSelected = Color(0xFF2A533F)
    val DarkOvulationSelected = Color(0xFF1F5254)
    val DarkPeriodSwatch = Color(0xFFEFA594)
    val DarkFertileSwatch = Color(0xFFA2CFB7)
    val DarkOvulationSwatch = Color(0xFF5CC4BF)
}

@Immutable
data class ExtendedColors(
    val calendarPeriodFill: Color,
    val calendarOvulationFill: Color,
    val calendarFertileFill: Color,
    val calendarPeriodSelected: Color,
    val calendarFertileSelected: Color,
    val calendarOvulationSelected: Color,
    val calendarPeriodSwatch: Color,
    val calendarFertileSwatch: Color,
    val calendarOvulationSwatch: Color,
)

val LightExtendedColors = ExtendedColors(
    calendarPeriodFill = CalendarLegendColors.LightPeriodFill,
    calendarOvulationFill = CalendarLegendColors.LightOvulationFill,
    calendarFertileFill = CalendarLegendColors.LightFertileFill,
    calendarPeriodSelected = CalendarLegendColors.LightPeriodSelected,
    calendarFertileSelected = CalendarLegendColors.LightFertileSelected,
    calendarOvulationSelected = CalendarLegendColors.LightOvulationSelected,
    calendarPeriodSwatch = CalendarLegendColors.LightPeriodSwatch,
    calendarFertileSwatch = CalendarLegendColors.LightFertileSwatch,
    calendarOvulationSwatch = CalendarLegendColors.LightOvulationSwatch,
)

val DarkExtendedColors = ExtendedColors(
    calendarPeriodFill = CalendarLegendColors.DarkPeriodFill,
    calendarOvulationFill = CalendarLegendColors.DarkOvulationFill,
    calendarFertileFill = CalendarLegendColors.DarkFertileFill,
    calendarPeriodSelected = CalendarLegendColors.DarkPeriodSelected,
    calendarFertileSelected = CalendarLegendColors.DarkFertileSelected,
    calendarOvulationSelected = CalendarLegendColors.DarkOvulationSelected,
    calendarPeriodSwatch = CalendarLegendColors.DarkPeriodSwatch,
    calendarFertileSwatch = CalendarLegendColors.DarkFertileSwatch,
    calendarOvulationSwatch = CalendarLegendColors.DarkOvulationSwatch,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** The active extended colours. Never branches on the system setting. */
val extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable
    get() = LocalExtendedColors.current
