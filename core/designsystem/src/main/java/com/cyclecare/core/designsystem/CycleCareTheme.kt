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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import com.cyclecare.core.designsystem.token.CycleCareColor
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
import com.cyclecare.core.designsystem.text.UiText
import com.cyclecare.core.designsystem.text.resolve
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
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
    outline = CycleCareColor.LightOutline,
    outlineVariant = CycleCareColor.LightOutlineVariant,
    inverseSurface = CycleCareColor.LightInverseSurface,
    inverseOnSurface = CycleCareColor.LightInverseOnSurface,
    inversePrimary = CycleCareColor.LightInversePrimary,
    error = CycleCareColor.LightError,
    onError = CycleCareColor.LightOnError,
    errorContainer = CycleCareColor.LightErrorContainer,
    onErrorContainer = CycleCareColor.LightOnErrorContainer,
    surfaceTint = CycleCareColor.LightPrimary,
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
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
    outline = CycleCareColor.DarkOutline,
    outlineVariant = CycleCareColor.DarkOutlineVariant,
    inverseSurface = CycleCareColor.DarkInverseSurface,
    inverseOnSurface = CycleCareColor.DarkInverseOnSurface,
    inversePrimary = CycleCareColor.DarkInversePrimary,
    error = CycleCareColor.DarkError,
    onError = CycleCareColor.DarkOnError,
    errorContainer = CycleCareColor.DarkErrorContainer,
    onErrorContainer = CycleCareColor.DarkOnErrorContainer,
    surfaceTint = CycleCareColor.DarkPrimary,
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
            CycleCareColor.DarkPeriodActiveStart,
            CycleCareColor.DarkPeriodActiveEnd,
            CycleCareColor.DarkPeriodActiveContainer,
        )
    } else {
        listOf(
            CycleCareColor.LightPeriodActiveStart,
            CycleCareColor.LightPeriodActiveEnd,
            CycleCareColor.LightPeriodActiveContainer,
        )
    }
}

@Composable
fun PeriodActiveContainerColor(): Color =
    if (isSystemInDarkTheme()) CycleCareColor.DarkPeriodActiveContainer else CycleCareColor.LightPeriodActiveContainer

/**
 * Fill colour for chart bars.
 *
 * A field of bars is a large surface, so it uses the softened teal rather
 * than the saturated primary, which would otherwise dominate the screen.
 * The saturated teal stays on actions, selection and navigation.
 */
@Composable
fun ChartFillColor(): Color =
    if (isSystemInDarkTheme()) CycleCareColor.DarkChartFill else CycleCareColor.LightChartFill

@Composable
fun PeriodActiveAccentColor(): Color =
    if (isSystemInDarkTheme()) CycleCareColor.DarkPeriodActiveAccent else CycleCareColor.LightPeriodActiveAccent

val HeaderOnGradientColor: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary

@Composable
fun GradientHeaderCard(
    title: String,
    subtitle: String,
    titleStyle: TextStyle = MaterialTheme.typography.headlineSmall,
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
            val isDark = isSystemInDarkTheme()
            // A large surface uses the softened hero gradient rather than
            // the saturated primary, so the card does not dominate the
            // screen. The saturated teal stays on actions, selection and
            // navigation.
            val resolvedGradientColors = gradientColors ?: if (isDark) {
                listOf(
                    CycleCareColor.DarkHeroGradientStart,
                    CycleCareColor.DarkHeroGradientEnd,
                )
            } else {
                listOf(
                    CycleCareColor.LightHeroGradientStart,
                    CycleCareColor.LightHeroGradientEnd,
                )
            }
            val contentColor = HeaderOnGradientColor
            val containerModifier = Modifier
                .background(
                    Brush.linearGradient(
                        resolvedGradientColors,
                    ),
                )
                .padding(contentPadding)

            CompositionLocalProvider(LocalContentColor provides contentColor) {
                if (useStackedTrailing) {
                    Column(
                        modifier = containerModifier,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = title,
                                style = titleStyle,
                                color = contentColor,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = if (isDark) 0.82f else 0.88f),
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
                                style = titleStyle,
                                color = contentColor,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = if (isDark) 0.82f else 0.88f),
                            )
                            supportingContent?.invoke(this)
                        }
                        trailing?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * The surfaces a metric card is painted on.
 *
 * Sibling metric cards share one neutral surface. Only a metric that is
 * genuinely about the period keeps the warm period tint, because that is
 * the one distinction a reader can act on.
 *
 * The previous mix of mint, blush, sage-grey and near-white had no rule
 * behind it: three of the four were arbitrary. The neutral is also the
 * lightest of them, so it is the safer one for the label text, which is
 * onSurfaceVariant in every case.
 */
data class MetricCardAccents(
    val neutral: Color,
    val period: Color,
)

fun metricCardAccents(scheme: ColorScheme): MetricCardAccents = MetricCardAccents(
    neutral = scheme.surfaceVariant.copy(alpha = 0.82f),
    period = scheme.secondaryContainer.copy(alpha = 0.86f),
)

@Composable
fun MetricCard(
    /**
     * The card's label.
     *
     * A [UiText] rather than a [String] so a hardcoded English label
     * cannot be passed in by accident. Every metric card in the app is
     * labelled with a resource, and this is what keeps it that way.
     */
    label: UiText,
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
                text = label.resolve(),
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
