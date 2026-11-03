package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import java.time.LocalDate
import java.time.ZoneId

fun formatCash(kopecks: Long): String {
    val sign = if (kopecks < 0) "-" else ""
    val abs = kotlin.math.abs(kopecks)
    val rubles = abs / 100
    val kopecksRemainder = abs % 100
    return "$sign$rubles,${kopecksRemainder.toString().padStart(2, '0')} ₽"
}

@Composable
fun FinancePanel(api: ApiClient, token: String) {
    val scope = rememberCoroutineScope()
    val period = remember { LocalDate.now(ZoneId.of("Europe/Moscow")) }
    var totals by remember { mutableStateOf<CashSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf("") }
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
        scope.launch {
            loading = true
            try {
                totals = api.financeSummary(token, period.year, period.monthValue)
                warning = ""
            } catch (ex: Exception) {
                warning = "Не удалось загрузить финансы: ${ex.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(token) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Деньги · ${period.monthValue}.${period.year}",
            style = MaterialTheme.typography.titleLarge)
        Text("Только фактические поступления и расходы. Разница — не бухгалтерская прибыль.")
        totals?.let {
            Text("Поступления: ${formatCash(it.incomeKopecks)}")
            Text("Расходы: ${formatCash(it.expenseKopecks)}")
            Text("Денежная разница: ${formatCash(it.balanceKopecks)}",
                style = MaterialTheme.typography.titleMedium)
            if (it.isClosed) Text("Месяц закрыт: добавление операций заблокировано.")
        }
        Button(onClick = { refresh() }, enabled = !loading) { Text("Обновить суммы") }
        Text("Новая операция", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            kind = if (kind == "income") "expense" else "income"
            category = if (kind == "income") "materials" else "other"
        }) {
            Text(if (kind == "income") "Доход — переключить на расход"
                else "Расход — переключить на доход")
        }
        Button(onClick = { dropdown = true }) {
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
            onValueChange = { value -> if (value.all(Char::isDigit)) amountRubles = value },
            label = { Text("Сумма в целых рублях") },
            modifier = Modifier.fillMaxWidth(),
        )
        val kopecks = amountRubles.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 100 }
            ?.times(100)
        Button(
            onClick = {
                if (kopecks != null) scope.launch {
                    loading = true
                    try {
                        api.addCash(token, kind, category, kopecks)
                        amountRubles = ""
                        totals = api.financeSummary(token, period.year, period.monthValue)
                        warning = ""
                    } catch (ex: Exception) {
                        warning = "Операция не сохранена: ${ex.message}"
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = kopecks != null && !loading && totals?.isClosed == false,
        ) { Text("Записать фактическую операцию") }
        if (warning.isNotBlank()) {
            Text(warning, color = MaterialTheme.colorScheme.error)
        }
        Text("Доходы от процедур вносятся после завершения визита. Экран пока не оформляет оплату процедуры по ссылке на запись.")
    }
}
