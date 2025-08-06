package com.example.pharmahub11.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.data.Review
import com.example.pharmahub11.firebase.FirebaseCommon
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val firebaseCommon: FirebaseCommon
) : ViewModel() {

    private val _addToCart = MutableStateFlow<Resource<CartProduct>>(Resource.Unspecified())
    val addToCart = _addToCart.asStateFlow()

    private val _prescriptionStatus = MutableStateFlow<Resource<Boolean>>(Resource.Unspecified())
    val prescriptionStatus = _prescriptionStatus.asStateFlow()

    private val _reviews = MutableStateFlow<Resource<List<Review>>>(Resource.Unspecified())
    val reviews = _reviews.asStateFlow()

    fun addUpdateProductInCart(cartProduct: CartProduct) {
        viewModelScope.launch { _addToCart.emit(Resource.Loading()) }
        val userId = auth.uid
        if (userId != null) {
            firestore.collection("user").document(userId).collection("cart")
                .whereEqualTo("product.id", cartProduct.product.id).get()
                .addOnSuccessListener { querySnapshot ->
                    val documents = querySnapshot.documents
                    if (documents.isEmpty()) {
                        addNewProduct(cartProduct)
                    } else {
                        val existingProduct = documents.first().toObject(CartProduct::class.java)
                        if (existingProduct != null) {
                            if (existingProduct.product.id == cartProduct.product.id &&
                                existingProduct.selectedStrength == cartProduct.selectedStrength &&
                                existingProduct.selectedDosageForm == cartProduct.selectedDosageForm) {
                                val documentId = documents.first().id
                                increaseQuantity(documentId, cartProduct)
                            } else {
                                addNewProduct(cartProduct)
                            }
                        }
                    }
                }.addOnFailureListener { exception ->
                    viewModelScope.launch {
                        _addToCart.emit(Resource.Error(exception.localizedMessage ?: "An unknown error occurred"))
                    }
                }
        } else {
            viewModelScope.launch {
                _addToCart.emit(Resource.Error("User not authenticated"))
            }
        }
    }

    private fun addNewProduct(cartProduct: CartProduct) {
        firebaseCommon.addProductToCart(cartProduct) { addedProduct, exception ->
            viewModelScope.launch {
                if (exception == null) {
                    _addToCart.emit(Resource.Success(addedProduct!!))
                } else {
                    _addToCart.emit(Resource.Error(exception.localizedMessage ?: "Failed to add product"))
                }
            }
        }
    }

    private fun increaseQuantity(documentId: String, cartProduct: CartProduct) {
        firebaseCommon.increaseQuantity(documentId) { _, exception ->
            viewModelScope.launch {
                if (exception == null) {
                    _addToCart.emit(Resource.Success(cartProduct))
                } else {
                    _addToCart.emit(Resource.Error(exception.localizedMessage ?: "Failed to increase quantity"))
                }
            }
        }
    }

    fun loadPrescriptionStatus() {
        viewModelScope.launch {
            _prescriptionStatus.emit(Resource.Loading())
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
                val hasPendingPrescription = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", PrescriptionData.STATUS_PENDING)
                    .whereEqualTo("usedInOrder", null)
                    .get()
                    .await()
                    .documents.isNotEmpty()
                _prescriptionStatus.emit(Resource.Success(hasPendingPrescription))
            } catch (e: Exception) {
                _prescriptionStatus.emit(Resource.Error(e.message ?: "Failed to check prescription status"))
            }
        }
    }

    fun checkPendingPrescription(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    callback(false)
                    return@launch
                }
                val hasPendingPrescription = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", PrescriptionData.STATUS_PENDING)
                    .whereEqualTo("usedInOrder", null)
                    .get()
                    .await()
                    .documents.isNotEmpty()
                callback(hasPendingPrescription)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    fun isProductExpired(product: Product): Boolean {
        if (product.expiryDate.isNullOrBlank()) return false
        return try {
            val dateFormat = SimpleDateFormat("MM/yyyy", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Karachi")
            }
            val expiryDate = dateFormat.parse(product.expiryDate)
            val currentDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi")).apply {
                time = Date()
            }.time
            expiryDate != null && expiryDate.before(currentDate)
        } catch (e: Exception) {
            false
        }
    }

    // In your DetailsViewModel or a separate ReviewsViewModel
    fun loadProductReviews(productId: String) {
        viewModelScope.launch {
            _reviews.value = Resource.Loading()
            try {
                val reviews = firestore.collection("reviews")
                    .whereEqualTo("productId", productId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .documents.mapNotNull { document ->
                        try {
                            document.toObject(Review::class.java)?.also {
                                // Ensure productId matches exactly (case-sensitive)
                                if (it.productId == productId) {
                                    return@mapNotNull it
                                }
                            }
                            null
                        } catch (e: Exception) {
                            Log.e("DetailsViewModel", "Error parsing review", e)
                            null
                        }
                    }

                if (reviews.isNotEmpty()) {
                    _reviews.value = Resource.Success(reviews)
                } else {
                    _reviews.value = Resource.Success(emptyList()) // Explicit empty list
                }
            } catch (e: Exception) {
                Log.e("DetailsViewModel", "Error loading reviews", e)
                _reviews.value = Resource.Error(e.message ?: "Failed to load reviews")
            }
        }
    }
}

