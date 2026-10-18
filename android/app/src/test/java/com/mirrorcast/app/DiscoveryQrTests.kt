package com.mirrorcast.app

import com.mirrorcast.app.discovery.ReceiverDiscovery
import org.junit.Assert.*
import org.junit.Test

class DiscoveryQrTests {

    @Test
    fun testValidQrUriParsing() {
        val qrUri = "mirrorcast://pair?ip=192.168.1.150&port=8089&pin=654321&name=LivingRoom-PC"
        val receiver = ReceiverDiscovery.parseQrUri(qrUri)

        assertNotNull(receiver)
        assertEquals("192.168.1.150", receiver?.host)
        assertEquals(8089, receiver?.port)
        assertEquals("654321", receiver?.pin)
        assertEquals("LivingRoom-PC", receiver?.name)
    }

    @Test
    fun testInvalidQrUriReturnsNull() {
        assertNull(ReceiverDiscovery.parseQrUri("https://google.com"))
        assertNull(ReceiverDiscovery.parseQrUri("mirrorcast://invalid"))
        assertNull(ReceiverDiscovery.parseQrUri("random text without scheme"))
    }
}
