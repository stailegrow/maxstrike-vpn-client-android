package com.stailegrow.maxstrike.model

// : java.io.Serializable — тот же повод, что у ProxyConfig: едет в Intent
// при запуске VPN-сервиса.
data class RoutingConfig(
    val presetID: String = "global",

    val directSites: List<String> = emptyList(),
    val directIP: List<String> = emptyList(),
    val proxySites: List<String> = emptyList(),
    val proxyIP: List<String> = emptyList(),
    val blockSites: List<String> = emptyList(),
    val blockIP: List<String> = emptyList(),

    val bypassLAN: Boolean = true,
    val directDomains: List<String> = emptyList(),

    val domainStrategy: String = "IPIfNonMatch",

    val domesticDNS: String = "https://77.88.8.8/dns-query",
    val remoteDNS: String = "https://8.8.8.8/dns-query",
    val dnsHosts: Map<String, String> = emptyMap(),

    val geositeURL: String = "",
    val geoipURL: String = "",
) : java.io.Serializable {
    val effectiveDirectDomains: List<String>
        get() {
            val manual = directDomains.map { it.trim() }.filter { it.isNotEmpty() }
            return if (bypassLAN) manual + localDomainPatterns else manual
        }

    val needsGeoAssets: Boolean
        get() {
            val all = directSites + proxySites + blockSites + directIP + proxyIP + blockIP
            return all.any { it.startsWith("geosite:") || it.startsWith("geoip:") }
        }

    companion object {
        val localDomainPatterns = listOf("regexp:^[^.]+$", "domain:local")
    }
}

data class RoutingPreset(
    val id: String,
    val titleRU: String,
    val titleEN: String,
    val subtitleRU: String,
    val subtitleEN: String,
    val make: () -> RoutingConfig,
) {
    companion object {
        val global = RoutingPreset(
            id = "global",
            titleRU = "Глобально",
            titleEN = "Global",
            subtitleRU = "Весь трафик через VPN, мимо идут только локальные адреса",
            subtitleEN = "All traffic through the VPN; only local addresses go around it",
            make = {
                RoutingConfig(presetID = "global", domainStrategy = "AsIs", domesticDNS = "", remoteDNS = "")
            },
        )

        val bypassRU = RoutingPreset(
            id = "bypass-ru",
            titleRU = "Обход РФ",
            titleEN = "Bypass RU",
            subtitleRU = "Российские сайты и сервисы идут мимо VPN, остальное — через",
            subtitleEN = "Russian sites and services go around the VPN, the rest goes through it",
            make = {
                RoutingConfig(
                    presetID = "bypass-ru",
                    directSites = listOf(
                        "geosite:private", "geosite:category-ru", "geosite:whitelist",
                        "geosite:microsoft", "geosite:apple", "geosite:epicgames",
                        "geosite:riot", "geosite:escapefromtarkov", "geosite:steam",
                        "geosite:twitch", "geosite:pinterest", "geosite:faceit",
                    ),
                    directIP = listOf("geoip:private", "geoip:direct"),
                    proxySites = listOf(
                        "geosite:google-play", "geosite:github", "geosite:twitch-ads",
                        "geosite:youtube", "geosite:telegram",
                    ),
                    blockSites = listOf("geosite:win-spy", "geosite:torrent", "geosite:category-ads"),
                    dnsHosts = mapOf(
                        "lkfl2.nalog.ru" to "213.24.64.175",
                        "lknpd.nalog.ru" to "213.24.64.181",
                    ),
                )
            },
        )

        val all = listOf(bypassRU, global)
    }
}
