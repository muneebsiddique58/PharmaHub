package com.example.pharmahub11.data

import androidx.navigation.NavArgs

data class PrescriptionFragmentArgs(
    val returnToCheckout: Boolean = false,
    val productIds: Array<String> = emptyArray()
) : NavArgs