package com.example.lancamapp.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras")
    fun getAllCameras(): Flow<List<CameraEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: CameraEntity)

    @Delete
    suspend fun deleteCamera(camera: CameraEntity)

    // --- FAVORITES ---
    @Insert
    suspend fun insertFavoriteGrid(grid: FavoriteGrid): Long

    @Insert
    suspend fun insertFavoriteGridSlots(slots: List<FavoriteGridSlot>)

    @Transaction
    @Query("SELECT * FROM favorite_grids")
    fun getAllFavoriteGrids(): Flow<List<FavoriteGrid>>

    @Query("SELECT * FROM favorite_grid_slots WHERE gridId = :gridId")
    suspend fun getSlotsForGrid(gridId: Int): List<FavoriteGridSlot>

    @Query("SELECT * FROM cameras WHERE id = :id")
    suspend fun getCameraById(id: Int): CameraEntity?

    @Query("DELETE FROM favorite_grids WHERE id = :gridId")
    suspend fun deleteFavoriteGrid(gridId: Int)
}