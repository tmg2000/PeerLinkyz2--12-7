package com.zsolutions.peerlinkyz

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val friendId: Int,
    val data: ByteArray,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (id != other.id) return false
        if (friendId != other.friendId) return false
        if (!data.contentEquals(other.data)) return false
        if (isSent != other.isSent) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + friendId
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isSent.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}