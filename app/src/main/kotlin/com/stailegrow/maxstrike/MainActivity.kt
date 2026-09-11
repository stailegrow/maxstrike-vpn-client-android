package com.stailegrow.maxstrike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.letterSpacing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.LinkParser
import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.ui.theme.HudTextSecondary
import com.stailegrow.maxstrike.ui.theme.MaxStrikeTheme

// Этап 1 плана: вставил ссылку, разобрал — видно, что парсер и модель живые.
// Само подключение (Xray + VpnService) приедет следующим шагом, когда будет
// готова нативная библиотека ядра.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaxStrikeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    var linkText by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<LinkParser.ParseResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Wordmark()

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

        result?.let { ParseResultView(it) }
    }
}

@Composable
private fun ParseResultView(result: LinkParser.ParseResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(result.configs) { config -> ServerRow(config) }
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
private fun ServerRow(config: ProxyConfig) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = config.displayName, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
        Text(text = config.summary, color = HudTextSecondary, fontSize = 12.sp)
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
