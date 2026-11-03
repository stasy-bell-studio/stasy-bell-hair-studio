package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.math.BigDecimal

fun rublesToKopecks(raw: String): Int? = try {
    val value = BigDecimal(raw.replace(" ", "").replace(",", "."))
        .movePointRight(2).intValueExact()
    value.takeIf { it > 0 }
} catch (_: ArithmeticException) { null } catch (_: NumberFormatException) { null }

@Composable
fun ServicesPanel(
    api: ApiClient, token: String, services: List<ServiceItem>, onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var buffer by remember { mutableStateOf("0") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Услуги и длительность", style = MaterialTheme.typography.titleLarge)
        Text("Цены и время задаются мастером; ничего автоматически не добавляется.")
        services.forEach { item ->
            Text("${item.name} ${item.variant ?: ""}: " +
                "${item.priceKopecks?.let { formatCash(it.toLong()) } ?: "—"} · ${item.minutes ?: "—"} мин")
            Button(onClick = {
                editingId = item.id
                name = item.name; variant = item.variant.orEmpty()
                price = item.priceKopecks?.let { BigDecimal.valueOf(it.toLong(), 2).toPlainString() }.orEmpty()
                duration = item.minutes?.toString().orEmpty()
                buffer = item.bufferMinutes.toString()
            }) { Text("Изменить · ${item.name}") }
        }
        Text(if (editingId == null) "Новая услуга" else "Редактирование услуги",
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = variant, onValueChange = { variant = it },
            label = { Text("Вариант (если нужен)") })
        OutlinedTextField(value = price, onValueChange = { price = it },
            label = { Text("Стоимость услуги, ₽") })
        OutlinedTextField(value = duration, onValueChange = { duration = it },
            label = { Text("Длительность, минуты") })
        OutlinedTextField(value = buffer, onValueChange = { buffer = it },
            label = { Text("Буфер, минуты") })
        val cost = rublesToKopecks(price)
        val minutes = duration.toIntOrNull()?.takeIf { it in 1..1440 }
        val gap = buffer.toIntOrNull()?.takeIf { it in 0..1440 }
        Button(
            enabled = !busy && name.isNotBlank() && cost != null &&
                minutes != null && gap != null,
            onClick = {
                if (cost != null && minutes != null && gap != null) scope.launch {
                    busy = true
                    try {
                        if (editingId == null) {
                            api.createService(token, name.trim(), variant.trim().ifBlank { null },
                                cost, minutes, gap)
                        } else {
                            api.updateService(token, editingId!!, name.trim(),
                                variant.trim().ifBlank { null }, cost, minutes, gap)
                        }
                        name = ""; variant = ""; price = ""; duration = ""; buffer = "0"; editingId = null
                        message = "Услуга сохранена."
                        onChanged()
                    } catch (ex: Exception) { message = "Ошибка: ${ex.message}" }
                    finally { busy = false }
                }
            },
        ) { Text(if (editingId == null) "Создать услугу" else "Сохранить изменения") }
        if (message.isNotBlank()) Text(message)
    }
}
