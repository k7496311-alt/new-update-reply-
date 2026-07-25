package com.example.accessibility.imo

import com.example.model.MessageType
import java.util.regex.Pattern

/**
 * Categorizes and classifies IMO messages, notifications, and media elements.
 * Filters out stickers, images, videos, links, and missed audio/video calls.
 */
object IMOMessageClassifier {

    private val URL_PATTERN = Pattern.compile("(?i).*\\b(https?://|www\\.)\\S+.*")
    private val DURATION_PATTERN = Pattern.compile("^\\d+:\\d{2}$")

    /**
     * Determines whether an incoming message string or UI element is media, link, or call
     * that should be SKIPPED automatically.
     */
    fun isMediaOrCallOrLink(text: String, detectedType: MessageType = MessageType.PLAIN_TEXT): Boolean {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // 1. Explicit UI message types scanned on screen
        if (detectedType == MessageType.IMAGE ||
            detectedType == MessageType.STICKER ||
            detectedType == MessageType.VIDEO ||
            detectedType == MessageType.GIF ||
            detectedType == MessageType.LINK ||
            detectedType == MessageType.FILE ||
            detectedType == MessageType.CONTACT ||
            detectedType == MessageType.LOCATION) {
            return true
        }

        // 2. Links / URLs
        if (URL_PATTERN.matcher(trimmed).matches() || lower.contains("http://") || lower.contains("https://") || lower.contains("www.")) {
            return true
        }

        // 3. Missed Audio / Video Calls (English & Bengali)
        if (lower.contains("missed call") ||
            lower.contains("missed video call") ||
            lower.contains("missed voice call") ||
            lower.contains("missed audio call") ||
            lower.contains("video call") ||
            lower.contains("voice call") ||
            lower.contains("audio call") ||
            lower.contains("call back") ||
            lower.contains("মিসড কল") ||
            lower.contains("মিসকল") ||
            lower.contains("অডিও কল") ||
            lower.contains("ভিডিও কল") ||
            (lower.contains("কল") && (lower.contains("মিস") || lower.contains("ভিডিও") || lower.contains("অডিও")))) {
            return true
        }

        // 4. Sticker previews
        if (lower == "sticker" || lower == "[sticker]" || lower == "স্টিকার" || lower.contains("sent a sticker") || lower.contains("স্টিকার পাঠিয়েছেন")) {
            return true
        }

        // 5. Image / Photo previews
        if (lower == "photo" || lower == "[photo]" || lower == "image" || lower == "[image]" || lower == "picture" || lower == "ছবি" || lower.contains("sent a photo")) {
            return true
        }

        // 6. Video previews
        if (lower == "video" || lower == "[video]" || lower == "ভিডিও" || lower.contains("sent a video")) {
            return true
        }

        return false
    }

    /**
     * Checks if the text or detected type indicates a voice message bubble or audio notification.
     */
    fun isVoiceMessage(text: String, detectedType: MessageType = MessageType.PLAIN_TEXT): Boolean {
        if (detectedType == MessageType.VOICE_MESSAGE) return true
        val lower = text.trim().lowercase()
        return lower.contains("voice message") ||
               lower.contains("audio message") ||
               lower.contains("ভয়েস") ||
               lower.contains("ভয়েস") ||
               lower.contains("রেকর্ডিং") ||
               DURATION_PATTERN.matcher(text.trim()).matches()
    }
}
