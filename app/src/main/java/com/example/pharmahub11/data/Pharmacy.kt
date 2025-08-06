package com.example.pharmahub11.data

data class Pharmacy(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Double? = null // in meters
)