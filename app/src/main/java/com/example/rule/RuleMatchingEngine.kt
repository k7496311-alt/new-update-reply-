package com.example.rule

import com.example.model.AutoReplyRule
import com.example.model.MatchType
import com.example.model.RuleStatus
import java.util.regex.PatternSyntaxException
import kotlin.random.Random

/**
 * Highly optimized, production-ready Rule Matching Engine.
 * Supports priority, Unicode (Bangla, English, etc.), AND/OR/NOT keyword groups,
 * ignoring punctuation/emojis, fast search algorithm with indexing for thousands of rules,
 * and selecting from multiple replies.
 */
class RuleMatchingEngine {

    // AST Nodes for keyword expression parsing
    sealed class KeywordExpression {
        abstract fun evaluate(evaluator: (String) -> Boolean): Boolean

        data class Literal(val value: String) : KeywordExpression() {
            override fun evaluate(evaluator: (String) -> Boolean): Boolean {
                return evaluator(value)
            }
        }

        data class And(val left: KeywordExpression, val right: KeywordExpression) : KeywordExpression() {
            override fun evaluate(evaluator: (String) -> Boolean): Boolean {
                return left.evaluate(evaluator) && right.evaluate(evaluator)
            }
        }

        data class Or(val left: KeywordExpression, val right: KeywordExpression) : KeywordExpression() {
            override fun evaluate(evaluator: (String) -> Boolean): Boolean {
                return left.evaluate(evaluator) || right.evaluate(evaluator)
            }
        }

        data class Not(val operand: KeywordExpression) : KeywordExpression() {
            override fun evaluate(evaluator: (String) -> Boolean): Boolean {
                return !operand.evaluate(evaluator)
            }
        }
    }

    // TokenType for keyword expression lexer
    private enum class TokenType {
        AND, OR, NOT, LPAREN, RPAREN, LITERAL, EOF
    }

    private data class Token(val type: TokenType, val value: String)

    private class Lexer(private val input: String) {
        private var pos = 0

        fun nextToken(): Token {
            while (pos < input.length) {
                val char = input[pos]
                if (char.isWhitespace()) {
                    pos++
                    continue
                }
                if (char == '(') {
                    pos++
                    return Token(TokenType.LPAREN, "(")
                }
                if (char == ')') {
                    pos++
                    return Token(TokenType.RPAREN, ")")
                }
                if (char == '"' || char == '\'') {
                    val quote = char
                    pos++
                    val start = pos
                    while (pos < input.length && input[pos] != quote) {
                        pos++
                    }
                    val value = input.substring(start, pos)
                    if (pos < input.length) pos++ // skip closing quote
                    return Token(TokenType.LITERAL, value)
                }

                // Check operators
                if (input.startsWith("&&", pos)) {
                    pos += 2
                    return Token(TokenType.AND, "&&")
                }
                if (input.startsWith("||", pos)) {
                    pos += 2
                    return Token(TokenType.OR, "||")
                }
                if (char == '!') {
                    pos++
                    return Token(TokenType.NOT, "!")
                }

                // Word operators: AND, OR, NOT
                if (input.startsWith("AND", pos, ignoreCase = true) && isWordBoundary(pos + 3)) {
                    pos += 3
                    return Token(TokenType.AND, "AND")
                }
                if (input.startsWith("OR", pos, ignoreCase = true) && isWordBoundary(pos + 2)) {
                    pos += 2
                    return Token(TokenType.OR, "OR")
                }
                if (input.startsWith("NOT", pos, ignoreCase = true) && isWordBoundary(pos + 3)) {
                    pos += 3
                    return Token(TokenType.NOT, "NOT")
                }

                // Literal word
                val start = pos
                while (pos < input.length) {
                    val c = input[pos]
                    if (c.isWhitespace() || c == '(' || c == ')' || c == '!' || input.startsWith("&&", pos) || input.startsWith("||", pos)) {
                        break
                    }
                    if (input.startsWith("AND", pos, ignoreCase = true) && isWordBoundary(pos + 3)) break
                    if (input.startsWith("OR", pos, ignoreCase = true) && isWordBoundary(pos + 2)) break
                    if (input.startsWith("NOT", pos, ignoreCase = true) && isWordBoundary(pos + 3)) break
                    pos++
                }
                val value = input.substring(start, pos)
                return Token(TokenType.LITERAL, value)
            }
            return Token(TokenType.EOF, "")
        }

