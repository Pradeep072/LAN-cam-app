package com.example.lancamapp.utils

import com.example.lancamapp.database.CameraEntity
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

object DeviceUtils {

    // Added Tiandy and Prama
    val DEVICE_TYPES = listOf(
        "CP Plus / Dahua",
        "Hikvision / Prama",
        "Tiandy",
        "Tapo / TP-Link",
        "CP Plus Ezycam",
        "Generic ONVIF"
    )

    fun generateRtspUrl(camera: CameraEntity): String {
        return generateUrlForChannel(camera, 1)
    }

    fun generateUrlForChannel(camera: CameraEntity, targetChannel: Int): String {
        val safeUser = URLEncoder.encode(camera.username, "UTF-8")
        val safePass = URLEncoder.encode(camera.password, "UTF-8")

        val path = when {
            camera.type.contains("CP Plus / Dahua") -> "/cam/realmonitor?channel=$targetChannel&subtype=1"
            camera.type.contains("Hikvision") -> "/Streaming/Channels/${targetChannel}02" // 02 = Substream (Faster)
            camera.type.contains("Tiandy") -> "/" // Tiandy often uses default root for ch1 or /1/1
            camera.type.contains("Tapo") -> "/stream2" // Substream
            camera.type.contains("CP Plus Ezycam") -> "/cam/realmonitor?channel=1&subtype=1" // Defaulting to channel 1, substream
            else -> "/live/ch$targetChannel"
        }
        return "rtsp://$safeUser:$safePass@${camera.ip}:${camera.port}$path"
    }

    // --- NEW: THE PROBE ---
    // This runs on a background thread to guess the device type/channels
    fun probeChannelCount(type: String): Int {
        return when {
            type.contains("Tapo") -> 1
            type.contains("CP Plus Ezycam") -> 1
            type.contains("Generic") -> 1
            // For DVRs, we default to 16 placeholders.
            // Real probing requires checking RTSP connections which takes time,
            // so we assume "Max Standard" and let the user pick.
            else -> 16
        }
    }
}