package com.example

import com.example.model.AutoReplyRule
import com.example.model.MatchType
import com.example.model.RuleStatus
import com.example.rule.RuleMatchingEngine
import org.junit.Assert.*
import org.junit.Test

class RuleMatchingEngineTest {

    private val engine = RuleMatchingEngine()

    @Test
    fun testBasicMatchTypes() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "Exact", keyword = "hello", replyText = "hi", matchType = MatchType.EXACT),
            AutoReplyRule(id = 2, name = "Contains", keyword = "world", replyText = "hello world", matchType = MatchType.CONTAINS),
            AutoReplyRule(id = 3, name = "StartsWith", keyword = "start", replyText = "begins", matchType = MatchType.STARTS_WITH),
            AutoReplyRule(id = 4, name = "EndsWith", keyword = "end", replyText = "finishes", matchType = MatchType.ENDS_WITH)
        )
        engine.updateRulesIndex(rules)

        // Exact match
        val match1 = engine.findBestMatch("hello")
        assertNotNull(match1)
        assertEquals(1L, match1!!.rule.id)

        // Contains match
        val match2 = engine.findBestMatch("my world today")
        assertNotNull(match2)
        assertEquals(2L, match2!!.rule.id)

        // Starts with match
        val match3 = engine.findBestMatch("start the engine")
        assertNotNull(match3)
        assertEquals(3L, match3!!.rule.id)

        // Ends with match
        val match4 = engine.findBestMatch("this is the end")
        assertNotNull(match4)
        assertEquals(4L, match4!!.rule.id)
    }

    @Test
    fun testExcludeMatching() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "Exclude Match", keyword = "spam", replyText = "clean", matchType = MatchType.EXCLUDE)
        )
        engine.updateRulesIndex(rules)

        // Message contains "spam", so EXCLUDE rule should NOT match
        val matchSpam = engine.findBestMatch("buy this spam product")
        assertNull(matchSpam)

        // Message does not contain "spam", so EXCLUDE rule SHOULD match
        val matchClean = engine.findBestMatch("hello friend")
        assertNotNull(matchClean)
        assertEquals(1L, matchClean!!.rule.id)
    }

    @Test
    fun testPriorityMatching() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "Low Priority", keyword = "help", replyText = "low", priority = 1, matchType = MatchType.CONTAINS),
            AutoReplyRule(id = 2, name = "High Priority", keyword = "help", replyText = "high", priority = 10, matchType = MatchType.CONTAINS)
        )
        engine.updateRulesIndex(rules)

        val match = engine.findBestMatch("please help me")
        assertNotNull(match)
        assertEquals(2L, match!!.rule.id) // high priority rule chosen
        assertEquals("high", match.selectedReply)
    }

    @Test
    fun testMultipleKeywordsCommaSeparated() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "Multi Keyword", keyword = "apple, banana, cherry", replyText = "fruit", matchType = MatchType.CONTAINS)
        )
        engine.updateRulesIndex(rules)

        assertNotNull(engine.findBestMatch("I like apple"))
        assertNotNull(engine.findBestMatch("banana smoothie"))
        assertNotNull(engine.findBestMatch("cherry pie"))
        assertNull(engine.findBestMatch("orange juice"))
    }

    @Test
    fun testMultipleReplies() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "Multi Replies", keyword = "hello", replyText = "Reply A | Reply B | Reply C", matchType = MatchType.CONTAINS)
        )
        engine.updateRulesIndex(rules)

        val match = engine.findBestMatch("hello")
        assertNotNull(match)
        val validReplies = setOf("Reply A", "Reply B", "Reply C")
        assertTrue(match!!.selectedReply in validReplies)
    }

    @Test
    fun testAndCondition() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "AND logic", keyword = "buy AND discount", replyText = "offer", matchType = MatchType.CONTAINS)
        )
        engine.updateRulesIndex(rules)

        // Contains both
        assertNotNull(engine.findBestMatch("where can I buy with a discount?"))

        // Only contains one
        assertNull(engine.findBestMatch("I want to buy a computer"))
        assertNull(engine.findBestMatch("great discount here"))
    }

    @Test
    fun testOrCondition() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "OR logic", keyword = "pizza OR burger", replyText = "food", matchType = MatchType.CONTAINS)
        )
        engine.updateRulesIndex(rules)

        assertNotNull(engine.findBestMatch("I want a pizza"))
        assertNotNull(engine.findBestMatch("let's get a burger"))
        assertNull(engine.findBestMatch("salad only"))
    }

    @Test
    fun testNotCondition() {
        val rules = listOf(
            AutoReplyRule(id = 1, name = "NOT logic", keyword = "meeting AND NOT online", replyText = "office", matchType = MatchType.CONTAINS)
        )
        engine.updateRulesIndex(rules)

        // Meeting and not online -> Matches
        assertNotNull(engine.findBestMatch("we have a meeting in the boardroom"))

        // Meeting but online -> Fails
        assertNull(engine.findBestMatch("we have an online meeting on zoom"))
    }

    @Test
    fun testKeywordGroupsAndParentheses() {
        val rules = listOf(
            AutoReplyRule(
                id = 1,
                name = "Group logic",
                keyword = "(bangla AND classes) OR (english AND exam)",
                replyText = "schedule",
                matchType = MatchType.CONTAINS
            )
        )
        engine.updateRulesIndex(rules)

        // Test group 1 match
        assertNotNull(engine.findBestMatch("Are there any bangla classes?"))

        // Test group 2 match
        assertNotNull(engine.findBestMatch("The english exam is tomorrow"))

        // Partial match fail
        assertNull(engine.findBestMatch("bangla exam schedule"))
        assertNull(engine.findBestMatch("english classes are fun"))
    }

    @Test
    fun testBanglaAndUnicode() {
        val rules = listOf(
            AutoReplyRule(
                id = 1,
                name = "Bangla Rule",
                keyword = "হ্যালো AND ধন্যবাদ",
                replyText = "আপনাকে স্বাগতম",
                matchType = MatchType.CONTAINS
            )
        )
        engine.updateRulesIndex(rules)

        // Unicode Bangla match
        val match = engine.findBestMatch("হ্যালো ভাই, ধন্যবাদ আপনাকে")
        assertNotNull(match)
        assertEquals("আপনাকে স্বাগতম", match!!.selectedReply)

        // Unicode Bangla half match
        assertNull(engine.findBestMatch("হ্যালো ভাই কেমন আছেন"))
    }

    @Test
    fun testIgnorePunctuationAndEmoji() {
        val rules = listOf(
            AutoReplyRule(
                id = 1,
                name = "Clean Rule",
                keyword = "gift card",
                replyText = "reward",
                matchType = MatchType.CONTAINS,
                shouldIgnoreEmoji = true,
                shouldIgnoreSymbols = true
            )
        )
        engine.updateRulesIndex(rules)

        // Message contains punctuation and emojis: "Gift!!! 🎁 Card???" -> should match "gift card"
        val match = engine.findBestMatch("Gift!!! 🎁 Card???")
        assertNotNull(match)
    }

    @Test
    fun testOptimizationForThousandsOfRules() {
        // Generate 2000 rules
        val thousandRules = mutableListOf<AutoReplyRule>()
        for (i in 1..2000) {
            thousandRules.add(
                AutoReplyRule(
                    id = i.toLong(),
                    name = "Rule $i",
                    keyword = "key$i",
                    replyText = "reply$i",
                    matchType = MatchType.CONTAINS
                )
            )
        }
        // Add one special high priority rule at the end
        thousandRules.add(
            AutoReplyRule(
                id = 9999,
                name = "Target Rule",
                keyword = "matchme",
                replyText = "target",
                matchType = MatchType.CONTAINS,
                priority = 5
            )
        )

        val startTime = System.nanoTime()
        engine.updateRulesIndex(thousandRules)
        val indexTime = System.nanoTime() - startTime

        val searchStartTime = System.nanoTime()
        val match = engine.findBestMatch("this is a test message to matchme quickly!")
        val searchTime = System.nanoTime() - searchStartTime

        assertNotNull(match)
        assertEquals(9999L, match!!.rule.id)
        assertEquals("target", match.selectedReply)

        // Optimization check: search should be extremely fast (usually well under 5 milliseconds)
        val searchTimeMs = searchTime / 1_000_000.0
        println("Index time: ${indexTime / 1_000_000.0} ms, Search time for 2000+ rules: $searchTimeMs ms")
        assertTrue("Search should take less than 50ms", searchTimeMs < 50.0)
    }
}
