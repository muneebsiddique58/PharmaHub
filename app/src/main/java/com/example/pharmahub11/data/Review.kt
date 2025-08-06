package com.example.pharmahub11.data

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Review(
    val orderId: String = "",
    val productId: String = "",
    val userId: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    val timestamp: Any? = null,
    val userName: String = "", // Optional: to display reviewer name
    val userEmail: String = ""
){
    fun getFormattedDate(): String {
        return when (timestamp) {
            is Timestamp -> formatDate(timestamp.toDate())
            is Date -> formatDate(timestamp)
            else -> "N/A"
        }
    }

    private fun formatDate(date: Date): String {
        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    }
}