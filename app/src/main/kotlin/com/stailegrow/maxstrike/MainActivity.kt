package com.stailegrow.maxstrike

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.GeoAssets
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.RoutingStore
import com.stailegrow.maxstrike.core.ServerStore
import com.stailegrow.maxstrike.core.SettingsStore
import com.stailegrow.maxstrike.core.ThemeStore
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.ui.HomeScreen
import com.stailegrow.maxstrike.ui.ServersScreen
import com.stailegrow.maxstrike.ui.SettingsScreen
import com.stailegrow.maxstrike.ui.components.AddServerDialog
import com.stailegrow.maxstrike.ui.components.AppBackground
import com.stailegrow.maxstrike.ui.components.AppTab
import com.stailegrow.maxstrike.ui.components.AppTopBar
import com.stailegrow.maxstrike.ui.components.BottomNav
import com.stailegrow.maxstrike.ui.theme.MaxStrikeTheme

// Интерфейс переведён на три вкладки (Главная/Сервера/Настройки) + общая
// шапка (AppTopBar) — то, что раньше было одним экраном с вордмарком,
// пикерами темы/роутинга и списком серверов вперемешку, теперь разложено
// ровно так, как на присланном пользователем макете мака: роутинг и темы
// переехали в "Настройки" (SettingsScreen.kt), быстрое добавление — из
// текстового поля на экране в диалог по кнопке "+" (AddServerDialog),
// список серверов общий для "Главной" и "Сервера" (ui/components/Cards.kt
// и ServerRow.kt), а "Сервера" дополнительно показывает историю пинга
// (LatencyCard). AppRoot ниже — единственное место, которое знает про все
// три экрана сразу; сами экраны друг про друга не знают.
class MainActivity : ComponentActivity() {

    // Системный диалог "разрешить VPN" можно показать только через Activity,
    // поэтому сам этот запрос живёт здесь, а не в ConnectionManager. Успешный
    // ответ просто повторяет вызов ConnectionManager.connect() — второй раз
    // needsPermission() уже вернёт false.
    private var pendingServer: ProxyConfig? = null
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val server = pendingServer
        pendingServer = null
        if (server != null) {
            if (result.resultCode == RESULT_OK) {
                ConnectionManager.connect(this, server)
            } else {
                // Пользователь отклонил системный диалог "разрешить VPN" —
                // раньше это молча ничего не делало и кнопка просто гасла,
                // как будто тапа не было; теперь показываем то же состояние
                // Failed, что и explicit-проверка в ConnectionManager.connect().
                ConnectionManager.reportFailed(L.t("Нет разрешения на VPN.", "No VPN permission."))
            }
        }
    }

    // Единственная точка входа для тапа по ConnectSlab: если уже подключены
    // (или подключаемся) именно к этому серверу — отключаем, иначе просим
    // разрешение (если ещё не выдано) и подключаемся.
    private fun requestToggle(server: ProxyConfig) {
        val alreadyThisServer = ConnectionManager.activeServer.value?.id == server.id &&
            (ConnectionManager.state.value.isConnected || ConnectionManager.state.value.isBusy)
        if (alreadyThisServer) {
            ConnectionManager.disconnect(this)
            return
        }
        val prepareIntent: Intent? = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingServer = server
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            ConnectionManager.connect(this, server)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServerStore.init(applicationContext)
        ServerStore.startAutoRefresh()
        ThemeStore.init(applicationContext)
        RoutingStore.init(applicationContext)
        GeoAssets.init(applicationContext)
        SettingsStore.init(applicationContext)

        // На 120-герцовых экранах (например, Samsung S25) окно по умолчанию
        // не всегда просит максимальную частоту обновления у системы — явно
        // заявляем её, иначе часть анимаций (живой фон, ConnectSlab)
        // визуально упирается в 60 Гц даже когда сам экран умеет больше.
        // preferredRefreshRate — поле WindowManager.LayoutParams, доступно
        // с API 21, так что дополнительная проверка версии не нужна.
        window.attributes = window.attributes.apply {
            preferredRefreshRate = Float.MAX_VALUE
        }

        enableEdgeToEdge()
        setContent {
            MaxStrikeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(onToggle = ::requestToggle)
                }
            }
        }
    }
}

// LocalOverscrollFactory (отключение "резинки" при скролле, см. комментарий
// ниже) в некоторых версиях Compose Foundation помечен экспериментальным —
// @OptIn на всякий случай, лишним он не будет, даже если аннотация сейчас
// и не обязательна для этого конкретного API.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRoot(onToggle: (ProxyConfig) -> Unit) {
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var addDialogOpen by remember { mutableStateOf(false) }

    val state by ConnectionManager.state.collectAsState()
    val connected = state is ConnectionState.Connected
    val liveBackground by SettingsStore.liveBackground.collectAsState()
    // L.t() читает L.current обычным полем, а не через State — Compose
    // сам по себе не узнает о смене языка. key(language) вокруг всего
    // дерева форсирует его пересборку целиком при смене — тот же приём,
    // что .id(settings.language) на маке (App.swift).
    val language by SettingsStore.language.collectAsState()

    // Растягивающийся оверскролл ("резинка" на краях списка) в Compose
    // включён по умолчанию на КАЖДОМ скролле и рендерится всё время, пока
    // палец на экране двигает список — даже если список короче экрана и
    // фактически никуда не прокручивается (ровно наш случай на
    // "Главной"/"Сервера" с парой серверов). Это отдельный, не связанный
    // с живым фоном источник подтормаживания при свайпах, и он одинаков на
    // всех трёх вкладках — что и совпадает с тем, что тормозит везде,
    // включая "Настройки". Отключаем эффект для всего приложения разом;
    // единственное, что меняется внешне — исчезает сама "резинка" на
    // краях списка, к скорости и плавности самого скролла это отношения
    // не имеет.
    key(language) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppBackground(animated = liveBackground, modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp),
            ) {
                AppTopBar(connected = connected, onAddClick = { addDialogOpen = true })

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        AppTab.HOME -> HomeScreen(onToggle = onToggle, modifier = Modifier.fillMaxSize())
                        AppTab.SERVERS -> ServersScreen(modifier = Modifier.fillMaxSize())
                        AppTab.SETTINGS -> SettingsScreen(modifier = Modifier.fillMaxSize())
                    }
                }

                BottomNav(selected = tab, onSelect = { tab = it })
            }
        }

        if (addDialogOpen) {
            AddServerDialog(onDismiss = { addDialogOpen = false })
        }
    }
    }
}
