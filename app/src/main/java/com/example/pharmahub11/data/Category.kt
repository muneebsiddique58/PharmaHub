package com.example.pharmahub11.data



enum class Category(val categoryName: String) {
    OVER_THE_COUNTER("Over The Counter"),
    PRESCRIPTION("Prescription"),
    VITAMINS("Vitamins"),
    FIRST_AID("First Aid"),
    WELLNESS("Wellness");

    companion object {
        fun fromString(value: String): Category {
            return values().firstOrNull { it.categoryName.equals(value, true) }
                ?: OVER_THE_COUNTER
        }
    }
}