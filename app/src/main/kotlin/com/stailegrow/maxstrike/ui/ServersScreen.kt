package com.stailegrow.maxstrike.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.ServerStore
import com.stailegrow.maxstrike.ui.components.LatencyCard
import com.stailegrow.maxstrike.ui.components.ServersCard
import com.stailegrow.maxstrike.ui.components.SubscriptionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Вкладка "Сервера" — Android-аналог ServersTab.swift: та же карточка
 * подписки и та же карточка со списком узлов, что и на главной (они и на
 * маке общие), плюс здесь ещё история задержки по каждому серверу
 * ("// ЗАДЕРЖКА"), которой на главном экране нет — там вместо неё спидтест.
 */
@Composable
fun ServersScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    val servers by ServerStore.servers.collectAsState()
    val subscriptions by ServerStore.subscriptions.collectAsState()
    val selectedID by ServerStore.selectedID.collectAsState()
    val pings by ServerStore.pings.collectAsState()
    val pingHistory by ServerStore.pingHistory.collectAsState()
    val refreshingIDs by ServerStore.refreshingIDs.collectAsState()
    val isPinging by ServerStore.isPinging.collectAsState()
    val activeServer by ConnectionManager.activeServer.collectAsState()
    val connectionState by ConnectionManager.state.collectAsState()

    val manual = servers.filter { it.subscriptionID == null }
    val isActiveState = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting

    // Опрос раз в 5 секунд, пока открыта именно эта вкладка — Android-аналог
    // ServerStore.startAutoPing()/stopAutoPing() на маке (там дёргается из
    // ServersTab.onAppear/onDisappear). Тут отдельный стоп не нужен:
    // LaunchedEffect сам отменяется, когда ServersScreen уходит из
    // композиции (ушли на другую вкладку) — то же самое onDisappear, только
    // возможностями Compose. Раньше история пинга (LatencyCard, подпись
    // "опрос каждые 5 с") обновлялась только по ручному нажатию "⟳" —
    // подпись обещала автообновление, которого не было.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            ServerStore.pingAll()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        items(subscriptions, key = { it.id }) { subscription ->
            val nodes = servers.filter { it.subscriptionID == subscription.id }
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                        onPingAll = null,
                        isPinging = isPinging,
                    )
                    LatencyCard(servers = nodes, pingHistory = pingHistory)
                }
            }
        }

        if (manual.isNotEmpty()) {
            item(key = "manual") {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    LatencyCard(servers = manual, pingHistory = pingHistory)
                }
            }
        }
    }
}
