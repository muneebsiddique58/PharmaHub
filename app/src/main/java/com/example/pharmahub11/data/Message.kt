package com.example.pharmahub11.data

import java.util.Date

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Date = Date(),
    val isRead: Boolean = false
)