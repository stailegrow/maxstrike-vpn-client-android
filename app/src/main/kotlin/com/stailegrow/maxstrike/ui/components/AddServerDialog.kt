package com.stailegrow.maxstrike.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.core.LinkParser
import com.stailegrow.maxstrike.core.ServerStore
import com.stailegrow.maxstrike.core.ServerStore.QuickAddResult
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect
import kotlinx.coroutines.launch

/**
 * Два способа добавить сервер — свои ссылки (одна или сразу пачка, по
 * одной на строку) или ссылка подписки с панели. Android-аналог режимов
 * .links и .subscription из AddSheet.swift на маке.
 */
private enum class AddMode { LINKS, SUBSCRIPTION, QR }

/**
 * Диалог добавления сервера — Android-аналог AddSheet.swift. Раньше это
 * был голый Material3 AlertDialog с одной однострочной строкой (нельзя
 * было вставить больше одной ссылки за раз, и подписку нельзя было сразу
 * подписать своим именем) — теперь свой HUD-корпус (HudCard внутри
 * Dialog), переключатель режимов и оба сценария с мака: пачка ссылок
 * узлов и подписка с отдельным полем имени, с тем же поведением при
 * ошибке первой загрузки — подписка, которая не смогла загрузиться,
 * не остаётся висеть в списке.
 */
