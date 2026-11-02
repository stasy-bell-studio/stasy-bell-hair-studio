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

@Composable
fun CalendarPanel(
    api: ApiClient, token: String, visits: List<VisitItem>,
    clients: List<ClientItem>, services: List<ServiceItem>, onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<VisitItem?>(null) }
    var pendingAction by remember { mutableStateOf("") }
    var moveDay by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).toString()) }
    var moveTime by remember { mutableStateOf("10:00") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun request(visit: VisitItem, action: String) {
        selected = visit
        pendingAction = action
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Календарь · ближайшие 8 дней", style = MaterialTheme.typography.titleLarge)
        if (visits.isEmpty()) Text("На выбранные даты записей нет.")
        visits.forEach { visit ->
            val client = clients.find { it.id == visit.clientId }?.name ?: "Без клиента"
            val service = services.find { it.id == visit.serviceId }?.name ?: "Блок времени"
            Text("${visit.localTime} · $client · $service")
            Text("${visit.status} · ${visit.source}", style = MaterialTheme.typography.bodySmall)
            if (visit.kind == "appointment" && visit.status == "confirmed") {
                Button(onClick = { request(visit, "move") }) { Text("Перенести") }
                Button(onClick = { request(visit, "complete") }) { Text("Услуга выполнена") }
                Button(onClick = { request(visit, "no_show") }) { Text("Не пришёл") }
                Button(onClick = { request(visit, "cancel") }) { Text("Отменить") }
            }
            if (visit.kind == "appointment" && visit.status in
                    listOf("confirmed", "completed", "no_show", "cancelled")) {
                Button(onClick = { request(visit, "delete") }) { Text("Удалить с историей") }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
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
                    if (pendingAction == "move") {
                        OutlinedTextField(
                            value = moveDay, onValueChange = { moveDay = it },
                            label = { Text("Новая дата, ГГГГ-ММ-ДД") },
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
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            when (pendingAction) {
                                "move" -> api.moveVisit(token, visit.id, moveDay, moveTime)
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
