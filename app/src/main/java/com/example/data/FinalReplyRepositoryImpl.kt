package com.example.data

import com.example.model.AutoReplyRule
import com.example.reply.FinalReplyResult
import com.example.reply.FinalReplyStatus
import com.example.repository.FinalReplyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Concrete implementation of FinalReplyRepository.
 * Strictly loads the stored reply text from the selected rule,
 * replaces dynamic variables ({customer_name}, {date}, {time}),
 * leaves missing variables empty, and preserves original text, Bangla, English, Unicode, and emojis intact.
 */
class FinalReplyRepositoryImpl : FinalReplyRepository {

    override suspend fun generateFinalReply(
        selectedRule: AutoReplyRule,
        customerName: String?,
        currentTimeMillis: Long
    ): FinalReplyResult {
        val rawReply = selectedRule.replyText

        if (rawReply.isBlank()) {
            return FinalReplyResult(
                status = FinalReplyStatus.EMPTY_REPLY,
                selectedRuleId = selectedRule.id,
                selectedRuleName = selectedRule.name,
                rawReplyText = rawReply,
                expandedReplyText = "",
                characterCount = 0,
                customerName = customerName,
                date = formatDate(currentTimeMillis),
                time = formatTime(currentTimeMillis),
                details = "Selected rule has an empty reply text."
            )
        }

        val dateStr = formatDate(currentTimeMillis)
        val timeStr = formatTime(currentTimeMillis)
        val nameReplacement = customerName?.trim()?.takeIf { it.isNotEmpty() } ?: ""

        // Expand variables while preserving exact raw text, Unicode, multi-line formatting, and Emojis
        val expandedReply = rawReply
            .replace(Regex("\\{(?i)customer_name\\}|\\{(?i)name\\}|\\[(?i)customer_name\\]|\\[(?i)name\\]|\\[(?i)sender\\]"), nameReplacement)
            .replace(Regex("\\{(?i)date\\}|\\[(?i)date\\]"), dateStr)
            .replace(Regex("\\{(?i)time\\}|\\[(?i)time\\]"), timeStr)

        val finalCharCount = expandedReply.length

        return FinalReplyResult(
            status = FinalReplyStatus.SUCCESS,
            selectedRuleId = selectedRule.id,
            selectedRuleName = selectedRule.name,
            rawReplyText = rawReply,
            expandedReplyText = expandedReply,
            characterCount = finalCharCount,
            customerName = customerName,
            date = dateStr,
            time = timeStr,
            details = "Successfully generated final reply from selected rule '${selectedRule.name}'."
        )
    }

    private fun formatDate(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timestampMillis))
    }

    private fun formatTime(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date(timestampMillis))
    }
}
