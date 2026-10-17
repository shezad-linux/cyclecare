package com.cyclecare.core.designsystem.token

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    // Light scheme
    val LightPrimary = Color(0xFF7A3458)
    val LightOnPrimary = Color(0xFFFDF7F2)
    val LightPrimaryContainer = Color(0xFFF0D7E2)
    val LightOnPrimaryContainer = Color(0xFF3A1328)
    val LightSecondary = Color(0xFFDC6C57)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFFFD9D1)
    val LightOnSecondaryContainer = Color(0xFF4C160D)
    val LightTertiary = Color(0xFF6F8D7E)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFD8E8DE)
    val LightOnTertiaryContainer = Color(0xFF173527)
    val LightBackground = Color(0xFFF8F3EC)
    val LightOnBackground = Color(0xFF241826)
    val LightSurface = Color(0xFFFFFBF7)
    val LightOnSurface = Color(0xFF241826)
    val LightSurfaceVariant = Color(0xFFF4E5E2)
    val LightOnSurfaceVariant = Color(0xFF655763)
    val LightOutline = Color(0xFF8B7A84)
    val LightOutlineVariant = Color(0xFFD9C7D1)
    val LightInverseSurface = Color(0xFF392D37)
    val LightInverseOnSurface = Color(0xFFF8EEF3)
    val LightInversePrimary = Color(0xFFFFB1C8)
    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFdad6)
    val LightOnErrorContainer = Color(0xFF410002)

    // Dark scheme
    val DarkPrimary = Color(0xFFFFC9D8)
    val DarkOnPrimary = Color(0xFF43192E)
    val DarkPrimaryContainer = Color(0xFF5F2542)
    val DarkOnPrimaryContainer = Color(0xFFFFD9E5)
    val DarkSecondary = Color(0xFFFFB19B)
    val DarkOnSecondary = Color(0xFF5D1F12)
    val DarkSecondaryContainer = Color(0xFF823426)
    val DarkOnSecondaryContainer = Color(0xFFFFD9D1)
    val DarkTertiary = Color(0xFFC3D7CB)
    val DarkOnTertiary = Color(0xFF25392E)
    val DarkTertiaryContainer = Color(0xFF3B5144)
    val DarkOnTertiaryContainer = Color(0xFFDDEFE4)
    val DarkBackground = Color(0xFF19131A)
    val DarkOnBackground = Color(0xFFF3E9EF)
    val DarkSurface = Color(0xFF251D27)
    val DarkOnSurface = Color(0xFFF3E9EF)
    val DarkSurfaceVariant = Color(0xFF413541)
    val DarkOnSurfaceVariant = Color(0xFFD4C3CE)
    val DarkOutline = Color(0xFFA996A2)
    val DarkOutlineVariant = Color(0xFF51444F)
    val DarkInverseSurface = Color(0xFFF3E9EF)
    val DarkInverseOnSurface = Color(0xFF352934)
    val DarkInversePrimary = Color(0xFF7A3458)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    // Surface elevation, tint-derived rather than shadow-derived.
    // Keeps cards separable without heavy shadows.
    val LightSurfaceContainerLowest = Color(0xFFFFFBF7)
    val LightSurfaceContainerLow = Color(0xFFFDF7F1)
    val LightSurfaceContainer = Color(0xFFF8F3EC)
    val LightSurfaceContainerHigh = Color(0xFFF3EDE6)
    val LightSurfaceContainerHighest = Color(0xFFEDE7E0)
    val DarkSurfaceContainerLowest = Color(0xFF19131A)
    val DarkSurfaceContainerLow = Color(0xFF211A23)
    val DarkSurfaceContainer = Color(0xFF251D27)
    val DarkSurfaceContainerHigh = Color(0xFF2F2731)
    val DarkSurfaceContainerHighest = Color(0xFF3A313B)

    // Brand accents, previously hardcoded in CycleCareTheme.kt.
    val LightPeriodActiveStart = Color(0xFF8F2430)
    val LightPeriodActiveEnd = Color(0xFFB43A3F)
    val LightPeriodActiveContainer = Color(0xFF4C1F28)
    val LightPeriodActiveOnContainer = Color(0xFFFFECE8)
    val LightPeriodActiveAccent = Color(0xFFB7333F)
    val DarkPeriodActiveStart = Color(0xFFB7333F)
    val DarkPeriodActiveEnd = Color(0xFFE0635B)
    val DarkPeriodActiveContainer = Color(0xFFFFD7CE)
    val DarkPeriodActiveOnContainer = Color(0xFF3F1F27)
    val DarkPeriodActiveAccent = Color(0xFFFFB8AF)
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
    val LightPeriodFill = Color(0xFFF7CCD6)
    val LightOvulationFill = Color(0xFFCFE8F7)
    val LightFertileFill = Color(0xFFD8F5E6)
    val LightPeriodSelected = Color(0xFFFFEEF3)
    val LightFertileSelected = Color(0xFFEAF8EF)
    val LightOvulationSelected = Color(0xFFEAF5FB)
    // Swatches sit on the bare page background, not behind text, so
    // they use the deep end of each hue. The previous pale values were
    // the same as the fills and landed near 1.1:1 against the surface.
    val LightPeriodSwatch = Color(0xFF8F2430)
    val LightFertileSwatch = Color(0xFF2F6B4F)
    val LightOvulationSwatch = Color(0xFF1F4E63)

    val DarkPeriodFill = Color(0xFF6A3B4B)
    val DarkOvulationFill = Color(0xFF31556C)
    val DarkFertileFill = Color(0xFF2E5B47)
    val DarkPeriodSelected = Color(0xFF3F2531)
    val DarkFertileSelected = Color(0xFF213E31)
    val DarkOvulationSelected = Color(0xFF213D4D)
    val DarkPeriodSwatch = Color(0xFFB78493)
    val DarkFertileSwatch = Color(0xFF86C0A2)
    val DarkOvulationSwatch = Color(0xFF8AB6D1)
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
