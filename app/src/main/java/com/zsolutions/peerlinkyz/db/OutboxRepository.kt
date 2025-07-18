package com.zsolutions.peerlinkyz.db

class OutboxRepository(private val outboxDao: OutboxDao) {

    suspend fun addMessage(message: OutboxMessage): Long {
        return outboxDao.insert(message)
    }

    suspend fun getUnsentMessages(): List<OutboxMessage> {
        return outboxDao.getUnsentMessages()
    }

    suspend fun markAsSent(id: Long) {
        outboxDao.markAsSent(id)
    }

    suspend fun deleteMessage(id: Long) {
        outboxDao.delete(id)
    }
}