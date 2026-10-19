@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.cyclecare.core.designsystem.token.extendedColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.text.resolve

@Composable
internal fun LegendChip(item: CalendarLegendItem) {
    val accent = legendColor(item.type)
    Surface(
        color = accent.copy(alpha = 0.28f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.75.dp, accent.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CycleCareSpace.space10, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent.copy(alpha = 0.9f), CircleShape),
            )
            Text(
                item.label.resolve(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun legendColor(type: CalendarLegendType): Color {
    val extended = extendedColors
    return when (type) {
        CalendarLegendType.Period -> extended.calendarPeriodSwatch
        CalendarLegendType.Fertile -> extended.calendarFertileSwatch
        CalendarLegendType.Ovulation -> extended.calendarOvulationSwatch
        CalendarLegendType.TodaySelected -> MaterialTheme.colorScheme.primary
        CalendarLegendType.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

internal fun CalendarLegendType.icon(): ImageVector = when (this) {
    CalendarLegendType.Period -> Icons.Rounded.WaterDrop
    CalendarLegendType.Fertile -> Icons.Rounded.EventRepeat
    CalendarLegendType.Ovulation -> Icons.Rounded.Star
    CalendarLegendType.TodaySelected -> Icons.Rounded.EventRepeat
    CalendarLegendType.Neutral -> Icons.Rounded.Star
}

