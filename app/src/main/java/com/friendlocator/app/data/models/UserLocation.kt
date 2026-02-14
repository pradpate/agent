package com.friendlocator.app.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a user's location
 */
data class UserLocation(
    @DocumentId
    val id: String = "",
    @get:PropertyName("user_id")
    @set:PropertyName("user_id")
    var userId: String = "",
    @get:PropertyName("geo_point")
    @set:PropertyName("geo_point")
    var geoPoint: GeoPoint? = null,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var accuracy: Float = 0f,
    var altitude: Double = 0.0,
    var speed: Float = 0f,
    var bearing: Float = 0f,
    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    @ServerTimestamp
    var updatedAt: Timestamp? = null
) {
    // No-arg constructor required for Firestore
    constructor() : this("")
}
