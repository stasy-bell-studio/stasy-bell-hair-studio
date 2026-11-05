package com.stasybell.os

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val studioLocale = Locale.forLanguageTag("ru-RU")

data class WeeklyCashTotals(
    val label: String,
    val incomeKopecks: Long,
    val expenseKopecks: Long,
)

fun summarizeCashByWeek(
    transactions: List<CashEntry>,
    period: YearMonth,
): List<WeeklyCashTotals> {
    val daysInMonth = period.lengthOfMonth()
    val weeks = (0 until (daysInMonth + 6) / 7).map { index ->
        val firstDay = index * 7 + 1
        WeeklyCashTotals(
            label = "$firstDay–${minOf(firstDay + 6, daysInMonth)}",
            incomeKopecks = 0,
            expenseKopecks = 0,
        )
    }
    val incomes = LongArray(weeks.size)
    val expenses = LongArray(weeks.size)
    val moscow = ZoneId.of("Europe/Moscow")

    transactions.forEach { entry ->
        val day = OffsetDateTime.parse(entry.occurredAt)
            .atZoneSameInstant(moscow)
            .toLocalDate()
        if (YearMonth.from(day) != period) return@forEach
        val weekIndex = (day.dayOfMonth - 1) / 7
        val signedAmount = if (entry.correctionOfId != null) -entry.amountKopecks
            else entry.amountKopecks
        when (entry.kind) {
            "income" -> incomes[weekIndex] += signedAmount
            "expense" -> expenses[weekIndex] += signedAmount
        }
    }

    return weeks.mapIndexed { index, week ->
        week.copy(incomeKopecks = incomes[index], expenseKopecks = expenses[index])
    }
}

@Composable
fun ForestSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.background(Brush.linearGradient(
        listOf(StasyColors.Forest, StasyColors.PrimaryDeep, StasyColors.Primary)
    ))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(StasyColors.Mint.copy(alpha = .15f), Color.Transparent)),
                size.width * .65f, Offset(size.width, size.height * .7f))
            // Decorative leaves stay at the edge, away from the text and controls.
            for (i in 0..3) {
                val x = size.width - (18 + i * 15).dp.toPx()
                val y = (6 + i * 25).dp.toPx()
                val leaf = Path().apply {
                    moveTo(x, y)
                    cubicTo(x - 56.dp.toPx(), y - 8.dp.toPx(), x - 44.dp.toPx(), y + 36.dp.toPx(), x, y)
                }
                drawPath(leaf, Brush.linearGradient(listOf(StasyColors.Mint.copy(alpha = .21f),
                    StasyColors.Primary.copy(alpha = .12f))))
                drawPath(leaf, StasyColors.Mint.copy(alpha = .18f), style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun EditorialCard(modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        color = StasyColors.Surface, border = BorderStroke(1.dp, StasyColors.Border),
        shadowElevation = 2.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
fun GlassMetric(label: String, value: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .13f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .24f))) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .09f),
            Color.Transparent))).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            Text(value, color = Color.White, fontSize = if (compact && value.length > 9) 15.sp else 22.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = 27.sp)
        }
    }
}

@Composable
fun VisitSummaryRow(visit: VisitItem, client: String, service: String, onClick: (() -> Unit)? = null) {
    val indicator = when (visit.status) {
        "cancelled", "no_show" -> StasyColors.Error
        "pending_channel" -> StasyColors.Warning
        else -> StasyColors.Primary
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "${visit.localTime} · $client · $service"
    }.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(3.dp).height(38.dp).background(indicator, RoundedCornerShape(2.dp)))
        Column(Modifier.width(54.dp)) {
            Text(visit.localTime.substringAfter(" "), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(visit.localTime.take(5), fontSize = 11.sp, color = StasyColors.Muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(client, style = MaterialTheme.typography.titleMedium)
            Text(service, style = MaterialTheme.typography.bodySmall, color = StasyColors.Body)
            Text(visitStatusLabel(visit.status), fontSize = 11.sp, color = StasyColors.Muted)
        }
        if (onClick != null) Text("›", fontSize = 24.sp, color = StasyColors.Primary)
    }
}

