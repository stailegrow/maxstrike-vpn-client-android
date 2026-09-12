package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette

enum class AppTab(val labelRU: String, val labelEN: String) {
    HOME("ГЛАВНАЯ", "HOME"),
    SERVERS("СЕРВЕРА", "SERVERS"),
    SETTINGS("НАСТРОЙКИ", "SETTINGS"),
}

/** Нижняя навигация — три вкладки, как в macOS RootView (Tab enum). */
@Composable
fun BottomNav(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tab in AppTab.values()) {
            val isSelected = tab == selected
            val color = if (isSelected) palette.accent else palette.textSecondary

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onSelect(tab) }
                    .padding(top = 6.dp),
            ) {
                when (tab) {
                    AppTab.HOME -> HomeIcon(color = color)
                    AppTab.SERVERS -> ServersIcon(color = color)
                    AppTab.SETTINGS -> SettingsIcon(color = color)
                }
                Text(
                    text = L.t(tab.labelRU, tab.labelEN),
                    style = HudType.label(9.sp),
                    color = color,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Column(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.4f)
                        .height(2.dp)
                        .background(if (isSelected) palette.accent else androidx.compose.ui.graphics.Color.Transparent),
                ) {}
            }
        }
    }
}
