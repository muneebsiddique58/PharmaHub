package com.example.pharmahub11.helper

/**
 * Calculates the product price after applying discount percentage
 * @param price The original price of the product
 * @return Price after discount (guaranteed non-negative)
 */
fun Float?.getProductPrice(price: Float): Float {
    // If no discount percentage, return original price (ensured positive)
    if (this == null) return price.coerceAtLeast(0f)

    // Ensure discount is between 0-100%
    val validDiscount = this.coerceIn(0f, 100f)

    // Calculate price after discount (ensured non-negative)
    return (price.coerceAtLeast(0f) * (1 - validDiscount / 100f)).coerceAtLeast(0f)
}

/**
 * Extension function to format price as PKR
 */
fun Float.toPkrString(): String = "PKR ${"%.2f".format(this.coerceAtLeast(0f))}"

/**
 * Extension function to get discounted price formatted in PKR
 */
fun Float?.getProductPriceFormatted(originalPrice: Float): String {
    return this.getProductPrice(originalPrice).toPkrString()
}