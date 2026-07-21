package com.example.accessibility.imo

import android.util.Log
import com.example.accessibility.AccessibilityLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Enumeration representing the distinct phases of the automated auto-reply sequence.
 */
enum class OrchestratorState {
    IDLE,
    QUEUED,
    OPENING_CHAT,
    ANALYZING_MESSAGE,
    TRANSCRIBING_VOICE,
    MATCHING_RULES,
    GENERATING_REPLY,
    CHECKING_COOLDOWN,
    SENDING_REPLY,
    VERIFYING_SENT,
    COMPLETING,
    FAILED,
    SKIPPED
}

/**
 * State machine managing the distinct phases of the auto-reply workflow.
 * Provides entry/exit callbacks, transition validations, and timing capabilities.
 */
class OrchestratorStateMachine(
    private val onStateChanged: (oldState: OrchestratorState, newState: OrchestratorState) -> Unit = { _, _ -> }
) {

    companion object {
        private const val TAG = "OrchestratorStateMachine"
    }

    private var _currentState = OrchestratorState.IDLE
    val currentState: OrchestratorState get() = _currentState

    private val entryActions = ConcurrentHashMap<OrchestratorState, () -> Unit>()
    private val exitActions = ConcurrentHashMap<OrchestratorState, () -> Unit>()
    private val stateStartTimes = ConcurrentHashMap<OrchestratorState, Long>()

    init {
        // Setup default entry and exit logs for all states
        OrchestratorState.values().forEach { state ->
            entryActions[state] = {
                val now = System.currentTimeMillis()
                stateStartTimes[state] = now
                AccessibilityLogger.i(TAG, "Entered state: $state")
            }
            exitActions[state] = {
                val start = stateStartTimes[state] ?: System.currentTimeMillis()
                val duration = System.currentTimeMillis() - start
                AccessibilityLogger.d(TAG, "Exited state: $state (Time spent: ${duration}ms)")
            }
        }
    }

    /**
     * Set a custom action to be executed when entering a specific state.
     */
    fun setEntryAction(state: OrchestratorState, action: () -> Unit) {
        val defaultAction = entryActions[state]
        entryActions[state] = {
            defaultAction?.invoke()
            action()
        }
    }

    /**
     * Set a custom action to be executed when exiting a specific state.
     */
    fun setExitAction(state: OrchestratorState, action: () -> Unit) {
        val defaultAction = exitActions[state]
        exitActions[state] = {
            defaultAction?.invoke()
            action()
        }
    }

    /**
     * Checks if a transition from [currentState] to [newState] is valid.
     * Includes constraints:
     * - Can transition to FAILED from any state.
     * - Can transition to SKIPPED from ANALYZING_MESSAGE, CHECKING_COOLDOWN, and GENERATING_REPLY.
     */
    fun isValidTransition(newState: OrchestratorState): Boolean {
        if (newState == OrchestratorState.FAILED) return true
        if (newState == currentState) return true

        return when (currentState) {
            OrchestratorState.IDLE -> newState == OrchestratorState.QUEUED || newState == OrchestratorState.OPENING_CHAT
            OrchestratorState.QUEUED -> newState == OrchestratorState.OPENING_CHAT || newState == OrchestratorState.IDLE
            OrchestratorState.OPENING_CHAT -> newState == OrchestratorState.ANALYZING_MESSAGE
            OrchestratorState.ANALYZING_MESSAGE -> newState == OrchestratorState.TRANSCRIBING_VOICE ||
                    newState == OrchestratorState.MATCHING_RULES || newState == OrchestratorState.SKIPPED
            OrchestratorState.TRANSCRIBING_VOICE -> newState == OrchestratorState.MATCHING_RULES
            OrchestratorState.MATCHING_RULES -> newState == OrchestratorState.GENERATING_REPLY || newState == OrchestratorState.SKIPPED
            OrchestratorState.GENERATING_REPLY -> newState == OrchestratorState.CHECKING_COOLDOWN || newState == OrchestratorState.SENDING_REPLY || newState == OrchestratorState.SKIPPED
            OrchestratorState.CHECKING_COOLDOWN -> newState == OrchestratorState.SENDING_REPLY || newState == OrchestratorState.SKIPPED
            OrchestratorState.SENDING_REPLY -> newState == OrchestratorState.VERIFYING_SENT
            OrchestratorState.VERIFYING_SENT -> newState == OrchestratorState.COMPLETING
            OrchestratorState.COMPLETING -> newState == OrchestratorState.IDLE
            OrchestratorState.FAILED -> newState == OrchestratorState.IDLE || newState == OrchestratorState.QUEUED
            OrchestratorState.SKIPPED -> newState == OrchestratorState.IDLE || newState == OrchestratorState.COMPLETING || newState == OrchestratorState.QUEUED
        }
    }

    /**
     * Executes the state transition and triggers associated lifecycle callbacks.
     */
    @Synchronized
    fun transitionTo(newState: OrchestratorState) {
        if (_currentState == newState) return

        if (!isValidTransition(newState)) {
            AccessibilityLogger.w(TAG, "Warning: Non-standard transition from $_currentState to $newState")
        }

        val oldState = _currentState

        // Trigger exit action on the old state
        try {
            exitActions[oldState]?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing exit action for $oldState", e)
        }

        _currentState = newState

        // Trigger entry action on the new state
        try {
            entryActions[newState]?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing entry action for $newState", e)
        }

        onStateChanged(oldState, newState)
    }

    /**
     * Returns the duration in milliseconds that has elapsed since entering the current state.
     */
    fun getTimeSpentInCurrentState(): Long {
        val start = stateStartTimes[_currentState] ?: return 0L
        return System.currentTimeMillis() - start
    }

    /**
     * Resets the machine to the idle state.
     */
    @Synchronized
    fun reset() {
        transitionTo(OrchestratorState.IDLE)
    }
}
