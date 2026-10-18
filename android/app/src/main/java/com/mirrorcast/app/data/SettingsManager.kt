package com.mirrorcast.app.data

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "mirrorcast_preferences"

    private const val KEY_RESOLUTION = "pref_resolution"
    private const val KEY_FRAME_RATE = "pref_frame_rate"
    private const val KEY_BITRATE = "pref_bitrate"
    private const val KEY_CODEC = "pref_codec"
    private const val KEY_AUDIO_CAPTURE = "pref_audio_capture"
    private const val KEY_LOW_LATENCY = "pref_low_latency"
    private const val KEY_AUTO_DISCOVER = "pref_auto_discover"
    private const val KEY_USE_QR = "pref_use_qr"
    private const val KEY_REMEMBER_DEVICE = "pref_remember_device"
    private const val KEY_LAST_HOST = "pref_last_host"
    private const val KEY_LAST_PIN = "pref_last_pin"
    private const val KEY_THEME = "pref_theme"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, "system") ?: "system"
    }

    fun setTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    fun getResolution(context: Context): String {
        return getPrefs(context).getString(KEY_RESOLUTION, "1080p (Full HD)") ?: "1080p (Full HD)"
    }

    fun setResolution(context: Context, resolution: String) {
        getPrefs(context).edit().putString(KEY_RESOLUTION, resolution).apply()
    }

    fun getFrameRate(context: Context): String {
        return getPrefs(context).getString(KEY_FRAME_RATE, "60 fps") ?: "60 fps"
    }

    fun setFrameRate(context: Context, frameRate: String) {
        getPrefs(context).edit().putString(KEY_FRAME_RATE, frameRate).apply()
    }

    fun getFpsInt(context: Context): Int {
        val str = getFrameRate(context)
        return when {
            str.contains("30") -> 30
            str.contains("90") -> 90
            else -> 60
        }
    }

    fun getBitrate(context: Context): String {
        return getPrefs(context).getString(KEY_BITRATE, "8 Mbps (High)") ?: "8 Mbps (High)"
    }

    fun setBitrate(context: Context, bitrate: String) {
        getPrefs(context).edit().putString(KEY_BITRATE, bitrate).apply()
    }

    fun getCodec(context: Context): String {
        return getPrefs(context).getString(KEY_CODEC, "H.264 (Hardware)") ?: "H.264 (Hardware)"
    }

    fun setCodec(context: Context, codec: String) {
        getPrefs(context).edit().putString(KEY_CODEC, codec).apply()
    }

    fun isAudioCapture(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUDIO_CAPTURE, true)
    }

    fun setAudioCapture(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUDIO_CAPTURE, enabled).apply()
    }

    fun isLowLatency(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOW_LATENCY, true)
    }

    fun setLowLatency(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOW_LATENCY, enabled).apply()
    }

    fun isAutoDiscover(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DISCOVER, true)
    }

    fun setAutoDiscover(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DISCOVER, enabled).apply()
    }

    fun isUseQr(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_QR, true)
    }

    fun setUseQr(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_QR, enabled).apply()
    }

    fun isRememberDevice(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMEMBER_DEVICE, false)
    }

    fun setRememberDevice(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMEMBER_DEVICE, enabled).apply()
    }

    fun getLastHost(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_HOST, "") ?: ""
    }

    fun setLastHost(context: Context, host: String) {
        getPrefs(context).edit().putString(KEY_LAST_HOST, host).apply()
    }

    fun getLastPin(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_PIN, "") ?: ""
    }

    fun setLastPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_LAST_PIN, pin).apply()
    }

    fun getScaledDimensions(context: Context, nativeWidth: Int, nativeHeight: Int): Pair<Int, Int> {
        val res = getResolution(context)
        val targetShortEdge = when {
            res.contains("720") -> 720
            res.contains("1440") -> 1440
            else -> 1080
        }

        val isPortrait = nativeHeight >= nativeWidth
        val shortEdge = if (isPortrait) nativeWidth else nativeHeight
        val longEdge = if (isPortrait) nativeHeight else nativeWidth

        if (shortEdge <= targetShortEdge) {
            // Native is already smaller or equal to target, maintain native size (ensure even numbers for encoder)
            val w = if (nativeWidth % 2 != 0) nativeWidth - 1 else nativeWidth
            val h = if (nativeHeight % 2 != 0) nativeHeight - 1 else nativeHeight
            return Pair(w, h)
        }

        val scale = targetShortEdge.toFloat() / shortEdge.toFloat()
        var newW = (nativeWidth * scale).toInt()
        var newH = (nativeHeight * scale).toInt()

        // Encoders require even dimensions
        if (newW % 2 != 0) newW -= 1
        if (newH % 2 != 0) newH -= 1

        return Pair(newW, newH)
    }
}
