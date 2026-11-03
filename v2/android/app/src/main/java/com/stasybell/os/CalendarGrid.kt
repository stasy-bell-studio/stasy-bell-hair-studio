package com.stasybell.os

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun calendarWeekDates(anchor: LocalDate): List<LocalDate> {
    val monday = anchor.minusDays((anchor.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0L..6L).map(monday::plusDays)
}

fun calendarMonthDates(anchor: LocalDate): List<LocalDate> {
    val first = anchor.withDayOfMonth(1)
    val monday = first.minusDays((first.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return (0L..41L).map(monday::plusDays)
}

@Composable
fun CalendarDateGrid(
    dates: List<LocalDate>,
    anchor: LocalDate,
    displayMonth: LocalDate,
    visitCounts: Map<LocalDate, Int>,
    showWeekdaysInCells: Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val scroll = rememberScrollState()
    val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    val weekLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    if (!showWeekdaysInCells) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            weekLabels.forEach { label ->
                Text(label, modifier = Modifier.width(48.dp).padding(vertical = 8.dp),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                    color = StasyColors.Muted)
            }
        }
    }

    dates.chunked(7).forEach { week ->
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            week.forEach { date ->
                val isInMonth = date.month == displayMonth.month && date.year == displayMonth.year
                val count = visitCounts[date] ?: 0
                val isSelected = date == anchor
                Card(
                    onClick = { onSelect(date) },
                    modifier = Modifier.width(48.dp).heightIn(min = if (showWeekdaysInCells) 72.dp else 56.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Выбрать ${date.format(dateFormat)}, записей: $count"
                            this.selected = isSelected
                        },
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, if (isSelected) StasyColors.Primary else StasyColors.Border),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) StasyColors.MintSoft else StasyColors.Surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (showWeekdaysInCells) {
                            Text(weekLabels[date.dayOfWeek.value - 1],
                                style = MaterialTheme.typography.bodySmall, color = StasyColors.Muted,
                                textAlign = TextAlign.Center)
                        }
                        Text(date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (showWeekdaysInCells || isInMonth) StasyColors.Ink else StasyColors.Muted,
                            textAlign = TextAlign.Center)
                        if (showWeekdaysInCells || count > 0) {
                            Text(count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (count > 0) StasyColors.Primary else StasyColors.Muted,
                                textAlign = TextAlign.Center)
                        } else {
                            Spacer(Modifier.heightIn(min = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
