package com.example.pharmahub11.data.order

import java.util.Date

data class StatusUpdate(
    val status: OrderStatus,
    val timestamp: Date = Date(),
    val updatedBy: String, // userId or pharmacistId
    val notes: String? = null
)