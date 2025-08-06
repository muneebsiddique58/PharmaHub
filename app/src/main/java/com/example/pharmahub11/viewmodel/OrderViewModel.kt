package com.example.pharmahub11.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.toFirestoreMap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    // State flows for order processing
    private val _orderState = MutableStateFlow<Resource<Order>>(Resource.Unspecified())
    val order: StateFlow<Resource<Order>> = _orderState.asStateFlow()

    private val _prescriptionValidation = MutableStateFlow<Resource<Boolean>>(Resource.Unspecified())
    val prescriptionValidation: StateFlow<Resource<Boolean>> = _prescriptionValidation.asStateFlow()

    private val _prescriptions = MutableStateFlow<Resource<List<PrescriptionData>>>(Resource.Unspecified())
    val prescriptions: StateFlow<Resource<List<PrescriptionData>>> = _prescriptions.asStateFlow()

    /**
     * Places a new order with prescription validation
     */
    fun placeOrder(order: Order, paymentMethod: String = "COD") {
        viewModelScope.launch {
            _orderState.emit(Resource.Loading())

            try {
                val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

                // 1. Validate prescriptions if needed
                val validatedPrescriptions = if (order.prescriptionData.isNotEmpty()) {
                    try {
                        validatePrescriptions(order.prescriptionData, userId)
                    } catch (e: Exception) {
                        Log.e("OrderViewModel", "Prescription validation failed", e)
                        _orderState.emit(Resource.Error("Prescription validation failed. Please check your prescriptions and try again."))
                        return@launch
                    }
                } else {
                    emptyList()
                }

                // 2. Enhance products with pharmacy information
                val productsWithPharmacyInfo = order.products.map { cartProduct ->
                    if (cartProduct.pharmacistId?.isNotBlank() == true && cartProduct.pharmacyName?.isNotBlank() == true) {
                        cartProduct
                    } else {
                        fetchProductWithPharmacyInfo(cartProduct)
                    }
                }

                // 3. Create enhanced order with validated data
                val enhancedOrder = order.copy(
                    prescriptionData = validatedPrescriptions,
                    userId = userId,
                    paymentMethod = paymentMethod,
                    products = productsWithPharmacyInfo,
                    orderStatus = if (paymentMethod == "COD") OrderStatus.ORDERED.name
                    else OrderStatus.PAID.name
                )

                // 4. Save to database
                saveOrderToDatabase(enhancedOrder)

                // 5. Mark prescriptions as used
                if (validatedPrescriptions.isNotEmpty()) {
                    markPrescriptionsAsUsed(validatedPrescriptions, enhancedOrder.orderId)
                }

                _orderState.emit(Resource.Success(enhancedOrder))
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Order placement failed", e)
                _orderState.emit(Resource.Error(e.message ?: "Order placement failed. Please try again."))
            }
        }
    }

    private suspend fun fetchProductWithPharmacyInfo(cartProduct: CartProduct): CartProduct {
        return try {
            val productDoc = firestore.collection("Products")
                .document(cartProduct.product.id)
                .get()
                .await()

            cartProduct.copy(
                pharmacistId = productDoc.getString("pharmacistId") ?: "",
                pharmacyName = productDoc.getString("pharmacyName") ?: "",
                pharmacyAddress = productDoc.getString("pharmacyAddress") ?: ""
            )
        } catch (e: Exception) {
            Log.e("OrderViewModel", "Failed to fetch pharmacy info for product ${cartProduct.product.id}", e)
            cartProduct // Return original if failed to fetch
        }
    }

    private suspend fun markPrescriptionsAsUsed(
        prescriptions: List<PrescriptionData>,
        orderId: String
    ) {
        try {
            val batch = firestore.batch()
            prescriptions.forEach { prescription ->
                val ref = firestore.collection("prescriptions").document(prescription.id)
                batch.update(ref, mapOf(
                    "usedInOrder" to orderId,
                    "status" to PrescriptionData.STATUS_USED,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.e("OrderViewModel", "Failed to mark prescriptions as used", e)
        }
    }

    private suspend fun validatePrescriptions(
        prescriptions: List<PrescriptionData>,
        userId: String
    ): List<PrescriptionData> {
        if (prescriptions.isEmpty()) return emptyList()

        return try {
            val prescriptionIds = prescriptions.map { it.id }
            val querySnapshot = firestore.collection("prescriptions")
                .whereIn(FieldPath.documentId(), prescriptionIds)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", PrescriptionData.STATUS_APPROVED)
                .get()
                .await()

            val validPrescriptions = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(PrescriptionData::class.java)?.copy(id = doc.id)
            }

            if (validPrescriptions.size != prescriptionIds.size) {
                val missingIds = prescriptionIds - validPrescriptions.map { it.id }
                throw IllegalStateException("Invalid or used prescriptions: $missingIds")
            }

            validPrescriptions
        } catch (e: Exception) {
            Log.e("OrderViewModel", "Prescription validation error", e)
            throw IllegalStateException("Prescription validation failed: ${e.message}")
        }
    }

    internal suspend fun validatePrescriptionsForOrder(
        prescriptions: List<PrescriptionData>,
        userId: String
    ): Resource<Boolean> {
        return try {
            if (prescriptions.isEmpty()) return Resource.Success(true)

            val prescriptionIds = prescriptions.map { it.id }
            val querySnapshot = firestore.collection("prescriptions")
                .whereIn(FieldPath.documentId(), prescriptionIds)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", PrescriptionData.STATUS_APPROVED)
                .get()
                .await()

            val foundPrescriptions = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(PrescriptionData::class.java)?.copy(id = doc.id)
            }

            if (foundPrescriptions.size != prescriptionIds.size) {
                return Resource.Error("Some prescriptions are invalid or already used")
            }

            Resource.Success(true)
        } catch (e: Exception) {
            Log.e("OrderViewModel", "Prescription validation error", e)
            Resource.Error("Prescription validation failed: ${e.message}")
        }
    }

    private suspend fun saveOrderToDatabase(order: Order) {
        val batch = firestore.batch()

        try {
            // 1. Save to user's orders
            val userOrderRef = firestore.collection("user")
                .document(order.userId)
                .collection("orders")
                .document(order.orderId)
            batch.set(userOrderRef, order.toFirestoreMap())

            // 2. Save to global orders
            val globalOrderRef = firestore.collection("orders")
                .document(order.orderId)
            batch.set(globalOrderRef, order.toFirestoreMap())

            // 3. Save to pharmacists' orders
            saveToPharmacistOrders(batch, order)

            // 4. Clear cart
            clearUserCart(batch, order.userId)

            // 5. Update product quantities
            updateProductQuantities(batch, order.products)

            batch.commit().await()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to save order: ${e.message}")
        }
    }

    private fun saveToPharmacistOrders(batch: com.google.firebase.firestore.WriteBatch, order: Order) {
        order.products.groupBy { it.pharmacistId }.forEach { (pharmacistId, products) ->
            // Safe check for null or blank pharmacistId
            if (!pharmacistId.isNullOrBlank()) {
                val pharmacistOrderRef = firestore.collection("pharmacists")
                    .document(pharmacistId)
                    .collection("orders")
                    .document(order.orderId)

                // Create product maps safely
                val productMaps = products.map { product ->
                    mapOf(
                        "productId" to product.product.id,
                        "name" to product.product.name,
                        "quantity" to product.quantity,
                        "price" to product.product.price,
                        "images" to (product.product.images ?: emptyList<String>()),
                        "pharmacyName" to (product.pharmacyName ?: ""),
                        "pharmacyAddress" to (product.pharmacyAddress ?: "")
                    )
                }

                // Create prescription maps safely
                val prescriptionMaps = order.prescriptionData.map { prescription ->
                    mapOf(
                        "id" to prescription.id,
                        "imageUrl" to prescription.prescriptionImageUrl
                    )
                }

                batch.set(pharmacistOrderRef, mapOf(
                    "orderId" to order.orderId,
                    "userId" to order.userId,
                    "pharmacistId" to pharmacistId,
                    "pharmacyName" to (products.firstOrNull()?.pharmacyName ?: ""),
                    "pharmacyAddress" to (products.firstOrNull()?.pharmacyAddress ?: ""),
                    "products" to productMaps,
                    "prescriptions" to prescriptionMaps,
                    "totalPrice" to products.sumOf { it.product.price.toDouble() * it.quantity.toDouble() },
                    "status" to order.orderStatus,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }
        }
    }

    private suspend fun clearUserCart(batch: com.google.firebase.firestore.WriteBatch, userId: String) {
        firestore.collection("user")
            .document(userId)
            .collection("cart")
            .get()
            .await()
            .forEach { batch.delete(it.reference) }
    }

    private fun updateProductQuantities(batch: com.google.firebase.firestore.WriteBatch, products: List<CartProduct>) {
        products.forEach { product ->
            val productRef = firestore.collection("Products")
                .document(product.product.id)
            batch.update(
                productRef,
                "quantity", FieldValue.increment(-product.quantity.toLong()),
                "salesCount", FieldValue.increment(product.quantity.toLong())
            )
        }
    }

    fun loadPrescriptionsForOrder(orderId: String) {
        viewModelScope.launch {
            _prescriptions.emit(Resource.Loading())
            try {
                val querySnapshot = firestore.collection("prescriptions")
                    .whereEqualTo("usedInOrder", orderId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val prescriptions = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(PrescriptionData::class.java)?.copy(id = doc.id)
                }

                _prescriptions.emit(Resource.Success(prescriptions))
            } catch (e: Exception) {
                _prescriptions.emit(Resource.Error(e.message ?: "Failed to load prescriptions"))
            }
        }
    }

    fun verifyOrderPrescriptions(orderId: String) {
        viewModelScope.launch {
            try {
                val orderDoc = firestore.collection("orders")
                    .document(orderId)
                    .get()
                    .await()

                val prescriptions = orderDoc.get("prescriptions") as? List<Map<String, Any>> ?: emptyList()
                Log.d("OrderViewModel", "Order $orderId has ${prescriptions.size} prescriptions")

                val prescriptionsQuery = firestore.collection("prescriptions")
                    .whereEqualTo("usedInOrder", orderId)
                    .get()
                    .await()

                Log.d("OrderViewModel", "Found ${prescriptionsQuery.size()} prescriptions linked to order $orderId")
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Error verifying prescriptions", e)
            }
        }
    }

    fun resetOrderState() {
        _orderState.value = Resource.Unspecified()
    }

    fun resetPrescriptionValidation() {
        _prescriptionValidation.value = Resource.Unspecified()
    }

    fun resetPrescriptionsState() {
        _prescriptions.value = Resource.Unspecified()
    }
}