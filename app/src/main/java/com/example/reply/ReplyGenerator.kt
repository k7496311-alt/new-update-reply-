package com.example.reply

import com.example.model.AutoReplyRule
import java.util.Calendar
import kotlin.random.Random

class ReplyGenerator(
    private val repository: ReplyGeneratorRepository
) {

    /**
     * Core function to generate a final reply from a matched rule or fallback setting.
     * Checks cooldowns, maximum daily/global limits, parses formatting, rotations, and time ranges.
     */
    suspend fun generateReply(
        rule: AutoReplyRule?,
        senderName: String,
        incomingMessage: String
    ): FinalReply {
        // If no rule matched, look for default reply configuration
        if (rule == null) {
            val defaultSetting = repository.getDefaultReplySetting()
            return if (defaultSetting != null) {
                val processedDefault = replacePlaceholders(defaultSetting, senderName)
                FinalReply(
                    replyText = processedDefault,
                    isTriggered = true,
                    status = ReplyGenerationStatus.DEFAULT,
                    reason = "No matched rule. Fallback to default setting."
                )
            } else {
                FinalReply(
                    replyText = "",
                    isTriggered = false,
                    status = ReplyGenerationStatus.NO_MATCH,
                    reason = "No matched rule and no default reply setting configured."
                )
            }
        }

        // 1. Cooldown Check
        if (rule.cooldownMillis > 0) {
            val lastReplyTimestamp = repository.getLastReplyTimestamp(rule.id)
            if (lastReplyTimestamp != null) {
                val elapsed = System.currentTimeMillis() - lastReplyTimestamp
                if (elapsed < rule.cooldownMillis) {
                    val remaining = rule.cooldownMillis - elapsed
                    return FinalReply(
                        replyText = "",
                        isTriggered = false,
                        status = ReplyGenerationStatus.COOLDOWN,
                        ruleId = rule.id,
                        reason = "Rule in cooldown. $remaining ms remaining."
                    )
                }
            }
        }

        // 2. Global / Max Reply Limit Check
        val globalLimit = maxOf(rule.globalLimit, rule.maxReplies)
        if (globalLimit > 0) {
            val count = repository.getGlobalReplyCount(rule.id)
            if (count >= globalLimit) {
                return FinalReply(
                    replyText = "",
                    isTriggered = false,
                    status = ReplyGenerationStatus.LIMIT_EXCEEDED,
                    ruleId = rule.id,
                    reason = "Global reply limit ($globalLimit) reached."
                )
            }
        }

        // 3. Daily Limit Check
        if (rule.dailyLimit > 0) {
            val startOfDay = getStartOfDayTimestamp()
            val dailyCount = repository.getReplyCountSince(rule.id, startOfDay)
            if (dailyCount >= rule.dailyLimit) {
                return FinalReply(
                    replyText = "",
                    isTriggered = false,
                    status = ReplyGenerationStatus.LIMIT_EXCEEDED,
                    ruleId = rule.id,
                    reason = "Daily reply limit (${rule.dailyLimit}) reached."
                )
            }
        }

        // 4. Determine and process replyText based on rotation modes
        val processedText = processReplyText(rule)

        // 5. Replace greetings and placeholders
        val finalMessage = replacePlaceholders(processedText, senderName)

        return FinalReply(
            replyText = finalMessage,
            isTriggered = true,
            status = ReplyGenerationStatus.SUCCESS,
            delayMillis = rule.replyDelayMillis,
            ruleId = rule.id,
            reason = "Successfully generated reply."
        )
    }

    /**
     * Resolves rotation, random, time-based, sequential tags from the rule's replyText.
     */
    private suspend fun processReplyText(rule: AutoReplyRule): String {
        val rawText = rule.replyText.trim()
        if (rawText.isEmpty()) return ""

        // Detect sequential mode
        if (rawText.startsWith("[sequential]", ignoreCase = true) || rawText.startsWith("[seq]", ignoreCase = true)) {
            val cleanText = removeTagPrefix(rawText)
            val options = splitOptions(cleanText)
            if (options.isEmpty()) return ""
            val idx = repository.getSequentialIndex(rule.id)
            val validIdx = if (idx in options.indices) idx else 0
            val nextIdx = (validIdx + 1) % options.size
            repository.saveSequentialIndex(rule.id, nextIdx)
            return options[validIdx]
        }

        // Detect daily rotation mode
        if (rawText.startsWith("[daily]", ignoreCase = true) || rawText.startsWith("[rotation]", ignoreCase = true)) {
            val cleanText = removeTagPrefix(rawText)
            val options = splitOptions(cleanText)
            if (options.isEmpty()) return ""
            val epochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
            val idx = (epochDay % options.size).toInt()
            return options[idx]
        }

        // Detect explicit random tag
        if (rawText.startsWith("[random]", ignoreCase = true)) {
            val cleanText = removeTagPrefix(rawText)
            val options = splitOptions(cleanText)
            if (options.isEmpty()) return ""
            return options.random()
        }

        // Support Time-Based and standard multiple/random replies
        val options = splitOptions(rawText)
        if (options.size > 1) {
            val parsedOptions = options.map { parseOption(it) }

            // If we have time-based tags, evaluate them
            val hasTimeTags = parsedOptions.any { it.tag != null }
            if (hasTimeTags) {
                for (po in parsedOptions) {
                    if (po.tag != null && evaluateTimeTag(po.tag)) {
                        return po.cleanText
                    }
                }
                // Fallback: use first option that has NO tag, or first overall
                val fallbackOption = parsedOptions.firstOrNull { it.tag == null } ?: parsedOptions.first()
                return fallbackOption.cleanText
            }

            // Default behavior for multiple replies without specific tags: Random Reply
            return options.random()
        }

        return rawText
    }

    private fun removeTagPrefix(text: String): String {
        val index = text.indexOf(']')
        return if (index != -1 && index + 1 < text.length) {
            text.substring(index + 1).trim()
        } else {
            text
        }
    }

    private fun splitOptions(text: String): List<String> {
        return text.split(Regex("[|\\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    data class TaggedOption(val tag: String?, val cleanText: String)

    private fun parseOption(option: String): TaggedOption {
        val trimmed = option.trim()
        val matchResult = Regex("^\\[([^\\]]+)\\](.*)", RegexOption.DOT_MATCHES_ALL).matchEntire(trimmed)
        return if (matchResult != null) {
            val tag = matchResult.groupValues[1].trim().lowercase()
            val text = matchResult.groupValues[2].trim()
            TaggedOption(tag, text)
        } else {
            TaggedOption(null, trimmed)
        }
    }

    /**
     * Evaluates whether current local time matches a specific tag (e.g., "morning", "09:00-17:00").
     */
    private fun evaluateTimeTag(tag: String): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        when (tag) {
            "morning" -> return hour in 6..11
            "afternoon" -> return hour in 12..16
            "evening" -> return hour in 17..21
            "night" -> return hour >= 22 || hour <= 5
        }

        // Try HH:mm-HH:mm parse
        val regex = Regex("^(\\d{2}):(\\d{2})\\s*-\\s*(\\d{2}):(\\d{2})$")
        val match = regex.matchEntire(tag)
        if (match != null) {
            val startH = match.groupValues[1].toInt()
            val startM = match.groupValues[2].toInt()
            val endH = match.groupValues[3].toInt()
            val endM = match.groupValues[4].toInt()

            val curMin = hour * 60 + cal.get(Calendar.MINUTE)
            val startMin = startH * 60 + startM
            val endMin = endH * 60 + endM

            return if (startMin <= endMin) {
                curMin in startMin..endMin
            } else {
                curMin >= startMin || curMin <= endMin
            }
        }

        return false
    }

    private fun replacePlaceholders(text: String, senderName: String): String {
        val nameVal = if (senderName.trim().isEmpty()) "there" else senderName.trim()

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val greetingVal = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }

        val timeVal = String.format("%02d:%02d", hour, cal.get(Calendar.MINUTE))
        val dateVal = String.format("%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        return text
            .replace("[Name]", nameVal, ignoreCase = true)
            .replace("[Sender]", nameVal, ignoreCase = true)
            .replace("[Contact]", nameVal, ignoreCase = true)
            .replace("[Greeting]", greetingVal, ignoreCase = true)
            .replace("[Time]", timeVal, ignoreCase = true)
            .replace("[Date]", dateVal, ignoreCase = true)
    }

    private fun getStartOfDayTimestamp(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
