package com.example.pharmahub11.prescriptions

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.databinding.FragmentPrescriptionBinding
import com.example.pharmahub11.firebase.CloudinaryHelper
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.viewmodel.PrescriptionViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import android.Manifest
import com.example.pharmahub11.data.PrescriptionData
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class PrescriptionFragment : Fragment() {

    private lateinit var binding: FragmentPrescriptionBinding
    private val viewModel by viewModels<PrescriptionViewModel>()
    private var imageUri: Uri? = null
    private var currentTempFile: File? = null
    private var isUploading = false

    private val args: PrescriptionFragmentArgs by navArgs()

    @Inject lateinit var cloudinaryHelper: CloudinaryHelper
    @Inject lateinit var firebaseAuth: FirebaseAuth

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imageUri = it
            showImagePreview(it)
            updateUIForImageSelected(true)
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && imageUri != null) {
            showImagePreview(imageUri!!)
            updateUIForImageSelected(true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrescriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeUploadState()
        checkUserAuthentication()
    }

    private fun setupUI() {
        binding.apply {
            btnGallery.setOnClickListener { openGallery() }
            btnCamera.setOnClickListener { openCamera() }
            btnUpload.setOnClickListener { uploadSelectedImage() }
            btnChangeRemove.setOnClickListener { showChangeRemoveOptions() }

            // Hide the Place Order button as per requirement
            btnPlaceOrder.visibility = View.GONE

            updateUIForImageSelected(false)
        }
    }

    private fun showChangeRemoveOptions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Prescription Options")
            .setItems(arrayOf("Change Prescription", "Remove Prescription")) { _, which ->
                when (which) {
                    0 -> showImageSelectionOptions()
                    1 -> removePrescription()
                }
            }
            .show()
    }

    private fun showImageSelectionOptions() {
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image Source")
            .setItems(arrayOf("Camera", "Gallery")) { _, which ->
                if (which == 0) openCamera() else openGallery()
            }
            .show()
    }

    private fun removePrescription() {
        imageUri = null
        currentTempFile?.delete()
        binding.ivPrescriptionPreview.visibility = View.GONE
        updateUIForImageSelected(false)
        Snackbar.make(binding.root, "Prescription removed", Snackbar.LENGTH_SHORT).show()
    }

    private fun updateUIForImageSelected(selected: Boolean) {
        binding.btnUpload.isEnabled = selected
        binding.btnChangeRemove.visibility = if (selected) View.VISIBLE else View.GONE
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            showError("Camera permission is required to take prescription photos")
        }
    }

    private fun openCamera() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                try {
                    createImageUri()?.let { uri ->
                        imageUri = uri
                        cameraLauncher.launch(uri)
                    } ?: showError("Failed to create image file")
                } catch (e: IOException) {
                    showError("Failed to create image file: ${e.message}")
                } catch (e: Exception) {
                    showError("Camera error: ${e.message}")
                }
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationale()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("Camera Permission Needed")
            .setMessage("This app needs the Camera permission to take photos of your prescriptions")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createImageUri(): Uri? {
        return context?.let { ctx ->
            File.createTempFile(
                "prescription_${System.currentTimeMillis()}",
                ".jpg",
                ctx.cacheDir
            ).apply { currentTempFile = this }
                .let { file ->
                    FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.provider",
                        file
                    )
                }
        }
    }

    private fun showImagePreview(uri: Uri) {
        try {
            Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.ic_prescription)
                .error(R.drawable.ic_broken_image)
                .centerCrop()
                .into(binding.ivPrescriptionPreview)
            binding.ivPrescriptionPreview.visibility = View.VISIBLE
        } catch (e: Exception) {
            showError("Failed to load image: ${e.message}")
        }
    }

    private fun uploadSelectedImage() {
        imageUri?.let {
            lifecycleScope.launch {
                uploadPrescription(it)
            }
        } ?: showError(getString(R.string.no_image_selected))
    }

    // Updated to fetch actual product details from Firestore
    private suspend fun uploadPrescription(uri: Uri) {
        try {
            showLoading()

            // Upload image first
            val imageUrl = cloudinaryHelper.uploadImage(uri)

            // Get product IDs from arguments
            val productIds = args.productIds?.toList() ?: emptyList()

            // Fetch actual product details from Firestore
            val productsInfo = viewModel.getProductsDetails(productIds)

            // Create prescription data with actual product details
            val prescriptionData = PrescriptionData(
                prescriptionImageUrl = imageUrl,
                userId = firebaseAuth.currentUser?.uid ?: "",
                productIds = productIds,
                products = productsInfo,
                status = PrescriptionData.STATUS_PENDING,
                timestamp = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Save prescription and get ID
            val prescriptionId = viewModel.savePrescription(prescriptionData)

            hideLoading()
            showSuccessMessage("Prescription uploaded successfully!")

            handleNavigationAfterUpload(imageUrl, prescriptionId, productsInfo)

        } catch (e: Exception) {
            hideLoading()
            showError("Upload failed: ${e.message}")
        }
    }

    private fun showSuccessMessage(message: String) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleNavigationAfterUpload(
        imageUrl: String,
        prescriptionId: String,
        productsInfo: List<PrescriptionData.ProductInfo>
    ) {
        if (args.returnToCheckout) {
            // Pass data back to CartFragment
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "prescriptionUploaded",
                mapOf(
                    "imageUrl" to imageUrl,
                    "prescriptionId" to prescriptionId,
                    "productIds" to args.productIds,
                    "productDetails" to productsInfo
                )
            )
            findNavController().popBackStack()
        } else {
            // Navigate to profile or other destination
            findNavController().navigateUp()
        }
    }

    private fun observeUploadState() {
        lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uploadState.collect { state ->
                    when (state) {
                        is Resource.Loading -> showLoading()
                        is Resource.Success -> {
                            hideLoading()
                            state.data?.let { imageUrl ->
                                Toast.makeText(
                                    requireContext(),
                                    "Prescription uploaded successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            viewModel.resetUploadState()
                        }
                        is Resource.Error -> {
                            hideLoading()
                            showError(state.message ?: "Upload failed")
                            viewModel.resetUploadState()
                        }
                        else -> hideLoading()
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            btnUpload.isEnabled = false
            btnGallery.isEnabled = false
            btnCamera.isEnabled = false
        }
    }

    private fun hideLoading() {
        binding.apply {
            progressBar.visibility = View.GONE
            btnGallery.isEnabled = true
            btnCamera.isEnabled = true
        }
    }

    private fun showError(message: String?) {
        hideLoading()
        Toast.makeText(
            requireContext(),
            message ?: getString(R.string.unknown_error),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun checkUserAuthentication() {
        if (firebaseAuth.currentUser == null) {
            showError(getString(R.string.auth_required))
            findNavController().navigate(R.id.action_prescriptionFragment_to_profileFragment)
        }
    }
}