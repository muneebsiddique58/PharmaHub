package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.databinding.BestDealsRvItemBinding

class BestDealsAdapter : RecyclerView.Adapter<BestDealsAdapter.BestDealsViewHolder>() {

    inner class BestDealsViewHolder(private val binding: BestDealsRvItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.apply {
                // Safely load product image
                if (product.images.isNotEmpty()) {
                    Glide.with(itemView)
                        .load(product.images[0])
                        .into(imgBestDeal)
                }

                // Always show non-negative original price
                val originalPrice = product.price.coerceAtLeast(0f)
                tvOldPrice.text = "PKR ${"%.2f".format(originalPrice)}"

                // Handle discounted price
                product.offerPercentage?.let { discount ->
                    val validDiscount = discount.coerceIn(0f, 100f)
                    val discountedPrice = (originalPrice * (1 - validDiscount / 100f)).coerceAtLeast(0f)
                    tvNewPrice.text = "PKR ${"%.2f".format(discountedPrice)}"
                    tvNewPrice.visibility = View.VISIBLE
                } ?: run {
                    tvNewPrice.visibility = View.GONE
                }

                tvDealProductName.text = product.name
            }
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BestDealsViewHolder {
        return BestDealsViewHolder(
            BestDealsRvItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: BestDealsViewHolder, position: Int) {
        val product = differ.currentList[position]
        holder.bind(product)

        holder.itemView.setOnClickListener {
            onClick?.invoke(product)
        }
    }

    override fun getItemCount(): Int = differ.currentList.size

    var onClick: ((Product) -> Unit)? = null
}