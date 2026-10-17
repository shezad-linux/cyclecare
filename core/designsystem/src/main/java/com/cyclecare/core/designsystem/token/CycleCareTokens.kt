package com.cyclecare.core.designsystem.token

import androidx.compose.ui.graphics.Color
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