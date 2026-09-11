package com.stailegrow.maxstrike.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    @Test
    fun `reality plus vision link round-trips into a valid outbound`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?security=reality&sni=example.org&fp=chrome&pbk=pub&sid=abcd" +
            "&flow=xtls-rprx-vision&type=tcp#Server"
        val config = LinkParser.parse(link)

        val tree = XrayConfigBuilder.makeTree(config, XrayConfigBuilder.Options())
        val outbound = tree.getJSONArray("outbounds").getJSONObject(0)

        assertEquals("proxy", outbound.getString("tag"))
        assertEquals("vless", outbound.getString("protocol"))

        val user = outbound.getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals(config.userID, user.getString("id"))
        assertEquals("xtls-rprx-vision", user.getString("flow"))

        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("reality", stream.getString("security"))
        assertEquals("example.org", stream.getJSONObject("realitySettings").getString("serverName"))
    }

    @Test
    fun `vision flow is dropped for xhttp transport`() {
        val link = "vless://u@h:443?security=tls&type=xhttp&path=%2Fapi&flow=xtls-rprx-vision"
        val config = LinkParser.parse(link)

        val outbound = XrayConfigBuilder.outbound(config)
        val user = outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)

        assertFalse(user.has("flow"))
    }

    @Test
    fun `xhttp settings merge extra JSON with explicit link params, link params win`() {
        val link = "vless://u@h:443?security=tls&type=xhttp&path=%2Flink&host=cdn.example.com" +
            "&mode=stream-up&extra=" + java.net.URLEncoder.encode(
            """{"path":"/from-extra","xmux":{"maxConcurrency":"16"}}""",
            "UTF-8",
        )
        val config = LinkParser.parse(link)

        val settings = XrayConfigBuilder.xhttpSettings(config)

        assertEquals("/link", settings.getString("path"))
        assertEquals("cdn.example.com", settings.getString("host"))
        assertEquals("stream-up", settings.getString("mode"))
        assertEquals("16", settings.getJSONObject("xmux").getString("maxConcurrency"))
    }

    @Test
    fun `bypass LAN prepends a direct rule for private ranges`() {
        val config = LinkParser.parse("vless://u@h:443")
        val tree = XrayConfigBuilder.makeTree(config, XrayConfigBuilder.Options())

        val rules = tree.getJSONObject("routing").getJSONArray("rules")
        val firstRule = rules.getJSONObject(0)
        assertEquals("direct", firstRule.getString("outboundTag"))
        assertTrue(firstRule.getJSONArray("ip").length() == XrayConfigBuilder.privateRanges.size)
    }

    @Test
    fun `two inbounds are always socks and http on localhost`() {
        val config = LinkParser.parse("vless://u@h:443")
        val tree = XrayConfigBuilder.makeTree(
            config,
            XrayConfigBuilder.Options(socksPort = 11080, httpPort = 11081),
        )

        val inbounds = tree.getJSONArray("inbounds")
        assertEquals(2, inbounds.length())
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))
        assertEquals(11080, inbounds.getJSONObject(0).getInt("port"))
        assertEquals("http", inbounds.getJSONObject(1).getString("protocol"))
        assertEquals(11081, inbounds.getJSONObject(1).getInt("port"))
    }

    @Test
    fun `output is valid JSON text`() {
        val config = LinkParser.parse("vless://u@h:443?security=reality&sni=a.com&pbk=x&sid=y")
        val text = XrayConfigBuilder.makeJSON(config)
        JSONObject(text)
    }

    @Test
    fun `tun inbound and env fd are used when tunFileDescriptor is set`() {
        val config = LinkParser.parse("vless://u@h:443")
        val tree = XrayConfigBuilder.makeTree(
            config,
            XrayConfigBuilder.Options(tunFileDescriptor = 42, tunMtu = 1400),
        )

        val inbounds = tree.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("tun-in", inbounds.getJSONObject(0).getString("tag"))
        assertEquals("tun", inbounds.getJSONObject(0).getString("protocol"))
        assertEquals(1400, inbounds.getJSONObject(0).getJSONObject("settings").getInt("mtu"))

        assertEquals("42", tree.getJSONObject("env").getString("xray.tun.fd"))
    }

    @Test
    fun `no tunFileDescriptor keeps the socks and http inbounds, no env block`() {
        val config = LinkParser.parse("vless://u@h:443")
        val tree = XrayConfigBuilder.makeTree(config, XrayConfigBuilder.Options())

        assertEquals(2, tree.getJSONArray("inbounds").length())
        assertFalse(tree.has("env"))
    }
}
