package com.friendlocator.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a friendship between two users
 */
data class Friendship(
    @DocumentId
    val id: String = "",
    @get:PropertyName("user_id")
    @set:PropertyName("user_id")
    var userId: String = "",
    @get:PropertyName("friend_id")
    @set:PropertyName("friend_id")
    var friendId: String = "",
    @get:PropertyName("friend_email")
    @set:PropertyName("friend_email")
    var friendEmail: String = "",
    @get:PropertyName("friend_name")
    @set:PropertyName("friend_name")
    var friendName: String = "",
    @get:PropertyName("friend_photo")
    @set:PropertyName("friend_photo")
    var friendPhoto: String = "",
    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    @ServerTimestamp
    var createdAt: Timestamp? = null
) {
    // No-arg constructor required for Firestore
    constructor() : this("")
}
