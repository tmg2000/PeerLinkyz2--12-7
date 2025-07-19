package com.zsolutions.peerlinkyz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderOnionAddress: String,
    val recipientOnionAddress: String,
    val message: ByteArray,
    val sent: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
