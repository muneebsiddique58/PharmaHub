package com.example.pharmahub11.adapters

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.databinding.StrengthRvItemBinding

class StrengthAdapter : RecyclerView.Adapter<StrengthAdapter.StrengthViewHolder>() {

    private var selectedPosition = -1

    inner class StrengthViewHolder(private val binding: StrengthRvItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(strength: String, position: Int) {
            binding.tvStrength.text = strength

            val context = binding.root.context
            if (position == selectedPosition) {
                binding.tvStrength.setBackgroundResource(R.drawable.shape_selected_strength)
                binding.tvStrength.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.tvStrength.setBackgroundResource(R.drawable.shape_default_strength)
                binding.tvStrength.setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            }
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StrengthViewHolder {
        return StrengthViewHolder(
            StrengthRvItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: StrengthViewHolder, position: Int) {
        val strength = differ.currentList[position]
        holder.bind(strength, position)

        holder.itemView.setOnClickListener {
            if (selectedPosition >= 0) notifyItemChanged(selectedPosition)
            selectedPosition = holder.adapterPosition
            notifyItemChanged(selectedPosition)
            onItemClick?.invoke(strength)
        }
    }

    override fun getItemCount(): Int = differ.currentList.size

    var onItemClick: ((String) -> Unit)? = null
}