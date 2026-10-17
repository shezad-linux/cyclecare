package com.cyclecare.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.cyclecare.core.designsystem.token.CycleCareShapes
import com.cyclecare.core.designsystem.token.CycleCareTypography
import com.cyclecare.core.designsystem.token.DarkExtendedColors
import com.cyclecare.core.designsystem.token.LightExtendedColors
import com.cyclecare.core.designsystem.token.LocalExtendedColors
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A3458),
    onPrimary = Color(0xFFFDF7F2),
    primaryContainer = Color(0xFFF0D7E2),
    onPrimaryContainer = Color(0xFF3A1328),
    secondary = Color(0xFFDC6C57),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9D1),
    onSecondaryContainer = Color(0xFF4C160D),
    tertiary = Color(0xFF6F8D7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E8DE),
    onTertiaryContainer = Color(0xFF173527),
    background = Color(0xFFF8F3EC),
    onBackground = Color(0xFF241826),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF241826),
    surfaceVariant = Color(0xFFF4E5E2),
    onSurfaceVariant = Color(0xFF655763),
    outline = Color(0xFF8B7A84),
    outlineVariant = Color(0xFFD9C7D1),
    inverseSurface = Color(0xFF392D37),
    inverseOnSurface = Color(0xFFF8EEF3),
    inversePrimary = Color(0xFFFFB1C8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color(0xFF7A3458),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC9D8),
    onPrimary = Color(0xFF43192E),
    primaryContainer = Color(0xFF5F2542),
    onPrimaryContainer = Color(0xFFFFD9E5),
    secondary = Color(0xFFFFB19B),
    onSecondary = Color(0xFF5D1F12),
    secondaryContainer = Color(0xFF823426),
    onSecondaryContainer = Color(0xFFFFD9D1),
    tertiary = Color(0xFFC3D7CB),
    onTertiary = Color(0xFF25392E),
    tertiaryContainer = Color(0xFF3B5144),
    onTertiaryContainer = Color(0xFFDDEFE4),
    background = Color(0xFF19131A),
    onBackground = Color(0xFFF3E9EF),
    surface = Color(0xFF251D27),
    onSurface = Color(0xFFF3E9EF),
    surfaceVariant = Color(0xFF413541),
    onSurfaceVariant = Color(0xFFD4C3CE),
    outline = Color(0xFFA996A2),
    outlineVariant = Color(0xFF51444F),
    inverseSurface = Color(0xFFF3E9EF),
    inverseOnSurface = Color(0xFF352934),
    inversePrimary = Color(0xFF7A3458),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFFFC9D8),
    scrim = Color(0xFF000000),
)

@Immutable
data class CycleCareSpacing(
    val xs: PaddingValues = PaddingValues(4.dp),
    val s: PaddingValues = PaddingValues(8.dp),
    val m: PaddingValues = PaddingValues(16.dp),
    val l: PaddingValues = PaddingValues(24.dp),
)

val LocalCycleCareSpacing = staticCompositionLocalOf { CycleCareSpacing() }

@Composable
fun CycleCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extended) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CycleCareTypography.all,
        shapes = CycleCareShapes.all,
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
    }
}

@Composable
fun PeriodActiveGradientColors(): List<Color> {
    val darkTheme = isSystemInDarkTheme()
    return if (darkTheme) {
        listOf(
            Color(0xFF8F2430),
            Color(0xFFB43A3F),
            Color(0xFF4C1F28),
        )
    } else {
        listOf(
            Color(0xFFB7333F),
            Color(0xFFE0635B),
            Color(0xFFFFD7CE),
        )
    }
}

@Composable
fun PeriodActiveContainerColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF3F1F27) else Color(0xFFFFECE8)

@Composable
fun PeriodActiveAccentColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFFB8AF) else Color(0xFFB7333F)

@Composable
fun GradientHeaderCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    gradientColors: List<Color>? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    stackTrailingOnCompact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        BoxWithConstraints {
            val useStackedTrailing = stackTrailingOnCompact &&
                trailing != null &&
                (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f)
            val resolvedGradientColors = gradientColors ?: listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
            )
            val containerModifier = Modifier
                .background(
                    Brush.linearGradient(
                        resolvedGradientColors,
                    ),
                )
                .padding(contentPadding)

            if (useStackedTrailing) {
                Column(
                    modifier = containerModifier,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        supportingContent?.invoke(this)
                    }
                    trailing?.invoke()
                }
            } else {
                Row(
                    modifier = containerModifier,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        supportingContent?.invoke(this)
                    }
                    trailing?.invoke()
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(
        modifier = modifier.heightIn(min = 94.dp),
        shape = RoundedCornerShape(18.dp),
        color = accent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
