package com.example.accessibility.sendbutton

import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Send Button Detector.
 */
sealed class SendButtonDetectorUiState {
    object Idle : SendButtonDetectorUiState()
    object Searching : SendButtonDetectorUiState()
    data class Verified(val result: SendButtonVerificationResult) : SendButtonDetectorUiState()
    data class Missing(val result: SendButtonVerificationResult) : SendButtonDetectorUiState()
    data class Error(val message: String) : SendButtonDetectorUiState()
}

/**
 * ViewModel for Send Button Detector operations following MVVM architecture.
 */
class SendButtonDetectorViewModel(
    private val detectorEngine: SendButtonDetectorEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<SendButtonDetectorUiState>(SendButtonDetectorUiState.Idle)
    val uiState: StateFlow<SendButtonDetectorUiState> = _uiState.asStateFlow()

    fun detectSendButton(
        rootNode: AccessibilityNodeInfo?,
        criteria: SendButtonDetectorCriteria = SendButtonDetectorCriteria()
    ) {
        viewModelScope.launch {
            _uiState.value = SendButtonDetectorUiState.Searching
            try {
                val result = detectorEngine.detectSendButton(rootNode, criteria)
                _uiState.value = if (result.isVerified) {
                    SendButtonDetectorUiState.Verified(result)
                } else {
                    SendButtonDetectorUiState.Missing(result)
                }
            } catch (e: Exception) {
                _uiState.value = SendButtonDetectorUiState.Error("Send button detection failed: ${e.message}")
            }
        }
    }
}
