package com.friendlocator.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a friend request between two users
 */
data class FriendRequest(
    @DocumentId
    val id: String = "",
    @get:PropertyName("from_user_id")
    @set:PropertyName("from_user_id")
    var fromUserId: String = "",
    @get:PropertyName("to_user_id")
    @set:PropertyName("to_user_id")
    var toUserId: String = "",
    @get:PropertyName("from_user_email")
    @set:PropertyName("from_user_email")
    var fromUserEmail: String = "",
    @get:PropertyName("from_user_name")
    @set:PropertyName("from_user_name")
    var fromUserName: String = "",
    @get:PropertyName("from_user_photo")
    @set:PropertyName("from_user_photo")
    var fromUserPhoto: String = "",
    @get:PropertyName("to_user_email")
    @set:PropertyName("to_user_email")
    var toUserEmail: String = "",
    var status: FriendRequestStatus = FriendRequestStatus.PENDING,
    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    @ServerTimestamp
    var createdAt: Timestamp? = null,
    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    @ServerTimestamp
    var updatedAt: Timestamp? = null
) {
    // No-arg constructor required for Firestore
    constructor() : this("")
}

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
