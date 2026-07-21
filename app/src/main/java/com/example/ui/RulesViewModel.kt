package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoReplyRule
import com.example.repository.RuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RuleSortOrder {
    PRIORITY_DESC,
    UPDATED_DESC,
    NAME_ASC,
    CATEGORY_ASC
}

class RulesViewModel(
    private val ruleRepository: RuleRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(RuleSortOrder.UPDATED_DESC)

    val rulesState: StateFlow<List<AutoReplyRule>> = combine(
        ruleRepository.getAllRules(),
        searchQuery,
        sortOrder
    ) { rules, query, sort ->
        val filtered = if (query.isBlank()) {
            rules
        } else {
            rules.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.keyword.contains(query, ignoreCase = true) ||
                it.replyText.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        }
        
        when (sort) {
            RuleSortOrder.PRIORITY_DESC -> filtered.sortedWith(
                compareByDescending<AutoReplyRule> { it.priority }
                    .thenByDescending { it.updatedAt }
            )
            RuleSortOrder.UPDATED_DESC -> filtered.sortedByDescending { it.updatedAt }
            RuleSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            RuleSortOrder.CATEGORY_ASC -> filtered.sortedBy { it.category.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveRule(rule: AutoReplyRule) {
        viewModelScope.launch {
            ruleRepository.saveRule(rule)
        }
    }

    fun deleteRule(rule: AutoReplyRule) {
        viewModelScope.launch {
            ruleRepository.deleteRule(rule)
        }
    }

    fun toggleRuleEnabled(rule: AutoReplyRule) {
        viewModelScope.launch {
            val updated = rule.copy(
                isEnabled = !rule.isEnabled,
                updatedAt = System.currentTimeMillis()
            )
            ruleRepository.saveRule(updated)
        }
    }

    fun duplicateRule(rule: AutoReplyRule) {
        viewModelScope.launch {
            val duplicated = rule.copy(
                id = 0L,
                name = "${rule.name} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            ruleRepository.saveRule(duplicated)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSortOrder(order: RuleSortOrder) {
        sortOrder.value = order
    }
}
