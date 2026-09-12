package com.stailegrow.maxstrike.core

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Разбор QR-кода из кадра камеры — Android-аналог Core/QRCode.swift.
 * Там на каждый кадр свой CIDetector (он не потокобезопасен и не
 * Sendable), здесь по той же причине заводится свой QRCodeReader на
 * каждый вызов: кодов читаем единицы в секунду, переиспользовать не на
 * чем, а делить один экземпляр между кадрами камеры не стоит риска.
 *
 * Специально ZXing, а не ML Kit: ML Kit тянет Google Play Services, а
 * это распространяемый APK-релизом (без магазина) VPN-клиент — заводить
 * для одной фичи зависимость от GMS ни к чему (см. PLAN-ANDROID.md).
 */
object CameraQRDecoder {

    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    /**
     * data — одна Y-плоскость (яркость) кадра, уже повёрнутая под нужный
     * угол (см. [rotateY] ниже). Сенсор камеры почти всегда лежит боком
     * относительно портретного превью на телефоне, и без поворота код
     * не читается вовсе — эта часть ортогональна самому декодированию,
     * поэтому и вынесена отдельной чистой функцией.
     */
    fun decode(data: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            QRCodeReader().decode(bitmap, hints).text
        } catch (e: NotFoundException) {
            // Обычное дело — в кадре пока просто нет читаемого кода.
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Поворот одноканального (яркостного) буфера на 0/90/180/270 градусов
     * по часовой стрелке — под rotationDegrees, который сообщает сама
     * камера (ImageProxy.imageInfo.rotationDegrees в CameraX). Чистая
     * перестановка байтов без единой зависимости от Android/камеры,
     * поэтому и living отдельно от кода, который читает ImageProxy
     * (см. ui/components/QrScannerView.kt).
     */
    fun rotateY(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): Triple<ByteArray, Int, Int> {
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> {
                val out = ByteArray(width * height)
                var i = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        out[i] = data[y * width + x]
                        i++
                    }
                }
                Triple(out, height, width)
            }
            180 -> {
                val out = ByteArray(data.size)
                val last = data.size - 1
                for (i in data.indices) out[last - i] = data[i]
                Triple(out, width, height)
            }
            270 -> {
                val out = ByteArray(width * height)
                var i = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        out[i] = data[y * width + x]
                        i++
                    }
                }
                Triple(out, height, width)
            }
            else -> Triple(data, width, height)
        }
    }
}
