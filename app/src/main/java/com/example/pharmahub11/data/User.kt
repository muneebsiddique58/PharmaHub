package com.example.pharmahub11.data

import com.google.firebase.firestore.Exclude

data class User(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val imagePath: String = "",
    @Exclude val id: String = ""  // Useful for client-side operations
) {
    // Calculated property for full name
    val fullName: String
        @Exclude get() = "$firstName $lastName".trim()

    // For Firestore serialization
    fun toMap(): Map<String, Any?> = mapOf(
        "firstName" to firstName,
        "lastName" to lastName,
        "email" to email,
        "imagePath" to imagePath
    )

    companion object {
        fun fromMap(map: Map<String, Any?>, id: String = ""): User = User(
            firstName = map["firstName"] as? String ?: "",
            lastName = map["lastName"] as? String ?: "",
            email = map["email"] as? String ?: "",
            imagePath = map["imagePath"] as? String ?: "",
            id = id
        )
    }
}