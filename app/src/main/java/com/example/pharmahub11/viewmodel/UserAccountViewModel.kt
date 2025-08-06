package com.example.pharmahub11.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pharmahub11.data.User
import com.example.pharmahub11.firebase.CloudinaryHelper
import com.example.pharmahub11.util.RegisterValidation
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.validateEmail
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.StorageReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserAccountViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageReference,
    private val cloudinaryHelper: CloudinaryHelper,

) : ViewModel() {

    private val _user = MutableStateFlow<Resource<User>>(Resource.Unspecified())
    val user = _user.asStateFlow()

    private val _updateInfo = MutableStateFlow<Resource<User>>(Resource.Unspecified())
    val updateInfo = _updateInfo.asStateFlow()

    init {
        getUser()
    }

    fun getUser() {
        _user.value = Resource.Loading()

        firestore.collection("user").document(auth.uid!!).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                user?.let {
                    _user.value = Resource.Success(it)
                } ?: run {
                    _user.value = Resource.Error("User data not found")
                }
            }.addOnFailureListener { e ->
                _user.value = Resource.Error(e.message ?: "Failed to fetch user data")
            }
    }

    fun updateUser(user: User, imageUri: Uri?) {
        val areInputsValid = validateEmail(user.email) is RegisterValidation.Success
                && user.firstName.trim().isNotEmpty()
                && user.lastName.trim().isNotEmpty()

        if (!areInputsValid) {
            _updateInfo.value = Resource.Error("Check your inputs")
            return
        }

        _updateInfo.value = Resource.Loading()

        if (imageUri == null) {
            saveUserInformation(user, true)
        } else {
            saveUserInformationWithNewImage(user, imageUri)
        }
    }

    private fun saveUserInformationWithNewImage(user: User, imageUri: Uri) {
        viewModelScope.launch {
            try {
                val imageUrl = cloudinaryHelper.uploadImage(imageUri)
                val updatedUser = user.copy(imagePath = imageUrl)

                firestore.collection("user").document(auth.uid!!)
                    .set(updatedUser)
                    .addOnSuccessListener {
                        _updateInfo.value = Resource.Success(updatedUser)
                    }
                    .addOnFailureListener { e ->
                        _updateInfo.value = Resource.Error("Failed to save user: ${e.message}")
                    }
            } catch (e: Exception) {
                _updateInfo.value = Resource.Error("Image upload failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun saveUserInformation(user: User, shouldRetrieveOldImage: Boolean) {
        if (shouldRetrieveOldImage) {
            firestore.collection("user").document(auth.uid!!).get()
                .addOnSuccessListener { document ->
                    val currentUser = document.toObject(User::class.java)
                    val updatedUser = user.copy(imagePath = currentUser?.imagePath ?: "")
                    saveToFirestore(updatedUser)
                }
                .addOnFailureListener { e ->
                    _updateInfo.value = Resource.Error("Failed to retrieve current user: ${e.message}")
                }
        } else {
            saveToFirestore(user)
        }
    }

    private fun saveToFirestore(user: User) {
        firestore.collection("user").document(auth.uid!!)
            .set(user)
            .addOnSuccessListener {
                _updateInfo.value = Resource.Success(user)
            }
            .addOnFailureListener { e ->
                _updateInfo.value = Resource.Error("Failed to update user: ${e.message}")
            }
    }
}