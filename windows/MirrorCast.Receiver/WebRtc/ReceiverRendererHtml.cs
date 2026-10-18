namespace MirrorCast.Receiver.WebRtc;

public static class ReceiverRendererHtml
{
    public static string GetHtmlContent(int port)
    {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MirrorCast Video Renderer</title>
    <style>
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            user-select: none;
        }
        body, html {
            width: 100%;
            height: 100%;
            overflow: hidden;
            background-color: #0d0f12;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #videoContainer {
            position: relative;
            width: 100%;
            height: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
            background: radial-gradient(circle at center, #161a22 0%, #0d0f12 100%);
        }
        video {
            width: 100%;
            height: 100%;
            object-fit: contain;
            display: block;
            transform: translateZ(0);
            will-change: transform;
        }
        .placeholder {
            position: absolute;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 16px;
            color: #8b949e;
            pointer-events: none;
            transition: opacity 0.3s ease;
        }
        .placeholder.hidden {
            opacity: 0;
            pointer-events: none;
        }
        .icon {
            width: 64px;
            height: 64px;
            stroke: #58a6ff;
            fill: none;
            stroke-width: 1.5;
        }
        .spinner {
            width: 32px;
            height: 32px;
            border: 3px solid rgba(88, 166, 255, 0.2);
            border-radius: 50%;
            border-top-color: #58a6ff;
            animation: spin 1s linear infinite;
        }
        @keyframes spin {
            to { transform: rotate(360deg); }
        }
        .stats-badge {
            position: absolute;
            top: 16px;
            right: 16px;
            background: rgba(13, 17, 23, 0.75);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            color: #58a6ff;
            font-family: monospace;
            font-size: 12px;
            padding: 6px 12px;
            border-radius: 8px;
            display: none;
            pointer-events: none;
        }
    </style>
</head>
<body>
    <div id="videoContainer">
        <video id="remoteVideo" autoplay playsinline></video>
        <audio id="remoteAudio" autoplay playsinline></audio>
        <div id="placeholder" class="placeholder">
            <svg class="icon" viewBox="0 0 24 24">
                <rect x="2" y="3" width="20" height="14" rx="2"></rect>
                <line x1="8" y1="21" x2="16" y2="21"></line>
                <line x1="12" y1="17" x2="12" y2="21"></line>
            </svg>
            <div id="statusText">Ready to mirror</div>
            <div id="loader" class="spinner" style="display:none;"></div>
        </div>
        <div id="statsBadge" class="stats-badge">60 FPS | 5.2 Mbps</div>
    </div>

    <script>
        let pc = null;
        let lastBytesReceived = 0;
        let lastStatsTimestamp = 0;
        let statsInterval = null;

        const video = document.getElementById('remoteVideo');
        const placeholder = document.getElementById('placeholder');
        const statusText = document.getElementById('statusText');
        const loader = document.getElementById('loader');
        const statsBadge = document.getElementById('statsBadge');

        function sendToHost(data) {
            if (window.chrome && window.chrome.webview) {
                window.chrome.webview.postMessage(data);
            }
        }

        let pendingCandidates = [];
        let disconnectTimer = null;

        function createPeerConnection() {
            if (pc) {
                try { pc.close(); } catch (e) { }
                pc = null;
            }
            pendingCandidates = [];
            if (disconnectTimer) {
                clearTimeout(disconnectTimer);
                disconnectTimer = null;
            }

            const config = {
                iceServers: [
                    { urls: 'stun:stun.l.google.com:19302' },
                    { urls: 'stun:stun1.l.google.com:19302' },
                    { urls: 'stun:stun2.l.google.com:19302' }
                ],
                sdpSemantics: 'unified-plan',
                bundlePolicy: 'max-bundle'
            };

            pc = new RTCPeerConnection(config);

            try {
                pc.addTransceiver('video', { direction: 'recvonly' });
                pc.addTransceiver('audio', { direction: 'recvonly' });
            } catch (e) {
                console.warn('[WebRTC] Transceiver setup note:', e);
            }

            const remoteStream = new MediaStream();
            video.srcObject = remoteStream;

            pc.ontrack = (event) => {
                console.log('[WebRTC] Remote track received:', event.track.kind, event.track.id);
                if (event.track) {
                    remoteStream.addTrack(event.track);
                    event.track.enabled = true;
                }
                if (event.streams && event.streams[0]) {
                    event.streams[0].getTracks().forEach(t => {
                        if (!remoteStream.getTracks().some(x => x.id === t.id)) {
                            remoteStream.addTrack(t);
                            t.enabled = true;
                        }
                    });
                }

                if (event.track.kind === 'audio') {
                    const audioElem = document.getElementById('remoteAudio');
                    if (audioElem) {
                        audioElem.srcObject = new MediaStream([event.track]);
                        audioElem.muted = false;
                        audioElem.volume = 1.0;
                        audioElem.play().catch(e => console.warn('Audio play note:', e));
                    }
                }

                video.muted = false;
                video.volume = 1.0;
                video.play().catch(e => console.warn('Autoplay note:', e));
                placeholder.classList.add('hidden');
                statsBadge.style.display = 'block';
                sendToHost({ type: 'stream_started' });
                startStatsMonitoring();
            };

            pc.onicecandidate = (event) => {
                if (event.candidate) {
                    sendToHost({
                        type: 'ice_candidate',
                        candidate: event.candidate.candidate,
                        sdpMid: event.candidate.sdpMid,
                        sdpMLineIndex: event.candidate.sdpMLineIndex
                    });
                }
            };

            pc.onconnectionstatechange = () => {
                console.log('[WebRTC] Connection state:', pc.connectionState);
                sendToHost({ type: 'connection_state', state: pc.connectionState });
                if (pc.connectionState === 'connected') {
                    if (disconnectTimer) {
                        clearTimeout(disconnectTimer);
                        disconnectTimer = null;
                    }
                    placeholder.classList.add('hidden');
                } else if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
                    if (!disconnectTimer) {
                        disconnectTimer = setTimeout(() => {
                            if (pc && (pc.connectionState === 'disconnected' || pc.connectionState === 'failed')) {
                                resetRenderer('Connection interrupted');
                            }
                        }, 4000);
                    }
                }
            };

            return pc;
        }

