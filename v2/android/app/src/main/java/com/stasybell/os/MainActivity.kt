package com.stasybell.os

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent { StasyTheme { MasterApp() } }
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
    var cash by remember { mutableStateOf<CashSummary?>(null) }
    val today = LocalDate.now(ZoneId.of("Europe/Moscow"))

    fun refresh(currentToken: String) {
        scope.launch {
            busy = true
            cash = null
            try {
                services = api.services(currentToken)
                clients = api.clients(currentToken)
                visits = api.visits(currentToken, today)
                error = ""
                cash = try { api.financeSummary(currentToken, today.year, today.monthValue) }
                    catch (_: Exception) { null }
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
            modifier = Modifier.fillMaxSize().background(StasyColors.Canvas).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("STASY BELL", style = MaterialTheme.typography.titleLarge, color = StasyColors.Primary)
            Spacer(Modifier.height(24.dp))
            Text("Вход", style = MaterialTheme.typography.headlineSmall)
            Text("Личный кабинет мастера", color = StasyColors.Body)
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
            StasyButton(
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Войти") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text("Учётные данные не сохраняются на устройстве.")
        }
    } else {
        val activeToken = token ?: return
        val tabs = listOf("Сегодня", "Календарь", "Клиенты", "Финансы", "Ещё")
        val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.Person,
            Icons.Default.AccountBox, Icons.Default.MoreVert)
        val morePages = listOf("+ Запись", "График", "Услуги", "Материалы", "Отчёты", "Настройки")
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = StasyColors.Surface, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = page == tab || (tab == "Ещё" && page in morePages),
                            onClick = { page = tab },
                            icon = { Icon(icons[index], contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text(tab, fontSize = 10.sp) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StasyColors.Primary,
                                selectedTextColor = StasyColors.Primary,
                                indicatorColor = StasyColors.MintSoft,
                                unselectedIconColor = StasyColors.Body,
                                unselectedTextColor = StasyColors.Body,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Stasy Bell OS", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { refresh(activeToken) }, enabled = !busy) {
                        Text("Обновить")
                    }
                    TextButton(onClick = { token = null; cash = null; page = "Сегодня" }) {
                        Text("Выход")
                    }
                }
                if (busy) Text("Загрузка…")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                when (page) {
                    "Сегодня" -> {
                        val appointments = visits.filter {
                            it.kind == "appointment" && countsAsVisit(it.status)
                        }
                        val todayVisits = appointments.filter { it.localTime.startsWith(today.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        )) }
                        val upcoming = appointments.filter {
                            it.status in listOf("confirmed", "pending_channel") &&
                                java.time.OffsetDateTime.parse(it.startsAt).isAfter(java.time.OffsetDateTime.now())
                        }.take(4)
                        Surface(shape = MaterialTheme.shapes.large, color = StasyColors.PrimaryDeep) {
                            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(
                                listOf(StasyColors.Forest, StasyColors.PrimaryDeep, StasyColors.Primary)
                            )).padding(20.dp)) {
                                Text("РАБОЧИЙ ДЕНЬ", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text("Добро пожаловать", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(today.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")), color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KpiValue("Записей сегодня", todayVisits.size.toString(), Modifier.weight(1f))
                            KpiValue("Поступления (факт) · месяц", cash?.let { formatCash(it.incomeKopecks) } ?: "—", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        StasyButton(onClick = { page = "+ Запись" }, modifier = Modifier.fillMaxWidth()) { Text("+ Запись") }
                        Spacer(Modifier.height(16.dp))
                        AppCard {
                            Text("Ближайшие визиты", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            if (upcoming.isEmpty()) Text("На ближайшие дни записей нет.", color = StasyColors.Body)
                            upcoming.forEach { visit ->
                                val client = clients.find { it.id == visit.clientId }?.name ?: "Без клиента"
                                val service = services.find { it.id == visit.serviceId }?.name ?: "Блок времени"
                                Text("${visit.localTime} · $client · $service")
                            }
                            Text("Всего записей на 8 дней: ${appointments.size}", style = MaterialTheme.typography.bodySmall)
                            Text("Ожидающие, подтверждённые и выполненные.", style = MaterialTheme.typography.bodySmall)
                        }
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
                        ClientsPanel(clients, visits, services)
                    }
                    "Услуги" -> {
                        ServicesPanel(api, activeToken, services) { refresh(activeToken) }
                    }
                    "Финансы" -> {
                        FinancePanel(api = api, token = activeToken, onChanged = { refresh(activeToken) })
                    }
                    "Материалы" -> {
                        MaterialsPanel(api, activeToken)
                    }
                    "Отчёты" -> {
                        Text("Отчёты", style = MaterialTheme.typography.titleLarge)
                        AppCard {
                            Text("Подробные отчёты пока недоступны.")
                            Text("Фактические поступления и расходы доступны в разделе «Финансы».")
                        }
                    }
                    "Настройки" -> {
                        Text("Настройки", style = MaterialTheme.typography.titleLarge)
                        AppCard {
                            Text("Интеграции и резервные копии", style = MaterialTheme.typography.titleMedium)
                            Text("Состояние подключений и резервных копий в приложении пока не отображается.")
                        }
                    }
                    "Ещё" -> {
                        Text("Ещё", style = MaterialTheme.typography.titleLarge)
                        Text("Рабочие инструменты", color = StasyColors.Body)
                        morePages.forEach { destination ->
                            TextButton(onClick = { page = destination }, modifier = Modifier.fillMaxWidth()) {
                                Text(destination)
                            }
                        }
                    }
                    "+ Запись" -> {
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
fun ClientsPanel(clients: List<ClientItem>, visits: List<VisitItem>, services: List<ServiceItem>) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = clients.find { it.id == selectedId }
    val filtered = clients.filter {
        it.name.contains(query.trim(), ignoreCase = true) ||
            (it.phone?.contains(query.trim(), ignoreCase = true) == true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected != null) {
            Text("Карточка клиента", style = MaterialTheme.typography.titleLarge)
            AppCard {
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                Text(selected.phone ?: "Телефон не указан")
            }
            Text("Записи на ближайшие 8 дней", style = MaterialTheme.typography.titleMedium)
            val clientVisits = visits.filter { it.clientId == selected.id }
            if (clientVisits.isEmpty()) Text("Записей за этот период нет.")
            clientVisits.forEach { visit ->
                val service = services.find { it.id == visit.serviceId }?.name ?: "Услуга не указана"
                AppCard {
                    Text("${visit.localTime} · $service")
                    Text(visitStatusLabel(visit.status), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Полная история клиента пока недоступна.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { selectedId = null }) { Text("Назад к клиентам") }
        } else {
            Text("Клиенты · ${clients.size}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Поиск по имени или телефону") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (filtered.isEmpty()) {
                Text(if (clients.isEmpty()) "Клиентов пока нет." else "Клиенты не найдены.")
            }
            filtered.forEach { client ->
                AppCard {
                    Text(client.name, style = MaterialTheme.typography.titleMedium)
                    client.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { selectedId = client.id }) { Text("Подробнее · ${client.name}") }
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
    var date by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).format(studioDateFormat)) }
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
        StasyButton(onClick = { expanded = true }) {
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
        selected?.priceKopecks?.let { price ->
            Text("Ожидаемая стоимость записи: ${formatCash(price.toLong())}")
            Text("Фактические поступления записываются отдельно.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value = date, onValueChange = { date = it },
            label = { Text("Дата: ДД.ММ.ГГГГ") }, isError = date.isNotEmpty() && parseStudioDate(date) == null)
        OutlinedTextField(value = clock, onValueChange = { clock = it },
            label = { Text("Время: ЧЧ:ММ") })
        StasyButton(
            enabled = !busy && name.isNotBlank() && selected != null && parseStudioDate(date) != null,
            onClick = { selected?.let { service -> parseStudioDate(date)?.let { day ->
                onCreate(name.trim(), service.id, day.toString(), clock)
            } } },
        ) { Text("Создать подтверждённую запись") }
    }
}
