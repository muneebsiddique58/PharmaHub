package com.example.pharmahub11.fragments.shopping

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.ReviewsAdapter
import com.example.pharmahub11.adapters.StrengthAdapter
import com.example.pharmahub11.adapters.ViewPager2Images
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.databinding.FragmentProductDetailsBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.hideBottomNavigationView
import com.example.pharmahub11.viewmodel.DetailsViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class ProductDetailsFragment : Fragment() {

    private val args by navArgs<ProductDetailsFragmentArgs>()
    private lateinit var binding: FragmentProductDetailsBinding
    private val viewPagerAdapter by lazy { ViewPager2Images() }
    private val strengthAdapter by lazy { StrengthAdapter() }
    private val reviewsAdapter by lazy { ReviewsAdapter() }
    private var selectedStrength: String? = null
    private val viewModel by viewModels<DetailsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        hideBottomNavigationView()
        binding = FragmentProductDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val product = args.product.apply {
            availableQuantity = quantity
        }

        setupViewpager()
        setupStrengthRv()
        setupReviewsRv()
        setupProductAvailability(product)
        setupObservers()

        binding.imageClose.setOnClickListener {
            findNavController().navigateUp()
        }

        strengthAdapter.onItemClick = { strength ->
            selectedStrength = strength
        }

        binding.buttonAddToCart.setOnClickListener {
            handleAddToCart(product)
        }

        // Display prescription info
        binding.tvPrescriptionInfo.apply {
            text = if (product.requiresPrescription) "Prescription Required" else "No Prescription Needed"
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(
                requireContext(),
                if (product.requiresPrescription)
                    R.drawable.bg_prescription_required
                else
                    R.drawable.bg_no_prescription
            )
        }

        // Display product details
        binding.apply {
            tvProductName.text = product.name
            tvProductPrice.text = "PKR ${"%.2f".format(product.price)}"
            tvProductDescription.text = product.description
            tvDosageForm.text = product.dosageForm
            tvManufacturer.text = product.manufacturer ?: "N/A"
            tvGenericName.text = "Generic: ${product.genericName}"
            tvManufacturingDate.text = "Manufactured: ${product.manufacturingDate ?: "N/A"}"
            tvExpiryDate.text = "Expiry: ${product.expiryDate ?: "N/A"}"

            when {
                product.strengths.isEmpty() -> {
                    tvStrengthLabel.visibility = View.GONE
                    rvStrengths.visibility = View.GONE
                    tvStrengthValue.visibility = View.GONE
                }
                product.strengths.size == 1 -> {
                    tvStrengthValue.visibility = View.VISIBLE
                    tvStrengthValue.text = product.strengths.first()
                    selectedStrength = product.strengths.first()
                    rvStrengths.visibility = View.GONE
                }
                else -> {
                    tvStrengthValue.visibility = View.GONE
                    rvStrengths.visibility = View.VISIBLE
                    strengthAdapter.differ.submitList(product.strengths)
                }
            }

            viewPagerAdapter.differ.submitList(product.images)
        }

        // Load reviews for the product
        viewModel.loadProductReviews(product.id)

        // Load prescription status to check for pending prescriptions
        viewModel.loadPrescriptionStatus()
    }

    private fun setupObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.addToCart.collectLatest {
                when (it) {
                    is Resource.Loading -> binding.buttonAddToCart.startAnimation()
                    is Resource.Success -> {
                        binding.buttonAddToCart.revertAnimation()
                        binding.buttonAddToCart.setBackgroundColor(
                            ContextCompat.getColor(requireContext(), R.color.black)
                        )
                        showSnackbar("Added to cart successfully")
                    }
                    is Resource.Error -> {
                        binding.buttonAddToCart.stopAnimation()
                        showSnackbar(it.message ?: "Error adding to cart")
                    }
                    else -> Unit
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.prescriptionStatus.collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val hasPendingPrescription = resource.data ?: false
                        updateCartButtonForPrescriptionStatus(args.product, hasPendingPrescription)
                    }
                    is Resource.Error -> {
                        // Handle error if needed, but don't block cart functionality
                    }
                    else -> Unit
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.reviews.collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.reviewsProgressBar.visibility = View.VISIBLE
                        binding.cardCustomerReviews.visibility = View.GONE

                    }
                    is Resource.Success -> {
                        binding.reviewsProgressBar.visibility = View.GONE
                        resource.data?.let { reviews ->
                            if (reviews.isNotEmpty()) {
                                reviewsAdapter.differ.submitList(reviews)
                                binding.cardCustomerReviews.visibility = View.VISIBLE

                            } else {
                                // Only show "No reviews" if we actually got an empty list
                                binding.cardCustomerReviews.visibility = View.GONE

                            }
                        }
                    }
                    is Resource.Error -> {
                        binding.reviewsProgressBar.visibility = View.GONE
                        binding.cardCustomerReviews.visibility = View.GONE

                        showSnackbar(resource.message ?: "Failed to load reviews")
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun handleAddToCart(product: Product) {
        if (viewModel.isProductExpired(product)) {
            showSnackbar("Cannot add to cart: Product is expired")
            return
        }

        when {
            !isProductAvailable(product) -> {
                showSnackbar("Product is out of stock")
            }
            product.requiresPrescription -> {
                viewModel.checkPendingPrescription { hasPendingPrescription ->
                    if (hasPendingPrescription) {
                        showPrescriptionPendingDialog()
                    } else {
                        showSnackbar("Added to cart. You'll need to upload a prescription before checkout.")
                        addToCart(product)
                    }
                }
            }
            else -> {
                addToCart(product)
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showPrescriptionPendingDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Prescription Under Review")
            .setMessage("Your prescription is currently under review. Please wait for approval before adding more prescription medicines to your cart.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("View Cart") { _, _ ->
                findNavController().navigate(R.id.action_productDetailsFragment_to_cartFragment)
            }
            .show()
    }

    private fun updateCartButtonForPrescriptionStatus(product: Product, hasPendingPrescription: Boolean) {
        if (product.requiresPrescription && hasPendingPrescription) {
            binding.buttonAddToCart.apply {
                text = "Prescription Under Review"
                isEnabled = false
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))
            }
            binding.tvPrescriptionPendingStatus.apply {
                visibility = View.VISIBLE
                text = "⏳ Prescription review in progress. Please wait for approval before adding more medicines."
                setTextColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))
            }
        } else {
            setupProductAvailability(product)
            binding.tvPrescriptionPendingStatus.visibility = View.GONE
        }
    }

    private fun addToCart(product: Product) {
        val cartProduct = CartProduct(product, 1, "", selectedStrength = selectedStrength)
        viewModel.addUpdateProductInCart(cartProduct)
    }

    private fun setupProductAvailability(product: Product) {
        val isAvailable = isProductAvailable(product)
        binding.buttonAddToCart.apply {
            if (isAvailable) {
                text = "Add to Cart"
                isEnabled = true
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.g_blue))
            } else {
                text = "Out of Stock"
                isEnabled = false
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.grey_400))
            }
        }
        binding.tvAvailabilityStatus.apply {
            visibility = View.VISIBLE
            if (isAvailable) {
                text = "In Stock (${product.availableQuantity ?: product.quantity} available)"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.g_green))
            } else {
                text = "Out of Stock"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.g_red))
            }
        }
    }

    private fun isProductAvailable(product: Product): Boolean {
        return product.quantity > 0 && (product.availableQuantity == null || product.availableQuantity!! > 0)
    }

    private fun setupViewpager() {
        binding.viewPagerProductImages.adapter = viewPagerAdapter
    }

    private fun setupStrengthRv() {
        binding.rvStrengths.apply {
            adapter = strengthAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupReviewsRv() {
        binding.rvCustomerReviews.apply {
            adapter = reviewsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }
}