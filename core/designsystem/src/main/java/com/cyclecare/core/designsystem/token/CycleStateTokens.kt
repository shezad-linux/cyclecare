package com.cyclecare.core.designsystem.token

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic cycle states.
 *
 * The most important rule in the product: a user must never confuse
 * something they recorded with something the app guessed.
 *
 * Three independent channels encode state, so nothing is carried by hue
 * alone and everything survives greyscale:
 *
 *   fill         solid for confirmed, flat for estimated
 *   border style solid for confirmed, dashed for estimated
 *   icon         filled droplet versus dashed or dotted ring
 *
 * Confirmed and predicted are separated by border style AND by a
 * different fill treatment, not by two similar pinks.
 *
 * Shape, icon and spoken label are declared once, on the enum, so a
 * state cannot acquire a new visual treatment without its accessible
 * label changing too.
 *
 * Spec: CycleCare-Redesign-Docs/14-design-system.md section 6.
 */
enum class CycleStateKind(
    val border: CycleStateBorder,
    val icon: CycleStateIcon,
    val accessibilityLabel: String,
    val isEstimated: Boolean,
) {
    /** Filled droplet: the user logged this. */
    CONFIRMED_PERIOD(
        border = CycleStateBorder.SOLID,
        icon = CycleStateIcon.FILLED_DROPLET,
        accessibilityLabel = "Logged period",
        isEstimated = false,
    ),

    PREDICTED_PERIOD(
        border = CycleStateBorder.DASHED,
        icon = CycleStateIcon.DASHED_RING,
        accessibilityLabel = "Estimated period",
        isEstimated = true,
    ),

    ESTIMATED_FERTILE(
        border = CycleStateBorder.DOTTED,
        icon = CycleStateIcon.DOTTED_RING,
        accessibilityLabel = "Estimated fertile days",
        isEstimated = true,
    ),

    ESTIMATED_OVULATION(
        border = CycleStateBorder.DOTTED,
        icon = CycleStateIcon.DOT,
        accessibilityLabel = "Estimated ovulation",
        isEstimated = true,
    ),
    ;

    /**
     * Every state is identifiable without colour: a solid border with a
     * filled icon for the confirmed case, dashed or dotted otherwise.
     */
    val distinguishesFromColourAlone: Boolean
        get() = when (this) {
            CONFIRMED_PERIOD ->
                border == CycleStateBorder.SOLID && icon == CycleStateIcon.FILLED_DROPLET
            else -> border == CycleStateBorder.DASHED || border == CycleStateBorder.DOTTED
        }
}

enum class CycleStateBorder {
    NONE,
    SOLID,
    DASHED,
    DOTTED,
}

enum class CycleStateIcon {
    /** Filled droplet: the user logged this. */
    FILLED_DROPLET,

    /** Dashed ring: estimated period. */
    DASHED_RING,

    /** Dotted ring: estimated fertile days. */
    DOTTED_RING,

    /** Small dot: estimated ovulation. */
    DOT,

    /** Tick, bottom-right: this day has entries. */
    LOG_TICK,
}

/**
 * Resolved colours for one cycle state under the active theme.
 *
 * Passed in rather than read from a global, so a forced theme (preview
 * or screenshot) resolves correctly. The existing PeriodActive*
 * helpers call isSystemInDarkTheme() directly and therefore ignore an
 * explicit darkTheme argument; that class of bug is why the scheme is a
 * parameter here.
 */
data class CycleStateColors(
    val container: Color,
    val onContainer: Color,
    val border: Color,
)

data class CycleStateStyle(
    val kind: CycleStateKind,
    val colors: CycleStateColors,
) {
    val border: CycleStateBorder get() = kind.border
    val icon: CycleStateIcon get() = kind.icon
    val accessibilityLabel: String get() = kind.accessibilityLabel
    val isEstimated: Boolean get() = kind.isEstimated
}

@Composable
@ReadOnlyComposable
fun cycleStateStyleFor(
    kind: CycleStateKind,
    colors: ColorScheme,
): CycleStateStyle = CycleStateStyle(
    kind = kind,
    colors = when (kind) {
        CycleStateKind.CONFIRMED_PERIOD -> CycleStateColors(
            container = colors.primaryContainer,
            onContainer = colors.onPrimaryContainer,
            border = colors.primary,
        )

        CycleStateKind.PREDICTED_PERIOD -> CycleStateColors(
            container = colors.surfaceVariant,
            onContainer = colors.onSurfaceVariant,
            border = colors.outline,
        )

        CycleStateKind.ESTIMATED_FERTILE,
        CycleStateKind.ESTIMATED_OVULATION,
        -> CycleStateColors(
            container = colors.tertiaryContainer,
            onContainer = colors.onTertiaryContainer,
            border = colors.tertiary,
        )
    },
)