package com.friendlocator.app.data.repository

import com.friendlocator.app.data.models.Alert
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val alertsCollection = firestore.collection("alerts")

    /**
     * Send an alert to a friend
     */
    suspend fun sendAlert(
        toUserId: String,
        message: String = "is trying to reach you!"
    ): Result<Alert> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No authenticated user"))
            
            val alert = Alert(
                fromUserId = currentUser.uid,
                toUserId = toUserId,
                fromUserName = currentUser.displayName ?: "Someone",
                fromUserPhoto = currentUser.photoUrl?.toString() ?: "",
                message = message,
                isRead = false
            )

            val docRef = alertsCollection.document()
            alertsCollection.document(docRef.id).set(alert.copy(id = docRef.id)).await()
            
            Result.success(alert.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get alerts received by the current user
     */
    fun getReceivedAlertsFlow(): Flow<List<Alert>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = alertsCollection
            .whereEqualTo("to_user_id", currentUserId)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val alerts = snapshot?.documents?.mapNotNull { it.toObject(Alert::class.java) } ?: emptyList()
                trySend(alerts)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Mark an alert as read
     */
    suspend fun markAlertAsRead(alertId: String): Result<Unit> {
        return try {
            alertsCollection.document(alertId).update("is_read", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an alert
     */
    suspend fun deleteAlert(alertId: String): Result<Unit> {
        return try {
            alertsCollection.document(alertId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get unread alert count
     */
    fun getUnreadAlertCountFlow(): Flow<Int> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val listener = alertsCollection
            .whereEqualTo("to_user_id", currentUserId)
            .whereEqualTo("is_read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }

        awaitClose { listener.remove() }
    }
}
