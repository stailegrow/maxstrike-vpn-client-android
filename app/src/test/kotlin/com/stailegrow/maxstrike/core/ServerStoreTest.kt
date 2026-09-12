package com.stailegrow.maxstrike.core

import com.stailegrow.maxstrike.model.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerStoreTest {

    private fun config(address: String, subscriptionID: String? = null, name: String = "") =
        ProxyConfig(
            address = address,
            port = 443,
            userID = "u-$address",
            name = name,
            subscriptionID = subscriptionID,
            sourceLink = "vless://u-$address@$address:443",
        )

    @Test
    fun `merge keeps id for servers that survive and drops the ones that vanished`() {
        val kept = config("kept.example", subscriptionID = "sub")
        val gone = config("gone.example", subscriptionID = "sub")
        val other = config("other.example", subscriptionID = null)

        val fetchedKept = kept.copy(id = "ignored-new-id", name = "renamed on panel")
        val fetchedNew = config("new.example")

        val result = ServerStore.merge(
            existing = listOf(kept, gone, other),
            fetched = listOf(fetchedKept, fetchedNew),
            subscriptionID = "sub",
        )

        assertEquals(1, result.added)
        assertEquals(1, result.removed)
        assertEquals(1, result.kept)

        val survivedByOldID = result.servers.first { it.address == "kept.example" }
        assertEquals(kept.id, survivedByOldID.id)
        assertEquals("renamed on panel", survivedByOldID.name)

        assertTrue(result.servers.none { it.address == "gone.example" })
        assertTrue(result.servers.any { it.address == "other.example" })
        assertTrue(result.servers.any { it.address == "new.example" })
    }

    @Test
    fun `merge tags every fetched server with the subscription id`() {
        val fetched = listOf(config("a.example"), config("b.example"))
        val result = ServerStore.merge(existing = emptyList(), fetched = fetched, subscriptionID = "sub-1")

        assertTrue(result.servers.all { it.subscriptionID == "sub-1" })
        assertEquals(2, result.added)
        assertEquals(0, result.kept)
        assertEquals(0, result.removed)
    }
}
