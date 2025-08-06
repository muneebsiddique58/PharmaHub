package com.example.pharmahub11

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pharmahub11.R
import com.example.pharmahub11.activities.ShoppingActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "pharmahub_notifications"
        const val CHANNEL_ID_HIGH = "pharmahub_important"
        const val LOGIN_NOTIFICATION_ID = 1001
        const val PRODUCT_NOTIFICATION_ID = 1002
        const val PROMOTION_NOTIFICATION_ID = 1003
        const val ORDER_NOTIFICATION_ID = 1004
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Regular notifications channel
            val regularChannel = NotificationChannel(
                CHANNEL_ID,
                "PharmaHub Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "App notifications for orders and products"
                enableLights(true)
                lightColor = Color.BLUE
            }

            // High priority channel
            val highPriorityChannel = NotificationChannel(
                CHANNEL_ID_HIGH,
                "Important Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent order updates and promotions"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 100, 200)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(regularChannel)
            notificationManager.createNotificationChannel(highPriorityChannel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required before Android 13
        }
    }

    fun showLoginNotification(userName: String) {
        if (!hasNotificationPermission()) return

        val intent = createBaseIntent().apply {
            putExtra("notification_type", "login")
        }

        val largeIcon = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_notification
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle("Welcome to PharmaHub!")
            .setContentText("Hello $userName, you've successfully logged in")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createPendingIntent(intent))
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.primaryColor))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Hello $userName, you've successfully logged in"))

        safeNotify(LOGIN_NOTIFICATION_ID, builder)
    }

    fun showProductNotification(productName: String, message: String, isPromotion: Boolean = false) {
        if (!hasNotificationPermission()) return

        val intent = createBaseIntent().apply {
            putExtra("notification_type", if (isPromotion) "promotion" else "product")
            putExtra("product_name", productName)
        }

        val builder = NotificationCompat.Builder(
            context,
            if (isPromotion) CHANNEL_ID_HIGH else CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$productName Update")
            .setContentText(message)
            .setPriority(if (isPromotion) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createPendingIntent(intent))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .apply {
                if (isPromotion) {
                    setCategory(NotificationCompat.CATEGORY_PROMO)
                    setTimeoutAfter(3600000) // Auto-cancel after 1 hour
                }
            }

        safeNotify(
            if (isPromotion) PROMOTION_NOTIFICATION_ID else PRODUCT_NOTIFICATION_ID,
            builder
        )
    }

    fun showOrderNotification(status: String, orderId: String) {
        if (!hasNotificationPermission()) return

        val intent = createBaseIntent().apply {
            putExtra("notification_type", "order")
            putExtra("order_id", orderId)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Order Update")
            .setContentText("Your order status: $status")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createPendingIntent(intent))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your order status: $status"))

        safeNotify(ORDER_NOTIFICATION_ID, builder)
    }

    private fun createBaseIntent(): Intent {
        return Intent(context, ShoppingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    private fun createPendingIntent(intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun safeNotify(id: Int, builder: NotificationCompat.Builder) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)

            // Explicit permission check right before notify()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return  // Exit if permission not granted
                }
            }

            notificationManager.notify(id, builder.build())
        } catch (e: SecurityException) {
            // Handle cases where permission was revoked between checks
            Log.e("NotificationHelper", "Notification permission denied", e)
        }
    }
}