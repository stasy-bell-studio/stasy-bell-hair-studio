package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

fun formatCash(kopecks: Long): String {
    val sign = if (kopecks < 0) "-" else ""
    val abs = kotlin.math.abs(kopecks)
    val rubles = abs / 100
    val kopecksRemainder = abs % 100
    return "$sign$rubles,${kopecksRemainder.toString().padStart(2, '0')} ₽"
}

fun parseCash(raw: String): Long? = try {
    BigDecimal(raw.replace(",", ".")).movePointRight(2).longValueExact().takeIf { it > 0 }
} catch (_: ArithmeticException) { null } catch (_: NumberFormatException) { null }

@Composable
fun FinancePanel(api: ApiClient, token: String, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentPeriod = YearMonth.from(LocalDate.now(ZoneId.of("Europe/Moscow")))
    var period by remember { mutableStateOf(currentPeriod) }
    var totals by remember { mutableStateOf<CashSummary?>(null) }
    var transactions by remember { mutableStateOf<List<CashEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf("") }
    var requiresRefresh by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("income") }
    var category by remember { mutableStateOf("other") }
    var amountRubles by remember { mutableStateOf("") }
    var dropdown by remember { mutableStateOf(false) }
    val choices = if (kind == "income") {
        listOf("other" to "Прочие поступления")
    } else {
        listOf("materials" to "Материалы", "rent" to "Аренда",
            "taxes" to "Налоги", "advertising" to "Реклама", "other" to "Прочее")
    }

    fun refresh() {
        val requested = period
        scope.launch {
            loading = true
            try {
                val summary = api.financeSummary(token, requested.year, requested.monthValue)
                val entries = api.financeTransactions(token, requested.year, requested.monthValue)
                if (period == requested) {
                    totals = summary
                    transactions = entries
                    warning = ""
                    if (requested == currentPeriod) requiresRefresh = false
                }
            } catch (ex: Exception) {
                if (period == requested) warning = "Не удалось загрузить финансы: ${ex.message}"
            } finally {
                if (period == requested) loading = false
            }
        }
    }

    LaunchedEffect(token, period) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Финансы · ${period.monthValue.toString().padStart(2, '0')}.${period.year}",
            style = MaterialTheme.typography.titleLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                period = period.minusMonths(1); totals = null; transactions = emptyList(); warning = ""
            }, enabled = !loading && period > YearMonth.of(2000, 1),
                modifier = Modifier.weight(1f)) { Text("Предыдущий месяц") }
            TextButton(onClick = {
                period = period.plusMonths(1); totals = null; transactions = emptyList(); warning = ""
            }, enabled = !loading && period < YearMonth.of(2100, 12),
                modifier = Modifier.weight(1f)) { Text("Следующий месяц") }
        }
        Text("Только фактические поступления и расходы. Разница — не бухгалтерская прибыль.")
        totals?.let {
            AppCard {
                Text("Поступления: ${formatCash(it.incomeKopecks)}", style = MaterialTheme.typography.titleMedium)
                Text("Расходы: ${formatCash(it.expenseKopecks)}")
                Text("Денежная разница: ${formatCash(it.balanceKopecks)}",
                    style = MaterialTheme.typography.titleMedium)
            }
            if (it.isClosed) Text("Месяц закрыт: добавление операций заблокировано.")
        }
        StasyButton(onClick = { refresh() }, enabled = !loading) { Text("Обновить суммы") }
        Text("Новая операция", style = MaterialTheme.typography.titleMedium)
        if (period != currentPeriod) Text("Добавление операций доступно только в текущем месяце.")
        StasyButton(onClick = {
            kind = if (kind == "income") "expense" else "income"
            category = if (kind == "income") "materials" else "other"
        }) {
            Text(if (kind == "income") "Доход — переключить на расход"
                else "Расход — переключить на доход")
        }
        StasyButton(onClick = { dropdown = true }) {
            Text(choices.firstOrNull { it.first == category }?.second ?: "Выбрать категорию")
        }
        DropdownMenu(expanded = dropdown, onDismissRequest = { dropdown = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) },
                    onClick = { category = id; dropdown = false })
            }
        }
        OutlinedTextField(
            value = amountRubles,
            onValueChange = { value -> if (value.all { it.isDigit() || it == ',' || it == '.' }) amountRubles = value },
            label = { Text("Сумма, ₽") },
            modifier = Modifier.fillMaxWidth(),
        )
        val kopecks = parseCash(amountRubles)
        StasyButton(
            onClick = {
                if (kopecks != null) scope.launch {
                    loading = true
                    try {
                        api.addCash(token, kind, category, kopecks)
                        amountRubles = ""
                        onChanged()
                        try {
                            totals = api.financeSummary(token, period.year, period.monthValue)
                            transactions = api.financeTransactions(token, period.year, period.monthValue)
                            warning = ""
                        } catch (ex: Exception) {
                            warning = "Операция записана, но не удалось обновить данные: ${ex.message}"
                        }
                    } catch (ex: Exception) {
                        requiresRefresh = true
                        warning = "Не удалось подтвердить операцию. Проверьте историю перед повтором: ${ex.message}"
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = kopecks != null && !loading && !requiresRefresh &&
                period == currentPeriod && totals?.isClosed == false,
        ) { Text("Записать фактическую операцию") }
        if (warning.isNotBlank()) {
            Text(warning, color = MaterialTheme.colorScheme.error)
        }
        Text("Фактически полученную сумму мастер записывает вручную. Завершение визита не создаёт поступление.")
        Text("История операций", style = MaterialTheme.typography.titleMedium)
        if (!loading && warning.isBlank() && totals != null && transactions.isEmpty()) {
            Text("В этом месяце операций пока нет.")
        }
        transactions.asReversed().forEach { entry ->
            val label = if (entry.kind == "income") "Поступление" else "Расход"
            val categoryName = when (entry.category) {
                "procedure" -> "Услуга"
                "materials" -> "Материалы"
                "rent" -> "Аренда"
                "taxes" -> "Налоги"
                "advertising" -> "Реклама"
                "other" -> "Прочее"
                else -> entry.category
            }
            AppCard {
                Text("$label · $categoryName", style = MaterialTheme.typography.titleMedium)
                Text("${if (entry.correction) "Корректировка · " else ""}${formatCash(entry.amountKopecks)}")
                Text(localVisitTime(entry.occurredAt), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
