package com.stailegrow.maxstrike.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.stailegrow.maxstrike.MainActivity
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.GeoAssets
import com.stailegrow.maxstrike.core.IPChecker
import com.stailegrow.maxstrike.core.SettingsStore
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
        // Без адреса/маршрута IPv6 весь IPv6-трафик уходит мимо туннеля
        // напрямую через провайдера — на IPv6-сетях (почти все 4G/5G) это
        // реальная утечка настоящего адреса и трафика в обход VPN. ULA-адрес
        // (RFC 4193) — тот же принцип, что TUN_ADDRESS для IPv4: маршрутизируем
        // только внутри собственного TUN, наружу он никуда не резолвится.
        private const val TUN_ADDRESS_V6 = "fd00:1:fd00:1::1"
        private const val TUN_PREFIX_V6 = 64
        private const val TUN_MTU = 1500
        // Публичный резолвер по умолчанию для системного маршрута DNS —
        // не то же самое, что options.routing.remoteDNS (тот может быть
        // DoH-адресом вида "https://…/dns-query", годным для Xray, но не
        // для VpnService.Builder.addDnsServer(), которому нужен голый IP).
        private const val FALLBACK_DNS_IP = "1.1.1.1"
    }

    // limitedParallelism(1): CONNECT и DISCONNECT (и onRevoke) кладутся сюда
    // независимыми launch{} — на обычном Dispatchers.IO это пул потоков, и
    // ничего не гарантирует, что стартующий startTunnel() и параллельно
    // пришедший stopTunnel() не выполнятся на разных потоках одновременно
    // (гонка за tunInterface/coreRunning, самый частый триггер — быстрый
    // повторный тап или переключение сервера во время подключения).
    // limitedParallelism(1) превращает Dispatchers.IO в очередь: следующий
    // launch не начнётся, пока не закончится (или не приостановится) текущий.
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + Job())
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
                    ConnectionManager.reportFailed(L.t("Сервис получил пустой конфиг — это баг, а не сеть.", "The service received an empty config — that's a bug, not a network issue."))
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
            // startForeground() — самым первым делом, ещё до establish()/ядра/сетевой
            // проверки IP: те шаги могут суммарно занять несколько секунд на медленной
            // сети, а пока startForeground() не вызван, сервис не защищён системой как
            // foreground и рискует быть убит агрессивной прошивкой в этом окне.
            // Уведомление обновится на "подключено" ниже, как только тоннель реально
            // заработает (внешний IP получен).
            startForeground(NOTIFICATION_ID, buildNotification(server, connecting = true))

            // Переключение сервера "на лету": ACTION_CONNECT может прийти, пока
            // предыдущий туннель ещё поднят (тот же путь тапа, что и обычное
            // подключение, — UI не шлёт ACTION_DISCONNECT перед сменой сервера).
            // Без явного teardown() тут builder.establish() ниже тихо подменил
            // бы TUN у системы, а старый ParcelFileDescriptor и старое ядро
            // остались бы висеть — утечка дескриптора и два работающих ядра
            // разом. teardown() безопасен и когда ничего не поднято (coreRunning
            // == false, tunInterface == null) — тогда это просто no-op.
            teardown()

            val dnsIP = plainIPv4(routing.remoteDNS) ?: FALLBACK_DNS_IP

            val builder = Builder()
                .setSession("Max Strike")
                .addAddress(TUN_ADDRESS, TUN_PREFIX)
                .addRoute("0.0.0.0", 0)
                .addAddress(TUN_ADDRESS_V6, TUN_PREFIX_V6)
                .addRoute("::", 0)
                .addDnsServer(dnsIP)
                .setMtu(TUN_MTU)

            // Раздельное туннелирование: приложения из списка исключений не
            // видят TUN вообще, их трафик идёт мимо VPN напрямую. Если
            // пакет успели удалить с телефона — addDisallowedApplication
            // кидает NameNotFoundException, просто пропускаем его и не
            // валим всё подключение из-за одного отсутствующего пакета.
            for (pkg in SettingsStore.excludedApps.value) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (e: PackageManager.NameNotFoundException) {
                    // пропускаем — приложения уже нет на устройстве
                }
            }

            val iface = builder.establish()
                ?: throw IllegalStateException(
                    L.t(
                        "Android не выдал TUN-интерфейс (establish() вернул null) — " +
                            "разрешение на VPN не выдано или его отозвали.",
                        "Android did not provide a TUN interface (establish() returned null) — " +
                            "the VPN permission was not granted or was revoked.",
                    ),
                )
            tunInterface = iface

            XrayCoreBridge.registerDialerController(this)
            XrayCoreBridge.setDNS(this, "$dnsIP:53")

            // GeoAssets.init() зовёт MainActivity при старте приложения —
            // сервис живёт в том же процессе, поэтому dirPath() тут уже
            // готов (либо null, если этап 4 ни разу не подключался — тогда
            // просто нет geosite:/geoip: правил, которым он был бы нужен:
            // ConnectionManager.resolveRouting() уже проверил это раньше,
            // чем стартовал сервис).
            val options = XrayConfigBuilder.Options(
                routing = routing,
                tunFileDescriptor = iface.fd,
                tunMtu = TUN_MTU,
                geoAssetsDir = GeoAssets.dirPath(),
            )
            XrayCoreBridge.runXray(XrayConfigBuilder.makeJSON(server, options))
            coreRunning = true

            // Ядро с битым конфигом падает почти сразу — даём ему шанс и
            // проверяем, что оно правда поднялось (тот же приём, что и в
            // macOS-версии, см. Core/ConnectionManager.swift).
            delay(700)
            if (!XrayCoreBridge.isRunning()) {
                throw IllegalStateException(L.t("Ядро запустилось и сразу остановилось — конфиг или сервер невалидны.", "The core started and immediately stopped — the config or server is invalid."))
            }

            // Наш процесс не исключён из VPN-маршрута, так что этот запрос
            // тоже идёт через TUN — если он прошёл, туннель реально работает,
            // а не просто "ядро запущено".
            val ip = IPChecker.externalIPBlocking()
                ?: throw IllegalStateException(
                    L.t(
                        "Ядро запустилось, но трафик через туннель не идёт — внешний IP получить не удалось.",
                        "The core started, but no traffic is going through the tunnel — the external IP could not be obtained.",
                    ),
                )

            startForeground(NOTIFICATION_ID, buildNotification(server, connecting = false))
            ConnectionManager.reportConnected(ip)
        } catch (e: Exception) {
            teardown()
            ConnectionManager.reportFailed(e.message ?: L.t("Не удалось поднять туннель.", "Could not bring up the tunnel."))
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
        // minSdk 24 уже покрывает STOP_FOREGROUND_REMOVE (добавлен в API 24) —
        // старый stopForeground(true) не нужен, версии ниже 24 не поддерживаются.
        stopForeground(STOP_FOREGROUND_REMOVE)
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

    private fun buildNotification(server: ProxyConfig, connecting: Boolean): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "Max Strike VPN", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        // Кнопка "Отключить" прямо в уведомлении — обычный запрос сервису
        // с ACTION_DISCONNECT, тот же путь, что и из интерфейса
        // (ConnectionManager.disconnect()), просто минуя Activity.
        val disconnect = PendingIntent.getService(
            this,
            0,
            Intent(this, MaxStrikeVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Max Strike")
            .setContentText(
                if (connecting) {
                    L.t("Подключение: ${server.displayName}…", "Connecting: ${server.displayName}…")
                } else {
                    L.t("Подключено: ${server.displayName}", "Connected: ${server.displayName}")
                },
            )
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, L.t("Отключить", "Disconnect"), disconnect)
            .setOngoing(true)
            .build()
    }
}
