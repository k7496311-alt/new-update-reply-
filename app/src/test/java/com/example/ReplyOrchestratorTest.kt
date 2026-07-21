package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityManager
import com.example.accessibility.imo.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplyOrchestratorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testOrchestratorStateMachineTransitions() {
        val stateMachine = OrchestratorStateMachine()
        assertEquals(OrchestratorState.IDLE, stateMachine.currentState)

        // Valid transitions
        stateMachine.transitionTo(OrchestratorState.QUEUED)
        assertEquals(OrchestratorState.QUEUED, stateMachine.currentState)

        stateMachine.transitionTo(OrchestratorState.OPENING_CHAT)
        assertEquals(OrchestratorState.OPENING_CHAT, stateMachine.currentState)

        stateMachine.transitionTo(OrchestratorState.ANALYZING_MESSAGE)
        assertEquals(OrchestratorState.ANALYZING_MESSAGE, stateMachine.currentState)

        stateMachine.transitionTo(OrchestratorState.SKIPPED)
        assertEquals(OrchestratorState.SKIPPED, stateMachine.currentState)

        // Transition to FAILED from any state
        stateMachine.transitionTo(OrchestratorState.FAILED)
        assertEquals(OrchestratorState.FAILED, stateMachine.currentState)

        // Reset
        stateMachine.reset()
        assertEquals(OrchestratorState.IDLE, stateMachine.currentState)
    }

    @Test
    fun testOrchestratorStateMachineTiming() {
        val stateMachine = OrchestratorStateMachine()
        stateMachine.transitionTo(OrchestratorState.OPENING_CHAT)
        
        // Assert timing captures some elapsed milliseconds
        val spent = stateMachine.getTimeSpentInCurrentState()
        assertTrue(spent >= 0L)
    }

    @Test
    fun testOrchestratorStateMachineEntryExitActions() {
        var entryCalled = false
        var exitCalled = false

        val stateMachine = OrchestratorStateMachine()
        stateMachine.setEntryAction(OrchestratorState.MATCHING_RULES) {
            entryCalled = true
        }
        stateMachine.setExitAction(OrchestratorState.MATCHING_RULES) {
            exitCalled = true
        }

        stateMachine.transitionTo(OrchestratorState.QUEUED)
        stateMachine.transitionTo(OrchestratorState.OPENING_CHAT)
        stateMachine.transitionTo(OrchestratorState.ANALYZING_MESSAGE)
        
        // Enter MATCHING_RULES
        stateMachine.transitionTo(OrchestratorState.MATCHING_RULES)
        assertTrue(entryCalled)
        assertFalse(exitCalled)

        // Exit MATCHING_RULES
        stateMachine.transitionTo(OrchestratorState.GENERATING_REPLY)
        assertTrue(exitCalled)
    }
}
