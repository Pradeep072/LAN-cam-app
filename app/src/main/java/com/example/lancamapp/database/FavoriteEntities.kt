package com.example.lancamapp.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_grids")
data class FavoriteGrid(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val maxSlots: Int
)

@Entity(
    tableName = "favorite_grid_slots",
    foreignKeys = [
        ForeignKey(
            entity = FavoriteGrid::class,
            parentColumns = ["id"],
            childColumns = ["gridId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FavoriteGridSlot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gridId: Int,
    val slotIndex: Int,
    val cameraId: Int,
    val channelNumber: Int
)
