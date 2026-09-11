package com.stailegrow.maxstrike.model

import java.util.UUID

enum class ProxyProtocol(val rawValue: String) {
    VLESS("vless"),
}

enum class Security(val rawValue: String) {
    NONE("none"), TLS("tls"), REALITY("reality");

    companion object {
        fun fromRaw(value: String?): Security = entries.firstOrNull { it.rawValue == value } ?: NONE
    }
}

enum class Transport(val rawValue: String) {
    TCP("tcp"), WS("ws"), GRPC("grpc"), HTTP("http"), XHTTP("xhttp"), HTTPUPGRADE("httpupgrade");

    val settingsKey: String?
        get() = when (this) {
            TCP -> null
            WS -> "wsSettings"
            GRPC -> "grpcSettings"
            HTTP -> "httpSettings"
            XHTTP -> "xhttpSettings"
            HTTPUPGRADE -> "httpupgradeSettings"
        }

    companion object {
        fun fromRaw(value: String?): Transport = entries.firstOrNull { it.rawValue == value } ?: TCP
    }
}

// : java.io.Serializable — чтобы конфиг сервера можно было положить в Intent
// при запуске MaxStrikeVpnService (см. core/ConnectionManager.kt). Все поля —
// примитивы/строки/списки строк/энамы, так что сериализуется без сюрпризов.
data class ProxyConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: ProxyProtocol = ProxyProtocol.VLESS,

    val address: String = "",
    val port: Int = 443,
    val userID: String = "",
    val encryption: String = "none",
    val flow: String? = null,

    val security: Security = Security.NONE,
    val transport: Transport = Transport.TCP,

    val sni: String? = null,
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortID: String? = null,
    val spiderX: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,

    val path: String? = null,
    val host: String? = null,
    val serviceName: String? = null,
    val headerType: String? = null,

    val xhttpMode: String? = null,
    val xhttpExtraJSON: String? = null,
    val xPaddingBytes: String? = null,

    val sourceLink: String? = null,
    val subscriptionID: String? = null,
) : java.io.Serializable {
    val identityKey: String
        get() = "${kind.rawValue}|$address|$port|$userID|${transport.rawValue}|${path ?: ""}"

    val displayName: String
        get() = name.ifEmpty { "$address:$port" }

    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (security != Security.NONE) parts.add(security.rawValue)
            parts.add(transport.rawValue)
            if (!flow.isNullOrEmpty()) parts.add(flow)
            if (transport == Transport.XHTTP && !xhttpMode.isNullOrEmpty()) parts.add(xhttpMode)
            return parts.joinToString(" · ")
        }
}
