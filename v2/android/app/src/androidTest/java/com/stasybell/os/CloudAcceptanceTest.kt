package com.stasybell.os

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import android.content.Context
import android.view.inputmethod.InputMethodManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only against the disposable backend started by android-cloud-acceptance.yml. */
@RunWith(AndroidJUnit4::class)
class CloudAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun setSystemSetting(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: String) {
        waitFor(text)
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        compose.waitUntil(timeoutMillis = 20_000) {
            runCatching { node.assertIsEnabled() }.isSuccess
        }
        node.performClick()
    }

    private fun clickAccessible(description: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        val node = compose.onNodeWithContentDescription(description)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun enter(label: String, value: String) {
        waitFor(label)
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        compose.waitUntil(timeoutMillis = 20_000) {
            runCatching { node.assertIsEnabled() }.isSuccess
        }
        node.performTextReplacement(value)
    }

    private fun screenshot(name: String) {
        compose.activity.runOnUiThread {
            val focus = compose.activity.currentFocus
            val keyboard = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            keyboard.hideSoftInputFromWindow(focus?.windowToken, 0)
            focus?.clearFocus()
        }
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/acceptance", "screencap -p /sdcard/acceptance/$name.png")
            .forEach { command ->
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                    .use { it.readBytes() }
            }
    }

    private fun book(client: String, time: String, day: String) {
        click("Ещё")
        click("+ Запись")
        enter("Имя клиента", client)
        click("Выбрать услугу")
        click("Synthetic service · ")
        enter("Дата: ДД.ММ.ГГГГ", day)
        enter("Время: ЧЧ:ММ", time)
        screenshot("manual-booking")
        click("Создать подтверждённую запись")
        waitFor(client, substring = true)
    }

    @Test fun ownerJourney() {
        val day = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(1)
        val display = day.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        waitFor("Личный кабинет мастера")
        screenshot("01-login")
        enter("Логин", "acceptance_owner")
        enter("Пароль", "synthetic_password_only_123")
        click("Войти")
        waitFor("Ближайшие визиты")
        screenshot("02-today")

        click("Ещё")
        click("График")
        waitFor("Рабочий график")
        enter("Дата, ДД.ММ.ГГГГ", display)
        click("Обновить день")
        waitFor("В этот день нет открытых окон.")
        enter("Начало первого окна", "09:00")
        enter("Конец первого окна", "19:00")
        click("Сохранить рабочие окна")
        waitFor("График сохранён.")
        screenshot("03-schedule")

        click("Ещё")
        click("Услуги")
        waitFor("Новая услуга")
        enter("Название", "Synthetic service")
        enter("Вариант (если нужен)", "Synthetic variant")
        enter("Стоимость услуги, ₽", "1000")
        enter("Длительность, минуты", "60")
        enter("Буфер, минуты", "15")
        click("Создать услугу")
        waitFor("Изменить · Synthetic service")
        screenshot("04-service-created")
        click("Изменить · Synthetic service")
        enter("Вариант (если нужен)", "")
        enter("Стоимость услуги, ₽", "1200,50")
        click("Сохранить изменения")
        waitFor("Synthetic service : 1200,50 ₽", substring = true)
        click("Изменить · Synthetic service")
        waitFor("1200.50")
        click("Сохранить изменения")
        waitFor("Synthetic service : 1200,50 ₽", substring = true)
        screenshot("05-service-edited")

        click("Календарь")
        click("День")
        waitFor("На выбранные даты записей нет.")
        screenshot("05-calendar-day")
        click("Неделя")
        waitFor("Записей за период: 0")
        screenshot("05-calendar-week")
        val todayDisplay = day.minusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        clickAccessible("Выбрать $todayDisplay, записей: 0")
        waitFor(todayDisplay)
        click("Месяц")
        waitFor("Записей за период: 0")
        screenshot("05-calendar-month")
        clickAccessible("Выбрать $todayDisplay, записей: 0")
        waitFor(todayDisplay)
        click("Перейти к дате")
        enter("Дата, ДД.ММ.ГГГГ", display)
        click("Открыть день")
        waitFor(display)

        book("Synthetic Move", "10:00", display)
        waitFor("$display 10:00 · Synthetic Move", substring = true)
        waitFor("Ожидаемая стоимость записи: 1200,50 ₽")
        screenshot("06-calendar")
        click("Перейти к дате")
        enter("Дата, ДД.ММ.ГГГГ", display)
        click("Открыть день")
        waitFor("$display 10:00 · Synthetic Move", substring = true)
        waitFor("Рабочие окна: 09:00–19:00")
        waitFor("09:00–10:00 · Без записи")
        waitFor("10:00–11:15 · Synthetic Move · Подтверждена")
        click("Неделя")
        clickAccessible("Выбрать $display, записей: 1")
        waitFor("$display 10:00 · Synthetic Move", substring = true)
        click("Клиенты")
        click("Сегодня")
        waitFor("Synthetic Move", substring = true)
        screenshot("06-today-upcoming")
        click("Календарь")
        waitFor("$display 10:00 · Synthetic Move", substring = true)
        click("Перенести")
        enter("Новая дата, ДД.ММ.ГГГГ", display)
        enter("Новое время, ЧЧ:ММ", "12:00")
        click("Подтвердить")
        waitFor("$display 12:00 · Synthetic Move", substring = true)
        screenshot("07-moved")
        click("Отменить")
        click("Подтвердить")
        waitFor("Отменена · вручную")
        screenshot("08-cancelled")

        book("Synthetic Completed", "14:00", display)
        waitFor("$display 14:00 · Synthetic Completed", substring = true)
        click("Услуга выполнена")
        click("Подтвердить")
        waitFor("Выполнена · вручную")
        screenshot("09-completed")

        click("Клиенты")
        waitFor("Synthetic Completed")
        val client = compose.onNodeWithText("Synthetic Completed")
        if (!client.isDisplayed()) client.performScrollTo()
        client.assertIsDisplayed()
        enter("Поиск по имени или телефону", "Synthetic Completed")
        waitFor("Synthetic Completed")
        screenshot("09-clients")
        click("Подробнее · Synthetic Completed")
        waitFor("Карточка клиента")
        waitFor("Полная история клиента пока недоступна.")
        screenshot("09-client-detail")
        click("Назад к клиентам")

        click("Финансы")
        waitFor("Поступления: 0,00 ₽")
        screenshot("10-finance-overview")
        enter("Сумма, ₽", "900")
        click("Записать фактическую операцию")
        waitFor("Поступления: 900,00 ₽")
        enter("Сумма, ₽", "1500")
        compose.onNodeWithText("Записать фактическую операцию")
            .performTouchInput { doubleClick() }
        waitFor("Поступления: 2400,00 ₽")
        click("Доход — переключить на расход")
        click("Прочее")
        click("Материалы")
        enter("Сумма, ₽", "300,25")
        click("Записать фактическую операцию")
        waitFor("Расходы: 300,25 ₽")
        waitFor("Денежная разница: 2099,75 ₽")
        compose.onNodeWithText("Финансы ·", substring = true).performScrollTo()
        screenshot("10-finance")
        waitFor("Расход · Материалы")
        val history = compose.onNodeWithText("Расход · Материалы")
        if (!history.isDisplayed()) history.performScrollTo()
        history.assertIsDisplayed()
        val incomeHistory = compose.onNodeWithText("Поступление · Прочее")
        incomeHistory.performScrollTo()
        incomeHistory.assertIsDisplayed()
        check(compose.onAllNodesWithText("Поступление · Прочее").fetchSemanticsNodes().size == 2) {
            "A double tap should create one operation, and two actual entries should remain visible."
        }
        screenshot("10-finance-history")
        click("Предыдущий месяц")
        waitFor("Добавление операций доступно только в текущем месяце.")
        click("Следующий месяц")
        waitFor("Поступления: 2400,00 ₽")
        click("Сегодня")
        waitFor("2400,00 ₽")
        screenshot("10-today-after-cash")

        click("Ещё")
        click("Материалы")
        waitFor("Новый материал")
        enter("Название состава", "Synthetic material")
        click("Добавить материал")
        waitFor("Изменить остаток · Synthetic material")
        click("Изменить остаток · Synthetic material")
        enter("Количество г (например 25,5)", "25")
        enter("Причина: закупка / процедура", "Synthetic purchase")
        click("Добавить на склад")
        waitFor("Synthetic material: 25.0 г")
        click("Изменить остаток · Synthetic material")
        enter("Количество г (например 25,5)", "5")
        enter("Причина: закупка / процедура", "Synthetic usage")
        click("Списать со склада")
        waitFor("Synthetic material: 20.0 г")
        val stock = compose.onNodeWithText("Synthetic material: 20.0 г")
        if (!stock.isDisplayed()) stock.performScrollTo()
        stock.assertIsDisplayed()
        screenshot("11-materials")
        click("История · Synthetic material")
        waitFor("-5 г · Synthetic usage")
        val movement = compose.onNodeWithText("-5 г · Synthetic usage")
        if (!movement.isDisplayed()) movement.performScrollTo()
        movement.assertIsDisplayed()
        screenshot("11-material-history")
        click("Ещё")
        click("Отчёты")
        waitFor("Подробные отчёты пока недоступны.")
        screenshot("11-reports-unavailable")
        click("Ещё")
        click("Настройки")
        waitFor("Состояние подключений и резервных копий в приложении пока не отображается.")
        screenshot("11-settings-unavailable")
        clickAccessible("Выход")
        waitFor("Личный кабинет мастера")
        screenshot("12-logout")
    }

    @Test fun keyScreensRemainNavigableAtLargeSystemFont() {
        setSystemSetting("wm size 320x640")
        setSystemSetting("wm density 160")
        setSystemSetting("settings put system font_scale 1.5")
        try {
            compose.waitForIdle()
            waitFor("Личный кабинет мастера")
            enter("Логин", "acceptance_owner")
            enter("Пароль", "synthetic_password_only_123")
            click("Войти")
            waitFor("Ближайшие визиты")
            screenshot("13-today-font-1.5")

            clickAccessible("Календарь")
            clickAccessible("Неделя")
            waitFor("Записей за период:", substring = true)
            screenshot("13-calendar-week-font-1.5")
            val todayDate = LocalDate.now(ZoneId.of("Europe/Moscow"))
            val today = todayDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val todayCell = compose.onNodeWithContentDescription("Выбрать $today, записей: 0")
            if (!todayCell.isDisplayed()) todayCell.performScrollTo()
            screenshot("13-calendar-week-grid-font-1.5")
            click("Месяц")
            screenshot("13-calendar-month-font-1.5")
            val firstOfMonth = todayDate.withDayOfMonth(1)
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val firstCell = compose.onNodeWithContentDescription("Выбрать $firstOfMonth, записей: 0")
            if (!firstCell.isDisplayed()) firstCell.performScrollTo()
            screenshot("13-calendar-month-grid-font-1.5")

            clickAccessible("Финансы")
            waitFor("Новая операция")
            screenshot("13-finance-font-1.5")
            clickAccessible("Клиенты")
            waitFor("Поиск по имени или телефону")
            screenshot("13-clients-font-1.5")

            clickAccessible("Ещё")
            click("График")
            waitFor("Рабочий график")
            screenshot("13-schedule-font-1.5")
            clickAccessible("Ещё")
            click("Услуги")
            waitFor("Услуги и длительность")
            screenshot("13-services-font-1.5")
            clickAccessible("Ещё")
            click("Материалы")
            waitFor("Новый материал")
            screenshot("13-materials-font-1.5")
            clickAccessible("Ещё")
            click("+ Запись")
            waitFor("Ручная запись")
            screenshot("13-booking-font-1.5")
            clickAccessible("Выход")
        } finally {
            setSystemSetting("settings put system font_scale 1.0")
            setSystemSetting("wm size reset")
            setSystemSetting("wm density reset")
        }
    }
}
