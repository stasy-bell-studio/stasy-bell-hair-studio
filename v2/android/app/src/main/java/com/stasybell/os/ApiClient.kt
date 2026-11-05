package com.stasybell.os

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ServiceItem(
    val id: String, val name: String, val variant: String?,
    val minutes: Int?, val priceKopecks: Int?, val bufferMinutes: Int,
)
data class ClientItem(val id: String, val name: String, val phone: String?)
data class ClientHistoryVisit(
    val id: String, val startsAt: String, val status: String, val source: String,
    val service: String, val variant: String?, val quotedKopecks: Long?,
    val receivedKopecks: Long,
)
data class ClientHistoryPage(
    val visits: List<ClientHistoryVisit>, val total: Int, val hasMore: Boolean,
)
data class ServiceReportRow(val name: String, val variant: String?, val completed: Int)
data class OwnerMonthlyReport(
    val year: Int, val month: Int, val visitsTotal: Int,
    val visitsByStatus: Map<String, Int>, val completedMinutes: Long,
    val serviceBreakdown: List<ServiceReportRow>,
    val incomeKopecks: Long, val expenseKopecks: Long, val balanceKopecks: Long,
    val isClosed: Boolean,
)
data class CashSummary(
    val incomeKopecks: Long,
    val expenseKopecks: Long,
    val balanceKopecks: Long,
    val isClosed: Boolean,
)
data class CashEntry(
    val id: String, val kind: String, val category: String, val amountKopecks: Long,
    val occurredAt: String, val correctionOfId: String?, val note: String?,
)
data class FinanceClosure(
    val version: Int, val incomeKopecks: Long, val expenseKopecks: Long,
    val balanceKopecks: Long, val reopenedAt: String?, val reopenReason: String?,
)

data class VisitItem(
    val id: String, val clientId: String?, val serviceId: String?,
    val startsAt: String, val occupiedUntil: String, val localTime: String, val localEnd: String, val status: String,
    val source: String, val kind: String,
    val durationMinutes: Int?, val quotedPriceKopecks: Long?,
)
data class WorkingTime(val start: String, val end: String)
data class MaterialItem(val id: String, val name: String, val unit: String, val milliunits: Long)
data class MaterialMovement(val changeMilliunits: Long, val reason: String, val createdAt: String)

val studioDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    .withResolverStyle(ResolverStyle.STRICT)

fun parseStudioDate(value: String): LocalDate? = try {
    LocalDate.parse(value, studioDateFormat)
} catch (_: java.time.format.DateTimeParseException) { null }

fun toMoscowOffset(date: String, clock: String): String =
    LocalDateTime.parse("${date}T${clock}")
        .atZone(ZoneId.of("Europe/Moscow"))
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))

fun localVisitTime(utcOrOffset: String): String =
    java.time.OffsetDateTime.parse(utcOrOffset)
        .atZoneSameInstant(ZoneId.of("Europe/Moscow"))
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

class ApiClient(private val base: String = BuildConfig.API_BASE_URL) {
    private var secureStore: SecureSessionStore? = null
    private val sessionMutex = Mutex()
    @Volatile private var currentAccess: String? = null
    var onSessionExpired: (() -> Unit)? = null

    fun attachSessionStore(store: SecureSessionStore) {
        secureStore = store
    }

    private class ApiStatusError(val status: Int) : IOException("Сервер вернул код $status")

