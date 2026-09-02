package com.example.lancamapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,         // User friendly name (e.g. "Gate Camera")
    val ip: String,
    val port: Int = 554,
    val username: String,
    val password: String,
    val type: String,         // "CP Plus / Dahua", "Hikvision / Prama", "Tapo / TP-Link", etc.
    val channel: Int = 1,     // Channel number
    val channelCount: Int = 1, // Number of channels (1 for Wi-Fi Cams, 4/8/16 for DVRs)
    val customPath: String = "", // Optional custom RTSP path override
    val streamType: String = "sub" // "sub" (Substream) or "main" (Mainstream)
)