package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.data.order.Order
import java.text.SimpleDateFormat
import java.util.*

class OrderAdapter(
    private val orders: List<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view, onOrderClick)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount() = orders.size

    inner class OrderViewHolder(
        itemView: View,
        private val onOrderClick: (Order) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvOrderId: TextView = itemView.findViewById(R.id.tvOrderId)
        private val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        private val tvOrderStatus: TextView = itemView.findViewById(R.id.tvOrderStatus)
        private val tvTotalPrice: TextView = itemView.findViewById(R.id.tvTotalPrice)
        private val tvPharmacyName: TextView = itemView.findViewById(R.id.tvPharmacyName)

        fun bind(order: Order) {
            tvOrderId.text = itemView.context.getString(R.string.order_number, order.orderId?.takeLast(6) ?: "------")
            tvOrderDate.text = formatDate(order.date ?: "")
            tvOrderStatus.text = order.orderStatus ?: "Unknown"
            tvTotalPrice.text = itemView.context.getString(R.string.price_format, order.totalPrice ?: 0.0)

            // Display first pharmacy name if available
            order.products?.firstOrNull()?.pharmacyName?.let {
                tvPharmacyName.text = it
                tvPharmacyName.visibility = View.VISIBLE
            } ?: run {
                tvPharmacyName.visibility = View.GONE
            }

            itemView.setOnClickListener { onOrderClick(order) }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = if (dateString.contains(" ")) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                }
                val date = inputFormat.parse(dateString) ?: return dateString
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                dateString
            }
        }
    }
}