package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoReplyRule
import com.example.repository.RuleRepository
import com.example.util.ExcelRuleExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    fun saveRule(context: Context? = null, rule: AutoReplyRule) {
        viewModelScope.launch {
            ruleRepository.saveRule(rule)
            syncXls(context)
        }
    }

    fun deleteRule(context: Context? = null, rule: AutoReplyRule) {
        viewModelScope.launch {
            ruleRepository.deleteRule(rule)
            syncXls(context)
        }
    }

    fun toggleRuleEnabled(context: Context? = null, rule: AutoReplyRule) {
        viewModelScope.launch {
            val updated = rule.copy(
                isEnabled = !rule.isEnabled,
                updatedAt = System.currentTimeMillis()
            )
            ruleRepository.saveRule(updated)
            syncXls(context)
        }
    }

    fun duplicateRule(context: Context? = null, rule: AutoReplyRule) {
        viewModelScope.launch {
            val duplicated = rule.copy(
                id = 0L,
                name = "${rule.name} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            ruleRepository.saveRule(duplicated)
            syncXls(context)
        }
    }

    fun toggleAllRules(context: Context? = null, enabled: Boolean) {
        viewModelScope.launch {
            val currentRules = rulesState.value
            currentRules.forEach { rule ->
                if (rule.isEnabled != enabled) {
                    ruleRepository.saveRule(rule.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis()))
                }
            }
            syncXls(context)
        }
    }

    fun backupRules(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val allRules = ruleRepository.getAllRules().first()
            val resultPath = ExcelRuleExporter.exportToDownloads(context, allRules)
            val msg = if (resultPath != null) {
                "Backup XLS saved successfully: $resultPath"
            } else {
                "Backup failed to write XLS file."
            }
            onResult(msg)
        }
    }

    fun restoreRulesFromUri(context: Context, uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val importedRules = ExcelRuleExporter.importRulesFromUri(context, uri)
            var count = 0
            importedRules.forEach { rule ->
                ruleRepository.saveRule(rule)
                count++
            }
            val allRules = ruleRepository.getAllRules().first()
            ExcelRuleExporter.autoSyncXls(context, allRules)
            onResult(count)
        }
    }

    fun clearAllRulesAndBackup(context: Context, onResult: () -> Unit) {
        viewModelScope.launch {
            ruleRepository.deleteAllRules()
            ExcelRuleExporter.clearAllXlsFiles(context)
            onResult()
        }
    }

    private suspend fun syncXls(context: Context?) {
        if (context != null) {
            val allRules = ruleRepository.getAllRules().first()
            ExcelRuleExporter.autoSyncXls(context, allRules)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSortOrder(order: RuleSortOrder) {
        sortOrder.value = order
    }
}
