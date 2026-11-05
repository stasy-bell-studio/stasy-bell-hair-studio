package com.stasybell.os

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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
        // Capture the actual Compose rendering after its draw barrier, avoiding
        // a shell screenshot racing the system compositor's previous frame.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "visual-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        // Connected tests uninstall the app at teardown, removing its scoped files.
        // Preserve the rendered image in the shell-owned evidence directory first.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/acceptance",
            "cp ${directory.absolutePath}/$name.png /sdcard/acceptance/$name.png").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                .use { it.readBytes() }
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
        waitFor("Анна · демо")
        compose.onNodeWithText("Анна · демо").assertIsDisplayed()
        capture("review-02-calendar")

        compose.onNodeWithContentDescription("Финансы").performClick()
        waitFor("Поступления и расходы по неделям")
        compose.onNodeWithContentDescription("Итог: 10 200 ₽").assertIsDisplayed()
        val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
        val firstDay = ((today.dayOfMonth - 1) / 7) * 7 + 1
        val lastDay = minOf(firstDay + 6, YearMonth.from(today).lengthOfMonth())
        compose.onNodeWithContentDescription(
            "$firstDay–$lastDay: поступления 12 000 ₽, расходы 1 800 ₽",
        ).assertExists()
        capture("review-03-finance")
    }
}
