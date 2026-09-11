package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.RoutingConfig
import com.stailegrow.maxstrike.model.RoutingPreset
import com.stailegrow.maxstrike.vpn.MaxStrikeVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    /** true, если разрешение на VPN ещё не выдано или отозвано — экран
     *  должен сперва провести пользователя через системный диалог
     *  (см. VpnService.prepare() в MainActivity), а уже потом звать connect(). */
    fun needsPermission(context: Context): Boolean = VpnService.prepare(context) != null

    fun connect(context: Context, server: ProxyConfig, routing: RoutingConfig = RoutingPreset.global.make()) {
        if (needsPermission(context)) {
            _state.value = ConnectionState.Failed("Нет разрешения на VPN.")
            return
        }
        _activeServer.value = server
        _externalIP.value = null
        _connectedSince.value = null
        _state.value = ConnectionState.Connecting

        context.startService(
            Intent(context, MaxStrikeVpnService::class.java).apply {
                action = MaxStrikeVpnService.ACTION_CONNECT
                putExtra(MaxStrikeVpnService.EXTRA_SERVER, server)
                putExtra(MaxStrikeVpnService.EXTRA_ROUTING, routing)
            },
        )
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
