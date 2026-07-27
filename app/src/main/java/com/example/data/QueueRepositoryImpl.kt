package com.example.data

import com.example.database.QueueDao
import com.example.database.QueueEntity
import com.example.model.QueueItem
import com.example.model.QueueStatus
import com.example.repository.QueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QueueRepositoryImpl(
    private val queueDao: QueueDao
) : QueueRepository {

    override fun getAllQueueItems(): Flow<List<QueueItem>> {
        return queueDao.getAllQueueItemsFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getQueueItemsByStatus(status: QueueStatus): List<QueueItem> {
        return queueDao.getQueueItemsByStatus(status.name).map { it.toDomainModel() }
    }

    override suspend fun getQueueItemsByStatuses(statuses: List<QueueStatus>): List<QueueItem> {
        val statusNames = statuses.map { it.name }
        return queueDao.getQueueItemsByStatuses(statusNames).map { it.toDomainModel() }
    }

    override suspend fun getActiveQueueCount(): Int {
        return queueDao.getActiveQueueCount()
    }

    override suspend fun findDuplicate(packageName: String, senderName: String, incomingMessage: String): QueueItem? {
        return queueDao.findDuplicate(packageName, senderName, incomingMessage)?.toDomainModel()
    }

    override suspend fun findActiveQueueItemBySender(packageName: String, senderName: String): QueueItem? {
        return queueDao.findActiveQueueItemBySender(packageName, senderName)?.toDomainModel()
    }

    override suspend fun getQueueItemById(id: Long): QueueItem? {
        return queueDao.getQueueItemById(id)?.toDomainModel()
    }

    override suspend fun saveQueueItem(item: QueueItem): Long {
        val entity = QueueEntity.fromDomainModel(item)
        return if (entity.id == 0L) {
            queueDao.insertQueueItem(entity)
        } else {
            queueDao.updateQueueItem(entity)
            entity.id
        }
    }

    override suspend fun deleteQueueItem(item: QueueItem) {
        queueDao.deleteQueueItem(QueueEntity.fromDomainModel(item))
    }

    override suspend fun deleteQueueItemById(id: Long) {
        queueDao.deleteQueueItemById(id)
    }

    override suspend fun clearQueue() {
        queueDao.clearQueue()
    }
}
