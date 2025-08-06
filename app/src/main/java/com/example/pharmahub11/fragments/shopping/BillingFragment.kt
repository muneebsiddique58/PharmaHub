package com.example.pharmahub11.fragments.shopping

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.AddressAdapter
import com.example.pharmahub11.adapters.BillingProductsAdapter
import com.example.pharmahub11.adapters.PrescriptionThumbnailAdapter
import com.example.pharmahub11.data.Address
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.databinding.FragmentBillingBinding
import com.example.pharmahub11.util.HorizontalItemDecoration
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.viewmodel.BillingViewModel
import com.example.pharmahub11.viewmodel.CartViewModel
import com.example.pharmahub11.viewmodel.OrderViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@AndroidEntryPoint
class BillingFragment : Fragment() {

    private lateinit var binding: FragmentBillingBinding
    private val addressAdapter by lazy { AddressAdapter() }
    private val billingProductsAdapter by lazy { BillingProductsAdapter() }
    private val billingViewModel by viewModels<BillingViewModel>()
    private val orderViewModel by viewModels<OrderViewModel>()
    private val args by navArgs<BillingFragmentArgs>()
    private var products = emptyList<CartProduct>()
    private var totalPrice = 0.0
    private var prescriptionData = emptyList<PrescriptionData>()
    private var selectedAddress: Address? = null
    private var orderPlaced = false
    private lateinit var prescriptionAdapter: PrescriptionThumbnailAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        products = args.products.toList()
        totalPrice = args.totalPrice.toDouble()
        prescriptionData = args.prescriptionData?.toList() ?: emptyList()

        // Debug log to verify prescription data
        Log.d("BillingFragment", "Received ${prescriptionData.size} prescriptions")
        prescriptionData.forEach {
            Log.d("BillingFragment", "Prescription: ${it.id} - ${it.prescriptionImageUrl}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBillingBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBillingProductsRv()
        setupAddressRv()
        setupPrescriptionThumbnails()
        updatePrescriptionVisibility()

        if (!args.payment) {
            binding.apply {
                buttonPlaceOrder.visibility = View.INVISIBLE
                totalBoxContainer.visibility = View.INVISIBLE
                middleLine.visibility = View.INVISIBLE
                bottomLine.visibility = View.INVISIBLE
            }
        }

        billingProductsAdapter.differ.submitList(products)
        binding.tvTotalPrice.text = "RS $totalPrice"

        binding.imageCloseBilling.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.imageAddAddress.setOnClickListener {
            findNavController().navigate(R.id.action_billingFragment_to_addressFragment)
        }

        addressAdapter.onClick = { address ->
            selectedAddress = address
            if (!args.payment) {
                val bundle = Bundle().apply { putParcelable("address", address) }
                findNavController().navigate(R.id.action_billingFragment_to_addressFragment, bundle)
            }
        }

        binding.buttonPlaceOrder.setOnClickListener {
            if (selectedAddress == null) {
                Toast.makeText(requireContext(), "Please select an address", Toast.LENGTH_SHORT).show()
            } else {
                showOrderConfirmationDialog()
            }
        }

        collectAddressUpdates()
        collectOrderUpdates()
    }

    private fun setupPrescriptionThumbnails() {
        prescriptionAdapter = PrescriptionThumbnailAdapter(
            onRemoveClick = { prescription ->
                removePrescription(prescription)
            },
            onImageClick = { prescription ->
                showFullScreenPrescription(prescription.prescriptionImageUrl)
            }
        )

        binding.rvPrescriptions.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = prescriptionAdapter
            addItemDecoration(HorizontalItemDecoration())
        }

        prescriptionAdapter.submitList(prescriptionData)
    }

    private fun showFullScreenPrescription(imageUrl: String) {
        val dialog = Dialog(requireContext()).apply {
            setContentView(R.layout.dialog_fullscreen_image)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val imageView = findViewById<ImageView>(R.id.imageFullScreen)
            Glide.with(requireContext())
                .load(imageUrl)
                .placeholder(R.drawable.ic_prescription)
                .error(R.drawable.bg_no_prescription)
                .into(imageView)

            findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
                dismiss()
            }

            // Enable zooming functionality
            val attrs = window?.attributes
            attrs?.dimAmount = 0.7f // 70% dim
            window?.attributes = attrs
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
    }

    private fun updatePrescriptionVisibility() {
        val hasPrescriptions = prescriptionData.isNotEmpty()
        binding.tvPrescriptionsHeader.visibility = if (hasPrescriptions) View.VISIBLE else View.GONE
        binding.rvPrescriptions.visibility = if (hasPrescriptions) View.VISIBLE else View.GONE
        binding.prescriptionDivider.visibility = if (hasPrescriptions) View.VISIBLE else View.GONE
    }

