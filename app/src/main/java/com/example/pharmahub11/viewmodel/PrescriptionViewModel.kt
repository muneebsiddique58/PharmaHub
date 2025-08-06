package com.example.pharmahub11.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PrescriptionViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uploadState = MutableStateFlow<Resource<String>>(Resource.Idle())
    val uploadState: StateFlow<Resource<String>> = _uploadState

    private val _orderCreationState = MutableStateFlow<Resource<String>>(Resource.Idle())
    val orderCreationState: StateFlow<Resource<String>> = _orderCreationState

    private val _prescriptions = MutableStateFlow<Resource<List<PrescriptionData>>>(Resource.Idle())
    val prescriptions: StateFlow<Resource<List<PrescriptionData>>> = _prescriptions

    private val _prescription = MutableStateFlow<Resource<PrescriptionData?>>(Resource.Idle())
    val prescription: StateFlow<Resource<PrescriptionData?>> = _prescription

    fun savePrescription(prescriptionData: PrescriptionData): String {
        val prescriptionId = firestore.collection("prescriptions").document().id
        val completePrescription = prescriptionData.copy(id = prescriptionId)

        try {
            firestore.collection("prescriptions")
                .document(prescriptionId)
                .set(completePrescription)
                .addOnSuccessListener {
                    _uploadState.value = Resource.Success(prescriptionId)
                }
                .addOnFailureListener { e ->
                    _uploadState.value = Resource.Error(e.message ?: "Failed to save prescription")
                }
        } catch (e: Exception) {
            _uploadState.value = Resource.Error(e.message ?: "Failed to save prescription")
        }

        return prescriptionId
    }

    fun createOrderWithPrescription(prescriptionId: String, productIds: List<String>) {
        viewModelScope.launch {
            _orderCreationState.value = Resource.Loading()
            try {
                val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
                val orderId = firestore.collection("orders").document().id

                val orderData = hashMapOf(
                    "id" to orderId,
                    "userId" to userId,
                    "prescriptionId" to prescriptionId,
                    "products" to productIds,
                    "status" to "pending_prescription_verification",
                    "isPrescriptionVerified" to false,
                    "totalPrice" to 0.0, // You should calculate this
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                firestore.collection("orders")
                    .document(orderId)
                    .set(orderData)
                    .await()

                attachPrescriptionToOrder(prescriptionId, orderId)

                _orderCreationState.value = Resource.Success(orderId)
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error creating order", e)
                _orderCreationState.value = Resource.Error(e.message ?: "Failed to create order")
            }
        }
    }

    private suspend fun attachPrescriptionToOrder(prescriptionId: String, orderId: String) {
        try {
            firestore.collection("prescriptions")
                .document(prescriptionId)
                .update(
                    mapOf(
                        "orderId" to orderId,
                        "status" to "attached_to_order",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e("PrescriptionVM", "Error attaching prescription", e)
            throw e
        }
    }

    fun getPrescription(prescriptionId: String) {
        viewModelScope.launch {
            _prescription.value = Resource.Loading()
            try {
                val document = firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .get()
                    .await()

                val prescription = document.toObject(PrescriptionData::class.java)
                _prescription.value = Resource.Success(prescription)
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error fetching prescription", e)
                _prescription.value = Resource.Error(e.message ?: "Failed to fetch prescription")
            }
        }
    }

    fun getUserPrescriptions(): LiveData<List<PrescriptionData>> {
        val result = MutableLiveData<List<PrescriptionData>>()
        viewModelScope.launch {
            try {
                val prescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", auth.currentUser?.uid ?: "")
                    .get()
                    .await()
                    .toObjects(PrescriptionData::class.java)
                    .sortedByDescending { it.timestamp }

                result.postValue(prescriptions)
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error getting prescriptions", e)
                result.postValue(emptyList())
            }
        }
        return result
    }

    // Updated method to fetch actual product details including images and names
    suspend fun getProductsDetails(productIds: List<String>): List<PrescriptionData.ProductInfo> {
        val productDetails = mutableListOf<PrescriptionData.ProductInfo>()

        for (productId in productIds) {
            try {
                val document = firestore.collection("Products").document(productId).get().await()
                val product = document.toObject(Product::class.java)

                product?.let {
                    val productInfo = PrescriptionData.ProductInfo(
                        productId = it.id,
                        name = it.name,
                        genericName = it.genericName,
                        dosageForm = it.dosageForm,
                        strength = it.displayStrength,
                        imageUrl = it.images.firstOrNull() ?: "",
                        price = it.discountedPrice,
                        manufacturer = it.manufacturer ?: "",
                        requiresPrescription = it.requiresPrescription
                    )
                    productDetails.add(productInfo)
                }
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error fetching product $productId: ${e.message}")
            }
        }

        return productDetails
    }

    fun updatePrescriptionStatus(prescriptionId: String, status: String) {
        viewModelScope.launch {
            try {
                firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .update(
                        mapOf(
                            "status" to status,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                getPrescription(prescriptionId)
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error updating prescription status", e)
            }
        }
    }

    fun deletePrescription(prescriptionId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .delete()
                    .await()

                getUserPrescriptions()
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error deleting prescription", e)
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = Resource.Idle()
    }

    fun getPrescriptionsByOrder(orderId: String) {
        viewModelScope.launch {
            _prescriptions.value = Resource.Loading()
            try {
                val snapshot = firestore.collection("prescriptions")
                    .whereEqualTo("orderId", orderId)
                    .get()
                    .await()

                val prescriptionsList = snapshot.documents.mapNotNull {
                    it.toObject(PrescriptionData::class.java)
                }
                _prescriptions.value = Resource.Success(prescriptionsList)
            } catch (e: Exception) {
                Log.e("PrescriptionVM", "Error fetching order prescriptions", e)
                _prescriptions.value = Resource.Error(e.message ?: "Failed to fetch prescriptions for order")
            }
        }
    }
}