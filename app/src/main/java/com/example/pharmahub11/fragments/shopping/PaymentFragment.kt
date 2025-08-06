package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.PaymentMethodAdapter
import com.example.pharmahub11.data.PaymentMethod
import com.example.pharmahub11.data.PaymentType
import com.example.pharmahub11.databinding.FragmentPaymentBinding

/**
 * PaymentFragment - Currently supports COD (Cash on Delivery) only
 *
 * FUTURE EXPANSION NOTES:
 * - Add support for Credit/Debit Cards
 * - Add support for Digital Wallets (PayPal, Google Pay, etc.)
 * - Add support for Bank Transfer
 * - Add support for Buy Now Pay Later options
 *
 * The current implementation is designed to be easily extensible for future payment methods
 */
class PaymentFragment : Fragment() {
    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!
    private lateinit var paymentAdapter: PaymentMethodAdapter
    private var selectedMethod: PaymentMethod? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPaymentMethods()
        setupConfirmButton()
        updateUIForCODOnly()
    }

    /**
     * Setup payment methods - Currently only COD is available
     *
     * FUTURE: When adding new payment methods, simply add them to this list
     * and ensure corresponding UI layouts and logic are implemented
     */
    private fun setupPaymentMethods() {
        val paymentMethods = listOf(
            PaymentMethod(
                id = "cod",
                type = PaymentType.COD,
                name = "Cash on Delivery",
                info = "Pay in cash when you receive your order",
                iconRes = R.drawable.ic_credit_card_24,
                note = "Please keep exact change ready for delivery"
            )
            // FUTURE PAYMENT METHODS TO ADD:
            /*
            PaymentMethod(
                id = "card",
                type = PaymentType.CARD,
                name = "Credit/Debit Card",
                info = "Pay with your Visa, Mastercard, etc.",
                iconRes = R.drawable.ic_cards
            ),
            PaymentMethod(
                id = "paypal",
                type = PaymentType.PAYPAL,
                name = "PayPal",
                info = "Pay securely with your PayPal account",
                iconRes = R.drawable.ic_paypal
            ),
            PaymentMethod(
                id = "gpay",
                type = PaymentType.GOOGLE_PAY,
                name = "Google Pay",
                info = "Pay with Google Pay",
                iconRes = R.drawable.ic_google_pay
            )
            */
        )

        paymentAdapter = PaymentMethodAdapter(paymentMethods) { method ->
            selectedMethod = method
            updatePaymentNote(method)
            updateConfirmButtonText(method)
        }

        binding.rvPaymentMethods.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = paymentAdapter
            setHasFixedSize(true)
        }

        // Auto-select COD since it's the only option
        if (paymentMethods.isNotEmpty()) {
            selectedMethod = paymentMethods.first()
            paymentAdapter.selectFirstItem()
        }
    }

    /**
     * Update payment note based on selected method
     * FUTURE: Add cases for other payment types
     */
    private fun updatePaymentNote(method: PaymentMethod) {
        binding.tvPaymentNote.text = when (method.type) {
            PaymentType.COD -> method.note
            // FUTURE: Add other payment type notes
            // PaymentType.CARD -> "Your card will be charged after order confirmation"
            // PaymentType.PAYPAL -> "You will be redirected to PayPal for secure payment"
            else -> ""
        }
    }

    /**
     * Update confirm button text based on selected payment method
     */
    private fun updateConfirmButtonText(method: PaymentMethod) {
        binding.btnConfirmPayment.text = when (method.type) {
            PaymentType.COD -> "Confirm COD Order"
            // FUTURE: Add other payment type button texts
            // PaymentType.CARD -> "Proceed to Card Payment"
            // PaymentType.PAYPAL -> "Pay with PayPal"
            else -> "Confirm Payment"
        }
    }

    /**
     * Update UI specifically for COD-only implementation
     */
    private fun updateUIForCODOnly() {
        // Update header note to inform users about current payment options
        binding.tvPaymentNote.text = "Currently, we only accept Cash on Delivery (COD). More payment options coming soon!"

        // Set confirm button text for COD
        binding.btnConfirmPayment.text = "Confirm COD Order"
    }

    private fun setupConfirmButton() {
        binding.btnConfirmPayment.setOnClickListener {
            selectedMethod?.let { method ->
                when (method.type) {
                    PaymentType.COD -> processCodOrder()
                    // FUTURE: Add other payment processing methods
                    // PaymentType.CARD -> processCardPayment()
                    // PaymentType.PAYPAL -> processPayPalPayment()
                    else -> processGenericPayment()
                }
            } ?: run {
                Toast.makeText(
                    requireContext(),
                    "Please select a payment method",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Process COD order - Navigate to order confirmation
     */
    private fun processCodOrder() {
        Toast.makeText(
            requireContext(),
            "COD Order confirmed! You'll pay when you receive your order.",
            Toast.LENGTH_LONG
        ).show()

        // Navigate to order confirmation with COD details
        findNavController().navigate(
            PaymentFragmentDirections.actionPaymentFragmentToProfileFragment(
                paymentMethod = selectedMethod?.name ?: "Cash on Delivery"
            )
        )
    }

    /**
     * FUTURE: Process online payment methods
     * This method will be expanded when other payment methods are added
     */
    private fun processGenericPayment() {
        Toast.makeText(
            requireContext(),
            "Processing ${selectedMethod?.name} payment...",
            Toast.LENGTH_SHORT
        ).show()

        // FUTURE: Implement specific payment gateway integrations
        // For example:
        // - Stripe for card payments
        // - PayPal SDK for PayPal payments
        // - Google Pay API for Google Pay
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}