package com.cyclecare.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import com.cyclecare.core.designsystem.token.CycleCareShapes
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cyclecare.core.designsystem.LocalIsDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashboardSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    accent: Color = MaterialTheme.colorScheme.surface,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    CycleCarePanel(
        modifier = modifier,
        containerColor = accent,
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
        subtitle?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content?.invoke(this)
    }
}

@Composable
fun CycleCarePanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun DashboardPill(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val isDark = LocalIsDarkTheme.current
    val isHeaderContext = LocalContentColor.current != MaterialTheme.colorScheme.onSurface
    val containerColor = when {
        isHeaderContext -> if (isDark) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        }
        emphasized && isDark -> MaterialTheme.colorScheme.primaryContainer
        emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isDark -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isHeaderContext && emphasized -> MaterialTheme.colorScheme.primary
        isHeaderContext -> MaterialTheme.colorScheme.onSurface
        emphasized && isDark -> MaterialTheme.colorScheme.onPrimaryContainer
        emphasized -> MaterialTheme.colorScheme.primary
        isDark -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = when {
        isHeaderContext && emphasized -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f))
        isHeaderContext -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
        emphasized && isDark -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        emphasized -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        isDark -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = CycleCareShapes.pill,
        border = borderStroke,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = CycleCareSpace.space12, vertical = CycleCareSpace.space6),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun DashboardKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
