package com.example.reply

import android.app.PendingIntent
import android.content.Context

interface ReplySender {
    suspend fun sendReply(context: Context, replyIntent: PendingIntent, message: String): Boolean
}
