package com.example.pharmahub11.util

import android.util.Log
import com.example.pharmahub11.data.PrescriptionData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Utility class to fix any issues with orders and prescriptions in Firestore
 */
object FirestoreOrderFixUtil {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Checks if prescriptions are correctly linked to an order
     * @param orderId The ID of the order to check
     * @return A map with diagnostic information
     */
    suspend fun checkOrderPrescriptions(orderId: String): Map<String, Any> {
        val resultMap = mutableMapOf<String, Any>()

        try {
            // Get the order document
            val orderDoc = firestore.collection("orders")
                .document(orderId)
                .get()
                .await()

            if (!orderDoc.exists()) {
                resultMap["error"] = "Order not found"
                return resultMap
            }

            // Extract prescription data from order
            val prescriptionsInOrder = orderDoc.get("prescriptions") as? List<Map<String, Any>> ?: emptyList()
            resultMap["prescriptionsInOrderCount"] = prescriptionsInOrder.size

            // Get prescriptions from prescriptions collection
            val prescriptionsCollection = firestore.collection("prescriptions")
                .whereEqualTo("usedInOrder", orderId)
                .get()
                .await()

            resultMap["prescriptionsInCollectionCount"] = prescriptionsCollection.size()

            // Check if counts match
            val countsMatch = prescriptionsInOrder.size == prescriptionsCollection.size()
            resultMap["prescriptionCountsMatch"] = countsMatch

            // Get prescription IDs from both sources
            val orderPrescriptionIds = prescriptionsInOrder.mapNotNull { it["id"] as? String }
            val collectionPrescriptionIds = prescriptionsCollection.documents.map { it.id }

            resultMap["orderPrescriptionIds"] = orderPrescriptionIds
            resultMap["collectionPrescriptionIds"] = collectionPrescriptionIds

            // Check if all IDs in order exist in collection
            val missingInCollection = orderPrescriptionIds - collectionPrescriptionIds.toSet()
            resultMap["missingInCollection"] = missingInCollection

            // Check if all IDs in collection exist in order
            val missingInOrder = collectionPrescriptionIds - orderPrescriptionIds.toSet()
            resultMap["missingInOrder"] = missingInOrder

            return resultMap
        } catch (e: Exception) {
            Log.e("FirestoreOrderFixUtil", "Error checking order prescriptions", e)
            resultMap["error"] = e.message ?: "Unknown error"
            return resultMap
        }
    }

