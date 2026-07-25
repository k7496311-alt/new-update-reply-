package com.example.performance

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import com.example.accessibility.AccessibilityLogger
import com.example.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic & Telemetry Monitor for tracking auto-reply application performance metrics:
 * - Average reply latency
 * - Queue wait time
 * - Success rate
 * - Failure distribution
 * - Device battery level & temperature
 * - Application memory footprint
 */
data class PerformanceMetrics(
    val totalProcessed: Int = 0,
    val totalSuccesses: Int = 0,
    val totalFailures: Int = 0,
    val successRatePercentage: Float = 100f,
    val averageReplyTimeMs: Long = 0L,
    val averageQueueWaitTimeMs: Long = 0L,
    val memoryUsedMb: Float = 0f,
    val totalMemoryMb: Float = 0f,
    val batteryLevelPercentage: Int = 100,
    val batteryTemperatureCelsius: Float = 0f,
    val topFailureReasons: List<Pair<String, Int>> = emptyList()
)

class PerformanceMonitor(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceMonitor"

        @Volatile
        private var instance: PerformanceMonitor? = null

        fun getInstance(context: Context): PerformanceMonitor {
            return instance ?: synchronized(this) {
                instance ?: PerformanceMonitor(context.applicationContext).also { instance = it }
            }
        }
    }

    private val totalProcessedCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)

    private val replyDurationsMs = ConcurrentLinkedQueue<Long>()
    private val queueWaitTimesMs = ConcurrentLinkedQueue<Long>()
    private val failureReasons = ConcurrentLinkedQueue<String>()

    private val _metricsState = MutableStateFlow(PerformanceMetrics())
    val metricsState: StateFlow<PerformanceMetrics> = _metricsState.asStateFlow()

    /**
     * Records a completed processing cycle time for an auto-reply execution.
     */
    fun recordReplyLatency(durationMs: Long) {
        if (durationMs > 0) {
            replyDurationsMs.add(durationMs)
            if (replyDurationsMs.size > 100) {
                replyDurationsMs.poll() // Keep rolling window of 100 samples
            }
        }
        updateMetrics()
    }

    /**
     * Records queue holding duration before processing commenced.
     */
    fun recordQueueWaitTime(waitTimeMs: Long) {
        if (waitTimeMs >= 0) {
            queueWaitTimesMs.add(waitTimeMs)
            if (queueWaitTimesMs.size > 100) {
                queueWaitTimesMs.poll()
            }
        }
        updateMetrics()
    }

    /**
     * Records a successful auto-reply transaction.
     */
    fun recordSuccess() {
        totalProcessedCount.incrementAndGet()
        successCount.incrementAndGet()
        updateMetrics()
    }

    /**
     * Records a failed auto-reply attempt with reason.
     */
    fun recordFailure(reason: String) {
        totalProcessedCount.incrementAndGet()
        failureCount.incrementAndGet()
        if (reason.isNotBlank()) {
            failureReasons.add(reason)
            if (failureReasons.size > 50) {
                failureReasons.poll()
            }
        }
        updateMetrics()
    }

    /**
     * Samples hardware memory & battery metrics and updates snapshot.
     */
    fun updateMetrics() {
        val total = totalProcessedCount.get()
        val succ = successCount.get()
        val fail = failureCount.get()

        val successRate = if (total > 0) (succ.toFloat() / total.toFloat()) * 100f else 100f

        val avgReply = if (replyDurationsMs.isNotEmpty()) {
            replyDurationsMs.average().toLong()
        } else 0L

        val avgQueue = if (queueWaitTimesMs.isNotEmpty()) {
            queueWaitTimesMs.average().toLong()
        } else 0L

        // Memory usage
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val maxMem = runtime.maxMemory() / (1024f * 1024f)

        // Battery status
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemp = tempTenths / 10f

        // Top failure reasons map
        val reasonCounts = failureReasons.groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        _metricsState.value = PerformanceMetrics(
            totalProcessed = total,
            totalSuccesses = succ,
            totalFailures = fail,
            successRatePercentage = successRate,
            averageReplyTimeMs = avgReply,
            averageQueueWaitTimeMs = avgQueue,
            memoryUsedMb = usedMem,
            totalMemoryMb = maxMem,
            batteryLevelPercentage = batteryPct,
            batteryTemperatureCelsius = batteryTemp,
            topFailureReasons = reasonCounts
        )
    }

    /**
     * Refreshes metrics from Room database history records for historical accurate representation.
     */
    suspend fun syncWithDatabaseHistory(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val history = db.historyDao().getAllHistoryList()
            if (history.isNotEmpty()) {
                val succ = history.count { it.isSuccessfullySent }
                val total = history.size
                val fail = total - succ

                totalProcessedCount.set(total)
                successCount.set(succ)
                failureCount.set(fail)
                updateMetrics()
            }
        } catch (e: Exception) {
            AccessibilityLogger.e(TAG, "Error syncing performance monitor with database: ${e.message}", e)
        }
    }
}
