package com.example.pharmahub11.fragments.settings

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.adapters.BillingProductsAdapter
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.databinding.FragmentOrderDetailBinding
import com.example.pharmahub11.util.VerticalItemDecoration
import com.example.pharmahub11.util.hideBottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class OrderDetailFragment : Fragment() {
    private lateinit var binding: FragmentOrderDetailBinding
    private val billingProductsAdapter by lazy { BillingProductsAdapter() }
    private val args by navArgs<OrderDetailFragmentArgs>()
    private var orderListener: ListenerRegistration? = null
    private var currentOrderStatus: OrderStatus = OrderStatus.ORDERED
    private var cancellationDate: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrderDetailBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val order = args.order
        hideBottomNavigationView()

        // Setup RecyclerView first
        setupOrderRv()

        // Convert order.orderStatus (String) to OrderStatus enum
        currentOrderStatus = getOrderStatus(order.orderStatus)

        Log.d("OrderDetailFragment", "Initial order status: $currentOrderStatus")
        Log.d("OrderDetailFragment", "Order ID: ${order.orderId}")

        // Initialize adapter with order information IMMEDIATELY
        initializeAdapterWithOrder(order)

        // Setup UI elements
        setupOrderStatusListener(order.orderId)
        setupCancelButton(order.orderId)
        setupReviewButton(order)

        binding.apply {
            tvOrderId.text = "Order #${order.orderId}"

            // Set dynamic steps from visible statuses (excluding CANCELLED)
            val visibleStatuses = OrderStatus.getCustomerVisibleStatuses()
                .filter { it != OrderStatus.CANCELLED }
            stepView.setSteps(visibleStatuses.map { it.status })

            // Handle cancelled orders differently
            if (currentOrderStatus == OrderStatus.CANCELLED) {
                showCancelledOrderUI()
            } else {
                showNormalOrderUI(visibleStatuses)
            }

            // Fixed address display - handle the proper address structure
            tvFullName.text = order.address.fullName

            // Build complete address string with proper formatting
            val addressParts = mutableListOf<String>()

            // Add street if not empty
            if (!order.address.street.isNullOrBlank()) {
                addressParts.add(order.address.street.trim())
            }

            // Add addressTitle if not empty (like "Gulberg")
            if (!order.address.addressTitle.isNullOrBlank()) {
                addressParts.add(order.address.addressTitle.trim())
            }

            // Add city if not empty
            if (!order.address.city.isNullOrBlank()) {
                addressParts.add(order.address.city.trim())
            }

            // Add state if not empty
            if (!order.address.state.isNullOrBlank()) {
                addressParts.add(order.address.state.trim())
            }

            // Join all parts with comma and space
            tvAddress.text = addressParts.joinToString(", ")

            tvPhoneNumber.text = order.address.phone
            tvTotalPrice.text = "Rs ${order.totalPrice}"
        }
    }

    private fun initializeAdapterWithOrder(order: Order) {
        // Set adapter properties FIRST
        billingProductsAdapter.currentOrderStatus = currentOrderStatus
        billingProductsAdapter.currentOrderId = order.orderId

        Log.d("OrderDetailFragment", "Initializing adapter - Status: $currentOrderStatus, Order ID: ${order.orderId}")

        // Then submit the list
        billingProductsAdapter.differ.submitList(order.products) {
            Log.d("OrderDetailFragment", "Products list submitted successfully")
            // Force a refresh after submit completes
            billingProductsAdapter.notifyDataSetChanged()
        }
    }

    private fun setupCancelButton(orderId: String) {
        binding.btnCancelOrder.setOnClickListener {
            showCancelConfirmationDialog(orderId)
        }
    }

    private fun setupReviewButton(order: Order) {
        billingProductsAdapter.setOnReviewClickListener { cartProduct ->
            val action = OrderDetailFragmentDirections.actionOrderDetailFragmentToAddReviewFragment(
                orderId = order.orderId,
                productId = cartProduct.product.id
            )
            findNavController().navigate(action)
        }
    }

    private fun showCancelConfirmationDialog(orderId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                cancelOrder(orderId)
            }
            .setNegativeButton("No, Keep Order") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun cancelOrder(orderId: String) {
        // Show loading state
        binding.btnCancelOrder.isEnabled = false
        binding.btnCancelOrder.text = "Cancelling..."

        // Add cancellation timestamp
        val cancellationTimestamp = System.currentTimeMillis()
        val updates = hashMapOf<String, Any>(
            "orderStatus" to OrderStatus.CANCELLED.name,
            "cancellationDate" to cancellationTimestamp
        )

        FirebaseFirestore.getInstance()
            .collection("orders")
            .document(orderId)
            .update(updates)
            .addOnSuccessListener {
                showSuccessMessage("Order cancelled successfully")
                cancellationDate = formatTimestamp(cancellationTimestamp)
                currentOrderStatus = OrderStatus.CANCELLED
                updateAdapterStatus() // Update adapter immediately
                showCancelledOrderUI()
            }
            .addOnFailureListener { exception ->
                Log.e("OrderDetail", "Error cancelling order", exception)
                showErrorMessage("Failed to cancel order. Please try again.")
                binding.btnCancelOrder.isEnabled = true
                binding.btnCancelOrder.text = "Cancel Order"
            }
    }

    private fun showSuccessMessage(message: String) {
        try {
            val coordinatorLayout = binding.root.findViewById<CoordinatorLayout>(com.example.pharmahub11.R.id.coordinatorLayout)
            if (coordinatorLayout != null) {
                Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showErrorMessage(message: String) {
        try {
            val coordinatorLayout = binding.root.findViewById<CoordinatorLayout>(com.example.pharmahub11.R.id.coordinatorLayout)
            if (coordinatorLayout != null) {
                val snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG)
                snackbar.setBackgroundTint(resources.getColor(android.R.color.holo_red_dark, null))
                snackbar.show()
            } else {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCancelledOrderUI() {
        binding.apply {
            linearStepView.visibility = View.GONE
            btnCancelOrder.visibility = View.GONE
            btnReviewOrder.visibility = View.GONE
            layoutCancelledOrder.visibility = View.VISIBLE
            tvCancelledStatus.text = "This order has been cancelled"
            val dateToShow = cancellationDate ?: getCurrentDate()
            tvCancelledDate.text = "Cancelled on: $dateToShow"
        }
    }

    private fun showNormalOrderUI(visibleStatuses: List<OrderStatus>) {
        binding.apply {
            linearStepView.visibility = View.VISIBLE
            layoutCancelledOrder.visibility = View.GONE
            val currentIndex = visibleStatuses.indexOf(currentOrderStatus).takeIf { it >= 0 } ?: 0
            stepView.go(currentIndex, false)
            when {
                currentIndex == visibleStatuses.lastIndex -> {
                    stepView.done(true)
                    btnCancelOrder.visibility = View.GONE
                    // Show review button only when order is delivered
                    btnReviewOrder.visibility = if (currentOrderStatus == OrderStatus.DELIVERED) View.VISIBLE else View.GONE
                }
                canCancelOrder(currentOrderStatus) -> {
                    btnCancelOrder.visibility = View.VISIBLE
                    btnCancelOrder.isEnabled = true
                    btnCancelOrder.text = "Cancel Order"
                    btnReviewOrder.visibility = View.GONE
                }
                else -> {
                    btnCancelOrder.visibility = View.GONE
                    btnReviewOrder.visibility = View.GONE
                }
            }
        }
    }

    private fun canCancelOrder(status: OrderStatus): Boolean {
        return when (status) {
            OrderStatus.ORDERED -> true
            OrderStatus.PROCESSING -> true
            OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED -> false
            else -> false
        }
    }

    private fun setupOrderStatusListener(orderId: String) {
        orderListener = FirebaseFirestore.getInstance()
            .collection("orders")
            .document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderDetail", "Listen failed.", error)
                    showErrorMessage("Failed to sync order status")
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val newStatus = doc.getString("orderStatus") ?: return@let
                    val cancellationTimestamp = doc.getLong("cancellationDate")
                    if (cancellationTimestamp != null) {
                        cancellationDate = formatTimestamp(cancellationTimestamp)
                        Log.d("OrderDetail", "Cancellation date found: $cancellationDate")
                    }
                    updateOrderStatus(newStatus)
                }
            }
    }

    private fun updateOrderStatus(newStatus: String) {
        val newOrderStatus = getOrderStatus(newStatus)
        Log.d("OrderDetailFragment", "Order status changed from $currentOrderStatus to $newOrderStatus")

        if (currentOrderStatus != newOrderStatus) {
            currentOrderStatus = newOrderStatus
            updateAdapterStatus()

            if (currentOrderStatus == OrderStatus.CANCELLED) {
                showCancelledOrderUI()
            } else {
                val visibleStatuses = OrderStatus.getCustomerVisibleStatuses()
                    .filter { it != OrderStatus.CANCELLED }
                showNormalOrderUI(visibleStatuses)
            }
        }
    }

    private fun updateAdapterStatus() {
        Log.d("OrderDetailFragment", "Updating adapter with status: $currentOrderStatus, orderId: ${args.order.orderId}")
        billingProductsAdapter.currentOrderStatus = currentOrderStatus
        billingProductsAdapter.currentOrderId = args.order.orderId
        // Force immediate update
        billingProductsAdapter.notifyDataSetChanged()
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        orderListener?.remove()
    }

    private fun setupOrderRv() {
        binding.rvProducts.apply {
            adapter = billingProductsAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            addItemDecoration(VerticalItemDecoration())
        }
    }

    private fun getOrderStatus(status: String): OrderStatus {
        return try {
            OrderStatus.valueOf(status.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.w("OrderDetailFragment", "Invalid order status: $status, defaulting to ORDERED")
            OrderStatus.ORDERED
        }
    }
}