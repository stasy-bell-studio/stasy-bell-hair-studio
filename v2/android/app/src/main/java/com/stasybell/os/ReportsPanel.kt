package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Operational report: visits are never revenue until money was entered manually. */
@Composable
fun ReportsPanel(api: ApiClient, token: String) {
    val current = remember { YearMonth.now(ZoneId.of("Europe/Moscow")) }
    var period by remember { mutableStateOf(current) }
    var report by remember { mutableStateOf<OwnerMonthlyReport?>(null) }
    var legacy by remember { mutableStateOf<LegacyMonthlyReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(token, period) {
        loading = true
        report = null
        legacy = null
        error = ""
        try {
            report = api.monthlyReport(token, period.year, period.monthValue)
            legacy = api.legacyMonthlyReport(token, period.year, period.monthValue)
        } catch (ex: Exception) {
            error = "Не удалось загрузить отчёт: ${ex.message}"
        } finally { loading = false }
    }
    val statusLabels = mapOf(
        "confirmed" to "Подтверждены", "completed" to "Выполнены",
        "cancelled" to "Отменены", "no_show" to "Неявка",
        "pending_channel" to "Ждут подтверждения", "expired" to "Истекли",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Отчёты", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { period = period.minusMonths(1) },
                enabled = period > YearMonth.of(2000, 1)) { Text("‹") }
            Text(period.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))),
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { period = period.plusMonths(1) },
                enabled = period < YearMonth.of(2100, 12)) { Text("›") }
        }
        if (loading) Text("Загрузка…")
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        legacy?.let { archive ->
            if (archive.archivedVisits > 0) {
                AppCard {
                    Text("История из Excel · ${archive.archivedVisits} визитов",
                        style = MaterialTheme.typography.titleMedium)
                    archive.historicalAmountKopecks?.let {
                        Text("Суммы в архивных строках: ${formatCash(it)}")
                    }
                    archive.recordedRevenueKopecks?.let {
                        Text("Сводка старой таблицы: ${formatCash(it)}")
                    }
                    archive.recordedConsumablesKopecks?.let {
                        Text("Расходники по старой таблице: ${formatCash(it)}")
                    }
                    Text("Архивные значения не включены в текущие поступления и расходы.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        report?.let { r ->
            AppCard {
                Text("Фактические деньги", style = MaterialTheme.typography.titleMedium)
                Text("Поступления: ${formatCash(r.incomeKopecks)}")
                Text("Расходы: ${formatCash(r.expenseKopecks)}")
                Text("Итог: ${formatCash(r.balanceKopecks)}", fontWeight = FontWeight.Bold)
                Text(if (r.isClosed) "Месяц закрыт" else "Месяц открыт",
                    style = MaterialTheme.typography.bodySmall)
            }
            AppCard {
                Text("Визиты · ${r.visitsTotal}", style = MaterialTheme.typography.titleMedium)
                listOf("completed", "confirmed", "cancelled", "no_show",
                    "pending_channel", "expired").forEach { status ->
                    val number = r.visitsByStatus[status] ?: 0
                    if (number > 0) Text("${statusLabels[status]}: $number")
                }
                if (r.visitsTotal == 0) Text("Визитов в этом месяце нет.")
                if (r.completedMinutes > 0) Text("Отработано по завершённым визитам: ${r.completedMinutes} мин")
            }
            if (r.serviceBreakdown.isNotEmpty()) {
                AppCard {
                    Text("Выполненные услуги", style = MaterialTheme.typography.titleMedium)
                    r.serviceBreakdown.forEach { row ->
                        Text("${row.name}${row.variant?.let { " · $it" } ?: ""}: ${row.completed}")
                    }
                }
            }
        }
    }
}
