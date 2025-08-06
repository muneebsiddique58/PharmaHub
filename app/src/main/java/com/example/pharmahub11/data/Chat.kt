package com.example.pharmahub11.data

import java.util.Date

data class Chat(
    val chatId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerProfileImage: String = "",
    val adminId: String = "ADMIN_ID", // Fixed admin ID
    val pharmacistId: String = "", // Only for order chats
    val orderId: String = "", // Only for order chats
    val lastMessage: String = "",
    val lastMessageTime: Date = Date(),
    val unreadCount: Int = 0,
    val chatType: String = "" // "app_support" or "order_support"
)