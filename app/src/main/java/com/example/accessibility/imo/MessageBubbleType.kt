package com.example.accessibility.imo

/**
 * Deterministic types for chat bubble nodes.
 */
enum class MessageBubbleType {
    INCOMING_MESSAGE,
    OUTGOING_MESSAGE,
    STICKER,
    EMOJI,
    IMAGE,
    VIDEO,
    AUDIO,
    VOICE,
    MISSED_AUDIO_CALL,
    MISSED_VIDEO_CALL,
    SYSTEM_MESSAGE,
    DATE_SEPARATOR,
    UNREAD_SEPARATOR,
    UNKNOWN
}