@Composable
fun AddServerDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AddMode.LINKS) }
    var linksText by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var subName by remember { mutableStateOf("") }

    var isWorking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var tone by remember { mutableStateOf(NoticeTone.INFO) }

    fun submitLinks() {
        val text = linksText
        isWorking = true
        status = null
        scope.launch {
            val parsed = LinkParser.parseMany(text)
            if (parsed.configs.isEmpty()) {
                status = parsed.errors.firstOrNull() ?: L.t("Ссылки не распознались.", "The links were not recognised.")
                tone = NoticeTone.ERROR
            } else {
                val added = ServerStore.add(parsed.configs)
                if (parsed.errors.isEmpty()) {
                    onDismiss()
                } else {
                    val addedText = if (added == 0) {
                        L.t("Новых узлов нет — всё это уже добавлено.", "No new nodes — all of this is already added.")
                    } else {
                        L.t("Добавлено узлов: $added.", "Nodes added: $added.")
                    }
                    status = "$addedText " + L.t("Не разобралось: ${parsed.errors.size}.", "Could not parse: ${parsed.errors.size}.")
                    tone = NoticeTone.WARNING
                }
            }
            isWorking = false
        }
    }

    fun submitSubscription() {
        val url = subUrl.trim()
        isWorking = true
        status = null
        scope.launch {
            val subscription = ServerStore.addSubscription(url = url, name = subName)
            val ok = ServerStore.refresh(subscription)
            val stored = ServerStore.subscriptions.value.firstOrNull { it.id == subscription.id }
            if (!ok || stored == null) {
                status = stored?.lastError ?: L.t("Не удалось загрузить подписку.", "Could not load the subscription.")
                tone = NoticeTone.ERROR
                ServerStore.removeSubscription(subscription.id)
            } else {
                onDismiss()
            }
            isWorking = false
        }
    }

    // QR несёт готовую строку — ссылку узла или ссылку подписки, само
    // приложение не знает заранее какую. Разбор той же строки, что и
    // быстрое добавление на маке (см. RootView.swift add(_:) -> quickAdd):
    // один и тот же диспетчер сам решает, подписка это или узел(-ы).
    fun handleQrFound(code: String) {
        if (isWorking) return
        isWorking = true
        status = null
        scope.launch {
            val result = ServerStore.quickAdd(code)
            status = result.message
            if (result is QuickAddResult.Failure) {
                tone = NoticeTone.ERROR
                isWorking = false
            } else {
                tone = NoticeTone.INFO
                onDismiss()
            }
        }
    }

    val canSubmit = !isWorking && when (mode) {
        AddMode.LINKS -> linksText.isNotBlank()
        AddMode.SUBSCRIPTION -> subUrl.isNotBlank()
        AddMode.QR -> false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            HudCard(title = L.t("Добавить сервер", "Add server")) {
                ModeSwitch(
                    mode = mode,
                    enabled = !isWorking,
                    onSelect = { selected -> mode = selected; status = null },
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (mode) {
                    AddMode.LINKS -> {
                        HudTextField(
                            value = linksText,
                            onValueChange = { linksText = it },
                            placeholder = "vless://…\n" + L.t("по одной ссылке на строку", "one link per line"),
                            enabled = !isWorking,
                            monospaced = true,
                            minLines = 4,
                            maxLines = 8,
                        )
                    }
                    AddMode.SUBSCRIPTION -> {
                        HudTextField(
                            value = subUrl,
                            onValueChange = { subUrl = it },
                            placeholder = "https://…/subscribe",
                            enabled = !isWorking,
                            monospaced = true,
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HudTextField(
                            value = subName,
                            onValueChange = { subName = it },
                            placeholder = L.t("Название (необязательно)", "Name (optional)"),
                            enabled = !isWorking,
                            monospaced = false,
                            singleLine = true,
                        )
                    }
                    AddMode.QR -> {
                        QrScanner(
                            active = !isWorking,
                            onFound = { code -> handleQrFound(code) },
                        )
                    }
                }

                status?.let { message ->
                    NoticeBanner(text = message, tone = tone, modifier = Modifier.padding(top = 10.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DialogButton(text = L.t("Отмена", "Cancel"), accent = false, enabled = !isWorking, onClick = onDismiss)
                    if (mode != AddMode.QR) {
                        Spacer(modifier = Modifier.width(10.dp))
                        DialogButton(
                            text = if (isWorking) "…" else L.t("Добавить", "Add"),
                            accent = true,
                            enabled = canSubmit,
                            onClick = {
                                when (mode) {
                                    AddMode.LINKS -> submitLinks()
                                    AddMode.SUBSCRIPTION -> submitSubscription()
                                    AddMode.QR -> {}
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: AddMode, enabled: Boolean, onSelect: (AddMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeTab(
            text = L.t("Ссылки", "Links"),
            selected = mode == AddMode.LINKS,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(AddMode.LINKS) },
        )
        ModeTab(
            text = L.t("Подписка", "Subscription"),
            selected = mode == AddMode.SUBSCRIPTION,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(AddMode.SUBSCRIPTION) },
        )
        ModeTab(
            text = "QR",
            selected = mode == AddMode.QR,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(AddMode.QR) },
        )
    }
}

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = cutRect(6.dp)
    val borderColor = if (selected) palette.accent else palette.cardBorder
    val backgroundColor = if (selected) palette.accent.copy(alpha = 0.12f) else Color.Transparent
    val textColor = if (selected) palette.accent else palette.textSecondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(text = text, style = HudType.label(12.sp), color = textColor)
    }
}

@Composable
private fun DialogButton(text: String, accent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = cutRect(6.dp)
    val textColor = when {
        !enabled -> palette.textSecondary.copy(alpha = 0.4f)
        accent -> palette.accent
        else -> palette.textSecondary
    }
    val borderColor = if (accent && enabled) palette.accent.copy(alpha = 0.6f) else palette.cardBorder

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text = text, style = HudType.label(12.sp), color = textColor)
    }
}

/**
 * Текстовое поле в стиле HUD — переиспользуемая обёртка над Material3
 * OutlinedTextField со срезанным углом (cutRect) и цветами из палитры,
 * вместо скруглённого материального инпута по умолчанию.
 */
@Composable
private fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    monospaced: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val palette = LocalPalette.current
    val style = if (monospaced) HudType.code(13.sp) else HudType.body(13.sp)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = { Text(placeholder, style = style, color = palette.textSecondary) },
        textStyle = style.copy(color = palette.textPrimary),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = cutRect(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.cardBorder,
            disabledBorderColor = palette.cardBorder.copy(alpha = 0.5f),
            cursorColor = palette.accent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary,
        ),
    )
}
