package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.LogCategory
import com.example.model.LogLevel
import com.example.model.LogItem
import com.example.repository.LogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogViewModel(private val logRepository: LogRepository) : ViewModel() {

    // Filter states
    val searchQuery = MutableStateFlow("")
    val selectedCategories = MutableStateFlow<Set<LogCategory>>(emptySet())
    val selectedLevels = MutableStateFlow<Set<LogLevel>>(emptySet())

    // All logs from the repository mapped reactive-style
    val logs: StateFlow<List<LogItem>> = combine(
        logRepository.getAllLogs(),
        searchQuery,
        selectedCategories,
        selectedLevels
    ) { allLogs, query, categories, levels ->
        allLogs.filter { log ->
            // Search filter
            val matchesQuery = query.isEmpty() || 
                log.message.contains(query, ignoreCase = true) || 
                (log.extraData?.contains(query, ignoreCase = true) == true)

            // Category filter
            val matchesCategory = categories.isEmpty() || categories.contains(log.category)

            // Level filter
            val matchesLevel = levels.isEmpty() || levels.contains(log.level)

            matchesQuery && matchesCategory && matchesLevel
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleCategoryFilter(category: LogCategory) {
        val current = selectedCategories.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        selectedCategories.value = current
    }

    fun toggleLevelFilter(level: LogLevel) {
        val current = selectedLevels.value.toMutableSet()
        if (current.contains(level)) {
            current.remove(level)
        } else {
            current.add(level)
        }
        selectedLevels.value = current
    }

    fun clearFilters() {
        searchQuery.value = ""
        selectedCategories.value = emptySet()
        selectedLevels.value = emptySet()
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            logRepository.deleteLogById(id)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.clearAllLogs()
        }
    }

    /**
     * Deletes logs older than N days.
     */
    fun performAutoCleanup(days: Int = 7) {
        viewModelScope.launch {
            val thresholdTime = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            logRepository.deleteLogsBefore(thresholdTime)
            logRepository.insertLog(
                category = LogCategory.APPLICATION,
                level = LogLevel.SUCCESS,
                message = "Auto-cleanup completed: logs older than $days days purged.",
                extraData = "Threshold timestamp: $thresholdTime"
            )
        }
    }

    /**
     * Formats logs to a clean, exportable string.
     */
    fun getLogsExportString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return logs.value.joinToString("\n") { log ->
            val time = dateFormat.format(Date(log.timestamp))
            val extra = if (log.extraData != null) " | Details: ${log.extraData}" else ""
            "[$time] [${log.level}] [${log.category}] ${log.message}$extra"
        }
    }
}
