package com.stailegrow.maxstrike.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionFetcherTest {

    @Test
    fun `decodeBody returns plain links as is`() {
        val body = "vless://a@h:443\n# comment\nvless://b@h:443\n"
        val links = SubscriptionFetcher.decodeBody(body)
        assertEquals(listOf("vless://a@h:443", "vless://b@h:443"), links)
    }

    @Test
    fun `decodeBody decodes base64 body with url-safe alphabet and missing padding`() {
        val plain = "vless://a@h:443\nvless://b@h:443"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(plain.toByteArray())

        val links = SubscriptionFetcher.decodeBody(encoded)
        assertEquals(listOf("vless://a@h:443", "vless://b@h:443"), links)
    }

    @Test
    fun `decodeBase64 round-trips standard padded base64`() {
        val original = "hello, max strike"
        val encoded = java.util.Base64.getEncoder().encodeToString(original.toByteArray())
        assertEquals(original, SubscriptionFetcher.decodeBase64(encoded))
    }

    @Test
    fun `decodeHeaderValue unwraps base64 prefix`() {
        val encoded = java.util.Base64.getEncoder().encodeToString("Мой сервер".toByteArray())
        assertEquals("Мой сервер", SubscriptionFetcher.decodeHeaderValue("base64:$encoded"))
        assertEquals("Plain Title", SubscriptionFetcher.decodeHeaderValue("Plain Title"))
        assertNull(SubscriptionFetcher.decodeHeaderValue("   "))
    }

    @Test
    fun `parseUserInfo reads traffic and treats expire=0 as unlimited`() {
        val (used, total, expiresAt) = SubscriptionFetcher.parseUserInfo("upload=100; download=200; total=1000; expire=0")
        assertEquals(300L, used)
        assertEquals(1000L, total)
        assertNull(expiresAt)
    }

    @Test
    fun `parseUserInfo converts a nonzero expire to milliseconds`() {
        val (_, _, expiresAt) = SubscriptionFetcher.parseUserInfo("expire=1000")
        assertEquals(1_000_000L, expiresAt)
    }
}
