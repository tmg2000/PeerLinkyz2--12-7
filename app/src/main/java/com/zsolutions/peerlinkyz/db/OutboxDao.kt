package com.zsolutions.peerlinkyz.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(message: OutboxMessage): Long

    @Query("SELECT * FROM outbox WHERE sent = 0 ORDER BY timestamp ASC")
    suspend fun getUnsentMessages(): List<OutboxMessage>

    @Query("UPDATE outbox SET sent = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)
}