        private fun isWordBoundary(index: Int): Boolean {
            if (index >= input.length) return true
            val c = input[index]
            return c.isWhitespace() || c == '(' || c == ')' || c == '!' || c == '"' || c == '\''
        }
    }

    private class Parser(private val lexer: Lexer) {
        private var currentToken = lexer.nextToken()

        private fun consume(type: TokenType) {
            if (currentToken.type == type) {
                currentToken = lexer.nextToken()
            } else {
                throw IllegalArgumentException("Expected token of type $type, but got ${currentToken.type}")
            }
        }

        fun parse(): KeywordExpression {
            val expr = parseExpression()
            if (currentToken.type != TokenType.EOF) {
                throw IllegalArgumentException("Unexpected token: ${currentToken.value}")
            }
            return expr
        }

        private fun parseExpression(): KeywordExpression {
            var node = parseAndExpression()
            while (currentToken.type == TokenType.OR) {
                consume(TokenType.OR)
                val right = parseAndExpression()
                node = KeywordExpression.Or(node, right)
            }
            return node
        }

        private fun parseAndExpression(): KeywordExpression {
            var node = parseUnaryExpression()
            while (currentToken.type == TokenType.AND) {
                consume(TokenType.AND)
                val right = parseUnaryExpression()
                node = KeywordExpression.And(node, right)
            }
            return node
        }

        private fun parseUnaryExpression(): KeywordExpression {
            if (currentToken.type == TokenType.NOT) {
                consume(TokenType.NOT)
                val operand = parseUnaryExpression()
                return KeywordExpression.Not(operand)
            }
            return parsePrimaryExpression()
        }

        private fun parsePrimaryExpression(): KeywordExpression {
            val token = currentToken
            return when (token.type) {
                TokenType.LPAREN -> {
                    consume(TokenType.LPAREN)
                    val expr = parseExpression()
                    consume(TokenType.RPAREN)
                    expr
                }
                TokenType.LITERAL -> {
                    consume(TokenType.LITERAL)
                    KeywordExpression.Literal(token.value)
                }
                else -> throw IllegalArgumentException("Unexpected token in expression: ${token.value}")
            }
        }
    }

    /**
     * Parses the raw keyword configuration into an evaluable AST.
     * Backwards compatible: splits by comma if there are no logical operators.
     */
    fun parseKeywordExpression(keyword: String): KeywordExpression {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return KeywordExpression.Literal("")

        val hasOperators = trimmed.contains(" AND ", ignoreCase = true) ||
                trimmed.contains(" OR ", ignoreCase = true) ||
                trimmed.contains(" NOT ", ignoreCase = true) ||
                trimmed.contains("&&") ||
                trimmed.contains("||") ||
                trimmed.contains("!") ||
                trimmed.contains("(") ||
                trimmed.contains(")")

        if (!hasOperators) {
            val parts = trimmed.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (parts.isEmpty()) {
                return KeywordExpression.Literal("")
            }

            var expr: KeywordExpression = KeywordExpression.Literal(parts[0])
            for (i in 1 until parts.size) {
                expr = KeywordExpression.Or(expr, KeywordExpression.Literal(parts[i]))
            }
            return expr
        }

        return try {
            val lexer = Lexer(trimmed)
            val parser = Parser(lexer)
            parser.parse()
        } catch (e: Exception) {
            KeywordExpression.Literal(trimmed)
        }
    }

    // Representation of a precompiled rule for high-performance matching
    data class CompiledRule(
        val rule: AutoReplyRule,
        val expression: KeywordExpression,
        val positiveWords: Set<String>,
        val isSimpleExact: Boolean
    )

    private var compiledRulesCache: List<CompiledRule> = emptyList()
    private var exactMatchesIndex: Map<String, List<CompiledRule>> = emptyMap()

