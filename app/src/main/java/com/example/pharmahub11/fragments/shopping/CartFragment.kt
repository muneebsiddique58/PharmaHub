package com.example.pharmahub11.fragments.shopping

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.CartProductAdapter
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.databinding.FragmentCartBinding
import com.example.pharmahub11.firebase.FirebaseCommon
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.VerticalItemDecoration
import com.example.pharmahub11.viewmodel.CartViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class CartFragment : Fragment(R.layout.fragment_cart) {
    private lateinit var binding: FragmentCartBinding
    private val cartAdapter by lazy { CartProductAdapter() }
    private val viewModel by activityViewModels<CartViewModel>()
    private var totalPrice = 0f
    private var hasPrescriptionItems = false
    private var prescriptionItems = emptyList<CartProduct>()
    private var shouldAutoCheckout = false
    private val args: CartFragmentArgs by navArgs()

    companion object {
        private const val PRESCRIPTION_EXPIRY_DAYS = 7L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCartBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCartRv()
        setupObservers()
        setupClickListeners()

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Map<String, Any>>(
            "prescriptionUploaded"
        )?.observe(viewLifecycleOwner) { data ->
            val imageUrl = data["imageUrl"] as String
            val prescriptionId = data["prescriptionId"] as String
            val productDetails = data["productDetails"] as? List<PrescriptionData.ProductInfo>

            showPrescriptionStatus(imageUrl, productDetails)
            viewModel.loadPrescriptions()
        }

        shouldAutoCheckout = args.returnToCheckout
        binding.imageCloseCart.setOnClickListener { findNavController().navigateUp() }
        viewModel.loadPrescriptions()
    }

    private fun setupCartRv() {
        binding.rvCart.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            adapter = cartAdapter
            addItemDecoration(VerticalItemDecoration())
        }
    }

    private fun setupCartProducts() {
        val cartItems = viewModel.cartProducts.value?.data

        if (cartItems.isNullOrEmpty()) {
            showEmptyCart()
            cartAdapter.differ.submitList(emptyList())
            return
        }

        hideEmptyCart()
        cartAdapter.differ.submitList(cartItems)

        // Filter prescription items
        prescriptionItems = cartAdapter.differ.currentList.filter { cartProduct ->
            requiresPrescription(cartProduct.product)
        }
        hasPrescriptionItems = prescriptionItems.isNotEmpty()

        // Update UI based on prescription requirements
        updatePrescriptionUI()
    }

    private fun requiresPrescription(product: Product): Boolean {
        return product.requiresPrescription ||
                product.category.equals("prescription", ignoreCase = true)
    }

    private fun updatePrescriptionUI() {
        if (!hasPrescriptionItems) {
            binding.layoutPrescriptionStatus.visibility = View.GONE
            binding.layoutPrescriptionLock.visibility = View.GONE
            return
        }

        val prescriptions = viewModel.prescriptions.value?.data
        if (prescriptions.isNullOrEmpty()) {
            showNewPrescriptionRequired()
            return
        }

        val currentPrescription = getCurrentValidPrescription(prescriptions)
        if (currentPrescription == null) {
            showNewPrescriptionRequired()
            return
        }

        when (currentPrescription.status) {
            PrescriptionData.STATUS_PENDING -> {
                if (currentPrescription.usedInOrder == null) {
                    showPrescriptionPending(currentPrescription)
                } else {
                    showNewPrescriptionRequired()
                }
            }
            PrescriptionData.STATUS_APPROVED -> {
                if (isPrescriptionExpired(currentPrescription)) {
                    showPrescriptionExpired(currentPrescription)
                } else {
                    showPrescriptionApproved(currentPrescription)
                }
            }
            PrescriptionData.STATUS_REJECTED -> {
                showPrescriptionRejected(currentPrescription)
            }
            else -> showNewPrescriptionRequired()
        }
    }

    private fun getCurrentValidPrescription(prescriptions: List<PrescriptionData>): PrescriptionData? {
        return prescriptions
            .filter { prescription ->
                // Get the most recent prescription that covers current cart items
                prescriptionItems.any { cartItem ->
                    cartItem.product.id in prescription.productIds
                }
            }
            .maxByOrNull { it.timestamp }
    }

    private fun isPrescriptionExpired(prescription: PrescriptionData): Boolean {
        val now = Date()
        val prescriptionDate = Date(prescription.timestamp)
        val daysDiff = TimeUnit.MILLISECONDS.toDays(now.time - prescriptionDate.time)
        return daysDiff > PRESCRIPTION_EXPIRY_DAYS
    }

    private fun showNewPrescriptionRequired() {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.layoutPrescriptionLock.visibility = View.GONE

        binding.tvPrescriptionStatus.text = "New prescription required - Previous prescription cannot be reused"
        binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))

        binding.tvPrescriptionProducts.text = "Each order requires current, approved prescription"
        binding.tvPrescriptionProducts.visibility = View.VISIBLE

        binding.ivPrescriptionPreview.visibility = View.GONE
        binding.buttonCheckout.isEnabled = false
        binding.buttonCheckout.text = "Upload Prescription Required"

        // Show upload button
        binding.btnUploadNewPrescription.visibility = View.VISIBLE
        binding.btnUploadNewPrescription.setOnClickListener {
            navigateToPrescriptionUpload()
        }
    }

    private fun showPrescriptionPending(prescription: PrescriptionData) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.layoutPrescriptionLock.visibility = View.VISIBLE

        binding.tvPrescriptionStatus.text = "⏳ Prescription uploaded - pending review"
        binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))

        // Show upload timestamp - Fixed: Use DateFormat instead of android.text.format.DateFormat
        val uploadDate = Date(prescription.timestamp)
        binding.tvPrescriptionProducts.text = "Uploaded on: ${DateFormat.getDateTimeInstance().format(uploadDate)}"
        binding.tvPrescriptionProducts.visibility = View.VISIBLE

        // Show prescription image
        binding.ivPrescriptionPreview.visibility = View.VISIBLE
        Glide.with(this).load(prescription.prescriptionImageUrl).into(binding.ivPrescriptionPreview)

        // Show lock message
        binding.tvPrescriptionLockMessage.text = "⏳ Prescription under review. Cannot add more medicines until approved."
        binding.tvPrescriptionLockMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))
        binding.ivPrescriptionLock.visibility = View.VISIBLE
        binding.ivPrescriptionLock.setImageResource(R.drawable.ic_lock)

        // Show cancel button with confirmation
        binding.btnCancelPrescription.visibility = View.VISIBLE
        binding.btnCancelPrescription.setOnClickListener {
            showCancelPrescriptionDialog()
        }

        binding.buttonCheckout.isEnabled = false
        binding.buttonCheckout.text = "Waiting for Approval"
        binding.btnUploadNewPrescription.visibility = View.GONE
    }

    private fun showPrescriptionApproved(prescription: PrescriptionData) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.layoutPrescriptionLock.visibility = View.GONE

        binding.tvPrescriptionStatus.text = "✅ Prescription approved"
        binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_green))

        // Show approved products
        if (prescription.products.isNotEmpty()) {
            binding.tvPrescriptionProducts.text = buildString {
                append("Approved products:\n")
                prescription.products.forEachIndexed { index, product ->
                    append("${index + 1}. ${product.name} (${product.strength})\n")
                }
            }
            binding.tvPrescriptionProducts.visibility = View.VISIBLE
        }

        binding.ivPrescriptionPreview.visibility = View.VISIBLE
        Glide.with(this).load(prescription.prescriptionImageUrl).into(binding.ivPrescriptionPreview)

        binding.buttonCheckout.isEnabled = true
        binding.buttonCheckout.text = "Checkout"
        binding.btnUploadNewPrescription.visibility = View.GONE

        if (shouldAutoCheckout) {
            shouldAutoCheckout = false
            proceedToCheckout()
        }
    }

    private fun showPrescriptionRejected(prescription: PrescriptionData) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.layoutPrescriptionLock.visibility = View.GONE

        binding.tvPrescriptionStatus.text = "❌ Prescription rejected"
        binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_red))

        val rejectionDate = Date(prescription.timestamp)
        // Fixed: Handle missing rejectionReason property safely
        val rejectionReason = try {
            // If rejectionReason property exists, use it
            prescription.rejectionReason ?: "No reason provided"
        } catch (e: Exception) {
            // If property doesn't exist, provide default message
            "Please contact support for details"
        }

        binding.tvPrescriptionProducts.text = "Prescription rejected on ${DateFormat.getDateInstance().format(rejectionDate)} for: $rejectionReason"
        binding.tvPrescriptionProducts.visibility = View.VISIBLE

        binding.ivPrescriptionPreview.visibility = View.GONE
        binding.buttonCheckout.isEnabled = false
        binding.buttonCheckout.text = "Prescription Rejected"

        binding.btnUploadNewPrescription.visibility = View.VISIBLE
        binding.btnUploadNewPrescription.setOnClickListener {
            // Clear rejected prescription and start fresh
            viewModel.clearRejectedPrescription(prescription.id)
            navigateToPrescriptionUpload()
        }
    }

    private fun showPrescriptionExpired(prescription: PrescriptionData) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.layoutPrescriptionLock.visibility = View.GONE

        binding.tvPrescriptionStatus.text = "⏰ Prescription expired"
        binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_red))

        val expiredDate = Date(prescription.timestamp + TimeUnit.DAYS.toMillis(PRESCRIPTION_EXPIRY_DAYS))
        // Fixed: Use DateFormat.getDateInstance() instead of android.text.format.DateFormat.getDateInstance()
        binding.tvPrescriptionProducts.text = "Prescription expired on ${DateFormat.getDateInstance().format(expiredDate)}. Prescriptions are valid for $PRESCRIPTION_EXPIRY_DAYS days."
        binding.tvPrescriptionProducts.visibility = View.VISIBLE

        binding.ivPrescriptionPreview.visibility = View.GONE
        binding.buttonCheckout.isEnabled = false
        binding.buttonCheckout.text = "Prescription Expired"

        binding.btnUploadNewPrescription.visibility = View.VISIBLE
        binding.btnUploadNewPrescription.setOnClickListener {
            navigateToPrescriptionUpload()
        }
    }

    private fun showCancelPrescriptionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Prescription Review")
            .setMessage("Are you sure? Cancelling will require a new prescription upload.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                viewModel.cancelPendingPrescription()
                Toast.makeText(requireContext(), "Prescription review cancelled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No, Keep Waiting") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showPrescriptionStatus(imageUrl: String, productDetails: List<PrescriptionData.ProductInfo>? = null) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.tvPrescriptionStatus.text = "Prescription uploaded - pending review"
        binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))

        productDetails?.let { products ->
            val productsText = buildString {
                append("Products:\n")
                products.forEachIndexed { index, product ->
                    append("${index + 1}. ${product.name}\n")
                    append("   - Generic: ${product.genericName}\n")
                    append("   - Dosage: ${product.dosageForm}\n")
                    append("   - Strength: ${product.strength}\n\n")
                }
            }
            binding.tvPrescriptionProducts.text = productsText
            binding.tvPrescriptionProducts.visibility = View.VISIBLE
        }

        Glide.with(this).load(imageUrl).into(binding.ivPrescriptionPreview)
        binding.ivPrescriptionPreview.visibility = View.VISIBLE
    }

    private fun setupObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.productsPrice.collectLatest { price ->
                price?.let {
                    totalPrice = it
                    binding.tvTotalPrice.text = "PKR $it"
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.deleteDialog.collectLatest {
                showDeleteConfirmationDialog(it)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.cartProducts.collectLatest {
                when (it) {
                    is Resource.Loading -> showLoading()
                    is Resource.Success -> {
                        hideLoading()
                        if (it.data.isNullOrEmpty()) showEmptyCart() else {
                            hideEmptyCart()
                            setupCartProducts()
                        }
                    }
                    is Resource.Error -> showError(it.message)
                    else -> Unit
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.prescriptions.collect { state ->
                when (state) {
                    is Resource.Loading -> showPrescriptionLoading()
                    is Resource.Success -> {
                        state.data?.let {
                            updatePrescriptionUI()
                            // Auto-purge old rejected/expired prescriptions
                            viewModel.purgeOldPrescriptions()
                        }
                    }
                    is Resource.Error -> showPrescriptionError(state.message)
                    else -> Unit
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.prescriptionCancelled.collectLatest { cancelled ->
                if (cancelled) {
                    showNewPrescriptionRequired()
                    Toast.makeText(requireContext(), "You can now upload a new prescription", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        cartAdapter.onProductClick = { navigateToProductDetails(it.product) }

        cartAdapter.onPlusClick = { cartProduct ->
            if (canIncreaseQuantity(cartProduct)) {
                viewModel.changeQuantity(cartProduct, FirebaseCommon.QuantityChanging.INCREASE)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Cannot add more. Available quantity: ${cartProduct.product.availableQuantity}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        cartAdapter.onMinusClick = { cartProduct ->
            viewModel.changeQuantity(cartProduct, FirebaseCommon.QuantityChanging.DECREASE)
        }

        binding.buttonCheckout.setOnClickListener {
            val cartItems = cartAdapter.differ.currentList
            if (cartItems.isEmpty()) {
                Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            proceedToCheckout()
        }
    }

    private fun canIncreaseQuantity(cartProduct: CartProduct): Boolean {
        val availableQuantity = cartProduct.product.availableQuantity
        val currentQuantity = cartProduct.quantity
        return availableQuantity == null || availableQuantity == 0 || currentQuantity < availableQuantity
    }

    private fun proceedToCheckout() {
        val cartProducts = cartAdapter.differ.currentList
        val prescriptions = (viewModel.prescriptions.value as? Resource.Success)?.data ?: emptyList()
        val currentPrescription = getCurrentValidPrescription(prescriptions)

        val isPrescriptionVerified = !hasPrescriptionItems ||
                (currentPrescription?.status == PrescriptionData.STATUS_APPROVED &&
                        !isPrescriptionExpired(currentPrescription))

        if (hasPrescriptionItems && !isPrescriptionVerified) {
            Toast.makeText(requireContext(), "Valid prescription required for checkout", Toast.LENGTH_LONG).show()
            return
        }

        try {
            findNavController().navigate(
                CartFragmentDirections.actionCartFragmentToBillingFragment(
                    totalPrice = totalPrice,
                    products = cartProducts.toTypedArray(),
                    isPrescriptionVerified = isPrescriptionVerified,
                    prescriptionData = if (currentPrescription != null) arrayOf(currentPrescription) else emptyArray()
                )
            )
        } catch (e: Exception) {
            Log.e("CartFragment", "Navigation failed", e)
            Toast.makeText(requireContext(), "Checkout failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToPrescriptionUpload(items: List<CartProduct> = prescriptionItems) {
        val action = CartFragmentDirections.actionCartFragmentToPrescriptionFragment(
            productIds = items.map { it.product.id }.toTypedArray(),
            returnToCheckout = true
        )
        findNavController().navigate(action)
    }

    private fun showLoading() { binding.progressbarCart.visibility = View.VISIBLE }
    private fun hideLoading() { binding.progressbarCart.visibility = View.INVISIBLE }

    private fun showEmptyCart() {
        binding.layoutCartEmpty.visibility = View.VISIBLE
        binding.totalBoxContainer.visibility = View.GONE
        binding.buttonCheckout.visibility = View.GONE
        binding.layoutPrescriptionStatus.visibility = View.GONE
        binding.layoutPrescriptionLock.visibility = View.GONE
    }

    private fun hideEmptyCart() {
        binding.layoutCartEmpty.visibility = View.GONE
        binding.totalBoxContainer.visibility = View.VISIBLE
        binding.buttonCheckout.visibility = View.VISIBLE
    }

    private fun showError(message: String?) {
        Toast.makeText(requireContext(), message ?: "Error occurred", Toast.LENGTH_SHORT).show()
    }

    private fun showPrescriptionLoading() {
        if (hasPrescriptionItems) {
            binding.layoutPrescriptionStatus.visibility = View.VISIBLE
            binding.tvPrescriptionStatus.text = "Checking prescription status..."
        }
    }

    private fun showPrescriptionError(message: String?) {
        if (hasPrescriptionItems) {
            binding.layoutPrescriptionStatus.visibility = View.VISIBLE
            binding.tvPrescriptionStatus.text = message ?: "Error checking prescription"
            binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_red))
        }
    }

    private fun navigateToProductDetails(product: Product) {
        findNavController().navigate(
            R.id.action_cartFragment_to_productDetailsFragment,
            Bundle().apply { putParcelable("product", product) }
        )
    }

    private fun showDeleteConfirmationDialog(item: CartProduct) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to remove this item from your cart?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Yes") { _, _ ->
                viewModel.deleteCartProduct(item)
                Toast.makeText(requireContext(), "Item removed", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}