package com.stailegrow.maxstrike

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.letterSpacing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.net.VpnService
import com.stailegrow.maxstrike.core.ConnectionManager
import com.stailegrow.maxstrike.core.ConnectionState
import com.stailegrow.maxstrike.core.LinkParser
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.ui.theme.HudTextSecondary
import com.stailegrow.maxstrike.ui.theme.MaxStrikeTheme

// Этап 1 плана: вставил ссылку, разобрал, нажал "Подключить" — дальше
// ConnectionManager просит систему поднять MaxStrikeVpnService (VpnService +
// ядро Xray через XrayCoreBridge, см. core/ и vpn/). Экран, как и на macOS,
// знает только про ConnectionManager.state/activeServer/externalIP.
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
        if (server != null && result.resultCode == RESULT_OK) {
            ConnectionManager.connect(this, server)
        }
    }

    private fun requestConnect(server: ProxyConfig) {
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
        enableEdgeToEdge()
        setContent {
            MaxStrikeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(onConnect = ::requestConnect)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onConnect: (ProxyConfig) -> Unit) {
    var linkText by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<LinkParser.ParseResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Wordmark()
        StatusLine()

        OutlinedTextField(
            value = linkText,
            onValueChange = { linkText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            label = { Text("vless://…") },
            placeholder = { Text("Вставь одну или несколько ссылок, по одной на строку") },
            minLines = 3,
        )

        Button(
            onClick = { result = LinkParser.parseMany(linkText) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("Разобрать")
        }

        result?.let { ParseResultView(it, onConnect) }
    }
}

@Composable
private fun StatusLine() {
    val context = LocalContext.current
    val state by ConnectionManager.state.collectAsState()
    val activeServer by ConnectionManager.activeServer.collectAsState()
    val externalIP by ConnectionManager.externalIP.collectAsState()

    val text = when (val s = state) {
        is ConnectionState.Disconnected -> "Отключено"
        is ConnectionState.Connecting -> "Подключаюсь к ${activeServer?.displayName ?: "серверу"}…"
        is ConnectionState.Connected -> "Подключено · ${externalIP ?: "…"}"
        is ConnectionState.Failed -> "Ошибка: ${s.message}"
    }
    val color = when (state) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        is ConnectionState.Failed -> MaterialTheme.colorScheme.error
        else -> HudTextSecondary
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(text = text, color = color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (state.isConnected || state.isBusy) {
            OutlinedButton(onClick = { ConnectionManager.disconnect(context) }) {
                Text("Отключить")
            }
        }
    }
}

@Composable
private fun ParseResultView(result: LinkParser.ParseResult, onConnect: (ProxyConfig) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(result.configs) { config -> ServerRow(config, onConnect) }
        items(result.errors) { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ServerRow(config: ProxyConfig, onConnect: (ProxyConfig) -> Unit) {
    val state by ConnectionManager.state.collectAsState()
    val activeServer by ConnectionManager.activeServer.collectAsState()
    val isThisServer = activeServer?.id == config.id

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = config.displayName, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
            Text(text = config.summary, color = HudTextSecondary, fontSize = 12.sp)
        }
        Button(
            onClick = { onConnect(config) },
            enabled = !(isThisServer && state.isBusy),
        ) {
            Text(if (isThisServer && state.isConnected) "Переподключить" else "Подключить")
        }
    }
}

@Composable
private fun Wordmark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "MAX STRIKE",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.15.em,
        )
        Text(
            text = "SECURE TUNNEL",
            color = HudTextSecondary,
            fontSize = 12.sp,
            letterSpacing = 0.3.em,
        )
    }
}
