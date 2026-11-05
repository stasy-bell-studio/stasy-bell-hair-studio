package com.stasybell.os

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent { StasyTheme { MasterApp() } }
    }
}

@Composable
fun MasterApp() {
    val appContext = LocalContext.current
    val activity = appContext as? FragmentActivity
    val sessionStore = remember { SecureSessionStore(appContext) }
    val api = remember { ApiClient().apply { attachSessionStore(sessionStore) } }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var checkingSession by remember { mutableStateOf(true) }
    var biometricEnabled by remember { mutableStateOf(sessionStore.biometricUnlockEnabled) }
    var biometricMessage by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Сегодня") }
    var busy by remember { mutableStateOf(false) }
    var services by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }
    var clients by remember { mutableStateOf<List<ClientItem>>(emptyList()) }
    var visits by remember { mutableStateOf<List<VisitItem>>(emptyList()) }
    var cash by remember { mutableStateOf<CashSummary?>(null) }
    var dailyIncome by remember { mutableStateOf<Long?>(null) }
    var workingWindows by remember { mutableStateOf<List<WorkingTime>?>(null) }
    val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
    val view = LocalView.current
    SideEffect {
        api.onSessionExpired = {
            token = null
            error = "Сессия завершена. Войдите снова."
        }
        val activity = view.context as? android.app.Activity
        if (activity != null) WindowCompat.getInsetsController(activity.window, view)
            .isAppearanceLightStatusBars = token == null || page !in listOf("Сегодня", "Календарь", "Финансы")
    }

    fun refresh(currentToken: String) {
        scope.launch {
            busy = true
            cash = null
            dailyIncome = null
            workingWindows = null
            try {
                services = api.services(currentToken)
                clients = api.clients(currentToken)
                visits = api.visits(currentToken, today)
                error = ""
                cash = try { api.financeSummary(currentToken, today.year, today.monthValue) }
                    catch (_: Exception) { null }
                dailyIncome = try {
                    api.financeTransactions(currentToken, today.year, today.monthValue)
                        .filter { it.kind == "income" && java.time.OffsetDateTime.parse(it.occurredAt)
                            .atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDate() == today }
                        .sumOf { if (it.correctionOfId != null) -it.amountKopecks else it.amountKopecks }
                } catch (_: Exception) { null }
                workingWindows = try { api.schedule(currentToken, today.toString()) }
                    catch (_: Exception) { null }
            } catch (ex: Exception) {
                error = "Не удалось обновить данные: ${ex.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val allowed = !sessionStore.hasSession() || !biometricEnabled ||
            (activity != null && confirmFingerprint(activity))
        if (allowed) {
            token = api.restoreSession()
        }
        checkingSession = false
    }

    LaunchedEffect(token) {
        token?.let { refresh(it) }
    }

    if (checkingSession) {
        Column(modifier = Modifier.fillMaxSize().background(StasyColors.Canvas).padding(24.dp),
            verticalArrangement = Arrangement.Center) {
            Text("STASY BELL", style = MaterialTheme.typography.titleLarge,
                color = StasyColors.Primary)
            Text("Проверка входа…")
        }
    } else if (token == null) {
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
            Text("Пароль не сохраняется. Сессия защищена Android Keystore.")
        }
    } else {
        val activeToken = token ?: return
        val tabs = listOf("Сегодня", "Календарь", "Клиенты", "Финансы", "Ещё")
        val compactLabels = listOf("День", "Кал.", "Кл.", "Фин.", "Ещё")
        val useCompactLabels = LocalDensity.current.fontScale >= 1.3f
        val pageScrollState = rememberScrollState()
        LaunchedEffect(page) { pageScrollState.scrollTo(0) }
        val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.Person,
            Icons.Default.AccountBox, Icons.Default.MoreVert)
        val morePages = listOf("+ Запись", "График", "Услуги", "Материалы", "Отчёты", "Настройки")
        val forestPage = page in listOf("Сегодня", "Календарь", "Финансы")
        Scaffold(
            topBar = {
                if (forestPage) ForestSurface(Modifier.background(StasyColors.Forest).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (page == "Сегодня") "Stasy Bell OS" else page,
                            style = MaterialTheme.typography.titleMedium, color = Color.White,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { refresh(activeToken) }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, "Обновить", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            token = null; cash = null; page = "Сегодня"
                            scope.launch { api.logout() }
                        }) {
                            Icon(Icons.Default.ExitToApp, "Выход", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            floatingActionButton = {
                if (page in listOf("Сегодня", "Календарь")) FloatingActionButton(
                    onClick = { page = "+ Запись" }, containerColor = StasyColors.PrimaryDeep,
                    contentColor = Color.White, shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Default.Add, "+ Запись") }
            },
            bottomBar = {
                NavigationBar(containerColor = StasyColors.Surface, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = page == tab || (tab == "Ещё" && page in morePages),
                            onClick = { page = tab },
                            modifier = Modifier.semantics { contentDescription = tab },
                            icon = { Icon(icons[index], contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = { Text(if (useCompactLabels) compactLabels[index] else tab, fontSize = 10.sp) },
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
                modifier = Modifier.fillMaxSize().padding(padding)
                    .background(StasyColors.Canvas)
                    .verticalScroll(pageScrollState)
                    .padding(horizontal = if (page in listOf("Сегодня", "Финансы")) 0.dp else if (forestPage) 12.dp else 16.dp,
                        vertical = if (page in listOf("Сегодня", "Финансы")) 0.dp else 12.dp),
            ) {
                if (!forestPage) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Stasy Bell OS", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(top = 12.dp))
                    IconButton(onClick = { refresh(activeToken) }, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = {
                            token = null; cash = null; page = "Сегодня"
                            scope.launch { api.logout() }
                        }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Выход")
                    }
                }
                if (busy) Text("Загрузка…")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                }
                if (forestPage && busy) Text("Загрузка…", modifier = Modifier.padding(horizontal = 16.dp))
                if (forestPage && error.isNotBlank()) Text(error, color = StasyColors.Error,
                    modifier = Modifier.padding(16.dp))
                MaterialTheme(typography = if (forestPage) operationalTypography else MaterialTheme.typography) {
                when (page) {
                    "Сегодня" -> TodayOverview(today, visits, clients, services, dailyIncome, workingWindows) {
                        page = "Календарь"
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
                        ClientsPanel(api, activeToken, clients) { refresh(activeToken) }
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
                    "Отчёты" -> { ReportsPanel(api, activeToken) }
                    "Настройки" -> {
                        Text("Настройки", style = MaterialTheme.typography.titleLarge)
                        AppCard {
                            Text("Защита входа", style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Разблокировка отпечатком", modifier = Modifier.weight(1f))
                                Switch(checked = biometricEnabled, onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        sessionStore.biometricUnlockEnabled = false
                                        biometricEnabled = false
                                        biometricMessage = ""
                                    } else {
                                        scope.launch {
                                            if (activity != null && confirmFingerprint(activity)) {
                                                sessionStore.biometricUnlockEnabled = true
                                                biometricEnabled = true
                                                biometricMessage = "Включено для следующих запусков."
                                            } else {
                                                biometricMessage = "Отпечаток недоступен или не подтверждён."
                                            }
                                        }
                                    }
                                })
                            }
                            Text("Пароль вводится один раз. Сессия обновляется автоматически.",
                                style = MaterialTheme.typography.bodySmall)
                            if (biometricMessage.isNotBlank()) {
                                Text(biometricMessage, style = MaterialTheme.typography.bodySmall)
                            }
                        }
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
                            services = services, clients = clients, busy = busy,
                            onCreate = { name, existingClientId, service, day, clock ->
                                scope.launch {
                                    busy = true
                                    try {
                                        api.manualBooking(activeToken, name, existingClientId, service, day, clock)
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
}

@Composable
fun ClientsPanel(
    api: ApiClient, token: String, clients: List<ClientItem>,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<ClientHistoryVisit>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    val selected = clients.find { it.id == selectedId }
    val filtered = clients.filter {
        it.name.contains(query.trim(), ignoreCase = true) ||
            (it.phone?.contains(query.trim(), ignoreCase = true) == true)
    }

    LaunchedEffect(selectedId, token) {
        history = emptyList()
        total = 0
        hasMore = false
        errorMessage = ""
        editing = false
        val id = selectedId ?: return@LaunchedEffect
        loading = true
        try {
            val data = api.clientHistory(token, id)
            history = data.visits
            total = data.total
            hasMore = data.hasMore
        } catch (ex: Exception) {
            errorMessage = "Не удалось получить историю: ${ex.message}"
        } finally {
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected != null) {
            Text("Карточка клиента", style = MaterialTheme.typography.titleLarge)
            AppCard {
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                Text(selected.phone ?: "Телефон не указан")
                TextButton(onClick = {
                    clientName = selected.name
                    clientPhone = selected.phone.orEmpty()
                    editing = !editing
                }) { Text(if (editing) "Отмена изменений" else "Изменить клиента") }
                if (editing) {
                    OutlinedTextField(value = clientName, onValueChange = { clientName = it },
                        label = { Text("Имя клиента") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = clientPhone, onValueChange = { clientPhone = it },
                        label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth())
                    StasyButton(enabled = !loading && clientName.isNotBlank(), onClick = {
                        loading = true
                        val target = selected.id
                        scope.launch {
                            try {
                                api.updateClient(token, target, clientName.trim(), clientPhone.trim())
                                editing = false
                                errorMessage = ""
                                onChanged()
                            } catch (ex: Exception) {
                                errorMessage = "Не удалось сохранить клиента: ${ex.message}"
                            } finally { loading = false }
                        }
                    }) { Text("Сохранить клиента") }
                }
            }
            Text("История визитов · $total", style = MaterialTheme.typography.titleMedium)
            if (loading) Text("Загрузка…")
            if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
            if (!loading && history.isEmpty() && errorMessage.isBlank()) {
                Text("Визитов пока нет.")
            }
            history.forEach { visit ->
                AppCard {
                    Text(localVisitTime(visit.startsAt), style = MaterialTheme.typography.titleMedium)
                    Text(visit.service + (visit.variant?.let { " · $it" } ?: ""))
                    Text(visitStatusLabel(visit.status), style = MaterialTheme.typography.bodySmall)
                    visit.quotedKopecks?.let { Text("Стоимость по записи: ${formatCash(it)}") }
                    Text("Получено по связанным операциям: ${formatCash(visit.receivedKopecks)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (hasMore) {
                StasyButton(enabled = !loading, onClick = {
                    loading = true
                    val target = selected.id
                    val offset = history.size
                    scope.launch {
                        try {
                            val next = api.clientHistory(token, target, offset)
                            if (selectedId == target) {
                                history = history + next.visits
                                hasMore = next.hasMore
                                total = next.total
                                errorMessage = ""
                            }
                        } catch (ex: Exception) {
                            errorMessage = "Ошибка загрузки продолжения: ${ex.message}"
                        } finally { loading = false }
                    }
                }) { Text("Показать ещё") }
            }
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
                    TextButton(onClick = { selectedId = client.id }) {
                        Text("Подробнее · ${client.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun ManualBookingForm(
    services: List<ServiceItem>, clients: List<ClientItem>, busy: Boolean,
    onCreate: (String, String?, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var existingClientId by remember { mutableStateOf<String?>(null) }
    var clientPickerOpen by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Moscow")).format(studioDateFormat)) }
    var clock by remember { mutableStateOf("10:00") }
    var selected by remember { mutableStateOf<ServiceItem?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ручная запись", style = MaterialTheme.typography.titleLarge)
        Text("Подтверждение по телефону и через бота не требуется.")
        OutlinedTextField(
            value = name, onValueChange = { name = it; existingClientId = null },
            label = { Text("Имя клиента") }, modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { clientPickerOpen = true }) {
            Text(if (existingClientId == null) "Выбрать существующего клиента"
                else "Существующий клиент выбран")
        }
        DropdownMenu(expanded = clientPickerOpen,
            onDismissRequest = { clientPickerOpen = false }) {
            val matches = clients.filter {
                name.isBlank() || it.name.contains(name.trim(), ignoreCase = true)
            }.take(30)
            matches.forEach { client ->
                DropdownMenuItem(
                    text = { Text(client.name + (client.phone?.let { " · $it" } ?: "")) },
                    onClick = {
                        name = client.name
                        existingClientId = client.id
                        clientPickerOpen = false
                    },
                )
            }
            if (existingClientId != null) {
                DropdownMenuItem(text = { Text("Создать нового клиента") },
                    onClick = { existingClientId = null; clientPickerOpen = false })
            }
            if (matches.isEmpty()) {
                DropdownMenuItem(text = { Text("Совпадений нет — создайте нового клиента") },
                    onClick = { clientPickerOpen = false })
            }
        }
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
                onCreate(name.trim(), existingClientId, service.id, day.toString(), clock)
            } } },
        ) { Text("Создать подтверждённую запись") }
    }
}
