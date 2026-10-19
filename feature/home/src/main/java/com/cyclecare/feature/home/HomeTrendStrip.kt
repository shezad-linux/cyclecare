@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.home

import com.cyclecare.core.designsystem.token.CycleCareSpace
import com.cyclecare.core.designsystem.text.resolve
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeMiniTrendStrip(
    points: List<HomeTrendPoint>,
) {
    val maxValue = points.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
        verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
    ) {
        points.takeLast(6).forEach { point ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space6),
                modifier = Modifier.width(42.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height((28 + (point.value * 72 / maxValue)).dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CycleCareSpace.space4)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
                    )
                }
                Text(
                    point.label.resolve(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

