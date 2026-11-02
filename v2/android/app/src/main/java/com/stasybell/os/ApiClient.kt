package com.stasybell.os

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ServiceItem(
    val id: String, val name: String, val variant: String?,
    val minutes: Int?, val priceKopecks: Int?,
)
data class ClientItem(val id: String, val name: String)
data class CashSummary(
    val incomeKopecks: Long,
    val expenseKopecks: Long,
    val balanceKopecks: Long,
    val isClosed: Boolean,
)

data class VisitItem(val localTime: String, val status: String, val source: String)

fun toMoscowOffset(date: String, clock: String): String =
    LocalDateTime.parse("${date}T${clock}")
        .atZone(ZoneId.of("Europe/Moscow"))
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))

fun localVisitTime(utcOrOffset: String): String =
    java.time.OffsetDateTime.parse(utcOrOffset)
        .atZoneSameInstant(ZoneId.of("Europe/Moscow"))
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

class ApiClient(private val base: String = BuildConfig.API_BASE_URL) {
    private suspend fun call(
        method: String, path: String, payload: JSONObject? = null, token: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (payload != null) connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val content = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                // No credentials or request body are included in error messages.
                throw IOException("Сервер вернул код $status")
            }
            JSONObject(content)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun financeSummary(token: String, year: Int, month: Int): CashSummary {
        val result = call("GET", "owner/finances/summary?year=$year&month=$month",
            token = token)
        return CashSummary(
            result.getLong("income_kopecks"), result.getLong("expense_kopecks"),
            result.getLong("balance_kopecks"), result.getBoolean("is_closed"),
        )
    }

    suspend fun addCash(
        token: String, kind: String, category: String, amountKopecks: Long,
    ) {
        val occurredAt = java.time.OffsetDateTime.now(ZoneId.of("Europe/Moscow"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val body = JSONObject()
            .put("kind", kind)
            .put("category", category)
            .put("amount_kopecks", amountKopecks)
            .put("occurred_at", occurredAt)
        call("POST", "owner/finances/transactions", body, token)
    }

    suspend fun login(user: String, password: String): String =
        call("POST", "auth/login", JSONObject().put("username", user)
            .put("password", password)).getString("access_token")

    suspend fun services(token: String): List<ServiceItem> {
        val rows = call("GET", "owner/services", token = token).getJSONArray("services")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ServiceItem(
                row.getString("id"), row.getString("name"),
                row.optString("variant").takeIf { it.isNotBlank() && it != "null" },
                if (row.isNull("duration_minutes")) null else row.getInt("duration_minutes"),
                if (row.isNull("price_kopecks")) null else row.getInt("price_kopecks"),
            )
        }
    }

    suspend fun clients(token: String): List<ClientItem> {
        val rows: JSONArray = call("GET", "owner/clients", token = token).getJSONArray("clients")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ClientItem(row.getString("id"), row.getString("name"))
        }
    }

    suspend fun visits(token: String, today: LocalDate): List<VisitItem> {
        val end = today.plusDays(7)
        val rows = call("GET", "owner/calendar?from_day=$today&to_day=$end",
            token = token).getJSONArray("entries")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            VisitItem(
                localVisitTime(row.getString("starts_at")),
                row.getString("status"),
                row.getString("source"),
            )
        }
    }

    suspend fun manualBooking(
        token: String, clientName: String, serviceId: String, date: String, clock: String,
    ): String {
        val body = JSONObject().put("client_name", clientName)
            .put("service_id", serviceId)
            .put("starts_at", toMoscowOffset(date, clock))
            .put("source", "phone")
        return call("POST", "owner/calendar/manual", body, token).getString("id")
    }
}