        async function handleSetOffer(sdp) {
            try {
                loader.style.display = 'block';
                statusText.innerText = 'Connecting video stream...';
                createPeerConnection();

                await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: sdp }));
                console.log('[WebRTC] Remote offer set successfully');

                // Flush queued candidates
                while (pendingCandidates.length > 0) {
                    const cand = pendingCandidates.shift();
                    try {
                        await pc.addIceCandidate(new RTCIceCandidate({
                            candidate: cand.candidate,
                            sdpMid: cand.sdpMid,
                            sdpMLineIndex: cand.sdpMLineIndex
                        }));
                    } catch (e) {
                        console.warn('[WebRTC] Error adding flushed candidate:', e);
                    }
                }

                const answer = await pc.createAnswer();
                await pc.setLocalDescription(answer);
                console.log('[WebRTC] Local answer created and set');

                sendToHost({ type: 'answer', sdp: answer.sdp });
            } catch (err) {
                console.error('[WebRTC] Error handling offer:', err);
                sendToHost({ type: 'error', message: err.toString() });
                resetRenderer('Negotiation failed');
            }
        }

        async function handleAddIceCandidate(cand) {
            if (!pc || !pc.remoteDescription) {
                pendingCandidates.push(cand);
                return;
            }

            try {
                if (cand.candidate) {
                    await pc.addIceCandidate(new RTCIceCandidate({
                        candidate: cand.candidate,
                        sdpMid: cand.sdpMid,
                        sdpMLineIndex: cand.sdpMLineIndex
                    }));
                }
            } catch (err) {
                console.warn('[WebRTC] Error adding ICE candidate:', err);
            }
        }

        function startStatsMonitoring() {
            if (statsInterval) clearInterval(statsInterval);

            statsInterval = setInterval(async () => {
                if (!pc) return;
                try {
                    const stats = await pc.getStats();
                    stats.forEach(report => {
                        if (report.type === 'inbound-rtp' && report.kind === 'video') {
                            const now = report.timestamp;
                            const bytes = report.bytesReceived;
                            let bitrate = 0;
                            if (lastStatsTimestamp > 0 && now > lastStatsTimestamp) {
                                bitrate = (bytes - lastBytesReceived) * 8 / ((now - lastStatsTimestamp) / 1000) / 1000;
                            }
                            lastBytesReceived = bytes;
                            lastStatsTimestamp = now;

                            const fps = report.framesPerSecond || 0;
                            const packetsLost = report.packetsLost || 0;
                            const width = report.frameWidth || video.videoWidth || 0;
                            const height = report.frameHeight || video.videoHeight || 0;

                            statsBadge.innerText = `${Math.round(fps)} FPS | ${bitrate.toFixed(1)} Mbps`;

                            sendToHost({
                                type: 'stats',
                                fps: fps,
                                bitrateKbps: bitrate,
                                packetsLost: packetsLost,
                                width: width,
                                height: height
                            });
                        }
                    });
                } catch (e) { }
            }, 1000);
        }

        function resetRenderer(msg = 'Ready to mirror') {
            if (statsInterval) clearInterval(statsInterval);
            statsInterval = null;
            if (pc) {
                pc.close();
                pc = null;
            }
            video.srcObject = null;
            const audioElem = document.getElementById('remoteAudio');
            if (audioElem) {
                audioElem.srcObject = null;
            }
            placeholder.classList.remove('hidden');
            loader.style.display = 'none';
            statusText.innerText = msg;
            statsBadge.style.display = 'none';
            lastBytesReceived = 0;
            lastStatsTimestamp = 0;
        }

        // Mouse and Touch Remote Control
        let isMouseDown = false;
        let startX = 0, startY = 0;
        let startTime = 0;

        function getPhoneCoords(clientX, clientY) {
            const rect = video.getBoundingClientRect();
            const videoWidth = video.videoWidth || 1080;
            const videoHeight = video.videoHeight || 2400;
            const videoAspect = videoWidth / videoHeight;
            const containerAspect = rect.width / rect.height;

            let displayWidth, displayHeight, offsetX, offsetY;
            if (containerAspect > videoAspect) {
                displayHeight = rect.height;
                displayWidth = displayHeight * videoAspect;
                offsetX = (rect.width - displayWidth) / 2;
                offsetY = 0;
            } else {
                displayWidth = rect.width;
                displayHeight = displayWidth / videoAspect;
                offsetX = 0;
                offsetY = (rect.height - displayHeight) / 2;
            }

            const clickX = clientX - rect.left - offsetX;
            const clickY = clientY - rect.top - offsetY;

            if (clickX >= 0 && clickX <= displayWidth && clickY >= 0 && clickY <= displayHeight) {
                const normX = clickX / displayWidth;
                const normY = clickY / displayHeight;
                return {
                    x: Math.round(normX * videoWidth),
                    y: Math.round(normY * videoHeight)
                };
            }
            return null;
        }

        video.addEventListener('mousedown', (e) => {
            isMouseDown = true;
            startX = e.clientX;
            startY = e.clientY;
            startTime = Date.now();
        });

        video.addEventListener('mouseup', (e) => {
            if (!isMouseDown) return;
            isMouseDown = false;

            const dx = e.clientX - startX;
            const dy = e.clientY - startY;
            const dist = Math.hypot(dx, dy);
            const duration = Math.min(Math.max(Date.now() - startTime, 100), 500);

            const startCoords = getPhoneCoords(startX, startY);
            const endCoords = getPhoneCoords(e.clientX, e.clientY);

            if (dist < 10) {
                if (startCoords) {
                    sendToHost({ type: 'device_input', action: 'tap', x: startCoords.x, y: startCoords.y });
                }
            } else {
                if (startCoords && endCoords) {
                    sendToHost({
                        type: 'device_input',
                        action: 'swipe',
                        x1: startCoords.x,
                        y1: startCoords.y,
                        x2: endCoords.x,
                        y2: endCoords.y,
                        duration: duration
                    });
                }
            }
        });

        // Listen for messages from C# Host
        if (window.chrome && window.chrome.webview) {
            window.chrome.webview.addEventListener('message', (event) => {
                const msg = event.data;
                if (!msg) return;

                switch (msg.action) {
                    case 'set_offer':
                        handleSetOffer(msg.sdp);
                        break;
                    case 'add_candidate':
                        handleAddIceCandidate(msg);
                        break;
                    case 'reset':
                        resetRenderer(msg.message || 'Ready to mirror');
                        break;
                    case 'set_fit':
                        video.style.objectFit = msg.fit || 'contain';
                        break;
                }
            });
            sendToHost({ type: 'renderer_ready' });
        }
    </script>
</body>
</html>
""";
    }
}
