package com.example.pharmahub11.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.Complaint
import com.example.pharmahub11.data.User
import com.example.pharmahub11.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _complaintState = MutableLiveData<Resource<String>>()
    val complaintState: LiveData<Resource<String>> = _complaintState

    private val _userData = MutableLiveData<Resource<User>>()
    val userData: LiveData<Resource<User>> = _userData

    init {
        fetchUserData()
    }

    fun fetchUserData() {
        _userData.value = Resource.Loading()

        val userId = auth.currentUser?.uid ?: run {
            _userData.value = Resource.Error("User not authenticated")
            return
        }

        viewModelScope.launch {
            try {
                val document = firestore.collection("user").document(userId).get().await()

                val user = if (document.exists()) {
                    User(
                        firstName = document.getString("firstName") ?: "",
                        lastName = document.getString("lastName") ?: "",
                        email = document.getString("email") ?: auth.currentUser?.email ?: ""
                    )
                } else {
                    User(email = auth.currentUser?.email ?: "")
                }

                _userData.value = Resource.Success(user)
            } catch (e: Exception) {
                _userData.value = Resource.Error(
                    e.message ?: "Failed to fetch user data"
                )
            }
        }
    }

    fun submitComplaint(complaintText: String) {
        if (complaintText.isBlank()) {
            _complaintState.value = Resource.Error("Complaint text cannot be empty")
            return
        }

        _complaintState.value = Resource.Loading()

        val userId = auth.currentUser?.uid ?: run {
            _complaintState.value = Resource.Error("User not authenticated")
            return
        }

        viewModelScope.launch {
            try {
                // Fetch fresh user data to ensure we have the latest name
                val userDoc = firestore.collection("user").document(userId).get().await()
                val currentUser = if (userDoc.exists()) {
                    User(
                        firstName = userDoc.getString("firstName") ?: "",
                        lastName = userDoc.getString("lastName") ?: "",
                        email = userDoc.getString("email") ?: auth.currentUser?.email ?: ""
                    )
                } else {
                    User(email = auth.currentUser?.email ?: "")
                }

                // Create document reference and get ID
                val docRef = firestore.collection("complaints").document()
                val complaintId = docRef.id

                val complaint = hashMapOf<String, Any>(
                    "id" to complaintId,
                    "userId" to userId,
                    "userEmail" to (currentUser.email.ifBlank {
                        auth.currentUser?.email ?: "unknown@example.com"
                    }),
                    "userName" to ("${currentUser.firstName} ${currentUser.lastName}".trim()
                        .takeIf { it.isNotBlank() } ?: "Anonymous User"),
                    "text" to complaintText,
                    "status" to "pending",
                    "type" to "customer_service",
                    "timestamp" to FieldValue.serverTimestamp()
                )

                docRef.set(complaint).await()
                _complaintState.value = Resource.Success(complaintId)
            } catch (e: Exception) {
                _complaintState.value = Resource.Error(
                    e.message ?: "Failed to submit complaint. Please try again."
                )
            }
        }
    }
}