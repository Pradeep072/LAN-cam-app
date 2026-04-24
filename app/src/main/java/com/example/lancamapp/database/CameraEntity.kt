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
    val type: String,         // "CP_PLUS", "HIKVISION", "TAPO", "GENERIC"
    val channel: Int = 1 ,     // Channel number (useful for DVRs)
    val channelCount: Int = 1 // <--- NEW FIELD (Default 1 for Wi-Fi Cams, 4/8/16 for DVRs)
)