package com.stailegrow.maxstrike.model

import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Подписка — источник, из которого список серверов подтягивается целиком.
 * Android-аналог Models/Subscription.swift.
 */
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    // 0 — обновлять только вручную.
    val updateIntervalHours: Double = 12.0,
    val lastUpdated: Long? = null,
    val lastError: String? = null,
    val announce: String? = null,
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAt: Long? = null,
    // Сырое значение заголовка subscription-userinfo — панели врут
    // по-разному, без исходной строки расхождения не разобрать.
    val rawUserInfo: String? = null,
) {
    val displayName: String
        get() = name.ifEmpty {
            try { URI(url).host } catch (e: Exception) { null } ?: "Подписка"
        }

    val isExpired: Boolean
        get() = expiresAt?.let { it < System.currentTimeMillis() } ?: false

    /** «12,4 ГБ из 100 ГБ» либо «12,4 ГБ из ∞». total = 0 у панелей значит безлимит. */
    val trafficSummary: String?
        get() {
            val used = usedBytes ?: return null
            val usedText = formatBytes(used)
            val total = totalBytes
            return if (total == null || total <= 0) "$usedText из ∞" else "$usedText из ${formatBytes(total)}"
        }

    /** Доля израсходованного, 0…1. null при безлимите. */
    val trafficFraction: Double?
        get() {
            val used = usedBytes ?: return null
            val total = totalBytes ?: return null
            if (total <= 0) return null
            return (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        }

    /** «до 5 сентября 2026» / «истекла 5 сентября 2026» — Android-аналог
     *  expirySummary из Subscription.swift. null, если панель не прислала
     *  срок действия. */
    val expirySummary: String?
        get() {
            val at = expiresAt ?: return null
            val formatter = SimpleDateFormat("d MMMM yyyy", Locale("ru", "RU"))
            return formatter.format(Date(at))
        }

    /** Пора ли обновлять по расписанию. */
    fun isDue(now: Long = System.currentTimeMillis()): Boolean {
        if (updateIntervalHours <= 0) return false
        val last = lastUpdated ?: return true
        return (now - last) >= updateIntervalHours * 3600_000.0
    }

    fun toJSON(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("updateIntervalHours", updateIntervalHours)
        lastUpdated?.let { put("lastUpdated", it) }
        lastError?.let { put("lastError", it) }
        announce?.let { put("announce", it) }
        usedBytes?.let { put("usedBytes", it) }
        totalBytes?.let { put("totalBytes", it) }
        expiresAt?.let { put("expiresAt", it) }
        rawUserInfo?.let { put("rawUserInfo", it) }
    }

    companion object {
        fun fromJSON(json: JSONObject): Subscription = Subscription(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", ""),
            url = json.optString("url", ""),
            updateIntervalHours = json.optDouble("updateIntervalHours", 12.0),
            lastUpdated = if (json.has("lastUpdated")) json.getLong("lastUpdated") else null,
            lastError = if (json.has("lastError")) json.getString("lastError") else null,
            announce = if (json.has("announce")) json.getString("announce") else null,
            usedBytes = if (json.has("usedBytes")) json.getLong("usedBytes") else null,
            totalBytes = if (json.has("totalBytes")) json.getLong("totalBytes") else null,
            expiresAt = if (json.has("expiresAt")) json.getLong("expiresAt") else null,
            rawUserInfo = if (json.has("rawUserInfo")) json.getString("rawUserInfo") else null,
        )

        fun formatBytes(value: Long): String {
            val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
            if (value < 1024) return "$value ${units[0]}"
            var v = value.toDouble()
            var unitIndex = 0
            while (v >= 1024 && unitIndex < units.size - 1) {
                v /= 1024
                unitIndex++
            }
            return String.format(Locale.US, "%.1f %s", v, units[unitIndex])
        }
    }
}
