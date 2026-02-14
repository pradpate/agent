package com.friendlocator.app.data.repository

import com.friendlocator.app.data.models.UserLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val locationsCollection = firestore.collection("locations")

    /**
     * Update the current user's location
     */
    suspend fun updateLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        altitude: Double,
        speed: Float,
        bearing: Float
    ): Result<UserLocation> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            
            val location = UserLocation(
                id = userId,
                userId = userId,
                geoPoint = GeoPoint(latitude, longitude),
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                altitude = altitude,
                speed = speed,
                bearing = bearing
            )

            locationsCollection.document(userId).set(location).await()
            Result.success(location)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a specific user's location
     */
    suspend fun getUserLocation(userId: String): Result<UserLocation?> {
        return try {
            val document = locationsCollection.document(userId).get().await()
            val location = document.toObject(UserLocation::class.java)
            Result.success(location)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get real-time location updates for a specific user
     */
    fun getUserLocationFlow(userId: String): Flow<UserLocation?> = callbackFlow {
        val listener = locationsCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val location = snapshot?.toObject(UserLocation::class.java)
                trySend(location)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get real-time location updates for multiple users (friends)
     */
    fun getFriendsLocationsFlow(friendIds: List<String>): Flow<List<UserLocation>> = callbackFlow {
        if (friendIds.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Firestore 'in' queries support up to 30 values
        // For more friends, we'd need to split into multiple queries
        val limitedIds = friendIds.take(30)

        val listener = locationsCollection
            .whereIn("user_id", limitedIds)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { 
                    it.toObject(UserLocation::class.java) 
                } ?: emptyList()
                trySend(locations)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Delete the current user's location data
     */
    suspend fun deleteUserLocation(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            locationsCollection.document(userId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current user's location
     */
    fun getCurrentUserLocationFlow(): Flow<UserLocation?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = locationsCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val location = snapshot?.toObject(UserLocation::class.java)
                trySend(location)
            }

        awaitClose { listener.remove() }
    }
}
