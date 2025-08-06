package com.example.pharmahub11.util

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.databinding.DialogImageZoomBinding

fun Context.showImageDialog(imageUrl: String) {
    // Option 1: Using View Binding (Recommended)
    val binding = DialogImageZoomBinding.inflate(LayoutInflater.from(this))

    // Load image using Glide
    Glide.with(this)
        .load(imageUrl)
        .placeholder(R.drawable.ic_prescription)
        .error(R.drawable.ic_warning)
        .into(binding.imageView) // Make sure this matches your layout ID

    val dialog = AlertDialog.Builder(this)
        .setView(binding.root)
        .create()

    // Set click listener on the ImageView to close dialog
    binding.imageView.setOnClickListener {
        dialog.dismiss()
    }

    // Optional: Set click listener on the close button if you have one
    binding.btnClose?.setOnClickListener {
        dialog.dismiss()
    }

    dialog.show()
}

