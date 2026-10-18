package com.mirrorcast.app.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.mirrorcast.app.signaling.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

sealed class StreamState {
    object Idle : StreamState()
    object Capturing : StreamState()
    object Streaming : StreamState()
    data class Error(val message: String) : StreamState()
}

class WebRtcStreamManager(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val TAG = "WebRtcStreamManager"

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    private var signalingJob: Job? = null
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        eglBase = EglBase.create()
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase?.eglBaseContext,
            true, // Enable Intel VP8
            true  // Enable H.264 High Profile
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

        val audioDeviceModule = try {
            org.webrtc.audio.JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
        } catch (e: Exception) {
            Log.w(TAG, "JavaAudioDeviceModule initialization note: ${e.message}")
            null
        }

        val pcfBuilder = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)

        if (audioDeviceModule != null) {
            pcfBuilder.setAudioDeviceModule(audioDeviceModule)
        }

        peerConnectionFactory = pcfBuilder.createPeerConnectionFactory()
    }

    fun startStreaming(
        mediaProjectionResultData: Intent,
        width: Int,
        height: Int,
        fps: Int = 60,
        enableAudio: Boolean = true,
        projectionCallback: MediaProjection.Callback? = null
    ) {
        try {
            _streamState.value = StreamState.Capturing

            // Setup ScreenCapturer using Android MediaProjection result intent
            val callback = projectionCallback ?: object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection revoked by system")
                    stopStreaming()
                }
            }

            videoCapturer = ScreenCapturerAndroid(mediaProjectionResultData, callback)

            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase?.eglBaseContext)
            videoSource = peerConnectionFactory?.createVideoSource(true)

            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            videoCapturer?.startCapture(width, height, fps)

            videoTrack = peerConnectionFactory?.createVideoTrack("MIRROR_VIDEO_TRACK", videoSource)
            videoTrack?.setEnabled(true)

            // Setup AudioTrack for screen and system audio streaming
            if (enableAudio) {
                try {
                    val audioConstraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
                        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "false"))
                        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
                        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
                    }
                    audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                    audioTrack = peerConnectionFactory?.createAudioTrack("MIRROR_AUDIO_TRACK", audioSource)
                    audioTrack?.setEnabled(true)
                    Log.d(TAG, "WebRTC AudioTrack created and enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Audio initialization skipped or not permitted: ${e.message}")
                }
            }

            createPeerConnection()

            // Observe incoming signaling messages
            signalingJob?.cancel()
            signalingJob = scope.launch {
                signalingClient.incomingMessages.collect { msg ->
                    when (msg.type) {
                        "answer" -> {
                            val payload = msg.getPayload<com.mirrorcast.app.signaling.SdpPayload>()
                            if (payload != null) {
                                handleRemoteAnswer(payload.sdp)
                            }
                        }
                        "ice_candidate" -> {
                            val payload = msg.getPayload<com.mirrorcast.app.signaling.IceCandidatePayload>()
                            if (payload != null) {
                                handleRemoteIceCandidate(payload)
                            }
                        }
                    }
                }
            }

            // Create and send SDP Offer
            createOffer()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming: ${e.message}")
            _streamState.value = StreamState.Error(e.message ?: "Screen capture initialization failed")
        }
    }

    private val pendingCandidates = mutableListOf<com.mirrorcast.app.signaling.IceCandidatePayload>()

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE Connection state: $state")
                    if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                        _streamState.value = StreamState.Streaming
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        signalingClient.sendIceCandidate(
                            candidate = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    }
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            }
        )

        videoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("MIRROR_STREAM"))
        }

        audioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("MIRROR_STREAM"))
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                Log.d(TAG, "Local SDP Offer created")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local SDP Offer set, dispatching to Windows receiver")
                        signalingClient.sendOffer(desc.description)
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set local SDP: $error")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun handleRemoteAnswer(sdp: String) {
        val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote SDP Answer set successfully, media pipeline active")
                _streamState.value = StreamState.Streaming
                synchronized(pendingCandidates) {
                    for (cand in pendingCandidates) {
                        peerConnection?.addIceCandidate(
                            IceCandidate(cand.sdpMid ?: "0", cand.sdpMLineIndex ?: 0, cand.candidate)
                        )
                    }
                    pendingCandidates.clear()
                }
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote SDP Answer: $error")
            }
        }, sessionDesc)
    }

    private fun handleRemoteIceCandidate(candidate: com.mirrorcast.app.signaling.IceCandidatePayload) {
        if (peerConnection?.remoteDescription == null) {
            synchronized(pendingCandidates) {
                pendingCandidates.add(candidate)
            }
            return
        }
        val iceCandidate = IceCandidate(
            candidate.sdpMid ?: "0",
            candidate.sdpMLineIndex ?: 0,
            candidate.candidate
        )
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun updateScreenDimensions(width: Int, height: Int, fps: Int) {
        videoCapturer?.changeCaptureFormat(width, height, fps)
        Log.d(TAG, "Screen capture format updated: ${width}x${height} @ ${fps}fps")
    }

    fun stopStreaming() {
        try {
            signalingJob?.cancel()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            videoTrack?.dispose()
            videoTrack = null

            videoSource?.dispose()
            videoSource = null

            audioTrack?.dispose()
            audioTrack = null

            audioSource?.dispose()
            audioSource = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            _streamState.value = StreamState.Idle
            Log.d(TAG, "WebRTC Streaming stopped and resources disposed cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping streaming: ${e.message}")
        }
    }
}
