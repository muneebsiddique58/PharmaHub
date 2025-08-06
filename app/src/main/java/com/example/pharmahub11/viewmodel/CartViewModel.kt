package com.example.pharmahub11.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.firebase.FirebaseCommon
import com.example.pharmahub11.helper.getProductPrice
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val firebaseCommon: FirebaseCommon
) : ViewModel() {

    companion object {
        private const val PRESCRIPTION_EXPIRY_DAYS = 7L
        private const val PRESCRIPTION_PURGE_HOURS = 24L
    }

    // Prescription State Machine
    enum class PrescriptionState {
        NEW_UPLOAD_REQUIRED,
        UPLOADED_PENDING,
        APPROVED,
        REJECTED,
        EXPIRED,
        CANCELLED
    }

    private val _cartProducts =
        MutableStateFlow<Resource<List<CartProduct>>>(Resource.Unspecified())
    val cartProducts = _cartProducts.asStateFlow()

    private val _prescriptions = MutableStateFlow<Resource<List<PrescriptionData>>>(Resource.Unspecified())
    val prescriptions = _prescriptions.asStateFlow()

    // Prescription state flow
    private val _prescriptionState = MutableStateFlow(PrescriptionState.NEW_UPLOAD_REQUIRED)
    val prescriptionState = _prescriptionState.asStateFlow()

    // Flow for prescription cancellation
    private val _prescriptionCancelled = MutableSharedFlow<Boolean>()
    val prescriptionCancelled = _prescriptionCancelled.asSharedFlow()

    // Flow for prescription cleared (after rejection/expiry)
    private val _prescriptionCleared = MutableSharedFlow<Boolean>()
    val prescriptionCleared = _prescriptionCleared.asSharedFlow()

    // Flow for checkout blocked state
    private val _checkoutBlocked = MutableStateFlow(false)
    val checkoutBlocked = _checkoutBlocked.asStateFlow()

    // Flow for cart modification blocked state
    private val _cartModificationBlocked = MutableStateFlow(false)
    val cartModificationBlocked = _cartModificationBlocked.asStateFlow()

    val productsPrice = cartProducts.map {
        when (it) {
            is Resource.Success -> {
                calculatePrice(it.data!!)
            }
            else -> null
        }
    }

    private val _deleteDialog = MutableSharedFlow<CartProduct>()
    val deleteDialog = _deleteDialog.asSharedFlow()

    private var cartProductDocuments = emptyList<DocumentSnapshot>()

    init {
        getCartProducts()
        setupPrescriptionStateObserver()
    }

    private fun setupPrescriptionStateObserver() {
        viewModelScope.launch {
            // Observe cart products and update prescription state
            cartProducts.collect { resource ->
                if (resource is Resource.Success) {
                    updatePrescriptionStateForCart(resource.data ?: emptyList())
                }
            }
        }
    }

    private fun updatePrescriptionStateForCart(cartItems: List<CartProduct>) {
        val hasPrescriptionItems = cartItems.any { requiresPrescription(it.product) }

        if (!hasPrescriptionItems) {
            _prescriptionState.value = PrescriptionState.NEW_UPLOAD_REQUIRED
            _checkoutBlocked.value = false
            _cartModificationBlocked.value = false
            return
        }

        val currentPrescriptions = (prescriptions.value as? Resource.Success)?.data ?: emptyList()
        val relevantPrescription = getRelevantPrescription(currentPrescriptions, cartItems)

        updatePrescriptionState(relevantPrescription)
    }

    private fun requiresPrescription(product: com.example.pharmahub11.data.Product): Boolean {
        return product.requiresPrescription ||
                product.category.equals("prescription", ignoreCase = true)
    }

    private fun getRelevantPrescription(
        prescriptions: List<PrescriptionData>,
        cartItems: List<CartProduct>
    ): PrescriptionData? {
        val prescriptionItems = cartItems.filter { requiresPrescription(it.product) }

        return prescriptions
            .filter { prescription ->
                // Check if prescription covers current cart items
                prescriptionItems.any { cartItem ->
                    cartItem.product.id in prescription.productIds
                }
            }
            .maxByOrNull { it.timestamp }
    }

    private fun updatePrescriptionState(prescription: PrescriptionData?) {
        val newState = when {
            prescription == null -> PrescriptionState.NEW_UPLOAD_REQUIRED
            prescription.status == PrescriptionData.STATUS_PENDING -> {
                when {
                    prescription.usedInOrder != null -> PrescriptionState.NEW_UPLOAD_REQUIRED
                    else -> PrescriptionState.UPLOADED_PENDING
                }
            }
            prescription.status == PrescriptionData.STATUS_APPROVED -> {
                when {
                    prescription.usedInOrder != null -> PrescriptionState.NEW_UPLOAD_REQUIRED
                    isPrescriptionExpired(prescription) -> PrescriptionState.EXPIRED
                    else -> PrescriptionState.APPROVED
                }
            }
            prescription.status == PrescriptionData.STATUS_REJECTED -> PrescriptionState.REJECTED
            prescription.status == PrescriptionData.STATUS_CANCELLED -> PrescriptionState.CANCELLED
            else -> PrescriptionState.NEW_UPLOAD_REQUIRED
        }

        _prescriptionState.value = newState
        updateBlockedStates(newState)
    }

    private fun updateBlockedStates(state: PrescriptionState) {
        _checkoutBlocked.value = when (state) {
            PrescriptionState.APPROVED -> false
            else -> true
        }

        _cartModificationBlocked.value = when (state) {
            PrescriptionState.UPLOADED_PENDING -> true
            else -> false
        }
    }

    private fun isPrescriptionExpired(prescription: PrescriptionData): Boolean {
        val now = Date()
        val prescriptionDate = Date(prescription.timestamp)
        val daysDiff = TimeUnit.MILLISECONDS.toDays(now.time - prescriptionDate.time)
        return daysDiff > PRESCRIPTION_EXPIRY_DAYS
    }

    fun deleteCartProduct(cartProduct: CartProduct) {
        val index = cartProducts.value.data?.indexOf(cartProduct)
        if (index != null && index != -1) {
            val documentId = cartProductDocuments[index].id

            viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }

            firestore.collection("user").document(auth.uid!!).collection("cart")
                .document(documentId).delete()
                .addOnSuccessListener {
                    // Deletion successful - the snapshot listener will automatically update the UI
                }
                .addOnFailureListener { exception ->
                    viewModelScope.launch {
                        _cartProducts.emit(Resource.Error(exception.message ?: "Failed to delete item"))
                    }
                }
        }
    }

    private fun calculatePrice(data: List<CartProduct>): Float {
        return data.sumByDouble { cartProduct ->
            (cartProduct.product.offerPercentage.getProductPrice(cartProduct.product.price) * cartProduct.quantity).toDouble()
        }.toFloat()
    }

    private fun getCartProducts() {
        viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }
        firestore.collection("user").document(auth.uid!!).collection("cart")
            .addSnapshotListener { value, error ->
                if (error != null || value == null) {
                    viewModelScope.launch { _cartProducts.emit(Resource.Error(error?.message.toString())) }
                } else {
                    cartProductDocuments = value.documents
                    val cartProducts = value.toObjects(CartProduct::class.java)
                    viewModelScope.launch { _cartProducts.emit(Resource.Success(cartProducts)) }
                }
            }
    }

    fun hasPendingPrescriptionForProducts(productIds: List<String>): Boolean {
        return (prescriptions.value as? Resource.Success)?.data?.any { prescription ->
            prescription.status == PrescriptionData.STATUS_PENDING &&
                    prescription.productIds.any { it in productIds }
        } ?: false
    }

    fun hasPendingPrescription(): Boolean {
        return (prescriptions.value as? Resource.Success)?.data?.any { prescription ->
            prescription.status == PrescriptionData.STATUS_PENDING && prescription.usedInOrder == null
        } ?: false
    }

    // Enhanced prescription cancellation with complete cleanup
    fun cancelPendingPrescription() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch

                val pendingPrescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", PrescriptionData.STATUS_PENDING)
                    .whereEqualTo("usedInOrder", null)
                    .get()
                    .await()

                val batch = firestore.batch()
                pendingPrescriptions.documents.forEach { doc ->
                    // Mark as cancelled instead of just updating status
                    batch.update(doc.reference, mapOf(
                        "status" to PrescriptionData.STATUS_CANCELLED,
                        "cancelledAt" to System.currentTimeMillis()
                    ))
                }

                batch.commit().await()

                // Clear prescription state
                _prescriptionState.value = PrescriptionState.NEW_UPLOAD_REQUIRED
                _checkoutBlocked.value = true
                _cartModificationBlocked.value = false

                // Refresh prescriptions and emit cancellation event
                loadPrescriptions()
                _prescriptionCancelled.emit(true)

            } catch (e: Exception) {
                Log.e("CartViewModel", "Error cancelling prescription", e)
            }
        }
    }

    // New function to clear rejected/expired prescriptions
    fun clearRejectedPrescription(prescriptionId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .update(mapOf(
                        "status" to PrescriptionData.STATUS_CANCELLED,
                        "clearedAt" to System.currentTimeMillis()
                    ))
                    .await()

                _prescriptionState.value = PrescriptionState.NEW_UPLOAD_REQUIRED
                _prescriptionCleared.emit(true)
                loadPrescriptions()

            } catch (e: Exception) {
                Log.e("CartViewModel", "Error clearing prescription", e)
            }
        }
    }

    // Auto-purge old prescriptions
    fun purgeOldPrescriptions() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(PRESCRIPTION_PURGE_HOURS)

                val oldPrescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereIn("status", listOf(
                        PrescriptionData.STATUS_REJECTED,
                        PrescriptionData.STATUS_CANCELLED
                    ))
                    .whereLessThan("timestamp", cutoffTime)
                    .get()
                    .await()

                val batch = firestore.batch()
                oldPrescriptions.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                if (oldPrescriptions.documents.isNotEmpty()) {
                    batch.commit().await()
                    Log.d("CartViewModel", "Purged ${oldPrescriptions.size()} old prescriptions")
                }

            } catch (e: Exception) {
                Log.e("CartViewModel", "Error purging old prescriptions", e)
            }
        }
    }

    // Enhanced function to check if new prescription is required
    fun requiresNewPrescription(cartItems: List<CartProduct>): Boolean {
        if (cartItems.none { requiresPrescription(it.product) }) {
            return false
        }

        val currentState = prescriptionState.value
        return when (currentState) {
            PrescriptionState.NEW_UPLOAD_REQUIRED,
            PrescriptionState.REJECTED,
            PrescriptionState.EXPIRED,
            PrescriptionState.CANCELLED -> true
            else -> false
        }
    }

    // Function to get prescription status message
    fun getPrescriptionStatusMessage(): String {
        return when (prescriptionState.value) {
            PrescriptionState.NEW_UPLOAD_REQUIRED -> "New prescription required - Previous prescription cannot be reused"
            PrescriptionState.UPLOADED_PENDING -> "⏳ Prescription uploaded - pending review"
            PrescriptionState.APPROVED -> "✅ Prescription approved"
            PrescriptionState.REJECTED -> "❌ Prescription rejected"
            PrescriptionState.EXPIRED -> "⏰ Prescription expired"
            PrescriptionState.CANCELLED -> "New prescription required - Previous prescription cannot be reused"
        }
    }

    // Function to get prescription instruction message
    fun getPrescriptionInstructionMessage(): String {
        return when (prescriptionState.value) {
            PrescriptionState.NEW_UPLOAD_REQUIRED,
            PrescriptionState.CANCELLED -> "Each order requires current, approved prescription"
            PrescriptionState.UPLOADED_PENDING -> "⏳ Prescription under review. Cannot modify cart until approved."
            PrescriptionState.REJECTED -> "Upload a new prescription to continue"
            PrescriptionState.EXPIRED -> "Prescriptions are valid for $PRESCRIPTION_EXPIRY_DAYS days"
            PrescriptionState.APPROVED -> "Valid prescription attached"
        }
    }

    fun changeQuantity(
        cartProduct: CartProduct,
        quantityChanging: FirebaseCommon.QuantityChanging
    ) {
        // Block quantity changes if cart modification is blocked
        if (cartModificationBlocked.value) {
            return
        }

        val index = cartProducts.value.data?.indexOf(cartProduct)

        if (index != null && index != -1) {
            val documentId = cartProductDocuments[index].id
            when (quantityChanging) {
                FirebaseCommon.QuantityChanging.INCREASE -> {
                    viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }
                    increaseQuantity(documentId)
                }
                FirebaseCommon.QuantityChanging.DECREASE -> {
                    if (cartProduct.quantity == 1) {
                        viewModelScope.launch { _deleteDialog.emit(cartProduct) }
                        return
                    }
                    viewModelScope.launch { _cartProducts.emit(Resource.Loading()) }
                    decreaseQuantity(documentId)
                }
            }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            _cartProducts.value = Resource.Loading()
            try {
                firestore.collection("user")
                    .document(auth.uid!!)
                    .collection("cart")
                    .get()
                    .addOnSuccessListener { documents ->
                        val batch = firestore.batch()
                        documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                            .addOnSuccessListener {
                                _cartProducts.value = Resource.Success(emptyList())
                                _prescriptionState.value = PrescriptionState.NEW_UPLOAD_REQUIRED
                                _checkoutBlocked.value = false
                                _cartModificationBlocked.value = false
                            }
                            .addOnFailureListener { e ->
                                _cartProducts.value = Resource.Error(e.message.toString())
                            }
                    }
                    .addOnFailureListener { e ->
                        _cartProducts.value = Resource.Error(e.message.toString())
                    }
            } catch (e: Exception) {
                _cartProducts.value = Resource.Error(e.message.toString())
            }
        }
    }

    private fun decreaseQuantity(documentId: String) {
        firebaseCommon.decreaseQuantity(documentId) { result, exception ->
            if (exception != null)
                viewModelScope.launch { _cartProducts.emit(Resource.Error(exception.message.toString())) }
        }
    }

    private fun increaseQuantity(documentId: String) {
        firebaseCommon.increaseQuantity(documentId) { result, exception ->
            if (exception != null)
                viewModelScope.launch { _cartProducts.emit(Resource.Error(exception.message.toString())) }
        }
    }

    fun loadPrescriptions() {
        viewModelScope.launch {
            _prescriptions.value = Resource.Loading()
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                val prescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("usedInOrder", null)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .toObjects(PrescriptionData::class.java)
                    .map { it.copy(id = it.id) }

                _prescriptions.value = Resource.Success(prescriptions)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error getting prescriptions", e)
                _prescriptions.value = Resource.Error(e.message ?: "Failed to load prescriptions")
            }
        }
    }

    fun markPrescriptionAsUsed(prescriptionId: String, orderId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .update("usedInOrder", orderId)
                    .await()

                loadPrescriptions()
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error marking prescription as used", e)
            }
        }
    }

    fun checkPrescriptionValidity(): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()

        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    result.postValue(false)
                    return@launch
                }

                val validPrescription = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", "approved")
                    .limit(1)
                    .get()
                    .await()
                    .documents.isNotEmpty()

                result.postValue(validPrescription)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error checking prescription validity", e)
                result.postValue(false)
            }
        }

        return result
    }

    fun getPrescriptionData(): LiveData<List<PrescriptionData>> {
        val liveData = MutableLiveData<List<PrescriptionData>>()

        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    liveData.postValue(emptyList())
                    return@launch
                }

                val prescriptionsSnapshot = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", "approved")
                    .whereEqualTo("usedInOrder", null)
                    .get()
                    .await()

                val prescriptions = prescriptionsSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(PrescriptionData::class.java)?.copy(id = doc.id)
                }

                liveData.postValue(prescriptions)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error fetching prescription data", e)
                liveData.postValue(emptyList())
            }
        }

        return liveData
    }

    fun getLatestRelevantPrescription(
        prescriptions: List<PrescriptionData>,
        prescriptionItems: List<CartProduct>
    ): PrescriptionData? {
        return prescriptions.filter { prescription ->
            prescription.usedInOrder == null &&
                    prescriptionItems.any { cartItem ->
                        cartItem.product.id in prescription.productIds
                    }
        }.maxByOrNull { it.timestamp }
    }

    fun viewPrescription(prescriptionId: String): LiveData<PrescriptionData?> {
        val prescriptionLiveData = MutableLiveData<PrescriptionData?>()

        viewModelScope.launch {
            try {
                val document = firestore.collection("prescriptions")
                    .document(prescriptionId)
                    .get()
                    .await()

                val prescription = document.toObject(PrescriptionData::class.java)
                prescriptionLiveData.postValue(prescription)
            } catch (e: Exception) {
                prescriptionLiveData.postValue(null)
            }
        }

        return prescriptionLiveData
    }

    fun getUserPrescriptions() {
        viewModelScope.launch {
            _prescriptions.value = Resource.Loading()
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                val prescriptions = firestore.collection("prescriptions")
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                    .toObjects(PrescriptionData::class.java)
                    .map { it.copy(id = it.id) }

                _prescriptions.value = Resource.Success(prescriptions)
            } catch (e: Exception) {
                Log.e("CartViewModel", "Error getting prescriptions", e)
                _prescriptions.value = Resource.Error(
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        "Database index is being created. Please wait a few minutes."
                    } else {
                        "Failed to load prescriptions: ${e.message}"
                    }
                )
            }
        }
    }
}