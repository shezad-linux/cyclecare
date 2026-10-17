package com.cyclecare.core.designsystem

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "360 Light",
    widthDp = 360,
    showBackground = true,
    backgroundColor = 0xFFF8F3EC,
)
@Preview(
    name = "360 Light 1.3x",
    widthDp = 360,
    fontScale = 1.3f,
    showBackground = true,
    backgroundColor = 0xFFF8F3EC,
)
@Preview(
    name = "360 Dark",
    widthDp = 360,
    showBackground = true,
    backgroundColor = 0xFF19131A,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "360 Dark 1.3x",
    widthDp = 360,
    fontScale = 1.3f,
    showBackground = true,
    backgroundColor = 0xFF19131A,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class ScreenDebugPreviews
