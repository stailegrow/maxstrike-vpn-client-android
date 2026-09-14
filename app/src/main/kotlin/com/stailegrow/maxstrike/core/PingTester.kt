package com.stailegrow.maxstrike.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Замер задержки до сервера обычным TCP-подключением — Android-аналог
 * Core/PingTester.swift.
 *
 * Это не ICMP-пинг: меряем время до установления TCP-соединения с портом
 * сервера. Для прокси такой замер честнее — он проверяет ровно тот порт,
 * через который пойдёт трафик, и не требует специальных прав.
 */
object PingTester {

    /**
     * Одиночный замер врёт: в первую попытку попадает резолв имени, а
     * параллельные замеры мешают друг другу. Поэтому греем соединение,
     * делаем несколько попыток и берём медиану — минимум слишком
     * чувствителен к единственному ложному (заниженному) замеру.
     */
    suspend fun latency(
        host: String,
        port: Int,
        samples: Int = 3,
        timeoutMs: Int = 4000,
        warmup: Boolean = true,
    ): Int? =
        withContext(Dispatchers.IO) {
            if (warmup) attempt(host, port, timeoutMs) // прогрев, результат выбрасываем

            val values = mutableListOf<Int>()
            val total = maxOf(1, samples)
            for (index in 0 until total) {
                val value = attempt(host, port, timeoutMs)
                if (value != null && value >= 1) values.add(value)
                if (index < total - 1) delay(120)
            }
            median(values)
        }

    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private suspend fun attempt(host: String, port: Int, timeoutMs: Int): Int? =
        withTimeoutOrNull(timeoutMs.toLong() + 300) {
            val started = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                ((System.nanoTime() - started) / 1_000_000L).toInt()
            } catch (e: Exception) {
                null
            }
        }
}

/** Как красить значение задержки. */
enum class PingQuality {
    GOOD, FAIR, POOR;

    companion object {
        fun of(ms: Int): PingQuality = when {
            ms < 120 -> GOOD
            ms < 250 -> FAIR
            else -> POOR
        }
    }
}
