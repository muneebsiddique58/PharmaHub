package com.example.pharmahub11.firebase


import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine

@Singleton
class CloudinaryHelper @Inject constructor(private val context: Context) {

    init {
        // Initialize Cloudinary (ensure this runs only once)
        val config = mapOf(
            "cloud_name" to "dkidsf3ud",
            "api_key" to "566781526617128",
            "api_secret" to "2GQUUt4d3Qlu7h0ROGwmwtRPkrA"
        )
        MediaManager.init(context, config)
    }

    suspend fun uploadImage(uri: Uri): String = suspendCoroutine { continuation ->
        MediaManager.get().upload(uri)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<Any?, Any?>) {
                    val url = resultData["secure_url"] as? String  // Prefer `secure_url` for HTTPS
                        ?: resultData["url"] as? String
                        ?: run {
                            continuation.resumeWith(Result.failure(RuntimeException("Invalid URL response")))
                            return
                        }
                    continuation.resumeWith(Result.success(url))
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    continuation.resumeWith(Result.failure(RuntimeException("Cloudinary error: ${error.description}")))
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    continuation.resumeWith(Result.failure(RuntimeException("Upload rescheduled: ${error.description}")))
                }
            })
            .dispatch()
    }
}