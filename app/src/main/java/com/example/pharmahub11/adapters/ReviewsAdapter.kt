package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.data.Review
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class ReviewsAdapter : RecyclerView.Adapter<ReviewsAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    }

    private val differCallback = object : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review): Boolean {
            return oldItem.orderId == newItem.orderId && oldItem.productId == newItem.productId
        }

        override fun areContentsTheSame(oldItem: Review, newItem: Review): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, differCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = differ.currentList[position]

        // Display user name or anonymized user ID
        holder.tvUserName.text = if (review.userName.isNullOrBlank() || review.userName == "null null") {
            "User ${review.userId?.takeLast(4) ?: "Anonymous"}"
        } else {
            review.userName
        }

        // Set rating
        holder.ratingBar.rating = review.rating.toFloat()

        // Set comment
        holder.tvComment.text = review.comment.ifEmpty { "No comment provided" }

        // Format date - handle Timestamp from Firestore
        holder.tvDate.text = when (review.timestamp) {
            is Timestamp -> {
                val date = (review.timestamp as Timestamp).toDate()
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
            }
            is Date -> {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(review.timestamp as Date)
            }
            else -> "N/A"
        }
    }

    override fun getItemCount(): Int = differ.currentList.size
}