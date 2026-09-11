package com.stailegrow.maxstrike.core

import com.stailegrow.maxstrike.model.ProxyConfig
import com.stailegrow.maxstrike.model.RoutingConfig
import com.stailegrow.maxstrike.model.RoutingPreset
import com.stailegrow.maxstrike.model.Security
import com.stailegrow.maxstrike.model.Transport
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    data class Options(
        val socksPort: Int = 10808,
        val httpPort: Int = 10809,
        val logLevel: String = "warning",
        val logPath: String? = null,
        val routing: RoutingConfig = RoutingPreset.global.make(),
    )

    fun makeJSON(config: ProxyConfig, options: Options = Options()): String =
        makeTree(config, options).toString(2)

    fun makeTree(config: ProxyConfig, options: Options): JSONObject {
        val log = JSONObject().put("loglevel", options.logLevel)
        options.logPath?.let { log.put("error", it) }

        val root = JSONObject()
            .put("log", log)
            .put("inbounds", inbounds(options))
            .put(
                "outbounds",
                JSONArray().put(outbound(config)).put(directOutbound()).put(blockOutbound()),
            )
            .put("routing", routing(options.routing))

        dns(options.routing)?.let { root.put("dns", it) }
        return root
    }

    private fun inbounds(options: Options): JSONArray {
        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            .put("routeOnly", false)

        val socks = JSONObject()
            .put("tag", "socks")
            .put("listen", "127.0.0.1")
            .put("port", options.socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", sniffing)

        val http = JSONObject()
            .put("tag", "http")
            .put("listen", "127.0.0.1")
            .put("port", options.httpPort)
            .put("protocol", "http")
            .put("settings", JSONObject())
            .put("sniffing", sniffing)

        return JSONArray().put(socks).put(http)
    }

    private fun directOutbound(): JSONObject =
        JSONObject().put("tag", "direct").put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))

    private fun blockOutbound(): JSONObject =
        JSONObject().put("tag", "block").put("protocol", "blackhole").put("settings", JSONObject())

    val privateRanges = listOf(
        "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
        "169.254.0.0/16", "100.64.0.0/10", "::1/128", "fc00::/7", "fe80::/10",
    )

    fun routing(config: RoutingConfig): JSONObject {
        val rules = JSONArray()

        if (config.bypassLAN) {
            rules.put(
                JSONObject().put("type", "field")
                    .put("ip", JSONArray(privateRanges))
                    .put("outboundTag", "direct"),
            )
        }

        appendRule(rules, config.blockSites, config.blockIP, "block")
        appendRule(rules, config.effectiveDirectDomains, emptyList(), "direct")
        appendRule(rules, config.proxySites, config.proxyIP, "proxy")
        appendRule(rules, config.directSites, config.directIP, "direct")

        return JSONObject().put("domainStrategy", config.domainStrategy).put("rules", rules)
    }

    private fun appendRule(rules: JSONArray, domains: List<String>, ips: List<String>, tag: String) {
        if (domains.isNotEmpty()) {
            rules.put(
                JSONObject().put("type", "field").put("domain", JSONArray(domains)).put("outboundTag", tag),
            )
        }
        if (ips.isNotEmpty()) {
            rules.put(
                JSONObject().put("type", "field").put("ip", JSONArray(ips)).put("outboundTag", tag),
            )
        }
    }

    fun dns(config: RoutingConfig): JSONObject? {
        if (config.remoteDNS.isEmpty() && config.domesticDNS.isEmpty() && config.effectiveDirectDomains.isEmpty()) {
            return null
        }

        val servers = JSONArray()
        val localDomains = config.effectiveDirectDomains
        if (localDomains.isNotEmpty()) {
            servers.put(
                JSONObject().put("address", "localhost")
                    .put("domains", JSONArray(localDomains))
                    .put("skipFallback", true),
            )
        }
        if (config.domesticDNS.isNotEmpty() && config.directSites.isNotEmpty()) {
            servers.put(
                JSONObject().put("address", config.domesticDNS)
                    .put("domains", JSONArray(config.directSites))
                    .put("skipFallback", true),
            )
        }
        if (config.remoteDNS.isNotEmpty()) {
            servers.put(config.remoteDNS)
        }
        if (servers.length() == 0) return null

        val dns = JSONObject().put("servers", servers)
        if (config.dnsHosts.isNotEmpty()) {
            dns.put("hosts", JSONObject(config.dnsHosts))
        }
        return dns
    }

    fun probeTree(servers: List<ProxyConfig>, basePort: Int): JSONObject {
        val inbounds = JSONArray()
        val outbounds = JSONArray()
        val rules = JSONArray()

        servers.forEachIndexed { index, server ->
            val inTag = "probe-in-$index"
            val outTag = "probe-out-$index"

            inbounds.put(
                JSONObject().put("tag", inTag).put("listen", "127.0.0.1")
                    .put("port", basePort + index).put("protocol", "http")
                    .put("settings", JSONObject()),
            )

            val out = outbound(server)
            out.put("tag", outTag)
            outbounds.put(out)

            rules.put(
                JSONObject().put("type", "field")
                    .put("inboundTag", JSONArray(listOf(inTag)))
                    .put("outboundTag", outTag),
            )
        }
        outbounds.put(blockOutbound())

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "none"))
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
    }

    fun probeJSON(servers: List<ProxyConfig>, basePort: Int): String =
        probeTree(servers, basePort).toString(2)

    fun outbound(config: ProxyConfig): JSONObject {
        val user = JSONObject().put("id", config.userID).put("encryption", config.encryption)
        if (!config.flow.isNullOrEmpty() && config.transport == Transport.TCP) {
            user.put("flow", config.flow)
        }

        val vnext = JSONObject()
            .put("address", config.address)
            .put("port", config.port)
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", config.kind.rawValue)
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", streamSettings(config))
    }

    fun streamSettings(config: ProxyConfig): JSONObject {
        val stream = JSONObject()
            .put("network", config.transport.rawValue)
            .put("security", if (config.security == Security.NONE) "none" else config.security.rawValue)

        when (config.security) {
            Security.REALITY -> stream.put("realitySettings", realitySettings(config))
            Security.TLS -> stream.put("tlsSettings", tlsSettings(config))
            Security.NONE -> {}
        }

        config.transport.settingsKey?.let { key ->
            stream.put(key, transportSettings(config))
        }

        return stream
    }

    private fun realitySettings(config: ProxyConfig): JSONObject {
        val reality = JSONObject().put("show", false)
        config.sni?.let { reality.put("serverName", it) }
        config.fingerprint?.let { reality.put("fingerprint", it) }
        config.publicKey?.let { reality.put("publicKey", it) }
        config.shortID?.let { reality.put("shortId", it) }
        config.spiderX?.let { reality.put("spiderX", it) }
        return reality
    }

    private fun tlsSettings(config: ProxyConfig): JSONObject {
        val tls = JSONObject().put("allowInsecure", config.allowInsecure)
        config.sni?.let { tls.put("serverName", it) }
        config.fingerprint?.let { tls.put("fingerprint", it) }
        if (config.alpn.isNotEmpty()) tls.put("alpn", JSONArray(config.alpn))
        return tls
    }

    private fun transportSettings(config: ProxyConfig): JSONObject = when (config.transport) {
        Transport.TCP -> JSONObject()

        Transport.WS, Transport.HTTPUPGRADE -> JSONObject().apply {
            config.path?.let { put("path", it) }
            config.host?.let { put("host", it) }
        }

        Transport.GRPC -> JSONObject().apply {
            (config.serviceName ?: config.path)?.let { put("serviceName", it) }
        }

        Transport.HTTP -> JSONObject().apply {
            config.path?.let { put("path", it) }
            config.host?.let { put("host", JSONArray().put(it)) }
        }

        Transport.XHTTP -> xhttpSettings(config)
    }

    fun xhttpSettings(config: ProxyConfig): JSONObject {
        val settings = config.xhttpExtraJSON?.let {
            try { JSONObject(it) } catch (e: Exception) { JSONObject() }
        } ?: JSONObject()

        config.path?.let { settings.put("path", it) }
        config.host?.let { settings.put("host", it) }
        config.xhttpMode?.let { settings.put("mode", it) }
        config.xPaddingBytes?.let { settings.put("xPaddingBytes", it) }

        return settings
    }
}
