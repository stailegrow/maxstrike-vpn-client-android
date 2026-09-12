package com.stailegrow.maxstrike.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Базы geosite.dat и geoip.dat — по ним ядро понимает, что такое
 * «российский сайт» или «реклама». Android-аналог Core/GeoAssets.swift.
 *
 * На маке путь к папке ядру отдают через настоящую переменную окружения
 * процесса (XRAY_LOCATION_ASSET) при запуске xray как отдельного бинарника.
 * На Android ядро линкуется в приложение (gomobile), отдельного процесса
 * нет — вместо этого путь пишется в корневой "env" самого JSON-конфига
 * (см. XrayConfigBuilder — geoAssetsDir), который Xray-core сам применяет
 * через os.Setenv() при разборе конфига, раньше, чем строит routing/dns —
 * тот же приём, что уже работает для xray.tun.fd.
 */
object GeoAssets {

    // Сборка hydraponique — та же, что использует macOS-версия: в ней есть
    // категории category-ru, whitelist и остальные из пресета «Обход РФ».
    const val DEFAULT_GEOSITE_URL =
        "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geosite@202604152235/release/geosite.dat"
    const val DEFAULT_GEOIP_URL =
        "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geoip@202604160537/release/geoip.dat"

    class FetchException(message: String) : Exception(message)

    // Оборвавшаяся закачка иначе выглядит как успешная, а ядро падает на
    // обрезанном файле при старте.
    private const val MINIMUM_SIZE = 50 * 1024L
    private const val REFRESH_INTERVAL_MS = 6 * 3600_000L

    private var dir: File? = null

    /** Готовность баз для экрана "Настройки" (// МАРШРУТИЗАЦИЯ):
     *  размер на диске и когда обновлялись в последний раз. */
    data class Status(val ready: Boolean, val sizeBytes: Long, val updatedAtMillis: Long?)

    private val _status = MutableStateFlow(Status(ready = false, sizeBytes = 0L, updatedAtMillis = null))
    val status: StateFlow<Status> = _status.asStateFlow()

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "geo")
        refreshStatus()
    }

    private fun refreshStatus() {
        val d = dir ?: return
        val g1 = geosite(d)
        val g2 = geoip(d)
        val size = (if (g1.exists()) g1.length() else 0L) + (if (g2.exists()) g2.length() else 0L)
        _status.value = Status(ready = isReady(), sizeBytes = size, updatedAtMillis = lastUpdatedMs())
    }

    /** Путь к папке с базами — то, что уезжает в "env" Xray-конфига. Готова
     *  ли сама папка, решает isReady(); путь безопасно отдавать всегда. */
    fun dirPath(): String? = dir?.absolutePath

    private fun geosite(d: File): File = File(d, "geosite.dat")
    private fun geoip(d: File): File = File(d, "geoip.dat")

    private fun isValid(file: File): Boolean = file.exists() && file.length() >= MINIMUM_SIZE

    fun isReady(): Boolean {
        val d = dir ?: return false
        return isValid(geosite(d)) && isValid(geoip(d))
    }

    private fun lastUpdatedMs(): Long? {
        val d = dir ?: return null
        val files = listOf(geosite(d), geoip(d)).filter { it.exists() }
        if (files.size < 2) return null
        return files.minOf { it.lastModified() }
    }

    fun isStale(): Boolean {
        if (!isReady()) return true
        val updated = lastUpdatedMs() ?: return true
        return System.currentTimeMillis() - updated > REFRESH_INTERVAL_MS
    }

    /** Тихое обновление при запуске: ошибку показывать некому и незачем —
     *  если базы не обновились, работают прежние. Звать только с
     *  Dispatchers.IO. */
    fun refreshIfStaleBlocking(geositeURL: String, geoipURL: String) {
        if (!isStale()) return
        try {
            downloadBlocking(geositeURL, geoipURL)
        } catch (e: Exception) {
            // Тихо — тот же повод, что и на маке.
        }
    }

    /** Блокирующий вызов — звать только с Dispatchers.IO. */
    fun downloadBlocking(geositeURL: String, geoipURL: String) {
        val d = dir ?: throw FetchException(L.t("GeoAssets не инициализирован.", "GeoAssets not initialized."))
        d.mkdirs()
        try {
            fetch(geositeURL, geosite(d), "geosite.dat")
            fetch(geoipURL, geoip(d), "geoip.dat")
        } finally {
            // Даже если один из двух файлов не докачался — статус должен
            // отразить то, что реально лежит на диске сейчас.
            refreshStatus()
        }
    }

    private fun fetch(address: String, destination: File, name: String) {
        val url = try {
            URL(address)
        } catch (e: Exception) {
            throw FetchException(L.t("Неверная ссылка на $name.", "Bad link for $name."))
        }

        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = true

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw FetchException(L.t("Сервер вернул код $code на $name.", "The server returned code $code for $name."))

            // Пишем через временный файл: оборванная замена не должна
            // оставить на месте рабочей базы огрызок, на котором ядро не
            // поднимется.
            val staging = File(destination.parentFile, "${destination.name}.new")
            connection.inputStream.use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }

            if (staging.length() < MINIMUM_SIZE) {
                staging.delete()
                throw FetchException(L.t("Файл $name пришёл обрезанным (${staging.length()} байт).", "File $name arrived truncated (${staging.length()} bytes)."))
            }

            if (destination.exists()) destination.delete()
            if (!staging.renameTo(destination)) {
                throw FetchException(L.t("Не удалось сохранить $name на диск.", "Could not save $name to disk."))
            }
        } finally {
            connection.disconnect()
        }
    }
}
