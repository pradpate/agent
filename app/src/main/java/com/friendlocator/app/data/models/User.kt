package com.friendlocator.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a user in the system
 */
data class User(
    @DocumentId
    val id: String = "",
    val email: String = "",
    @get:PropertyName("display_name")
    @set:PropertyName("display_name")
    var displayName: String = "",
    @get:PropertyName("profile_picture_url")
    @set:PropertyName("profile_picture_url")
    var profilePictureUrl: String = "",
    @get:PropertyName("fcm_token")
    @set:PropertyName("fcm_token")
    var fcmToken: String = "",
    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    @ServerTimestamp
    var createdAt: Timestamp? = null,
    @get:PropertyName("last_active")
    @set:PropertyName("last_active")
    @ServerTimestamp
    var lastActive: Timestamp? = null,
    @get:PropertyName("location_sharing_enabled")
    @set:PropertyName("location_sharing_enabled")
    var locationSharingEnabled: Boolean = true
) {
    // No-arg constructor required for Firestore
    constructor() : this("")
}
