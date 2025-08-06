package com.example.pharmahub11.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.OrderAdapter
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.databinding.FragmentOrderListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class OrderListFragment : Fragment() {
    private var _binding: FragmentOrderListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter
    private val orders = mutableListOf<Order>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadOrders()
        setupBackButton()
    }

    private fun setupRecyclerView() {
        adapter = OrderAdapter(orders) { order ->
            try {
                // Get the first product (if available) to extract pharmacy info
                val firstProduct = order.products?.firstOrNull()

                val action = OrderListFragmentDirections
                    .actionOrderListFragmentToOrderSupportChatFragment3(
                        orderId = order.orderId ?: "", // Required string parameter
                        pharmacyName = firstProduct?.pharmacyName ?: "", // Required string parameter
                        pharmacistId = firstProduct?.pharmacistId ?: "" // Required string parameter
                    )
                findNavController().navigate(action)
            } catch (e: Exception) {
                showErrorMessage("Navigation error: ${e.message}")
            }
        }

        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@OrderListFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun loadOrders() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            showErrorMessage("User not authenticated")
            return
        }

        showLoading(true)

        FirebaseFirestore.getInstance()
            .collection("orders")
            .whereEqualTo("userId", currentUserId)
            .orderBy("createdAt", Query.Direction.DESCENDING) // Changed to use timestamp
            .addSnapshotListener { snapshots, error ->
                showLoading(false)

                error?.let {
                    showErrorMessage("Error loading orders: ${it.message}")
                    return@addSnapshotListener
                }

                val newOrders = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Order::class.java)?.copy(
                            orderId = doc.id // Using document ID as fallback
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                orders.apply {
                    clear()
                    addAll(newOrders)
                }
                adapter.notifyDataSetChanged()
                showEmptyState(orders.isEmpty())
            }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        binding.emptyStateLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvOrders.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showErrorMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}