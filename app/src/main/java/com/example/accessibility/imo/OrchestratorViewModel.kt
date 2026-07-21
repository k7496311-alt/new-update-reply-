package com.example.accessibility.imo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel implementation for the Full Reply Orchestrator module.
 * Exposes live states of processing steps, queue items, service running indicators, and control triggers.
 */
class OrchestratorViewModel(
    private val orchestrator: ReplyOrchestrator,
    private val repository: OrchestratorRepository
) : ViewModel() {

    private val _currentState = MutableStateFlow(orchestrator.stateMachine.currentState)
    val currentState = _currentState.asStateFlow()

    /**
     * Emits true if the background worker loop is active.
     */
    val isProcessingActive = orchestrator.isProcessingActive

    /**
     * Exposes a reactive flow of all active/historical queue items.
     */
    val queueItems = repository.getAllQueueItemsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    /**
     * Emits the background auto-reply service's state.
     */
    val isServiceRunning = repository.isServiceRunning
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    init {
        // Register entry hooks on the State Machine to propagate state transitions to the UI Flow
        OrchestratorState.values().forEach { state ->
            orchestrator.stateMachine.setEntryAction(state) {
                _currentState.value = state
            }
        }
    }

    /**
     * Toggles the background processing worker on.
     */
    fun startProcessingWorker() {
        orchestrator.startQueueProcessingWorker()
    }

    /**
     * Toggles the background processing worker off.
     */
    fun stopProcessingWorker() {
        orchestrator.stopQueueProcessingWorker()
    }

    /**
     * Explicitly interrupts any active UI automations.
     */
    fun cancelActiveAutomations() {
        orchestrator.cancelActiveProcessing()
    }

    /**
     * Deletes a queue item from the database.
     */
    fun deleteQueueItem(item: QueueItem) {
        viewModelScope.launch {
            repository.deleteQueueItem(item)
        }
    }
}
