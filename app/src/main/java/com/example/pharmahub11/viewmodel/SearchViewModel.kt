package com.example.pharmahub11.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.util.Resource
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _searchResults = MutableLiveData<Resource<List<Product>>>()
    val searchResults: LiveData<Resource<List<Product>>> = _searchResults

    private var searchJob: Job? = null
    private var currentQuery = ""
    private var currentCategory: String? = null

    fun searchProducts(query: String) {
        currentQuery = query.trim()
        executeSearch()
    }

    fun setCategoryFilter(category: String?) {
        currentCategory = category
        executeSearch()
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        currentQuery = ""
        currentCategory = null
        _searchResults.value = Resource.Success(emptyList())
    }

    private fun executeSearch() {
        // Cancel previous search job
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                _searchResults.postValue(Resource.Loading())

                // Add debouncing delay for better UX
                if (currentQuery.isNotEmpty()) {
                    delay(300)
                }

                val products = if (currentQuery.isEmpty()) {
                    // If query is empty, get products by category filter or return empty
                    if (currentCategory != null) {
                        getProductsByCategory(currentCategory!!)
                    } else {
                        emptyList()
                    }
                } else {
                    // Search products with query and category filter
                    searchProductsInFirestore(currentQuery, currentCategory)
                }

                _searchResults.postValue(Resource.Success(products))

            } catch (e: Exception) {
                _searchResults.postValue(Resource.Error(e.message ?: "Search failed"))
            }
        }
    }

    private suspend fun searchProductsInFirestore(query: String, categoryFilter: String?): List<Product> {
        return try {
            // Get all products first (with category filter if applied)
            var firestoreQuery = firestore.collection("Products")
                .whereEqualTo("inStock", true) // Only show in-stock products

            // Apply category filter if provided
            categoryFilter?.let { category ->
                firestoreQuery = firestoreQuery.whereEqualTo("category", category)
            }

            val snapshot = firestoreQuery.get().await()
            val allProducts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Product::class.java)?.copy(id = doc.id)
            }

            // Filter products based on search query (client-side search)
            allProducts.filter { product ->
                searchInProduct(product, query.lowercase())
            }.sortedWith(compareBy<Product> { product ->
                // Sort by relevance: exact name match first, then starts with, then contains
                when {
                    product.name?.lowercase() == query.lowercase() -> 0
                    product.name?.lowercase()?.startsWith(query.lowercase()) == true -> 1
                    product.genericName?.lowercase() == query.lowercase() -> 2
                    product.genericName?.lowercase()?.startsWith(query.lowercase()) == true -> 3
                    else -> 4
                }
            }.thenBy { it.name }) // Then alphabetically by name

        } catch (e: Exception) {
            throw Exception("Search failed: ${e.message}")
        }
    }

    private fun searchInProduct(product: Product, query: String): Boolean {
        val searchQuery = query.lowercase()

        return listOfNotNull(
            product.name,
            product.genericName,
            product.description,
            product.manufacturer,
            product.category,
            product.dosageForm,
            product.pharmacyName,
            product.activeIngredients?.joinToString(" "),
            product.strengths?.joinToString(" ")
        ).any { field ->
            field.lowercase().contains(searchQuery)
        }
    }

    private suspend fun getProductsByCategory(category: String): List<Product> {
        return try {
            val snapshot = firestore.collection("Products")
                .whereEqualTo("category", category)
                .whereEqualTo("inStock", true)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Product::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            throw Exception("Failed to get products by category: ${e.message}")
        }
    }
}