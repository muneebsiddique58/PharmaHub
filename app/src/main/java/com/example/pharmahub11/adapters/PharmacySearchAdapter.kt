package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.data.Pharmacy

class PharmacySearchAdapter(
    private val onItemClick: (Pharmacy) -> Unit
) : ListAdapter<Pharmacy, PharmacySearchAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvPharmacyName)
        private val tvAddress: TextView = view.findViewById(R.id.tvPharmacyAddress)

        fun bind(pharmacy: Pharmacy) {
            tvName.text = pharmacy.name
            // Address would come from your data model
            itemView.setOnClickListener { onItemClick(pharmacy) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Pharmacy>() {
        override fun areItemsTheSame(oldItem: Pharmacy, newItem: Pharmacy) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: Pharmacy, newItem: Pharmacy) = oldItem == newItem
    }
}