package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.databinding.CartProductItemBinding
import com.example.pharmahub11.helper.getProductPrice

class CartProductAdapter :
    RecyclerView.Adapter<CartProductAdapter.CartProductsViewHolder>() {

    inner class CartProductsViewHolder(val binding: CartProductItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cartProduct: CartProduct) {
            binding.apply {
                // Load product image
                Glide.with(itemView.context) // Use itemView.context
                    .load(cartProduct.product.images.firstOrNull())
                    .into(imageCartProduct)

                // Set product info
                tvProductCartName.text = cartProduct.product.name
                tvCartProductQuantity.text = cartProduct.quantity.toString()

                // Calculate and display price
                val priceAfterPercentage =
                    cartProduct.product.offerPercentage.getProductPrice(cartProduct.product.price)
                tvProductCartPrice.text = "PKR ${String.format("%.2f", priceAfterPercentage)}"

                // Display strength if available
                cartProduct.selectedStrength?.let { strength ->
                    tvCartProductStrength.text = strength
                    tvCartProductStrength.visibility = View.VISIBLE
                } ?: run {
                    tvCartProductStrength.visibility = View.GONE
                }

                // Load prescription preview if available
                if (!cartProduct.prescriptionImageUrl.isNullOrEmpty()) {
                    ivPrescriptionPreview.visibility = View.VISIBLE
                    Glide.with(itemView.context) // Use itemView.context
                        .load(cartProduct.prescriptionImageUrl)
                        .placeholder(com.example.pharmahub11.R.drawable.ic_prescription) // Use your placeholder
                        .error(com.example.pharmahub11.R.drawable.ic_broken_image) // Use your error image
                        .into(ivPrescriptionPreview)
                    // Adjust constraints of other elements if needed, consider using ConstraintSet
                } else {
                    ivPrescriptionPreview.visibility = View.GONE
                }
            }
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<CartProduct>() {
        override fun areItemsTheSame(oldItem: CartProduct, newItem: CartProduct): Boolean {
            return oldItem.product.id == newItem.product.id
        }

        override fun areContentsTheSame(oldItem: CartProduct, newItem: CartProduct): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartProductsViewHolder {
        return CartProductsViewHolder(
            CartProductItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: CartProductsViewHolder, position: Int) {
        val cartProduct = differ.currentList[position]
        holder.bind(cartProduct)

        holder.itemView.setOnClickListener {
            onProductClick?.invoke(cartProduct)
        }

        holder.binding.imagePlus.setOnClickListener {
            onPlusClick?.invoke(cartProduct)
        }

        holder.binding.imageMinus.setOnClickListener {
            onMinusClick?.invoke(cartProduct)
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    var onProductClick: ((CartProduct) -> Unit)? = null
    var onPlusClick: ((CartProduct) -> Unit)? = null
    var onMinusClick: ((CartProduct) -> Unit)? = null
}
