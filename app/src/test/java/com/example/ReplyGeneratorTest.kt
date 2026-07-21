package com.example

import com.example.model.AutoReplyRule
import com.example.model.MatchType
import com.example.reply.FinalReply
import com.example.reply.ReplyGenerationStatus
import com.example.reply.ReplyGenerator
import com.example.reply.ReplyGeneratorRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ReplyGeneratorTest {

    // Simple fake repository implementation for tests
    private class FakeReplyGeneratorRepository : ReplyGeneratorRepository {
        var lastReplyTimestampMap = mutableMapOf<Long, Long>()
        var globalReplyCountMap = mutableMapOf<Long, Int>()
        var replyCountSinceMap = mutableMapOf<Pair<Long, Long>, Int>()
        var sequentialIndexMap = mutableMapOf<Long, Int>()
        var defaultReplySetting: String? = null

        override suspend fun getLastReplyTimestamp(ruleId: Long): Long? {
            return lastReplyTimestampMap[ruleId]
        }

        override suspend fun getGlobalReplyCount(ruleId: Long): Int {
            return globalReplyCountMap[ruleId] ?: 0
        }

        override suspend fun getReplyCountSince(ruleId: Long, sinceTimestamp: Long): Int {
            return replyCountSinceMap[Pair(ruleId, sinceTimestamp)] ?: 0
        }

        override suspend fun getSequentialIndex(ruleId: Long): Int {
            return sequentialIndexMap[ruleId] ?: 0
        }

        override suspend fun saveSequentialIndex(ruleId: Long, index: Int) {
            sequentialIndexMap[ruleId] = index
        }

        override suspend fun getDefaultReplySetting(): String? {
            return defaultReplySetting
        }

        override suspend fun saveDefaultReplySetting(reply: String) {
            defaultReplySetting = reply
        }
    }

    private val fakeRepository = FakeReplyGeneratorRepository()
    private val generator = ReplyGenerator(fakeRepository)

    @Test
    fun testStaticReply() = runBlocking {
        val rule = AutoReplyRule(
            id = 1L,
            name = "Static Rule",
            keyword = "hello",
            replyText = "Hello from static reply!",
            matchType = MatchType.EXACT
        )

        val result = generator.generateReply(rule, "Alice", "hello")
        assertEquals(ReplyGenerationStatus.SUCCESS, result.status)
        assertTrue(result.isTriggered)
        assertEquals("Hello from static reply!", result.replyText)
    }

    @Test
    fun testMultipleRepliesRandomSelection() = runBlocking {
        val rule = AutoReplyRule(
            id = 2L,
            name = "Multi Options",
            keyword = "hi",
            replyText = "Response A | Response B | Response C",
            matchType = MatchType.CONTAINS
        )

        val validSet = setOf("Response A", "Response B", "Response C")
        val result = generator.generateReply(rule, "Bob", "hi there")
        assertEquals(ReplyGenerationStatus.SUCCESS, result.status)
        assertTrue(result.replyText in validSet)
    }

    @Test
    fun testSequentialReplyRotation() = runBlocking {
        val rule = AutoReplyRule(
            id = 3L,
            name = "Seq Rule",
            keyword = "count",
            replyText = "[seq] One | Two | Three",
            matchType = MatchType.CONTAINS
        )

        // Set index to 0
        fakeRepository.sequentialIndexMap[rule.id] = 0

        // 1st run: Should return "One" and set next index to 1
        val result1 = generator.generateReply(rule, "Charlie", "count")
        assertEquals("One", result1.replyText)
        assertEquals(1, fakeRepository.sequentialIndexMap[rule.id])

        // 2nd run: Should return "Two" and set next index to 2
        val result2 = generator.generateReply(rule, "Charlie", "count")
        assertEquals("Two", result2.replyText)
        assertEquals(2, fakeRepository.sequentialIndexMap[rule.id])

        // 3rd run: Should return "Three" and set next index to 0 (loops)
        val result3 = generator.generateReply(rule, "Charlie", "count")
        assertEquals("Three", result3.replyText)
        assertEquals(0, fakeRepository.sequentialIndexMap[rule.id])

        // 4th run: Should loop and return "One"
        val result4 = generator.generateReply(rule, "Charlie", "count")
        assertEquals("One", result4.replyText)
    }

    @Test
    fun testDailyRotationReply() = runBlocking {
        val rule = AutoReplyRule(
            id = 4L,
            name = "Daily Rule",
            keyword = "quote",
            replyText = "[daily] Quote Alpha | Quote Beta | Quote Gamma",
            matchType = MatchType.CONTAINS
        )

        val result = generator.generateReply(rule, "Daniel", "quote")
        val validQuotes = setOf("Quote Alpha", "Quote Beta", "Quote Gamma")
        assertEquals(ReplyGenerationStatus.SUCCESS, result.status)
        assertTrue(result.replyText in validQuotes)

        // Verify that the index is calculated deterministically from system millis/days
        val epochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val expectedIdx = (epochDay % 3).toInt()
        val expectedText = listOf("Quote Alpha", "Quote Beta", "Quote Gamma")[expectedIdx]
        assertEquals(expectedText, result.replyText)
    }

    @Test
    fun testTimeBasedReply() = runBlocking {
        val rule = AutoReplyRule(
            id = 5L,
            name = "Time Rule",
            keyword = "support",
            replyText = "[morning] Good morning support! | [afternoon] Good afternoon support! | [evening] Good evening support! | [night] Good night support!",
            matchType = MatchType.CONTAINS
        )

        val result = generator.generateReply(rule, "Eve", "support")
        assertEquals(ReplyGenerationStatus.SUCCESS, result.status)

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val expectedPart = when (hour) {
            in 6..11 -> "Good morning support!"
            in 12..16 -> "Good afternoon support!"
            in 17..21 -> "Good evening support!"
            else -> "Good night support!"
        }
        assertEquals(expectedPart, result.replyText)
    }

    @Test
    fun testCustomTimeRangeBlocks() = runBlocking {
        val rule = AutoReplyRule(
            id = 6L,
            name = "Office Hours",
            keyword = "hours",
            replyText = "[09:00-17:00] We are open! | [17:01-08:59] We are currently closed.",
            matchType = MatchType.CONTAINS
        )

        val result = generator.generateReply(rule, "Frank", "hours")
        assertEquals(ReplyGenerationStatus.SUCCESS, result.status)

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val currentMins = hour * 60 + min
        val startMins = 9 * 60
        val endMins = 17 * 60

        val expected = if (currentMins in startMins..endMins) "We are open!" else "We are currently closed."
        assertEquals(expected, result.replyText)
    }

    @Test
    fun testGreetingReplyAndPlaceholders() = runBlocking {
        val rule = AutoReplyRule(
            id = 7L,
            name = "Greetings Placeholder",
            keyword = "hello",
            replyText = "Hi [Name], [Greeting]! It is [Time] on [Date].",
            matchType = MatchType.CONTAINS
        )

        val result = generator.generateReply(rule, "Grace Kelly", "hello")
        assertEquals(ReplyGenerationStatus.SUCCESS, result.status)

        // Verify name placeholder replacement
        assertTrue(result.replyText.contains("Grace Kelly"))

        // Verify greeting replacement
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val greetingWord = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
        assertTrue(result.replyText.contains(greetingWord))

        // Verify date format pattern replacement
        val dateVal = String.format("%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        assertTrue(result.replyText.contains(dateVal))
    }

    @Test
    fun testCooldownCheck() = runBlocking {
        val rule = AutoReplyRule(
            id = 8L,
            name = "Cooldown Rule",
            keyword = "cooldown",
            replyText = "Don't spam me!",
            matchType = MatchType.CONTAINS,
            cooldownMillis = 10_000L // 10 seconds cooldown
        )

        // Case A: Last sent 5 seconds ago (should block)
        fakeRepository.lastReplyTimestampMap[rule.id] = System.currentTimeMillis() - 5000L
        val resultBlocked = generator.generateReply(rule, "Hank", "cooldown")
        assertEquals(ReplyGenerationStatus.COOLDOWN, resultBlocked.status)
        assertFalse(resultBlocked.isTriggered)

        // Case B: Last sent 15 seconds ago (should pass)
        fakeRepository.lastReplyTimestampMap[rule.id] = System.currentTimeMillis() - 15000L
        val resultAllowed = generator.generateReply(rule, "Hank", "cooldown")
        assertEquals(ReplyGenerationStatus.SUCCESS, resultAllowed.status)
        assertTrue(resultAllowed.isTriggered)
    }

    @Test
    fun testMaximumReplyLimit() = runBlocking {
        val rule = AutoReplyRule(
            id = 9L,
            name = "Limit Rule",
            keyword = "limited",
            replyText = "Limit count!",
            matchType = MatchType.CONTAINS,
            globalLimit = 5,
            dailyLimit = 2
        )

        // Case A: Below all limits
        fakeRepository.globalReplyCountMap[rule.id] = 1
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fakeRepository.replyCountSinceMap[Pair(rule.id, startOfDay)] = 0

        val resultSuccess = generator.generateReply(rule, "Iris", "limited")
        assertEquals(ReplyGenerationStatus.SUCCESS, resultSuccess.status)

        // Case B: Daily limit hit (dailyCount = 2, limit = 2)
        fakeRepository.replyCountSinceMap[Pair(rule.id, startOfDay)] = 2
        val resultDailyHit = generator.generateReply(rule, "Iris", "limited")
        assertEquals(ReplyGenerationStatus.LIMIT_EXCEEDED, resultDailyHit.status)

        // Case C: Reset daily limit but hit global limit (globalCount = 5, limit = 5)
        fakeRepository.replyCountSinceMap[Pair(rule.id, startOfDay)] = 0
        fakeRepository.globalReplyCountMap[rule.id] = 5
        val resultGlobalHit = generator.generateReply(rule, "Iris", "limited")
        assertEquals(ReplyGenerationStatus.LIMIT_EXCEEDED, resultGlobalHit.status)
    }

    @Test
    fun testDefaultReply() = runBlocking {
        // Case A: No matched rule and no default setting -> NO_MATCH
        fakeRepository.defaultReplySetting = null
        val resultNoMatch = generator.generateReply(null, "Jack", "no rules matched")
        assertEquals(ReplyGenerationStatus.NO_MATCH, resultNoMatch.status)
        assertFalse(resultNoMatch.isTriggered)

        // Case B: No matched rule but default setting configured -> DEFAULT
        fakeRepository.defaultReplySetting = "Hello [Name], thank you for your message!"
        val resultDefault = generator.generateReply(null, "Jack", "no rules matched")
        assertEquals(ReplyGenerationStatus.DEFAULT, resultDefault.status)
        assertTrue(resultDefault.isTriggered)
        assertEquals("Hello Jack, thank you for your message!", resultDefault.replyText)
    }
}
