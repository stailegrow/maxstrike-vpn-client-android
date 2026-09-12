package com.stailegrow.maxstrike.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/**
 * Диалог раздельного туннелирования — список установленных приложений с
 * иконками; отмеченные не заходят в туннель, их трафик идёт мимо VPN
 * напрямую (MaxStrikeVpnService.addDisallowedApplication). Список читается
 * через queryIntentActivities — на Android 11+ для этого нужен блок
 * <queries> в манифесте (добавлен), иначе список окажется пустым.
 */
@Composable
fun AppPickerDialog(initiallyExcluded: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(initiallyExcluded) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.t("Исключённые приложения", "Excluded apps")) },
        text = {
            Column {
                Text(
                    text = L.t(
                        "Эти приложения не заходят в туннель — их трафик идёт мимо VPN, напрямую.",
                        "These apps don't enter the tunnel — their traffic goes directly, around the VPN.",
                    ),
                    style = HudType.body(11.sp),
                    color = palette.textSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    loading -> Text(L.t("Загружаю список…", "Loading the list…"), style = HudType.body(12.sp), color = palette.textSecondary)
                    apps.isEmpty() -> Text(
                        text = L.t("Не удалось получить список приложений.", "Could not get the list of apps."),
                        style = HudType.body(12.sp),
                        color = palette.textSecondary,
                    )
                    else -> LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            AppPickerRow(
                                app = app,
                                checked = selected.contains(app.packageName),
                                onToggle = { checked ->
                                    selected = if (checked) selected + app.packageName else selected - app.packageName
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) { Text(L.t("Сохранить", "Save"), color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L.t("Отмена", "Cancel"), color = palette.textSecondary) }
        },
    )
}

@Composable
private fun AppPickerRow(app: InstalledApp, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        if (app.icon != null) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(28.dp))
        } else {
            Spacer(modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = app.label,
            style = HudType.body(13.sp),
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val ownPackage = context.packageName
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    return try {
        pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ownPackage }
            .mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        appInfo.loadIcon(pm).toBitmap().asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                    InstalledApp(pkg, label, icon)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }
}
