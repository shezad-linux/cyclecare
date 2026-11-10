package com.cyclecare.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Maximum content width on wide windows.
 *
 * CycleCare is a phone-first product and its screens are composed for a
 * single narrow column. Without a ceiling, a tablet stretches every row of
 * text, every chart, every button and every form field to 800+ dp, which is
 * unreadable and unusable even though nothing "breaks".
 *
 * This is not an adaptive layout. It is the cheapest form of one: every
 * destination is centred and capped, nothing is reflowed into two panes, and
 * nothing about the navigation, the theme or the components changes. The bar
 * above it (top app bar / bottom navigation) stays full width, which is what
 * a full bar on a wide canvas looks like.
 *
 * 840 dp matches the Material expanded-width breakpoint (600 / 840).
 */
internal val CenterConstrainedMaxWidth: Dp = 840.dp

/**
 * Centres [content] horizontally inside the window and caps its width at
 * [CenterConstrainedMaxWidth]. The window background fills the side stripes,
 * painted with the same [androidx.compose.material3.ColorScheme.background]
 * the screens use, so the transition at the cap is invisible.
 *
 * A no-op on compact windows (≤ 840 dp), where the cap is never reached.
 */
@Composable
internal fun CenterConstrainedBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Order matters: widthIn outer, fillMaxWidth inner — the inner
        // fills the narrowed range. fillMaxWidth outer would force the
        // exact incoming min (the full window) before widthIn is applied.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = CenterConstrainedMaxWidth)
                .fillMaxWidth(),
            content = content,
        )
    }
}
