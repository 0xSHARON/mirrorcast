package com.mirrorcast.app.signaling

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class SignalingMessage(
    @SerializedName("version")
    val version: String = "1.0",
    @SerializedName("type")
    val type: String,
    @SerializedName("sessionId")
    val sessionId: String? = null,
    @SerializedName("payload")
    val payload: JsonElement? = null
) {
    companion object {
        @PublishedApi
        internal val gson = Gson()

        fun <T> create(type: String, sessionId: String? = null, payload: T): SignalingMessage {
            val jsonTree = gson.toJsonTree(payload)
            return SignalingMessage(
                version = "1.0",
                type = type,
                sessionId = sessionId,
                payload = jsonTree
            )
        }

        fun fromJson(json: String): SignalingMessage? {
            return try {
                gson.fromJson(json, SignalingMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }

    inline fun <reified T> getPayload(): T? {
        if (payload == null) return null
        return try {
            gson.fromJson(payload, T::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

data class PairRequestPayload(
    @SerializedName("deviceName")
    val deviceName: String,
    @SerializedName("pin")
    val pin: String,
    @SerializedName("androidVersion")
    val androidVersion: Int = android.os.Build.VERSION.SDK_INT,
    @SerializedName("screenWidth")
    val screenWidth: Int,
    @SerializedName("screenHeight")
    val screenHeight: Int,
    @SerializedName("preferredFps")
    val preferredFps: Int = 60,
    @SerializedName("preferredCodec")
    val preferredCodec: String = "H264"
)

data class PairResponsePayload(
    @SerializedName("status")
    val status: String, // "approved", "rejected", "invalid_pin"
    @SerializedName("receiverName")
    val receiverName: String,
    @SerializedName("supportedCodecs")
    val supportedCodecs: List<String> = listOf("H264", "VP8"),
    @SerializedName("maxBitrateKbps")
    val maxBitrateKbps: Int = 8000
)

data class SdpPayload(
    @SerializedName("sdp")
    val sdp: String,
    @SerializedName("type")
    val type: String // "offer" or "answer"
)

data class IceCandidatePayload(
    @SerializedName("sdpMid")
    val sdpMid: String?,
    @SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int?,
    @SerializedName("candidate")
    val candidate: String
)

data class ControlPayload(
    @SerializedName("action")
    val action: String, // "pause", "resume", "set_quality", "rotate"
    @SerializedName("rotation")
    val rotation: Int? = null,
    @SerializedName("targetFps")
    val targetFps: Int? = null,
    @SerializedName("targetBitrate")
    val targetBitrate: Int? = null
)

data class StatsPayload(
    @SerializedName("fps")
    val fps: Double,
    @SerializedName("bitrateKbps")
    val bitrateKbps: Double,
    @SerializedName("packetsLost")
    val packetsLost: Long,
    @SerializedName("rttMs")
    val rttMs: Double,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int
)

data class DisconnectPayload(
    @SerializedName("reason")
    val reason: String = "user_stopped"
)
