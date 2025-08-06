package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.databinding.PrescriptionThumbnailItemBinding

class PrescriptionThumbnailAdapter(
    private val onRemoveClick: (PrescriptionData) -> Unit,
    private val onImageClick: (PrescriptionData) -> Unit = {}
) : ListAdapter<PrescriptionData, PrescriptionThumbnailAdapter.PrescriptionViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrescriptionViewHolder {
        val binding = PrescriptionThumbnailItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PrescriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PrescriptionViewHolder, position: Int) {
        val prescription = getItem(position)
        holder.bind(prescription)
    }

    inner class PrescriptionViewHolder(
        private val binding: PrescriptionThumbnailItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(prescription: PrescriptionData) {
            // Load image using Glide
            Glide.with(binding.root.context)
                .load(prescription.prescriptionImageUrl)
                .placeholder(R.drawable.ic_prescription)
                .error(R.drawable.ic_empty_box)
                .into(binding.ivPrescription)

            // Set click listeners
            binding.ivPrescription.setOnClickListener {
                onImageClick(prescription)
            }
            binding.btnRemove.setOnClickListener {
                onRemoveClick(prescription)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PrescriptionData>() {
        override fun areItemsTheSame(oldItem: PrescriptionData, newItem: PrescriptionData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PrescriptionData, newItem: PrescriptionData): Boolean {
            return oldItem == newItem
        }
    }
}