package com.stailegrow.maxstrike.core

import com.stailegrow.maxstrike.model.Security
import com.stailegrow.maxstrike.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test
    fun `reality plus vision link parses core fields`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=reality&sni=example.org&fp=chrome" +
            "&pbk=publicKeyValue&sid=abcd&spx=%2F&flow=xtls-rprx-vision&type=tcp#My%20Server"

        val config = LinkParser.parse(link)

        assertEquals("11111111-2222-3333-4444-555555555555", config.userID)
        assertEquals("example.com", config.address)
        assertEquals(443, config.port)
        assertEquals("My Server", config.name)
        assertEquals(Security.REALITY, config.security)
        assertEquals(Transport.TCP, config.transport)
        assertEquals("example.org", config.sni)
        assertEquals("chrome", config.fingerprint)
        assertEquals("publicKeyValue", config.publicKey)
        assertEquals("abcd", config.shortID)
        assertEquals("xtls-rprx-vision", config.flow)
    }

    @Test
    fun `xhttp link parses transport-specific fields`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?security=tls&type=xhttp&path=%2Fapi&host=cdn.example.com&mode=stream-up&sni=example.com#XHTTP"

        val config = LinkParser.parse(link)

        assertEquals(Transport.XHTTP, config.transport)
        assertEquals(Security.TLS, config.security)
        assertEquals("/api", config.path)
        assertEquals("cdn.example.com", config.host)
        assertEquals("stream-up", config.xhttpMode)
    }

    @Test
    fun `splithttp legacy type is normalized to xhttp`() {
        val link = "vless://uuid@example.com:443?type=splithttp&path=%2Fold"
        val config = LinkParser.parse(link)
        assertEquals(Transport.XHTTP, config.transport)
    }

    @Test
    fun `missing user id throws`() {
        assertThrows(LinkParser.ParseException.MissingUser::class.java) {
            LinkParser.parse("vless://@example.com:443")
        }
    }

    @Test
    fun `unsupported scheme throws`() {
        val ex = assertThrows(LinkParser.ParseException.UnsupportedScheme::class.java) {
            LinkParser.parse("vmess://something")
        }
        assertEquals("vmess", ex.scheme)
    }

    @Test
    fun `parseMany skips blank lines and comments, collects errors separately`() {
        val text = """
            # мои серверы
            vless://uuid1@a.example.com:443?security=none#One

            vmess://not-supported-yet
            vless://uuid2@b.example.com:8443?security=tls#Two
        """.trimIndent()

        val result = LinkParser.parseMany(text)

        assertEquals(2, result.configs.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().contains("vmess"))
    }

    @Test
    fun `allowInsecure recognizes 1 and true, defaults to false`() {
        assertTrue(LinkParser.parse("vless://u@h:443?allowInsecure=1").allowInsecure)
        assertTrue(LinkParser.parse("vless://u@h:443?allowInsecure=true").allowInsecure)
        assertFalse(LinkParser.parse("vless://u@h:443").allowInsecure)
    }

    @Test
    fun `alpn list is split and trimmed`() {
        val config = LinkParser.parse("vless://u@h:443?alpn=h2%2C%20http%2F1.1")
        assertEquals(listOf("h2", "http/1.1"), config.alpn)
    }

    @Test
    fun `empty query values are treated as absent`() {
        val config = LinkParser.parse("vless://u@h:443?sni=&fp=chrome")
        assertNull(config.sni)
        assertEquals("chrome", config.fingerprint)
    }
}
