package com.zsolutions.peerlinkyz

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends")
    suspend fun getAllFriends(): List<Friend>

    @Query("SELECT * FROM friends WHERE id = :friendId")
    suspend fun getFriendById(friendId: Int): Friend?

    @Query("SELECT * FROM friends WHERE peerId = :peerId")
    suspend fun getFriendByPeerId(peerId: String): Friend?

    @Insert
    suspend fun insertFriend(friend: Friend): Long

    @Query("DELETE FROM friends WHERE id = :friendId")
    suspend fun deleteFriend(friendId: Int)
}