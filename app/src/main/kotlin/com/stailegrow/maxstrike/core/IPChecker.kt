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
    fun externalIPBlocking(timeoutMs: Int = 6000): String? = try {
        val connection = URL("https://api.ipify.org").openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