    private fun removePrescription(prescription: PrescriptionData) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Prescription")
            .setMessage("Are you sure you want to remove this prescription from your order?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                prescriptionData = prescriptionData.filter { it.id != prescription.id }
                prescriptionAdapter.submitList(prescriptionData)
                updatePrescriptionVisibility()
                Toast.makeText(requireContext(), "Prescription removed", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun collectAddressUpdates() {
        lifecycleScope.launchWhenStarted {
            billingViewModel.address.collectLatest {
                when (it) {
                    is Resource.Loading -> binding.progressbarAddress.visibility = View.VISIBLE
                    is Resource.Success -> {
                        binding.progressbarAddress.visibility = View.GONE
                        addressAdapter.differ.submitList(it.data)

                        // Select the first address by default if available
                        if (it.data?.isNotEmpty() == true && selectedAddress == null) {
                            selectedAddress = it.data.first()
                        }
                    }
                    is Resource.Error -> {
                        binding.progressbarAddress.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun collectOrderUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                orderViewModel.order.collect { state ->
                    when (state) {
                        is Resource.Loading -> {
                            binding.buttonPlaceOrder.startAnimation()
                            binding.buttonPlaceOrder.isEnabled = false
                        }
                        is Resource.Success -> {
                            binding.buttonPlaceOrder.revertAnimation()
                            binding.buttonPlaceOrder.isEnabled = true
                            if (!orderPlaced) {
                                orderPlaced = true
                                state.data?.let { handleOrderSuccess(it) }
                            }
                        }
                        is Resource.Error -> {
                            binding.buttonPlaceOrder.revertAnimation()
                            binding.buttonPlaceOrder.isEnabled = true
                            showOrderError(state.message)
                        }
                        else -> {
                            binding.buttonPlaceOrder.revertAnimation()
                            binding.buttonPlaceOrder.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun showOrderError(message: String?) {
        val errorMessage = when {
            message?.contains("Missing or invalid prescriptions") == true -> {
                "Some prescriptions are invalid or expired. Please upload new prescriptions."
            }
            message?.contains("Prescription validation failed") == true -> {
                "Prescription validation failed. Please check your prescriptions and try again."
            }
            else -> message ?: "Order failed. Please try again."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Order Failed")
            .setMessage(errorMessage)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun handleOrderSuccess(order: Order) {
        val cartViewModel: CartViewModel by activityViewModels()
        cartViewModel.clearCart()

        // Log prescription data for debugging
        Log.d("BillingFragment", "Order placed with ${order.prescriptionData.size} prescriptions")
        order.prescriptionData.forEach { prescription ->
            Log.d("BillingFragment", "Prescription ${prescription.id} attached to order ${order.orderId}")
        }

        Snackbar.make(requireView(), "Order #${order.orderId} placed successfully!", Snackbar.LENGTH_LONG).show()
        findNavController().navigate(R.id.action_billingFragment_to_ordersFragment)
    }


    private fun showOrderConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Order")
            .setMessage("Proceed with Cash on Delivery payment?")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Confirm") { dialog, _ ->
                placeOrderWithPrescriptionValidation()
                dialog.dismiss()
            }
            .show()
    }
    private fun placeOrderWithPrescriptionValidation() {
        val order = Order(
            orderId = UUID.randomUUID().toString(),
            date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            orderStatus = OrderStatus.ORDERED.status,
            totalPrice = totalPrice,
            products = products,
            address = selectedAddress!!,
            prescriptionData = prescriptionData,
            paymentMethod = "COD",
            userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )

        // Show loading state
        binding.buttonPlaceOrder.startAnimation()
        binding.buttonPlaceOrder.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Validate prescriptions if needed
                if (prescriptionData.isNotEmpty()) {
                    when (val validationResult = orderViewModel.validatePrescriptionsForOrder(
                        prescriptionData,
                        order.userId
                    )) {
                        is Resource.Success -> {
                            if (validationResult.data == false) {
                                showPrescriptionError("Invalid or expired prescriptions found")
                                return@launch
                            }
                        }
                        is Resource.Error -> {
                            showPrescriptionError(validationResult.message)
                            return@launch
                        }
                        else -> {
                            showPrescriptionError("Unknown validation state")
                            return@launch
                        }
                    }
                }

                // Place the order
                orderViewModel.placeOrder(order, "COD")
            } catch (e: Exception) {
                showPrescriptionError("Order failed: ${e.message}")
            } finally {
                binding.buttonPlaceOrder.revertAnimation()
                binding.buttonPlaceOrder.isEnabled = true
            }
        }
    }

    private fun showPrescriptionError(message: String?) {
        Toast.makeText(
            requireContext(),
            message ?: "Prescription validation error",
            Toast.LENGTH_LONG
        ).show()
        Log.e("BillingFragment", "Prescription error: $message")
    }

    private fun setupAddressRv() {
        binding.rvAddress.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = addressAdapter
            addItemDecoration(HorizontalItemDecoration())
        }
    }

    private fun setupBillingProductsRv() {
        binding.rvProducts.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            adapter = billingProductsAdapter
            addItemDecoration(HorizontalItemDecoration())
        }
    }
}