@Composable
fun TodayOverview(today: LocalDate, visits: List<VisitItem>, clients: List<ClientItem>,
    services: List<ServiceItem>, dailyIncome: Long?, workingWindows: List<WorkingTime>?,
    onCalendar: () -> Unit) {
    val appointments = visits.filter { it.kind == "appointment" && countsAsVisit(it.status) }
    val todayVisits = appointments.filter { it.localTime.startsWith(today.format(studioDateFormat)) }
    val availableMinutes = workingWindows?.sumOf {
        java.time.Duration.between(java.time.LocalTime.parse(it.start), java.time.LocalTime.parse(it.end)).toMinutes()
    }
    val occupiedMinutes = workingWindows?.sumOf { window ->
        val windowStart = today.atTime(java.time.LocalTime.parse(window.start))
        val windowEnd = today.atTime(java.time.LocalTime.parse(window.end))
        todayVisits.sumOf { visit ->
            val start = OffsetDateTime.parse(visit.startsAt).atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime()
            val end = OffsetDateTime.parse(visit.occupiedUntil).atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime()
            java.time.Duration.between(maxOf(start, windowStart), minOf(end, windowEnd)).toMinutes().coerceAtLeast(0)
        }
    }
    val load = if (availableMinutes != null && availableMinutes > 0 && occupiedMinutes != null)
        "${(occupiedMinutes * 100 / availableMinutes).coerceIn(0, 100)}%" else "—"
    val upcoming = appointments.filter { it.status in listOf("confirmed", "pending_channel") &&
        OffsetDateTime.parse(it.startsAt).isAfter(OffsetDateTime.now()) }.sortedBy { it.startsAt }.take(5)
    ForestSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Добрый день, Настя", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", studioLocale)),
                color = StasyColors.Mint, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassMetric("Записей\nсегодня", todayVisits.size.toString(), Modifier.weight(1f))
                GlassMetric("Получено\nсегодня", dailyIncome?.let { formatCash(it) } ?: "—", Modifier.weight(1f), compact = true)
                GlassMetric("Загрузка\nдня", load, Modifier.weight(1f))
            }
        }
    }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditorialCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ближайшие визиты", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onCalendar) { Text("Все") }
            }
            if (upcoming.isEmpty()) Text("На ближайшие дни записей нет.", color = StasyColors.Muted)
            upcoming.forEachIndexed { index, visit ->
                if (index > 0) HorizontalDivider(color = StasyColors.Border)
                VisitSummaryRow(visit, clients.find { it.id == visit.clientId }?.name ?: "Без клиента",
                    services.find { it.id == visit.serviceId }?.name ?: "Услуга", onCalendar)
            }
        }
        Text("Фактические поступления вносятся вручную.", style = MaterialTheme.typography.bodySmall,
            color = StasyColors.Muted, modifier = Modifier.padding(start = 4.dp, end = 72.dp, bottom = 24.dp))
    }
}

@Composable
fun CashOverview(summary: CashSummary, transactions: List<CashEntry>, period: YearMonth) {
    val weeklyTotals = remember(transactions, period) {
        summarizeCashByWeek(transactions, period)
    }
    // Income and expenses share one scale so their column heights are comparable.
    val commonMaximum = weeklyTotals.flatMap {
        listOf(it.incomeKopecks, it.expenseKopecks)
    }.filter { it > 0 }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    ForestSurface {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassMetric(
                    "Поступления", formatCashLabel(summary.incomeKopecks),
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription = "Поступления: ${formatCashLabel(summary.incomeKopecks)}"
                    },
                )
                EditorialCard(Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "Расходы: ${formatCashLabel(summary.expenseKopecks)}"
                }) {
                    Text("Расходы", style = MaterialTheme.typography.bodySmall, color = StasyColors.Muted)
                    Text(formatCashLabel(summary.expenseKopecks), fontSize = 23.sp, lineHeight = 29.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Итог: ${formatCashLabel(summary.balanceKopecks)}"
                    },
            ) {
                Text("Итог", style = MaterialTheme.typography.bodySmall, color = StasyColors.Mint)
                Text(formatCashLabel(summary.balanceKopecks), style = MaterialTheme.typography.headlineSmall,
                    color = Color.White)
            }
        }
    }
    EditorialCard(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text("Поступления и расходы по неделям", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CashLegendItem(StasyColors.Primary, "Поступления")
            CashLegendItem(StasyColors.Muted, "Расходы")
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            weeklyTotals.forEach { week ->
                WeeklyCashPair(week, commonMaximum)
            }
        }
    }
}

@Composable
private fun CashLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = StasyColors.Body)
    }
}

@Composable
private fun WeeklyCashPair(week: WeeklyCashTotals, commonMaximum: Long) {
    Column(
        Modifier.width(184.dp).semantics(mergeDescendants = true) {
            contentDescription = "${week.label}: поступления ${formatCashLabel(week.incomeKopecks)}, " +
                "расходы ${formatCashLabel(week.expenseKopecks)}"
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatCashLabel(week.incomeKopecks), Modifier.weight(1f).heightIn(min = 34.dp),
                fontSize = 12.sp, lineHeight = 15.sp, color = StasyColors.Body, textAlign = TextAlign.Center)
            Text(formatCashLabel(week.expenseKopecks), Modifier.weight(1f).heightIn(min = 34.dp),
                fontSize = 12.sp, lineHeight = 15.sp, color = StasyColors.Body, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeeklyCashBar(week.incomeKopecks, commonMaximum, StasyColors.Primary, Modifier.weight(1f))
            WeeklyCashBar(week.expenseKopecks, commonMaximum, StasyColors.Muted, Modifier.weight(1f))
        }
        Text(week.label, Modifier.fillMaxWidth(), fontSize = 12.sp, lineHeight = 16.sp,
            color = StasyColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WeeklyCashBar(amountKopecks: Long, commonMaximum: Long, color: Color, modifier: Modifier) {
    Box(modifier.height(72.dp), contentAlignment = Alignment.BottomCenter) {
        if (amountKopecks > 0) {
            val barHeight = (amountKopecks.toDouble() / commonMaximum * 68.0)
                .coerceIn(2.0, 68.0).dp
            Box(Modifier.width(24.dp).height(barHeight)
                .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = StasyColors.Border)
    }
}
