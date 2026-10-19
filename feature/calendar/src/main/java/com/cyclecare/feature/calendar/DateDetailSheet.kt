@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cyclecare.core.designsystem.token.extendedColors
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.CycleCarePanel
import com.cyclecare.core.designsystem.text.resolve

@Composable
internal fun StatusPill(summary: SelectedDaySummary) {
    val accent = legendColor(summary.type)
    Surface(
        color = accent.copy(alpha = 0.25f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CycleCareSpace.space12, vertical = CycleCareSpace.space8),
            horizontalArrangement = Arrangement.spacedBy(CycleCareSpace.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = summary.type.icon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(summary.statusLabel.resolve(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun selectedDayPanelColor(type: CalendarLegendType): Color {
    val extended = extendedColors
    return when (type) {
        CalendarLegendType.Period -> extended.calendarPeriodSelected
        CalendarLegendType.Fertile -> extended.calendarFertileSelected
        CalendarLegendType.Ovulation -> extended.calendarOvulationSelected
        CalendarLegendType.TodaySelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        CalendarLegendType.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
}

@Composable
internal fun HistoryCard(
    item: CycleHistoryItem,
    onEdit: () -> Unit,
) {
    CycleCarePanel(
        modifier = Modifier.testTag(CalendarTags.history(item.id)),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CycleCareSpace.space10),
        ) {
            Text(item.dateRangeLabel.resolve(), style = MaterialTheme.typography.titleMedium)
            Text(
                item.cycleLengthLabel.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.notesPreview?.let { notes ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_entry))
            }
        }
    }
}

