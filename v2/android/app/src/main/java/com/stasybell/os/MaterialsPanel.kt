package com.stasybell.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import java.math.BigDecimal

fun parseMilliunits(raw: String): Long? = try {
    val whole = BigDecimal(raw.replace(",", ".")).multiply(BigDecimal(1000))
    if (whole <= BigDecimal.ZERO || whole > BigDecimal(Int.MAX_VALUE))
        null else whole.longValueExact()
} catch (_: Exception) { null }

fun materialUnitLabel(unit: String): String = when (unit) {
    "g" -> "г"
    "ml" -> "мл"
    "pcs" -> "шт"
    else -> unit
}

@Composable
fun MaterialsPanel(api: ApiClient, token: String) {
    val scope = rememberCoroutineScope()
    var materials by remember { mutableStateOf<List<MaterialItem>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var changing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            busy = true
            try { materials = api.materials(token); error = "" }
            catch (ex: Exception) { error = "Не удалось загрузить остатки: ${ex.message}" }
            finally { busy = false }
        }
    }
    LaunchedEffect(token) { reload() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Материалы", style = MaterialTheme.typography.titleLarge)
        Text("Остатки фиксируются отдельно от расходов. Закупку внесите также во вкладке «Финансы».")
        materials.forEach { material ->
            AppCard {
                Text("${material.name}: ${material.milliunits / 1000.0} ${materialUnitLabel(material.unit)}")
                StasyButton(onClick = { changing = if (changing == material.id) null else material.id }) {
                    Text("Изменить остаток · ${material.name}")
                }
                if (changing == material.id) {
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it },
                        label = { Text("Количество ${materialUnitLabel(material.unit)} (например 25,5)") })
                    OutlinedTextField(value = reason, onValueChange = { reason = it },
                        label = { Text("Причина: закупка / процедура") })
                    val delta = parseMilliunits(quantity)
                    StasyButton(enabled = !busy && delta != null && reason.trim().length >= 3,
                        onClick = {
                            if (delta != null) scope.launch {
                                busy = true
                                try {
                                    api.adjustStock(token, material.id, delta, reason.trim())
                                    changing = null; quantity = ""; reason = ""
                                    reload()
                                } catch (ex: Exception) { error = "Ошибка: ${ex.message}" }
                                finally { busy = false }
                            }
                        }) { Text("Добавить на склад") }
                    StasyButton(enabled = !busy && delta != null && reason.trim().length >= 3,
                        onClick = {
                            if (delta != null) scope.launch {
                                busy = true
                                try {
                                    api.adjustStock(token, material.id, -delta, reason.trim())
                                    changing = null; quantity = ""; reason = ""
                                    reload()
                                } catch (ex: Exception) { error = "Ошибка: ${ex.message}" }
                                finally { busy = false }
                            }
                        }) { Text("Списать со склада") }
                }
            }
        }
        Text("Новый материал", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("Название состава") }, modifier = Modifier.fillMaxWidth())
        StasyButton(onClick = { unit = when (unit) { "g" -> "ml"; "ml" -> "pcs"; else -> "g" } }) {
            Text("Единица: ${materialUnitLabel(unit)} (сменить)")
        }
        StasyButton(enabled = name.trim().isNotEmpty() && !busy, onClick = {
            scope.launch {
                busy = true
                try {
                    api.newMaterial(token, name.trim(), unit)
                    name = ""
                    reload()
                } catch (ex: Exception) { error = "Материал не создан: ${ex.message}" }
                finally { busy = false }
            }
        }) { Text("Добавить материал") }
        StasyButton(onClick = { reload() }, enabled = !busy) { Text("Обновить остатки") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
