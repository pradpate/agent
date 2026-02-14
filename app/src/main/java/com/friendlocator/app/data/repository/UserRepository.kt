package com.friendlocator.app.data.repository

import com.friendlocator.app.data.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    private val usersCollection = firestore.collection("users")

    /**
     * Get the current authenticated user's ID
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Get the current authenticated user's email
     */
    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    /**
     * Create or update a user in Firestore
     */
    suspend fun createOrUpdateUser(user: User): Result<User> {
        return try {
            val userId = user.id.ifEmpty { auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user")) }
            
            // Get FCM token
            val fcmToken = try {
                messaging.token.await()
            } catch (e: Exception) {
                ""
            }

            val userWithToken = user.copy(fcmToken = fcmToken)
            usersCollection.document(userId).set(userWithToken).await()
            Result.success(userWithToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a user by their ID
     */
    suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val document = usersCollection.document(userId).get().await()
            val user = document.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a user by their email
     */
    suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            val querySnapshot = usersCollection
                .whereEqualTo("email", email.lowercase())
                .limit(1)
                .get()
                .await()
            
            val user = querySnapshot.documents.firstOrNull()?.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search users by email (partial match)
     */
    suspend fun searchUsersByEmail(emailQuery: String): Result<List<User>> {
        return try {
            val querySnapshot = usersCollection
                .whereGreaterThanOrEqualTo("email", emailQuery.lowercase())
                .whereLessThanOrEqualTo("email", emailQuery.lowercase() + "\uf8ff")
                .limit(20)
                .get()
                .await()

            val users = querySnapshot.documents.mapNotNull { it.toObject(User::class.java) }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get current user data as a flow
     */
    fun getCurrentUserFlow(): Flow<User?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java)
                trySend(user)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Update user's FCM token
     */
    suspend fun updateFcmToken(token: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            usersCollection.document(userId).update("fcm_token", token).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user's last active timestamp
     */
    suspend fun updateLastActive(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            usersCollection.document(userId).update(
                "last_active", com.google.firebase.Timestamp.now()
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggle location sharing setting
     */
    suspend fun setLocationSharingEnabled(enabled: Boolean): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            usersCollection.document(userId).update("location_sharing_enabled", enabled).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
