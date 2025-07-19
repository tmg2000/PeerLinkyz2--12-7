package com.zsolutions.peerlinkyz

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val onionAddress: String
)

suspend fun addFriendFromQrJson(context: Context, jsonString: String): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(jsonString)
            val username = json.getString("username")
            val onionAddress = if (json.has("onionAddress")) {
                json.getString("onionAddress")
            } else if (json.has("peerId")) {
                json.getString("peerId")
            } else if (json.has("peerID")) {
                json.getString("peerID")
            } else {
                throw IllegalArgumentException("QR code does not contain 'onionAddress', 'peerId' or 'peerID' field.")
            }

            val newFriend = Friend(
                username = username,
                onionAddress = onionAddress
            )
            val friendDao = AppDatabase.getDatabase(context).friendDao()
            friendDao.insertFriend(newFriend)
            Result.success(username)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
