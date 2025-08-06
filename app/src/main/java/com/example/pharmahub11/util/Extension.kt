package com.example.pharmahub11.util

import android.app.Activity
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pharmahub11.R
import com.example.pharmahub11.data.Address
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.data.order.Order
import com.google.type.LatLng
import java.io.IOException
import java.security.Timestamp
import java.util.Date
import java.util.Locale

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Activity.hideKeyboard() {
    hideKeyboard(currentFocus ?: View(this))
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}
fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
// Add this to a new file or your existing extensions
fun String.capitalizeWords(): String {
    return this.split("_").joinToString(" ") { it.capitalize() }
}

fun String.getPrescriptionStatusColor(): Int {
    return when (this.toLowerCase()) {
        "approved" -> R.color.g_green
        "rejected" -> R.color.g_red
        else -> R.color.g_orange_yellow
    }
}
fun Order.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "orderId" to orderId,
        "userId" to userId,
        "date" to date,
        "totalPrice" to totalPrice,
        "orderStatus" to orderStatus,
        "paymentMethod" to paymentMethod,
        "createdAt" to createdAt,
        "address" to address.toFirestoreMap(),
        "products" to products.map { it.toFirestoreMap() },
        "prescriptions" to prescriptionData.map { it.toFirestoreMap() }
    )
}
fun Address.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "addressTitle" to addressTitle,
        "fullName" to fullName,
        "street" to street,
        "phone" to phone,
        "city" to city,
        "state" to state
    )
}
fun CartProduct.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "product" to product.toFirestoreMap(),
        "quantity" to quantity,
        "selectedStrength" to (selectedStrength ?: ""),
        "selectedDosageForm" to (selectedDosageForm ?: ""),
        "pharmacistId" to pharmacistId,
        "pharmacyName" to (pharmacyName ?: ""),
        "pharmacyAddress" to (pharmacyAddress ?: ""),
        "prescriptionImageUrl" to (prescriptionImageUrl ?: "")
    )
}
fun Product.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "name" to name,
        "genericName" to genericName,
        "category" to category,
        "price" to price,
        "offerPercentage" to (offerPercentage ?: 0f),
        "description" to (description ?: ""),
        "dosageForm" to dosageForm,
        "strengths" to strengths,
        "manufacturer" to (manufacturer ?: ""),
        "images" to images,
        "requiresPrescription" to requiresPrescription,
        "activeIngredients" to activeIngredients,
        "sideEffects" to (sideEffects ?: ""),
        "storageInstructions" to (storageInstructions ?: ""),
        "pharmacyId" to pharmacyId,
        "pharmacyName" to (pharmacyName ?: "")
    )
}

fun PrescriptionData.toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "orderId" to (orderId ?: ""),
        "productIds" to productIds,
        "prescriptionImageUrl" to prescriptionImageUrl,
        "userId" to userId,
        "timestamp" to timestamp,
        "status" to status,
        "pharmacistId" to (pharmacistId ?: ""),
        "note" to (note ?: ""),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}


