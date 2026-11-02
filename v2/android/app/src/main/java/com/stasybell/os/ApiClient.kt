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

data class VisitItem(
    val id: String, val clientId: String?, val serviceId: String?,
    val startsAt: String, val localTime: String, val status: String,
    val source: String, val kind: String,
)
data class WorkingTime(val start: String, val end: String)
data class MaterialItem(val id: String, val name: String, val unit: String, val milliunits: Long)

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
                id = row.getString("id"),
                clientId = row.optString("client_id").takeIf { it.isNotBlank() && it != "null" },
                serviceId = row.optString("service_id").takeIf { it.isNotBlank() && it != "null" },
                startsAt = row.getString("starts_at"),
                localTime = localVisitTime(row.getString("starts_at")),
                status = row.getString("status"),
                source = row.getString("source"),
                kind = row.getString("kind"),
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

    suspend fun schedule(token: String, day: String): List<WorkingTime> {
        val rows = call("GET", "owner/schedule/$day", token = token).getJSONArray("windows")
        return (0 until rows.length()).map { index ->
            val r = rows.getJSONObject(index)
            WorkingTime(r.getString("starts_at"), r.getString("ends_at"))
        }
    }

    suspend fun setSchedule(token: String, day: String, windows: List<WorkingTime>) {
        val rows = JSONArray()
        windows.forEach { rows.put(JSONObject().put("starts_at", it.start).put("ends_at", it.end)) }
        call("PUT", "owner/schedule/$day", JSONObject().put("windows", rows), token)
    }

    suspend fun moveVisit(token: String, id: String, day: String, clock: String) {
        call("PATCH", "owner/calendar/$id/move",
             JSONObject().put("starts_at", toMoscowOffset(day, clock)), token)
    }

    suspend fun updateVisitStatus(token: String, id: String, status: String) {
        call("POST", "owner/calendar/$id/status", JSONObject().put("status", status), token)
    }

    suspend fun cancelVisit(token: String, id: String) {
        call("POST", "owner/calendar/$id/cancel", token = token)
    }

    suspend fun deleteVisit(token: String, visit: VisitItem, currentClientName: String) {
        val body = JSONObject().put("confirm", true)
            .put("expected_start", visit.startsAt)
            .put("expected_client_name", currentClientName)
            .put("completed_acknowledged", visit.status == "completed")
        call("POST", "owner/calendar/${visit.id}/delete", body, token)
    }

    suspend fun addBlock(token: String, day: String, start: String, end: String) {
        val body = JSONObject()
            .put("starts_at", toMoscowOffset(day, start))
            .put("ends_at", toMoscowOffset(day, end))
        call("POST", "owner/calendar/blocks", body, token)
    }

    suspend fun materials(token: String): List<MaterialItem> {
        val rows = call("GET", "owner/materials", token = token).getJSONArray("materials")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            MaterialItem(row.getString("id"), row.getString("name"),
                row.getString("unit"), row.getLong("stock_milliunits"))
        }
    }

    suspend fun newMaterial(token: String, name: String, unit: String) {
        call("POST", "owner/materials", JSONObject().put("name", name).put("unit", unit), token)
    }

    suspend fun adjustStock(token: String, id: String, changeMilliunits: Long, reason: String) {
        val body = JSONObject().put("change_milliunits", changeMilliunits).put("reason", reason)
        call("POST", "owner/materials/$id/movement", body, token)
    }

    suspend fun createService(token: String, name: String, variant: String?,
                              priceKopecks: Int, duration: Int, buffer: Int) {
        val body = JSONObject()
            .put("code", "owner-" + java.util.UUID.randomUUID())
            .put("name", name).put("variant", variant)
            .put("price_kopecks", priceKopecks)
            .put("duration_minutes", duration).put("buffer_minutes", buffer)
            .put("active", true)
        call("POST", "owner/services", body, token)
    }
}
