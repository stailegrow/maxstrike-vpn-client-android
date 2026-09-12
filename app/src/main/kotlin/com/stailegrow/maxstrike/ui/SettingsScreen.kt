package com.stailegrow.maxstrike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.BuildConfig
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.GeoAssets
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.Lang
import com.stailegrow.maxstrike.core.RoutingChecker
import com.stailegrow.maxstrike.core.RoutingStore
import com.stailegrow.maxstrike.core.SettingsStore
import com.stailegrow.maxstrike.core.ThemeStore
import com.stailegrow.maxstrike.model.RoutingPreset
import com.stailegrow.maxstrike.ui.components.AppPickerDialog
import com.stailegrow.maxstrike.ui.components.HudCard
import com.stailegrow.maxstrike.ui.components.NoticeBanner
import com.stailegrow.maxstrike.ui.components.NoticeTone
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Вкладка "Настройки" — Android-аналог того, что на маке размазано по
 * разным местам (роутинг был на главной, темы тоже) — здесь всё в одном
 * месте, как в последнем макете от пользователя.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
        item(key = "routing") { RoutingCard() }
        item(key = "language") { LanguageCard() }
        item(key = "theme") { ThemeCard() }
        item(key = "behavior") { BehaviorCard() }
        item(key = "about") { AboutCard() }
    }
}

