package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var closures by remember { mutableStateOf<List<FinanceClosure>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf("") }
    var requiresRefresh by remember { mutableStateOf(false) }
    var actionInFlight by remember { mutableStateOf(false) }
    var closeDialog by remember { mutableStateOf(false) }
    var reopenDialog by remember { mutableStateOf(false) }
    var reopenReason by remember { mutableStateOf("") }
    var correctionEntry by remember { mutableStateOf<CashEntry?>(null) }
    var correctionReason by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("income") }
    var category by remember { mutableStateOf("other") }
    var amountRubles by remember { mutableStateOf("") }
    var dropdown by remember { mutableStateOf(false) }
    var showEntryForm by remember { mutableStateOf(false) }
    val choices = if (kind == "income") {
        listOf("other" to "Прочие поступления")
    } else {
        listOf("materials" to "Материалы", "rent" to "Аренда",
            "taxes" to "Налоги", "advertising" to "Реклама", "other" to "Прочее")
    }

    suspend fun refreshPeriod(requested: YearMonth) {
        val summary = api.financeSummary(token, requested.year, requested.monthValue)
        val entries = api.financeTransactions(token, requested.year, requested.monthValue)
        val history = api.financeClosures(token, requested.year, requested.monthValue)
        if (period == requested) {
            totals = summary
            transactions = entries
            closures = history
            warning = ""
            requiresRefresh = false
        }
    }

    fun refresh() {
        val requested = period
        scope.launch {
            loading = true
            try {
                refreshPeriod(requested)
            } catch (ex: Exception) {
                if (period == requested) warning = "Не удалось загрузить финансы: ${ex.message}"
            } finally {
                if (period == requested) loading = false
            }
        }
    }

    fun performFinancialAction(
        actionName: String,
        action: suspend (YearMonth) -> Unit,
    ) {
        if (loading || actionInFlight) return
        actionInFlight = true
        loading = true
        val requested = period
        scope.launch {
            warning = ""
            try {
                action(requested)
                refreshPeriod(requested)
                onChanged()
            } catch (ex: Exception) {
                if (period == requested) {
                    requiresRefresh = true
                    warning = "Не удалось подтвердить $actionName. Обновите историю перед повтором: ${ex.message}"
                }
            } finally {
                if (period == requested) loading = false
                actionInFlight = false
            }
        }
    }

    LaunchedEffect(token, period) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                period = period.minusMonths(1); totals = null; transactions = emptyList(); closures = emptyList(); warning = ""
            }, enabled = !loading && period > YearMonth.of(2000, 1),
                ) { Text("‹", modifier = Modifier.semantics { contentDescription = "Предыдущий месяц" }) }
            Text(period.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", studioLocale)),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = {
                period = period.plusMonths(1); totals = null; transactions = emptyList(); closures = emptyList(); warning = ""
            }, enabled = !loading && period < YearMonth.of(2100, 12),
                ) { Text("›", modifier = Modifier.semantics { contentDescription = "Следующий месяц" }) }
        }
        totals?.let { CashOverview(it, transactions, period) }
        StasyButton(onClick = { showEntryForm = !showEntryForm }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showEntryForm) "Скрыть форму" else "Новая операция")
        }
        Text("Фактические суммы вносятся вручную.", style = MaterialTheme.typography.bodySmall,
            color = StasyColors.Muted)
        totals?.let {
            if (it.isClosed) {
                Text("Месяц закрыт: добавление операций заблокировано.")
                TextButton(
                    onClick = { reopenReason = ""; reopenDialog = true },
                    enabled = !loading && !requiresRefresh,
                ) { Text("Открыть месяц повторно") }
            } else if (period > currentPeriod) {
                Text("Будущий месяц нельзя закрыть заранее.")
            } else {
                TextButton(
                    onClick = { closeDialog = true },
                    enabled = !loading && !requiresRefresh,
                ) { Text("Закрыть месяц") }
            }
        }
        TextButton(onClick = { refresh() }, enabled = !loading) { Text("Обновить суммы") }
        if (period != currentPeriod) Text("Добавление операций доступно только в текущем месяце.")
        if (showEntryForm) EditorialCard {
        Text("Ручной ввод", style = MaterialTheme.typography.titleMedium)
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
                if (kopecks != null) {
                    val amount = kopecks
                    val selectedKind = kind
                    val selectedCategory = category
                    performFinancialAction("операцию") {
                        api.addCash(token, selectedKind, selectedCategory, amount)
                        amountRubles = ""
                    }
                }
            },
            enabled = kopecks != null && !loading && !requiresRefresh &&
                period == currentPeriod && totals?.isClosed == false,
        ) { Text("Записать фактическую операцию") }
        }
        if (warning.isNotBlank()) {
            Text(warning, color = MaterialTheme.colorScheme.error)
        }
        Text("Завершение визита не создаёт поступление.", style = MaterialTheme.typography.bodySmall,
            color = StasyColors.Muted)
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
            val reversed = transactions.any { it.correctionOfId == entry.id }
            AppCard {
                Text(
                    "${if (entry.correctionOfId != null) "Сторно · " else ""}$label · $categoryName",
                    style = MaterialTheme.typography.titleMedium,
                )
                val signedAmount = if (entry.correctionOfId != null) -entry.amountKopecks
                    else entry.amountKopecks
                Text(formatCash(signedAmount))
                entry.note?.removePrefix("Reversal: ")?.takeIf { it.isNotBlank() }?.let {
                    Text("Причина: $it")
                }
                Text(localVisitTime(entry.occurredAt), style = MaterialTheme.typography.bodySmall)
                if (entry.correctionOfId == null) {
                    when {
                        reversed -> Text("Операция сторнирована.")
                        totals?.isClosed == true -> Text("Исправления доступны после повторного открытия месяца.")
                        else -> TextButton(
                            onClick = { correctionReason = ""; correctionEntry = entry },
                            enabled = !loading && !requiresRefresh,
                        ) { Text("Сторнировать $label · $categoryName") }
                    }
                }
            }
        }
        Text("История закрытия месяца", style = MaterialTheme.typography.titleMedium)
        if (closures.isEmpty()) Text("Месяц ещё не закрывался.")
        closures.asReversed().forEach { closure ->
            AppCard {
                Text("Закрытие · версия ${closure.version}", style = MaterialTheme.typography.titleMedium)
                Text("Поступления: ${formatCash(closure.incomeKopecks)}")
                Text("Расходы: ${formatCash(closure.expenseKopecks)}")
                Text("Денежная разница: ${formatCash(closure.balanceKopecks)}")
                if (closure.reopenedAt != null) {
                    Text("Повторно открыто · ${localVisitTime(closure.reopenedAt)}")
                    closure.reopenReason?.let { Text("Причина: $it") }
                } else Text("Статус: закрыт")
            }
        }
    }

    if (closeDialog) AlertDialog(
        onDismissRequest = { if (!loading) closeDialog = false },
        title = { Text("Закрыть месяц?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Итоги будут сохранены в истории. Новые операции и исправления будут заблокированы.")
                totals?.let {
                    Text("Поступления: ${formatCash(it.incomeKopecks)}")
                    Text("Расходы: ${formatCash(it.expenseKopecks)}")
                    Text("Денежная разница: ${formatCash(it.balanceKopecks)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                closeDialog = false
                performFinancialAction("закрытие месяца") {
                    api.closeFinanceMonth(token, it.year, it.monthValue)
                }
            }, enabled = !loading && !requiresRefresh) { Text("Подтвердить закрытие") }
        },
        dismissButton = {
            TextButton(onClick = { closeDialog = false }, enabled = !loading) { Text("Назад") }
        },
    )

    if (reopenDialog) AlertDialog(
        onDismissRequest = { if (!loading) reopenDialog = false },
        title = { Text("Повторно открыть месяц?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("После повторного открытия можно будет добавлять операции и исправления. Причина попадёт в журнал.")
                OutlinedTextField(
                    value = reopenReason,
                    onValueChange = { if (it.length <= 500) reopenReason = it },
                    label = { Text("Причина повторного открытия") },
                    supportingText = { Text("Не менее 10 символов.") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val reason = reopenReason.trim()
                reopenDialog = false
                performFinancialAction("повторное открытие") {
                    api.reopenFinanceMonth(token, it.year, it.monthValue, reason)
                }
            }, enabled = !loading && !requiresRefresh && reopenReason.trim().length >= 10) {
                Text("Подтвердить повторное открытие")
            }
        },
        dismissButton = {
            TextButton(onClick = { reopenDialog = false }, enabled = !loading) { Text("Назад") }
        },
    )

    correctionEntry?.let { entry ->
        val label = if (entry.kind == "income") "поступление" else "расход"
        AlertDialog(
            onDismissRequest = { if (!loading) correctionEntry = null },
            title = { Text("Сторнировать $label?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Будет создана обратная запись на ${formatCash(entry.amountKopecks)}. История останется доступна.")
                    OutlinedTextField(
                        value = correctionReason,
                        onValueChange = { if (it.length <= 500) correctionReason = it },
                        label = { Text("Причина корректировки") },
                        supportingText = { Text("Не менее 10 символов.") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val reason = correctionReason.trim()
                    correctionEntry = null
                    performFinancialAction("сторно операции") {
                        api.reverseCashTransaction(token, entry.id, reason)
                    }
                }, enabled = !loading && !requiresRefresh && correctionReason.trim().length >= 10) {
                    Text("Подтвердить сторно")
                }
            },
            dismissButton = {
                TextButton(onClick = { correctionEntry = null }, enabled = !loading) { Text("Назад") }
            },
        )
    }
}
