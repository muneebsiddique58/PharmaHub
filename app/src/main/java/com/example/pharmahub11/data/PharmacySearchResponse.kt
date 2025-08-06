package com.example.pharmahub11.data

data class PharmacySearchResponse(
    val pharmacies: List<Pharmacy>,
    val totalResults: Int
)