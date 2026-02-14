package com.friendlocator.app.data.repository

import com.friendlocator.app.data.models.FriendRequest
import com.friendlocator.app.data.models.FriendRequestStatus
import com.friendlocator.app.data.models.Friendship
import com.friendlocator.app.data.models.User
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
class FriendRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val friendRequestsCollection = firestore.collection("friend_requests")
    private val friendshipsCollection = firestore.collection("friendships")
    private val usersCollection = firestore.collection("users")

    /**
     * Send a friend request to another user
     */
    suspend fun sendFriendRequest(toUserEmail: String): Result<FriendRequest> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("No authenticated user"))
            
            // Find target user by email
            val targetUserQuery = usersCollection
                .whereEqualTo("email", toUserEmail.lowercase())
                .limit(1)
                .get()
                .await()

            if (targetUserQuery.isEmpty) {
                return Result.failure(Exception("User not found with email: $toUserEmail"))
            }

            val targetUser = targetUserQuery.documents.first().toObject(User::class.java)
                ?: return Result.failure(Exception("Failed to parse user data"))

            if (targetUser.id == currentUser.uid) {
                return Result.failure(Exception("You cannot send a friend request to yourself"))
            }

            // Check if a friend request already exists
            val existingRequest = friendRequestsCollection
                .whereEqualTo("from_user_id", currentUser.uid)
                .whereEqualTo("to_user_id", targetUser.id)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .limit(1)
                .get()
                .await()

            if (!existingRequest.isEmpty) {
                return Result.failure(Exception("Friend request already sent"))
            }

            // Check if they're already friends
            val existingFriendship = friendshipsCollection
                .whereEqualTo("user_id", currentUser.uid)
                .whereEqualTo("friend_id", targetUser.id)
                .limit(1)
                .get()
                .await()

            if (!existingFriendship.isEmpty) {
                return Result.failure(Exception("You are already friends with this user"))
            }

            // Create friend request
            val friendRequest = FriendRequest(
                fromUserId = currentUser.uid,
                toUserId = targetUser.id,
                fromUserEmail = currentUser.email ?: "",
                fromUserName = currentUser.displayName ?: "",
                fromUserPhoto = currentUser.photoUrl?.toString() ?: "",
                toUserEmail = toUserEmail.lowercase(),
                status = FriendRequestStatus.PENDING
            )

            val docRef = friendRequestsCollection.document()
            friendRequestsCollection.document(docRef.id).set(friendRequest.copy(id = docRef.id)).await()
            
            Result.success(friendRequest.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun acceptFriendRequest(requestId: String): Result<Friendship> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            
            val requestDoc = friendRequestsCollection.document(requestId).get().await()
            val request = requestDoc.toObject(FriendRequest::class.java)
                ?: return Result.failure(Exception("Friend request not found"))

            if (request.toUserId != currentUserId) {
                return Result.failure(Exception("You are not authorized to accept this request"))
            }

            // Update request status
            friendRequestsCollection.document(requestId).update(
                mapOf(
                    "status" to FriendRequestStatus.ACCEPTED.name,
                    "updated_at" to com.google.firebase.Timestamp.now()
                )
            ).await()

            // Get both users' info
            val fromUserDoc = usersCollection.document(request.fromUserId).get().await()
            val fromUser = fromUserDoc.toObject(User::class.java)

            val toUserDoc = usersCollection.document(request.toUserId).get().await()
            val toUser = toUserDoc.toObject(User::class.java)

            // Create bidirectional friendship
            val friendship1 = Friendship(
                userId = request.fromUserId,
                friendId = request.toUserId,
                friendEmail = toUser?.email ?: "",
                friendName = toUser?.displayName ?: "",
                friendPhoto = toUser?.profilePictureUrl ?: ""
            )

            val friendship2 = Friendship(
                userId = request.toUserId,
                friendId = request.fromUserId,
                friendEmail = fromUser?.email ?: "",
                friendName = fromUser?.displayName ?: "",
                friendPhoto = fromUser?.profilePictureUrl ?: ""
            )

            // Save both friendships
            val batch = firestore.batch()
            val doc1 = friendshipsCollection.document()
            val doc2 = friendshipsCollection.document()
            batch.set(doc1, friendship1.copy(id = doc1.id))
            batch.set(doc2, friendship2.copy(id = doc2.id))
            batch.commit().await()

            Result.success(friendship2.copy(id = doc2.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decline a friend request
     */
    suspend fun declineFriendRequest(requestId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))
            
            val requestDoc = friendRequestsCollection.document(requestId).get().await()
            val request = requestDoc.toObject(FriendRequest::class.java)
                ?: return Result.failure(Exception("Friend request not found"))

            if (request.toUserId != currentUserId) {
                return Result.failure(Exception("You are not authorized to decline this request"))
            }

            friendRequestsCollection.document(requestId).update(
                mapOf(
                    "status" to FriendRequestStatus.DECLINED.name,
                    "updated_at" to com.google.firebase.Timestamp.now()
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get pending friend requests received by the current user
     */
    fun getPendingRequestsFlow(): Flow<List<FriendRequest>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = friendRequestsCollection
            .whereEqualTo("to_user_id", currentUserId)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { it.toObject(FriendRequest::class.java) } ?: emptyList()
                trySend(requests)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get sent friend requests by the current user
     */
    fun getSentRequestsFlow(): Flow<List<FriendRequest>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = friendRequestsCollection
            .whereEqualTo("from_user_id", currentUserId)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { it.toObject(FriendRequest::class.java) } ?: emptyList()
                trySend(requests)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get all friends of the current user
     */
    fun getFriendsFlow(): Flow<List<Friendship>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = friendshipsCollection
            .whereEqualTo("user_id", currentUserId)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val friendships = snapshot?.documents?.mapNotNull { it.toObject(Friendship::class.java) } ?: emptyList()
                trySend(friendships)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Remove a friend
     */
    suspend fun removeFriend(friendId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("No authenticated user"))

            // Find and delete both friendship documents
            val friendship1 = friendshipsCollection
                .whereEqualTo("user_id", currentUserId)
                .whereEqualTo("friend_id", friendId)
                .get()
                .await()

            val friendship2 = friendshipsCollection
                .whereEqualTo("user_id", friendId)
                .whereEqualTo("friend_id", currentUserId)
                .get()
                .await()

            val batch = firestore.batch()
            friendship1.documents.forEach { batch.delete(it.reference) }
            friendship2.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get list of friend IDs for the current user
     */
    suspend fun getFriendIds(): List<String> {
        val currentUserId = auth.currentUser?.uid ?: return emptyList()
        
        return try {
            val friendships = friendshipsCollection
                .whereEqualTo("user_id", currentUserId)
                .get()
                .await()

            friendships.documents.mapNotNull { 
                it.toObject(Friendship::class.java)?.friendId 
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