@Composable
private fun RoutingCard(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val presetID by RoutingStore.presetID.collectAsState()
    val customDomains by SettingsStore.customDirectDomains.collectAsState()
    val excludedApps by SettingsStore.excludedApps.collectAsState()
    val geoStatus by GeoAssets.status.collectAsState()
    val connectionState by ConnectionManager.state.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val scope = rememberCoroutineScope()

    var isUpdatingGeo by remember { mutableStateOf(false) }
    var geoError by remember { mutableStateOf<String?>(null) }
    var editDomainsOpen by remember { mutableStateOf(false) }
    var appPickerOpen by remember { mutableStateOf(false) }
    var isCheckingRoute by remember { mutableStateOf(false) }
    var routeCheckResult by remember { mutableStateOf<RoutingChecker.Result?>(null) }
    var routeCheckError by remember { mutableStateOf<String?>(null) }

    HudCard(title = L.t("Маршрутизация", "Routing"), modifier = modifier) {
        Column {
            for (preset in RoutingPreset.all) {
                val isSelected = preset.id == presetID
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { RoutingStore.select(preset.id) }
                        .padding(vertical = 8.dp),
                ) {
                    RadioButton(selected = isSelected, onClick = { RoutingStore.select(preset.id) })
                    Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                        Text(L.t(preset.titleRU, preset.titleEN), style = HudType.body(14.sp), color = palette.textPrimary)
                        Text(
                            text = L.t(preset.subtitleRU, preset.subtitleEN),
                            style = HudType.body(11.sp),
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Divider()

            SettingsRow(
                label = L.t("Свои домены напрямую", "Custom direct domains"),
                value = customDomains.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: L.t("не заданы", "not set"),
                buttonText = L.t("Изменить", "Change"),
                enabled = true,
                onClick = { editDomainsOpen = true },
            )

            Divider()

            SettingsRow(
                label = L.t("Исключённые приложения", "Excluded apps"),
                value = if (excludedApps.isEmpty()) {
                    L.t("все идут через VPN", "everything goes through the VPN")
                } else {
                    L.t("${excludedApps.size} прил. мимо VPN", "${excludedApps.size} apps bypass VPN")
                },
                buttonText = L.t("Изменить", "Change"),
                enabled = true,
                onClick = { appPickerOpen = true },
            )

            Divider()

            SettingsRow(
                label = L.t("Базы правил", "Rule databases"),
                value = geoAssetsSummary(geoStatus),
                buttonText = if (isUpdatingGeo) "…" else L.t("Обновить", "Update"),
                enabled = !isUpdatingGeo,
                onClick = {
                    isUpdatingGeo = true
                    geoError = null
                    scope.launch {
                        val requested = RoutingStore.current()
                        try {
                            withContext(Dispatchers.IO) {
                                GeoAssets.downloadBlocking(
                                    geositeURL = requested.geositeURL.ifEmpty { GeoAssets.DEFAULT_GEOSITE_URL },
                                    geoipURL = requested.geoipURL.ifEmpty { GeoAssets.DEFAULT_GEOIP_URL },
                                )
                            }
                        } catch (e: Exception) {
                            geoError = L.t("Не удалось обновить: ${e.message}", "Failed to update: ${e.message}")
                        }
                        isUpdatingGeo = false
                    }
                },
            )
            geoError?.let {
                NoticeBanner(text = it, tone = NoticeTone.ERROR, modifier = Modifier.padding(top = 8.dp))
            }

            Divider()

            SettingsRow(
                label = L.t("Проверка маршрута", "Route check"),
                value = if (!isConnected) L.t("нужно подключение", "connection required") else routeCheckSummary(routeCheckResult),
                buttonText = if (isCheckingRoute) "…" else L.t("Проверить", "Check"),
                enabled = isConnected && !isCheckingRoute,
                onClick = {
                    isCheckingRoute = true
                    routeCheckError = null
                    scope.launch {
                        try {
                            routeCheckResult = withContext(Dispatchers.IO) { RoutingChecker.checkBlocking(presetID) }
                        } catch (e: Exception) {
                            routeCheckError = e.message ?: L.t("Не удалось проверить маршрут.", "Could not check the route.")
                        }
                        isCheckingRoute = false
                    }
                },
            )
            Text(
                text = L.t(
                    "Приблизительно: внешний IP со страной и, для \"Обход РФ\", доступность ya.ru — " +
                        "не точный трассировщик внутри ядра.",
                    "Approximate: external IP with country and, for \"Bypass RU\", ya.ru reachability — " +
                        "not an exact traceroute inside the core.",
                ),
                style = HudType.body(10.sp),
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            routeCheckError?.let {
                NoticeBanner(text = it, tone = NoticeTone.ERROR, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (editDomainsOpen) {
        EditDomainsDialog(
            initial = SettingsStore.customDirectDomainsRaw(),
            onDismiss = { editDomainsOpen = false },
            onSave = { raw ->
                SettingsStore.setCustomDirectDomains(raw)
                editDomainsOpen = false
            },
        )
    }

    if (appPickerOpen) {
        AppPickerDialog(
            initiallyExcluded = excludedApps,
            onDismiss = { appPickerOpen = false },
            onSave = { picked ->
                SettingsStore.setExcludedApps(picked)
                appPickerOpen = false
            },
        )
    }
}

private fun routeCheckSummary(result: RoutingChecker.Result?): String {
    if (result == null) return L.t("не проверялась", "not checked yet")
    val countryPart = result.country?.let { " ($it)" } ?: ""
    val ipPart = "${result.externalIP}$countryPart"
    val domesticPart = if (result.domesticSite != null) {
        val status = if (result.domesticReachable == true) {
            L.t("${result.domesticSite}: ${result.domesticLatencyMs} мс", "${result.domesticSite}: ${result.domesticLatencyMs} ms")
        } else {
            L.t("${result.domesticSite}: нет ответа", "${result.domesticSite}: no response")
        }
        " · $status"
    } else ""
    return "$ipPart$domesticPart"
}

private fun geoAssetsSummary(status: GeoAssets.Status): String {
    if (!status.ready) return L.t("не загружены", "not downloaded")
    val kb = status.sizeBytes / 1024
    val updated = status.updatedAtMillis?.let { relativeShort(it) } ?: L.t("давно", "a while ago")
    return L.t("$kb КБ · $updated", "$kb KB · $updated")
}

private fun relativeShort(ms: Long): String {
    val minutes = (System.currentTimeMillis() - ms) / 60000
    return when {
        minutes < 1 -> L.t("только что", "just now")
        minutes < 60 -> L.t("$minutes мин назад", "$minutes min ago")
        minutes < 60 * 24 -> L.t("${minutes / 60} ч назад", "${minutes / 60} h ago")
        else -> L.t("${minutes / (60 * 24)} дн назад", "${minutes / (60 * 24)} d ago")
    }
}

@Composable
private fun SettingsRow(label: String, value: String, buttonText: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = HudType.body(13.sp), color = palette.textPrimary)
            Text(
                text = value,
                style = HudType.body(11.sp),
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        TextButton(onClick = onClick, enabled = enabled) {
            Text(buttonText, color = palette.accent)
        }
    }
}

@Composable
private fun Divider() {
    val palette = LocalPalette.current
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.cardBorder))
}

@Composable
private fun EditDomainsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.t("Свои домены напрямую", "Custom direct domains")) },
        text = {
            Column {
                Text(
                    text = L.t(
                        "Через запятую — эти адреса всегда идут мимо VPN, независимо от пресета.",
                        "Comma-separated — these addresses always bypass the VPN, regardless of the preset.",
                    ),
                    style = HudType.body(11.sp),
                    color = palette.textSecondary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("nas.local, printer.local") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(L.t("Сохранить", "Save"), color = palette.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L.t("Отмена", "Cancel"), color = palette.textSecondary) } },
    )
}

@Composable
private fun LanguageCard(modifier: Modifier = Modifier) {
    val language by SettingsStore.language.collectAsState()
    HudCard(title = L.t("Язык", "Language"), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguagePill(
                text = Lang.RU.title,
                selected = language == Lang.RU,
                onClick = { SettingsStore.setLanguage(Lang.RU) },
            )
            LanguagePill(
                text = Lang.EN.title,
                selected = language == Lang.EN,
                onClick = { SettingsStore.setLanguage(Lang.EN) },
            )
        }
    }
}

