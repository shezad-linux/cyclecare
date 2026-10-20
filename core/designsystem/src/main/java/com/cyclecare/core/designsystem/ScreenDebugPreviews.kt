package com.cyclecare.core.designsystem

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Previews for the widths the layout actually has to survive.
 *
 * The breakpoints in the app are 340dp, 360dp, 420dp and 430dp, so
 * 320dp exercises every stacked arrangement, 360dp sits on the breakpoint
 * itself, and 600dp and 840dp are the side-by-side arrangements.
 *
 * Applied only to the components and screens that change shape at those
 * widths. Generating one of these for every trivial component would make
 * the preview list unusable and would not find anything.
 */
@Preview(
    name = "320 Light",
    widthDp = 320,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
)
@Preview(
    name = "360 Light",
    widthDp = 360,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
)
@Preview(
    name = "600 Light",
    widthDp = 600,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
)
@Preview(
    name = "840 Light",
    widthDp = 840,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
)
@Preview(
    name = "320 Dark",
    widthDp = 320,
    showBackground = true,
    backgroundColor = 0xFF141414,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "840 Dark",
    widthDp = 840,
    showBackground = true,
    backgroundColor = 0xFF141414,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class ScreenWidthPreviews

/**
 * Previews for a screen at one width in both colour schemes, with the
 * large font scale the layout has to survive.
 *
 * @param widthDp the width to preview at
 */
@Preview(
    name = "Light",
    widthDp = 360,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
)
@Preview(
    name = "Light 1.3x",
    widthDp = 360,
    fontScale = 1.3f,
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
)
@Preview(
    name = "Dark",
    widthDp = 360,
    showBackground = true,
    backgroundColor = 0xFF141414,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Dark 1.3x",
    widthDp = 360,
    fontScale = 1.3f,
    showBackground = true,
    backgroundColor = 0xFF141414,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class ScreenDebugPreviews
