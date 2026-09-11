package com.stailegrow.maxstrike.core

import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.ProxyProtocol
import com.stailegrow.maxstrike.model.Security
import com.stailegrow.maxstrike.model.Transport
import java.net.URI
import java.net.URLDecoder

object LinkParser {

    sealed class ParseException(message: String) : Exception(message) {
        class UnsupportedScheme(val scheme: String) :
            ParseException("Протокол $scheme:// пока не поддерживается.")
        class Malformed(val link: String) :
            ParseException("Не удалось разобрать ссылку: ${link.take(48)}…")
        object MissingUser :
            ParseException("В ссылке нет идентификатора пользователя.")
        object MissingHost :
            ParseException("В ссылке нет адреса сервера.")
        object MissingPort :
            ParseException("В ссылке нет порта.")
    }

    data class ParseResult(val configs: List<ProxyConfig>, val errors: List<String>)

    fun parseMany(text: String): ParseResult {
        val configs = mutableListOf<ProxyConfig>()
        val errors = mutableListOf<String>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            try {
                configs.add(parse(line))
            } catch (e: ParseException) {
                errors.add(e.message ?: line)
            }
        }
        return ParseResult(configs, errors)
    }

    fun parse(link: String): ProxyConfig {
        val trimmed = link.trim()
        val schemeEnd = trimmed.indexOf(':')
        if (schemeEnd <= 0) throw ParseException.Malformed(trimmed)
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        return when (scheme) {
            "vless" -> parseVless(trimmed)
            else -> throw ParseException.UnsupportedScheme(scheme)
        }
    }

    private fun parseVless(link: String): ProxyConfig {
        val uri = try {
            URI(link)
        } catch (e: Exception) {
            throw ParseException.Malformed(link)
        }
        val user = uri.userInfo?.takeIf { it.isNotEmpty() } ?: throw ParseException.MissingUser
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: throw ParseException.MissingHost
        val port = uri.port.takeIf { it != -1 } ?: throw ParseException.MissingPort

        val query = queryMap(uri.rawQuery)
        val isXhttp = normalizedTransport(query) == "xhttp"

        return ProxyConfig(
            kind = ProxyProtocol.VLESS,
            name = uri.rawFragment?.let { decode(it) } ?: "",
            address = host,
            port = port,
            userID = user,
            encryption = query["encryption"] ?: "none",
            flow = nonEmpty(query["flow"]),
            security = Security.fromRaw(query["security"]?.lowercase()),
            transport = Transport.fromRaw(normalizedTransport(query)),
            sni = nonEmpty(query["sni"]) ?: nonEmpty(query["peer"]),
            fingerprint = nonEmpty(query["fp"]),
            publicKey = nonEmpty(query["pbk"]),
            shortID = nonEmpty(query["sid"]),
            spiderX = nonEmpty(query["spx"]),
            alpn = nonEmpty(query["alpn"])?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            allowInsecure = query["allowInsecure"]?.lowercase() in setOf("1", "true"),
            path = nonEmpty(query["path"]),
            host = nonEmpty(query["host"]),
            serviceName = nonEmpty(query["serviceName"]),
            headerType = nonEmpty(query["headerType"]),
            xhttpMode = if (isXhttp) nonEmpty(query["mode"]) else null,
            xhttpExtraJSON = if (isXhttp) nonEmpty(query["extra"]) else null,
            xPaddingBytes = if (isXhttp) {
                nonEmpty(query["x_padding_bytes"]) ?: nonEmpty(query["xPaddingBytes"])
            } else null,
            sourceLink = link,
        )
    }

    private fun normalizedTransport(query: Map<String, String>): String {
        val raw = (query["type"] ?: query["net"] ?: "tcp").lowercase()
        return if (raw == "splithttp") "xhttp" else raw
    }

    private fun queryMap(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split("&")) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val name = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            result[decode(name)] = decode(value)
        }
        return result
    }

    private fun decode(value: String): String {
        val escaped = value.replace("+", "%2B")
        return try { URLDecoder.decode(escaped, "UTF-8") } catch (e: Exception) { value }
    }

    private fun nonEmpty(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}
