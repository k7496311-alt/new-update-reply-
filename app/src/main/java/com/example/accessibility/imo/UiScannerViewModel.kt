package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Accessibility UI Scanner operations.
 */
sealed class UiScannerUiState {
    object Idle : UiScannerUiState()
    object Scanning : UiScannerUiState()
    data class Success(val report: UiScanReport) : UiScannerUiState()
    data class Error(val message: String) : UiScannerUiState()
}

/**
 * ViewModel for Accessibility UI Scanner following MVVM architecture.
 */
class UiScannerViewModel(
    private val scannerEngine: UiScannerEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiScannerUiState>(UiScannerUiState.Idle)
    val uiState: StateFlow<UiScannerUiState> = _uiState.asStateFlow()

    fun scanUiTree() {
        viewModelScope.launch {
            _uiState.value = UiScannerUiState.Scanning
            try {
                val report = scannerEngine.executeScan()
                _uiState.value = UiScannerUiState.Success(report)
            } catch (e: Exception) {
                _uiState.value = UiScannerUiState.Error("Scan failed: ${e.message}")
            }
        }
    }

    fun getInMemoryReport(): UiScanReport? {
        return scannerEngine.getInMemoryTree()
    }
}
