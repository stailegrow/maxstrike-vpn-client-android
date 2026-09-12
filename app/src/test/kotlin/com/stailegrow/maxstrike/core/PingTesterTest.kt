package com.stailegrow.maxstrike.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingTesterTest {

    @Test
    fun `median of an odd count is the middle value`() {
        assertEquals(20, PingTester.median(listOf(30, 10, 20)))
    }

    @Test
    fun `median of an even count averages the two middle values`() {
        assertEquals(25, PingTester.median(listOf(10, 20, 30, 40)))
    }

    @Test
    fun `median of empty list is null`() {
        assertNull(PingTester.median(emptyList()))
    }

    @Test
    fun `ping quality thresholds match good fair poor`() {
        assertEquals(PingQuality.GOOD, PingQuality.of(50))
        assertEquals(PingQuality.FAIR, PingQuality.of(150))
        assertEquals(PingQuality.POOR, PingQuality.of(400))
    }
}
