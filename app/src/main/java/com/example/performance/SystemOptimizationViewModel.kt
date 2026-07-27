package com.example.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.SystemOptimizationMetrics
import com.example.repository.SystemOptimizationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for controlling and monitoring system optimization performance metrics.
 */
class SystemOptimizationViewModel(
    private val systemOptimizationRepository: SystemOptimizationRepository
) : ViewModel() {

    private val _metricsState = MutableStateFlow(SystemOptimizationMetrics())
    val metricsState: StateFlow<SystemOptimizationMetrics> = _metricsState.asStateFlow()

    /**
     * Triggers performance evaluation and logs required metrics.
     */
    fun recordAndLogMetrics(
        processingTimeMs: Long,
        queueTimeMs: Long,
        accessibilityScanTimeMs: Long
    ) {
        viewModelScope.launch {
            val metrics = systemOptimizationRepository.logSystemPerformance(
                processingTimeMs = processingTimeMs,
                queueTimeMs = queueTimeMs,
                accessibilityScanTimeMs = accessibilityScanTimeMs
            )
            _metricsState.value = metrics
        }
    }

    /**
     * Clears cached UI nodes to free memory.
     */
    fun clearNodeCache() {
        viewModelScope.launch {
            systemOptimizationRepository.clearNodeCache()
        }
    }
}
