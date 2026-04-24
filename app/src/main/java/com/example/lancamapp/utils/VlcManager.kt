package com.example.lancamapp.utils

import android.content.Context
import org.videolan.libvlc.LibVLC

object VlcManager {
    private var libVLC: LibVLC? = null

    fun getLibVLC(context: Context): LibVLC {
        return libVLC ?: synchronized(this) {
            libVLC ?: LibVLC(context, arrayListOf(
                "-vvv",
                "--network-caching=300",
                "--rtsp-tcp",
                "--drop-late-frames",
                "--skip-frames"
            )).also { libVLC = it }
        }
    }

    fun configureMedia(media: org.videolan.libvlc.Media, url: String) {
        media.setHWDecoderEnabled(true, false)
        
        // Use :avcodec-low-delay for low latency decoding without crashing the engine
        media.addOption(":avcodec-low-delay")
        
        if (url.contains(".m3u8") || url.contains(".mpd") || url.startsWith("http")) {
            // Adaptive streams benefit from higher caching
            media.addOption(":network-caching=1500")
            media.addOption(":adaptive-logic=highest")
        } else {
            // RTSP/Low latency streams
            media.addOption(":network-caching=300")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
        }
        
        media.addOption(":drop-late-frames")
        media.addOption(":skip-frames")
    }
}
