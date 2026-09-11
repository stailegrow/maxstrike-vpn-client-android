package com.stailegrow.maxstrike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.letterSpacing
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.ui.theme.HudTextSecondary
import com.stailegrow.maxstrike.ui.theme.MaxStrikeTheme

// Этап 0 плана: пустой экран, который запускается на телефоне. Дальше сюда
// приедет ConnectionManager и список серверов (этапы 1–3 PLAN-ANDROID.md).
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
