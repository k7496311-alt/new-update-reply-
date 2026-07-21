package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.HistoryStatus
import com.example.model.ReplyHistory
import com.example.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryStatusFilter {
    ALL,
    SENT,
    FAILED,
    SKIPPED
}

class HistoryViewModel(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val statusFilter = MutableStateFlow(HistoryStatusFilter.ALL)

    val historyState: StateFlow<List<ReplyHistory>> = combine(
        historyRepository.getAllHistory(),
        searchQuery,
        statusFilter
    ) { historyList, query, filter ->
        val filteredByStatus = when (filter) {
            HistoryStatusFilter.ALL -> historyList
            HistoryStatusFilter.SENT -> historyList.filter { it.status == HistoryStatus.SENT }
            HistoryStatusFilter.FAILED -> historyList.filter { it.status == HistoryStatus.FAILED }
            HistoryStatusFilter.SKIPPED -> historyList.filter { it.status == HistoryStatus.SKIPPED }
        }

        if (query.isBlank()) {
            filteredByStatus
        } else {
            filteredByStatus.filter {
                it.senderName.contains(query, ignoreCase = true) ||
                it.incomingMessage.contains(query, ignoreCase = true) ||
                it.repliedMessage.contains(query, ignoreCase = true) ||
                it.ruleName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true) ||
                it.reason.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setStatusFilter(filter: HistoryStatusFilter) {
        statusFilter.value = filter
    }

    fun deleteHistory(history: ReplyHistory) {
        viewModelScope.launch {
            historyRepository.deleteHistory(history)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    /**
     * Generates a standard CSV representation of the filtered history.
     */
    fun getHistoryAsCsv(items: List<ReplyHistory>): String {
        val builder = StringBuilder()
        // CSV Header
        builder.append("ID,Timestamp,Rule ID,Rule Name,Sender,Incoming Message,Replied Message,Package Name,Status,Reason\n")
        for (item in items) {
            val id = item.id
            val timestamp = item.timestamp
            val ruleId = item.ruleId
            val ruleName = escapeCsv(item.ruleName)
            val sender = escapeCsv(item.senderName)
            val incoming = escapeCsv(item.incomingMessage)
            val replied = escapeCsv(item.repliedMessage)
            val pkg = escapeCsv(item.packageName)
            val status = item.status.name
            val reason = escapeCsv(item.reason)
            builder.append("$id,$timestamp,$ruleId,$ruleName,$sender,$incoming,$replied,$pkg,$status,$reason\n")
        }
        return builder.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
