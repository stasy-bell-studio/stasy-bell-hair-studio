package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

fun visitStatusLabel(status: String): String = when (status) {
    "confirmed" -> "Подтверждена"
    "completed" -> "Выполнена"
    "cancelled" -> "Отменена"
    "no_show" -> "Не пришёл"
    "pending_channel" -> "Ожидает подтверждения"
    "expired" -> "Срок подтверждения истёк"
    "blocked" -> "Время заблокировано"
    else -> status
}

@Composable
fun CalendarPanel(
    api: ApiClient, token: String, visits: List<VisitItem>,
    clients: List<ClientItem>, services: List<ServiceItem>, onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<VisitItem?>(null) }
    var pendingAction by remember { mutableStateOf("") }
    var moveDay by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).format(studioDateFormat)) }
    var moveTime by remember { mutableStateOf("10:00") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var rangeMode by remember { mutableStateOf("Ближайшие") }
    var anchor by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow"))) }
    var rangeVisits by remember { mutableStateOf(visits) }
    var rangeLoading by remember { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf("") }
    var windows by remember { mutableStateOf<List<WorkingTime>?>(null) }
    var windowsError by remember { mutableStateOf("") }
    var dateDialog by remember { mutableStateOf(false) }
    var targetDate by remember { mutableStateOf(anchor.format(studioDateFormat)) }

    val from = when (rangeMode) {
        "День" -> anchor
        "Неделя" -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "Месяц" -> anchor.withDayOfMonth(1)
        else -> LocalDate.now(ZoneId.of("Europe/Moscow"))
    }
    val to = when (rangeMode) {
        "День" -> from
        "Неделя" -> from.plusDays(6)
        "Месяц" -> from.with(TemporalAdjusters.lastDayOfMonth())
        else -> from.plusDays(7)
    }
    LaunchedEffect(token, rangeMode, from, to, visits) {
        windows = null
        windowsError = ""
        if (rangeMode == "Ближайшие") {
            rangeVisits = visits
            rangeError = ""
        } else {
            rangeLoading = true
            rangeVisits = emptyList()
            try {
                rangeVisits = api.visits(token, from, to)
                rangeError = ""
                if (rangeMode == "День") {
                    try { windows = api.schedule(token, from.toString()) }
                    catch (ex: CancellationException) { throw ex }
                    catch (ex: Exception) { windowsError = "Не удалось загрузить рабочие окна: ${ex.message}" }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                rangeError = "Не удалось загрузить календарь: ${ex.message}"
            } finally { rangeLoading = false }
        }
    }

    fun request(visit: VisitItem, action: String) {
        selected = visit
        pendingAction = action
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Календарь", style = MaterialTheme.typography.titleLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("День", "Неделя", "Месяц").forEach { mode ->
                FilterChip(selected = rangeMode == mode, onClick = { rangeMode = mode },
                    label = { Text(mode) }, modifier = Modifier.weight(1f))
            }
        }
        if (rangeMode != "Ближайшие") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    anchor = when (rangeMode) {
                        "День" -> anchor.minusDays(1)
                        "Неделя" -> anchor.minusWeeks(1)
                        else -> anchor.minusMonths(1)
                    }
                }) { Text("Назад") }
                TextButton(onClick = { anchor = LocalDate.now(ZoneId.of("Europe/Moscow")) }) { Text("Сегодня") }
                TextButton(onClick = {
                    anchor = when (rangeMode) {
                        "День" -> anchor.plusDays(1)
                        "Неделя" -> anchor.plusWeeks(1)
                        else -> anchor.plusMonths(1)
                    }
                }) { Text("Вперёд") }
            }
        }
        val display = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        Text(if (from == to) from.format(display) else "${from.format(display)} — ${to.format(display)}",
            style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { targetDate = anchor.format(studioDateFormat); dateDialog = true }) {
            Text("Перейти к дате")
        }
        if (!rangeLoading && rangeError.isBlank()) {
            val count = rangeVisits.count { it.kind == "appointment" && it.status !in listOf("cancelled", "no_show") }
            Text("Записей за период: $count", style = MaterialTheme.typography.bodySmall)
        }
        if (rangeLoading) Text("Загрузка календаря…")
        if (rangeError.isNotBlank()) Text(rangeError, color = MaterialTheme.colorScheme.error)
        if (windowsError.isNotBlank()) Text(windowsError, color = MaterialTheme.colorScheme.error)
        windows?.let { actualWindows ->
            Text(if (actualWindows.isEmpty()) "Открытых рабочих окон нет." else
                "Рабочие окна: " + actualWindows.joinToString(", ") { "${it.start.take(5)}–${it.end.take(5)}" },
                style = MaterialTheme.typography.bodySmall)
            if (actualWindows.isNotEmpty()) {
                Text("Распорядок дня", style = MaterialTheme.typography.titleMedium)
                Text("Занятые интервалы включают буфер. Интервал без записи не гарантирует свободный слот для конкретной услуги.",
                    style = MaterialTheme.typography.bodySmall)
                dayTimeline(from, actualWindows, rangeVisits).forEach { slice ->
                    val interval = "${slice.start}–${slice.end}"
                    val label = slice.visit?.let { visit ->
                        val client = clients.find { it.id == visit.clientId }?.name ?: "Блок времени"
                        "$client · ${visitStatusLabel(visit.status)}"
                    } ?: "Без записи"
                    AppCard { Text("$interval · $label") }
                }
            }
        }
        if (!rangeLoading && rangeError.isBlank() && rangeVisits.isEmpty()) Text("На выбранные даты записей нет.")
        if (!rangeLoading && rangeError.isBlank() && rangeMode in listOf("Неделя", "Месяц")) {
            val dates = (0..java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt())
                .map { from.plusDays(it.toLong()) }
            val groups = if (rangeMode == "Неделя") dates.map { listOf(it) }
                else dates.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.values.toList()
            groups.forEach { group ->
                val dayLabels = group.map { it.format(display) }.toSet()
                val count = rangeVisits.count {
                    it.kind == "appointment" && it.status !in listOf("cancelled", "no_show") &&
                        it.localTime.substringBefore(" ") in dayLabels
                }
                val label = if (group.size == 1) group.first().format(display)
                    else "${group.first().format(display)} — ${group.last().format(display)}"
                TextButton(onClick = {
                    anchor = group.first()
                    rangeMode = if (rangeMode == "Месяц") "Неделя" else "День"
                }, modifier = Modifier.fillMaxWidth()) { Text("$label · записей: $count") }
            }
        }
        val detailedVisits = if (rangeMode in listOf("Неделя", "Месяц")) emptyList() else rangeVisits
        detailedVisits.forEach { visit ->
            val client = clients.find { it.id == visit.clientId }?.name ?: "Без клиента"
            val service = services.find { it.id == visit.serviceId }?.name ?: "Блок времени"
            AppCard {
                Text("${visit.localTime} · $client · $service", style = MaterialTheme.typography.titleMedium)
                val status = visitStatusLabel(visit.status)
                val source = when (visit.source) {
                    "phone" -> "вручную"
                    else -> visit.source
                }
                Text("$status · $source", style = MaterialTheme.typography.bodySmall)
                Text("Окончание: ${visit.localEnd}", style = MaterialTheme.typography.bodySmall)
                visit.durationMinutes?.let { Text("Длительность: $it мин", style = MaterialTheme.typography.bodySmall) }
                visit.quotedPriceKopecks?.let {
                    Text("Ожидаемая стоимость записи: ${formatCash(it)}", style = MaterialTheme.typography.bodySmall)
                }
                if (visit.kind == "appointment" && visit.status == "confirmed") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { request(visit, "move") }, modifier = Modifier.weight(1f)) { Text("Перенести") }
                        TextButton(onClick = { request(visit, "complete") }, modifier = Modifier.weight(1f)) { Text("Услуга выполнена") }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { request(visit, "no_show") }, modifier = Modifier.weight(1f)) { Text("Не пришёл") }
                        TextButton(onClick = { request(visit, "cancel") }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Отменить") }
                    }
                }
                if (visit.kind == "appointment" && visit.status in
                        listOf("confirmed", "completed", "no_show", "cancelled")) {
                    TextButton(onClick = { request(visit, "delete") },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить с историей") }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }

    if (dateDialog) {
        AlertDialog(
            onDismissRequest = { dateDialog = false },
            title = { Text("Перейти к дате") },
            text = {
                OutlinedTextField(value = targetDate, onValueChange = { targetDate = it },
                    label = { Text("Дата, ДД.ММ.ГГГГ") },
                    isError = targetDate.isNotEmpty() && parseStudioDate(targetDate) == null,
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = parseStudioDate(targetDate) != null, onClick = {
                    anchor = requireNotNull(parseStudioDate(targetDate))
                    rangeMode = "День"
                    dateDialog = false
                }) { Text("Открыть день") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Назад") } },
        )
    }

    val visit = selected
    if (visit != null && pendingAction.isNotBlank()) {
        val clientName = clients.find { it.id == visit.clientId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { if (!busy) { selected = null; pendingAction = "" } },
            title = { Text(when (pendingAction) {
                "delete" -> "Подтвердить мягкое удаление"
                "move" -> "Перенести визит"
                "complete" -> "Завершить визит"
                "no_show" -> "Зафиксировать неявку"
                else -> "Подтвердить отмену"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${visit.localTime} · $clientName")
                    if (pendingAction == "complete") {
                        Text("Поступление денег записывается отдельно вручную.")
                    }
                    if (pendingAction == "move") {
                        OutlinedTextField(
                            value = moveDay, onValueChange = { moveDay = it },
                            label = { Text("Новая дата, ДД.ММ.ГГГГ") },
                            isError = moveDay.isNotEmpty() && parseStudioDate(moveDay) == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = moveTime, onValueChange = { moveTime = it },
                            label = { Text("Новое время, ЧЧ:ММ") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (pendingAction == "delete") {
                        Text("Исходная запись остаётся в истории. Для завершённой услуги требуется повторное осознанное подтверждение.")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (pendingAction != "move" || parseStudioDate(moveDay) != null), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            when (pendingAction) {
                                "move" -> api.moveVisit(token, visit.id, requireNotNull(parseStudioDate(moveDay)).toString(), moveTime)
                                "complete" -> api.updateVisitStatus(token, visit.id, "completed")
                                "no_show" -> api.updateVisitStatus(token, visit.id, "no_show")
                                "cancel" -> api.cancelVisit(token, visit.id)
                                "delete" -> api.deleteVisit(token, visit, clientName)
                            }
                            error = ""
                            onChanged()
                            selected = null
                            pendingAction = ""
                        } catch (ex: Exception) {
                            error = "Операция не выполнена: ${ex.message}"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null; pendingAction = "" }) { Text("Назад") }
            },
        )
    }
}
