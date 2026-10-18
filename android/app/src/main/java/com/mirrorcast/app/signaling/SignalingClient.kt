package com.mirrorcast.app.signaling

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

sealed class SignalingState {
    object Disconnected : SignalingState()
    object Connecting : SignalingState()
    object Connected : SignalingState()
    data class Paired(val sessionId: String, val receiverName: String) : SignalingState()
    data class Error(val message: String) : SignalingState()
}

class SignalingClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "SignalingClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive
        .build()

    private var webSocket: WebSocket? = null
    private var activeSessionId: String? = null

    private val _connectionState = MutableStateFlow<SignalingState>(SignalingState.Disconnected)
    val connectionState: StateFlow<SignalingState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages.asSharedFlow()

    fun connect(host: String, port: Int, pin: String, width: Int, height: Int, fps: Int = 60) {
        disconnect()

        val cleanHost = host.trim()
            .removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(":")
            .trimEnd('/')

        val parsedPort = if (host.contains(":") && !host.startsWith("[")) {
            host.substringAfter(":").trim().removeSuffix("/").removeSuffix("/ws").toIntOrNull() ?: port
        } else {
            port
        }

        val url = "ws://$cleanHost:$parsedPort/ws"
        Log.d(TAG, "Connecting to signaling server at $url")
        _connectionState.value = SignalingState.Connecting

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Signaling WebSocket connected")
                _connectionState.value = SignalingState.Connected
                sendPairRequest(pin, width, height, fps)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "Incoming signaling message: $text")
                val msg = SignalingMessage.fromJson(text) ?: return

                scope.launch {
                    handleIncomingMessage(msg)
                    _incomingMessages.emit(msg)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Signaling closing: $reason")
                _connectionState.value = SignalingState.Disconnected
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling failure: ${t.message}")
                _connectionState.value = SignalingState.Error(t.message ?: "Signaling connection failed")
            }
        })
    }

    private fun handleIncomingMessage(msg: SignalingMessage) {
        when (msg.type) {
            "pair_response" -> {
                val payload = msg.getPayload<PairResponsePayload>()
                if (payload?.status == "approved" && msg.sessionId != null) {
                    activeSessionId = msg.sessionId
                    _connectionState.value = SignalingState.Paired(msg.sessionId, payload.receiverName)
                    Log.d(TAG, "Pairing approved by ${payload.receiverName}, session: ${msg.sessionId}")
                } else if (payload?.status == "invalid_pin") {
                    _connectionState.value = SignalingState.Error("Invalid PIN code. Please enter the 4 digits on your PC.")
                } else if (payload?.status == "rejected") {
                    _connectionState.value = SignalingState.Error("Connection was declined on your PC.")
                } else {
                    _connectionState.value = SignalingState.Error(payload?.status ?: "Pairing failed")
                }
            }
            "disconnect" -> {
                _connectionState.value = SignalingState.Disconnected
            }
        }
    }

    fun sendPairRequest(pin: String, width: Int, height: Int, fps: Int) {
        val payload = PairRequestPayload(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            pin = pin,
            androidVersion = Build.VERSION.SDK_INT,
            screenWidth = width,
            screenHeight = height,
            preferredFps = fps,
            preferredCodec = "H264"
        )
        sendMessage(SignalingMessage.create("pair_request", null, payload))
    }

    fun sendOffer(sdp: String) {
        val payload = SdpPayload(sdp = sdp, type = "offer")
        sendMessage(SignalingMessage.create("offer", activeSessionId, payload))
    }

    fun sendIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        val payload = IceCandidatePayload(candidate = candidate, sdpMid = sdpMid, sdpMLineIndex = sdpMLineIndex)
        sendMessage(SignalingMessage.create("ice_candidate", activeSessionId, payload))
    }

    fun sendStats(fps: Double, bitrateKbps: Double, packetsLost: Long, rttMs: Double, width: Int, height: Int) {
        val payload = StatsPayload(
            fps = fps,
            bitrateKbps = bitrateKbps,
            packetsLost = packetsLost,
            rttMs = rttMs,
            width = width,
            height = height
        )
        sendMessage(SignalingMessage.create("stats", activeSessionId, payload))
    }

    fun sendMessage(msg: SignalingMessage) {
        val json = msg.toJson()
        webSocket?.send(json)
    }

    fun disconnect() {
        if (activeSessionId != null) {
            sendMessage(SignalingMessage.create("disconnect", activeSessionId, DisconnectPayload("user_stopped")))
        }
        try {
            webSocket?.close(1000, "Normal Closure")
        } catch (e: Exception) { }
        webSocket = null
        activeSessionId = null
        _connectionState.value = SignalingState.Disconnected
    }
}
