package com.example.pharmahub11.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.pharmahub11.R
import com.example.pharmahub11.databinding.FragmentCustomerServiceBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.viewmodel.SupportViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CustomerServiceFragment : Fragment() {

    private lateinit var binding: FragmentCustomerServiceBinding
    private val viewModel: SupportViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCustomerServiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupUI() {
        binding.tvSupportHeader.text = "We're Here to Help"
        binding.tvContactInfo.text = """
            For any issues, please contact us at:

            Email: hamxak441@gmail.com
            Support Hours: 9AM - 5PM (Mon-Fri)

            We'll respond within 24 hours.
        """.trimIndent()
    }

    private fun setupClickListeners() {
        binding.btnSubmitComplaint.setOnClickListener {
            validateAndSubmitComplaint()
        }

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnQuick1.setOnClickListener {
            binding.etComplaint.setText("I'm facing an issue with the app performance.")
        }

        binding.btnQuick2.setOnClickListener {
            binding.etComplaint.setText("I'm having trouble with the payment process.")
        }

        binding.btnQuick3.setOnClickListener {
            binding.etComplaint.setText("My delivery has been delayed beyond the expected date.")
        }
    }

    private fun validateAndSubmitComplaint() {
        val complaintText = binding.etComplaint.text.toString().trim()

        when {
            complaintText.isEmpty() -> showErrorMessage("Please enter your complaint.")
            complaintText.length < 10 -> showErrorMessage("Complaint must be at least 10 characters.")
            else -> showConfirmationDialog(complaintText)
        }
    }

    private fun showConfirmationDialog(complaint: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Submission")
            .setMessage("Do you want to submit this complaint?\n\n\"$complaint\"")
            .setPositiveButton("Submit") { _, _ ->
                viewModel.submitComplaint(complaint)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.complaintState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    binding.etComplaint.text?.clear()
                    showSuccessMessage("Complaint submitted successfully.")
                }

                is Resource.Error -> {
                    showLoading(false)
                    showErrorMessage(resource.message ?: "Failed to submit complaint.")
                }

                else -> Unit
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmitComplaint.isEnabled = !isLoading
        binding.btnQuick1.isEnabled = !isLoading
        binding.btnQuick2.isEnabled = !isLoading
        binding.btnQuick3.isEnabled = !isLoading
    }

    private fun showSuccessMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.colorSuccess))
            .show()
    }

    private fun showErrorMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.colorError))
            .show()
    }
}
