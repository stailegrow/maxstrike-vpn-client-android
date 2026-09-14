package com.stailegrow.maxstrike.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * Простая проверка "трафик правда идёт через туннель": наш собственный
 * процесс не исключён из VPN-маршрута (addDisallowedApplication нигде не
 * зовём), поэтому этот запрос сам уезжает в TUN и возвращается настоящим
 * внешним IP только если ядро и маршрутизация реально работают. Блокирующий
 * вызов — звать только с Dispatchers.IO (см. MaxStrikeVpnService).
 *
 * Несколько сервисов подряд, а не один — MaxStrikeVpnService.startTunnel()
 * считает провал этой проверки провалом ВСЕГО подключения (ядро уже
 * запущено и реально работает, но приложение всё равно рвёт туннель и
 * показывает ошибку). Раньше единственной точкой отказа был api.ipify.org:
 * если он недоступен, залочен провайдером или отвечает с задержкой/ошибкой
 * — рабочее подключение выглядело как сломанное. Тот же принцип, что и
 * несколько User-Agent в SubscriptionFetcher.
 */
object IPChecker {
    // См. SpeedTester.USER_AGENT — та же причина: без обычного
    // браузерного User-Agent некоторые сервисы (в т.ч. за Cloudflare)
    // могут ответить 403 ещё до проверки.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    // Все четыре отдают внешний IP голым текстом, без обёртки в JSON —
    // разбирать нечего, просто trim() и есть готовый ответ.
    private val ENDPOINTS = listOf(
        "https://api.ipify.org",
        "https://icanhazip.com",
        "https://ident.me",
        "https://ifconfig.me/ip",
    )

    // Грубая, но достаточная проверка, что ответ похож на IP, а не на
    // страницу-заглушку провайдера/CDN (капча, "rate limit exceeded" и
    // т.п.) — такой текст не должен приниматься как настоящий внешний IP.
    private val IP_SHAPE = Regex("^[0-9a-fA-F.:]{3,45}$")

    fun externalIPBlocking(timeoutMs: Int = 6000): String? {
        for (endpoint in ENDPOINTS) {
            val ip = tryEndpoint(endpoint, timeoutMs)
            if (ip != null) return ip
        }
        return null
    }

    private fun tryEndpoint(endpoint: String, timeoutMs: Int): String? = try {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)
        val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
        body.takeIf { it.isNotEmpty() && IP_SHAPE.matches(it) }
    } catch (e: Exception) {
        null
    }
}
