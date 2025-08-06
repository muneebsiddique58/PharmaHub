package com.example.pharmahub11

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pharmahub11.databinding.ActivityFcmTokenBinding
import com.google.firebase.messaging.FirebaseMessaging

class FcmTokenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFcmTokenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFcmTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFetchToken.setOnClickListener {
            fetchFcmToken()
        }

        binding.btnCopyToken.setOnClickListener {
            copyTokenToClipboard()
        }

        binding.btnShareToken.setOnClickListener {
            shareToken()
        }
    }

    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                binding.tvToken.text = "Failed to get token: ${task.exception?.message}"
                return@addOnCompleteListener
            }
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM_TOKEN", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d("FCM_TOKEN", "Current token: $token")
                // Send token to your server if needed
            }

            val token = task.result
            binding.tvToken.text = token
            Toast.makeText(this, "Token fetched successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyTokenToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("FCM Token", binding.tvToken.text.toString())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareToken() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, binding.tvToken.text.toString())
        startActivity(Intent.createChooser(shareIntent, "Share FCM Token"))
    }
}