    /**
     * Rebuilds the fast-lookup indices from a list of active rules.
     * Call this when rules change to optimize searches for thousands of rules.
     */
    fun updateRulesIndex(rules: List<AutoReplyRule>) {
        val compiled = rules.map { rule ->
            val expr = parseKeywordExpression(rule.keyword)
            val positiveWords = if (rule.matchType == MatchType.EXCLUDE) {
                emptySet()
            } else {
                extractPositiveWords(expr)
            }
            val isSimpleExact = rule.matchType == MatchType.EXACT &&
                    !rule.keyword.contains(" AND ", ignoreCase = true) &&
                    !rule.keyword.contains(" OR ", ignoreCase = true) &&
                    !rule.keyword.contains(" NOT ", ignoreCase = true) &&
                    !rule.keyword.contains("&&") &&
                    !rule.keyword.contains("||") &&
                    !rule.keyword.contains("!") &&
                    !rule.keyword.contains(",")

            CompiledRule(rule, expr, positiveWords, isSimpleExact)
        }

        compiledRulesCache = compiled

        // Build exact match hashmap index for O(1) lookups
        val exactIndex = mutableMapOf<String, MutableList<CompiledRule>>()
        for (cr in compiled) {
            if (cr.isSimpleExact) {
                val key = normalizeWord(cr.rule.keyword, cr.rule)
                exactIndex.getOrPut(key) { mutableListOf() }.add(cr)
            }
        }
        exactMatchesIndex = exactIndex
    }

    private fun extractPositiveWords(expr: KeywordExpression, isNegated: Boolean = false): Set<String> {
        return when (expr) {
            is KeywordExpression.Literal -> {
                if (isNegated || expr.value.isBlank()) emptySet() else setOf(expr.value.lowercase())
            }
            is KeywordExpression.And -> {
                extractPositiveWords(expr.left, isNegated) + extractPositiveWords(expr.right, isNegated)
            }
            is KeywordExpression.Or -> {
                extractPositiveWords(expr.left, isNegated) + extractPositiveWords(expr.right, isNegated)
            }
            is KeywordExpression.Not -> {
                extractPositiveWords(expr.operand, !isNegated)
            }
        }
    }

    /**
     * Finds the best matching rule from indexed rules for high-performance.
     */
    fun findBestMatch(incomingMessage: String): BestMatchedRule? {
        if (compiledRulesCache.isEmpty()) return null

        val trimmedMsg = incomingMessage.trim()
        if (trimmedMsg.isEmpty()) return null

        val matchedCandidates = mutableListOf<BestMatchedRule>()

        // 1. Try absolute O(1) exact matches if applicable
        val normalizedExactKey = normalizeWord(trimmedMsg, null) // generic normalization
        val exactCandidates = exactMatchesIndex[normalizedExactKey]
        if (exactCandidates != null) {
            for (cr in exactCandidates) {
                if (cr.rule.status == RuleStatus.ACTIVE && cr.rule.isEnabled) {
                    val reply = selectReply(cr.rule.replyText)
                    val score = calculateScore(cr.rule, cr.rule.keyword, MatchType.EXACT)
                    matchedCandidates.add(BestMatchedRule(cr.rule, cr.rule.keyword, score, reply))
                }
            }
        }

        // Lazy cache of preprocessed messages for pre-filtering
        val preprocessedMsgCache = mutableMapOf<Pair<Boolean, Boolean>, String>()
        fun getPreprocessedMsg(ignoreEmoji: Boolean, ignorePunctuation: Boolean): String {
            return preprocessedMsgCache.getOrPut(Pair(ignoreEmoji, ignorePunctuation)) {
                preprocessText(
                    text = trimmedMsg,
                    ignoreEmoji = ignoreEmoji,
                    ignorePunctuation = ignorePunctuation,
                    ignoreMultipleSpaces = true,
                    trimSpaces = true,
                    caseSensitive = false
                )
            }
        }

        // 2. Scan remaining compiled rules with pruning/prefiltering
        for (cr in compiledRulesCache) {
            val rule = cr.rule
            if (rule.status != RuleStatus.ACTIVE || !rule.isEnabled) continue
            if (cr.isSimpleExact) continue // already evaluated in exact index step

            // Pruning/Prefiltering step: If positiveWords set is not empty, message must contain at least one of them
            if (cr.positiveWords.isNotEmpty()) {
                val preprocessedMsg = getPreprocessedMsg(rule.shouldIgnoreEmoji, rule.shouldIgnoreSymbols)
                val hasPositiveWord = cr.positiveWords.any { word ->
                    preprocessedMsg.contains(word)
                }
                if (!hasPositiveWord) continue // skip full AST evaluation, massive speedup!
            }

            // Full AST evaluation
            var matchedKeyword = ""
            val isMatch = cr.expression.evaluate { literal ->
                val match = evaluateSingleLiteral(trimmedMsg, literal, rule)
                if (match) {
                    matchedKeyword = literal
                }
                match
            }

            if (isMatch) {
                val reply = selectReply(rule.replyText)
                val score = calculateScore(rule, matchedKeyword, rule.matchType)
                matchedCandidates.add(BestMatchedRule(rule, matchedKeyword, score, reply))
            }
        }

        // Return highest scoring rule (priority first, then specificity / length)
        return matchedCandidates.maxByOrNull { it.score }
    }

