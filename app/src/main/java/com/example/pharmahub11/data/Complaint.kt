package com.example.pharmahub11.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

data class Complaint(
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val text: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val status: String = "pending",
    val type: String = "customer_service",
    @Exclude val id: String = ""  // Useful for client-side operations
) {
    // For Firestore serialization
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "userEmail" to userEmail,
        "userName" to userName,
        "text" to text,
        "timestamp" to timestamp,
        "status" to status,
        "type" to type
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, id: String = ""): Complaint = Complaint(
            userId = map["userId"] as? String ?: "",
            userEmail = map["userEmail"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            text = map["text"] as? String ?: "",
            timestamp = map["timestamp"] as? Timestamp,
            status = map["status"] as? String ?: "pending",
            type = map["type"] as? String ?: "customer_service",
            id = id
        )
    }
}