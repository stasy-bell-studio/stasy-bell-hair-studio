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
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /sdcard/acceptance").close()
        automation.executeShellCommand("screencap -p /sdcard/acceptance/$name.png").close()
    }

    private fun book(client: String, time: String, day: String) {
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

        click("Услуги")
        waitFor("Новая услуга")
        enter("Название", "Synthetic service")
        enter("Стоимость, ₽ целыми рублями", "1000")
        enter("Длительность, минуты", "60")
        click("Создать услугу")
        waitFor("Изменить · Synthetic service")
        screenshot("04-service-created")
        click("Изменить · Synthetic service")
        enter("Стоимость, ₽ целыми рублями", "1200")
        click("Сохранить изменения")
        waitFor("Synthetic service : 1200 ₽", substring = true)
        screenshot("05-service-edited")

        book("Synthetic Move", "10:00", iso)
        waitFor("$display 10:00 · Synthetic Move", substring = true)
        screenshot("06-calendar")
        click("Перенести")
        enter("Новая дата, ГГГГ-ММ-ДД", iso)
        enter("Новое время, ЧЧ:ММ", "12:00")
        click("Подтвердить")
        waitFor("$display 12:00 · Synthetic Move", substring = true)
        screenshot("07-moved")
        click("Отменить")
        click("Подтвердить")
        waitFor("cancelled · phone")
        screenshot("08-cancelled")

        book("Synthetic Completed", "14:00", iso)
        waitFor("$display 14:00 · Synthetic Completed", substring = true)
        click("Услуга выполнена")
        click("Подтвердить")
        waitFor("completed · phone")
        screenshot("09-completed")

        click("Финансы")
        waitFor("Поступления: 0,00 ₽")
        enter("Сумма в целых рублях", "1500")
        click("Записать фактическую операцию")
        waitFor("Поступления: 1500,00 ₽")
        screenshot("10-finance")

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
        screenshot("11-materials")
        compose.onNodeWithText("Synthetic material: 25.0 g").assertIsDisplayed()
    }
}
