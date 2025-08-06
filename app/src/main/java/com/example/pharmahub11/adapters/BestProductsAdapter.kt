package com.example.pharmahub11.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.databinding.ProductRvItemBinding
import com.example.pharmahub11.helper.getProductPrice

class BestProductsAdapter : RecyclerView.Adapter<BestProductsAdapter.BestProductsViewHolder>() {

    inner class BestProductsViewHolder(private val binding: ProductRvItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.apply {
                // Handle price display
                val priceAfterOffer = product.offerPercentage?.let {
                    it.getProductPrice(product.price)
                } ?: product.price

                tvNewPrice.text = "PKR ${String.format("%.2f", priceAfterOffer)}"

                if (product.offerPercentage == null) {
                    tvNewPrice.visibility = View.INVISIBLE
                    tvPrice.visibility = View.VISIBLE
                    tvPrice.text = "PKR ${product.price}"
                    tvPrice.paintFlags = 0 // Remove strike-through
                } else {
                    tvNewPrice.visibility = View.VISIBLE
                    tvPrice.visibility = View.VISIBLE
                    tvPrice.text = "PKR ${product.price}"
                    tvPrice.paintFlags = tvPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                }

                // Handle product image safely
                if (!product.images.isNullOrEmpty()) {
                    Glide.with(itemView)
                        .load(product.images[0])
                        .into(imgProduct)
                } else {
                    // Set a placeholder image if no image available
                    imgProduct.setImageResource(R.drawable.ic_cancelled)
                }

                // Handle product name safelys
                tvName.text = product.name ?: "No name available"
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BestProductsViewHolder {
        return BestProductsViewHolder(
            ProductRvItemBinding.inflate(
                LayoutInflater.from(parent.context)
            )
        )
    }

    override fun onBindViewHolder(holder: BestProductsViewHolder, position: Int) {
        val product = differ.currentList[position]
        holder.bind(product)

        holder.itemView.setOnClickListener {
            onClick?.invoke(product)
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    var onClick: ((Product) -> Unit)? = null

}