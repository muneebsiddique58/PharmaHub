package com.example.pharmahub11.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CartProduct(
    val product: Product,
    val quantity: Int,
    val pharmacyId: String,
    val orderId: String? = null,
    val selectedStrength: String? = null,  // Changed from selectedColor
    val selectedDosageForm: String? = null, // Changed from selectedSize
    val pharmacistId: String = "",
    val pharmacyAddress: String = "",
    val pharmacyName: String? = null,
    val prescriptionImageUrl: String? = null
) : Parcelable {
    constructor() : this(Product(), 1, "",null, null)

    // Helper function to display key product info
    fun displayInfo(): String {
        return buildString {
            append(product.name)
            selectedStrength?.let { append(" ($it)") }
            selectedDosageForm?.let { append(" - $it") }
        }
    }
}