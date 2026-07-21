package com.example.data

import com.example.model.AnalyzedMessage
import com.example.model.MessageType
import com.example.repository.MessageAnalyzerRepository
import java.util.regex.Pattern

class MessageAnalyzerRepositoryImpl : MessageAnalyzerRepository {

    // Regex for standard email identification
    private val emailPattern = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
        "\\@" +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(" +
        "\\." +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
        ")+"
    )

    // Regex for standard phone number identification
    private val phonePattern = Pattern.compile(
        "(\\+?\\d{1,4}[\\s-]?)?\\(?\\d{2,4}\\)?[\\s-]?\\d{3,4}[\\s-]?\\d{4,6}"
    )

    // Regex for web URL/link identification
    private val urlPattern = Pattern.compile(
        "(https?:\\/\\/)?(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//,=]*)"
    )

    override fun analyze(message: String): AnalyzedMessage {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return AnalyzedMessage(
                originalText = message,
                normalizedText = "",
                messageType = MessageType.EMPTY
            )
        }

        // Normalize text: remove duplicate spaces, convert to lowercase
        val normalized = trimmed
            .replace("\\s+".toRegex(), " ")
            .lowercase()

        // 1. Detect Unsupported Message
        if (isUnsupported(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.UNSUPPORTED)
        }

        // 2. Detect Emoji Only
        if (isEmojiOnly(trimmed)) {
            return AnalyzedMessage(message, normalized, MessageType.EMOJI_ONLY)
        }

        // 3. Detect Location (Checked before other types because location maps URLs are a specific subset)
        if (isLocation(normalized, trimmed)) {
            val extracted = extractLocationValue(trimmed)
            return AnalyzedMessage(message, normalized, MessageType.LOCATION, extracted)
        }

        // 4. Detect Email
        if (isEmail(trimmed)) {
            val extracted = extractEmail(trimmed)
            return AnalyzedMessage(message, normalized, MessageType.EMAIL, extracted)
        }

        // 5. Detect Phone Number
        if (isPhoneNumber(trimmed)) {
            val extracted = extractPhoneNumber(trimmed)
            return AnalyzedMessage(message, normalized, MessageType.PHONE_NUMBER, extracted)
        }

        // 6. Detect Media types and structural content
        if (isVoiceMessage(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.VOICE_MESSAGE)
        }

        if (isImage(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.IMAGE)
        }

        if (isGif(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.GIF)
        }

        if (isVideo(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.VIDEO)
        }

        if (isSticker(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.STICKER)
        }

        if (isFile(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.FILE)
        }

        if (isContact(normalized)) {
            return AnalyzedMessage(message, normalized, MessageType.CONTACT)
        }

        // 7. Detect General Link (Evaluated after specific media patterns to avoid false matches on local file extension texts)
        if (isLink(trimmed)) {
            val extracted = extractLink(trimmed)
            return AnalyzedMessage(message, normalized, MessageType.LINK, extracted)
        }

