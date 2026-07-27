package com.example.repository

import com.example.model.QueueItem
import com.example.model.QueueStatus
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun getAllQueueItems(): Flow<List<QueueItem>>
    suspend fun getQueueItemsByStatus(status: QueueStatus): List<QueueItem>
    suspend fun getQueueItemsByStatuses(statuses: List<QueueStatus>): List<QueueItem>
    suspend fun getActiveQueueCount(): Int
    suspend fun findDuplicate(packageName: String, senderName: String, incomingMessage: String): QueueItem?
    suspend fun findActiveQueueItemBySender(packageName: String, senderName: String): QueueItem?
    suspend fun getQueueItemById(id: Long): QueueItem?
    suspend fun saveQueueItem(item: QueueItem): Long
    suspend fun deleteQueueItem(item: QueueItem)
    suspend fun deleteQueueItemById(id: Long)
    suspend fun clearQueue()
}

