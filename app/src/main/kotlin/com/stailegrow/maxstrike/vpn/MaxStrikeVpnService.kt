package com.stailegrow.maxstrike.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.stailegrow.maxstrike.MainActivity
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.IPChecker
import com.stailegrow.maxstrike.core.XrayConfigBuilder
import com.stailegrow.maxstrike.core.XrayCoreBridge
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.RoutingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Держит поднятый туннель: TUN-интерфейс (VpnService.Builder) снаружи и
 * ядро Xray (через XrayCoreBridge) внутри. Android-аналог связки
 * XrayProcess + SystemProxy с macOS, только оба слились в один компонент,
 * потому что система управления туннелем тут ровно одна — сам VpnService.
 *
 * libXray.DialerController реализует сам сервис (не отдельный класс),
 * потому что protect() — метод экземпляра VpnService, его больше неоткуда
 * вызвать.
 */
class MaxStrikeVpnService : VpnService(), libXray.DialerController {

    companion object {
        const val ACTION_CONNECT = "com.stailegrow.maxstrike.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.stailegrow.maxstrike.vpn.DISCONNECT"
        const val EXTRA_SERVER = "server"
        const val EXTRA_ROUTING = "routing"

        private const val NOTIFICATION_CHANNEL_ID = "maxstrike-vpn"
        private const val NOTIFICATION_ID = 1

        private const val TUN_ADDRESS = "10.10.14.1"
        private const val TUN_PREFIX = 30
        private const val TUN_MTU = 1500
        // Публичный резолвер по умолчанию для системного маршрута DNS —
        // не то же самое, что options.routing.remoteDNS (тот может быть
        // DoH-адресом вида "https://…/dns-query", годным для Xray, но не
        // для VpnService.Builder.addDnsServer(), которому нужен голый IP).
        private const val FALLBACK_DNS_IP = "1.1.1.1"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var tunInterface: ParcelFileDescriptor? = null
    private var coreRunning = false

    // gomobile переводит Go-тип int в Kotlin как Long (не Int) — сам
    // адрес protect() в Android-API остаётся Int, поэтому toInt() ниже.
    override fun protectFd(fd: Long): Boolean = protect(fd.toInt())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                @Suppress("DEPRECATION")
                val server = intent.getSerializableExtra(EXTRA_SERVER) as? ProxyConfig
                @Suppress("DEPRECATION")
                val routing = intent.getSerializableExtra(EXTRA_ROUTING) as? RoutingConfig
                if (server == null || routing == null) {
                    ConnectionManager.reportFailed("Сервис получил пустой конфиг — это баг, а не сеть.")
                    stopSelf()
                } else {
                    scope.launch { startTunnel(server, routing) }
                }
            }

            ACTION_DISCONNECT -> scope.launch { stopTunnel() }
        }
        return START_NOT_STICKY
    }

    private suspend fun startTunnel(server: ProxyConfig, routing: RoutingConfig) {
        try {
            val dnsIP = plainIPv4(routing.remoteDNS) ?: FALLBACK_DNS_IP

            val iface = Builder()
                .setSession("Max Strike")
                .addAddress(TUN_ADDRESS, TUN_PREFIX)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dnsIP)
                .setMtu(TUN_MTU)
                .establish()
                ?: throw IllegalStateException(
                    "Android не выдал TUN-интерфейс (establish() вернул null) — " +
                        "разрешение на VPN не выдано или его отозвали.",
                )
            tunInterface = iface

            XrayCoreBridge.registerDialerController(this)
            XrayCoreBridge.setDNS(this, "$dnsIP:53")

            val options = XrayConfigBuilder.Options(
                routing = routing,
                tunFileDescriptor = iface.fd,
                tunMtu = TUN_MTU,
            )
            XrayCoreBridge.runXray(XrayConfigBuilder.makeJSON(server, options))
            coreRunning = true

            // Ядро с битым конфигом падает почти сразу — даём ему шанс и
            // проверяем, что оно правда поднялось (тот же приём, что и в
            // macOS-версии, см. Core/ConnectionManager.swift).
            delay(700)
            if (!XrayCoreBridge.isRunning()) {
                throw IllegalStateException("Ядро запустилось и сразу остановилось — конфиг или сервер невалидны.")
            }

            // Наш процесс не исключён из VPN-маршрута, так что этот запрос
            // тоже идёт через TUN — если он прошёл, туннель реально работает,
            // а не просто "ядро запущено".
            val ip = IPChecker.externalIPBlocking()
                ?: throw IllegalStateException(
                    "Ядро запустилось, но трафик через туннель не идёт — внешний IP получить не удалось.",
                )

            startForeground(NOTIFICATION_ID, buildNotification(server))
            ConnectionManager.reportConnected(ip)
        } catch (e: Exception) {
            teardown()
            ConnectionManager.reportFailed(e.message ?: "Не удалось поднять туннель.")
            stopSelf()
        }
    }

    private suspend fun stopTunnel() {
        teardown()
        ConnectionManager.reportDisconnected()
        stopSelf()
    }

    private fun teardown() {
        if (coreRunning) {
            runCatching { XrayCoreBridge.stopXray() }
            runCatching { XrayCoreBridge.resetDNS() }
            coreRunning = false
        }
        runCatching { tunInterface?.close() }
        tunInterface = null
        @Suppress("DEPRECATION")
        stopForeground(true)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Пользователь отозвал разрешение на VPN в системных настройках —
        // приходит без ACTION_DISCONNECT, обрабатываем так же.
        scope.launch { stopTunnel() }
        super.onRevoke()
    }

    private fun plainIPv4(value: String): String? {
        val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        return value.takeIf { ipv4.matches(it) }
    }

    private fun buildNotification(server: ProxyConfig): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "Max Strike VPN", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Max Strike")
            .setContentText("Подключено: ${server.displayName}")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }
}
