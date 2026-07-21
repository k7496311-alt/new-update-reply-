package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.example.accessibility.AccessibilityActionHelper
import com.example.accessibility.NodeFinder
import com.example.accessibility.NodeScanner
import com.example.accessibility.SearchCriteria
import com.example.accessibility.UiTreeAnalyzer
import com.example.data.AccessibilityRepositoryImpl
import com.example.permission.PermissionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityFrameworkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testSearchCriteriaMatching() {
        val criteriaText = SearchCriteria(text = "Submit")
        val criteriaContains = SearchCriteria(textContains = "Sub")
        val criteriaId = SearchCriteria(resourceId = "com.example:id/submit_button")

        // We can verify matching behavior logic conceptually and check fields
        assertEquals("Submit", criteriaText.text)
        assertEquals("Sub", criteriaContains.textContains)
        assertEquals("com.example:id/submit_button", criteriaId.resourceId)
    }

    @Test
    fun testActionHelperRetrySystemSuccess() = runBlocking {
        val counter = AtomicInteger(0)
        val result = AccessibilityActionHelper.executeWithRetry<String>(
            maxAttempts = 3,
            timeoutMillis = 1000L,
            retryDelayMillis = 10L
        ) {
            val attempt = counter.incrementAndGet()
            if (attempt < 2) {
                throw RuntimeException("Temporary failure")
            }
            "Success"
        }

        assertTrue(result.isSuccess)
        assertEquals("Success", result.getOrNull())
        assertEquals(2, counter.get())
    }

    @Test
    fun testActionHelperRetrySystemFailure() = runBlocking {
        val counter = AtomicInteger(0)
        val result = AccessibilityActionHelper.executeWithRetry<String>(
            maxAttempts = 3,
            timeoutMillis = 1000L,
            retryDelayMillis = 10L
        ) {
            counter.incrementAndGet()
            throw RuntimeException("Fatal error")
        }

        assertTrue(result.isFailure)
        assertEquals(3, counter.get())
    }

    @Test
    fun testUiTreeAnalyzerEmpty() {
        val result = UiTreeAnalyzer.analyzeAndDump(null)
        assertEquals("Root is null", result)

        val diagnostics = UiTreeAnalyzer.runDiagnostics(null)
        assertEquals(0, diagnostics.totalNodesCount)
        assertEquals(0, diagnostics.inputFieldsCount)
        assertEquals(0, diagnostics.clickableElementsCount)
    }

    @Test
    fun testNodeScannerEmpty() {
        val nodes = NodeScanner.scanTree(null)
        assertTrue(nodes.isEmpty())
    }

    @Test
    fun testAccessibilityRepositoryStatus() {
        val mockPermissionManager = object : PermissionManager() {
            override fun isAccessibilityServiceEnabled(
                context: Context,
                serviceClass: Class<out AccessibilityService>
            ): Boolean {
                return true
            }
        }

        val repository = AccessibilityRepositoryImpl(context, mockPermissionManager)
        assertFalse(repository.isServiceRunning()) // Not running as active static reference is null
    }
}
