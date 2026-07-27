package com.example.reply.postverify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Post-Send Verification.
 */
sealed class PostVerifyUiState {
    object Idle : PostVerifyUiState()
    object Verifying : PostVerifyUiState()
    data class Completed(val result: PostVerifyResult) : PostVerifyUiState()
    data class Failed(val result: PostVerifyResult) : PostVerifyUiState()
    data class Error(val message: String) : PostVerifyUiState()
}

/**
 * ViewModel for Post-Send Reply Verification following MVVM architecture.
 */
class PostVerifyViewModel(
    private val verifyEngine: PostVerifyEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<PostVerifyUiState>(PostVerifyUiState.Idle)
    val uiState: StateFlow<PostVerifyUiState> = _uiState.asStateFlow()

    fun verifyReply(criteria: PostVerifyCriteria) {
        viewModelScope.launch {
            _uiState.value = PostVerifyUiState.Verifying
            try {
                val result = verifyEngine.verifyReplyAndComplete(criteria)
                _uiState.value = if (result.isCompleted) {
                    PostVerifyUiState.Completed(result)
                } else {
                    PostVerifyUiState.Failed(result)
                }
            } catch (e: Exception) {
                _uiState.value = PostVerifyUiState.Error("Post-send verification failed: ${e.message}")
            }
        }
    }
}
