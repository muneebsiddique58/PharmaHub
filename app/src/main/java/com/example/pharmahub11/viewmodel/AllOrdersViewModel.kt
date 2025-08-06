package com.example.pharmahub11.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AllOrdersViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _allOrders = MutableStateFlow<Resource<List<Order>>>(Resource.Unspecified())
    val allOrders = _allOrders.asStateFlow()

    init {
        getAllOrders()
    }

    fun getAllOrders() {
        viewModelScope.launch {
            _allOrders.emit(Resource.Loading())

            try {
                val orders = firestore.collection("user")
                    .document(auth.uid!!)
                    .collection("orders")
                    .orderBy("createdAt", Query.Direction.DESCENDING) // Most recent first
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        try {
                            val order = doc.toObject(Order::class.java)
                            order?.copy(orderId = doc.id) // Ensure orderId is set from document ID
                        } catch (e: Exception) {
                            Log.e("AllOrdersViewModel", "Error parsing order ${doc.id}", e)
                            null
                        }
                    }

                _allOrders.emit(Resource.Success(orders))
            } catch (e: Exception) {
                _allOrders.emit(Resource.Error(e.message ?: "Failed to load orders"))
                Log.e("AllOrdersViewModel", "Error loading orders", e)
            }
        }
    }

    // Alternative method to get orders with custom sorting
    fun getAllOrdersWithCustomSort() {
        viewModelScope.launch {
            _allOrders.emit(Resource.Loading())

            try {
                val orders = firestore.collection("user")
                    .document(auth.uid!!)
                    .collection("orders")
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        try {
                            val order = doc.toObject(Order::class.java)
                            order?.copy(orderId = doc.id)
                        } catch (e: Exception) {
                            Log.e("AllOrdersViewModel", "Error parsing order ${doc.id}", e)
                            null
                        }
                    }
                    .sortedWith(compareByDescending<Order> { it.createdAt }
                        .thenByDescending { it.date })

                _allOrders.emit(Resource.Success(orders))
            } catch (e: Exception) {
                _allOrders.emit(Resource.Error(e.message ?: "Failed to load orders"))
                Log.e("AllOrdersViewModel", "Error loading orders", e)
            }
        }
    }

    // Method to refresh orders
    fun refreshOrders() {
        getAllOrders()
    }
}