    private suspend fun send(
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
                // Neither passwords nor refresh tokens enter exception messages.
                throw ApiStatusError(status)
            }
            if (content.isBlank()) JSONObject() else JSONObject(content)
        } finally {
            connection.disconnect()
        }
    }

    private fun acceptTokens(response: JSONObject): String {
        val access = response.getString("access_token")
        val refresh = response.getString("refresh_token")
        // Durable token first: never allow an in-memory session without its refresh credential.
        secureStore?.save(refresh)
        currentAccess = access
        return access
    }

    private suspend fun renewSession(): String? {
        val stored = secureStore?.load() ?: return null
        return try {
            acceptTokens(send("POST", "auth/refresh",
                JSONObject().put("refresh_token", stored)))
        } catch (err: ApiStatusError) {
            if (err.status == 401 || err.status == 422) {
                secureStore?.clear()
                currentAccess = null
                withContext(Dispatchers.Main) { onSessionExpired?.invoke() }
            }
            null
        } catch (_: IOException) {
            // Offline is not a logout: leave the encrypted refresh credential intact.
            null
        }
    }

    suspend fun restoreSession(): String? = sessionMutex.withLock {
        if (secureStore?.hasSession() != true) return@withLock null
        renewSession()
    }

    private suspend fun call(
        method: String, path: String, payload: JSONObject? = null, token: String? = null,
    ): JSONObject {
        val initial = if (token == null) null else currentAccess ?: token
        return try {
            send(method, path, payload, initial)
        } catch (err: ApiStatusError) {
            if (err.status != 401 || token == null) throw err
            val renewed = sessionMutex.withLock {
                // Other simultaneous calls may already have rotated this device's token.
                if (currentAccess != null && currentAccess != initial) {
                    currentAccess
                } else {
                    renewSession()
                }
            } ?: throw err
            send(method, path, payload, renewed)
        }
    }

    suspend fun logout() {
        val refresh = secureStore?.load()
        secureStore?.clear()
        currentAccess = null
        if (refresh != null) {
            try {
                send("POST", "auth/logout", JSONObject().put("refresh_token", refresh))
            } catch (_: IOException) {
                // Local sign-out is immediate; remote revocation needs a live API.
            }
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

    suspend fun financeTransactions(token: String, year: Int, month: Int): List<CashEntry> {
        val rows = call("GET", "owner/finances/transactions?year=$year&month=$month", token = token)
            .getJSONArray("entries")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CashEntry(
                row.getString("id"), row.getString("kind"), row.getString("category"),
                row.getLong("amount_kopecks"), row.getString("occurred_at"),
                if (row.isNull("correction_of_id")) null else row.getString("correction_of_id"),
                if (row.isNull("note")) null else row.getString("note"),
            )
        }
    }

    suspend fun financeClosures(token: String, year: Int, month: Int): List<FinanceClosure> {
        val rows = call("GET", "owner/finances/closures?year=$year&month=$month", token = token)
            .getJSONArray("versions")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            FinanceClosure(
                row.getInt("version"), row.getLong("income_kopecks"),
                row.getLong("expense_kopecks"), row.getLong("balance_kopecks"),
                if (row.isNull("reopened_at")) null else row.getString("reopened_at"),
                if (row.isNull("reopen_reason")) null else row.getString("reopen_reason"),
            )
        }
    }

    suspend fun closeFinanceMonth(token: String, year: Int, month: Int) {
        call("POST", "owner/finances/close",
            JSONObject().put("year", year).put("month", month), token)
    }

    suspend fun reopenFinanceMonth(token: String, year: Int, month: Int, reason: String) {
        call("POST", "owner/finances/reopen",
            JSONObject().put("year", year).put("month", month).put("reason", reason), token)
    }

    suspend fun reverseCashTransaction(token: String, transactionId: String, reason: String) {
        call("POST", "owner/finances/transactions/$transactionId/reverse",
            JSONObject().put("reason", reason), token)
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
        acceptTokens(send("POST", "auth/login", JSONObject().put("username", user)
            .put("password", password)))

    suspend fun services(token: String): List<ServiceItem> {
        val rows = call("GET", "owner/services", token = token).getJSONArray("services")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ServiceItem(
                row.getString("id"), row.getString("name"),
                row.optString("variant").takeIf { it.isNotBlank() && it != "null" },
                if (row.isNull("duration_minutes")) null else row.getInt("duration_minutes"),
                if (row.isNull("price_kopecks")) null else row.getInt("price_kopecks"),
                row.getInt("buffer_minutes"),
            )
        }
    }

    suspend fun clientHistory(token: String, clientId: String, offset: Int = 0): ClientHistoryPage {
        val result = call("GET", "owner/clients/$clientId/history?offset=$offset&limit=30", token = token)
        val array = result.getJSONArray("visits")
        val rows = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            ClientHistoryVisit(
                id = row.getString("id"),
                startsAt = row.getString("starts_at"),
                status = row.getString("status"),
                source = row.getString("source"),
                service = row.optString("service_name").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Услуга не указана",
                variant = row.optString("service_variant").takeIf { it.isNotBlank() && it != "null" },
                quotedKopecks = if (row.isNull("quoted_price_kopecks")) null
                    else row.getLong("quoted_price_kopecks"),
                receivedKopecks = row.getLong("received_kopecks"),
            )
        }
        return ClientHistoryPage(rows, result.getInt("total"), result.getBoolean("has_more"))
    }

    suspend fun updateClient(token: String, clientId: String, name: String, phone: String) {
        call("PATCH", "owner/clients/$clientId",
            JSONObject().put("name", name).put("phone", phone.ifBlank { JSONObject.NULL }), token)
    }

    suspend fun monthlyReport(token: String, year: Int, month: Int): OwnerMonthlyReport {
        val result = call("GET", "owner/reports/month?year=$year&month=$month", token = token)
        val statuses = result.getJSONObject("visits_by_status")
        val keys = statuses.keys().asSequence().toList()
        val rows = result.getJSONArray("service_breakdown")
        return OwnerMonthlyReport(
            year = result.getInt("year"), month = result.getInt("month"),
            visitsTotal = result.getInt("visits_total"),
            visitsByStatus = keys.associateWith { statuses.getInt(it) },
            completedMinutes = result.getLong("completed_minutes"),
            serviceBreakdown = (0 until rows.length()).map { i ->
                val r = rows.getJSONObject(i)
                ServiceReportRow(r.getString("name"),
                    r.optString("variant").takeIf { it.isNotBlank() && it != "null" },
                    r.getInt("completed"))
            },
            incomeKopecks = result.getLong("income_kopecks"),
            expenseKopecks = result.getLong("expense_kopecks"),
            balanceKopecks = result.getLong("balance_kopecks"),
            isClosed = result.getBoolean("is_closed"),
        )
    }

    suspend fun clients(token: String): List<ClientItem> {
        val rows: JSONArray = call("GET", "owner/clients", token = token).getJSONArray("clients")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ClientItem(row.getString("id"), row.getString("name"),
                row.optString("phone").takeIf { it.isNotBlank() && it != "null" })
        }
    }

    suspend fun visits(token: String, from: LocalDate, to: LocalDate = from.plusDays(7)): List<VisitItem> {
        val rows = call("GET", "owner/calendar?from_day=$from&to_day=$to",
            token = token).getJSONArray("entries")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            VisitItem(
                id = row.getString("id"),
                clientId = row.optString("client_id").takeIf { it.isNotBlank() && it != "null" },
                serviceId = row.optString("service_id").takeIf { it.isNotBlank() && it != "null" },
                startsAt = row.getString("starts_at"),
                occupiedUntil = row.getString("occupied_until"),
                localTime = localVisitTime(row.getString("starts_at")),
                localEnd = localVisitTime(row.getString("ends_at")),
                status = row.getString("status"),
                source = row.getString("source"),
                kind = row.getString("kind"),
                durationMinutes = if (row.isNull("duration_minutes")) null else row.getInt("duration_minutes"),
                quotedPriceKopecks = if (row.isNull("quoted_price_kopecks")) null else row.getLong("quoted_price_kopecks"),
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

    suspend fun materialHistory(token: String, id: String): List<MaterialMovement> {
        val rows = call("GET", "owner/materials/$id/movements", token = token).getJSONArray("movements")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            MaterialMovement(row.getLong("change_milliunits"), row.getString("reason"), row.getString("created_at"))
        }
    }

    suspend fun adjustStock(token: String, id: String, changeMilliunits: Long, reason: String) {
        val body = JSONObject().put("change_milliunits", changeMilliunits).put("reason", reason)
        call("POST", "owner/materials/$id/movement", body, token)
    }

    suspend fun createService(token: String, name: String, variant: String?,
                              priceKopecks: Int, duration: Int, buffer: Int) {
        val body = JSONObject()
            .put("code", "owner-" + java.util.UUID.randomUUID())
            .put("name", name).put("variant", variant ?: JSONObject.NULL)
            .put("price_kopecks", priceKopecks)
            .put("duration_minutes", duration).put("buffer_minutes", buffer)
            .put("active", true)
        call("POST", "owner/services", body, token)
    }

    suspend fun updateService(token: String, serviceId: String,
                              name: String, variant: String?,
                              priceKopecks: Int, duration: Int, buffer: Int) {
        val body = JSONObject()
            .put("name", name).put("variant", variant ?: JSONObject.NULL)
            .put("price_kopecks", priceKopecks)
            .put("duration_minutes", duration).put("buffer_minutes", buffer)
        call("PATCH", "owner/services/$serviceId", body, token)
    }
}
