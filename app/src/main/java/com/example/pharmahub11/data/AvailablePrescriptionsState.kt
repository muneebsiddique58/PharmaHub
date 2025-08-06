package com.example.pharmahub11.data

sealed class AvailablePrescriptionsState {
    object Loading : AvailablePrescriptionsState()
    data class Success(val prescriptions: List<PrescriptionData>) : AvailablePrescriptionsState()
    data class Error(val message: String) : AvailablePrescriptionsState()
}