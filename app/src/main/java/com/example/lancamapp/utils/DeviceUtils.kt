package com.example.lancamapp.utils

import com.example.lancamapp.database.CameraEntity

object DeviceUtils {

    val DEVICE_TYPES = listOf(
        "CP Plus / Dahua",
        "Hikvision / Prama",
        "Tiandy",
        "Tapo / TP-Link",
        "CP Plus Ezycam",
        "Uniview / UNV",
        "Axis",
        "Reolink",
        "V380 Wi-Fi",
        "Generic ONVIF / RTSP",
        "HLS Stream (.m3u8)",
        "DASH Stream (.mpd)"
    )

    fun generateRtspUrl(camera: CameraEntity): String {
        return generateUrlForChannel(camera, camera.channel.coerceAtLeast(1))
    }

    fun generateUrlForChannel(camera: CameraEntity, targetChannel: Int, forceMainStream: Boolean = false): String {
        val trimmedIp = camera.ip.trim()
        
        // If the user already entered a full RTSP/HTTP URL as IP
        if (trimmedIp.startsWith("rtsp://") || trimmedIp.startsWith("http://") || trimmedIp.startsWith("https://")) {
            return trimmedIp
        }

        if (camera.type.contains("HLS") || camera.type.contains("DASH")) {
            return trimmedIp
        }

        val formattedUser = encodeCredential(camera.username.trim())
        val formattedPass = encodeCredential(camera.password)
        val authPrefix = if (formattedUser.isNotEmpty()) "$formattedUser:$formattedPass@" else ""
        val portNumber = if (camera.port > 0) camera.port else 554

        // If a custom RTSP path is specified
        if (camera.customPath.isNotBlank()) {
            val custom = camera.customPath.trim()
            val finalPath = if (custom.startsWith("/")) custom else "/$custom"
            return "rtsp://$authPrefix$trimmedIp:$portNumber$finalPath"
        }

        val isMain = forceMainStream || camera.streamType == "main"
        val subCode = if (isMain) 0 else 1
        val hikSubCode = if (isMain) "01" else "02"

        val path = when {
            camera.type.contains("CP Plus") || camera.type.contains("Dahua") ->
                "/cam/realmonitor?channel=$targetChannel&subtype=$subCode"
            camera.type.contains("Hikvision") || camera.type.contains("Prama") ->
                "/Streaming/Channels/${targetChannel}$hikSubCode"
            camera.type.contains("Tiandy") ->
                "/$targetChannel/${if (isMain) 1 else 2}"
            camera.type.contains("Tapo") || camera.type.contains("TP-Link") ->
                if (isMain) "/stream1" else "/stream2"
            camera.type.contains("Uniview") || camera.type.contains("UNV") ->
                "/unicast/c$targetChannel/s${if (isMain) 1 else 2}/live"
            camera.type.contains("Axis") ->
                "/axis-media/media.amp?videocodec=h264"
            camera.type.contains("Reolink") ->
                "/h264Preview_${String.format("%02d", targetChannel)}_${if (isMain) "main" else "sub"}"
            camera.type.contains("V380") ->
                "/live/ch$targetChannel"
            else ->
                // Generic ONVIF / RTSP fallback
                "/cam/realmonitor?channel=$targetChannel&subtype=$subCode"
        }

        return "rtsp://$authPrefix$trimmedIp:$portNumber$path"
    }

    private fun encodeCredential(value: String): String {
        if (value.isEmpty()) return ""
        // Do NOT turn '@' into '%40' because LIVE555/LibVLC RTSP demuxer uses raw password for Digest MD5 calculation.
        // Only escape spaces, hashes, and percents.
        return value
            .replace("%", "%25")
            .replace(" ", "%20")
            .replace("#", "%23")
    }

    fun probeChannelCount(type: String): Int {
        return when {
            type.contains("Tapo") || type.contains("TP-Link") -> 1
            type.contains("Ezycam") -> 1
            type.contains("Axis") -> 1
            type.contains("V380") -> 1
            type.contains("HLS") || type.contains("DASH") -> 1
            else -> 4
        }
    }
}