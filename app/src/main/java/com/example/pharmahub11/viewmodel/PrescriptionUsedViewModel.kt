package com.example.pharmahub11.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.pharmahub11.data.PrescriptionData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PrescriptionUsedViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _prescriptions = MutableLiveData<List<PrescriptionData>>()
    val prescriptions: LiveData<List<PrescriptionData>> = _prescriptions

    private val _filteredPrescriptions = MutableLiveData<List<PrescriptionData>>()
    val filteredPrescriptions: LiveData<List<PrescriptionData>> = _filteredPrescriptions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var currentFilter: String = ""

    suspend fun loadPrescriptions(userId: String) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val snapshot = firestore.collection("prescriptions")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val prescriptionsList = snapshot.documents.mapNotNull { document ->
                PrescriptionData.fromFirestore(document)
            }

            println("Loaded ${prescriptionsList.size} prescriptions from Firestore") // Debug log

            // Filter to show only non-pending prescriptions by default
            val nonPendingPrescriptions = prescriptionsList.filter { prescription ->
                !prescription.isPending()
            }

            println("Non-pending prescriptions: ${nonPendingPrescriptions.size}") // Debug log

            _prescriptions.value = nonPendingPrescriptions
            filterPrescriptions(currentFilter)
        } catch (e: Exception) {
            println("Error loading prescriptions: ${e.message}") // Debug log
            _errorMessage.value = "Failed to load prescriptions: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun filterPrescriptions(status: String) {
        currentFilter = status
        _prescriptions.value?.let { allPrescriptions ->
            val filtered = when (status) {
                PrescriptionData.STATUS_USED -> allPrescriptions.filter { it.isUsed() }
                PrescriptionData.STATUS_APPROVED -> allPrescriptions.filter { it.isApproved() }
                PrescriptionData.STATUS_REJECTED -> allPrescriptions.filter { it.isRejected() }
                PrescriptionData.STATUS_CANCELLED -> allPrescriptions.filter { it.isCancelled() }
                else -> allPrescriptions // Show all non-pending prescriptions if status is empty
            }

            println("Filtering by status: '$status', found ${filtered.size} prescriptions") // Debug log
            _filteredPrescriptions.value = filtered
        }
    }
}