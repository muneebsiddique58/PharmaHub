package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.data.Review
import com.example.pharmahub11.databinding.FragmentAddReviewBinding
import com.example.pharmahub11.helper.getProductPrice
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class AddReviewFragment : Fragment() {

    private var _binding: FragmentAddReviewBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddReviewFragmentArgs>()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupRatingBar()
        loadProductInfo()
        checkExistingReview()
    }

    private fun setupClickListeners() {
        binding.imageCloseReview.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSubmitReview.setOnClickListener {
            submitReview()
        }
    }

    private fun setupRatingBar() {
        binding.ratingBar.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { _, rating, _ ->
            binding.tvRatingText.text = when (rating.toInt()) {
                1 -> "Poor"
                2 -> "Fair"
                3 -> "Good"
                4 -> "Very Good"
                5 -> "Excellent"
                else -> "Tap to rate"
            }
        }
    }

    private fun loadProductInfo() {
        firestore.collection("Products")
            .document(args.productId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val product = document.toObject(Product::class.java)
                    product?.let {
                        binding.tvProductName.text = it.name
                        val priceAfterPercentage = it.offerPercentage.getProductPrice(it.price)
                        binding.tvProductPrice.text = "PKR ${String.format("%.2f", priceAfterPercentage)}"
                        Glide.with(requireContext())
                            .load(it.images.firstOrNull())
                            .placeholder(R.color.g_blue)
                            .into(binding.imageProduct)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddReviewFragment", "Error loading product info", e)
                Toast.makeText(requireContext(), "Error loading product information", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkExistingReview() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("reviews")
            .whereEqualTo("orderId", args.orderId)
            .whereEqualTo("productId", args.productId)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val existingReview = documents.first().toObject(Review::class.java)
                    binding.ratingBar.rating = existingReview.rating.toFloat()
                    binding.etComment.setText(existingReview.comment)
                    binding.btnSubmitReview.text = "Update Review"
                    Toast.makeText(
                        requireContext(),
                        "You have already reviewed this product. You can update your review.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("AddReviewFragment", "Error checking existing review", e)
            }
    }

    private fun submitReview() {
        val rating = binding.ratingBar.rating
        val comment = binding.etComment.text.toString().trim()

        // Validation
        if (rating == 0f) {
            Toast.makeText(requireContext(), "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }
        if (comment.isEmpty()) {
            Toast.makeText(requireContext(), "Please write a comment", Toast.LENGTH_SHORT).show()
            return
        }
        if (comment.length < 10) {
            Toast.makeText(requireContext(), "Please write a more detailed review (at least 10 characters)", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid ?: return
        binding.btnSubmitReview.isEnabled = false
        binding.btnSubmitReview.text = "Submitting..."

        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                // Construct full name, handling null or empty cases
                val firstName = userDoc.getString("firstName")?.takeIf { it.isNotBlank() } ?: ""
                val lastName = userDoc.getString("lastName")?.takeIf { it.isNotBlank() } ?: ""
                val userName = when {
                    firstName.isNotEmpty() && lastName.isNotEmpty() -> "$firstName $lastName"
                    firstName.isNotEmpty() -> firstName
                    lastName.isNotEmpty() -> lastName
                    else -> "Anonymous"
                }
                val userEmail = userDoc.getString("email")?.takeIf { it.isNotBlank() } ?: ""

                val review = Review(
                    orderId = args.orderId,
                    productId = args.productId,
                    userId = userId,
                    rating = rating.toFloat(),
                    comment = comment,
                    timestamp = Timestamp.now(),
                    userName = userName,
                    userEmail = userEmail
                )

                checkAndSubmitReview(review)
            }
            .addOnFailureListener { e ->
                Log.e("AddReviewFragment", "Error getting user info", e)
                // Fallback to anonymous review if user info fetch fails
                val review = Review(
                    orderId = args.orderId,
                    productId = args.productId,
                    userId = userId,
                    rating = rating.toFloat(),
                    comment = comment,
                    timestamp = Timestamp.now(),
                    userName = "Anonymous",
                    userEmail = ""
                )
                checkAndSubmitReview(review)
            }
    }

    private fun checkAndSubmitReview(review: Review) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("reviews")
            .whereEqualTo("orderId", args.orderId)
            .whereEqualTo("productId", args.productId)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Create new review
                    firestore.collection("reviews")
                        .add(review)
                        .addOnSuccessListener {
                            handleSubmitSuccess("Review submitted successfully!")
                        }
                        .addOnFailureListener { e ->
                            handleSubmitError(e, "Failed to submit review")
                        }
                } else {
                    // Update existing review
                    val documentId = documents.first().id
                    firestore.collection("reviews")
                        .document(documentId)
                        .set(review)
                        .addOnSuccessListener {
                            handleSubmitSuccess("Review updated successfully!")
                        }
                        .addOnFailureListener { e ->
                            handleSubmitError(e, "Failed to update review")
                        }
                }
            }
            .addOnFailureListener { e ->
                handleSubmitError(e, "Error checking existing review")
            }
    }

    private fun handleSubmitSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun handleSubmitError(exception: Exception, message: String) {
        Log.e("AddReviewFragment", message, exception)
        Toast.makeText(requireContext(), "$message: ${exception.message}", Toast.LENGTH_SHORT).show()
        binding.btnSubmitReview.isEnabled = true
        binding.btnSubmitReview.text = "Submit Review"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}