package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.SpeedTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.Subscription
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect

/**
 * Карточка подписки — Android-аналог SubscriptionCard из HomeTab.swift,
 * используется и на главной, и на вкладке "Сервера" (там же и там же
 * выглядит одинаково — на маке тоже одна и та же карточка).
 */
@Composable
fun SubscriptionCard(
    subscription: Subscription,
    nodeCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    HudCard(title = L.t("Подписка", "Subscription"), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = subscription.displayName,
                style = HudType.heading(16.sp),
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "✎",
                color = palette.textSecondary,
                style = HudType.body(16.sp),
                modifier = Modifier.clickable { renameOpen = true }.padding(6.dp),
            )
            Text(
                text = if (isRefreshing) "…" else "⟳",
                color = palette.accent,
                style = HudType.body(16.sp),
                modifier = Modifier.clickable(enabled = !isRefreshing, onClick = onRefresh).padding(6.dp),
            )
            Box {
                Text(
                    text = "⋯",
                    color = palette.textSecondary,
                    style = HudType.body(16.sp),
                    modifier = Modifier.clickable { menuOpen = true }.padding(6.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isRefreshing) L.t("Обновляется…", "Updating…") else L.t("Обновить", "Refresh")) },
                        enabled = !isRefreshing,
                        onClick = { menuOpen = false; onRefresh() },
                    )
                    DropdownMenuItem(
                        text = { Text(L.t("Удалить подписку", "Delete subscription")) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }

        subscription.announce?.takeIf { it.isNotBlank() }?.let {
            NoticeBanner(text = it, tone = NoticeTone.INFO, modifier = Modifier.padding(top = 8.dp))
        }
        subscription.trafficSummary?.let {
            TrafficMeter(subscription = subscription, modifier = Modifier.padding(top = 8.dp))
        }
        subscription.lastError?.let {
            NoticeBanner(text = it, tone = NoticeTone.ERROR, modifier = Modifier.padding(top = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(relativeUpdatedText(subscription.lastUpdated), style = HudType.body(11.sp), color = palette.textSecondary)
            Text(L.t("$nodeCount узлов", "$nodeCount nodes"), style = HudType.body(11.sp), color = palette.textSecondary)
        }
        subscription.expirySummary?.let { summary ->
            Text(
                text = if (subscription.isExpired) L.t("истекла $summary", "expired $summary") else L.t("до $summary", "until $summary"),
                style = HudType.body(11.sp),
                color = if (subscription.isExpired) palette.bad else palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (renameOpen) {
        RenameSubscriptionDialog(
            subscription = subscription,
            onDismiss = { renameOpen = false },
            onConfirm = onRename,
        )
    }
}

@Composable
private fun RenameSubscriptionDialog(
    subscription: Subscription,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(subscription.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.t("Переименовать подписку", "Rename subscription")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(subscription.displayName) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text); onDismiss() }) { Text(L.t("Сохранить", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L.t("Отмена", "Cancel")) }
        },
    )
}

private fun relativeUpdatedText(lastUpdated: Long?): String {
    if (lastUpdated == null) return L.t("Ещё не обновлялась", "Not updated yet")
    val minutes = (System.currentTimeMillis() - lastUpdated) / 60000
    return when {
        minutes < 1 -> L.t("Обновлена только что", "Updated just now")
        minutes < 60 -> L.t("Обновлена $minutes мин назад", "Updated $minutes min ago")
        minutes < 60 * 24 -> L.t("Обновлена ${minutes / 60} ч назад", "Updated ${minutes / 60} h ago")
        else -> L.t("Обновлена ${minutes / (60 * 24)} дн назад", "Updated ${minutes / (60 * 24)} d ago")
    }
}

/**
 * Карточка списка серверов — Android-аналог блока serverList Card из
 * HomeTab.swift, тоже общая для главной и вкладки "Сервера". onPingAll —
 * необязательный: на главной есть значок пинга в шапке, на вкладке
 * "Сервера" его роль уже играет отдельная карточка "// ЗАДЕРЖКА".
 */
@Composable
fun ServersCard(
    title: String,
    servers: List<ProxyConfig>,
    selectedID: String?,
    activeID: String?,
    isActiveState: Boolean,
    measuredIDs: Set<String>,
    pings: Map<String, Int?>,
    onSelect: (String) -> Unit,
    onDelete: ((String) -> Unit)?,
    onPingAll: (() -> Unit)?,
    isPinging: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    HudCard(title = title, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = L.t("${servers.size} доступно", "${servers.size} available"),
                style = HudType.label(11.sp),
                color = palette.textSecondary,
            )
            if (onPingAll != null) {
                Text(
                    text = if (isPinging) "…" else "⟳",
                    color = palette.accent,
                    style = HudType.body(16.sp),
                    modifier = Modifier
                        .clickable(enabled = !isPinging, onClick = onPingAll)
                        .padding(4.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            servers.forEachIndexed { index, config ->
                ServerRow(
                    config = config,
                    index = index,
                    isSelected = config.id == selectedID,
                    isActive = isActiveState && activeID == config.id,
                    measured = measuredIDs.contains(config.id),
                    pingMs = pings[config.id],
                    onSelect = { onSelect(config.id) },
                    onDelete = onDelete?.let { callback -> { callback(config.id) } },
                )
            }
        }
    }
}

/**
 * Карточка спидтеста — Android-аналог SpeedCard. Работает и с активным
 * VPN, и без него: наш процесс не исключён из VPN-маршрута, поэтому при
 * поднятом туннеле обычный HTTP-запрос из SpeedTester сам едет через
 * него, а без туннеля — просто напрямую по обычной сети, никакой особой
 * логики для этого не нужно ни там, ни там.
 */
@Composable
fun SpeedCard(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val connectionState by ConnectionManager.state.collectAsState()
    val connected = connectionState is ConnectionState.Connected

    var isTesting by remember { mutableStateOf(false) }
    var resultMbps by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val shape = cutRect(6.dp)

    fun runTest() {
        if (isTesting) return
        isTesting = true
        error = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { SpeedTester.measureBlocking() }
                resultMbps = result.mbps
            } catch (e: Exception) {
                error = e.message ?: L.t("Не удалось измерить скорость.", "Could not measure the speed.")
            } finally {
                isTesting = false
            }
        }
    }

    HudCard(title = L.t("Скорость", "Speed"), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isTesting -> L.t("замер…", "measuring…")
                        resultMbps != null -> "%.1f %s".format(resultMbps, L.t("Мбит/с", "Mbit/s"))
                        else -> "— " + L.t("Мбит/с", "Mbit/s")
                    },
                    style = HudType.heading(16.sp),
                    color = palette.textPrimary,
                )
                Text(
                    text = if (connected) L.t("замер идёт через туннель", "the test runs through the tunnel") else L.t("замер идёт напрямую, без VPN", "the test runs directly, without VPN"),
                    style = HudType.body(11.sp),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(shape)
                    .border(BorderStroke(1.dp, palette.cardBorder), shape)
                    .clickable(enabled = !isTesting) { runTest() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (isTesting) "…" else L.t("Проверить", "Test"),
                    style = HudType.label(11.sp),
                    color = if (isTesting) palette.textSecondary.copy(alpha = 0.4f) else palette.textSecondary,
                )
            }
        }
        error?.let {
            NoticeBanner(text = it, tone = NoticeTone.ERROR, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
