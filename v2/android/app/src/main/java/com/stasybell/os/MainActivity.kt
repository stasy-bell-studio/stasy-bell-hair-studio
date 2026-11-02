package com.stasybell.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MasterApp() } }
    }
}

@Composable
fun MasterApp() {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Сегодня") }
    var busy by remember { mutableStateOf(false) }
    var services by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var clients by remember { mutableStateOf<List<ClientItem>>(emptyList()) }
    var visits by remember { mutableStateOf<List<VisitItem>>(emptyList()) }
    val today = LocalDate.now(ZoneId.of("Europe/Moscow"))

    fun refresh(currentToken: String) {
        scope.launch {
            busy = true
            try {
                services = api.services(currentToken)
                clients = api.clients(currentToken)
                visits = api.visits(currentToken, today)
                error = ""
            } catch (ex: Exception) {
                error = "Не удалось обновить данные: ${ex.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(token) {
        token?.let { refresh(it) }
    }

    if (token == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Stasy Bell OS", style = MaterialTheme.typography.headlineMedium)
            Text("Личный кабинет мастера")
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Логин") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            token = api.login(user, password)
                            password = ""
                            error = ""
                        } catch (ex: Exception) {
                            error = "Ошибка входа: ${ex.message}"
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy && user.isNotBlank() && password.isNotBlank(),
            ) { Text("Войти") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text("Учётные данные не сохраняются на устройстве.")
        }
    } else {
        val activeToken = token ?: return
        val tabs = listOf("Сегодня", "Календарь", "График", "Клиенты", "Услуги", "+ Запись", "Финансы", "Материалы")
        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { tab ->
                        Button(onClick = { page = tab }, enabled = page != tab) { Text(tab) }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stasy Bell OS", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { refresh(activeToken) }, enabled = !busy) {
                        Text("Обновить")
                    }
                    Button(onClick = { token = null; page = "Сегодня" }) {
                        Text("Выход")
                    }
                }
                if (busy) Text("Загрузка…")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                when (page) {
                    "Сегодня" -> {
                        Text("Ближайшие визиты", style = MaterialTheme.typography.titleLarge)
                        if (visits.isEmpty()) Text("Нет записей на ближайшую неделю")
                        visits.filter { it.localTime.startsWith(today.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        )) }.forEach { visit ->
                            Text("${visit.localTime} · ${visit.status} · ${visit.source}")
                        }
                        Text("Всего записей на 8 дней: ${visits.size}")
                    }
                    "Календарь" -> {
                        CalendarPanel(api, activeToken, visits, clients, services) {
                            refresh(activeToken)
                        }
                    }
                    "График" -> {
                        SchedulePanel(api, activeToken) { refresh(activeToken) }
                    }
                    "Клиенты" -> {
                        Text("Клиенты · ${clients.size}", style = MaterialTheme.typography.titleLarge)
                        clients.forEach { client -> Text(client.name) }
                    }
                    "Услуги" -> {
                        ServicesPanel(api, activeToken, services) { refresh(activeToken) }
                    }
                    "Финансы" -> {
                        FinancePanel(api = api, token = activeToken)
                    }
                    "Материалы" -> {
                        MaterialsPanel(api, activeToken)
                    }
                    else -> {
                        ManualBookingForm(
                            services = services, busy = busy,
                            onCreate = { name, service, day, clock ->
                                scope.launch {
                                    busy = true
                                    try {
                                        api.manualBooking(activeToken, name, service, day, clock)
                                        error = ""
                                        page = "Календарь"
                                        refresh(activeToken)
                                    } catch (ex: Exception) {
                                        error = "Запись не создана: ${ex.message}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ManualBookingForm(
    services: List<ServiceItem>, busy: Boolean,
    onCreate: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).toString()) }
    var clock by remember { mutableStateOf("10:00") }
    var selected by remember { mutableStateOf<ServiceItem?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ручная запись", style = MaterialTheme.typography.titleLarge)
        Text("Подтверждение по телефону и через бота не требуется.")
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Имя клиента") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { expanded = true }) {
            Text(selected?.let { "${it.name} · ${it.variant ?: ""}" } ?: "Выбрать услугу")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            services.filter { it.minutes != null }.forEach { service ->
                DropdownMenuItem(
                    text = { Text(service.name + " · " + (service.variant ?: "")) },
                    onClick = { selected = service; expanded = false },
                )
            }
        }
        OutlinedTextField(value = date, onValueChange = { date = it },
            label = { Text("Дата: ГГГГ-ММ-ДД") })
        OutlinedTextField(value = clock, onValueChange = { clock = it },
            label = { Text("Время: ЧЧ:ММ") })
        Button(
            enabled = !busy && name.isNotBlank() && selected != null,
            onClick = { selected?.let { onCreate(name.trim(), it.id, date, clock) } },
        ) { Text("Создать подтверждённую запись") }
    }
}
