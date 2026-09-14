package com.stailegrow.maxstrike.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Загрузка и разбор подписки — Android-аналог Core/SubscriptionFetcher.swift.
 *
 * Панели отдают список по-разному: то простым текстом, то base64 целиком, то
 * url-safe base64 без выравнивания. Часть панелей смотрит на User-Agent и
 * незнакомому клиенту подсовывает формат другого клиента — поэтому при
 * пустом результате пробуем ещё раз под распространёнными именами.
 */
object SubscriptionFetcher {

    data class Payload(
        val links: List<String> = emptyList(),
        val title: String? = null,
        val announce: String? = null,
        val updateIntervalHours: Double? = null,
        val usedBytes: Long? = null,
        val totalBytes: Long? = null,
        val expiresAt: Long? = null,
        val rawUserInfo: String? = null,
    )

    class FetchException(message: String) : Exception(message)

    private val userAgents = listOf(
        "MaxStrike/1.0",
        "v2rayNG/1.9.5",
        "Happ/1.0",
    )

    /** Блокирующий вызов — звать только с Dispatchers.IO. */
    fun fetch(urlString: String, timeoutMs: Int = 20000): Payload {
        val trimmed = urlString.trim()
        val url = try {
            URL(trimmed)
        } catch (e: Exception) {
            throw FetchException(L.t("Это не похоже на ссылку подписки.", "This does not look like a subscription link."))
        }
        val scheme = url.protocol?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw FetchException(L.t("Это не похоже на ссылку подписки.", "This does not look like a subscription link."))
        }
        // Ссылка на подписку приходит от пользователя (вставлена руками или
        // из QR-кода — а его мог сделать кто угодно). Без этой проверки
        // приложение послушно сходило бы HTTP-запросом на локальный роутер,
        // 169.254.x.x или другой адрес в собственной сети телефона (SSRF) —
        // резолвим хост и отбрасываем сразу все приватные/loopback/link-local
        // адреса, а не только литеральные IP в самой ссылке (иначе домен
        // вида "attacker.example" с A-записью на 192.168.1.1 прошёл бы мимо
        // проверки).
        rejectPrivateHost(url.host)

        var lastFailure: FetchException? = null
        for (agent in userAgents) {
            try {
                val payload = load(url, agent, timeoutMs)
                if (payload.links.isNotEmpty()) return payload
                lastFailure = FetchException(L.t("Подписка ответила, но ни одной ссылки в ответе нет.", "The subscription answered, but the reply holds no links."))
            } catch (e: FetchException) {
                lastFailure = e
            } catch (e: Exception) {
                lastFailure = FetchException(e.message ?: L.t("Не удалось загрузить подписку.", "Could not load the subscription."))
            }
        }
        throw lastFailure ?: FetchException(L.t("Не удалось загрузить подписку.", "Could not load the subscription."))
    }

    /** Резолвит хост и отбрасывает адреса из приватных/loopback/link-local
     *  диапазонов (127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16,
     *  169.254.0.0/16, ::1, fc00::/7, fe80::/10 и т.п. — ровно то же, что
     *  XrayConfigBuilder.privateRanges относит к локальной сети). Если хост
     *  не резолвится вовсе — это отдельная сетевая ошибка, пусть с ней
     *  разбирается сам load() ниже, тут её не перехватываем. */
    private fun rejectPrivateHost(host: String) {
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            return
        }
        for (address in addresses) {
            if (address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isAnyLocalAddress
            ) {
                throw FetchException(
                    L.t(
                        "Ссылки на локальную сеть не поддерживаются.",
                        "Links to the local network are not supported.",
                    ),
                )
            }
        }
    }

    private fun load(url: URL, userAgent: String, timeoutMs: Int): Payload {
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw FetchException(L.t("Сервер подписки ответил кодом $code.", "The subscription server replied with code $code."))

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            var payload = Payload(links = decodeBody(body))
            payload = payload.copy(
                title = header(connection, "profile-title")?.let(::decodeHeaderValue),
                announce = header(connection, "announce")?.let(::decodeHeaderValue),
            )
            header(connection, "subscription-userinfo")?.let { info ->
                val (used, total, expiresAt) = parseUserInfo(info)
                payload = payload.copy(rawUserInfo = info, usedBytes = used, totalBytes = total, expiresAt = expiresAt)
            }
            header(connection, "profile-update-interval")?.toDoubleOrNull()?.let { days ->
                if (days > 0) payload = payload.copy(updateIntervalHours = days * 24)
            }
            return payload
        } finally {
            connection.disconnect()
        }
    }

    private fun header(connection: HttpURLConnection, name: String): String? {
        for (key in connection.headerFields.keys) {
            if (key != null && key.equals(name, ignoreCase = true)) {
                val value = connection.getHeaderField(key)?.trim()
                if (!value.isNullOrEmpty()) return value
            }
        }
        return null
    }

    // MARK: - Разбор тела

    fun decodeBody(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.contains("://")) return links(trimmed)
        decodeBase64(trimmed)?.let { return links(it) }
        return emptyList()
    }

    private fun links(text: String): List<String> =
        text.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("://") }

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * Своя реализация, а не java.util.Base64 (API 26+) или android.util.Base64
     * (недоступен в чистых JVM юнит-тестах) — так работает одинаково и на
     * minSdk 24, и под JUnit без Robolectric.
     */
    fun decodeBase64(value: String): String? {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')
            .filterNot { it.isWhitespace() }
            .trimEnd('=')
        if (normalized.isEmpty() || normalized.any { it !in BASE64_ALPHABET }) return null

        val output = ByteArrayOutputStream()
        var buffer = 0
        var bitsCollected = 0
        for (c in normalized) {
            buffer = (buffer shl 6) or BASE64_ALPHABET.indexOf(c)
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                output.write((buffer shr bitsCollected) and 0xFF)
            }
        }
        return try {
            String(output.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** Заголовки с не-ASCII панели присылают как `base64:<...>`. */
    fun decodeHeaderValue(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.lowercase().startsWith("base64:")) {
            val payload = trimmed.substring("base64:".length)
            return decodeBase64(payload) ?: payload
        }
        return trimmed.ifEmpty { null }
    }

    /** `upload=1; download=2; total=3; expire=1767225600` -> (used, total, expiresAtMillis) */
    fun parseUserInfo(value: String): Triple<Long?, Long?, Long?> {
        val fields = mutableMapOf<String, Long>()
        for (pair in value.split(";")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size != 2) continue
            val key = parts[0].trim().lowercase()
            parts[1].trim().toLongOrNull()?.let { fields[key] = it }
        }
        val hasTraffic = fields.containsKey("upload") || fields.containsKey("download")
        val used = if (hasTraffic) (fields["upload"] ?: 0) + (fields["download"] ?: 0) else null
        val total = fields["total"]
        // expire=0 у панелей значит «бессрочно», а не 1970 год.
        val expiresAt = fields["expire"]?.takeIf { it > 0 }?.let { it * 1000 }
        return Triple(used, total, expiresAt)
    }
}
