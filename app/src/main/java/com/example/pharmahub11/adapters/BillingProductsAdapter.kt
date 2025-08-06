package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.databinding.BillingProductsRvItemBinding

class BillingProductsAdapter : RecyclerView.Adapter<BillingProductsAdapter.BillingProductsViewHolder>() {

    var currentOrderStatus: OrderStatus = OrderStatus.ORDERED
    var currentOrderId: String = ""
    private var onReviewClickListener: ((CartProduct) -> Unit)? = null

    inner class BillingProductsViewHolder(val binding: BillingProductsRvItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    private val diffCallback = object : DiffUtil.ItemCallback<CartProduct>() {
        override fun areItemsTheSame(oldItem: CartProduct, newItem: CartProduct): Boolean {
            return oldItem.product.id == newItem.product.id
        }

        override fun areContentsTheSame(oldItem: CartProduct, newItem: CartProduct): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillingProductsViewHolder {
        return BillingProductsViewHolder(
            BillingProductsRvItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: BillingProductsViewHolder, position: Int) {
        val cartProduct = differ.currentList[position]
        val product = cartProduct.product
        holder.binding.apply {
            tvProductCartName.text = product.name
            tvProductCartPrice.text = "Rs ${product.price}"
            tvProductCartQuantity.text = "Qty: ${cartProduct.quantity}"

            // Show strength if available
            cartProduct.selectedStrength?.let { strength ->
                tvProductCartStrength.text = strength
                tvProductCartStrength.visibility = View.VISIBLE
            } ?: run {
                tvProductCartStrength.visibility = View.GONE
            }

            Glide.with(root.context)
                .load(product.images.firstOrNull())
                .placeholder(R.color.g_blue)
                .into(imgProductCart)

            // Show review button only for delivered orders
            btnReviewProduct.visibility = if (currentOrderStatus == OrderStatus.PAID) {
                View.VISIBLE
            } else {
                View.GONE
            }

            btnReviewProduct.setOnClickListener {
                onReviewClickListener?.invoke(cartProduct)
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    fun setOnReviewClickListener(listener: (CartProduct) -> Unit) {
        onReviewClickListener = listener
    }
}