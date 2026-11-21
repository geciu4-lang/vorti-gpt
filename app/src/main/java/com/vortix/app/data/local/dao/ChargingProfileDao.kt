package com.vortix.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vortix.app.data.local.entity.ChargingProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ChargingProfileEntity): Long

    @Update
    suspend fun update(profile: ChargingProfileEntity)

    @Query("SELECT * FROM charging_profiles ORDER BY startedAtEpochMillis DESC LIMIT 20")
    fun observeRecent(): Flow<List<ChargingProfileEntity>>

    @Query("SELECT * FROM charging_profiles WHERE endedAtEpochMillis IS NULL ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<ChargingProfileEntity?>

    @Query("SELECT * FROM charging_profiles WHERE endedAtEpochMillis IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun findActive(): ChargingProfileEntity?
}
