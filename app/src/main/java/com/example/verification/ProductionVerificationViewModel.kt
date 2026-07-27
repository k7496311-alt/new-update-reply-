package com.example.verification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for running and displaying production verification results.
 */
class ProductionVerificationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val verificationEngine = ProductionVerificationEngine(application)

    private val _verificationReport = MutableStateFlow<FullVerificationReport?>(null)
    val verificationReport: StateFlow<FullVerificationReport?> = _verificationReport.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    init {
        runVerification()
    }

    fun runVerification() {
        if (_isExecuting.value) return

        viewModelScope.launch {
            _isExecuting.value = true
            try {
                val report = verificationEngine.runFullProductionVerification()
                _verificationReport.value = report
            } finally {
                _isExecuting.value = false
            }
        }
    }
}
