package com.example.model

enum class MessageType(val displayName: String) {
    PLAIN_TEXT("Plain Text"),
    VOICE_MESSAGE("Voice Message"),
    IMAGE("Image"),
    STICKER("Sticker"),
    GIF("GIF"),
    VIDEO("Video"),
    FILE("File"),
    CONTACT("Contact"),
    LOCATION("Location"),
    LINK("Link"),
    EMAIL("Email"),
    PHONE_NUMBER("Phone Number"),
    EMOJI_ONLY("Emoji Only"),
    EMPTY("Empty Message"),
    UNSUPPORTED("Unsupported Message")
}
