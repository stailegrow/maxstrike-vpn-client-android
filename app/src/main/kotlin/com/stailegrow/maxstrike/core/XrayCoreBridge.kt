package com.stailegrow.maxstrike.core

import org.json.JSONObject

/**
 * Единственное место в проекте, которое напрямую знает про Java API,
 * который gomobile генерирует из libXray (app/libs/libXray.aar — его
 * кладёт туда Scripts/build-libxray.sh). Весь остальной код обращается
 * только к XrayCoreBridge и о самом gomobile не знает.
 *
 * Достоверно (взято из исходников android_wrapper.go/invoke.go библиотеки
 * XTLS/libXray): Go-пакет называется libXray, в нём есть
 * Invoke(string) string, интерфейс DialerController с методом
 * ProtectFd(int) bool, SetDNS/ResetDNS, RegisterDialerController,
 * RegisterListenerController — сигнатуры ниже им соответствуют.
 *
 * Не проверено, потому что gomobile решает это только во время сборки:
 * точные имена Java-класса/пакета (ниже — libXray.LibXray и
 * libXray.DialerController, как в README самой libXray) и то, что gomobile
 * понижает первую букву у методов (ProtectFd -> protectFd). Если после
 * Scripts/build-libxray.sh Android Studio подчеркнёт импорт красным —
 * открой libXray.aar в Project view (или автокомплит по "libXray.") и
 * поправь только этот файл и MaxStrikeVpnService.protectFd — остального
 * кода это не касается.
 */
object XrayCoreBridge {

    class CoreException(message: String) : Exception(message)

    private fun invoke(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", 3)
            .put("method", method)
            .put("payload", payload)
        val responseText = libXray.LibXray.invoke(request.toString())
        return JSONObject(responseText)
    }

    private fun JSONObject.dataOrThrow(): JSONObject {
        if (!optBoolean("success", false)) {
            val message = optString("error").ifEmpty { "Xray вернул ошибку без описания." }
            throw CoreException(message)
        }
        return optJSONObject("data") ?: JSONObject()
    }

    /** Запускает ядро с готовым JSON-конфигом (см. XrayConfigBuilder). */
    fun runXray(configJson: String) {
        invoke("runXray", JSONObject().put("xrayJson", configJson)).dataOrThrow()
    }

    fun stopXray() {
        invoke("stopXray", JSONObject()).dataOrThrow()
    }

    /** true, пока ядро поднято. */
    fun isRunning(): Boolean =
        invoke("getXrayState", JSONObject()).dataOrThrow().optBoolean("running", false)

    fun xrayVersion(): String =
        invoke("xrayVersion", JSONObject()).dataOrThrow().optString("version", "")

    /** Вызывать до runXray: направляет Go-резолвер ядра на этот DNS и
     *  защищает его сокет от собственного же VPN-туннеля (иначе DNS-запросы
     *  ядра сами попадут в TUN и получится петля). server — вида "IP:port". */
    fun setDNS(controller: libXray.DialerController, server: String) {
        libXray.LibXray.setDNS(controller, server)
    }

    /** Вызывать после stopXray. */
    fun resetDNS() {
        libXray.LibXray.resetDNS()
    }

    /** Защищает исходящие сокеты самого ядра (соединение к настоящему
     *  VPN-серверу) от попадания обратно в TUN — без этого тоже петля. */
    fun registerDialerController(controller: libXray.DialerController) {
        libXray.LibXray.registerDialerController(controller)
        libXray.LibXray.registerListenerController(controller)
    }
}