        // Fallback to PLAIN_TEXT
        return AnalyzedMessage(message, normalized, MessageType.PLAIN_TEXT)
    }

    override fun isSupported(messageType: MessageType): Boolean {
        // "Ignore unsupported messages" -> unsupported and empty messages are not supported/ignored.
        return messageType != MessageType.UNSUPPORTED && messageType != MessageType.EMPTY
    }

    private fun isUnsupported(text: String): Boolean {
        return text.contains("unsupported message") ||
               text.contains("unsupported format") ||
               text.contains("decryption failed") ||
               text.contains("cannot view message") ||
               text.contains("not supported") ||
               text.contains("unsupported media") ||
               text.contains("message format not supported")
    }

    private fun isEmojiOnly(text: String): Boolean {
        val clean = text.replace("\\s".toRegex(), "")
        if (clean.isEmpty()) return false
        var i = 0
        while (i < clean.length) {
            val codePoint = clean.codePointAt(i)
            val type = Character.getType(codePoint)
            val isEmoji = type == Character.SURROGATE.toInt() || 
                           type == Character.OTHER_SYMBOL.toInt() || 
                           (codePoint in 0x1F300..0x1F9FF) || 
                           (codePoint in 0x2600..0x26FF) || 
                           (codePoint in 0x2700..0x27BF) || 
                           (codePoint in 0x1F000..0x1F02F) ||
                           (codePoint in 0x1F0A0..0x1F0FF) ||
                           (codePoint in 0x1F100..0x1F1FF) ||
                           (codePoint in 0x1F200..0x1F2FF) ||
                           (codePoint in 0x1F300..0x1F5FF) ||
                           (codePoint in 0x1F600..0x1F64F) ||
                           (codePoint in 0x1F680..0x1F6FF) ||
                           (codePoint in 0x1F700..0x1F77F) ||
                           (codePoint in 0x1F780..0x1F7FF) ||
                           (codePoint in 0x1F800..0x1F8FF) ||
                           (codePoint in 0x1F900..0x1F9FF) ||
                           (codePoint in 0x1FA00..0x1FA6F) ||
                           (codePoint in 0x1FA70..0x1FAFF)
            
            if (!isEmoji) {
                return false
            }
            i += Character.charCount(codePoint)
        }
        return true
    }

    private fun isLocation(normalized: String, original: String): Boolean {
        val coordPattern = "^-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?$".toRegex()
        return normalized.contains("📍") ||
               normalized.contains("maps.google.com") ||
               normalized.contains("maps.apple.com") ||
               normalized.contains("google.com/maps") ||
               normalized.contains("goo.gl/maps") ||
               normalized.contains("shared location") ||
               normalized.contains("current location") ||
               normalized.contains("location pin") ||
               normalized.contains("latitude:") ||
               normalized.contains("longitude:") ||
               coordPattern.containsMatchIn(original.trim())
    }

    private fun extractLocationValue(original: String): String? {
        val urlMatcher = urlPattern.matcher(original)
        if (urlMatcher.find()) {
            return urlMatcher.group()
        }
        val coordPattern = "-?\\d+(\\.\\d+)?,\\s*-?\\d+(\\.\\d+)?".toRegex()
        val match = coordPattern.find(original)
        if (match != null) {
            return match.value
        }
        return null
    }

    private fun isLink(text: String): Boolean {
        return urlPattern.matcher(text).find()
    }

    private fun extractLink(text: String): String? {
        val matcher = urlPattern.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }

    private fun isEmail(text: String): Boolean {
        return emailPattern.matcher(text).find()
    }

    private fun extractEmail(text: String): String? {
        val matcher = emailPattern.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }

    private fun isPhoneNumber(text: String): Boolean {
        return phonePattern.matcher(text).find()
    }

    private fun extractPhoneNumber(text: String): String? {
        val matcher = phonePattern.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }

    private fun isVoiceMessage(text: String): Boolean {
        return text.contains("🎤") ||
               text.contains("voice message") ||
               text.contains("voice note") ||
               text.contains("audio message") ||
               text.contains("sent an audio") ||
               text.contains("[voice message]") ||
               text.contains("[audio]")
    }

    private fun isImage(text: String): Boolean {
        return text.contains("📷") ||
               text.contains("🖼️") ||
               text.contains("photo") ||
               text.contains("[image]") ||
               text.contains("sent a photo") ||
               text.contains(".jpg") ||
               text.contains(".jpeg") ||
               text.contains(".png") ||
               text.contains("picture")
    }

    private fun isGif(text: String): Boolean {
        return text.contains("gif") ||
               text.contains("animated gif") ||
               text.contains("sent a gif") ||
               text.contains("[gif]") ||
               text.contains(".gif")
    }

    private fun isVideo(text: String): Boolean {
        return text.contains("🎥") ||
               text.contains("📹") ||
               text.contains("video") ||
               text.contains("sent a video") ||
               text.contains("[video]") ||
               text.contains(".mp4") ||
               text.contains(".mov") ||
               text.contains(".avi") ||
               text.contains(".mkv")
    }

    private fun isSticker(text: String): Boolean {
        return text.contains("sticker") ||
               text.contains("sent a sticker") ||
               text.contains("[sticker]")
    }

    private fun isFile(text: String): Boolean {
        return text.contains("📄") ||
               text.contains("📁") ||
               text.contains("document") ||
               text.contains("file") ||
               text.contains("sent a file") ||
               text.contains("[file]") ||
               text.contains(".pdf") ||
               text.contains(".docx") ||
               text.contains(".xlsx") ||
               text.contains(".zip") ||
               text.contains(".txt")
    }

    private fun isContact(text: String): Boolean {
        return text.contains("👤") ||
               text.contains("contact card") ||
               text.contains("shared a contact") ||
               text.contains("vcard") ||
               text.contains("[contact]")
    }
}
