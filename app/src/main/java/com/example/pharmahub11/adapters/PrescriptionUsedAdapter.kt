package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.databinding.ItemUsedPrescriptionBinding

class PrescriptionUsedAdapter(
    private var prescriptions: List<PrescriptionData>,
    private val onDetailsClick: (PrescriptionData) -> Unit,
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<PrescriptionUsedAdapter.PrescriptionViewHolder>() {

    inner class PrescriptionViewHolder(private val binding: ItemUsedPrescriptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(prescription: PrescriptionData) = with(binding) {
            Glide.with(root.context)
                .load(prescription.prescriptionImageUrl)
                .placeholder(R.drawable.ic_prescription)
                .error(R.drawable.ic_warning) // Add error placeholder
                .into(imgPrescription)

            val productName = prescription.products.firstOrNull()?.name ?: "Multiple Products"
            tvProductName.text = productName
            tvStatus.text = prescription.status.replaceFirstChar { it.uppercase() }
            tvDate.text = prescription.formattedDateTime

            when {
                prescription.isUsed() -> tvStatus.setBackgroundResource(R.drawable.bg_status_used)
                prescription.isApproved() -> tvStatus.setBackgroundResource(R.drawable.bg_status_accepted)
                prescription.isRejected() -> tvStatus.setBackgroundResource(R.drawable.bg_status_rejected)
                prescription.isCancelled() -> tvStatus.setBackgroundResource(R.drawable.bg_status_cancelled)
                else -> tvStatus.setBackgroundResource(R.drawable.bg_status_default)
            }

            // Add null check for image URL
            imgPrescription.setOnClickListener {
                val imageUrl = prescription.prescriptionImageUrl
                if (!imageUrl.isNullOrEmpty()) {
                    onImageClick(imageUrl)
                } else {
                    // Handle case where image URL is null or empty
                    println("Image URL is null or empty")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrescriptionViewHolder {
        val binding = ItemUsedPrescriptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PrescriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PrescriptionViewHolder, position: Int) {
        holder.bind(prescriptions[position])
    }

    override fun getItemCount(): Int = prescriptions.size

    fun updatePrescriptions(newPrescriptions: List<PrescriptionData>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = prescriptions.size
            override fun getNewListSize() = newPrescriptions.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                prescriptions[oldItemPosition].id == newPrescriptions[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                prescriptions[oldItemPosition] == newPrescriptions[newItemPosition]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        prescriptions = newPrescriptions
        diffResult.dispatchUpdatesTo(this)
    }
}