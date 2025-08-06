package com.example.pharmahub11.fragments.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.NotificationHelper
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.BestProductsAdapter
import com.example.pharmahub11.databinding.FragmentBaseCategoryBinding
import com.example.pharmahub11.util.showBottomNavigationView
import com.google.android.material.snackbar.Snackbar

open class BaseCategoryFragment: Fragment(R.layout.fragment_base_category) {
    private lateinit var binding: FragmentBaseCategoryBinding
    protected val offerAdapter: BestProductsAdapter by lazy { BestProductsAdapter() }
    protected val bestProductsAdapter: BestProductsAdapter by lazy { BestProductsAdapter() }
    private lateinit var notificationHelper: NotificationHelper

    // Flags to track if notifications have been shown
    private var hasShownOfferNotification = false
    private var hasShownBestProductsNotification = false
    private var hasShownWelcomeNotification = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentBaseCategoryBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize NotificationHelper
        notificationHelper = NotificationHelper(requireContext())

        setupOfferRv()
        setupBestProductsRv()

        bestProductsAdapter.onClick = {
            val b = Bundle().apply { putParcelable("product", it) }
            findNavController().navigate(R.id.action_homeFragment_to_productDetailsFragment, b)

            // Show product viewed notification (this can be shown multiple times for different products)
            showProductNotification(it.name, "You viewed ${it.name}. Check out similar products!")
        }

        offerAdapter.onClick = {
            val b = Bundle().apply { putParcelable("product", it) }
            findNavController().navigate(R.id.action_homeFragment_to_productDetailsFragment, b)

            // Show special offer notification (this can be shown multiple times for different products)
            showProductNotification(it.name, "Special offer on ${it.name}! Limited time only!")
        }

        binding.rvOfferProducts.addOnScrollListener(object : RecyclerView.OnScrollListener(){
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!recyclerView.canScrollVertically(1) && dx != 0){
                    onOfferPagingRequest()
                }
            }
        })

        binding.nestedScrollBaseCategory.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener{ v, _, scrollY, _, _ ->
            if (v.getChildAt(0).bottom <= v.height + scrollY){
                onBestProductsPagingRequest()
            }
        })
    }

    private fun showProductNotification(productName: String, message: String) {
        // Use CoordinatorLayout as the parent for Snackbar
        val coordinatorLayout = binding.coordinatorLayout

        try {
            val snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG)
            snackbar.setAction("OK") { snackbar.dismiss() }
            snackbar.show()
        } catch (e: Exception) {
            // Fallback to Toast if Snackbar fails
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    fun showOfferLoading(){
        binding.offerProductsProgressBar.visibility = View.VISIBLE
    }

    fun hideOfferLoading(){
        binding.offerProductsProgressBar.visibility = View.GONE
    }

    fun showBestProductsLoading(){
        binding.bestProductsProgressBar.visibility = View.VISIBLE
    }

    fun hideBestProductsLoading(){
        binding.bestProductsProgressBar.visibility = View.GONE
    }

    open fun onOfferPagingRequest(){
        // Show notification only once when new offers are loaded
        if (!hasShownOfferNotification) {
            showProductNotification("New Offers", "Check out our latest offers!")
            hasShownOfferNotification = true
        }
    }

    open fun onBestProductsPagingRequest(){
        // Show notification only once when new best products are loaded
        if (!hasShownBestProductsNotification) {
            showProductNotification("Popular Products", "Discover our best-selling products!")
            hasShownBestProductsNotification = true
        }
    }

    private fun setupBestProductsRv() {
        binding.rvBestProducts.apply {
            layoutManager =
                GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false)
            adapter = bestProductsAdapter
        }
    }

    private fun setupOfferRv() {
        binding.rvOfferProducts.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = offerAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        showBottomNavigationView()

        // Show welcome back notification only once per session
        if (!hasShownWelcomeNotification) {
            showProductNotification("Welcome back", "Discover new products added just for you!")
            hasShownWelcomeNotification = true
        }
    }

    // Optional: Reset flags when fragment is destroyed (if you want notifications to show again on next visit)
    override fun onDestroyView() {
        super.onDestroyView()
        // Uncomment these lines if you want notifications to reset when fragment is recreated
        // hasShownOfferNotification = false
        // hasShownBestProductsNotification = false
        // hasShownWelcomeNotification = false
    }
}