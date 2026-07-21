package com.example.rule

import com.example.model.AutoReplyRule

sealed class ExecutionResult {
    data class Success(
        val ruleId: Long,
        val replyText: String,
        val delayMillis: Long
    ) : ExecutionResult()
    
    data class Error(val reason: String) : ExecutionResult()
}

class RuleExecutor {

    /**
     * Executes the matched and validated rule, preparing the reply message and resolving dynamic placeholders.
     */
    fun execute(rule: AutoReplyRule, senderName: String, incomingMessage: String): ExecutionResult {
        return try {
            if (rule.replyText.isBlank()) {
                return ExecutionResult.Error("Reply template is empty.")
            }
            
            val formattedReply = formatReply(rule.replyText, senderName, incomingMessage)
            ExecutionResult.Success(
                ruleId = rule.id,
                replyText = formattedReply,
                delayMillis = rule.replyDelayMillis
            )
        } catch (e: Exception) {
            ExecutionResult.Error("Failed to format reply: ${e.message}")
        }
    }

    /**
     * Helper to support basic placeholder expansion, e.g. {sender} or {message}.
     */
    private fun formatReply(template: String, senderName: String, messageBody: String): String {
        return template
            .replace("{sender}", senderName, ignoreCase = true)
            .replace("{sender_name}", senderName, ignoreCase = true)
            .replace("{message}", messageBody, ignoreCase = true)
            .replace("{msg}", messageBody, ignoreCase = true)
            .replace("{time}", java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()), ignoreCase = true)
    }
}