    private fun evaluateSingleLiteral(message: String, literalKeyword: String, rule: AutoReplyRule): Boolean {
        val processedMsg = preprocessText(
            text = message,
            ignoreEmoji = rule.shouldIgnoreEmoji,
            ignorePunctuation = rule.shouldIgnoreSymbols, // symbols include punctuation in this context
            ignoreMultipleSpaces = rule.shouldIgnoreMultipleSpaces,
            trimSpaces = rule.shouldTrimSpaces,
            caseSensitive = rule.isCaseSensitive
        )

        val processedKw = preprocessText(
            text = literalKeyword,
            ignoreEmoji = rule.shouldIgnoreEmoji,
            ignorePunctuation = rule.shouldIgnoreSymbols,
            ignoreMultipleSpaces = rule.shouldIgnoreMultipleSpaces,
            trimSpaces = rule.shouldTrimSpaces,
            caseSensitive = rule.isCaseSensitive
        )

        if (processedMsg.isEmpty() && processedKw.isNotEmpty()) return false
        if (processedKw.isEmpty()) return processedMsg.isEmpty()

        return when (rule.matchType) {
            MatchType.EXACT -> processedMsg == processedKw
            MatchType.CONTAINS -> processedMsg.contains(processedKw)
            MatchType.STARTS_WITH -> processedMsg.startsWith(processedKw)
            MatchType.ENDS_WITH -> processedMsg.endsWith(processedKw)
            MatchType.EXCLUDE -> !processedMsg.contains(processedKw)
            MatchType.REGEX -> {
                try {
                    val options = if (rule.isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    processedKw.toRegex(options).containsMatchIn(processedMsg)
                } catch (e: PatternSyntaxException) {
                    false
                }
            }
        }
    }

    /**
     * Calculates specificity/priority match score.
     */
    private fun calculateScore(rule: AutoReplyRule, matchedKeyword: String, matchType: MatchType): Int {
        val matchTypeBonus = when (matchType) {
            MatchType.EXACT -> 50
            MatchType.STARTS_WITH, MatchType.ENDS_WITH -> 30
            MatchType.REGEX -> 20
            MatchType.CONTAINS -> 10
            MatchType.EXCLUDE -> 5
        }
        // Multiply priority by 1000 to guarantee that higher priority is always chosen first
        return (rule.priority * 1000) + matchTypeBonus + matchedKeyword.length
    }

    private fun preprocessText(
        text: String,
        ignoreEmoji: Boolean,
        ignorePunctuation: Boolean,
        ignoreMultipleSpaces: Boolean,
        trimSpaces: Boolean,
        caseSensitive: Boolean
    ): String {
        var result = text

        if (ignoreEmoji) {
            result = removeEmojis(result)
        }

        if (ignorePunctuation) {
            result = removePunctuation(result)
        }

        if (ignoreMultipleSpaces || ignoreEmoji || ignorePunctuation) {
            result = removeMultipleSpaces(result)
        }

        if (trimSpaces) {
            result = result.trim()
        }

        if (!caseSensitive) {
            result = result.lowercase()
        }

        return result
    }

    private fun normalizeWord(word: String, rule: AutoReplyRule?): String {
        val ignoreEmoji = rule?.shouldIgnoreEmoji ?: true
        val ignorePunct = rule?.shouldIgnoreSymbols ?: true
        return preprocessText(
            text = word,
            ignoreEmoji = ignoreEmoji,
            ignorePunctuation = ignorePunct,
            ignoreMultipleSpaces = true,
            trimSpaces = true,
            caseSensitive = false
        )
    }

    private fun removeEmojis(text: String): String {
        return text.replace("[\\p{So}\\p{Cn}]".toRegex(), "")
    }

    private fun removePunctuation(text: String): String {
        return text.replace("[\\p{P}\\p{S}]".toRegex(), "")
    }

    private fun removeMultipleSpaces(text: String): String {
        return text.replace("\\s+".toRegex(), " ")
    }

    /**
     * Handles selecting from multiple replies separated by | or newlines.
     */
    fun selectReply(replyText: String): String {
        if (replyText.isBlank()) return ""
        val parts = replyText.split(Regex("[|\\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parts.isNotEmpty()) parts.random() else replyText
    }
}
