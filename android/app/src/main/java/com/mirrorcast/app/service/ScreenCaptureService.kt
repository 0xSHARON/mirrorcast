package com.mirrorcast.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mirrorcast.app.MainActivity
import com.mirrorcast.app.R
import com.mirrorcast.app.signaling.SignalingClient
import com.mirrorcast.app.webrtc.StreamState
import com.mirrorcast.app.webrtc.WebRtcStreamManager
import kotlinx.coroutines.flow.StateFlow

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"
    private val NOTIFICATION_CHANNEL_ID = "mirrorcast_service_channel"
    private val NOTIFICATION_ID = 1001

    private val binder = LocalBinder()
    private var streamManager: WebRtcStreamManager? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MirrorCast is Active")
            .setContentText("Your screen is being streamed to Windows PC")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mirroring", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAudioPerm) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MirrorCast Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active screen mirroring foreground notification"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun startStreaming(
        signalingClient: SignalingClient,
        projectionData: Intent,
        width: Int,
        height: Int,
        fps: Int = 60,
        enableAudio: Boolean = true
    ) {
        startAsForeground()
        streamManager = WebRtcStreamManager(this, signalingClient)
        streamManager?.startStreaming(projectionData, width, height, fps, enableAudio)
    }

    fun updateOrientation(width: Int, height: Int, fps: Int) {
        streamManager?.updateScreenDimensions(width, height, fps)
    }

    fun stopStreaming() {
        streamManager?.stopStreaming()
        streamManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun getStreamState(): StateFlow<StreamState>? = streamManager?.streamState

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.mirrorcast.app.ACTION_STOP"
    }
}
