package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.HistoryStatus
import com.example.model.ReplyHistory

@Entity(tableName = "reply_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val ruleId: Long,
    val ruleName: String,
    val senderName: String,
    val incomingMessage: String,
    val repliedMessage: String,
    val packageName: String,
    val timestamp: Long,
    val isSuccessfullySent: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val status: HistoryStatus,
    val reason: String = ""
) {
    fun toDomainModel(): ReplyHistory {
        return ReplyHistory(
            id = id,
            ruleId = ruleId,
            ruleName = ruleName,
            senderName = senderName,
            incomingMessage = incomingMessage,
            repliedMessage = repliedMessage,
            packageName = packageName,
            timestamp = timestamp,
            isSuccessfullySent = isSuccessfullySent,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status,
            reason = reason
        )
    }

    companion object {
        fun fromDomainModel(history: ReplyHistory): HistoryEntity {
            return HistoryEntity(
                id = history.id,
                ruleId = history.ruleId,
                ruleName = history.ruleName,
                senderName = history.senderName,
                incomingMessage = history.incomingMessage,
                repliedMessage = history.repliedMessage,
                packageName = history.packageName,
                timestamp = history.timestamp,
                isSuccessfullySent = history.isSuccessfullySent,
                createdAt = history.createdAt,
                updatedAt = history.updatedAt,
                status = history.status,
                reason = history.reason
            )
        }
    }
}
