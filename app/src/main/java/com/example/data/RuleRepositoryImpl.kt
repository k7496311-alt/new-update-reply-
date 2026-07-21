package com.example.data

import com.example.database.RuleDao
import com.example.database.RuleEntity
import com.example.model.AutoReplyRule
import com.example.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepositoryImpl(
    private val ruleDao: RuleDao
) : RuleRepository {

    override fun getAllRules(): Flow<List<AutoReplyRule>> {
        return ruleDao.getAllRulesFlow().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getActiveRules(): List<AutoReplyRule> {
        return ruleDao.getActiveRules().map { it.toDomainModel() }
    }

    override suspend fun getRuleById(id: Long): AutoReplyRule? {
        return ruleDao.getRuleById(id)?.toDomainModel()
    }

    override suspend fun saveRule(rule: AutoReplyRule): Long {
        val entity = RuleEntity.fromDomainModel(rule)
        return if (entity.id == 0L) {
            ruleDao.insertRule(entity)
        } else {
            ruleDao.updateRule(entity)
            entity.id
        }
    }

    override suspend fun deleteRule(rule: AutoReplyRule) {
        ruleDao.deleteRule(RuleEntity.fromDomainModel(rule))
    }
}
