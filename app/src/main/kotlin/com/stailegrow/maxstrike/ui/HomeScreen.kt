package com.stailegrow.maxstrike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.ServerStore
import com.stailegrow.maxstrike.core.SettingsStore
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.ui.components.ConnectSlab
import com.stailegrow.maxstrike.ui.components.HudCard
import com.stailegrow.maxstrike.ui.components.NoticeBanner
import com.stailegrow.maxstrike.ui.components.NoticeTone
import com.stailegrow.maxstrike.ui.components.ServersCard
import com.stailegrow.maxstrike.ui.components.SpeedCard
import com.stailegrow.maxstrike.ui.components.SubscriptionCard
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/**
 * Вкладка "Главная" — Android-аналог HomeTab.swift: ConnectSlab (герой
 * экрана), затем те же карточки подписки/серверов, что и на вкладке
 * "Сервера", и в конце — спидтест (пока только интерфейс, см. SpeedCard).
 */
@Composable
fun HomeScreen(onToggle: (ProxyConfig) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        ServerStore.prepareOnLaunch(measureLatency = SettingsStore.pingOnLaunch.value)
    }

    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()

    val state by ConnectionManager.state.collectAsState()
    val activeServer by ConnectionManager.activeServer.collectAsState()
    val externalIP by ConnectionManager.externalIP.collectAsState()
    val connectedSince by ConnectionManager.connectedSince.collectAsState()
    val routingNotice by ConnectionManager.routingNotice.collectAsState()
    val selectedID by ServerStore.selectedID.collectAsState()
    val servers by ServerStore.servers.collectAsState()
    val subscriptions by ServerStore.subscriptions.collectAsState()
    val pings by ServerStore.pings.collectAsState()
    val refreshingIDs by ServerStore.refreshingIDs.collectAsState()
    val isPinging by ServerStore.isPinging.collectAsState()

    val isActiveState = state is ConnectionState.Connected || state is ConnectionState.Connecting
    val slabServer = if (isActiveState) activeServer else servers.firstOrNull { it.id == selectedID }
    val manual = servers.filter { it.subscriptionID == null }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        item(key = "slab") {
            ConnectSlab(
                state = state,
                serverName = slabServer?.displayName,
                externalIP = externalIP,
                connectedSinceMillis = connectedSince,
                onToggle = { slabServer?.let(onToggle) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        routingNotice?.let { notice ->
            item(key = "notice") {
                NoticeBanner(text = notice, tone = NoticeTone.ERROR)
            }
        }

        items(subscriptions, key = { it.id }) { subscription ->
            val nodes = servers.filter { it.subscriptionID == subscription.id }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SubscriptionCard(
                    subscription = subscription,
                    nodeCount = nodes.size,
                    isRefreshing = refreshingIDs.contains(subscription.id),
                    onRefresh = { scope.launch { ServerStore.refresh(subscription) } },
                    onDelete = { scope.launch { ServerStore.removeSubscription(subscription.id) } },
                    onRename = { newName -> ServerStore.renameSubscription(subscription.id, newName) },
                )
                if (nodes.isNotEmpty()) {
                    ServersCard(
                        title = L.t("Узлы", "Nodes"),
                        servers = nodes,
                        selectedID = selectedID,
                        activeID = activeServer?.id,
                        isActiveState = isActiveState,
                        measuredIDs = pings.keys,
                        pings = pings,
                        onSelect = { id -> ServerStore.select(id) },
                        onDelete = null,
                        onPingAll = { scope.launch { ServerStore.pingAll() } },
                        isPinging = isPinging,
                    )
                }
            }
        }

        if (manual.isNotEmpty()) {
            item(key = "manual") {
                ServersCard(
                    title = L.t("Свои серверы", "Added manually"),
                    servers = manual,
                    selectedID = selectedID,
                    activeID = activeServer?.id,
                    isActiveState = isActiveState,
                    measuredIDs = pings.keys,
                    pings = pings,
                    onSelect = { id -> ServerStore.select(id) },
                    onDelete = { id -> scope.launch { ServerStore.remove(setOf(id)) } },
                    onPingAll = { scope.launch { ServerStore.pingAll() } },
                    isPinging = isPinging,
                )
            }
        }

        if (servers.isEmpty() && subscriptions.isEmpty()) {
            item(key = "empty") {
                HudCard {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = L.t("СПИСОК ПУСТ", "THE LIST IS EMPTY"),
                            style = HudType.label(11.sp),
                            color = palette.textSecondary,
                        )
                        Text(
                            text = L.t(
                                "Нажми \"+\" в шапке и вставь ссылку узла (vless://) или подписки",
                                "Tap \"+\" in the header and paste a node link (vless://) or a subscription",
                            ),
                            style = HudType.body(13.sp),
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        } else {
            item(key = "speed") { SpeedCard() }
        }
    }
}
