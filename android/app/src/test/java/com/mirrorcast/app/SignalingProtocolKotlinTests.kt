package com.mirrorcast.app

import com.mirrorcast.app.signaling.*
import org.junit.Assert.*
import org.junit.Test

class SignalingProtocolKotlinTests {

    @Test
    fun testPairRequestSerialization() {
        val payload = PairRequestPayload(
            deviceName = "Pixel 8 Pro",
            pin = "123456",
            androidVersion = 14,
            screenWidth = 1080,
            screenHeight = 2400,
            preferredFps = 60,
            preferredCodec = "H264"
        )

        val msg = SignalingMessage.create("pair_request", null, payload)
        val json = msg.toJson()

        assertNotNull(json)
        assertTrue(json.contains("\"type\":\"pair_request\""))
        assertTrue(json.contains("\"version\":\"1.0\""))

        val parsed = SignalingMessage.fromJson(json)
        assertNotNull(parsed)
        assertEquals("pair_request", parsed?.type)

        val parsedPayload = parsed?.getPayload<PairRequestPayload>()
        assertNotNull(parsedPayload)
        assertEquals("Pixel 8 Pro", parsedPayload?.deviceName)
        assertEquals("123456", parsedPayload?.pin)
        assertEquals(1080, parsedPayload?.screenWidth)
        assertEquals(2400, parsedPayload?.screenHeight)
    }

    @Test
    fun testSdpOfferAndAnswerSerialization() {
        val sdpContent = "v=0\r\no=- 987654 2 IN IP4 192.168.1.100\r\ns=-\r\n"
        val offerPayload = SdpPayload(sdp = sdpContent, type = "offer")
        val msg = SignalingMessage.create("offer", "sess-test-456", offerPayload)

        val json = msg.toJson()
        val parsed = SignalingMessage.fromJson(json)

        assertNotNull(parsed)
        assertEquals("sess-test-456", parsed?.sessionId)

        val parsedSdp = parsed?.getPayload<SdpPayload>()
        assertNotNull(parsedSdp)
        assertEquals(sdpContent, parsedSdp?.sdp)
        assertEquals("offer", parsedSdp?.type)
    }

    @Test
    fun testIceCandidateSerialization() {
        val candPayload = IceCandidatePayload(
            sdpMid = "0",
            sdpMLineIndex = 0,
            candidate = "candidate:1 1 UDP 2130706431 192.168.1.102 50004 typ host"
        )
        val msg = SignalingMessage.create("ice_candidate", "sess-test-456", candPayload)
        val json = msg.toJson()

        val parsed = SignalingMessage.fromJson(json)
        assertNotNull(parsed)
        val parsedCand = parsed?.getPayload<IceCandidatePayload>()
        assertNotNull(parsedCand)
        assertEquals("0", parsedCand?.sdpMid)
        assertEquals(0, parsedCand?.sdpMLineIndex)
        assertEquals("candidate:1 1 UDP 2130706431 192.168.1.102 50004 typ host", parsedCand?.candidate)
    }
}
