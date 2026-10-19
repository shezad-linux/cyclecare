@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.cyclecare.feature.calendar

import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cyclecare.core.designsystem.token.extendedColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun CalendarDayCell(
    day: CalendarDay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val extended = extendedColors
    val fillType = calendarDayFillType(day)
    val hasCustomFill = fillType != null

    val containerColor = when (fillType) {
        CalendarLegendType.Period -> extended.calendarPeriodFill
        CalendarLegendType.Ovulation -> extended.calendarOvulationFill
        CalendarLegendType.Fertile -> extended.calendarFertileFill
        else -> if (day.isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (day.isCurrentMonth) 0.74f else 0.4f)
        }
    }
    val borderColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }
    val dayStateText = calendarDayStateDescription(day)
        .map { state -> stringResource(state.labelRes()) }
        .joinToString(", ")

    Surface(
        modifier = modifier
            .testTag(CalendarTags.day(day.date))
            .semantics { stateDescription = dayStateText }
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (day.isSelected) 2.5.dp else if (borderColor == Color.Transparent) 0.dp else 2.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    color = when {
                        hasCustomFill -> if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> if (day.isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (day.isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                if (day.isOvulationDay) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = stringResource(R.string.ovulation),
                        modifier = Modifier.size(10.dp),
                        tint = if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (day.isToday && !day.isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = CycleCareSpace.space4)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
            }
        }
    }
}

private fun CalendarDayState.labelRes(): Int = when (this) {
    CalendarDayState.Selected -> R.string.day_state_selected
    CalendarDayState.Today -> R.string.day_state_today
    CalendarDayState.Period -> R.string.day_state_period
    CalendarDayState.Fertile -> R.string.day_state_fertile
    CalendarDayState.Ovulation -> R.string.day_state_ovulation
    CalendarDayState.CurrentMonth -> R.string.day_state_current_month
    CalendarDayState.OutsideMonth -> R.string.day_state_outside_month
}

