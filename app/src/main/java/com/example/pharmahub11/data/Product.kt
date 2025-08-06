package com.example.pharmahub11.data

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Product(
    val id: String,
    val name: String,                // Brand name (e.g., "Dispirin")
    val genericName: String,         // Generic name (e.g., "Aspirin")
    val category: String,            // e.g., "Prescription", "Over-the-Counter"
    val price: Float,
    val offerPercentage: Float? = null,
    val description: String? = null,  // Additional details
    val dosageForm: String,          // e.g., "Tablet", "Syrup", "Injection"
    val strengths: List<String>,     // List of available strengths
    val manufacturer: String?,       // e.g., "Bayer" (optional)
    val images: List<String>,        // Product image URLs
    val requiresPrescription: Boolean = false,
    val activeIngredients: List<String> = emptyList(),
    val sideEffects: String? = null,
    val storageInstructions: String? = null,
    val pharmacyId: String,
    val quantity: Int = 0, // This might be the total quantity
    var availableQuantity: Int = 0, // This is the field we need to check
    val pharmacyName: String? = null,
    val manufacturingDate: String? = null, // e.g., "MM/yyyy" format
    val expiryDate: String? = null         // e.g., "MM/yyyy" format
) : Parcelable {
    constructor() : this(
        id = "0",
        name = "",
        genericName = "",
        category = "",
        price = 0f,
        offerPercentage = null,
        description = null,
        dosageForm = "",
        strengths = emptyList(),
        manufacturer = null,
        images = emptyList(),
        requiresPrescription = false,
        activeIngredients = emptyList(),
        sideEffects = null,
        storageInstructions = null,
        pharmacyId = "",
        pharmacyName = "",
        manufacturingDate = null,
        expiryDate = null
    )

    @get:PropertyName("displayStrength")
    val displayStrength: String
        get() = if (strengths.size == 1) strengths.first() else ""

    @get:PropertyName("discountedPrice")
    val discountedPrice: Float
        get() = offerPercentage?.let { price * (1 - it / 100) } ?: price

    // Alternative names for Firestore mapping if needed
    fun getDisplayStrengthValue(): String = displayStrength
    fun getDiscountedPriceValue(): Float = discountedPrice
}