package com.example.pharmahub11.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.Category
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.util.Resource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val firestore: FirebaseFirestore,
    private val category: Category
) : ViewModel() {

    private val _offerProducts = MutableStateFlow<Resource<List<Product>>>(Resource.Unspecified())
    val offerProducts = _offerProducts.asStateFlow()

    private val _bestProducts = MutableStateFlow<Resource<List<Product>>>(Resource.Unspecified())
    val bestProducts = _bestProducts.asStateFlow()

    init {
        fetchAllProducts()
    }

    private fun fetchAllProducts() {
        fetchOfferProducts()
        fetchBestProducts()
    }

    fun fetchOfferProducts() {
        viewModelScope.launch { _offerProducts.emit(Resource.Loading()) }

        println("DEBUG: Fetching products for category: '${category.categoryName}'")

        firestore.collection("Products")
            .whereEqualTo("category", category.categoryName)
            .whereNotEqualTo("offerPercentage", null)
            .get()
            .addOnSuccessListener { result ->
                println("DEBUG: Found ${result.size()} products for ${category.categoryName}")
                val products = result.toObjects(Product::class.java)
                viewModelScope.launch {
                    _offerProducts.emit(Resource.Success(products))
                }
            }
            .addOnFailureListener { e ->
                println("DEBUG: Error fetching ${category.categoryName}: ${e.message}")
                viewModelScope.launch {
                    _offerProducts.emit(Resource.Error(e.message.toString()))
                }
            }
    }

    fun fetchBestProducts() {
        viewModelScope.launch { _bestProducts.emit(Resource.Loading()) }

        firestore.collection("Products")
            .whereEqualTo("category", category.categoryName)
            .get()
            .addOnSuccessListener { result ->
                val products = result.toObjects(Product::class.java)
                viewModelScope.launch {
                    _bestProducts.emit(Resource.Success(products))
                }
            }
            .addOnFailureListener { e ->
                viewModelScope.launch {
                    _bestProducts.emit(Resource.Error(e.message.toString()))
                }
            }
    }

    fun refreshAllProducts() {
        fetchAllProducts()
    }
}