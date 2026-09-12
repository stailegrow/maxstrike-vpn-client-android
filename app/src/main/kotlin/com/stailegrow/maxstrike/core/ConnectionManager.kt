package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.RoutingConfig
import com.stailegrow.maxstrike.model.RoutingPreset
import com.stailegrow.maxstrike.vpn.MaxStrikeVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Failed(val message: String) : ConnectionState()

    val isBusy: Boolean get() = this is Connecting
    val isConnected: Boolean get() = this is Connected
}

/**
 * Стейт-машина подключения — Android-аналог Core/ConnectionManager.swift.
 *
 * На macOS туннель — это системный SOCKS/HTTP-прокси, который включает и
 * выключает сам ConnectionManager напрямую. На Android туннель — это
 * VpnService: системный компонент со своим жизненным циклом, поднять и
 * уронить который можно только интентами. Поэтому здесь ConnectionManager
 * только просит систему запустить/остановить MaxStrikeVpnService, а
 * фактический прогресс ("поднялось", "вот внешний IP", "упало с ошибкой")
 * сервис репортит сюда сам вызовами reportConnected/reportFailed/
 * reportDisconnected — оба живут в одном процессе приложения (отдельный
 * :vpn-процесс не заводим, он был бы избыточен для одного соединения),
 * так что это обычный прямой вызов, без Binder/Messenger.
 *
 * Экран, как и на macOS, знает только про state/activeServer/externalIP —
 * ни про Intent, ни про VpnService он не в курсе.
 */
object ConnectionManager {

    // Нужен, чтобы connect() мог дождаться скачивания geo-баз (если они
    // нужны выбранному пресету) прежде, чем стартовать сервис, но при этом
    // сам оставаться обычной функцией, а не suspend — вызывающие места
    // (MainActivity) остаются простыми колбэками ActivityResult.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _activeServer = MutableStateFlow<ProxyConfig?>(null)
    val activeServer: StateFlow<ProxyConfig?> = _activeServer.asStateFlow()

    private val _externalIP = MutableStateFlow<String?>(null)
    val externalIP: StateFlow<String?> = _externalIP.asStateFlow()

    // Long (epoch-миллисекунды): minSdk 24 не тянет java.time без core
    // library desugaring, заводить его ради одного поля смысла нет.
    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    // Этап 4: пресет с обходом опирается на geo-базы (geosite.dat/geoip.dat).
    // Если их нет и скачать не вышло — откатываемся на глобальный режим и
    // говорим об этом здесь, вместо того чтобы уронить ядро на отсутствующем
    // файле (тот же приём, что и на маке, см. resolvedRouting() в
    // Core/ConnectionManager.swift).
    private val _routingNotice = MutableStateFlow<String?>(null)
    val routingNotice: StateFlow<String?> = _routingNotice.asStateFlow()

    /** true, если разрешение на VPN ещё не выдано или отозвано — экран
     *  должен сперва провести пользователя через системный диалог
     *  (см. VpnService.prepare() в MainActivity), а уже потом звать connect(). */
    fun needsPermission(context: Context): Boolean = VpnService.prepare(context) != null

    fun connect(context: Context, server: ProxyConfig, routing: RoutingConfig = RoutingStore.current()) {
        if (needsPermission(context)) {
            _state.value = ConnectionState.Failed(L.t("Нет разрешения на VPN.", "No VPN permission."))
            return
        }
        _activeServer.value = server
        _externalIP.value = null
        _connectedSince.value = null
        _routingNotice.value = null
        _state.value = ConnectionState.Connecting

        val appContext = context.applicationContext
        scope.launch {
            val resolved = resolveRouting(routing)
            appContext.startService(
                Intent(appContext, MaxStrikeVpnService::class.java).apply {
                    action = MaxStrikeVpnService.ACTION_CONNECT
                    putExtra(MaxStrikeVpnService.EXTRA_SERVER, server)
                    putExtra(MaxStrikeVpnService.EXTRA_ROUTING, resolved)
                },
            )
        }
    }

    /** Возвращает правила, с которыми реально можно стартовать. */
    private suspend fun resolveRouting(requested: RoutingConfig): RoutingConfig {
        if (!requested.needsGeoAssets || GeoAssets.isReady()) return requested

        return try {
            withContext(Dispatchers.IO) {
                GeoAssets.downloadBlocking(
                    geositeURL = requested.geositeURL.ifEmpty { GeoAssets.DEFAULT_GEOSITE_URL },
                    geoipURL = requested.geoipURL.ifEmpty { GeoAssets.DEFAULT_GEOIP_URL },
                )
            }
            requested
        } catch (e: Exception) {
            _routingNotice.value = L.t(
                "Базы правил не загрузились (${e.message}). " +
                    "Подключаюсь без обхода — весь трафик пойдёт через VPN.",
                "Rule databases failed to download (${e.message}). " +
                    "Connecting without bypass — all traffic will go through the VPN.",
            )
            RoutingPreset.global.make()
        }
    }

    fun disconnect(context: Context) {
        context.startService(
            Intent(context, MaxStrikeVpnService::class.java).apply {
                action = MaxStrikeVpnService.ACTION_DISCONNECT
            },
        )
    }

    fun toggle(context: Context, server: ProxyConfig) {
        if (state.value.isConnected && activeServer.value?.id == server.id) {
            disconnect(context)
        } else {
            connect(context, server)
        }
    }

    // Дальше — только для MaxStrikeVpnService (тот же процесс). UI это не вызывает.

    internal fun reportConnected(externalIP: String?) {
        _externalIP.value = externalIP
        _connectedSince.value = System.currentTimeMillis()
        _state.value = ConnectionState.Connected
    }

    internal fun reportFailed(message: String) {
        _state.value = ConnectionState.Failed(message)
        _externalIP.value = null
        _connectedSince.value = null
    }

    internal fun reportDisconnected() {
        _state.value = ConnectionState.Disconnected
        _activeServer.value = null
        _externalIP.value = null
        _connectedSince.value = null
    }
}
