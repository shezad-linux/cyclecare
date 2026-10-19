@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cyclecare.feature.logging

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.FormSectionCard

@Composable
internal fun SelectionSectionCard(
    title: String,
    helperText: String,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    FormSectionCard(
        title = title,
        helperText = helperText,
        accent = accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space12)) {
            content()
        }
    }
}

@Composable
internal fun NotTrackedChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionChip(
        text = stringResource(R.string.not_tracked),
        selected = selected,
        onClick = onClick,
        modifier = modifier.testTag(LoggingTags.notTrackedChip()),
    )
}

@Composable
internal fun SelectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.01f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "ChipScale"
    )

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onClick,
        selected = selected,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(99.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (selected) 0.dp else 1.dp,
            color = borderColor
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = CycleCareSpace.space14, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

