package com.example.pharmahub11.data.order

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.pharmahub11.R

enum class OrderStatus(
    val status: String, // For customer app
    val displayName: String, // For pharmacist app
    @StringRes val stringRes: Int = 0, // For customer app
    @DrawableRes val iconRes: Int, // Common
    @ColorRes val colorRes: Int = 0, // For customer app
    @DrawableRes val backgroundRes: Int = 0, // For customer app
    val isCustomerVisible: Boolean = true, // For customer app
    val isPharmacistActionable: Boolean = true // For customer app
) {

    ORDERED(
        status = "Ordered",
        displayName = "Ordered",
        stringRes = R.string.order_status_ordered,
        iconRes = R.drawable.ic_ordered, // Shared icon
        colorRes = R.color.status_ordered,
        backgroundRes = R.drawable.bg_status_ordered,
        isCustomerVisible = true,
        isPharmacistActionable = true
    ),
    CONFIRMED(
        status = "Confirmed",
        displayName = "Confirmed",
        stringRes = R.string.order_status_confirmed,
        iconRes = R.drawable.ic_confirmed, // Shared icon
        colorRes = R.color.status_confirmed,
        backgroundRes = R.drawable.bg_status_approved,
        isCustomerVisible = true,
        isPharmacistActionable = true
    ),
    PROCESSING(
        status = "Processing",
        displayName = "Processing",
        stringRes = R.string.order_status_processing,
        iconRes = R.drawable.ic_processing, // Shared icon
        colorRes = R.color.status_processing,
        backgroundRes = R.drawable.bg_status_waiting,
        isCustomerVisible = false, // Not shown to customers
        isPharmacistActionable = true
    ),
    READY_FOR_DELIVERY(
        status = "Ready for Delivery",
        displayName = "Ready for Delivery",
        stringRes = R.string.order_status_ready_for_delivery, // Add this string resource
        iconRes = R.drawable.bg_status_readys, // Shared icon
        colorRes = R.color.status_ready,
        backgroundRes = R.drawable.bg_status_ready,
        isCustomerVisible = false, // Pharmacist-only status
        isPharmacistActionable = true
    ),
    SHIPPED(
        status = "Shipped",
        displayName = "Shipped",
        stringRes = R.string.order_status_shipped,
        iconRes = R.drawable.ic_shipped, // Shared icon
        colorRes = R.color.status_shipped,
        backgroundRes = R.drawable.bg_status_prepared,
        isCustomerVisible = true,
        isPharmacistActionable = true
    ),
    DELIVERED(
        status = "Delivered",
        displayName = "Delivered",
        stringRes = R.string.order_status_delivered,
        iconRes = R.drawable.ic_delivered, // Shared icon
        colorRes = R.color.status_delivered,
        backgroundRes = R.drawable.bg_status_delivered,
        isCustomerVisible = true,
        isPharmacistActionable = false // Final state
    ),
    CANCELLED(
        status = "Cancelled",
        displayName = "Cancelled",
        stringRes = R.string.order_status_cancelled,
        iconRes = R.drawable.ic_cancelled, // Shared icon
        colorRes = R.color.status_cancelled,
        backgroundRes = R.drawable.bg_status_rejected,
        isCustomerVisible = true,
        isPharmacistActionable = false // Final state
    ),
    PAID(
        status = "Paid",
        displayName = "Paid",
        stringRes = R.string.order_status_cancelled,
        iconRes = R.drawable.ic_paid, // Shared icon
        colorRes = R.color.status_paid,
        backgroundRes = R.drawable.bg_status_paid,
        isCustomerVisible = true,
        isPharmacistActionable = false // Final state
    );



    companion object {
        fun getCustomerVisibleStatuses(): List<OrderStatus> {
            return values().filter { it.isCustomerVisible }
        }

        fun getPharmacistActionableStatuses(currentStatus: OrderStatus): List<OrderStatus> {
            return when (currentStatus) {
                ORDERED -> listOf(CONFIRMED, PROCESSING, CANCELLED)
                CONFIRMED -> listOf(PROCESSING, READY_FOR_DELIVERY, CANCELLED)
                PROCESSING -> listOf(READY_FOR_DELIVERY, CANCELLED)
                READY_FOR_DELIVERY -> listOf(SHIPPED, CANCELLED)
                SHIPPED -> listOf(DELIVERED)
                else -> emptyList() // Final states
            }
        }


    }
}