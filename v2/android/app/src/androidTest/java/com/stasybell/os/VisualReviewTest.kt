package com.stasybell.os

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only captures after seed-visual-review.py, on Android 15 at 393 × 852 dp. */
@RunWith(AndroidJUnit4::class)
class VisualReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/acceptance", "screencap -p /sdcard/acceptance/$name.png").forEach {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { stream ->
                stream.readBytes()
            }
        }
    }

    @Test fun threeApprovedScreens() {
        waitFor("Логин")
        compose.onNodeWithText("Логин").performTextReplacement("acceptance_owner")
        compose.onNodeWithText("Пароль").performTextReplacement("synthetic_password_only_123")
        compose.onNodeWithText("Войти").performClick()
        waitFor("Ближайшие визиты")
        waitFor("12000,00 ₽")
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Загрузка…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Добрый день, Настя").assertIsDisplayed()
        capture("review-01-home")

        compose.onNodeWithContentDescription("Календарь").performClick()
        waitFor("Записей за период: 7")
        compose.onNodeWithText("Анна · демо").assertIsDisplayed()
        capture("review-02-calendar")

        compose.onNodeWithContentDescription("Финансы").performClick()
        waitFor("Поступления по неделям")
        compose.onNodeWithContentDescription("Денежная разница: 10200,00 ₽").assertIsDisplayed()
        capture("review-03-finance")
    }
}
