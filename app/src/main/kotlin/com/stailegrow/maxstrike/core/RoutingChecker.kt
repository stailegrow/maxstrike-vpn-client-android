package com.stailegrow.maxstrike.core

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Живая проверка маршрутизации — не просто "ядро запущено", а видно ли
 * снаружи, что трафик действительно идёт так, как выбрано в настройках.
 * Как и IPChecker, пользуется тем, что процесс приложения не исключён из
 * VPN-маршрута: запросы отсюда идут тем же путём, что и любой другой
 * трафик приложения, через реальную маршрутизацию ядра Xray.
 *
 * Второй тест (домашний сайт) — честная эвристика, а не точный
 * трассировщик: он смотрит, доступен ли ya.ru и с какой задержкой, а не
 * какой именно исходящий ядро для него выбрало. Показывается только для
 * пресета "Обход РФ" — ya.ru входит в geosite:category-ru, который в этом
 * пресете идёт напрямую.
 */
object RoutingChecker {

    // См. SpeedTester.USER_AGENT / IPChecker.USER_AGENT.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    class RoutingCheckException(message: String) : Exception(message)

    data class Result(
        val externalIP: String,
        val country: String?,
        val domesticSite: String?,
        val domesticReachable: Boolean?,
        val domesticLatencyMs: Long?,
    )

    fun checkBlocking(presetID: String): Result {
        val ip = IPChecker.externalIPBlocking()
            ?: throw RoutingCheckException(L.t("Внешний IP не получен — туннель не отвечает.", "External IP not obtained — the tunnel isn't responding."))
        val country = countryFor(ip)

        var domesticSite: String? = null
        var domesticReachable: Boolean? = null
        var domesticLatencyMs: Long? = null

        if (presetID == "bypass-ru") {
            domesticSite = "ya.ru"
            val start = System.nanoTime()
            domesticReachable = try {
                val connection = URL("https://$domesticSite").openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val code = connection.responseCode
                connection.disconnect()
                code in 200..399
            } catch (e: Exception) {
                false
            }
            domesticLatencyMs = (System.nanoTime() - start) / 1_000_000
        }

        return Result(ip, country, domesticSite, domesticReachable, domesticLatencyMs)
    }

    // ip-api.com отдаёт геолокацию по IP без ключа, но только по HTTP —
    // это не приватные данные (просто страна нашего же VPN-выхода), так
    // что открытый запрос тут не страшен.
    private fun countryFor(ip: String): String? = try {
        val connection = URL("http://ip-api.com/json/$ip?fields=status,country").openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val json = JSONObject(text)
        if (json.optString("status") == "success") json.optString("country").takeIf { it.isNotBlank() } else null
    } catch (e: Exception) {
        null
    }
}
