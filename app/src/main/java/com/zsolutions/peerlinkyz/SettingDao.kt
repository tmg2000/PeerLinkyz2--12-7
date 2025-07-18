package com.zsolutions.peerlinkyz

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: Setting)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Update
    suspend fun update(setting: Setting)
}