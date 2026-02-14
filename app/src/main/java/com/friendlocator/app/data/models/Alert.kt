package com.friendlocator.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents an alert/page sent between friends
 */
data class Alert(
    @DocumentId
    val id: String = "",
    @get:PropertyName("from_user_id")
    @set:PropertyName("from_user_id")
    var fromUserId: String = "",
    @get:PropertyName("to_user_id")
    @set:PropertyName("to_user_id")
    var toUserId: String = "",
    @get:PropertyName("from_user_name")
    @set:PropertyName("from_user_name")
    var fromUserName: String = "",
    @get:PropertyName("from_user_photo")
    @set:PropertyName("from_user_photo")
    var fromUserPhoto: String = "",
    var message: String = "",
    @get:PropertyName("is_read")
    @set:PropertyName("is_read")
    var isRead: Boolean = false,
    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    @ServerTimestamp
    var createdAt: Timestamp? = null
) {
    // No-arg constructor required for Firestore
    constructor() : this("")
}
