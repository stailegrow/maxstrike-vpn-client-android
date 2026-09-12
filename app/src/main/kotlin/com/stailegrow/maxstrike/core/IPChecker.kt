package com.stailegrow.maxstrike.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * Простая проверка "трафик правда идёт через туннель": наш собственный
 * процесс не исключён из VPN-маршрута (addDisallowedApplication нигде не
 * зовём), поэтому этот запрос сам уезжает в TUN и возвращается настоящим
 * внешним IP только если ядро и маршрутизация реально работают. Блокирующий
 * вызов — звать только с Dispatchers.IO (см. MaxStrikeVpnService).
 */
object IPChecker {
    // См. SpeedTester.USER_AGENT — та же причина: без обычного
    // браузерного User-Agent некоторые сервисы (в т.ч. за Cloudflare)
    // могут ответить 403 ещё до проверки — а от этого запроса зависит
    // вообще любое успешное подключение (см. MaxStrikeVpnService).
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    fun externalIPBlocking(timeoutMs: Int = 6000): String? = try {
        val connection = URL("https://api.ipify.org").openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
