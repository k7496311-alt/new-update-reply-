package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM reply_queue ORDER BY scheduledTime ASC")
    fun getAllQueueItemsFlow(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM reply_queue WHERE status = :statusName ORDER BY scheduledTime ASC")
    suspend fun getQueueItemsByStatus(statusName: String): List<QueueEntity>

    @Query("SELECT * FROM reply_queue WHERE status IN (:statuses) ORDER BY priority DESC, scheduledTime ASC")
    suspend fun getQueueItemsByStatuses(statuses: List<String>): List<QueueEntity>

    @Query("SELECT COUNT(*) FROM reply_queue WHERE status IN ('INCOMING', 'PENDING', 'RETRY', 'PROCESSING')")
    suspend fun getActiveQueueCount(): Int

    @Query("SELECT * FROM reply_queue WHERE packageName = :packageName AND senderName = :senderName AND incomingMessage = :incomingMessage AND status IN ('INCOMING', 'PENDING', 'PROCESSING', 'RETRY') LIMIT 1")
    suspend fun findDuplicate(packageName: String, senderName: String, incomingMessage: String): QueueEntity?

    @Query("SELECT * FROM reply_queue WHERE packageName = :packageName AND senderName = :senderName AND status IN ('INCOMING', 'PENDING', 'PROCESSING', 'RETRY', 'COOLDOWN') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findActiveQueueItemBySender(packageName: String, senderName: String): QueueEntity?

    @Query("SELECT * FROM reply_queue WHERE id = :id LIMIT 1")
    suspend fun getQueueItemById(id: Long): QueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueEntity): Long

    @Update
    suspend fun updateQueueItem(item: QueueEntity)

    @Delete
    suspend fun deleteQueueItem(item: QueueEntity)

    @Query("DELETE FROM reply_queue WHERE id = :id")
    suspend fun deleteQueueItemById(id: Long)

    @Query("DELETE FROM reply_queue")
    suspend fun clearQueue()
}

