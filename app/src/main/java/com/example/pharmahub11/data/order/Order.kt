package com.example.pharmahub11.data.order

import android.os.Parcelable
import com.example.pharmahub11.data.Address
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random.Default.nextLong

@Parcelize
data class Order(
    val orderStatus: String = "Pending",
    val totalPrice: Double = 0.0,
    val products: List<CartProduct> = emptyList(),
    val address: Address = Address(),
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
    val orderId: String = System.currentTimeMillis().toString(),
    val prescriptionIds: List<String> = emptyList(),
    val prescriptionData: List<PrescriptionData> = emptyList(),
    val pharmacistId: String = "",
    val userId: String = "",
    val paymentMethod: String = "",
    val deliveryInstructions: String = "",
    val prescriptionImageUrl: String? = null,
    val pharmacyName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable {
    constructor() : this(
        orderStatus = OrderStatus.ORDERED.status,
        totalPrice = 0.0,
        products = emptyList(),
        address = Address(),
        date = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()),
        orderId = System.currentTimeMillis().toString(),
        prescriptionIds = emptyList(),
        userId = "",
        paymentMethod = "",
        deliveryInstructions = ""
    )
    fun hasPrescriptionItems(): Boolean {
        return prescriptionData.isNotEmpty() || products.any { it.product.requiresPrescription }
    }

    // Helper function to get all prescription product IDs
    fun getAllPrescriptionProductIds(): List<String> {
        return prescriptionData.flatMap { it.productIds } +
                products.filter { it.product.requiresPrescription }.map { it.product.id }
    }

}