package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.data.PaymentMethod
import com.example.pharmahub11.data.PaymentType
import com.example.pharmahub11.databinding.ItemCodPaymentBinding

/**
 * PaymentMethodAdapter - Currently optimized for COD-only implementation
 *
 * FUTURE EXPANSION NOTES:
 * - Add different ViewHolder types for different payment methods
 * - Add CardPaymentViewHolder for credit/debit cards
 * - Add WalletPaymentViewHolder for digital wallets
 * - Add BankTransferViewHolder for bank transfers
 *
 * The adapter is designed to be easily extensible for future payment methods
 */
class PaymentMethodAdapter(
    private val paymentMethods: List<PaymentMethod>,
    private val onMethodSelected: (PaymentMethod) -> Unit
) : RecyclerView.Adapter<PaymentMethodAdapter.PaymentMethodViewHolder>() {

    private var selectedPosition = 0 // Auto-select first item (COD)

    /**
     * ViewHolder for payment methods
     * Currently uses the same layout for all payment types
     * FUTURE: Create specific ViewHolders for different payment types
     */
    class PaymentMethodViewHolder(
        private val binding: ItemCodPaymentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(method: PaymentMethod, isSelected: Boolean) {
            binding.apply {
                tvPaymentTitle.text = method.name
                tvPaymentNote.text = when (method.type) {
                    PaymentType.COD -> method.note ?: "Pay when you receive your order"
                    // FUTURE: Add specific notes for other payment types
                    // PaymentType.CARD -> "Secure card payment"
                    // PaymentType.PAYPAL -> "Pay with PayPal account"
                    else -> method.info
                }

                ivPaymentIcon.setImageResource(method.iconRes)
                rbPaymentSelected.isChecked = isSelected

                // Add visual feedback for selected state
                root.alpha = if (isSelected) 1.0f else 0.7f
                root.elevation = if (isSelected) 8f else 0f
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentMethodViewHolder {
        val binding = ItemCodPaymentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PaymentMethodViewHolder(binding)

        // FUTURE: Create different ViewHolders based on viewType
        /*
        return when (viewType) {
            R.layout.item_cod_payment -> CodPaymentViewHolder(binding)
            R.layout.item_card_payment -> CardPaymentViewHolder(cardBinding)
            R.layout.item_wallet_payment -> WalletPaymentViewHolder(walletBinding)
            else -> DefaultPaymentViewHolder(binding)
        }
        */
    }

    override fun onBindViewHolder(holder: PaymentMethodViewHolder, position: Int) {
        val method = paymentMethods[position]
        holder.bind(method, position == selectedPosition)

        holder.itemView.setOnClickListener {
            selectPosition(position)
            onMethodSelected(method)
        }
    }

    /**
     * Select a payment method by position
     */
    private fun selectPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position

        // Optimize redraws by only updating changed items
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }

    /**
     * Auto-select the first item (useful for COD-only implementation)
     */
    fun selectFirstItem() {
        if (paymentMethods.isNotEmpty()) {
            selectPosition(0)
        }
    }

    override fun getItemCount() = paymentMethods.size

    override fun getItemViewType(position: Int): Int {
        // Currently all items use the same layout
        // FUTURE: Return different layout IDs based on payment type
        return when (paymentMethods[position].type) {
            PaymentType.COD -> R.layout.item_cod_payment
            // FUTURE: Add other payment type layouts
            // PaymentType.CARD -> R.layout.item_card_payment
            // PaymentType.PAYPAL -> R.layout.item_paypal_payment
            // PaymentType.GOOGLE_PAY -> R.layout.item_wallet_payment
            else -> R.layout.item_cod_payment
        }
    }
}