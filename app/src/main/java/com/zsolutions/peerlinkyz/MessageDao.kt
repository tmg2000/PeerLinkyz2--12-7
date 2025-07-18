package com.zsolutions.peerlinkyz

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE friendId = :friendId ORDER BY timestamp ASC")
    suspend fun getMessagesForFriend(friendId: Int): List<Message>

    @Insert
    suspend fun insertMessage(message: Message)

    @Query("DELETE FROM messages WHERE timestamp < :timestamp")
    suspend fun deleteOldMessages(timestamp: Long): Int

    @Query("DELETE FROM messages WHERE friendId = :friendId")
    suspend fun deleteMessagesForFriend(friendId: Int)
}