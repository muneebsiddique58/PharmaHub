package com.example.pharmahub11.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.pharmahub11.adapters.AllOrdersAdapter
import com.example.pharmahub11.databinding.FragmentOrdersBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.viewmodel.AllOrdersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class AllOrdersFragment : Fragment() {
    private lateinit var binding: FragmentOrdersBinding
    private val viewModel by viewModels<AllOrdersViewModel>()
    private val ordersAdapter by lazy { AllOrdersAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrdersBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOrdersRv()
        setupClickListeners()
        observeOrders()
    }

    private fun setupClickListeners() {
        // Handle close button click
        binding.imageCloseOrders.setOnClickListener {
            if (isAdded) {
                findNavController().navigateUp()
            }
        }

        // Handle order item clicks
        ordersAdapter.onClick = { order ->
            if (isAdded) {
                try {
                    findNavController().navigate(
                        AllOrdersFragmentDirections.actionOrdersFragmentToOrderDetailFragment(order)
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Unable to open order details",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun observeOrders() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.allOrders.collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        showLoading(true)
                        hideEmptyState()
                    }
                    is Resource.Success -> {
                        showLoading(false)
                        resource.data?.let { orders ->
                            if (orders.isEmpty()) {
                                showEmptyState()
                            } else {
                                hideEmptyState()
                                ordersAdapter.differ.submitList(orders)
                            }
                        } ?: showEmptyState()
                    }
                    is Resource.Error -> {
                        showLoading(false)
                        hideEmptyState()
                        if (isAdded) {
                            val message = resource.message ?: "Failed to load orders"
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

                            // Show empty state if no data is currently displayed
                            if (ordersAdapter.differ.currentList.isEmpty()) {
                                showEmptyState()
                            }
                        }
                    }
                    is Resource.Unspecified -> {
                        // Initial state - do nothing or show placeholder
                    }
                    else -> {
                        // Handle any other cases
                    }
                }
            }
        }
    }

    private fun setupOrdersRv() {
        binding.rvAllOrders.apply {
            adapter = ordersAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)

            // Add item decoration for better spacing
            addItemDecoration(
                androidx.recyclerview.widget.DividerItemDecoration(
                    requireContext(),
                    androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressbarAllOrders.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.tvEmptyOrders.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.tvEmptyOrders.visibility = View.GONE
    }

    // Optional: Add refresh functionality if you have SwipeRefreshLayout
    private fun setupSwipeRefresh() {
        // Add this if you want pull-to-refresh functionality
        // You would need to add SwipeRefreshLayout to your XML layout
        /*
        binding.swipeRefreshLayout?.setOnRefreshListener {
            viewModel.refreshOrders()
        }
        */
    }
}