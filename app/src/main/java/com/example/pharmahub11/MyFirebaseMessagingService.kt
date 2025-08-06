package com.example.pharmahub11

import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Refreshed token: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Handle data payload
        remoteMessage.data.let { data ->
            Log.d("FCM_DATA", "Message data payload: $data")
            when (data["type"]) {
                "login" -> {
                    val userName = data["userName"] ?: "User"
                    NotificationHelper(this).showLoginNotification(userName)
                }
                "product" -> {
                    val productName = data["productName"] ?: "Product"
                    val message = data["message"] ?: "New update available"
                    NotificationHelper(this).showProductNotification(productName, message)
                }
                else -> {
                    // Default notification handling
                    remoteMessage.notification?.let { notification ->
                        sendNotification(notification)
                    }
                }
            }
        }
    }

    private fun sendTokenToServer(token: String) {
        // Implement your server token update logic here
        // Example: Firebase Firestore or your backend API
    }

    private fun sendNotification(notification: RemoteMessage.Notification) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // ... build and show notification ...
    }
}