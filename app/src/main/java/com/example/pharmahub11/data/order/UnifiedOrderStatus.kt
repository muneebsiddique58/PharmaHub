package com.example.pharmahub11.data.order



import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class UnifiedOrderStatus(
    val status: String,
    val displayName: String,
    val isCustomerVisible: Boolean = true,
    val isPharmacistActionable: Boolean = true,
    val isFinalState: Boolean = false
) {
    ORDERED("ORDERED", "Ordered", true, true),
    CONFIRMED("CONFIRMED", "Confirmed", true, true),
    PROCESSING("PROCESSING", "Processing", false, true), // Hidden from customers
    READY_FOR_DELIVERY("READY_FOR_DELIVERY", "Ready for Delivery", true, true),
    SHIPPED("SHIPPED", "Shipped", true, true),
    DELIVERED("DELIVERED", "Delivered", true, false, true),
    CANCELLED("CANCELLED", "Cancelled", true, false, true),
    PENDING("PENDING", "Pending", true, true);

    companion object {
        fun fromString(value: String): UnifiedOrderStatus {
            return values().firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                        it.status.equals(value, ignoreCase = true)
            } ?: PENDING
        }

        fun getCustomerVisibleStatuses(): List<UnifiedOrderStatus> {
            return values().filter { it.isCustomerVisible }
        }

        fun getNextValidStatuses(currentStatus: UnifiedOrderStatus): List<UnifiedOrderStatus> {
            return when (currentStatus) {
                ORDERED -> listOf(CONFIRMED, PROCESSING, CANCELLED)
                CONFIRMED -> listOf(PROCESSING, READY_FOR_DELIVERY, CANCELLED)
                PROCESSING -> listOf(READY_FOR_DELIVERY, SHIPPED, CANCELLED)
                READY_FOR_DELIVERY -> listOf(SHIPPED, CANCELLED)
                SHIPPED -> listOf(DELIVERED)
                else -> emptyList() // Final states
            }
        }
    }
}