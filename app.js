/**
 * MirrorCast Client Interactivity (Vanilla ES6)
 * Strict minimalism: zero external dependencies, fast execution.
 */

document.addEventListener('DOMContentLoaded', () => {

  /* -------------------------------------------------------------------------
   * 1. Interactive Pipeline Simulator (index.html)
   * ------------------------------------------------------------------------- */
  const flowSteps = document.querySelectorAll('.flow-step');
  const simContents = document.querySelectorAll('.sim-content');

  function setSimStep(stepIndex) {
    flowSteps.forEach(step => {
      const match = parseInt(step.getAttribute('data-step'), 10) === stepIndex;
      step.classList.toggle('active', match);
    });

    simContents.forEach(content => {
      const isTarget = content.id === `sim-step-${stepIndex}`;
      content.style.display = isTarget ? 'block' : 'none';
      content.classList.toggle('active', isTarget);
    });
  }

  flowSteps.forEach(step => {
    step.addEventListener('click', () => {
      const targetStep = parseInt(step.getAttribute('data-step'), 10);
      setSimStep(targetStep);
    });
  });

  // Modal simulation interaction inside Step 3
  const simBtnAllow = document.getElementById('sim-btn-allow');
  const simBtnReject = document.getElementById('sim-btn-reject');
  const simFeedback = document.getElementById('sim-modal-feedback');

  if (simBtnAllow) {
    simBtnAllow.addEventListener('click', () => {
      if (simFeedback) {
        simFeedback.innerHTML = '<span style="color: var(--accent-green);">Session Authorized! Handshake complete. Loading video stream...</span>';
      }
      setTimeout(() => {
        setSimStep(4);
      }, 700);
    });
  }

  if (simBtnReject) {
    simBtnReject.addEventListener('click', () => {
      if (simFeedback) {
        simFeedback.innerHTML = '<span style="color: var(--accent-red);">Session Rejected by Host. Connection terminated.</span>';
      }
    });
  }

  /* -------------------------------------------------------------------------
   * 2. Copy to Clipboard Utility
   * ------------------------------------------------------------------------- */
  function setupCopyButton(btnId, getTextFn) {
    const btn = document.getElementById(btnId);
    if (!btn) return;

    btn.addEventListener('click', async () => {
      const textToCopy = getTextFn();
      try {
        await navigator.clipboard.writeText(textToCopy);
        const originalText = btn.textContent;
        btn.textContent = 'Copied!';
        btn.style.color = 'var(--accent-green)';
        setTimeout(() => {
          btn.textContent = originalText;
          btn.style.color = '';
        }, 1800);
      } catch (err) {
        console.error('Failed to copy text: ', err);
      }
    });
  }

  setupCopyButton('btn-copy-terminal', () => {
    return `.\\mirrorcast-receiver.exe --port 8080\nadb install -r mirrorcast.apk`;
  });

  setupCopyButton('btn-copy-install', () => {
    return `adb install -r mirrorcast.apk\nadb reverse tcp:8080 tcp:8080\nadb shell monkey -p com.mirrorcast 1`;
  });

  setupCopyButton('btn-copy-badge', () => {
    return `[![MirrorCast](https://img.shields.io/badge/MirrorCast-v1.0.0--P2P-00e5ff?logo=android&logoColor=white)](https://github.com/0xSHARON/mirrorcast)`;
  });

  /* -------------------------------------------------------------------------
   * 3. Interactive Protocol Wire Inspector (protocol.html)
   * ------------------------------------------------------------------------- */
  const payloadTabs = document.querySelectorAll('.payload-tab');
  const payloadCode = document.getElementById('payload-code');
  const payloadFilename = document.getElementById('payload-filename');

  const payloads = {
    pair_request: {
      type: "pair_request",
      version: "1.0.0",
      timestamp: 1729237200451,
      sender: {
        device_model: "Galaxy Tab A (SM-T295)",
        manufacturer: "samsung",
        android_api: 29,
        screen_resolution: "1280x800",
        refresh_rate: 60,
        audio_supported: true
      },
      pin: null
    },
    pair_response: {
      type: "pair_response",
      version: "1.0.0",
      status: "allowed",
      session_id: "mc_sess_9f8a12e34c",
      receiver: {
        hostname: "WORKSTATION-X",
        os: "Windows 11 x64 (Build 22631)",
        hw_acceleration: "D3D11_DXVA2",
        preferred_codec: "video/H264"
      }
    },
    webrtc_offer: {
      type: "offer",
      session_id: "mc_sess_9f8a12e34c",
      sdp: "v=0\r\no=- 42018892 2 IN IP4 192.168.1.20\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0 1\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\nc=IN IP4 192.168.1.20\r\na=rtpmap:96 H264/90000\r\na=fmtp:96 profile-level-id=42e01f;packetization-mode=1\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\n"
    },
    webrtc_answer: {
      type: "answer",
      session_id: "mc_sess_9f8a12e34c",
      sdp: "v=0\r\no=- 98471201 2 IN IP4 192.168.1.42\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0 1\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\nc=IN IP4 192.168.1.42\r\na=rtpmap:96 H264/90000\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\n"
    },
    ice_candidate: {
      type: "ice_candidate",
      session_id: "mc_sess_9f8a12e34c",
      candidate: {
        candidate: "candidate:189201 1 udp 2122260223 192.168.1.20 54123 typ host generation 0",
        sdpMid: "0",
        sdpMLineIndex: 0
      }
    },
    stream_control: {
      type: "stream_control",
      session_id: "mc_sess_9f8a12e34c",
      action: "throttle_bitrate",
      parameters: {
        max_bitrate_kbps: 6000,
        orientation: "landscape_sensor",
        mute_sender_speakers: true
      }
    }
  };

  function updatePayload(key) {
    if (!payloadCode || !payloads[key]) return;
    const formatted = JSON.stringify(payloads[key], null, 2);
    payloadCode.textContent = formatted;
    if (payloadFilename) {
      payloadFilename.textContent = `${key}.json`;
    }
  }

  if (payloadTabs.length > 0) {
    payloadTabs.forEach(tab => {
      tab.addEventListener('click', () => {
        payloadTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const key = tab.getAttribute('data-payload');
        updatePayload(key);
      });
    });

    // Initialize default payload
    updatePayload('pair_request');

    setupCopyButton('btn-copy-payload', () => {
      const activeTab = document.querySelector('.payload-tab.active');
      const key = activeTab ? activeTab.getAttribute('data-payload') : 'pair_request';
      return JSON.stringify(payloads[key], null, 2);
    });
  }

  /* -------------------------------------------------------------------------
   * 4. FAQ Accordion (index.html)
   * ------------------------------------------------------------------------- */
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach(item => {
    const questionBtn = item.querySelector('.faq-question');
    if (questionBtn) {
      questionBtn.addEventListener('click', () => {
        const isOpen = item.classList.contains('open');
        faqItems.forEach(i => i.classList.remove('open'));
        if (!isOpen) {
          item.classList.add('open');
        }
      });
    }
  });

  /* -------------------------------------------------------------------------
   * 5. Live GitHub Star Count Fetcher
   * ------------------------------------------------------------------------- */
  const starCountEl = document.getElementById('nav-star-count');
  if (starCountEl) {
    fetch('https://api.github.com/repos/0xSHARON/mirrorcast')
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (data && typeof data.stargazers_count === 'number') {
          starCountEl.textContent = `${data.stargazers_count} ★`;
        }
      })
      .catch(() => {
        // Fallback remains clean default
      });
  }

  /* -------------------------------------------------------------------------
   * 6. Viral Community Share Handlers
   * ------------------------------------------------------------------------- */
  const pageUrl = encodeURIComponent('https://github.com/0xSHARON/mirrorcast');
  const shareText = encodeURIComponent('MirrorCast: Sub-15ms screen and audio mirroring from Android to Windows. Direct P2P WebRTC, zero cloud, 100% open source!');

  const shareX = document.getElementById('share-x');
  if (shareX) {
    shareX.addEventListener('click', () => {
      window.open(`https://twitter.com/intent/tweet?text=${shareText}&url=${pageUrl}`, '_blank', 'width=600,height=400');
    });
  }

  const shareReddit = document.getElementById('share-reddit');
  if (shareReddit) {
    shareReddit.addEventListener('click', () => {
      window.open(`https://www.reddit.com/submit?url=${pageUrl}&title=${shareText}`, '_blank', 'width=700,height=550');
    });
  }

  const shareHN = document.getElementById('share-hn');
  if (shareHN) {
    shareHN.addEventListener('click', () => {
      window.open(`https://news.ycombinator.com/submitlink?u=${pageUrl}&t=${shareText}`, '_blank', 'width=700,height=550');
    });
  }

  const shareCopy = document.getElementById('share-copy');
  if (shareCopy) {
    shareCopy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText('https://github.com/0xSHARON/mirrorcast');
        const orig = shareCopy.textContent;
        shareCopy.textContent = 'Copied!';
        shareCopy.style.color = 'var(--accent-green)';
        setTimeout(() => {
          shareCopy.textContent = orig;
          shareCopy.style.color = '';
        }, 1800);
      } catch (err) {
        console.error(err);
      }
    });
  }

  /* -------------------------------------------------------------------------
   * 7. Interactive Demo Audio Mute Toggle
   * ------------------------------------------------------------------------- */
  const demoMuteBtn = document.getElementById('demo-mute-btn');
  if (demoMuteBtn) {
    let isMuted = false;
    demoMuteBtn.addEventListener('click', () => {
      isMuted = !isMuted;
      if (isMuted) {
        demoMuteBtn.textContent = '🔇 Audio MUTED';
        demoMuteBtn.style.color = 'var(--accent-amber)';
      } else {
        demoMuteBtn.textContent = '🔊 Audio ON';
        demoMuteBtn.style.color = 'var(--accent-green)';
      }
    });
  }

});

