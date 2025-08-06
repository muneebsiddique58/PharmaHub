package com.example.pharmahub11.locations

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pharmahub11.R
import com.example.pharmahub11.databinding.FragmentLocationBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.Locale
import kotlin.random.Random

@AndroidEntryPoint
class LocationFragment : Fragment() {
    private lateinit var binding: FragmentLocationBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    // Human verification variables
    private var locationLoadTime: Long = 0
    private var userInteractionStartTime: Long = 0
    private var hasUserMovedMarker: Boolean = false
    private var mapClickCount: Int = 0
    private var isLocationConfirmed: Boolean = false
    private var captchaCode: String = ""
    private var locationDetectionFailed: Boolean = false

    // Minimum time thresholds (in milliseconds)
    private val MIN_INTERACTION_TIME = 2000L // 2 seconds
    private val MIN_TOTAL_TIME = 3000L // 3 seconds from location load

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupUI()
        setupClickListeners()

        if (hasLocationPermission()) {
            fetchDeviceLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun setupUI() {
        // Initially hide confirm button until verification is complete
        binding.btnConfirmLocation.visibility = View.GONE
        binding.btnConfirmLocation.isEnabled = false

        // Set up verification message
        binding.tvVerificationMessage.text = "To ensure you're human, please confirm your current location by tapping the map marker. The app will use this only for verification and not store it permanently."

        // Hide fallback UI initially
        binding.layoutFallback.visibility = View.GONE
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnConfirmLocation.setOnClickListener {
            if (isHumanInteraction()) {
                confirmLocation()
            } else {
                showError("Please interact with the map to verify you're human.")
            }
        }

        // Set up map click listener for human verification
        binding.mapView.getMapboxMap().addOnMapClickListener { point ->
            handleMapClick(point.longitude(), point.latitude())
            true
        }

        // Set up CAPTCHA fallback
        binding.etCaptchaInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.toString()?.uppercase() == captchaCode) {
                    binding.btnConfirmLocation.visibility = View.VISIBLE
                    binding.btnConfirmLocation.isEnabled = true
                    isLocationConfirmed = true
                }
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocation() {
        try {
            binding.progressBar.visibility = View.VISIBLE
            locationLoadTime = System.currentTimeMillis()

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude

                    // Update map view
                    setupMapView(location.latitude, location.longitude)

                    // Reverse geocode to get address
                    reverseGeocode(location.latitude, location.longitude)

                    // Start human verification process
                    startHumanVerification()
                } ?: handleLocationFailure()
                binding.progressBar.visibility = View.GONE
            }.addOnFailureListener { e ->
                handleLocationFailure()
                binding.progressBar.visibility = View.GONE
            }
        } catch (e: SecurityException) {
            handleLocationFailure()
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupMapView(latitude: Double, longitude: Double) {
        binding.mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            // Set camera position
            val cameraOptions = CameraOptions.Builder()
                .center(com.mapbox.geojson.Point.fromLngLat(longitude, latitude))
                .zoom(14.0)
                .build()
            binding.mapView.getMapboxMap().setCamera(cameraOptions)

            // Add marker at current location
            val annotationApi = binding.mapView.annotations
            val pointAnnotationManager = annotationApi.createPointAnnotationManager()
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(com.mapbox.geojson.Point.fromLngLat(longitude, latitude))
                .withIconImage("marker-icon")
            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }

    private fun startHumanVerification() {
        userInteractionStartTime = System.currentTimeMillis()

        // Show instruction to user
        binding.tvVerificationMessage.text = "Please tap on the map marker to verify your location and confirm you're human."
        binding.tvVerificationMessage.visibility = View.VISIBLE

        // Add a slight delay to prevent immediate clicks
        Handler(Looper.getMainLooper()).postDelayed({
            binding.mapView.isClickable = true
        }, 1000)
    }

    private fun handleMapClick(longitude: Double, latitude: Double) {
        mapClickCount++

        // Check if click is near the marker (within reasonable distance)
        val distance = calculateDistance(currentLatitude, currentLongitude, latitude, longitude)

        if (distance < 500) { // Within 500 meters
            hasUserMovedMarker = true

            // Update current coordinates if user clicked nearby
            currentLatitude = latitude
            currentLongitude = longitude

            // Show visual feedback
            Toast.makeText(requireContext(), "Location confirmed! Please wait...", Toast.LENGTH_SHORT).show()

            // Enable confirm button after verification
            Handler(Looper.getMainLooper()).postDelayed({
                if (isHumanInteraction()) {
                    binding.btnConfirmLocation.visibility = View.VISIBLE
                    binding.btnConfirmLocation.isEnabled = true
                    isLocationConfirmed = true
                }
            }, 1000)
        } else {
            Toast.makeText(requireContext(), "Please tap closer to the marker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isHumanInteraction(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLoad = currentTime - locationLoadTime
        val timeSinceInteraction = currentTime - userInteractionStartTime

        return when {
            locationDetectionFailed -> isLocationConfirmed // For CAPTCHA fallback
            timeSinceLoad < MIN_TOTAL_TIME -> {
                showError("Please wait a moment before confirming.")
                false
            }
            timeSinceInteraction < MIN_INTERACTION_TIME -> {
                showError("Please take a moment to verify your location.")
                false
            }
            !hasUserMovedMarker -> {
                showError("Please tap on the map marker to verify.")
                false
            }
            mapClickCount < 1 -> {
                showError("Please interact with the map first.")
                false
            }
            else -> true
        }
    }

    private fun confirmLocation() {
        val result = Bundle().apply {
            putDouble("latitude", currentLatitude)
            putDouble("longitude", currentLongitude)
            putBoolean("human_verified", true)
            putLong("verification_time", System.currentTimeMillis() - locationLoadTime)
        }
        parentFragmentManager.setFragmentResult("location_result", result)
        findNavController().popBackStack()
    }

    private fun handleLocationFailure() {
        locationDetectionFailed = true
        showFallbackVerification()
    }

    private fun showFallbackVerification() {
        binding.tvVerificationMessage.text = "Unable to detect location. Please type your city name and complete the verification below."
        binding.layoutFallback.visibility = View.VISIBLE

        // Generate random CAPTCHA
        captchaCode = generateCaptchaCode()
        binding.tvCaptchaCode.text = "Type '$captchaCode' to proceed:"

        binding.etCityInput.visibility = View.VISIBLE
        binding.etCaptchaInput.visibility = View.VISIBLE
    }

    private fun generateCaptchaCode(): String {
        val codes = listOf("PHARMA", "HEALTH", "MEDS", "VERIFY", "HUMAN")
        return codes[Random.nextInt(codes.size)]
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private fun reverseGeocode(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                val addressText = buildString {
                    address.thoroughfare?.let { append("$it, ") }
                    address.locality?.let { append(it) }
                }
                binding.tvLocationAddress.text = addressText
            } ?: showError("No address found")
        } catch (e: IOException) {
            showError("Could not get address")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchDeviceLocation()
                } else {
                    handleLocationFailure()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}