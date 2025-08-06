package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.pharmahub11.R
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.databinding.OrderItemBinding
import java.text.SimpleDateFormat
import java.util.*

class AllOrdersAdapter : Adapter<AllOrdersAdapter.OrdersViewHolder>() {

    inner class OrdersViewHolder(private val binding: OrderItemBinding) : ViewHolder(binding.root) {
        fun bind(order: Order) {
            binding.apply {
                // Display order ID with fallback
                tvOrderId.text = "Order #${order.orderId?.take(8) ?: "Unknown"}"

                // Format date better
                tvOrderDate.text = formatOrderDate(order.date, order.createdAt)

                // Handle order status with improved error handling
                val status = getOrderStatusByString(order.orderStatus)
                val statusColor = ContextCompat.getColor(itemView.context, status.colorRes)

                imageOrderState.setImageResource(status.iconRes)
                imageOrderState.setColorFilter(statusColor)

                // Add item count if available
                val itemCount = order.products?.size ?: 0
                if (itemCount > 0) {
                    tvOrderDate.text = "${tvOrderDate.text} • $itemCount items"
                }
            }
        }

        private fun formatOrderDate(dateString: String?, createdAt: Long?): String {
            return try {
                when {
                    !dateString.isNullOrBlank() -> dateString
                    createdAt != null -> {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        sdf.format(Date(createdAt))
                    }
                    else -> "Date not available"
                }
            } catch (e: Exception) {
                "Date not available"
            }
        }

        private fun getOrderStatusByString(statusString: String?): OrderStatus {
            if (statusString.isNullOrBlank()) return OrderStatus.ORDERED

            return try {
                // First try direct valueOf (in case it's already in correct format)
                OrderStatus.valueOf(statusString.uppercase())
            } catch (e: IllegalArgumentException) {
                try {
                    // Try to find by case-insensitive comparison with status field
                    OrderStatus.values().find {
                        it.status.equals(statusString, ignoreCase = true)
                    } ?: OrderStatus.ORDERED
                } catch (e: Exception) {
                    OrderStatus.ORDERED // Safe fallback
                }
            }
        }
    }

    private val diffUtil = object : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            // Use orderId for better comparison
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffUtil)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersViewHolder {
        return OrdersViewHolder(
            OrderItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: OrdersViewHolder, position: Int) {
        val order = differ.currentList[position]
        holder.bind(order)

        holder.itemView.setOnClickListener {
            onClick?.invoke(order)
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    var onClick: ((Order) -> Unit)? = null
}