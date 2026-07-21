package com.example

import com.example.model.AutoReplyRule
import com.example.model.MatchType
import com.example.model.ReplyHistory
import com.example.model.RuleStatus
import com.example.repository.HistoryRepository
import com.example.repository.RuleRepository
import com.example.rule.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class RuleEngineCoreTest {

    // --- Fakes implementation for clean testing without real DB ---
    private class FakeRuleRepository : RuleRepository {
        val rules = mutableListOf<AutoReplyRule>()
        override fun getAllRules(): Flow<List<AutoReplyRule>> = flowOf(rules)
        override suspend fun getActiveRules(): List<AutoReplyRule> = rules.filter { it.isEnabled }
        override suspend fun getRuleById(id: Long): AutoReplyRule? = rules.find { it.id == id }
        override suspend fun saveRule(rule: AutoReplyRule): Long {
            val toSave = if (rule.id == 0L) rule.copy(id = (rules.size + 1).toLong()) else rule
            rules.removeIf { it.id == toSave.id }
            rules.add(toSave)
            return toSave.id
        }
        override suspend fun deleteRule(rule: AutoReplyRule) {
            rules.removeIf { it.id == rule.id }
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        val history = mutableListOf<ReplyHistory>()
        override fun getAllHistory(): Flow<List<ReplyHistory>> = flowOf(history)
        override suspend fun saveHistory(historyItem: ReplyHistory): Long {
            val toSave = if (historyItem.id == 0L) historyItem.copy(id = (history.size + 1).toLong()) else historyItem
            history.add(toSave)
            return toSave.id
        }
        override suspend fun deleteHistory(historyItem: ReplyHistory) {
            history.removeIf { it.id == historyItem.id }
        }
        override suspend fun clearHistory() {
            history.clear()
        }
        override suspend fun getReplyCountForRule(ruleId: Long): Int {
            return history.count { it.ruleId == ruleId }
        }
        override suspend fun getReplyCountForRuleSince(ruleId: Long, sinceTimestamp: Long): Int {
            return history.count { it.ruleId == ruleId && it.timestamp >= sinceTimestamp }
        }
        override suspend fun getLastReplyTimestampForRule(ruleId: Long): Long? {
            return history.filter { it.ruleId == ruleId }.maxOfOrNull { it.timestamp }
        }
    }

    // --- 1. RuleMatcher Tests ---
    @Test
    fun testMatcher_matchTypes() {
        val matcher = RuleMatcher()

        // Contains Match
        val containsRule = AutoReplyRule(id = 1, name = "Rule1", keyword = "hello", replyText = "Hi!", matchType = MatchType.CONTAINS)
        assertTrue(matcher.isMatch("hello world", containsRule))
        assertTrue(matcher.isMatch("say hello to me", containsRule))
        assertFalse(matcher.isMatch("goodbye world", containsRule))

        // Exact Match
        val exactRule = AutoReplyRule(id = 2, name = "Rule2", keyword = "hello", replyText = "Hi!", matchType = MatchType.EXACT)
        assertTrue(matcher.isMatch("hello", exactRule))
        assertFalse(matcher.isMatch("hello world", exactRule))

        // Starts With Match
        val startRule = AutoReplyRule(id = 3, name = "Rule3", keyword = "hello", replyText = "Hi!", matchType = MatchType.STARTS_WITH)
        assertTrue(matcher.isMatch("hello friend", startRule))
        assertFalse(matcher.isMatch("my hello friend", startRule))

        // Ends With Match
        val endRule = AutoReplyRule(id = 4, name = "Rule4", keyword = "hello", replyText = "Hi!", matchType = MatchType.ENDS_WITH)
        assertTrue(matcher.isMatch("say hello", endRule))
        assertFalse(matcher.isMatch("say hello to everyone", endRule))

        // Exclude Match
        val excludeRule = AutoReplyRule(id = 5, name = "Rule5", keyword = "spam", replyText = "Hi!", matchType = MatchType.EXCLUDE)
        assertTrue(matcher.isMatch("normal friendly message", excludeRule))
        assertFalse(matcher.isMatch("this is spam email", excludeRule))

        // Regular Expression Match
        val regexRule = AutoReplyRule(id = 6, name = "Rule6", keyword = "\\d{3}-\\d{3}", replyText = "Code!", matchType = MatchType.REGEX)
        assertTrue(matcher.isMatch("Your code is 123-456 today", regexRule))
        assertFalse(matcher.isMatch("Your code is 12-34-56 today", regexRule))
    }

    @Test
    fun testMatcher_flags() {
        val matcher = RuleMatcher()

        // Case Sensitivity
        val caseSensitiveRule = AutoReplyRule(
            id = 1, name = "R1", keyword = "Hello", replyText = "Hi!",
            matchType = MatchType.EXACT, isCaseSensitive = true
        )
        val caseInsensitiveRule = AutoReplyRule(
            id = 2, name = "R2", keyword = "Hello", replyText = "Hi!",
            matchType = MatchType.EXACT, isCaseSensitive = false
        )
        assertFalse(matcher.isMatch("hello", caseSensitiveRule))
        assertTrue(matcher.isMatch("hello", caseInsensitiveRule))

        // Trim Spaces
        val trimSpacesRule = AutoReplyRule(
            id = 3, name = "R3", keyword = " hello ", replyText = "Hi!",
            matchType = MatchType.EXACT, shouldTrimSpaces = true
        )
        val noTrimRule = AutoReplyRule(
            id = 4, name = "R4", keyword = " hello ", replyText = "Hi!",
            matchType = MatchType.EXACT, shouldTrimSpaces = false
        )
        assertTrue(matcher.isMatch("  hello  ", trimSpacesRule))
        assertFalse(matcher.isMatch("  hello  ", noTrimRule))

        // Ignore Emoji
        val ignoreEmojiRule = AutoReplyRule(
            id = 5, name = "R5", keyword = "wave", replyText = "Hi!",
            matchType = MatchType.CONTAINS, shouldIgnoreEmoji = true
        )
        val respectEmojiRule = AutoReplyRule(
            id = 6, name = "R6", keyword = "wave", replyText = "Hi!",
            matchType = MatchType.CONTAINS, shouldIgnoreEmoji = false
        )
        assertTrue(matcher.isMatch("wave 👋😊", ignoreEmojiRule))
        // They should both match because "wave" is still present
        assertTrue(matcher.isMatch("wave 👋😊", respectEmojiRule))
        
        // When emoji is part of the keyword
        val emojiKwRule = AutoReplyRule(
            id = 7, name = "R7", keyword = "hello 👋", replyText = "Hi!",
            matchType = MatchType.EXACT, shouldIgnoreEmoji = true
        )
        assertTrue(matcher.isMatch("hello 😊", emojiKwRule)) // both stripped of emojis -> "hello" == "hello"

        // Ignore Symbols
        val ignoreSymbolsRule = AutoReplyRule(
            id = 8, name = "R8", keyword = "hello", replyText = "Hi!",
            matchType = MatchType.EXACT, shouldIgnoreSymbols = true
        )
        assertTrue(matcher.isMatch("hello!!!", ignoreSymbolsRule))

        // Ignore Multiple Spaces
        val ignoreMultiSpacesRule = AutoReplyRule(
            id = 9, name = "R9", keyword = "hello world", replyText = "Hi!",
            matchType = MatchType.EXACT, shouldIgnoreMultipleSpaces = true
        )
        assertTrue(matcher.isMatch("hello    world", ignoreMultiSpacesRule))
    }

    @Test
    fun testMatcher_unlimitedMultiKeywords() {
        val matcher = RuleMatcher()

        // Multiple keywords (comma-separated list)
        val multiKeywordRule = AutoReplyRule(
            id = 1, name = "Multi", keyword = "hello, hi, hey, greetings",
            replyText = "Hi there!", matchType = MatchType.EXACT
        )
        assertTrue(matcher.isMatch("hello", multiKeywordRule))
        assertTrue(matcher.isMatch("hi", multiKeywordRule))
        assertTrue(matcher.isMatch("hey", multiKeywordRule))
        assertTrue(matcher.isMatch("greetings", multiKeywordRule))
        assertFalse(matcher.isMatch("howdy", multiKeywordRule))
    }

    // --- 2. RuleValidator Tests ---
    @Test
    fun testValidator_cooldownAndLimits() = runBlocking {
        val validator = RuleValidator()
        val historyRepo = FakeHistoryRepository()

        // Rule is disabled
        val disabledRule = AutoReplyRule(id = 1, name = "Disabled", keyword = "kw", replyText = "reply", isEnabled = false)
        val disabledResult = validator.validate(disabledRule, historyRepo)
        assertTrue(disabledResult is ValidationResult.Invalid)

        // Valid rule with no limits
        val validRule = AutoReplyRule(id = 2, name = "Valid", keyword = "kw", replyText = "reply", isEnabled = true)
        val validResult = validator.validate(validRule, historyRepo)
        assertTrue(validResult is ValidationResult.Valid)

        // Cooldown evaluation
        val cooldownRule = AutoReplyRule(id = 3, name = "Cooldown", keyword = "kw", replyText = "reply", cooldownMillis = 10000L)
        
        // First validation is fine because history is empty
        assertTrue(validator.validate(cooldownRule, historyRepo) is ValidationResult.Valid)
        
        // Insert a history entry representing a sent reply right now
        historyRepo.saveHistory(
            ReplyHistory(
                ruleId = 3,
                ruleName = "Cooldown",
                senderName = "Alice",
                incomingMessage = "kw",
                repliedMessage = "reply",
                packageName = "com.test",
                timestamp = System.currentTimeMillis()
            )
        )

        // Immediate validation fails on cooldown
        assertTrue(validator.validate(cooldownRule, historyRepo) is ValidationResult.Invalid)

        // Global Limit evaluation (coinciding with maxReplies and globalLimit)
        val limitRule = AutoReplyRule(id = 4, name = "Limit", keyword = "kw", replyText = "reply", globalLimit = 2)
        assertTrue(validator.validate(limitRule, historyRepo) is ValidationResult.Valid)

        historyRepo.saveHistory(ReplyHistory(ruleId = 4, ruleName = "Limit", senderName = "Bob", incomingMessage = "kw", repliedMessage = "r", packageName = "com.test", timestamp = System.currentTimeMillis()))
        assertTrue(validator.validate(limitRule, historyRepo) is ValidationResult.Valid)

        historyRepo.saveHistory(ReplyHistory(ruleId = 4, ruleName = "Limit", senderName = "Bob", incomingMessage = "kw", repliedMessage = "r", packageName = "com.test", timestamp = System.currentTimeMillis()))
        // Third execution is blocked by global limit (2)
        assertTrue(validator.validate(limitRule, historyRepo) is ValidationResult.Invalid)

        // Daily Limit evaluation
        val dailyRule = AutoReplyRule(id = 5, name = "Daily", keyword = "kw", replyText = "reply", dailyLimit = 1)
        assertTrue(validator.validate(dailyRule, historyRepo) is ValidationResult.Valid)

        historyRepo.saveHistory(ReplyHistory(ruleId = 5, ruleName = "Daily", senderName = "Bob", incomingMessage = "kw", repliedMessage = "r", packageName = "com.test", timestamp = System.currentTimeMillis()))
        // Second execution in the same day is blocked by daily limit (1)
        assertTrue(validator.validate(dailyRule, historyRepo) is ValidationResult.Invalid)
    }

    // --- 3. RuleExecutor Tests ---
    @Test
    fun testExecutor_placeholders() {
        val executor = RuleExecutor()
        val rule = AutoReplyRule(id = 1, name = "Exec", keyword = "hi", replyText = "Hello {sender}, you sent: {message}")

        val result = executor.execute(rule, "Alice", "hi there!")
        assertTrue(result is ExecutionResult.Success)
        val success = result as ExecutionResult.Success
        assertEquals("Hello Alice, you sent: hi there!", success.replyText)
    }

    // --- 4. RuleEngineManager Pipeline Tests ---
    @Test
    fun testPipeline_priorityAndExecution() = runBlocking {
        val ruleRepo = FakeRuleRepository()
        val historyRepo = FakeHistoryRepository()
        val manager = RuleEngineManager(ruleRepo, historyRepo)

        // Add low priority rule
        val lowPriority = AutoReplyRule(
            id = 1, name = "Low", keyword = "test", replyText = "Low Reply",
            priority = 1, isEnabled = true, matchType = MatchType.CONTAINS
        )
        // Add high priority rule
        val highPriority = AutoReplyRule(
            id = 2, name = "High", keyword = "test", replyText = "High Reply",
            priority = 10, isEnabled = true, matchType = MatchType.CONTAINS
        )

        ruleRepo.saveRule(lowPriority)
        ruleRepo.saveRule(highPriority)

        // High priority rule matches and gets selected first
        val result = manager.processPipeline("Alice", "this is a test message")
        assertTrue(result is PipelineResult.Matched)
        val matchedResult = result as PipelineResult.Matched
        assertEquals(2L, matchedResult.rule.id)
        assertEquals("High Reply", matchedResult.replyText)
    }
}
