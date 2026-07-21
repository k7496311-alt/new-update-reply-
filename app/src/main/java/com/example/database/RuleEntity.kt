package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AutoReplyRule
import com.example.model.MatchType
import com.example.model.RuleStatus

@Entity(tableName = "reply_rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val keyword: String,
    val replyText: String,
    val isEnabled: Boolean,
    val matchType: String,
    val replyDelayMillis: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val status: RuleStatus,
    val priority: Int = 0,
    val cooldownMillis: Long = 0L,
    val maxReplies: Int = 0,
    val category: String = "General",

    // Rule Options & Matching Configuration
    val isCaseSensitive: Boolean = false,
    val shouldTrimSpaces: Boolean = true,
    val shouldIgnoreEmoji: Boolean = false,
    val shouldIgnoreSymbols: Boolean = false,
    val shouldIgnoreMultipleSpaces: Boolean = false,

    // Performance and limit rules
    val dailyLimit: Int = 0,
    val globalLimit: Int = 0
) {
    fun toDomainModel(): AutoReplyRule {
        val parsedMatchType = try {
            MatchType.valueOf(matchType)
        } catch (e: IllegalArgumentException) {
            MatchType.CONTAINS
        }
        return AutoReplyRule(
            id = id,
            name = name,
            keyword = keyword,
            replyText = replyText,
            isEnabled = isEnabled,
            matchType = parsedMatchType,
            replyDelayMillis = replyDelayMillis,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status,
            priority = priority,
            cooldownMillis = cooldownMillis,
            maxReplies = maxReplies,
            category = category,
            isCaseSensitive = isCaseSensitive,
            shouldTrimSpaces = shouldTrimSpaces,
            shouldIgnoreEmoji = shouldIgnoreEmoji,
            shouldIgnoreSymbols = shouldIgnoreSymbols,
            shouldIgnoreMultipleSpaces = shouldIgnoreMultipleSpaces,
            dailyLimit = dailyLimit,
            globalLimit = globalLimit
        )
    }

    companion object {
        fun fromDomainModel(rule: AutoReplyRule): RuleEntity {
            return RuleEntity(
                id = rule.id,
                name = rule.name,
                keyword = rule.keyword,
                replyText = rule.replyText,
                isEnabled = rule.isEnabled,
                matchType = rule.matchType.name,
                replyDelayMillis = rule.replyDelayMillis,
                createdAt = rule.createdAt,
                updatedAt = rule.updatedAt,
                status = rule.status,
                priority = rule.priority,
                cooldownMillis = rule.cooldownMillis,
                maxReplies = rule.maxReplies,
                category = rule.category,
                isCaseSensitive = rule.isCaseSensitive,
                shouldTrimSpaces = rule.shouldTrimSpaces,
                shouldIgnoreEmoji = rule.shouldIgnoreEmoji,
                shouldIgnoreSymbols = rule.shouldIgnoreSymbols,
                shouldIgnoreMultipleSpaces = rule.shouldIgnoreMultipleSpaces,
                dailyLimit = rule.dailyLimit,
                globalLimit = rule.globalLimit
            )
        }
    }
}
