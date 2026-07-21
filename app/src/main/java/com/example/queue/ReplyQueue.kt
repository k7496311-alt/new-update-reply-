package com.example.queue

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ReplyQueue {

    private val queueScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun enqueueReply(ruleId: Long, delayMillis: Long, action: suspend () -> Unit) {
        // Cancel existing pending reply for this rule to avoid rapid duplicated sends
        cancelPendingReply(ruleId)

        val job = queueScope.launch {
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            action()
            activeJobs.remove(ruleId)
        }
        activeJobs[ruleId] = job
    }

    fun cancelPendingReply(ruleId: Long) {
        activeJobs.remove(ruleId)?.cancel()
    }

    fun clearQueue() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}
