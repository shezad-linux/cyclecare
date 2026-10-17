package com.cyclecare.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PeriodHeroMode {
    Active,
    Predicted,
}

enum class MetricPillTone {
    Neutral,
    Highlight,
    Inverse,
}

@Immutable
private data class PeriodHeroPalette(
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val coreStart: Color,
    val coreEnd: Color,
    val ring: Color,
    val accent: Color,
    val shadow: Color,
    val highlight: Color,
)

@Composable
fun MetricPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: MetricPillTone = MetricPillTone.Neutral,
) {
    val containerColor = when (tone) {
        MetricPillTone.Neutral -> MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        MetricPillTone.Highlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        MetricPillTone.Inverse -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
    }
    val borderColor = when (tone) {
        MetricPillTone.Neutral -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        MetricPillTone.Highlight -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        MetricPillTone.Inverse -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f)
    }
    val contentColor = when (tone) {
        MetricPillTone.Neutral -> MaterialTheme.colorScheme.onSurface
        MetricPillTone.Highlight -> MaterialTheme.colorScheme.primary
        MetricPillTone.Inverse -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun FormSectionCard(
    title: String,
    helperText: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.surface,
    eyebrow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CycleCarePanel(
        modifier = modifier,
        containerColor = accent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        eyebrow?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = helperText,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        content()
    }
}

@Composable
fun BottomActionRail(
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String,
    enabled: Boolean,
    isLoading: Boolean,
    onAction: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 5.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            message?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                AnimatedContent(
                    targetState = if (isLoading) "Saving" else actionLabel,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith
                                fadeOut(animationSpec = tween(220))
                    },
                    label = "ButtonContentTransition"
                ) { targetState ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Saving...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            if (targetState.contains("✓") || targetState.contains("Saved") || targetState.contains("Edit") || targetState.contains("Update")) {
                                Text(
                                    text = "✓ ",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(
                                text = targetState,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodHeroScene(
    mode: PeriodHeroMode,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    progress: Float = 0f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "period_hero")
    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (compact) 14000 else 18000,
                easing = LinearEasing,
            ),
        ),
        label = "orbit_progress",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (compact) 2400 else 3200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (compact) 5200 else 6800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo_pulse",
    )

    val palette = periodHeroPalette(mode)
    val density = LocalDensity.current
    val resolvedProgress = progress.coerceIn(0f, 1f)
    val borderWidth = with(density) { if (compact) 1.dp.toPx() else 1.5.dp.toPx() }

    Canvas(
        modifier = modifier
            .border(
                width = if (compact) 1.dp else 1.5.dp,
                color = palette.ring.copy(alpha = 0.18f),
                shape = RoundedCornerShape(if (compact) 26.dp else 34.dp),
            ),
    ) {
        val minDimension = size.minDimension
        val angle = orbitProgress * 2f * PI.toFloat()
        val driftX = (sin(angle.toDouble()) * minDimension * 0.045f).toFloat()
        val driftY = (cos(angle.toDouble()) * minDimension * 0.032f).toFloat()
        val heroCenter = center + Offset(driftX, driftY)
        val ringRadius = minDimension * if (compact) 0.34f else 0.36f
        val coreRadius = minDimension * if (compact) 0.24f else 0.28f
        val orbitDotRadius = minDimension * if (compact) 0.045f else 0.05f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.backgroundStart,
                    palette.backgroundEnd,
                ),
            ),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(minDimension * 0.18f),
        )

        drawCircle(
            color = palette.shadow.copy(alpha = 0.16f),
            radius = ringRadius * 1.06f,
            center = heroCenter + Offset(minDimension * 0.08f, minDimension * 0.11f),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.accent.copy(alpha = 0.24f),
                    Color.Transparent,
                ),
                center = heroCenter,
                radius = ringRadius * 1.9f * haloPulse,
            ),
            radius = ringRadius * 1.9f * haloPulse,
            center = heroCenter,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.coreStart,
                    palette.coreEnd.copy(alpha = 0.62f),
                    Color.Transparent,
                ),
                center = heroCenter,
                radius = coreRadius * 1.8f * pulse,
            ),
            radius = coreRadius * 1.8f * pulse,
            center = heroCenter,
        )

        drawCircle(
            color = palette.ring.copy(alpha = 0.18f),
            radius = ringRadius * 1.14f,
            center = heroCenter,
            style = Stroke(width = borderWidth),
        )

        drawCircle(
            color = palette.ring.copy(alpha = 0.36f),
            radius = ringRadius,
            center = heroCenter,
            style = Stroke(width = minDimension * if (compact) 0.018f else 0.022f),
        )

        drawArc(
            color = palette.accent.copy(alpha = 0.64f),
            startAngle = -90f,
            sweepAngle = resolvedProgress * 290f,
            useCenter = false,
            topLeft = Offset(heroCenter.x - ringRadius, heroCenter.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(
                width = minDimension * if (compact) 0.028f else 0.032f,
                cap = StrokeCap.Round,
            ),
        )

        val orbitCenter = heroCenter + Offset(
            x = (cos(angle.toDouble()) * ringRadius).toFloat(),
            y = (sin(angle.toDouble()) * ringRadius * 0.82f).toFloat(),
        )
        drawCircle(
            color = palette.accent.copy(alpha = 0.82f),
            radius = orbitDotRadius,
            center = orbitCenter,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.highlight.copy(alpha = 0.96f),
                    Color.Transparent,
                ),
                center = heroCenter - Offset(coreRadius * 0.55f, coreRadius * 0.6f),
                radius = coreRadius * 0.92f,
            ),
            radius = coreRadius * 0.92f,
            center = heroCenter - Offset(coreRadius * 0.2f, coreRadius * 0.16f),
        )

        drawCircle(
            color = palette.highlight.copy(alpha = 0.18f),
            radius = coreRadius * 0.58f,
            center = heroCenter - Offset(coreRadius * 0.24f, coreRadius * 0.22f),
        )
    }
}

@Composable
private fun periodHeroPalette(
    mode: PeriodHeroMode,
): PeriodHeroPalette = when (mode) {
    PeriodHeroMode.Active -> PeriodHeroPalette(
        backgroundStart = PeriodActiveContainerColor().copy(alpha = 0.98f),
        backgroundEnd = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        coreStart = PeriodActiveAccentColor().copy(alpha = 0.96f),
        coreEnd = MaterialTheme.colorScheme.secondary.copy(alpha = 0.84f),
        ring = PeriodActiveAccentColor().copy(alpha = 0.52f),
        accent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.96f),
        shadow = PeriodActiveAccentColor().copy(alpha = 0.42f),
        highlight = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
    )
    PeriodHeroMode.Predicted -> PeriodHeroPalette(
        backgroundStart = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        backgroundEnd = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        coreStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
        coreEnd = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.58f),
        ring = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
        shadow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    )
}