    /**
     * Fixes issues with prescriptions in an order
     * @param orderId The ID of the order to fix
     * @return true if fix was applied successfully
     */
    suspend fun fixOrderPrescriptions(orderId: String): Boolean {
        try {
            // Get diagnostic information
            val diagnostics = checkOrderPrescriptions(orderId)

            if (diagnostics.containsKey("error")) {
                Log.e("FirestoreOrderFixUtil", "Cannot fix order: ${diagnostics["error"]}")
                return false
            }

            val missingInCollection = diagnostics["missingInCollection"] as? List<String> ?: emptyList()
            val missingInOrder = diagnostics["missingInOrder"] as? List<String> ?: emptyList()

            // Case 1: Order has prescriptions that don't exist in prescriptions collection
            if (missingInCollection.isNotEmpty()) {
                Log.d("FirestoreOrderFixUtil", "Found ${missingInCollection.size} prescriptions missing in collection")

                // Get prescription details from order
                val orderDoc = firestore.collection("orders")
                    .document(orderId)
                    .get()
                    .await()

                val prescriptionsInOrder = orderDoc.get("prescriptions") as? List<Map<String, Any>> ?: emptyList()

                // Fix each missing prescription
                val batch = firestore.batch()

                missingInCollection.forEach { prescriptionId ->
                    val prescriptionData = prescriptionsInOrder.find { it["id"] == prescriptionId }

                    if (prescriptionData != null) {
                        val prescriptionRef = firestore.collection("prescriptions").document(prescriptionId)

                        batch.set(prescriptionRef, mapOf(
                            "userId" to (prescriptionData["userId"] as? String ?: auth.currentUser?.uid ?: ""),
                            "prescriptionImageUrl" to (prescriptionData["imageUrl"] as? String ?: ""),
                            "status" to "used",
                            "usedInOrder" to orderId,
                            "timestamp" to (prescriptionData["timestamp"] ?: FieldValue.serverTimestamp())
                        ))
                    }
                }

                batch.commit().await()
            }

            // Case 2: Prescriptions collection has entries that don't exist in order
            if (missingInOrder.isNotEmpty()) {
                Log.d("FirestoreOrderFixUtil", "Found ${missingInOrder.size} prescriptions missing in order")

                // Get prescription details from collection
                val prescriptions = missingInOrder.map { prescriptionId ->
                    val doc = firestore.collection("prescriptions")
                        .document(prescriptionId)
                        .get()
                        .await()

                    mapOf(
                        "id" to prescriptionId,
                        "imageUrl" to (doc.getString("prescriptionImageUrl") ?: ""),
                        "userId" to (doc.getString("userId") ?: auth.currentUser?.uid ?: ""),
                        "timestamp" to (doc.getDate("timestamp") ?: com.google.firebase.Timestamp.now())
                    )
                }

                // Update order document
                val orderRef = firestore.collection("orders").document(orderId)
                firestore.runTransaction { transaction ->
                    val orderDoc = transaction.get(orderRef)
                    val existingPrescriptions = orderDoc.get("prescriptions") as? List<Map<String, Any>> ?: emptyList()
                    val updatedPrescriptions = existingPrescriptions + prescriptions

                    transaction.update(orderRef, "prescriptions", updatedPrescriptions)
                }.await()

                // Also update in user's orders collection
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val userOrderRef = firestore.collection("user")
                        .document(userId)
                        .collection("orders")
                        .document(orderId)

                    firestore.runTransaction { transaction ->
                        val orderDoc = transaction.get(userOrderRef)
                        val existingPrescriptions = orderDoc.get("prescriptions") as? List<Map<String, Any>> ?: emptyList()
                        val updatedPrescriptions = existingPrescriptions + prescriptions

                        transaction.update(userOrderRef, "prescriptions", updatedPrescriptions)
                    }.await()
                }
            }

            return true
        } catch (e: Exception) {
            Log.e("FirestoreOrderFixUtil", "Error fixing order prescriptions", e)
            return false
        }
    }

    /**
     * Updates an existing order to include prescription data
     * @param orderId The ID of the order to update
     * @param prescriptionData List of prescription data to add
     */
    suspend fun updateOrderWithPrescriptions(orderId: String, prescriptionData: List<PrescriptionData>): Boolean {
        if (prescriptionData.isEmpty()) return true // Nothing to do

        try {
            val batch = firestore.batch()

            // 1. Mark prescriptions as used
            prescriptionData.forEach { prescription ->
                val prescriptionRef = firestore.collection("prescriptions").document(prescription.id)
                batch.update(prescriptionRef, mapOf(
                    "usedInOrder" to orderId,
                    "status" to "used",
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }

            // 2. Format prescription data for Firestore
            val prescriptionMaps = prescriptionData.map { prescription ->
                hashMapOf(
                    "id" to prescription.id,
                    "imageUrl" to prescription.prescriptionImageUrl,
                    "userId" to prescription.userId,
                    "timestamp" to prescription.timestamp
                )
            }

            // 3. Update order in global orders collection
            val orderRef = firestore.collection("orders").document(orderId)
            batch.update(orderRef, "prescriptions", prescriptionMaps)

            // 4. Update order in user's orders collection
            val userId = auth.currentUser?.uid
            if (userId != null) {
                val userOrderRef = firestore.collection("user")
                    .document(userId)
                    .collection("orders")
                    .document(orderId)
                batch.update(userOrderRef, "prescriptions", prescriptionMaps)
            }

            // Execute batch
            batch.commit().await()
            return true
        } catch (e: Exception) {
            Log.e("FirestoreOrderFixUtil", "Error updating order with prescriptions", e)
            return false
        }
    }
}