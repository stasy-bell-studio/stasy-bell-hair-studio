package com.stasybell.os

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: String) {
        waitFor(text)
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun enter(label: String, value: String) {
        waitFor(label)
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
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
        enter("Дата: ГГГГ-ММ-ДД", day)
        enter("Время: ЧЧ:ММ", time)
        screenshot("manual-booking")
        click("Создать подтверждённую запись")
        waitFor(client, substring = true)
    }

    @Test fun ownerJourney() {
        val day = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(1)
        val iso = day.toString()
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
        enter("Дата, ГГГГ-ММ-ДД", iso)
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
        enter("Стоимость услуги, ₽", "1000")
        enter("Длительность, минуты", "60")
        enter("Буфер, минуты", "15")
        click("Создать услугу")
        waitFor("Изменить · Synthetic service")
        screenshot("04-service-created")
        click("Изменить · Synthetic service")
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
        waitFor("На выбранные даты записей нет.")
        screenshot("05-calendar-week")
        click("Месяц")
        waitFor("На выбранные даты записей нет.")
        screenshot("05-calendar-month")

        book("Synthetic Move", "10:00", iso)
        waitFor("$display 10:00 · Synthetic Move", substring = true)
        waitFor("Ожидаемая стоимость записи: 1200,50 ₽")
        screenshot("06-calendar")
        click("Перенести")
        enter("Новая дата, ГГГГ-ММ-ДД", iso)
        enter("Новое время, ЧЧ:ММ", "12:00")
        click("Подтвердить")
        waitFor("$display 12:00 · Synthetic Move", substring = true)
        screenshot("07-moved")
        click("Отменить")
        click("Подтвердить")
        waitFor("Отменена · вручную")
        screenshot("08-cancelled")

        book("Synthetic Completed", "14:00", iso)
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

        click("Финансы")
        waitFor("Поступления: 0,00 ₽")
        enter("Сумма, ₽", "1500")
        click("Записать фактическую операцию")
        waitFor("Поступления: 1500,00 ₽")
        click("Доход — переключить на расход")
        click("Прочее")
        click("Материалы")
        enter("Сумма, ₽", "300,25")
        click("Записать фактическую операцию")
        waitFor("Расходы: 300,25 ₽")
        waitFor("Денежная разница: 1199,75 ₽")
        screenshot("10-finance")
        waitFor("Расход · Материалы")
        val history = compose.onNodeWithText("Расход · Материалы")
        if (!history.isDisplayed()) history.performScrollTo()
        history.assertIsDisplayed()
        val incomeHistory = compose.onNodeWithText("Поступление · Прочее")
        incomeHistory.performScrollTo()
        incomeHistory.assertIsDisplayed()
        screenshot("10-finance-history")
        click("Сегодня")
        waitFor("1500,00 ₽")
        screenshot("10-today-after-cash")

        click("Ещё")
        click("Материалы")
        waitFor("Новый материал")
        enter("Название состава", "Synthetic material")
        click("Добавить материал")
        waitFor("Изменить остаток · Synthetic material")
        click("Изменить остаток · Synthetic material")
        enter("Количество g (например 25,5)", "25")
        enter("Причина: закупка / процедура", "Synthetic purchase")
        click("Добавить на склад")
        waitFor("Synthetic material: 25.0 g")
        click("Изменить остаток · Synthetic material")
        enter("Количество g (например 25,5)", "5")
        enter("Причина: закупка / процедура", "Synthetic usage")
        click("Списать со склада")
        waitFor("Synthetic material: 20.0 g")
        val stock = compose.onNodeWithText("Synthetic material: 20.0 g")
        if (!stock.isDisplayed()) stock.performScrollTo()
        stock.assertIsDisplayed()
        screenshot("11-materials")
        click("Выход")
        waitFor("Личный кабинет мастера")
        screenshot("12-logout")
    }
}
