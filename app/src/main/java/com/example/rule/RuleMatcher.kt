package com.example.rule

import com.example.model.AutoReplyRule
import com.example.model.MatchType
import java.util.regex.PatternSyntaxException

class RuleMatcher {

    /**
     * Checks if an incoming message matches the given rule.
     * Splits rule keywords by comma to support multi-keyword matching.
     */
    fun isMatch(incomingMessage: String, rule: AutoReplyRule): Boolean {
        if (!rule.isEnabled) return false

        // Parse keywords: split by comma to support unlimited multi-keywords
        val keywords = rule.keyword.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (keywords.isEmpty()) return false

        // For Exclude type, we want to match only if NONE of the keywords are present in the message
        if (rule.matchType == MatchType.EXCLUDE) {
            return keywords.all { keyword ->
                !evaluateSingleMatch(incomingMessage, keyword, rule)
            }
        }

        // For other types, match if ANY of the keywords match
        return keywords.any { keyword ->
            evaluateSingleMatch(incomingMessage, keyword, rule)
        }
    }

    private fun evaluateSingleMatch(message: String, keyword: String, rule: AutoReplyRule): Boolean {
        // Preprocess message and keyword based on configuration flags
        var processedMsg = message
        var processedKw = keyword

        // 1. Ignore Emoji
        if (rule.shouldIgnoreEmoji) {
            processedMsg = removeEmojis(processedMsg)
            processedKw = removeEmojis(processedKw)
        }

        // 2. Ignore Symbols
        if (rule.shouldIgnoreSymbols) {
            processedMsg = removeSymbols(processedMsg)
            processedKw = removeSymbols(processedKw)
        }

        // 3. Ignore Multiple Spaces
        if (rule.shouldIgnoreMultipleSpaces) {
            processedMsg = removeMultipleSpaces(processedMsg)
            processedKw = removeMultipleSpaces(processedKw)
        }

        // 4. Trim Spaces
        if (rule.shouldTrimSpaces) {
            processedMsg = processedMsg.trim()
            processedKw = processedKw.trim()
        }

        if (processedMsg.isEmpty() && processedKw.isNotEmpty()) return false

        // 5. Handle Regular Expression separately as it compiles its own options
        if (rule.matchType == MatchType.EXACT && rule.keyword.startsWith("^") && rule.keyword.endsWith("$")) {
            // Treat as regex helper if requested, but matchType.REGEX is the official way
        }

        if (rule.matchType == MatchType.EXCLUDE) {
            // Exclude is already handled at the list level, but in single match context,
            // we evaluate the presence of the keyword.
            return isTextMatch(processedMsg, processedKw, MatchType.CONTAINS, rule.isCaseSensitive)
        }

        return isTextMatch(processedMsg, processedKw, rule.matchType, rule.isCaseSensitive)
    }

    private fun isTextMatch(
        message: String,
        keyword: String,
        matchType: MatchType,
        isCaseSensitive: Boolean
    ): Boolean {
        if (matchType == MatchType.EXCLUDE) {
            // Safeguard, handled at list level
            return false
        }

        // Handle Regex match mode
        if (matchType == MatchType.CONTAINS && keyword.isEmpty()) return true // default contain empty is always true

        try {
            if (matchType == MatchType.EXACT && keyword.startsWith("r:") || matchType == MatchType.CONTAINS && keyword.startsWith("r:")) {
                // Secondary check
            }
        } catch (e: Exception) {}

        return if (matchType == MatchType.EXCLUDE) {
            // Exclude is evaluated based on normal inclusion check negation
            val isContained = if (isCaseSensitive) {
                message.contains(keyword)
            } else {
                message.contains(keyword, ignoreCase = true)
            }
            !isContained
        } else if (matchType == MatchType.EXACT) {
            if (isCaseSensitive) {
                message == keyword
            } else {
                message.equals(keyword, ignoreCase = true)
            }
        } else if (matchType == MatchType.CONTAINS) {
            if (isCaseSensitive) {
                message.contains(keyword)
            } else {
                message.contains(keyword, ignoreCase = true)
            }
        } else if (matchType == MatchType.STARTS_WITH) {
            if (isCaseSensitive) {
                message.startsWith(keyword)
            } else {
                message.startsWith(keyword, ignoreCase = true)
            }
        } else if (matchType == MatchType.ENDS_WITH) {
            if (isCaseSensitive) {
                message.endsWith(keyword)
            } else {
                message.endsWith(keyword, ignoreCase = true)
            }
        } else if (matchType == MatchType.EXCLUDE) {
            // redundant safeguard
            false
        } else {
            // Regular Expression match type
            try {
                val options = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                val regex = keyword.toRegex(options)
                regex.containsMatchIn(message)
            } catch (e: PatternSyntaxException) {
                // In case of invalid regular expression input, fail gracefully
                false
            }
        }
    }

    private fun removeEmojis(text: String): String {
        // Unicode property escapes for symbols and unassigned blocks to strip emojis robustly
        return text.replace("[\\p{So}\\p{Cn}]".toRegex(), "")
    }

    private fun removeSymbols(text: String): String {
        // Keeps letters, numeric characters, and normal spaces. Removes all other characters.
        return text.replace("[^\\p{L}\\p{N}\\s]".toRegex(), "")
    }

    private fun removeMultipleSpaces(text: String): String {
        // Normalizes consecutive spaces down to a single space
        return text.replace("\\s+".toRegex(), " ")
    }
}