@Composable
private fun LanguagePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    val borderColor = if (selected) palette.accent else palette.cardBorder
    val textColor = if (selected) palette.accent else palette.textSecondary
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) palette.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(androidx.compose.foundation.BorderStroke(1.dp, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = HudType.label(12.sp), color = textColor)
    }
}

@Composable
private fun ThemeCard(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val paletteId by ThemeStore.paletteId.collectAsState()

    HudCard(title = L.t("Тема", "Theme"), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            for (p in Palette.all) {
                val isSelected = p.id == paletteId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { ThemeStore.select(p.id) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) p.accentStart else palette.cardBorder,
                                shape = CircleShape,
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(p.accentStart),
                    )
                    Text(
                        text = L.t(p.nameRU, p.nameEN),
                        style = HudType.label(10.sp),
                        color = if (isSelected) palette.textPrimary else palette.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorCard(modifier: Modifier = Modifier) {
    val bypassLAN by SettingsStore.bypassLAN.collectAsState()
    val pingOnLaunch by SettingsStore.pingOnLaunch.collectAsState()
    val liveBackground by SettingsStore.liveBackground.collectAsState()

    HudCard(title = L.t("Поведение", "Behavior"), modifier = modifier) {
        Column {
            ToggleRow(
                label = L.t("Обход локальной сети", "Bypass local network"),
                caption = L.t(
                    "Принтеры, NAS, роутер и соседние машины остаются доступны, пока VPN включён.",
                    "Printers, NAS, router, and nearby devices stay reachable while the VPN is on.",
                ),
                checked = bypassLAN,
                onCheckedChange = { SettingsStore.setBypassLAN(it) },
            )
            Divider()
            ToggleRow(
                label = L.t("Живой фон", "Live background"),
                caption = L.t("Лёгкая дышащая анимация фоновой сетки.", "A gentle breathing animation of the background grid."),
                checked = liveBackground,
                onCheckedChange = { SettingsStore.setLiveBackground(it) },
            )
            Divider()
            ToggleRow(
                label = L.t("Замерять задержку при запуске", "Measure latency on launch"),
                caption = null,
                checked = pingOnLaunch,
                onCheckedChange = { SettingsStore.setPingOnLaunch(it) },
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, caption: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = HudType.body(13.sp), color = palette.textPrimary)
            caption?.let {
                Text(
                    text = it,
                    style = HudType.body(10.sp),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutCard(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    HudCard(title = L.t("О программе", "About"), modifier = modifier) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Версия", "Version"), style = HudType.body(12.sp), color = palette.textSecondary)
                Text("Max Strike ${BuildConfig.VERSION_NAME}", style = HudType.code(12.sp), color = palette.textPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Пакет", "Package"), style = HudType.body(12.sp), color = palette.textSecondary)
                Text(BuildConfig.APPLICATION_ID, style = HudType.code(12.sp), color = palette.textPrimary)
            }
        }
    }
}
