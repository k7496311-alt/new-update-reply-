package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.testing.IntegrationTestSuite
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntegrationTestSuiteRunnerTest {

    @Test
    fun testIntegrationTestSuiteRunsSuccessfully() {
        val testSuite = IntegrationTestSuite()
        val results = testSuite.runFullTestSuite()

        assertTrue("Test suite should produce results", results.isNotEmpty())

        val failedTests = results.filter { !it.isSuccess }
        assertTrue("All integration suite tests should pass. Failures: ${failedTests.map { it.testName }}", failedTests.isEmpty())
    }

    @Test
    fun testRuleEngineSuiteDetails() {
        val testSuite = IntegrationTestSuite()
        val ruleResults = testSuite.runRuleEngineTests()

        assertEquals(5, ruleResults.size)
        assertTrue(ruleResults.all { it.isSuccess })
    }
}
