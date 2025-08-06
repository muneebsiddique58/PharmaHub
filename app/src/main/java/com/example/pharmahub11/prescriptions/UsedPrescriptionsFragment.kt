package com.example.pharmahub11.prescriptions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.PrescriptionUsedAdapter
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.databinding.FragmentUsedPrescriptionsBinding
import com.example.pharmahub11.util.showImageDialog
import com.example.pharmahub11.viewmodel.PrescriptionUsedViewModel
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UsedPrescriptionsFragment : Fragment() {

    private var _binding: FragmentUsedPrescriptionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PrescriptionUsedViewModel by viewModels()
    private lateinit var prescriptionAdapter: PrescriptionUsedAdapter
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsedPrescriptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupChipGroup()
        setupRecyclerView()
        observeViewModel()
        loadPrescriptions()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupChipGroup() {
        binding.chipGroup.clearCheck() // Clear any default selection

        // Add an "All" chip programmatically if you want
        // Or keep it without selection to show all by default

        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                when (chip.id) {
                    R.id.chipUsed -> {
                        println("Filtering by USED status")
                        viewModel.filterPrescriptions(PrescriptionData.STATUS_USED)
                    }
                    R.id.chipApproved -> {
                        println("Filtering by APPROVED status")
                        viewModel.filterPrescriptions(PrescriptionData.STATUS_APPROVED)
                    }
                    R.id.chipRejected -> {
                        println("Filtering by REJECTED status")
                        viewModel.filterPrescriptions(PrescriptionData.STATUS_REJECTED)
                    }
                    R.id.chipCancelled -> {
                        println("Filtering by CANCELLED status")
                        viewModel.filterPrescriptions(PrescriptionData.STATUS_CANCELLED)
                    }
                }
            } else {
                println("No chip selected - showing all prescriptions")
                viewModel.filterPrescriptions("") // Show all prescriptions if no chip is selected
            }
        }
    }

    private fun setupRecyclerView() {
        prescriptionAdapter = PrescriptionUsedAdapter(
            emptyList(),
            onDetailsClick = { prescription ->
                // Handle details click
            },
            onImageClick = { imageUrl ->
                showImageZoomDialog(imageUrl)
            }
        )

        binding.recyclerPrescriptions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = prescriptionAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeViewModel() {
        viewModel.filteredPrescriptions.observe(viewLifecycleOwner) { prescriptions ->
            println("Filtered prescriptions: $prescriptions") // Debug log

            // Sort prescriptions sequentially (newest first based on createdAt/timestamp)
            val sortedPrescriptions = prescriptions.sortedByDescending { prescription ->
                prescription.createdAt ?: prescription.timestamp ?: 0L
            }

            prescriptionAdapter.updatePrescriptions(sortedPrescriptions)
            binding.emptyState.visibility = if (sortedPrescriptions.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerPrescriptions.visibility = if (sortedPrescriptions.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerPrescriptions.visibility = View.GONE
            }
        }
    }

    private fun loadPrescriptions() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            println("Loading prescriptions for user: ${currentUser.uid}") // Debug log
            lifecycleScope.launch {
                viewModel.loadPrescriptions(currentUser.uid)
            }
        } else {
            println("No user logged in") // Debug log
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerPrescriptions.visibility = View.GONE
        }
    }

    private fun showImageZoomDialog(imageUrl: String) {
        requireContext().showImageDialog(imageUrl)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}