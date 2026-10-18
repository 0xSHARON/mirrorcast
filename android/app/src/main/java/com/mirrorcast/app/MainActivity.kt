package com.mirrorcast.app

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mirrorcast.app.data.SettingsManager
import com.mirrorcast.app.discovery.DiscoveredReceiver
import com.mirrorcast.app.discovery.ReceiverDiscovery
import com.mirrorcast.app.service.ScreenCaptureService
import com.mirrorcast.app.signaling.SignalingClient
import com.mirrorcast.app.signaling.SignalingState
import com.mirrorcast.app.ui.MirrorCastScreen
import com.mirrorcast.app.ui.theme.MirrorCastTheme
import com.mirrorcast.app.webrtc.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private lateinit var discovery: ReceiverDiscovery
    private lateinit var signalingClient: SignalingClient
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var captureService: ScreenCaptureService? = null
    private var isBound = false
    private var pendingReceiver: DiscoveredReceiver? = null
    private var pendingPin: String? = null

    private val currentStreamState = MutableStateFlow<StreamState>(StreamState.Idle)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenCaptureService.LocalBinder
            captureService = binder.getService()
            isBound = true

            lifecycleScope.launch {
                captureService?.getStreamState()?.collect {
                    currentStreamState.value = it
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            captureService = null
            isBound = false
            currentStreamState.value = StreamState.Idle
        }
    }

    // MediaProjection Consent Result Launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val (nativeW, nativeH) = getScreenDimensions()
            val (scaledW, scaledH) = SettingsManager.getScaledDimensions(this, nativeW, nativeH)
            val fps = SettingsManager.getFpsInt(this)
            val audioEnabled = SettingsManager.isAudioCapture(this)
            Log.d(TAG, "MediaProjection permission granted. Starting stream ${scaledW}x${scaledH} @ ${fps}fps (audio=$audioEnabled)")

            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

            lifecycleScope.launch {
                // Wait briefly for service connection
                while (captureService == null) {
                    kotlinx.coroutines.delay(50)
                }
                captureService?.startStreaming(
                    signalingClient = signalingClient,
                    projectionData = result.data!!,
                    width = scaledW,
                    height = scaledH,
                    fps = fps,
                    enableAudio = audioEnabled
                )
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            signalingClient.disconnect()
        }
    }

    // QR Code Scanner Launcher
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val receiver = ReceiverDiscovery.parseQrUri(result.contents)
            if (receiver != null && receiver.pin != null) {
                initiatePairing(receiver, receiver.pin)
            } else {
                Toast.makeText(this, "Invalid MirrorCast QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Notification and Camera Permission Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: true
        Log.d(TAG, "Permissions updated: notif=$notifGranted, camera=$cameraGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discovery = ReceiverDiscovery(this)
        signalingClient = SignalingClient(lifecycleScope)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        requestRequiredPermissions()
        observeSignaling()

        setContent {
            var themeMode by remember { mutableStateOf(SettingsManager.getTheme(this@MainActivity)) }
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemInDark
            }

            MirrorCastTheme(darkTheme = isDark) {
                val signalingState by signalingClient.connectionState.collectAsState()
                val streamState by currentStreamState.collectAsState()
                val discoveredReceivers by discovery.receivers.collectAsState()

                MirrorCastScreen(
                    signalingState = signalingState,
                    streamState = streamState,
                    discoveredReceivers = discoveredReceivers,
                    themeMode = themeMode,
                    onThemeChange = {
                        themeMode = it
                        SettingsManager.setTheme(this@MainActivity, it)
                    },
                    onScanQrClick = {
                        val options = ScanOptions().apply {
                            setPrompt("Point camera at the QR code on your Windows PC")
                            setBeepEnabled(true)
                            setOrientationLocked(false)
                        }
                        qrScanLauncher.launch(options)
                    },
                    onConnectReceiver = { receiver, pin ->
                        initiatePairing(receiver, pin)
                    },
                    onStopMirroring = {
                        stopMirroring()
                    },
                    onRefreshDiscovery = {
                        discovery.stopDiscovery()
                        discovery.startDiscovery()
                    },
                    onRequestPermission = {
                        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                        mediaProjectionLauncher.launch(captureIntent)
                    }
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun observeSignaling() {
        lifecycleScope.launch {
            signalingClient.connectionState.collect { state ->
                when (state) {
                    is SignalingState.Paired -> {
                        Toast.makeText(this@MainActivity, "Paired with ${state.receiverName}", Toast.LENGTH_SHORT).show()
                    }
                    is SignalingState.Error -> {
                        Toast.makeText(this@MainActivity, "Connection Error: ${state.message}", Toast.LENGTH_LONG).show()
                        stopMirroring()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun initiatePairing(receiver: DiscoveredReceiver, pin: String) {
        pendingReceiver = receiver
        pendingPin = pin
        val (width, height) = getScreenDimensions()
        signalingClient.connect(receiver.host, receiver.port, pin, width, height)
    }

    private fun stopMirroring() {
        captureService?.stopStreaming()
        signalingClient.disconnect()
        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) { }
            isBound = false
        }
        captureService = null
        currentStreamState.value = StreamState.Idle
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    override fun onResume() {
        super.onResume()
        discovery.startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        discovery.stopDiscovery()
    }

    override fun onDestroy() {
        stopMirroring()
        super.onDestroy()
    }
}
