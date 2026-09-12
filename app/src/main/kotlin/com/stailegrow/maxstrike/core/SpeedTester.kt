package com.stailegrow.maxstrike.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * Замер скорости — карточка "// СКОРОСТЬ" на главном экране. Тот же
 * принцип, что у IPChecker: наш процесс не исключён из VPN-маршрута, так
 * что обычный HTTP-запрос из приложения сам едет через TUN, пока туннель
 * поднят — никакого отдельного сокета на socksPort/httpPort заводить не
 * нужно.
 *
 * Источник — публичный тестовый файл Hetzner (fsn1-speed.hetzner.com,
 * датацентр Falkenstein), которым годами открыто пользуются сторонние
 * спидтесты. Изначально брали официальный эндпоинт Cloudflare
 * (speed.cloudflare.com) — он честнее подходит под задачу (отдаёт ровно
 * нужное число байт вместо целого файла), но стабильно отвечал 403 даже
 * после обычного браузерного User-Agent. Раз дело не в заголовках,
 * вероятнее всего Cloudflare блокирует сам IP VPN-сервера как
 * хостинговый/датацентровый — это защита от ботов на их стороне, с
 * клиента это не обойти. Простой файл-хостинг без такой защиты —
 * надёжнее для трафика через прокси. (Первая попытка перейти на
 * speed.hetzner.de — не тот адрес, такого хоста не существует вовсе,
 * отсюда и "No address associated with hostname"; правильные адреса —
 * с префиксом дата-центра, вида fsn1-speed.hetzner.com.)
 *
 * Файл целиком — 100 МБ, качать его весь не нужно: читаем, пока не
 * наберём достаточно данных или не кончится MAX_DURATION_MS, что
 * раньше, и просто прерываем поток.
 *
 * Блокирующий вызов — звать только с Dispatchers.IO.
 */
object SpeedTester {
    private const val TEST_URL = "https://fsn1-speed.hetzner.com/100MB.bin"
    private const val MAX_DURATION_MS = 10_000L
    // Через VPN-туннель (Reality/XHTTP поверх TLS, да ещё и с самим
    // подключением к серверу до кучи) до первого байта уходит заметно
    // больше времени, чем при прямом соединении без VPN — 8 секунд были
    // слишком жёстким таймаутом и на не самой быстрой сети роняли замер
    // ещё до того, как он успевал толком начаться.
    private const val TIMEOUT_MS = 15_000

    // Нашли причину «сервер вернул код 403»: HttpURLConnection по
    // умолчанию шлёт заголовок вида "User-Agent: Java/17.0.2" — Cloudflare
    // (как и многие другие WAF) блокирует именно такую сигнатуру как
    // подозрительную, ещё до того как запрос вообще доходит до раздачи
    // тестовых байт. Обычный браузерный User-Agent решает проблему.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    class SpeedTestException(message: String) : Exception(message)

    data class Result(val mbps: Double)

    fun measureBlocking(): Result {
        val connection = try {
            URL(TEST_URL).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SpeedTestException(L.t("Неверный адрес теста.", "Invalid test address."))
        }
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)

        try {
            // Раньше ошибка соединения/таймаут здесь вообще ничем не
            // ловились — наружу уходило сырое исключение вместо понятного
            // сообщения (или замер просто выглядел как «не работает»).
            val code = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                throw SpeedTestException(L.t("Не удалось подключиться к серверу замера (${e.message ?: "таймаут"}).", "Could not connect to the test server (${e.message ?: "timeout"})."))
            }
            if (code !in 200..299) throw SpeedTestException(L.t("Сервер вернул код $code.", "The server returned code $code."))

            val start = System.nanoTime()
            var totalBytes = 0L
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val elapsedMs = (System.nanoTime() - start) / 1_000_000
                        if (elapsedMs > MAX_DURATION_MS) break
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalBytes += read
                    }
                }
            } catch (e: java.io.IOException) {
                // Обрыв/таймаут посреди скачивания — если что-то уже успело
                // прийти, считаем замер по тому, что есть, вместо того чтобы
                // ронять весь результат из-за одного сбойного чтения.
                if (totalBytes <= 0) {
                    throw SpeedTestException(L.t("Соединение оборвалось раньше, чем пришли данные (${e.message ?: "таймаут"}).", "The connection dropped before any data arrived (${e.message ?: "timeout"})."))
                }
            }
            val elapsedSec = (System.nanoTime() - start) / 1_000_000_000.0
            if (totalBytes <= 0 || elapsedSec <= 0.0) {
                throw SpeedTestException(L.t("Пришло 0 байт — проверь соединение.", "0 bytes arrived — check the connection."))
            }

            val mbps = (totalBytes * 8) / elapsedSec / 1_000_000.0
            return Result(mbps)
        } finally {
            connection.disconnect()
        }
    }
}
