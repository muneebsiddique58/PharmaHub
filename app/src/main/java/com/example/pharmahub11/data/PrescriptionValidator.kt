package com.example.pharmahub11.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class to validate prescription images before upload
 */
@Singleton
class PrescriptionValidator @Inject constructor(
    private val context: Context
) {
    /**
     * Result class that represents the validation outcome
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Validates a prescription image from a URI
     *
     * @param imageUri The URI of the prescription image
     * @return ValidationResult indicating if the image is valid or not
     */
    suspend fun validatePrescriptionImage(imageUri: Uri): ValidationResult = withContext(Dispatchers.IO) {
        try {
            // Get input stream from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext ValidationResult.Invalid("Cannot open image")

            // Decode image dimensions without loading full bitmap to memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Check image dimensions
            if (options.outWidth < MIN_WIDTH || options.outHeight < MIN_HEIGHT) {
                return@withContext ValidationResult.Invalid("Image resolution too low. Please upload a clearer image.")
            }

            // Check file size
            val fileSize = getFileSizeFromUri(imageUri)
            if (fileSize <= 0 || fileSize < MIN_FILE_SIZE) {
                return@withContext ValidationResult.Invalid("File size too small. Please upload a proper prescription image.")
            }

            // For a real app, consider implementing:
            // 1. OCR to verify it contains doctor details, patient name, etc.
            // 2. ML-based verification to confirm it's a prescription
            // 3. Check for expiry date

            ValidationResult.Valid
        } catch (e: IOException) {
            ValidationResult.Invalid("Error reading image: ${e.message}")
        } catch (e: Exception) {
            ValidationResult.Invalid("Validation failed: ${e.message}")
        }
    }

    /**
     * Gets the file size from a URI
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val size = inputStream?.available()?.toLong() ?: 0
            inputStream?.close()
            size
        } catch (e: Exception) {
            -1
        }
    }

    companion object {
        private const val MIN_WIDTH = 800
        private const val MIN_HEIGHT = 800
        private const val MIN_FILE_SIZE = 20 * 1024 // 20KB
    }
}