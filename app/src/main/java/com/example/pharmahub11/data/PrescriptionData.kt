package com.example.pharmahub11.data

import android.os.Parcelable
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Parcelize
data class PrescriptionData(
    val id: String = "",
    val orderId: String? = null,
    val productIds: List<String> = emptyList(),
    val prescriptionImageUrl: String = "",
    val products: List<ProductInfo> = emptyList(),
    val userId: String = "",
    val usedInOrder: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING,
    val pharmacistId: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val rejectionReason: String? = null
) : Parcelable {

    @Parcelize
    data class ProductInfo(
        val productId: String = "",
        val name: String = "",
        val genericName: String = "",
        val dosageForm: String = "",
        val strength: String = "",
        val imageUrl: String = "", // Main product image URL
        val category: String = "",
        val description: String = "",
        val price: Float = 0.0f,
        val cancelledAt: Long? = null,
        // You can add more fields as needed
        val manufacturer: String = "",
        val requiresPrescription: Boolean = true
    ) : Parcelable


    val formattedDate: String
        get() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(timestamp))

    val formattedDateTime: String
        get() = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            .format(Date(timestamp))

    fun isPending() = status.equals(STATUS_PENDING, true)
    fun isApproved() = status.equals(STATUS_APPROVED, true)
    fun isRejected() = status.equals(STATUS_REJECTED, true)
    fun isUsed() = status.equals(STATUS_USED, true)
    fun isCancelled() = status.equals(STATUS_CANCELLED, true)

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_USED = "used"
        const val STATUS_CANCELLED = "cancelled"

        fun getStatusList() = listOf(
            STATUS_PENDING,
            STATUS_APPROVED,
            STATUS_REJECTED,
            STATUS_USED,
            STATUS_CANCELLED
        )
        fun fromFirestore(document: DocumentSnapshot): PrescriptionData? {
            return try {
                val data = document.data ?: return null

                // Convert products list
                val products = mutableListOf<ProductInfo>()
                @Suppress("UNCHECKED_CAST")
                (data["products"] as? List<Map<String, Any>>)?.forEach { productMap ->
                    products.add(
                        ProductInfo(
                            productId = productMap["productId"] as? String ?: "",
                            name = productMap["name"] as? String ?: "",
                            genericName = productMap["genericName"] as? String ?: "",
                            dosageForm = productMap["dosageForm"] as? String ?: "",
                            strength = productMap["strength"] as? String ?: "",
                            imageUrl = productMap["imageUrl"] as? String ?: "",
                            category = productMap["category"] as? String ?: "",
                            description = productMap["description"] as? String ?: "",
                            price = (productMap["price"] as? Number)?.toFloat() ?: 0.0f,
                            manufacturer = productMap["manufacturer"] as? String ?: "",
                            requiresPrescription = productMap["requiresPrescription"] as? Boolean ?: true,
                            cancelledAt = (productMap["cancelledAt"] as? Number)?.toLong()
                        )
                    )
                }

                PrescriptionData(
                    id = document.id,
                    orderId = data["orderId"] as? String,
                    productIds = (data["productIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    prescriptionImageUrl = data["prescriptionImageUrl"] as? String ?: "",
                    products = products,
                    userId = data["userId"] as? String ?: "",
                    usedInOrder = data["usedInOrder"] as? String,
                    timestamp = convertTimestamp(data["timestamp"]),
                    status = data["status"] as? String ?: STATUS_PENDING,
                    pharmacistId = data["pharmacistId"] as? String,
                    note = data["note"] as? String,
                    createdAt = convertTimestamp(data["createdAt"]),
                    updatedAt = convertTimestamp(data["updatedAt"]),
                    rejectionReason = data["rejectionReason"] as? String
                )
            } catch (e: Exception) {
                println("Error parsing prescription document: ${e.message}")
                null
            }
        }

        private fun convertTimestamp(value: Any?): Long {
            return when (value) {
                is com.google.firebase.Timestamp -> value.toDate().time
                is Long -> value
                is Date -> value.time
                else -> System.currentTimeMillis()
            }
        }

    }

}