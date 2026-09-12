package com.stailegrow.maxstrike.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.stailegrow.maxstrike.core.CameraQRDecoder
import com.stailegrow.maxstrike.core.L
import com.stailegrow.maxstrike.ui.theme.CornerTicks
import com.stailegrow.maxstrike.ui.theme.HudType
import com.stailegrow.maxstrike.ui.theme.LocalPalette
import com.stailegrow.maxstrike.ui.theme.cutRect
import java.util.concurrent.Executors

/**
 * Предпросмотр камеры с разбором QR-кода в реальном времени —
 * Android-аналог QRScannerView.swift (CameraScanner + QRScannerSheet).
 * На маке это отдельное окно с AVFoundation, здесь — один из режимов
 * AddServerDialog на CameraX, но по сути то же самое: включаем камеру,
 * читаем кадры детектором, отдаём первую же найденную строку наружу и
 * молчим про остальное (какая это ссылка — решает не сканер, а
 * ServerStore.quickAdd на стороне вызывающего).
 *
 * active управляет и запросом разрешения, и самим биндингом камеры:
 * пока родитель занят обработкой уже найденного кода (isWorking) или
 * показывает другой режим диалога, камера должна быть выключена, а не
 * просто "не отрисована" — иначе она продолжает жечь батарею и держать
 * устройство занятым в фоне.
 */
@Composable
fun QrScanner(active: Boolean, onFound: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var wasDenied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) wasDenied = true
    }

    // Разрешение просим один раз, как только режим стал активным — не на
    // каждую рекомпозицию (иначе системный диалог выскакивал бы заново
    // при любом чужом изменении состояния диалога).
    LaunchedEffect(active) {
        if (active && !hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember { PreviewView(context) }
    // Не @Composable-состояние: значение должно быть видно немедленно
    // внутри колбэка анализатора (другой поток), а не только после
    // следующей рекомпозиции.
    val hasHandled = remember { BooleanHolder() }

    DisposableEffect(active, hasPermission) {
        if (!active || !hasPermission) {
            return@DisposableEffect onDispose {}
        }

        hasHandled.value = false
        cameraError = null

        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        // Отдельный флаг, а не только boundProvider == null: ProcessCameraProvider
        // резолвится асинхронно, и если этот DisposableEffect успеет
        // задиспоуситься (смена режима/поворот/уход в фон) до того, как future
        // выполнится, коллбэк ниже без этой проверки всё равно забиндил бы
        // камеру после того, как её должны были выключить — камера осталась бы
        // висеть до следующего входа в режим QR.
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val provider = providerFuture.get()
                boundProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    analyzeFrame(image, hasHandled, onFound)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                cameraError = e.message ?: L.t("Не удалось включить камеру.", "Could not start the camera.")
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            boundProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    val shape = cutRect(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(palette.card)
            .border(BorderStroke(1.dp, palette.cardBorder), shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasPermission -> {
                AndroidView(modifier = Modifier.matchParentSize(), factory = { previewView })
                // Рамка-прицел — чисто декоративная, кликов не перехватывает.
                CornerTicks(
                    color = palette.accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(160.dp),
                    length = 16.dp,
                    strokeWidth = 1.6.dp,
                )
                cameraError?.let { message ->
                    ScannerMessage(text = message)
                }
            }
            wasDenied -> {
                ScannerMessage(
                    text = L.t(
                        "Нет доступа к камере. Разрешите его в настройках приложения.",
                        "No camera access. Allow it in the app settings.",
                    ),
                )
                SettingsButton(modifier = Modifier.padding(top = 96.dp))
            }
            else -> {
                ScannerMessage(text = L.t("Запрашиваю доступ к камере…", "Requesting camera access…"))
            }
        }
    }
}

/** Разбор одного кадра — вне композиции: колбэк CameraX вызывается со
 *  своего исполнителя, обращаться там к состоянию Compose нельзя. */
private fun analyzeFrame(image: ImageProxy, hasHandled: BooleanHolder, onFound: (String) -> Unit) {
    if (hasHandled.value) {
        image.close()
        return
    }
    try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val raw = ByteArray(width * height)
        buffer.rewind()
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(raw)
        } else {
            val row = ByteArray(rowStride)
            var offset = 0
            for (r in 0 until height) {
                buffer.position(r * rowStride)
                val length = minOf(rowStride, buffer.remaining())
                buffer.get(row, 0, length)
                for (c in 0 until width) {
                    raw[offset] = row[c * pixelStride]
                    offset++
                }
            }
        }

        val (data, w, h) = CameraQRDecoder.rotateY(raw, width, height, image.imageInfo.rotationDegrees)
        val code = CameraQRDecoder.decode(data, w, h)
        if (code != null && !hasHandled.value) {
            hasHandled.value = true
            onFound(code)
        }
    } finally {
        image.close()
    }
}

/** Простой мутируемый флаг, который нужно читать/писать из фонового
 *  потока анализатора немедленно — обычный remember { mutableStateOf }
 *  тут не подходит: запись в состояние Compose с чужого потока не
 *  гарантирует, что следующий же кадр (тоже на чужом потоке, до всякой
 *  рекомпозиции) её увидит. */
private class BooleanHolder {
    @Volatile var value: Boolean = false
}

@Composable
private fun ScannerMessage(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Text(
        text = text,
        style = HudType.body(11.5.sp),
        color = palette.textSecondary,
        modifier = modifier.padding(horizontal = 24.dp),
    )
}

@Composable
private fun SettingsButton(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val shape = cutRect(6.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, palette.accent.copy(alpha = 0.6f)), shape)
            .clickable {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = L.t("Открыть настройки", "Open settings"), style = HudType.label(11.sp), color = palette.accent)
    }
}
