package com.example.repository

import com.example.model.AutoReplyRule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun getAllRules(): Flow<List<AutoReplyRule>>
    suspend fun getActiveRules(): List<AutoReplyRule>
    suspend fun getRuleById(id: Long): AutoReplyRule?
    suspend fun saveRule(rule: AutoReplyRule): Long
    suspend fun deleteRule(rule: AutoReplyRule)
}
