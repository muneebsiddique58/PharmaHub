package com.example.pharmahub11.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PaymentMethod(
    val id: String,
    val type: PaymentType, // ✅ Use the enum here instead of String
    val name: String,
    val info: String,
    val iconRes: Int,
    val isEnabled: Boolean = true,
    val note: String? = null
) : Parcelable
