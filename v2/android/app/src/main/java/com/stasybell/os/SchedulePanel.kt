package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import java.time.LocalDate
import java.time.ZoneId

private val clockPattern = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

fun parseWindow(start: String, end: String): WorkingTime? =
    if (clockPattern.matches(start) && clockPattern.matches(end) && start < end)
        WorkingTime(start, end) else null

@Composable
fun SchedulePanel(api: ApiClient, token: String, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).format(studioDateFormat)) }
    var startOne by remember { mutableStateOf("09:00") }
    var endOne by remember { mutableStateOf("13:00") }
    var startTwo by remember { mutableStateOf("14:00") }
    var endTwo by remember { mutableStateOf("19:00") }
    var includeSecond by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmHoliday by remember { mutableStateOf(false) }
    var blockStart by remember { mutableStateOf("12:00") }
    var blockEnd by remember { mutableStateOf("13:00") }
    var requestNumber by remember { mutableStateOf(0) }
    var loadedDay by remember { mutableStateOf<String?>(null) }
    val dayIso = parseStudioDate(day)?.toString()

    fun load() {
        val validDay = dayIso ?: return
        requestNumber += 1
        val request = requestNumber
        loadedDay = null
        scope.launch {
            busy = true
            try {
                val windows = api.schedule(token, validDay)
                if (request != requestNumber) return@launch
                startOne = windows.firstOrNull()?.start?.take(5) ?: "09:00"
                endOne = windows.firstOrNull()?.end?.take(5) ?: "13:00"
                includeSecond = windows.size > 1
                startTwo = windows.getOrNull(1)?.start?.take(5) ?: "14:00"
                endTwo = windows.getOrNull(1)?.end?.take(5) ?: "19:00"
                error = if (windows.isEmpty()) "В этот день нет открытых окон." else ""
                loadedDay = validDay
            } catch (ex: Exception) {
                if (request == requestNumber) error = "Не удалось загрузить график: ${ex.message}"
            } finally { if (request == requestNumber) busy = false }
        }
    }
    LaunchedEffect(token, dayIso) { if (dayIso != null) load() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Рабочий график", style = MaterialTheme.typography.titleLarge)
        Text("Открытые часы. Разрыв между двумя окнами — перерыв. Записи не могут выйти за эти пределы.")
        OutlinedTextField(value = day, onValueChange = {
            day = it; requestNumber += 1; loadedDay = null
        },
            label = { Text("Дата, ДД.ММ.ГГГГ") }, modifier = Modifier.fillMaxWidth(),
            isError = day.isNotEmpty() && dayIso == null, enabled = !busy)
        Button(onClick = { load() }, enabled = !busy && dayIso != null) { Text("Обновить день") }
        OutlinedTextField(value = startOne, onValueChange = { startOne = it },
            label = { Text("Начало первого окна") })
        OutlinedTextField(value = endOne, onValueChange = { endOne = it },
            label = { Text("Конец первого окна") })
        Button(onClick = { includeSecond = !includeSecond }) {
            Text(if (includeSecond) "Убрать второе окно" else "Добавить второе окно")
        }
        if (includeSecond) {
            OutlinedTextField(value = startTwo, onValueChange = { startTwo = it },
                label = { Text("Начало второго окна") })
            OutlinedTextField(value = endTwo, onValueChange = { endTwo = it },
                label = { Text("Конец второго окна") })
        }
        Button(enabled = !busy && dayIso != null && loadedDay == dayIso, onClick = {
            val first = parseWindow(startOne, endOne)
            val second = if (includeSecond) parseWindow(startTwo, endTwo) else null
            if (first == null || (includeSecond && (second == null || first.end > second.start))) {
                error = "Окна должны иметь формат ЧЧ:ММ, не пересекаться и идти по порядку."
            } else scope.launch {
                busy = true
                try {
                    api.setSchedule(token, requireNotNull(dayIso), listOfNotNull(first, second))
                    error = "График сохранён."
                    onChanged()
                } catch (ex: Exception) {
                    error = "Не удалось сохранить график: ${ex.message}"
                } finally { busy = false }
            }
        }) { Text("Сохранить рабочие окна") }
        Button(enabled = !busy && dayIso != null && loadedDay == dayIso, onClick = { confirmHoliday = true }) { Text("Закрыть день (выходной)") }
        Text("Отдельно заблокировать время", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = blockStart, onValueChange = { blockStart = it },
            label = { Text("Начало блока") })
        OutlinedTextField(value = blockEnd, onValueChange = { blockEnd = it },
            label = { Text("Конец блока") })
        Button(enabled = !busy && dayIso != null && loadedDay == dayIso, onClick = {
            if (parseWindow(blockStart, blockEnd) == null) {
                error = "Некорректный интервал блока."
            } else scope.launch {
                busy = true
                try {
                    api.addBlock(token, requireNotNull(dayIso), blockStart, blockEnd)
                    error = "Время заблокировано."
                    onChanged()
                } catch (ex: Exception) {
                    error = "Не удалось заблокировать время: ${ex.message}"
                } finally { busy = false }
            }
        }) { Text("Заблокировать интервал") }
        if (error.isNotBlank()) Text(error)
    }
    if (confirmHoliday) AlertDialog(
        onDismissRequest = { confirmHoliday = false },
        title = { Text("Сделать день выходным?") },
        text = { Text("Дата: $day. При наличии записей сервер не разрешит закрыть день.") },
        confirmButton = {
            TextButton(onClick = {
                confirmHoliday = false
                scope.launch {
                    busy = true
                    try {
                        api.setSchedule(token, requireNotNull(dayIso), emptyList())
                        error = "День закрыт."
                        onChanged()
                    } catch (ex: Exception) { error = "День не закрыт: ${ex.message}" }
                    finally { busy = false }
                }
            }) { Text("Закрыть день") }
        },
        dismissButton = { TextButton(onClick = { confirmHoliday = false }) { Text("Отмена") } },
    )
}
