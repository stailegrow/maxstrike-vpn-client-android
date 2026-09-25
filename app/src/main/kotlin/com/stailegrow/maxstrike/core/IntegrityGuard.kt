package com.stailegrow.maxstrike.core

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import com.stailegrow.maxstrike.BuildConfig
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Проверка подписи APK. Релизная сборка знает SHA-256 сертификата, которым её
 * подписывает keystore/release.keystore. Если APK распаковали, изменили и
 * пересобрали чужим ключом — хэш не совпадёт, и приложение молча закроется
 * при запуске. Debug-сборки не проверяются, чтобы не мешать разработке.
 *
 * Важно: release-сборка без keystore.properties останется неподписанной или
 * подписанной другим ключом и тоже будет закрываться — это ожидаемо. При
 * смене релизного ключа хэш ниже нужно обновить (keytool -list -v).
 */
internal object IntegrityGuard {

    private const val EXPECTED_SHA256 = "50AD601D4E873259A772907ACDF154ADBE3E5AFF2CFB8ED0FD8DECF24CD40185"

    fun enforce(context: Context) {
        if (BuildConfig.DEBUG) return
        val actual = runCatching { signerSha256(context) }.getOrNull()
        if (actual == null || !actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }

    @Suppress("DEPRECATION")
    private fun signerSha256(context: Context): String? {
        val pm = context.packageManager
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signatures?.singleOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }
}
